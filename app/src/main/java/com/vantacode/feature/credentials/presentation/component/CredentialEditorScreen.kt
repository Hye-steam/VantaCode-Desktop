package com.vantacode.feature.credentials.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.vantacode.core.ui.AdaptiveModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vantacode.R
import com.vantacode.core.theme.Spacing
import com.vantacode.core.ui.AppTextField
import com.vantacode.feature.credentials.domain.model.GitCredential
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check
import compose.icons.feathericons.Eye
import compose.icons.feathericons.EyeOff

/**
 * 凭据编辑 BottomSheet 弹窗：从底部弹出编辑/新增 host、用户名、Token。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CredentialEditorSheet(
    initial: GitCredential?,
    onDismiss: () -> Unit,
    onSave: (GitCredential) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var host by remember(initial) { mutableStateOf(initial?.host ?: "") }
    var username by remember(initial) { mutableStateOf(initial?.username ?: "") }
    var token by remember(initial) { mutableStateOf(initial?.token ?: "") }
    var tokenVisible by remember { mutableStateOf(false) }

    val canSave = host.trim().isNotBlank() && username.trim().isNotBlank() && token.isNotBlank()

    fun current(): GitCredential? {
        if (!canSave) return null
        val h = host.trim()
        return GitCredential(
            id = h.lowercase(),
            host = h,
            username = username.trim(),
            token = token
        )
    }

    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = if (initial == null) stringResource(R.string.credential_add) else stringResource(R.string.credential_edit),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xs)
            )

            CredentialField(
                value = host,
                onValueChange = { host = it },
                label = stringResource(R.string.credential_host),
                placeholder = "github.com"
            )

            // 常用服务商一键填入。国内访问 GitHub 不稳，CNB / Gitee 是常见替代，
            // 手敲域名容易被打成 cnb.com 之类，这里直接给正确的。
            // 有固定用户名的平台（CNB 必须是 cnb）顺手填进去，否则用户填账号名会一直认证失败。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                HostPresets.forEach { preset ->
                    FilterChip(
                        selected = host.trim().equals(preset.host, ignoreCase = true),
                        onClick = {
                            host = preset.host
                            preset.username?.let { fixed ->
                                if (username.isBlank()) username = fixed
                            }
                        },
                        label = { Text(preset.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
            // 固定用户名的平台给出说明并锁住输入框：改了就登不上，不能让它看起来是可选的。
            val fixedUsername = HostPresets
                .firstOrNull { it.host.equals(host.trim(), ignoreCase = true) }
                ?.username
            CredentialField(
                value = username,
                onValueChange = { if (fixedUsername == null) username = it },
                label = stringResource(R.string.common_username),
                enabled = fixedUsername == null,
                placeholder = fixedUsername,
                supportingText = if (fixedUsername != null) {
                    { Text(stringResource(R.string.credential_username_fixed, fixedUsername)) }
                } else {
                    null
                }
            )
            CredentialField(
                value = token,
                onValueChange = { token = it },
                label = stringResource(R.string.credential_token),
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(if (tokenVisible) FeatherIcons.EyeOff else FeatherIcons.Eye, contentDescription = if (tokenVisible) stringResource(R.string.common_hide) else stringResource(R.string.common_show))
                    }
                }
            )

            Button(
                onClick = {
                    current()?.let {
                        onSave(it)
                        onDismiss()
                    }
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(FeatherIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (initial != null) stringResource(R.string.common_save) else stringResource(R.string.common_add),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

/** 统一使用全局 AppTextField 组件。 */
@Composable
private fun CredentialField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    supportingText: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        singleLine = true,
        enabled = enabled,
        supportingText = supportingText,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingIcon,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * 常用 Git 服务商：显示名、主机名、该平台认证要求的固定用户名。
 *
 * CNB（cnb.cool）是腾讯云的国内代码托管，访问速度和合规性对国内用户更友好；
 * Gitee 同理。两家都用「用户名 + token」，但 CNB 的用户名不是账号名——
 * 官方要求固定填 `cnb`，填成自己的账号名会一直认证失败。
 */
private data class HostPreset(val label: String, val host: String, val username: String?)

private val HostPresets = listOf(
    HostPreset("GitHub", "github.com", null),
    HostPreset("CNB", "cnb.cool", "cnb"),
    HostPreset("Gitee", "gitee.com", null)
)
