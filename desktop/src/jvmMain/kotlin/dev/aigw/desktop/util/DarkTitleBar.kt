package dev.aigw.desktop.util

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.platform.win32.Dwmapi
import com.sun.jna.platform.win32.WinDef.HWND
import java.awt.Window
import javax.swing.SwingUtilities

/**
 * 把 Windows 原生标题栏切成深色（DWM 沉浸式深色模式），与应用深色主题统一。
 *
 * Compose 的 Window 标题栏是系统原生的，浅色模式下是白条，与下面的深色界面
 * 割裂。Windows 10 1809+ 支持 DWMWA_USE_IMMERSIVE_DARK_MODE：标题栏变深、
 * 文字变白，原生按钮、拖拽、贴靠、缩放行为全部保留。
 *
 * 只在 Windows 上生效；调用失败（比如旧系统）就静默保持原样，绝不影响启动。
 */
fun applyDarkTitleBar(window: Window) {
    if (!System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)) return
    // 等窗口真正显示（拿到 HWND）后再调 DWM。
    Thread {
        Thread.sleep(500)
        SwingUtilities.invokeLater {
            runCatching { setImmersiveDarkMode(window) }
        }
    }.apply { isDaemon = true; name = "vantagate-dark-titlebar" }.start()
}

private fun setImmersiveDarkMode(window: Window) {
    val ptr = Native.getWindowPointer(window) ?: return
    val hwnd = HWND(ptr)
    val value = Memory(4).apply { setInt(0, 1) }
    // Win10 20H1+ 用 20；1809–1909 用 19。
    val hr = Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, 20, value, 4)
    if (hr.intValue() != 0) {
        Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, 19, value, 4)
    }
}
