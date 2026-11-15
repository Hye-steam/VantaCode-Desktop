package com.vantacode.feature.agent.presentation.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vantacode.core.theme.Radius
import com.vantacode.feature.agent.presentation.AIAgentViewModel

/**
 * 全局工具授权弹窗。
 *
 * 授权面板原本只挂在聊天页的中，用户在群聊/终端/设置页时，子代理请求授权完全看不见，
 * 执行就静默卡住。这里把它提到全局层：只要有未处理的请求，不管当前在哪个页面都会弹出来。
 *
 * 聊天页仍用原来嵌在输入栏上方的版本（带避让底栏、滚动弱化），所以这里只对其他路由生效，
 * 避免同一个请求弹出两次。
 */
@Composable
fun GlobalToolPermissionHost(
    viewModel: AIAgentViewModel,
    enabled: Boolean
) {
    if (!enabled) return
    val request by viewModel.pendingToolPermission.collectAsStateWithLifecycle()
    val sessionTitle by viewModel.pendingToolPermissionSessionTitle.collectAsStateWithLifecycle()
    val current = request ?: return

    Dialog(
        onDismissRequest = { /* 必须由用户明确选择，点外部不关闭 */ },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(Radius.lg),
            color = MaterialTheme.colorScheme.surface
        ) {
            ToolPermissionPanel(
                request = current,
                onChoice = { choice -> viewModel.resolveToolPermission(current.id, choice) },
                sessionTitle = sessionTitle,
                forceCollapse = false,
                isScrolling = false
            )
        }
    }
}
