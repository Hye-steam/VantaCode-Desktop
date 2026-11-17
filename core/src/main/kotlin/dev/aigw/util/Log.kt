package dev.aigw.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Log {
    private val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")

    @Volatile var level: Int = 1 // 0=debug 1=info 2=warn 3=error

    fun debug(msg: String) = log(0, "D", msg)
    fun info(msg: String) = log(1, "I", msg)
    fun warn(msg: String) = log(2, "W", msg)
    fun error(msg: String) = log(3, "E", msg)

    private fun log(l: Int, tag: String, msg: String) {
        if (l >= level) println("${LocalDateTime.now().format(fmt)} $tag $msg")
    }
}
