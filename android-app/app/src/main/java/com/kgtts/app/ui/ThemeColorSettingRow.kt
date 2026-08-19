package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhtstudio.kigtts.app.data.formatColorHexAndNameZhCn

@Composable
internal fun ThemeColorSettingRow(
    colorArgb: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = ColorPickerSettingRow(
    title = "主题色",
    colorArgb = colorArgb,
    onClick = onClick,
    modifier = modifier
)

@Composable
internal fun ColorPickerSettingRow(
    title: String,
    colorArgb: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String = formatColorHexAndNameZhCn(colorArgb),
    iconTint: Color? = null,
    contentColor: Color? = null,
    supportingColor: Color? = null,
    outlineColor: Color? = null
) {
    val hapticOnClick = rememberKigttsHapticClick(onClick)
    val resolvedContentColor = contentColor ?: MaterialTheme.colorScheme.onSurface
    val resolvedSupportingColor = supportingColor ?: MaterialTheme.colorScheme.onSurfaceVariant
    val resolvedOutlineColor = outlineColor ?: MaterialTheme.colorScheme.outline
    val resolvedIconTint = iconTint ?: MaterialTheme.colorScheme.accentText
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiTokens.Radius))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                onClick = hapticOnClick
            )
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MsIcon(
            name = "palette",
            contentDescription = null,
            tint = resolvedIconTint
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = resolvedContentColor
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = resolvedSupportingColor
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(Color(colorArgb), CircleShape)
                .border(1.dp, resolvedOutlineColor, CircleShape)
        )
    }
}
