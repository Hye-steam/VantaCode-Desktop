package com.vantacode.feature.agent.domain.subagent

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 子代理事件与副作用去重。
 *
 * 放在进程级单例里而不是 ViewModel 里，是因为同一个 AIAgentViewModel 可能被创建出多个实例
 * （Activity 重建、不同作用域各拿一个），每个实例各自订阅事件总线。去重状态一旦跟着实例走，
 * 每个实例都会放行同一条事件，于是同一条执行结果被写进群聊三遍。
 *
 * 职责有两层：
 * 1. [shouldHandleTerminal]：挡住同一会话短时间内的重复终态事件。workflow 收尾在出错时曾经
 *    Failed 紧跟 Completed，两条都会进来；另有并发实例各自处理的可能。
 * 2. [claimMessage]：写库前的原子占位，保证同一条消息在整个进程里只写一次。
 */
@Singleton
class SubAgentEventDeduper @Inject constructor() {

    companion object {
        /** 同一会话的两个终态事件若落在这个窗口内，只认第一个。 */
        const val TERMINAL_WINDOW_MS = 1_500L
        /** 记录上限：只用于防止长期累积，超了整体清空。 */
        private const val MAX_ENTRIES = 512
    }

    private val terminalAt = ConcurrentHashMap<String, Long>()
    private val writtenMessageKeys = ConcurrentHashMap.newKeySet<String>()

    /**
     * 该会话的终态事件是否应该被处理。返回 false 表示这是短时间内的重复事件，直接丢弃。
     */
    fun shouldHandleTerminal(subSessionId: String, now: Long = System.currentTimeMillis()): Boolean {
        if (subSessionId.isBlank()) return true
        val last = terminalAt[subSessionId]
        if (last != null && now - last < TERMINAL_WINDOW_MS) return false
        terminalAt[subSessionId] = now
        if (terminalAt.size > MAX_ENTRIES) terminalAt.clear()
        return true
    }

    /**
     * 原子占位：true 表示这次由调用方写入，false 表示已经有人写过了。
     * key 建议用 `会话id:内容哈希`，既挡住重复事件，也保留同一会话不同任务各自的结果。
     */
    fun claimMessage(key: String): Boolean {
        if (key.isBlank()) return true
        val added = writtenMessageKeys.add(key)
        if (writtenMessageKeys.size > MAX_ENTRIES * 4) writtenMessageKeys.clear()
        return added
    }
}
