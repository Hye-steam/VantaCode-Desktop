package dev.aigw.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 共享小组件：卡片 / 标题行 / 开关行 / 按钮。 */

@Composable
fun LoopCard(theme: ThemePalette, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.card),
        border = BorderStroke(1.dp, theme.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = { Column(Modifier.padding(16.dp), content = content) },
    )
}

@Composable
fun SectionTitle(theme: ThemePalette, text: String) {
    Text(text, color = theme.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun SwitchRow(
    theme: ThemePalette,
    label: String,
    desc: String = "",
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = theme.ink, fontSize = 15.sp)
            if (desc.isNotBlank()) Text(desc, color = theme.ink3, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = theme.card,
                checkedTrackColor = theme.primary,
                uncheckedThumbColor = theme.card,
                uncheckedTrackColor = theme.line,
            ),
        )
    }
}

@Composable
fun PrimaryButton(theme: ThemePalette, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = theme.primary,
            contentColor = androidx.compose.ui.graphics.Color.White,
            disabledContainerColor = theme.line,
            disabledContentColor = theme.ink3,
        ),
        shape = RoundedCornerShape(10.dp),
    ) { Text(text, fontSize = 14.sp) }
}

@Composable
fun GhostButton(theme: ThemePalette, text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, theme.cardBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.ink2),
        shape = RoundedCornerShape(10.dp),
    ) { Text(text, fontSize = 14.sp) }
}

@Composable
fun DangerTextButton(theme: ThemePalette, text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text, color = theme.danger, fontSize = 13.sp) }
}

@Composable
fun LoopTextField(
    theme: ThemePalette,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = theme.ink3, fontSize = 13.sp) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = theme.ink,
            unfocusedTextColor = theme.ink,
            focusedContainerColor = theme.fieldBg,
            unfocusedContainerColor = theme.fieldBg,
            focusedBorderColor = theme.primary,
            unfocusedBorderColor = theme.cardBorder,
            cursorColor = theme.primary,
        ),
        shape = RoundedCornerShape(10.dp),
    )
}

@Composable
fun RowButtons(vararg buttons: @Composable () -> Unit) {
    Row {
        buttons.forEachIndexed { i, b ->
            if (i > 0) Spacer(Modifier.width(8.dp))
            b()
        }
    }
}
