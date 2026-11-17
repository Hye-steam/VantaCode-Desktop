package dev.aigw.gateway

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.util.Log
import dev.aigw.util.firstString
import dev.aigw.util.objOrNull
import java.net.URLEncoder
/**
 * 远程后台客户端（对应「AI 网关后台」的 App API）。
 *
 * 上游把 GET 参数 / POST 表单 / POST JSON 都当作同一套参数解析，这里统一走
 * `GET ?action=...&key=...`：心跳等请求体量小、无敏感内容，且便于后台按 URL 排障。
 * 只有工单改用 `multipart/form-data` 文件上传（后台的图片通道只认 `images[]` 文件字段）。
 */
class RemoteAdminClient(
    private val apiBase: String = DEFAULT_API_BASE,
    private val appKey: String = DEFAULT_APP_KEY,
) {

    /** 通用调用结果。[data] 为响应里的 data 对象（缺失时为空对象）。 */
    data class Result(val ok: Boolean, val code: Int, val message: String, val data: JsonObject) {
        fun string(key: String): String = data.firstString(key)
        fun int(key: String): Int = data.get(key)?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        fun bool(key: String): Boolean = data.get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
    }

    // ------------------------------------------------------------------ 密钥校验

    /**
     * 校验密钥并刷新域名列表。
     *
     * 返回的 `domains` 是后台「域名健康」里已启用的 API 地址（主域名在前），
     * 客户端应缓存它，请求失败时按顺序切换。
     */
    fun verify(): Result = call("verify")

    // ------------------------------------------------------------------ 心跳

    /**
     * 心跳：上报设备状态，取回心跳间隔与（可能的）最新域名列表。
     *
     * [failedDomain] 是本次切换前请求失败的域名，后台累计到「域名健康」，
     * 同一域名 60 秒内只计一次。
     */
    fun heartbeat(
        deviceId: String,
        version: String,
        failedDomain: String = "",
        running: Boolean = false,
        port: Int = 0,
    ): Result = call(
        "heartbeat",
        buildList {
            add("device_id" to deviceId)
            add("version" to version)
            // 后台按这两个字段判断「网关是否在运行」，缺了就只能看到设备存在
            add("running" to if (running) "1" else "0")
            if (port > 0) add("port" to port.toString())
            if (failedDomain.isNotBlank()) add("failed_domain" to failedDomain)
        },
    )

    // ------------------------------------------------------------------ 公告 / 更新

    /** 拉取远程公告（enabled=false 时客户端不展示）。 */
    fun announcement(): Result = call("announcement")

    /** 检查更新。[version] 为当前版本名，如 `1.7.20`。 */
    fun update(version: String): Result = call("update", listOf("version" to version))

    // ------------------------------------------------------------------ 工单

    /** 工单提交结果。 */
    data class TicketResult(val ok: Boolean, val ticketId: Long, val message: String)

    /**
     * 提交工单。图片以 `multipart/form-data` 的文件字段 `images[]` 上传。
     *
     * 后台自带的网页反馈页 `report.php` 用的就是 `multipart` + `images[]` 文件字段，
     * 这是服务端确证可用的图片通道；改用文档里的 base64 数组时，服务端对非法内容、
     * 超限大小、超数量均不做任何校验（不报错也不处理），说明该路径在后台未生效。
     *
     * @param images base64 字符串（可带 `data:image/...;base64,` 前缀），最多 8 张
     */
    fun submitTicket(
        content: String,
        contactType: String,
        contact: String,
        images: List<String> = emptyList(),
        deviceId: String = "",
        version: String = "",
    ): TicketResult {
        val trimmed = content.trim()
        if (trimmed.length < 5) return TicketResult(false, 0, "问题描述至少 5 个字")
        if (trimmed.length > 2000) return TicketResult(false, 0, "问题描述不能超过 2000 字")
        if (contact.isBlank()) return TicketResult(false, 0, "请填写联系方式")

        val fields = mutableListOf(
            "action" to "bug_submit",
            "key" to appKey,
            "content" to trimmed,
            "contact_type" to contactType,
            "contact" to contact.trim(),
        )
        if (deviceId.isNotBlank()) fields += "device_id" to deviceId
        if (version.isNotBlank()) fields += "version" to version

        val files = mutableListOf<dev.aigw.util.Http.FilePart>()
        images.take(MAX_IMAGES).forEachIndexed { index, raw ->
            val bytes = decodeImage(raw)
            if (bytes != null && bytes.isNotEmpty()) {
                files += dev.aigw.util.Http.FilePart(
                    fieldName = "images[]",
                    fileName = "image_$index.jpg",
                    contentType = "image/jpeg",
                    bytes = bytes,
                )
            }
        }

        val result = try {
            dev.aigw.util.Http.postMultipartFields(apiBase, emptyMap(), fields, files, TICKET_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.warn("工单提交失败：${e.message}")
            return TicketResult(false, 0, "网络异常：${e.message ?: "上传失败"}")
        }

        val parsed = parse(result.body)
            ?: return TicketResult(false, 0, "服务器返回异常（HTTP ${result.status}）")
        val code = parsed.code
        if (code != 0) {
            Log.warn("工单提交被拒：code=$code msg=${parsed.message}")
            return TicketResult(false, 0, parsed.message)
        }
        val ticketId = parsed.data.get("ticket_id")
            ?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
        return TicketResult(true, ticketId, if (ticketId > 0) "提交成功，工单号 #$ticketId" else parsed.message)
    }

    // ------------------------------------------------------------------ 内部

    /** 把 base64（可带 data URI 前缀）解码为字节；非法输入返回 null。 */
    private fun decodeImage(raw: String): ByteArray? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val comma = trimmed.indexOf(',')
        val payload = if (trimmed.startsWith("data:") && comma >= 0) {
            trimmed.substring(comma + 1)
        } else {
            trimmed
        }
        return runCatching { java.util.Base64.getDecoder().decode(payload) }.getOrNull()
    }

    /** GET 调用：`{apiBase}?action=...&key=...&k=v...`。 */
    private fun call(action: String, extra: List<Pair<String, String>> = emptyList()): Result {
        val query = buildList {
            add("action" to action)
            add("key" to appKey)
            addAll(extra)
        }.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
        }
        val url = apiBase.trimEnd('/') + "/?" + query

        val response = try {
            dev.aigw.util.Http.get(url, emptyMap(), TIMEOUT_MS)
        } catch (e: Exception) {
            Log.warn("远程接口 $action 失败：${e.message}")
            return Result(false, -1, "网络异常：${e.message ?: "请求失败"}", JsonObject())
        }
        val parsed = parse(response.body)
            ?: return Result(false, -1, "服务器返回异常（HTTP ${response.status}）", JsonObject())
        return parsed
    }

    private fun parse(body: String): Result? {
        val obj = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null
        val code = obj.get("code")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1
        val message = obj.firstString("msg")
        val data = obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        return Result(code == 0, code, message, data)
    }

    companion object {
        const val DEFAULT_API_BASE = "http://71api.636.ltd/api/"
        const val DEFAULT_APP_KEY = "sk_B6D769492DCB2F6A"

        const val MAX_IMAGES = 8
        private const val TIMEOUT_MS = 20_000

        /** 工单可能带多张 base64 图片，上传超时放宽到 60 秒。 */
        private const val TICKET_TIMEOUT_MS = 60_000
    }
}
