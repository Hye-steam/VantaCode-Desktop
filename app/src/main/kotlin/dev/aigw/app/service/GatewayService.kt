package dev.aigw.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import dev.aigw.app.AiGatewayApp
import dev.aigw.app.MainActivity
import dev.aigw.app.R
import dev.aigw.app.ui.AppViewModel
import dev.aigw.app.ui.MonitorBubbleOverlay
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter

/**
 * 只负责「让进程活着」：网关本身由 UI 启动，成功后拉起本服务，
 * 锁屏或切后台后端口仍有人监听。
 */
class GatewayService : Service() {

    private var overlay: MonitorBubbleOverlay? = null
    private val healthHandler = Handler(Looper.getMainLooper())
    private var healthScheduled = false

    /** 远程后台心跳线程：按后台下发的间隔循环上报，失败退避。 */
    private var remoteThread: Thread? = null

    @Volatile
    private var remoteRunning = false

    /** 每小时探测一次 DeepSeek 网页 token 是否失效，失效则禁用账号（UI 会显示状态）。 */
    private val healthRunnable = object : Runnable {
        override fun run() {
            Thread {
                runCatching { (applicationContext as AiGatewayApp).engine.probeTokenHealth() }
            }.start()
            healthHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS)
        }
    }
    private val bubbleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val app = applicationContext as AiGatewayApp
            if (app.engine.settings.monitorBubble && app.server.isRunning()) {
                startOverlay(app)
                overlay?.refreshAppearance()
            } else {
                overlay?.stop()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = applicationContext as AiGatewayApp
        // 通知必须最先发：startForegroundService 拉起的服务不调 startForeground
        // 会被系统判定超时直接崩溃。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(app.engine.settings.port),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(app.engine.settings.port))
        }
        val started = app.server.isRunning() || runCatching { app.server.start() }
            .onFailure { app.lastStartError = it.message ?: it.javaClass.simpleName }
            .isSuccess
        if (started) {
            app.lastStartError = null
            startOverlay(app)
            scheduleHealthCheck()
            startRemoteSync()
            // START_NOT_STICKY：进程被杀后不自重建，用户需重新打开 App。
            return START_NOT_STICKY
        }
        app.engine.eventLog.error("网关", "启动失败：${app.lastStartError ?: "未知原因"}")
        stopSelf()
        return START_NOT_STICKY
    }

    /** 启动后先等 60 秒再首查（避开刚启动的建连高峰），之后每小时一次。 */
    private fun scheduleHealthCheck() {
        if (healthScheduled) return
        healthScheduled = true
        healthHandler.postDelayed(healthRunnable, HEALTH_FIRST_DELAY_MS)
    }

    /**
     * 启动远程后台心跳：立刻发一次，之后按后台下发的 `heartbeat_interval` 循环。
     *
     * 失败只退避不报错：后台挂了绝不能拖垮网关，这是上报链路的硬约束。
     */
    private fun startRemoteSync() {
        if (remoteRunning) return
        remoteRunning = true
        remoteThread = Thread {
            var intervalSec = DEFAULT_HEARTBEAT_SEC
            var failures = 0
            while (remoteRunning) {
                val app = applicationContext as AiGatewayApp
                val result = runCatching {
                    app.engine.syncRemote(
                        version = app.appVersion,
                        running = app.server.isRunning(),
                        port = app.engine.settings.port,
                    )
                }.getOrNull()

                if (result != null) {
                    failures = 0
                    val next = result.int("heartbeat_interval")
                    if (next in 10..3600) intervalSec = next
                    runCatching { app.engine.applyRemoteConfig(result) }
                } else {
                    failures++
                    // 指数退避：10s → 20s → 40s … 封顶 5 分钟
                    intervalSec = minOf(DEFAULT_HEARTBEAT_SEC shl minOf(failures, 5), 300)
                }

                var slept = 0
                while (remoteRunning && slept < intervalSec * 1000) {
                    Thread.sleep(1_000)
                    slept += 1_000
                }

                // 每次心跳完顺带拉一次公告与更新（失败静默），有变化才写日志
                if (remoteRunning) {
                    runCatching {
                        (applicationContext as AiGatewayApp).engine.pollAnnouncementAndUpdate(
                            (applicationContext as AiGatewayApp).appVersion,
                        )
                    }
                }
            }
        }.apply { isDaemon = true; name = "aigw-remote-sync" }
        remoteThread?.start()
    }

    private fun stopRemoteSync() {
        remoteRunning = false
        remoteThread = null
    }

    /** 悬浮监测球随网关服务启停；未授权「显示在应用上层」时自动跳过。 */
    private fun startOverlay(app: AiGatewayApp) {
        if (!app.engine.settings.monitorBubble) {
            overlay?.stop()
            return
        }
        if (!android.provider.Settings.canDrawOverlays(this)) return
        val bubble = overlay ?: MonitorBubbleOverlay(this, app.engine).also { overlay = it }
        bubble.start()
    }
    override fun onCreate() {
        super.onCreate()
        registerReceiver(bubbleReceiver, IntentFilter(AppViewModel.ACTION_BUBBLE_CHANGED), RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        stopRemoteSync()
        healthHandler.removeCallbacks(healthRunnable)
        healthScheduled = false
        runCatching { unregisterReceiver(bubbleReceiver) }
        overlay?.stop()
        overlay = null
        (applicationContext as AiGatewayApp).server.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(port: Int): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, AiGatewayApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gateway)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_running, port))
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        private const val HEALTH_CHECK_INTERVAL_MS = 60 * 60 * 1000L
        private const val HEALTH_FIRST_DELAY_MS = 60 * 1000L

        /** 后台未下发间隔时的默认心跳周期。 */
        private const val DEFAULT_HEARTBEAT_SEC = 60
    }
}
