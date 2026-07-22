package com.lhtstudio.kigtts.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSubtitleTextStyleTest {
    @Test
    fun adaptiveStyleKeepsCustomFontAndPlatformMetrics() {
        val platformStyle = PlatformTextStyle(includeFontPadding = false)
        val lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
        val base = TextStyle(
            fontFamily = FontFamily.Cursive,
            platformStyle = platformStyle,
            lineHeightStyle = lineHeightStyle,
            letterSpacing = 0.5.sp
        )

        val result = quickSubtitleAdaptiveTextStyle(
            baseStyle = base,
            color = Color.White,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSizeSp = 72f,
            lineHeightMultiplier = 1.36f
        )

        assertEquals(FontFamily.Cursive, result.fontFamily)
        assertEquals(platformStyle, result.platformStyle)
        assertEquals(lineHeightStyle, result.lineHeightStyle)
        assertEquals(0.5.sp, result.letterSpacing)
        assertEquals(72.sp, result.fontSize)
        assertEquals(97.92.sp, result.lineHeight)
    }
}
