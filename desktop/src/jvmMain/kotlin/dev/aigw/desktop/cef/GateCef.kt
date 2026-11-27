package dev.aigw.desktop.cef

import me.friwi.jcefmaven.CefAppBuilder
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefMessageRouter
import org.cef.handler.CefMessageRouterHandler
import org.cef.handler.CefRequestHandler
import java.io.File

/**
 * JCEF 单例管理：首次打开网页功能时才初始化（懒加载），避免拖慢主启动链路。
 * CefApp 全局只建一次，App 退出时统一 dispose；每个页面独立 CefClient/CefBrowser。
 *
 * 移植自 VantaCode Desktop 的 PluginCef（同款 JCEF 152.0.6），安装目录改为
 * ~/.vantagate/jcef，UA 改为桌面 Chrome（见 DesktopWebMode.UA）。
 */
object GateCef {
    private const val TAG = "GateCef"

    @Volatile
    private var cefApp: CefApp? = null
    private val lock = Any()

    /** 是否可用；初始化失败时保持 false，UI 走降级。 */
    @Volatile
    var available: Boolean = false
        private set

    /** 初始化失败原因，供 UI 展示。 */
    @Volatile
    var failure: String? = null
        private set

    val installDir: File
        get() = File(System.getProperty("user.home"), ".vantagate/jcef")

    /** 线程安全地获取 CefApp，失败抛异常由调用方捕获降级。 */
    fun app(): CefApp {
        cefApp?.let { return it }
        return synchronized(lock) {
            cefApp?.let { return@synchronized it }
            val built = try {
                val builder = CefAppBuilder()
                builder.setInstallDir(installDir)
                builder.cefSettings.windowless_rendering_enabled = true // OSR，SwingPanel 必需
                builder.cefSettings.cache_path = File(installDir, "cache").absolutePath
                // 全局桌面 UA：登录页按 UA 分流时出桌面布局（WorkBuddy 出二维码而非微信-only）
                builder.cefSettings.user_agent = DesktopWebMode.UA
                // 登录页按语言分流：JCEF 152 的 CefSettings 已没有 accept_language_list 字段，
                // 语言由 locale 控制（CEF 据此发 Accept-Language）；安卓同款 setZhLanguages("zh-CN,zh")
                builder.cefSettings.locale = "zh-CN"
                builder.setProgressHandler { progress, message ->
                    println("[$TAG] JCEF 安装 $progress%: $message")
                }
                builder.build()
            } catch (e: Throwable) {
                failure = e.message ?: e::class.simpleName
                System.err.println("[$TAG] JCEF 初始化失败：$failure")
                throw e
            }
            cefApp = built
            available = true
            built
        }
    }

    /** 新建一个 CefClient，挂上请求拦截与桥接 handler。 */
    fun newClient(
        requestHandler: CefRequestHandler?,
        bridgeHandler: CefMessageRouterHandler,
    ): CefClient {
        val client = app().createClient()
        if (requestHandler != null) client.addRequestHandler(requestHandler)
        val router = CefMessageRouter.create(
            CefMessageRouter.CefMessageRouterConfig("__aigwBridgeQuery", "__aigwBridgeCancel")
        )
        router.addHandler(bridgeHandler, true)
        client.addMessageRouter(router)
        return client
    }

    /** App 退出时调用。 */
    fun shutdown() {
        runCatching { cefApp?.dispose() }
        cefApp = null
        available = false
    }
}
