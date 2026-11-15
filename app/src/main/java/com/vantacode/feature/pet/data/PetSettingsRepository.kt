package com.vantacode.feature.pet.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vantacode.core.datastore.preferencesCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.petDataStore by preferencesDataStore(
    name = "pet_prefs",
    corruptionHandler = preferencesCorruptionHandler
)

/** 桌宠左上角在可用区域内的归一化位置，0..1。存比例而不是像素，换分辨率/折叠屏不会飞出屏幕。 */
data class PetPosition(val xRatio: Float, val yRatio: Float)

/**
 * 桌宠的两个持久化状态：开关与位置。
 *
 * 位置只在拖动松手时写一次，拖动过程中不落盘（避免高频 IO 掉帧）。
 * DataStore 用法与 [com.vantacode.feature.settings.data.repository.ScreenOnSettingsRepository] 一致。
 */
@Singleton
class PetSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("pet_enabled")
        val X_RATIO_KEY = floatPreferencesKey("pet_x_ratio")
        val Y_RATIO_KEY = floatPreferencesKey("pet_y_ratio")
    }

    /** 桌宠开关，默认开启（用户可在设置页关掉，关掉后覆层不进组合）。 */
    val enabledFlow: Flow<Boolean> = context.petDataStore.data.map { it[ENABLED_KEY] ?: true }

    /** 上次拖到的位置；没有记录时为 null，由界面层落到右下角。 */
    val positionFlow: Flow<PetPosition?> = context.petDataStore.data.map { prefs ->
        val x = prefs[X_RATIO_KEY]
        val y = prefs[Y_RATIO_KEY]
        if (x == null || y == null) null else PetPosition(x, y)
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.petDataStore.edit { it[ENABLED_KEY] = enabled }
    }

    suspend fun savePosition(xRatio: Float, yRatio: Float) {
        context.petDataStore.edit {
            it[X_RATIO_KEY] = xRatio.coerceIn(0f, 1f)
            it[Y_RATIO_KEY] = yRatio.coerceIn(0f, 1f)
        }
    }
}
