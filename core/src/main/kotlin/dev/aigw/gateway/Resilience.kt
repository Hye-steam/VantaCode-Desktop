package dev.aigw.gateway

import java.util.concurrent.ConcurrentHashMap

/**
 * 模型级冷却。
 *
 * 有些上游错误是**模型维度**的（例如「该模型已超限」）：换一个账号仍然撞同一道墙，
 * 换号既浪费一次尝试，又会把账号连续失败计数推高、误伤整个账号。所以按
 * `供应商|模型` 记一段冷却，冷却期内该模型直接跳过，改走其它候选。
 *
 * 与账号冷却一致：只在内存里，重启即清空——冷却本就是瞬态状态。
 */
class ModelCooldownTracker(private val now: () -> Long = System::currentTimeMillis) {

    private val untilMap = ConcurrentHashMap<String, Long>()

    private fun key(providerId: String, model: String) = "$providerId|$model"

    /** 该 (供应商, 模型) 是否正在冷却；到期项顺手清掉。 */
    fun isCooling(providerId: String, model: String): Boolean {
        val key = key(providerId, model)
        val deadline = untilMap[key] ?: return false
        if (now() >= deadline) {
            untilMap.remove(key, deadline)
            return false
        }
        return true
    }

    fun markCooling(providerId: String, model: String, durationMs: Long) {
        if (durationMs <= 0) return
        untilMap[key(providerId, model)] = now() + durationMs
    }

    /** 成功一次即解除该模型的冷却（上游已恢复）。 */
    fun clear(providerId: String, model: String) {
        untilMap.remove(key(providerId, model))
    }

    /** 仍在冷却中的条目：`providerId|model` → 剩余毫秒。供日志与界面展示。 */
    fun snapshot(): Map<String, Long> {
        val now = now()
        val out = LinkedHashMap<String, Long>()
        for ((k, deadline) in untilMap) {
            val remaining = deadline - now
            if (remaining > 0) out[k] = remaining else untilMap.remove(k, deadline)
        }
        return out
    }
}

/**
 * 供应商熔断器。
 *
 * 连续失败若干次后打开（OPEN），期间该供应商不再被选中——上游整体挂掉时，
 * 避免每个请求都去撞一遍、白白拉长延迟。打开满 [openDurationMs] 后转入半开
 * （HALF_OPEN），放请求当探针；连续成功 [halfOpenSuccessCount] 次才真正恢复
 * （CLOSED），中途再失败就立刻重新打开。
 *
 * 只统计**上游/网络**类失败：客户端 4xx 与凭证失效分别由请求本身和账号级禁用处理，
 * 不该让整个供应商背锅。
 */
class ProviderBreaker(
    private val failureThreshold: Int = 5,
    private val openDurationMs: Long = 60_000L,
    private val halfOpenSuccessCount: Int = 2,
    private val now: () -> Long = System::currentTimeMillis,
) {

    enum class State { CLOSED, OPEN, HALF_OPEN }

    private var state = State.CLOSED
    private var failures = 0
    private var halfOpenSuccesses = 0
    private var openedAt = 0L

    /**
     * 该供应商当前是否应被跳过（只读语义，但会把到期的 OPEN 惰性转成 HALF_OPEN）。
     * 选号阶段用它，不要在这里抛异常。
     */
    @Synchronized
    fun isBlocked(): Boolean {
        if (state == State.OPEN && now() - openedAt >= openDurationMs) {
            state = State.HALF_OPEN
            halfOpenSuccesses = 0
            openedAt = now()
        }
        return state == State.OPEN
    }

    @Synchronized
    fun recordSuccess() {
        failures = 0
        if (state == State.HALF_OPEN) {
            if (++halfOpenSuccesses >= halfOpenSuccessCount) {
                state = State.CLOSED
                halfOpenSuccesses = 0
            }
        }
    }

    @Synchronized
    fun recordFailure() {
        failures++
        if (state == State.HALF_OPEN || failures >= failureThreshold) {
            state = State.OPEN
            openedAt = now()
            halfOpenSuccesses = 0
        }
    }

    @Synchronized
    fun currentState(): State = state
}
