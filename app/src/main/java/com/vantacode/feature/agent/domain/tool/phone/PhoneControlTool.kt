package com.vantacode.feature.agent.domain.tool.phone

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vantacode.core.util.FileLogger
import com.vantacode.feature.agent.domain.model.AgentImage
import com.vantacode.feature.agent.domain.shizuku.ShizukuManager
import com.vantacode.feature.agent.domain.shizuku.ShizukuState
import com.vantacode.feature.agent.domain.tool.AgentTool
import com.vantacode.feature.agent.domain.tool.ParameterType
import com.vantacode.feature.agent.domain.tool.PendingToolPermission
import com.vantacode.feature.agent.domain.tool.ToolCapability
import com.vantacode.feature.agent.domain.tool.ToolParameter
import com.vantacode.feature.agent.domain.tool.ToolPermissionPolicy
import com.vantacode.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import javax.inject.Inject

/**
 * 手机屏幕控制工具：截屏、读 UI 树、点击、滑动、输入文字、按键、启动 App。
 *
 * 全部通过 Shizuku 执行 adb 级命令（`screencap` / `uiautomator dump` / `input`），
 * 让 AI 能「看到并操作」手机屏幕。
 *
 * 参数 `action` 决定操作类型：
 * - capture: 截取当前屏幕，返回图片
 * - dump: 获取当前界面的 UI 树（文本格式）
 * - tap: 在坐标 (x, y) 点击
 * - swipe: 从 (x1,y1) 滑到 (x2,y2)
 * - type: 输入文字（需先聚焦输入框）
 * - key: 按键（back/home/recents/volume_up/volume_down/power/enter 等）
 * - launch: 启动指定包名的 App
 * - current: 获取当前前台 App 包名和 Activity
 */
class PhoneControlTool @Inject constructor(
    private val shizukuManager: ShizukuManager,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : AgentTool() {
    private companion object {
        const val TAG = "PhoneControlTool"
        const val DEFAULT_TIMEOUT_SECONDS = 30L
    }

    override val name = "phoneControl"

    override val description = (
        "手机屏幕控制：截屏看当前界面、读 UI 树、点击/滑动/输入/按键、启动 App。" +
            "需 Shizuku 已授权。操作前先 capture 或 dump 确认当前界面。"
        )

    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(ToolCapability.EXECUTE_COMMANDS)

    override val parameters: Map<String, ToolParameter> = mapOf(
        "action" to ToolParameter(
            name = "action",
            type = ParameterType.STRING,
            description = "操作类型：capture=截屏, dump=读UI树, tap=点击, swipe=滑动, type=输入文字, key=按键, launch=启动App, current=当前App",
            required = true,
            enum = listOf("capture", "dump", "tap", "swipe", "type", "key", "launch", "current")
        ),
        "x" to ToolParameter(name = "x", type = ParameterType.INTEGER, description = "X 坐标（tap/swipe 用）", required = false),
        "y" to ToolParameter(name = "y", type = ParameterType.INTEGER, description = "Y 坐标（tap/swipe 用）", required = false),
        "x2" to ToolParameter(name = "x2", type = ParameterType.INTEGER, description = "滑动终点 X 坐标（swipe 用）", required = false),
        "y2" to ToolParameter(name = "y2", type = ParameterType.INTEGER, description = "滑动终点 Y 坐标（swipe 用）", required = false),
        "text" to ToolParameter(name = "text", type = ParameterType.STRING, description = "要输入的文字（type 用）", required = false),
        "key" to ToolParameter(
            name = "key", type = ParameterType.STRING,
            description = "按键名（key 用）：back/home/recents/volume_up/volume_down/power/enter/delete",
            required = false
        ),
        "package" to ToolParameter(name = "package", type = ParameterType.STRING, description = "App 包名（launch 用）", required = false),
        "duration" to ToolParameter(name = "duration", type = ParameterType.INTEGER, description = "滑动持续时间毫秒（swipe 用），默认 300", required = false),
        "enable" to ToolParameter(name = "enable", type = ParameterType.BOOLEAN, description = "capsule 用：true 开启/false 关闭悬浮胶囊", required = false)
    )

    override fun buildPermissionRequest(
        callId: String,
        args: Map<String, JsonElement>,
        argsPreview: String
    ): PendingToolPermission {
        val action = args["action"]?.jsonPrimitive?.contentOrNull ?: "未知"
        val summary = when (action) {
            "capture" -> "截取当前屏幕"
            "dump" -> "读取当前界面 UI 树"
            "tap" -> "点击 (${args["x"]}, ${args["y"]})"
            "swipe" -> "滑动 (${args["x"]},${args["y"]}) → (${args["x2"]},${args["y2"]})"
            "type" -> "输入文字: ${args["text"]}"
            "key" -> "按键: ${args["key"]}"
            "launch" -> "启动 App: ${args["package"]}"
            "current" -> "获取当前前台 App"
            else -> action
        }
        return PendingToolPermission(
            id = callId,
            toolName = name,
            title = "手机屏幕操作",
            summary = summary,
            details = "通过 Shizuku 在手机上执行。$summary",
            argsPreview = argsPreview
        )
    }

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        if (shizukuManager.state.value != ShizukuState.READY) {
            return ToolResult.Error("Shizuku 未就绪，请先在 Shizuku 应用中启动服务并授权", "SHIZUKU_NOT_READY")
        }

        return when (args["action"]?.jsonPrimitive?.contentOrNull) {
            "capture" -> doCapture()
            "dump" -> doDump()
            "tap" -> doTap(args)
            "swipe" -> doSwipe(args)
            "type" -> doType(args)
            "key" -> doKey(args)
            "launch" -> doLaunch(args)
            "current" -> doCurrent()
            else -> ToolResult.Error("未知操作，请指定 action: capture/dump/tap/swipe/type/key/launch/current")
        }
    }

    // ─── 截屏 ───

    private suspend fun doCapture(): ToolResult {
        // 先存到文件，再用 App 自己的 IO 读回来——不走 Shizuku 文本输出（大图 base64 会超缓冲）
        val tmpPath = "/sdcard/vc_screen.png"
        val saveResult = shizukuManager.runCommand("screencap -p $tmpPath", 15_000)
        if (saveResult.exitCode != 0) return ToolResult.Error("截屏失败: ${saveResult.output}")

        // 直接读文件（App 有 READ_EXTERNAL_STORAGE）
        val bytes = try {
            java.io.File(tmpPath).readBytes()
        } catch (e: Exception) {
            return ToolResult.Error("读取截屏文件失败: ${e.message}")
        }
        if (bytes.isEmpty()) return ToolResult.Error("截屏文件为空")

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return ToolResult.Error("截屏图片解析失败(${bytes.size} 字节)")

        val image = AgentImage(
            base64Data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP),
            mimeType = "image/png"
        )

        return ToolResult.Success(
            data = buildJsonObject {
                put("width", bitmap.width)
                put("height", bitmap.height)
                put("message", "截屏成功，已附图")
            },
            images = listOf(image)
        )
    }

    // ─── UI 树 ───

    private suspend fun doDump(): ToolResult {
        // uiautomator dump
        val tmpPath = "/sdcard/vc_ui.xml"
        val result = shizukuManager.runCommand("uiautomator dump $tmpPath", 15_000)
        if (result.exitCode != 0) return ToolResult.Error("UI dump 失败: ${result.output}")

        val readResult = shizukuManager.runCommand("cat $tmpPath", 10_000)
        shizukuManager.runCommand("rm -f $tmpPath", 5_000)

        val xml = readResult.output
        if (xml.isBlank()) return ToolResult.Error("UI dump 内容为空")

        // 简化 XML 为易读格式：提取节点的关键属性
        val simplified = simplifyUiTree(xml)
        return ToolResult.Success(JsonPrimitive(simplified))
    }

    /** 把 uiautomator 的 XML 压缩成 AI 易读的节点列表。 */
    private fun simplifyUiTree(xml: String): String {
        val sb = StringBuilder()
        val nodeRegex = Regex("""<node[^>]*?text="([^"]*)"[^>]*?resource-id="([^"]*)"[^>]*?class="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*?""")
        // 用更简单的方式：按行提取关键属性
        val lines = xml.replace("><", ">\n<").split("\n")
        for (line in lines) {
            if (!line.contains("<node")) continue
            val text = extractAttr(line, "text")
            val resId = extractAttr(line, "resource-id")
            val desc = extractAttr(line, "content-desc")
            val bounds = extractAttr(line, "bounds")
            val clazz = extractAttr(line, "class")
            val clickable = extractAttr(line, "clickable") == "true"

            if (text.isBlank() && desc.isBlank() && resId.isBlank()) continue

            val label = buildString {
                if (text.isNotBlank()) append("\"$text\" ")
                if (desc.isNotBlank()) append("[$desc] ")
                if (resId.isNotBlank()) append("id=${resId.substringAfterLast("/")} ")
                append(clazz.substringAfterLast("."))
                if (clickable) append(" (clickable)")
                append(" @ $bounds")
            }
            sb.appendLine(label)
        }
        return if (sb.isEmpty()) xml.take(4000) else sb.toString()
    }

    private fun extractAttr(line: String, attr: String): String {
        val regex = Regex("""$attr="([^"]*)"""")
        return regex.find(line)?.groupValues?.get(1) ?: ""
    }

    // ─── 交互操作 ───

    private suspend fun doTap(args: Map<String, JsonElement>): ToolResult {
        val x = args["x"]?.jsonPrimitive?.longOrNull ?: return ToolResult.Error("缺少参数 x")
        val y = args["y"]?.jsonPrimitive?.longOrNull ?: return ToolResult.Error("缺少参数 y")
        val result = shizukuManager.runCommand("input tap $x $y", 10_000)
        return if (result.exitCode == 0) {
            ToolResult.Success(JsonPrimitive("已点击 ($x, $y)"))
        } else {
            ToolResult.Error("点击失败: ${result.output}")
        }
    }

    private suspend fun doSwipe(args: Map<String, JsonElement>): ToolResult {
        val x = args["x"]?.jsonPrimitive?.longOrNull ?: return ToolResult.Error("缺少参数 x")
        val y = args["y"]?.jsonPrimitive?.longOrNull ?: return ToolResult.Error("缺少参数 y")
        val x2 = args["x2"]?.jsonPrimitive?.longOrNull ?: return ToolResult.Error("缺少参数 x2")
        val y2 = args["y2"]?.jsonPrimitive?.longOrNull ?: return ToolResult.Error("缺少参数 y2")
        val dur = args["duration"]?.jsonPrimitive?.longOrNull ?: 300L
        val result = shizukuManager.runCommand("input swipe $x $y $x2 $y2 $dur", 10_000)
        return if (result.exitCode == 0) {
            ToolResult.Success(JsonPrimitive("已滑动 ($x,$y) → ($x2,$y2)"))
        } else {
            ToolResult.Error("滑动失败: ${result.output}")
        }
    }

    private suspend fun doType(args: Map<String, JsonElement>): ToolResult {
        val text = args["text"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("缺少参数 text")
        // input text 对空格和特殊字符需要转义
        val escaped = text.replace(" ", "%s").replace("'", "\\'").replace("&", "\\&").replace("<", "\\<").replace(">", "\\>")
        val result = shizukuManager.runCommand("input text '$escaped'", 15_000)
        return if (result.exitCode == 0) {
            ToolResult.Success(JsonPrimitive("已输入: $text"))
        } else {
            ToolResult.Error("输入失败: ${result.output}")
        }
    }

    private suspend fun doKey(args: Map<String, JsonElement>): ToolResult {
        val keyName = args["key"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("缺少参数 key")
        val keyCode = when (keyName.lowercase()) {
            "back" -> "4"
            "home" -> "3"
            "recents" -> "187"
            "volume_up" -> "24"
            "volume_down" -> "25"
            "power" -> "26"
            "enter" -> "66"
            "delete" -> "67"
            "tab" -> "61"
            "escape" -> "111"
            else -> keyName
        }
        val result = shizukuManager.runCommand("input keyevent $keyCode", 10_000)
        return if (result.exitCode == 0) {
            ToolResult.Success(JsonPrimitive("已按键: $keyName"))
        } else {
            ToolResult.Error("按键失败: ${result.output}")
        }
    }

    // ─── App 操作 ───

    private suspend fun doLaunch(args: Map<String, JsonElement>): ToolResult {
        val pkg = args["package"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("缺少参数 package")
        // 先解析该包的启动 Activity 名（不硬编码 .MainActivity）
        val resolveResult = shizukuManager.runCommand(
            "cmd package resolve-activity --brief -c android.intent.category.LAUNCHER $pkg | tail -1",
            10_000
        )
        val activity = resolveResult.output.trim().let {
            if (it.contains("/")) it.substringAfterLast("/").trim() else ""
        }
        // 用 am start 启动
        val cmd = if (activity.isNotBlank() && activity != "No activity found") {
            "am start -n $pkg/$activity"
        } else {
            "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $pkg"
        }
        val result = shizukuManager.runCommand(cmd, 15_000)
        return if (result.exitCode == 0 && !result.output.contains("Error")) {
            ToolResult.Success(JsonPrimitive("已启动: $pkg"))
        } else {
            ToolResult.Error("启动失败: ${result.output}")
        }
    }

    private suspend fun doCurrent(): ToolResult {
        val result = shizukuManager.runCommand("dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | head -1", 10_000)
        val line = result.output.trim()
        return if (line.isNotBlank()) {
            ToolResult.Success(JsonPrimitive(line))
        } else {
            ToolResult.Error("无法获取当前 Activity: ${result.output}")
        }
    }

}
