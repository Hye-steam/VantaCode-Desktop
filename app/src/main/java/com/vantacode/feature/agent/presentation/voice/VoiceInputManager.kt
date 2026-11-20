package com.vantacode.feature.agent.presentation.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * 系统语音识别封装。
 *
 * 用 [SpeechRecognizer] 而不是云端 ASR：不联网、不产生费用，识别质量取决于手机自带引擎。
 * 部分国产 ROM 没有识别服务，调用前必须用 [isAvailable] 判断，否则 createSpeechRecognizer
 * 会直接返回 null 或抛出异常。
 */
class VoiceInputManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    /** 当前设备是否具备语音识别能力。没有引擎时按钮应当隐藏而不是点了报错。 */
    fun isAvailable(): Boolean = runCatching {
        SpeechRecognizer.isRecognitionAvailable(context)
    }.getOrDefault(false)

    /**
     * 开始识别。
     *
     * [onPartial] 在识别过程中持续回调中间结果，让输入框能实时显示已识别的文字；
     * [onResult] 只在最终结果到达时回调一次。
     * 所有回调都发生在主线程。
     */
    fun start(
        onPartial: (String) -> Unit = {},
        onResult: (String) -> Unit,
        onError: (VoiceError) -> Unit
    ) {
        stop()
        if (!isAvailable()) {
            onError(VoiceError.NOT_AVAILABLE)
            return
        }

        val engine = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (engine == null) {
            onError(VoiceError.NOT_AVAILABLE)
            return
        }
        recognizer = engine

        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.takeIf { it.isNotBlank() }?.let(onPartial)
            }

            override fun onResults(results: Bundle?) {
                val text = firstResult(results).orEmpty()
                release()
                if (text.isBlank()) onError(VoiceError.NO_SPEECH) else onResult(text)
            }

            override fun onError(error: Int) {
                release()
                onError(mapError(error))
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // 跟随系统语言：中文环境下识别中文，避免写死 zh-CN 导致英文技术词识别很差
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        runCatching { engine.startListening(intent) }.onFailure {
            release()
            onError(VoiceError.ENGINE_ERROR)
        }
    }

    /** 取消本次识别并释放引擎。页面销毁或用户点停止时调用。 */
    fun stop() {
        runCatching { recognizer?.cancel() }
        release()
    }

    private fun release() {
        val engine = recognizer ?: return
        recognizer = null
        runCatching { engine.destroy() }
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun mapError(code: Int): VoiceError = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NO_SPEECH

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.NO_PERMISSION

        // 网络类错误在系统引擎上通常意味着设备把识别交给了云端而网络不可用
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceError.NETWORK

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError.BUSY

        else -> VoiceError.ENGINE_ERROR
    }
}

/** 语音识别失败原因，用于给出可操作的中文提示。 */
enum class VoiceError {
    /** 设备没有语音识别引擎。 */
    NOT_AVAILABLE,

    /** 用户拒绝了录音权限。 */
    NO_PERMISSION,

    /** 没有识别到有效语音（静音或超时）。 */
    NO_SPEECH,

    /** 引擎需要联网但网络不可用。 */
    NETWORK,

    /** 上一次识别还没结束。 */
    BUSY,

    /** 引擎内部错误。 */
    ENGINE_ERROR
}
