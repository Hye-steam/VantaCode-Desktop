package dev.aigw.provider.deepseek

import com.google.gson.JsonObject
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * DeepSeek 网页版 PoW（DeepSeekHashV1）。
 *
 * 算法是 SHA3-256 的变体：Keccak-f[1600] 跳过第 0 轮，其余参数（rate 136、
 * padding 0x06 / 0x80、输出 32 字节）与标准一致。服务端在 [0, difficulty) 里选一个
 * answer，令 challenge = hash(salt + "_" + expire_at + "_" + answer)；
 * 客户端遍历 nonce 找到相同摘要。用完的 header 只能用于同一次请求。
 */
object DeepSeekHash {

    private val RC: LongArray = listOf(
        "0000000000000001", "0000000000008082", "800000000000808a", "8000000080008000",
        "000000000000808b", "0000000080000001", "8000000080008081", "8000000000008009",
        "000000000000008a", "0000000000000088", "0000000080008009", "000000008000000a",
        "000000008000808b", "800000000000008b", "8000000000008089", "8000000000008003",
        "8000000000008002", "8000000000000080", "000000000000800a", "800000008000000a",
        "8000000080008081", "8000000000008080", "0000000080000001", "8000000080008008",
    ).map { java.lang.Long.parseUnsignedLong(it, 16) }.toLongArray()
    private val ROTC = intArrayOf(1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 2, 14, 27, 41, 56, 8, 25, 43, 62, 18, 39, 61, 20, 44)
    private val PILN = intArrayOf(10, 7, 11, 17, 18, 3, 5, 16, 8, 21, 24, 4, 15, 23, 19, 13, 12, 2, 20, 14, 22, 9, 6, 1)

    /** 构造 `x-ds-pow-response` 头的值（base64 的 JSON）。 */
    fun powHeader(challenge: JsonObject): String {
        val algorithm = challenge.get("algorithm")?.asString ?: "DeepSeekHashV1"
        val salt = challenge.get("salt")?.asString.orEmpty()
        val expireAt = challenge.get("expire_at")?.asLong ?: 0L
        val difficulty = challenge.get("difficulty")?.asLong ?: 144_000L
        val challengeHex = challenge.get("challenge")?.asString.orEmpty()
        val signature = challenge.get("signature")?.asString.orEmpty()
        val targetPath = challenge.get("target_path")?.asString ?: "/api/v0/chat/completion"

        val prefix = "${salt}_${expireAt}_"
        val answer = solve(prefix, challengeHex, difficulty)
            ?: throw IllegalStateException("PoW 求解失败（difficulty=$difficulty）")

        val payload = JsonObject().apply {
            addProperty("algorithm", algorithm)
            addProperty("challenge", challengeHex)
            addProperty("salt", salt)
            addProperty("answer", answer)
            addProperty("signature", signature)
            addProperty("target_path", targetPath)
        }
        return Base64.getEncoder().encodeToString(payload.toString().toByteArray(StandardCharsets.UTF_8))
    }

    private fun solve(prefix: String, targetHex: String, difficulty: Long): Long? {
        val max = difficulty.coerceIn(0L, 5_000_000L)
        val prefixBytes = prefix.toByteArray(StandardCharsets.UTF_8)
        for (nonce in 0 until max) {
            val suffix = nonce.toString().toByteArray(StandardCharsets.UTF_8)
            val data = ByteArray(prefixBytes.size + suffix.size)
            System.arraycopy(prefixBytes, 0, data, 0, prefixBytes.size)
            System.arraycopy(suffix, 0, data, prefixBytes.size, suffix.size)
            if (toHex(sha3(data)) == targetHex) return nonce
        }
        return null
    }

    /** DeepSeekHashV1：与 SHA3-256 相同，只是省掉第 0 轮。 */
    fun sha3(data: ByteArray): ByteArray {
        val rate = 136
        val state = LongArray(25)
        var offset = 0
        while (data.size - offset >= rate) {
            for (i in 0 until rate / 8) state[i] = state[i] xor readLong(data, offset + i * 8)
            keccakF(state)
            offset += rate
        }
        val last = ByteArray(rate)
        val rem = data.size - offset
        System.arraycopy(data, offset, last, 0, rem)
        last[rem] = (last[rem].toInt() xor 0x06).toByte()
        last[rate - 1] = (last[rate - 1].toInt() xor 0x80).toByte()
        for (i in 0 until rate / 8) state[i] = state[i] xor readLong(last, i * 8)
        keccakF(state)
        val out = ByteArray(32)
        for (i in 0 until 4) writeLong(state[i], out, i * 8)
        return out
    }

    private fun keccakF(a: LongArray) {
        val bc = LongArray(5)
        for (round in 1 until 24) {
            for (i in 0 until 5) bc[i] = a[i] xor a[i + 5] xor a[i + 10] xor a[i + 15] xor a[i + 20]
            for (i in 0 until 5) {
                val t = bc[(i + 4) % 5] xor java.lang.Long.rotateLeft(bc[(i + 1) % 5], 1)
                for (j in 0 until 25 step 5) a[j + i] = a[j + i] xor t
            }
            var t = a[1]
            for (i in 0 until 24) {
                val j = PILN[i]
                val tmp = a[j]
                a[j] = java.lang.Long.rotateLeft(t, ROTC[i])
                t = tmp
            }
            for (j in 0 until 25 step 5) {
                for (i in 0 until 5) bc[i] = a[j + i]
                for (i in 0 until 5) a[j + i] = a[j + i] xor (bc[(i + 1) % 5].inv() and bc[(i + 2) % 5])
            }
            a[0] = a[0] xor RC[round]
        }
    }

    private fun readLong(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (b[o + i].toLong() and 0xFFL)
        return v
    }

    private fun writeLong(v: Long, b: ByteArray, o: Int) {
        for (i in 0 until 8) b[o + i] = (v ushr (8 * i)).toByte()
    }

    private val HEX = "0123456789abcdef".toCharArray()

    private fun toHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(out)
    }
}
