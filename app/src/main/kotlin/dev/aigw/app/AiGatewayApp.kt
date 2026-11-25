package dev.aigw.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dev.aigw.gateway.Engine
import dev.aigw.gateway.GatewayHttpServer
import dev.aigw.app.data.ChatglmWebTransport
import dev.aigw.app.data.MuseWebTransport
import dev.aigw.app.data.DeepSeekWebTransport
import java.io.File

/**
 * 进程级单例：引擎与 HTTP 服务懒初始化，避免 Application.onCreate 里做文件 IO 拖慢首帧。
 * UI 与前台服务共用同一实例，保证「UI 启动的网关」和「服务持有的端口」是同一个。
 */
class AiGatewayApp : Application() {

    val engine: Engine by lazy { Engine(File(filesDir, "aigw")) }

    val server: GatewayHttpServer by lazy { GatewayHttpServer(engine) }

    /** DeepSeek 原生请求被风控拦截时，改用这个 WebView 通道发请求。 */
    val deepSeekTransport: DeepSeekWebTransport by lazy { DeepSeekWebTransport(this) }

    /** 智谱清言：对话请求走这个 WebView 通道（WAF 签名由页面环境注入）。 */
    val chatglmTransport: ChatglmWebTransport by lazy { ChatglmWebTransport(this) }

    /** muse.ai 页面通道：懒加载，首次生成时才建 WebView。 */
    /**
     * 媒体落的公共目录。
     *
     * 私有目录 /Android/data/<包名>/ 从 Android 11 起对其他应用完全隔离，
     * 客户端拿到路径也读不到，所以优先写公共媒体目录；建不出来才退回私有目录
     * （退化后客户端仍看不到，至少不会让生成直接失败）。
     */
    private fun resolveMediaDir(): java.io.File {
        val public = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS,
            ),
            "VantaGate/media",
        )
        if (public.isDirectory || public.mkdirs()) return public
        return java.io.File(getExternalFilesDir(null) ?: filesDir, "media")
    }

    val museTransport: MuseWebTransport by lazy { MuseWebTransport(this) }


    /** 桌面宠物。不挂在网关服务上：开关一开就该出现，和网关是否运行无关。 */
    val petOverlay: dev.aigw.app.ui.PetOverlay by lazy {
        dev.aigw.app.ui.PetOverlay(this) {
            // 免费池是免登录通道，没有真实账号，和仪表盘口径保持一致
            val real = engine.pool.all().filter { it.account.providerId != "freepool" }
            val balances = real.map { it.state.balance }.filterNotNull()
            val total = balances.sum()
            val shown = if (balances.isEmpty()) "—" else {
                if (total >= 1000) String.format("%.1fK", total / 1000) else total.toLong().toString()
            }
            "当前积分 $shown  ·  ${real.size} 个账号"
        }
    }

    /** 最近一次启动网关的失败原因，供 UI 展示。 */
    @Volatile
    var lastStartError: String? = null

    /**
     * 当前 App 的真实版本号（versionName），运行时从 PackageManager 读取。
     * 上报后台用真实版本号，避免硬编码陈旧版本号导致后台误判「需要更新」。
     */
    val appVersion: String by lazy {
        runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "0.0.0"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        installCrashLogger()
        // 首个启动的免费池初始化：放主线程之前，保证首帧就能看到模型
        runCatching { engine.seedFreePoolIfNeeded() }
            .onFailure { engine.eventLog.error("设置", "免费池初始化失败：${it.message}") }
        // 媒体落盘目录：必须放在公共存储。
        // Android 11 起 /Android/data/<包名>/ 对其他应用完全隔离，
        // 客户端即使有 MANAGE_EXTERNAL_STORAGE 也读不到那里的文件。
        runCatching {
            val dir = resolveMediaDir()
            dir.mkdirs()
            dev.aigw.gateway.MediaProxy.setCacheDir(dir)
        }
        // 智谱清言页面通道：懒加载，提前挂到引擎上（真正请求时才创建 WebView）
        engine.chatglmWebTransport = chatglmTransport
        engine.museWebTransport = museTransport
        // App 启动即静默上报一次（不阻塞首帧，失败静默），保证后台能先看到设备
        Thread {
            runCatching { engine.syncRemote(appVersion) }
        }.apply { isDaemon = true }.start()
        // 桌宠默认开启：等首帧稳定后再挂，避免和界面初始化抢主线程
        runCatching {
            if (engine.settings.petBubble) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    { runCatching { petOverlay.start() } },
                    1200,
                )
            }
        }
    }

    /** 任何未捕获异常都先落到运行日志，再交给系统默认处理，避免只看到「应用已停止」。 */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                engine.eventLog.error(
                    "崩溃",
                    "${thread.name} 未捕获异常：${error.javaClass.name}: ${error.message}\n" +
                        error.stackTraceToString().take(2500),
                )
                engine.eventLog.flush()
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "gateway"
    }
}
