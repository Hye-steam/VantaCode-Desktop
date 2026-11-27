package dev.aigw.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.aigw.desktop.cef.GateCef
import dev.aigw.desktop.overlay.MonitorBubbleWindow
import dev.aigw.desktop.ui.AppRoot
import dev.aigw.desktop.util.UiPrefs
import dev.aigw.desktop.util.applyDarkTitleBar
import dev.aigw.gateway.Engine
import dev.aigw.gateway.GatewayHttpServer
import dev.aigw.gateway.MediaProxy
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object Main {
    /** 与 Android 2.17 功能集对应；未来桌面 tag 前缀 vantagate-desktop-v。 */
    const val VERSION = "2.17.0"
}

/**
 * 桌面入口（Android 的 AiGatewayApp + MainActivity + GatewayService 的合体）：
 * - 数据目录 ~/.aigw（与 :core CLI 一致），媒体输出 ~/Downloads/VantaGate/media
 * - 崩溃日志：未捕获异常写事件日志并落盘
 * - 后台线程：远程心跳（默认 60s，后台可下发间隔）+ Token 健康看门狗（每小时）
 * - 系统托盘：显示/启动/停止/退出；关闭主窗口收到托盘，退出时统一清理
 */
fun main() {
    val dataDir = File(System.getProperty("user.home"), ".aigw").apply { mkdirs() }
    val engine = Engine(dataDir)
    val server = GatewayHttpServer(engine)
    val prefs = UiPrefs(File(dataDir, "ui-prefs.json"))

    // 生成媒体输出到公开 Downloads（桌面无权限流程，直接写文件）
    val mediaDir = File(System.getProperty("user.home"), "Downloads/VantaGate/media").apply { mkdirs() }
    MediaProxy.setCacheDir(mediaDir)

    installCrashLogger(engine)

    val vm = GateViewModel(engine, server, dataDir)
    startRemoteSync(engine, server)
    startTokenWatchdog(engine)

    var trayIcon: TrayIcon? = null
    fun cleanup() {
        runCatching { server.stop() }
        runCatching { GateCef.shutdown() }
        runCatching { trayIcon?.let { SystemTray.getSystemTray().remove(it) } }
    }

    application {
        var windowVisible by remember { mutableStateOf(true) }
        val windowState = rememberWindowState(
            size = DpSize(1100.dp, 700.dp),
            position = WindowPosition(Alignment.Center),
        )
        val iconBitmap = remember { loadWindowIcon() }

        // 系统托盘（AWT）：与 Compose 窗口共存
        remember {
            runCatching {
                if (SystemTray.isSupported()) {
                    val image = loadTrayImage() ?: return@runCatching
                    val tray = SystemTray.getSystemTray()
                    // 托盘弹出菜单默认字体在打包的 jlink 运行时里没有中文字形，中文会显示成方框，
                    // 这里显式指定一个带中文的系统字体（微软雅黑优先）。
                    val menuFont = cjkMenuFont()
                    val popup = PopupMenu().apply { font = menuFont }
                    val showItem = MenuItem("显示主窗口").apply { font = menuFont }
                    showItem.addActionListener { windowVisible = true }
                    val startItem = MenuItem("启动网关").apply { font = menuFont }
                    startItem.addActionListener { vm.startGateway() }
                    val stopItem = MenuItem("停止网关").apply { font = menuFont }
                    stopItem.addActionListener { vm.stopGateway() }
                    val exitItem = MenuItem("退出").apply { font = menuFont }
                    exitItem.addActionListener {
                        cleanup()
                        exitApplication()
                    }
                    popup.add(showItem)
                    popup.add(startItem)
                    popup.add(stopItem)
                    popup.addSeparator()
                    popup.add(exitItem)
                    trayIcon = TrayIcon(image, "VantaGate", popup).apply {
                        isImageAutoSize = true
                        addActionListener { windowVisible = true }
                    }
                    tray.add(trayIcon)
                }
            }
            true
        }

        Window(
            onCloseRequest = {
                // 关闭主窗口收到托盘（网关继续跑），从托盘退出才真正结束
                windowVisible = false
            },
            state = windowState,
            visible = windowVisible,
            title = "VantaGate ${Main.VERSION}",
            icon = iconBitmap?.let { BitmapPainter(it) },
        ) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                vm.startAutoRefresh()
                vm.startGateway()
                vm.refreshModels()
            }
            // 原生标题栏切深色（DWM 沉浸式深色模式），与应用深色主题统一。
            androidx.compose.runtime.LaunchedEffect(Unit) {
                applyDarkTitleBar(window)
            }
            AppRoot(vm, dataDir)
        }

        MonitorBubbleWindow(
            engine = engine,
            theme = vm.theme(),
            prefs = prefs,
            visible = vm.state.monitorBubble,
            onClose = { vm.setMonitorBubble(false) },
        )
    }
    cleanup()
}

private fun loadWindowIcon(): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        val stream = Main::class.java.getResourceAsStream("/icon.png") ?: return null
        val image = ImageIO.read(stream) ?: return null
        image.toComposeImageBitmap()
    } catch (_: Exception) {
        null
    }
}

/** 托盘菜单字体：优先选系统里带中文的字体，避免默认字体缺中文字形导致菜单显示方框。 */
private fun cjkMenuFont(): java.awt.Font {
    val candidates = listOf("Microsoft YaHei", "SimSun", "NSimSun", "Microsoft JhengHei", "SimHei")
    val available = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        .availableFontFamilyNames.toSet()
    for (name in candidates) {
        if (name in available) {
            val font = java.awt.Font(name, java.awt.Font.PLAIN, 12)
            if (font.canDisplayUpTo("显示主窗口启动网关停止网关退出") == -1) return font
        }
    }
    return java.awt.Font("Dialog", java.awt.Font.PLAIN, 12)
}

private fun loadTrayImage(): BufferedImage? {
    return try {
        val stream = Main::class.java.getResourceAsStream("/icon.png") ?: return null
        ImageIO.read(stream)
    } catch (_: Exception) {
        null
    }
}

private fun installCrashLogger(engine: Engine) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        runCatching {
            engine.eventLog.error("崩溃", "${thread.name}: ${error.stackTraceToString().take(4000)}")
            engine.eventLog.flush()
        }
        previous?.uncaughtException(thread, error)
    }
}

/**
 * 远程同步 / 心跳（Android GatewayService.startRemoteSync 的桌面版）：
 * 立刻发一次，之后按后台下发的 heartbeat_interval 循环；失败指数退避，
 * 绝不影响网关主流程。
 */
private fun startRemoteSync(engine: Engine, server: GatewayHttpServer) {
    Thread {
        var failures = 0
        var intervalSec = 60
        while (true) {
            try {
                val result = engine.syncRemote(
                    version = Main.VERSION,
                    running = server.isRunning(),
                    port = engine.settings.port,
                )
                if (result != null) {
                    failures = 0
                    val next = result.int("heartbeat_interval")
                    if (next in 10..3600) intervalSec = next
                    engine.applyRemoteConfig(result)
                } else {
                    failures++
                    intervalSec = minOf(60 shl minOf(failures, 5), 300)
                }
            } catch (_: Exception) {
                failures++
                intervalSec = minOf(60 shl minOf(failures, 5), 300)
            }
            var slept = 0
            while (slept < intervalSec * 1000) {
                Thread.sleep(1000)
                slept += 1000
            }
        }
    }.apply { isDaemon = true; name = "vantagate-remote-sync" }.start()
}

/** Token 健康看门狗（Android GatewayService 的 hourly 健康检查）：首检 60s 后，每小时一次。 */
private fun startTokenWatchdog(engine: Engine) {
    Thread {
        Thread.sleep(60_000)
        while (true) {
            runCatching { engine.probeTokenHealth() }
            Thread.sleep(60 * 60 * 1000L)
        }
    }.apply { isDaemon = true; name = "vantagate-token-watchdog" }.start()
}
