package com.vantacode.feature.agent.domain.container

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 首次启动时把随包内置的配置种子释放到私有目录，让新安装直接带上原有的
 * 供应商、模型设置、技能、子代理、记忆与提示词自定义。
 *
 * 种子来自 `assets/seed/`：
 * - `db/` → `databases/`（聊天历史与会话表；当前库不存在或还是空库时才落盘）
 * - `prefs/` → `shared_prefs/`（各开关的轻量配置）
 * - `datastore/` → `files/datastore/`（主题、模型、容器等设置）
 * - `aicode/` → `files/aicode/`（`~/.aicode`：agents / skills / memory / prompts.custom 等）
 *
 * 必须是纯静态入口、并且要在 `Application.super.onCreate()` **之前**调用：
 * Hilt 的字段注入在 super.onCreate() 内完成，被注入的仓储有在构造时打开 Room 的，
 * 一旦它们先跑，databases/ 下就会出现一个空库，种子再也落不进去。
 *
 * 只补齐缺失文件：已有同名文件一律保留，避免把用户后来的改动盖回旧快照。
 * 执行结果写入独立 SharedPreferences，只做一次；卸载重装后种子会重新生效。
 */
class SeedInstaller private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SeedInstaller"
        private const val PREFS_NAME = "seed_state"
        private const val KEY_INSTALLED = "installed_v1"
        private const val DB_NAME = "aicode_agent_db"
        private const val SEED_ROOT = "seed"

        /** 低于该大小的库视为空库（Room 建表后空库约 4-200 KB），允许种子覆盖。 */
        private const val EMPTY_DB_MAX_BYTES = 1024L * 1024L

        /** 释放种子；供 [android.app.Application.onCreate] 在 super 之前调用。幂等。 */
        fun install(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_INSTALLED, false)) return

            val hasSeed = runCatching { context.assets.list(SEED_ROOT)?.isNotEmpty() == true }
                .getOrDefault(false)
            if (!hasSeed) {
                prefs.edit().putBoolean(KEY_INSTALLED, true).apply()
                return
            }

            runCatching {
                val installer = SeedInstaller(context)
                val dataDir = context.applicationInfo.dataDir
                val dbFile = context.getDatabasePath(DB_NAME)
                val dbDir = dbFile.parentFile
                // 空库（刚被 Room 建出来、还没数据）允许被种子覆盖；有数据的库一律不碰。
                val dbWorthSeeding = !dbFile.exists() || dbFile.length() < EMPTY_DB_MAX_BYTES
                if (dbDir != null && dbWorthSeeding) {
                    // 覆盖式：旧的 -wal / -shm 必须清掉，否则会与种子主库对不上。
                    listOf("", "-wal", "-shm").forEach { suffix ->
                        runCatching { File(dbDir, DB_NAME + suffix).delete() }
                    }
                    installer.copyAssetDir("$SEED_ROOT/db", dbDir, overwrite = true)
                }
                installer.copyAssetDir("$SEED_ROOT/prefs", File(dataDir, "shared_prefs"), overwrite = false)
                installer.copyAssetDir("$SEED_ROOT/datastore", File(context.filesDir, "datastore"), overwrite = false)
                installer.copyAssetDir("$SEED_ROOT/aicode", File(context.filesDir, "aicode"), overwrite = false)
                prefs.edit().putBoolean(KEY_INSTALLED, true).apply()
                Log.i(TAG, "内置配置种子已释放")
            }.onFailure { e ->
                Log.w(TAG, "释放内置配置种子失败，按空配置继续", e)
            }
        }
    }

    /** 递归复制 assets 下的目录；[overwrite] 为 false 时跳过已存在的同名文件。 */
    private fun copyAssetDir(assetPath: String, target: File, overwrite: Boolean) {
        val children = runCatching { context.assets.list(assetPath) }.getOrNull() ?: return
        if (children.isEmpty()) {
            copyAssetFile(assetPath, target, overwrite)
            return
        }
        if (!target.exists() && !target.mkdirs()) return
        children.forEach { child ->
            copyAssetDir("$assetPath/$child", File(target, child), overwrite)
        }
    }

    private fun copyAssetFile(assetPath: String, target: File, overwrite: Boolean) {
        if (!overwrite && target.exists()) return
        target.parentFile?.mkdirs()
        runCatching {
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }.onFailure { e ->
            Log.w(TAG, "复制种子文件失败: $assetPath", e)
        }
    }
}
