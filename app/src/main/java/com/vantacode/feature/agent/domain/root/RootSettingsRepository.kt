package com.vantacode.feature.agent.domain.root

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Root 能力开关。
 *
 * 默认**关闭**。用户不开启时，[RootManager.execute] 直接抛 [RootDisabledException]，
 * Root 工具不会被注册到工具表里，其余功能完全不受影响——这是「不开启也不影响正常使用」的实现方式。
 *
 * 分开存两个开关是刻意的：
 *   - [isEnabled]：总开关，控制 Root 工具是否可用
 *   - [isSkipConfirm]：开启后 root 命令不再逐次弹确认框（默认仍弹）
 * 这样用户可以「用 root 但要确认」，也可以「用 root 且不再确认」。
 */
@Singleton
class RootSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Root 总开关，默认关闭。 */
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * 是否跳过逐次确认，默认关闭（即默认每次都问）。
     *
     * 只在总开关打开时有意义。
     */
    fun isSkipConfirm(): Boolean = prefs.getBoolean(KEY_SKIP_CONFIRM, false)

    fun setSkipConfirm(skip: Boolean) {
        prefs.edit().putBoolean(KEY_SKIP_CONFIRM, skip).apply()
    }

    /**
     * 是否允许 root 命令访问其他应用的私有数据（`/data/data` 等）。
     *
     * 单独一个开关：这是 root 能力里风险最高的一档，用户可能想要 root 但不想要这个。
     * 默认关闭。
     */
    fun isPrivateDataAllowed(): Boolean = prefs.getBoolean(KEY_PRIVATE_DATA, false)

    fun setPrivateDataAllowed(allowed: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVATE_DATA, allowed).apply()
    }

    private companion object {
        const val PREFS = "root_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_SKIP_CONFIRM = "skip_confirm"
        const val KEY_PRIVATE_DATA = "private_data"
    }
}
