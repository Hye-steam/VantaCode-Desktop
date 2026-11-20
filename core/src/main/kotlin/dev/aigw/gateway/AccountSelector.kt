package dev.aigw.gateway

import java.util.concurrent.atomic.AtomicInteger

/**
 * 多账号选择策略。
 *
 * 排序发生在候选过滤之后、真正发起请求之前，只影响「先试哪个账号」，
 * 不影响故障转移——前面失败照样往后走。
 *
 * | 策略 | 适用场景 |
 * |---|---|
 * | ORDER | 只有一个主力账号，其余备份 |
 * | ROTATE | 账号额度相近，摊薄单号压力 |
 * | USAGE | 额度差距大，先用多的 |
 * | PACE | 防某个号被很快烧光，优先用消耗慢的 |
 *
 * 免费池这类「免登录占位账号」不参与轮转（只有一个），自然退化成 ORDER。
 */
object AccountSelector {

    /** 每策略一个游标，ROTATE 靠它推进。 */
    private val cursors = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()

    /**
     * 按策略排序候选。
     *
     * @param routes    已过滤掉冷却/熔断的候选
     * @param key       分组键（一般是模型名），轮转游标按它隔离
     * @param strategy  [Settings.accountStrategy]，未知值按 ORDER 处理
     * @param usageOf   取账号的「已消耗量」用于 PACE/USAGE，返回 null 表示未知
     */
    fun order(
        routes: List<Engine.Route>,
        key: String,
        strategy: String,
        remainOf: (Engine.Route) -> Long?,
        usageOf: (Engine.Route) -> Long,
    ): List<Engine.Route> {
        if (routes.size <= 1) return routes
        return when (strategy.uppercase()) {
            "ROTATE" -> rotate(routes, key)
            "USAGE" -> routes.sortedByDescending { remainOf(it) ?: Long.MAX_VALUE }
            "PACE" -> routes.sortedBy { usageOf(it) }
            "ORDER" -> routes
            // 未知值不猜：按添加顺序最不意外
            else -> routes
        }
    }

    /**
     * 轮转：每次把游标指向的账号提到最前，下次换下一个。
     *
     * 游标按 [key] 独立，避免模型 A 的轮转影响模型 B 的起点。
     */
    private fun rotate(routes: List<Engine.Route>, key: String): List<Engine.Route> {
        val cursor = cursors.computeIfAbsent(key) { AtomicInteger(0) }
        val start = Math.floorMod(cursor.getAndIncrement(), routes.size)
        if (start == 0) return routes
        return routes.drop(start) + routes.take(start)
    }

    /** 清掉轮转游标（账号增删后调用，避免游标越界到不存在的下标）。 */
    fun reset() = cursors.clear()
}
