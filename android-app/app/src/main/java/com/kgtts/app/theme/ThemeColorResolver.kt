package com.lhtstudio.kigtts.app.theme

import com.google.android.material.color.utilities.Contrast
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.TonalPalette

data class ThemeColorRoles(
    val primaryArgb: Int,
    val onPrimaryArgb: Int,
    val accentTextArgb: Int
)

object ThemeColorResolver {
    const val DEFAULT_THEME_COLOR_ARGB: Int = -16546937 // #038387

    private const val LIGHT_ACCENT_TONE = 40
    private const val DARK_ACCENT_TONE = 80
    private const val MIN_TEXT_CONTRAST = 4.5
    private const val MIN_COMPONENT_CONTRAST = 3.0
    private const val LIGHT_SURFACE_ARGB = -1 // #FFFFFF
    private const val DARK_SURFACE_ARGB = -14868445 // #1D2023
    private const val DARK_CONTENT_ARGB = -15657961 // #111417
    private const val LIGHT_CONTENT_ARGB = -1 // #FFFFFF

    fun normalizeSeedArgb(argb: Int): Int = argb or (0xFF shl 24)

    fun resolve(
        seedArgb: Int,
        darkTheme: Boolean,
        toneCorrectionEnabled: Boolean
    ): ThemeColorRoles {
        val seed = normalizeSeedArgb(seedArgb)
        val palette = TonalPalette.fromInt(seed)
        val primary = when {
            !toneCorrectionEnabled -> seed
            darkTheme -> palette.tone(DARK_ACCENT_TONE)
            else -> seed
        }
        val accentText = when {
            !toneCorrectionEnabled -> seed
            darkTheme -> palette.tone(DARK_ACCENT_TONE)
            contrastRatio(seed, LIGHT_SURFACE_ARGB) >= MIN_TEXT_CONTRAST -> seed
            else -> palette.tone(LIGHT_ACCENT_TONE)
        }
        return ThemeColorRoles(
            primaryArgb = primary,
            onPrimaryArgb = readableContentColor(primary),
            accentTextArgb = accentText
        )
    }

    fun shouldSuggestToneCorrection(seedArgb: Int): Boolean {
        val seed = normalizeSeedArgb(seedArgb)
        return contrastRatio(seed, LIGHT_SURFACE_ARGB) < MIN_COMPONENT_CONTRAST ||
            contrastRatio(seed, DARK_SURFACE_ARGB) < MIN_COMPONENT_CONTRAST
    }

    internal fun contrastRatio(foregroundArgb: Int, backgroundArgb: Int): Double {
        val foregroundTone = Hct.fromInt(normalizeSeedArgb(foregroundArgb)).tone
        val backgroundTone = Hct.fromInt(normalizeSeedArgb(backgroundArgb)).tone
        return Contrast.ratioOfTones(foregroundTone, backgroundTone)
    }

    private fun readableContentColor(backgroundArgb: Int): Int {
        val whiteContrast = contrastRatio(LIGHT_CONTENT_ARGB, backgroundArgb)
        if (whiteContrast >= MIN_TEXT_CONTRAST) return LIGHT_CONTENT_ARGB

        val darkContrast = contrastRatio(DARK_CONTENT_ARGB, backgroundArgb)
        if (darkContrast >= MIN_TEXT_CONTRAST) return DARK_CONTENT_ARGB

        return if (whiteContrast >= darkContrast) LIGHT_CONTENT_ARGB else DARK_CONTENT_ARGB
    }
}
