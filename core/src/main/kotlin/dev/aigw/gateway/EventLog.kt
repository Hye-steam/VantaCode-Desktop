package dev.aigw.gateway

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.aigw.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 运行日志：内存环形缓冲 + 文件持久化，只保留最近 24 小时。
 * 网关每次启动开启新会话，上一次运行的记录会被清掉。
 */
class EventLog(private val dir: File) {

    enum class Level { DEBUG, INFO, SUCCESS, WARN, ERROR }

    data class Entry(
        val time: Long,
        val level: String,
        val tag: String,
        val message: String,
    )

    private val gson = Gson()
    private val file = File(dir, "events.json")
    private val entries = ArrayDeque<Entry>()
    private val cap = 3000
    private var lastFlushAt = 0L

    init {
        dir.mkdirs()
        load()
    }

    fun debug(tag: String, message: String) = add(Level.DEBUG, tag, message)

    fun info(tag: String, message: String) = add(Level.INFO, tag, message)

    fun success(tag: String, message: String) = add(Level.SUCCESS, tag, message)

    fun warn(tag: String, message: String) = add(Level.WARN, tag, message)

    fun error(tag: String, message: String) = add(Level.ERROR, tag, message)

    @Synchronized
    fun add(level: Level, tag: String, message: String) {
        val text = if (message.length > MAX_MESSAGE) message.take(MAX_MESSAGE) + "…（已截断）" else message
        entries.addLast(Entry(System.currentTimeMillis(), level.name, tag, text))
        while (entries.size > cap) entries.removeFirst()
        flushIfDue()
    }

    /** 网关启动：开启新会话，丢弃上一次运行的记录。 */
    @Synchronized
    fun beginSession() {
        entries.clear()
        persist()
        add(Level.INFO, "网关", "会话开始")
    }

    @Synchronized
    fun snapshot(): List<Entry> {
        val cutoff = cutoff()
        return entries.filter { it.time >= cutoff }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        persist()
    }

    /** 立即落盘：崩溃前必须落盘，否则日志随进程一起丢。 */
    @Synchronized
    fun flush() {
        persist()
    }

    @Synchronized
    fun exportText(): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return entries.joinToString("\n") { entry ->
            "${format.format(Date(entry.time))} [${entry.level}] [${entry.tag}] ${entry.message}"
        }
    }

    private fun cutoff(): Long = System.currentTimeMillis() - 24L * 3600 * 1000

    private fun flushIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastFlushAt < 1000) return
        persist()
    }

    private fun persist() {
        lastFlushAt = System.currentTimeMillis()
        runCatching {
            val tmp = File(dir, "events.json.tmp")
            tmp.writeText(gson.toJson(entries.toList()))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }.onFailure { Log.warn("日志落盘失败: ${it.message}") }
    }

    private fun load() {        runCatching {
            if (!file.exists()) return
            val type = TypeToken.getParameterized(List::class.java, Entry::class.java).type
            val list: List<Entry> = gson.fromJson(file.readText(), type)
            val cutoff = cutoff()
            entries.addAll(list.filter { it.time >= cutoff })
            while (entries.size > cap) entries.removeFirst()
        }.onFailure { Log.warn("日志加载失败: ${it.message}") }
    }

    private companion object {
        /** 单条日志上限：上游原始报错、堆栈可能很长，避免一条拖垮落盘和列表。 */
        const val MAX_MESSAGE = 4000
    }
}
