package dev.aigw.desktop.util

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.win32.StdCallLibrary

/**
 * dwmapi.dll 的 JNA 绑定：JNA 没有内置 DWM 接口，这里自己声明。
 *
 * 只用 DwmSetWindowAttribute 一个函数（DWMWA_USE_IMMERSIVE_DARK_MODE，
 * 20 = Win10 20H1+，19 = 1809–1909）。
 */
interface Dwmapi : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: HWND, dwAttribute: Int, pvAttribute: Pointer, cbAttribute: Int): HRESULT

    companion object {
        val INSTANCE: Dwmapi = Native.load("dwmapi", Dwmapi::class.java)
    }
}
