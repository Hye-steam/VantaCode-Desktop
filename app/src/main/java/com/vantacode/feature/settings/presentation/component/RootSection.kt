package com.vantacode.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.vantacode.R
import com.vantacode.core.theme.Spacing
import com.vantacode.core.theme.semanticColors
import com.vantacode.core.ui.AppSwitch
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertTriangle
import compose.icons.feathericons.CheckCircle
import compose.icons.feathericons.Terminal
import compose.icons.feathericons.XCircle

/** Root 探测状态，供界面展示。 */
sealed interface RootProbeUi {
    data object Idle : RootProbeUi
    data object Probing : RootProbeUi
    data class Granted(val suPath: String, val androidVersion: String?) : RootProbeUi
    data class Unavailable(val reason: String) : RootProbeUi
}

/**
 * 「Root 权限」设置页。
 *
 * 三层开关是递进的，默认全关：
 *   1. 启用 root：关着时 Root 工具根本不注册，AI 看不到它
 *   2. 允许访问私有数据：关着时含 /data/data 等路径的命令直接被拒
 *   3. 跳过确认：关着时每次 root 调用都要用户点一下
 *
 * 页面顶部实时显示探测结果：设备到底有没有 root、su 在哪、能不能真的拿到 uid 0。
 * 不显示这个的话，用户开了开关却发现没 root，只会看到工具报错而不知道为什么。
 */
@Composable
internal fun RootSection(
    probe: RootProbeUi,
    enabled: Boolean,
    skipConfirm: Boolean,
    privateDataAllowed: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleSkipConfirm: (Boolean) -> Unit,
    onTogglePrivateData: (Boolean) -> Unit,
    onReprobe: () -> Unit,
) {
    // 关掉总开关时提示用户：真正生效要重开会话。工具表在会话启动时构建。
    var showRestartHint by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        RootStatusCard(probe = probe, onReprobe = onReprobe)

        SettingsGroupHeader(text = stringResource(R.string.root_group_basic))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Terminal,
                title = stringResource(R.string.root_enable),
                subtitle = stringResource(R.string.root_enable_desc),
                trailing = {
                    AppSwitch(
                        checked = enabled,
                        onCheckedChange = {
                            onToggleEnabled(it)
                            // 打开/关闭都要重开会话才反映到工具表，这里给一次提示而不是反复弹窗
                            showRestartHint = true
                        }
                    )
                }
            )
        }

        if (showRestartHint) {
            Text(
                text = stringResource(R.string.root_restart_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.semanticColors.subtleText,
                modifier = Modifier.padding(horizontal = Spacing.sm)
            )
        }

        // 后两项只在总开关打开时才有意义，关着时置灰但保留可见，让用户知道还有这些选项
        SettingsGroupHeader(text = stringResource(R.string.root_group_scope))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.AlertTriangle,
                title = stringResource(R.string.root_private_data),
                subtitle = stringResource(R.string.root_private_data_desc),
                enabled = enabled,
                trailing = {
                    AppSwitch(
                        checked = privateDataAllowed,
                        onCheckedChange = onTogglePrivateData,
                        enabled = enabled,
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.CheckCircle,
                title = stringResource(R.string.root_skip_confirm),
                subtitle = stringResource(R.string.root_skip_confirm_desc),
                enabled = enabled,
                trailing = {
                    AppSwitch(
                        checked = skipConfirm,
                        onCheckedChange = onToggleSkipConfirm,
                        enabled = enabled,
                    )
                }
            )
        }

        RootWarningCard()
    }
}

/** 探测结果卡片：一眼看出设备能不能用 root。 */
@Composable
private fun RootStatusCard(probe: RootProbeUi, onReprobe: () -> Unit) {
    SettingsGroup {
        SettingsRow(
            icon = when (probe) {
                is RootProbeUi.Granted -> FeatherIcons.CheckCircle
                is RootProbeUi.Unavailable -> FeatherIcons.XCircle
                else -> FeatherIcons.Terminal
            },
            title = stringResource(R.string.root_status_title),
            subtitle = when (probe) {
                RootProbeUi.Idle -> stringResource(R.string.root_status_idle)
                RootProbeUi.Probing -> stringResource(R.string.root_status_probing)
                is RootProbeUi.Granted -> buildString {
                    append(stringResource(R.string.root_status_granted))
                    append(" · ")
                    append(probe.suPath)
                    probe.androidVersion?.let { append(" · Android $it") }
                }
                is RootProbeUi.Unavailable -> probe.reason
            },
            trailing = {
                Text(
                    text = stringResource(R.string.root_reprobe),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (probe is RootProbeUi.Probing) {
                        MaterialTheme.semanticColors.subtleText
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(start = Spacing.sm)
                )
            },
            onClick = if (probe is RootProbeUi.Probing) null else onReprobe
        )
    }
}

/** 风险提示。root 是不可逆的能力放大，必须说清楚代价。 */
@Composable
private fun RootWarningCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = stringResource(R.string.root_warning_title),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = stringResource(R.string.root_warning_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.semanticColors.subtleText
        )
    }
}
