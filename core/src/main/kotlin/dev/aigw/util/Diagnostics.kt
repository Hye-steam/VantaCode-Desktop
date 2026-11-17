package dev.aigw.util

/**
 * Provider 与网关各层共用的日志出口。Engine 创建时接到 EventLog 上；
 * 没有安装时静默丢弃，:core 单独跑 CLI 也不会因此报错。
 */
object Diagnostics {

    @Volatile
    private var sink: ((level: String, tag: String, message: String) -> Unit)? = null

    fun install(target: ((level: String, tag: String, message: String) -> Unit)?) {
        sink = target
    }

    fun debug(tag: String, message: String) = emit("DEBUG", tag, message)

    fun info(tag: String, message: String) = emit("INFO", tag, message)

    fun warn(tag: String, message: String) = emit("WARN", tag, message)

    fun error(tag: String, message: String) = emit("ERROR", tag, message)

    private fun emit(level: String, tag: String, message: String) {
        sink?.invoke(level, tag, message)
    }
}
