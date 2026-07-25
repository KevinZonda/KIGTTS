package com.lhtstudio.kigtts.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

internal fun quickSubtitleAdaptiveTextStyle(
    baseStyle: TextStyle,
    color: Color,
    textAlign: TextAlign,
    fontWeight: FontWeight,
    fontSizeSp: Float,
    lineHeightMultiplier: Float
): TextStyle = baseStyle.copy(
    color = color,
    textAlign = textAlign,
    fontWeight = fontWeight,
    fontSize = fontSizeSp.sp,
    lineHeight = (fontSizeSp * lineHeightMultiplier).coerceAtLeast(fontSizeSp).sp
)
