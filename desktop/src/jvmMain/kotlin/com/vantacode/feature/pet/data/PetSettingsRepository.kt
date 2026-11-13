/*
 * VantaCode Desktop — 桌宠（Vanta）开关与位置持久化。
 * Copyright (C) 2026 VantaCode contributors. GPL-3.0.
 */
package com.vantacode.feature.pet.data

import com.vantacode.core.datastore.preferencesCorruptionHandler
import com.vantacode.core.datastore.preferencesDataStore
import com.vantacode.core.platform.DesktopContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val DesktopContext.petDataStore by preferencesDataStore(
    name = "pet_prefs",
    corruptionHandler = preferencesCorruptionHandler
)

/**
 * 桌宠设置：是否显示 + 宠物在内容区的位置（相对可移动范围的比例，0..1）。
 * 默认显示——桌宠是 VantaCode 的标志性陪伴，用户可随时关掉。
 */
@Singleton
class PetSettingsRepository @Inject constructor(
    private val context: DesktopContext
) {
    companion object {
        private val ENABLED_KEY = booleanPreferencesKey("pet_enabled")
        private val X_KEY = floatPreferencesKey("pet_x")
        private val Y_KEY = floatPreferencesKey("pet_y")
        const val DEFAULT_ENABLED = true
        const val DEFAULT_X = 0.86f
        const val DEFAULT_Y = 0.78f
    }

    val enabledFlow: Flow<Boolean> = context.petDataStore.data.map { it[ENABLED_KEY] ?: DEFAULT_ENABLED }

    /** 宠物位置（x/y 为可拖动范围的比例；只依赖这个范围，容器尺寸变了也不会跑出屏幕）。 */
    val positionFlow: Flow<Pair<Float, Float>> = context.petDataStore.data.map {
        (it[X_KEY] ?: DEFAULT_X).coerceIn(0f, 1f) to (it[Y_KEY] ?: DEFAULT_Y).coerceIn(0f, 1f)
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.petDataStore.edit { it[ENABLED_KEY] = enabled }
    }

    suspend fun savePosition(xFraction: Float, yFraction: Float) {
        context.petDataStore.edit {
            it[X_KEY] = xFraction.coerceIn(0f, 1f)
            it[Y_KEY] = yFraction.coerceIn(0f, 1f)
        }
    }
}
