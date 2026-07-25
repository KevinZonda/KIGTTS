package com.lhtstudio.kigtts.app.theme

import com.google.android.material.color.utilities.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorResolverTest {
    @Test
    fun correctionOffPreservesDefaultThemeColorAndWhiteButtonContent() {
        val roles = ThemeColorResolver.resolve(
            seedArgb = ThemeColorResolver.DEFAULT_THEME_COLOR_ARGB,
            darkTheme = false,
            toneCorrectionEnabled = false
        )

        assertEquals(ThemeColorResolver.DEFAULT_THEME_COLOR_ARGB, roles.primaryArgb)
        assertEquals(ThemeColorResolver.DEFAULT_THEME_COLOR_ARGB, roles.accentTextArgb)
        assertEquals(0xFFFFFFFF.toInt(), roles.onPrimaryArgb)
    }

    @Test
    fun darkThemeCorrectionRaisesADeepSeedToMaterialDarkTone() {
        val deepBlue = 0xFF001133.toInt()
        val roles = ThemeColorResolver.resolve(
            seedArgb = deepBlue,
            darkTheme = true,
            toneCorrectionEnabled = true
        )

        assertTrue(Hct.fromInt(roles.primaryArgb).tone >= 79.0)
        assertTrue(Hct.fromInt(roles.primaryArgb).tone > Hct.fromInt(deepBlue).tone)
        assertEquals(0xFF111417.toInt(), roles.onPrimaryArgb)
    }

    @Test
    fun lightThemeCorrectionKeepsPaleButtonButDarkensAccentText() {
        val palePink = 0xFFFFE4EC.toInt()
        val roles = ThemeColorResolver.resolve(
            seedArgb = palePink,
            darkTheme = false,
            toneCorrectionEnabled = true
        )

        assertEquals(palePink, roles.primaryArgb)
        assertTrue(Hct.fromInt(roles.accentTextArgb).tone <= 41.0)
        assertEquals(0xFF111417.toInt(), roles.onPrimaryArgb)
    }

    @Test
    fun correctionSuggestionOnlyFlagsColorsUnsafeInOneTheme() {
        assertFalse(
            ThemeColorResolver.shouldSuggestToneCorrection(
                ThemeColorResolver.DEFAULT_THEME_COLOR_ARGB
            )
        )
        assertTrue(ThemeColorResolver.shouldSuggestToneCorrection(0xFF001133.toInt()))
        assertTrue(ThemeColorResolver.shouldSuggestToneCorrection(0xFFFFF0F5.toInt()))
    }
}
