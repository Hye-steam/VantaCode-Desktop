package com.vantacode.feature.agent.presentation.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.vantacode.R

/**
 * 语音输入的可复用状态机：权限、引擎可用性、录音中状态与错误提示都由这里统一处理，
 * 调用方只需要拿到 [isListening] 和 [onToggle]，再把识别结果拼进输入框。
 *
 * 识别结果分两段交付：[onPartial] 覆盖式更新输入框中的「临时区」，[onFinal] 落定。
 * 这样用户说话时能看到文字逐字出现，而不是等说完才一次性蹦出来。
 */
@Composable
internal fun rememberVoiceInput(
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onError: (String) -> Unit
): VoiceInputState {
    val context = LocalContext.current
    val manager = remember { VoiceInputManager(context) }

    // 回调可能随重组变化，用 rememberUpdatedState 避免把旧闭包捕获进 listener
    val currentPartial by rememberUpdatedState(onPartial)
    val currentFinal by rememberUpdatedState(onFinal)
    val currentError by rememberUpdatedState(onError)

    var isListening by remember { mutableStateOf(false) }
    val isAvailable = remember { manager.isAvailable() }

    val showError: (VoiceError) -> Unit = { error ->
        isListening = false
        currentError(
            context.getString(
                when (error) {
                    VoiceError.NOT_AVAILABLE -> R.string.chat_voice_not_available
                    VoiceError.NO_PERMISSION -> R.string.chat_voice_permission_denied
                    VoiceError.NO_SPEECH -> R.string.chat_voice_no_speech
                    VoiceError.NETWORK -> R.string.chat_voice_network_error
                    VoiceError.BUSY -> R.string.chat_voice_busy
                    VoiceError.ENGINE_ERROR -> R.string.chat_voice_engine_error
                }
            )
        )
    }

    val startListening: () -> Unit = {
        isListening = true
        manager.start(
            onPartial = { currentPartial(it) },
            onResult = { text ->
                isListening = false
                currentFinal(text)
            },
            onError = showError
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else showError(VoiceError.NO_PERMISSION)
    }

    // 离开页面时释放引擎，否则录音会继续占用麦克风
    DisposableEffect(Unit) {
        onDispose { manager.stop() }
    }

    val onToggle: () -> Unit = {
        if (isListening) {
            manager.stop()
            isListening = false
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) startListening()
            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    return VoiceInputState(
        isAvailable = isAvailable,
        isListening = isListening,
        onToggle = onToggle
    )
}

/** [rememberVoiceInput] 暴露给 UI 的状态。 */
internal data class VoiceInputState(
    /** 设备有语音识别引擎。false 时不应渲染按钮。 */
    val isAvailable: Boolean,
    val isListening: Boolean,
    val onToggle: () -> Unit
)
