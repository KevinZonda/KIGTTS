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

@Composable
internal fun ThemeColorSettingRow(
    colorArgb: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticOnClick = rememberKigttsHapticClick(onClick)
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
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(Color(colorArgb), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "主题色",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "${colorToHexRgb(Color(colorArgb)).uppercase()} · 点击打开颜色选择器",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MsIcon(
            name = "palette",
            contentDescription = "选择主题色",
            tint = MaterialTheme.colorScheme.accentText
        )
    }
}
