package dev.aigw.gateway

import com.google.gson.JsonObject
import java.io.Writer

/** SSE 帧组装：所有事件必须以空行结尾，这里统一规范化。 */
object Sse {
    const val DONE = "[DONE]"

    fun frame(data: String): String = "data: $data\n\n"

    /** 把一条完整 JSON 拆成「单 chunk + [DONE]」的标准流式回复（上游给普通 JSON 而客户端要流式时）。 */
    fun completionAsSse(json: JsonObject, writer: Writer) {
        writer.write(frame(json.toString()))
        writer.write(frame(DONE))
        writer.flush()
    }
}
