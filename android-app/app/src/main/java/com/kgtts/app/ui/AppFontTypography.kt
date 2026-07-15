package com.lhtstudio.kigtts.app.ui

import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.lhtstudio.kigtts.app.data.OpenTypeFontParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun rememberAppFontFamily(
    fontFilePath: String?,
    preferredWeight: Int
): FontFamily? {
    val family by produceState<FontFamily?>(
        initialValue = null,
        key1 = fontFilePath,
        key2 = preferredWeight
    ) {
        value = withContext(Dispatchers.IO) {
            fontFilePath
                ?.let(::File)
                ?.takeIf { it.isFile }
                ?.let { file -> runCatching { loadAppFontFamily(file, preferredWeight) }.getOrNull() }
        }
    }
    return family
}

@OptIn(ExperimentalTextApi::class)
internal fun loadAppFontFamily(file: File, preferredWeight: Int): FontFamily {
    val axis = OpenTypeFontParser.parse(file).weightAxis
    if (axis == null) return FontFamily(Font(file))
    val selected = axis.clamp(preferredWeight)
    val offset = selected - axis.default
    val fonts = StandardFontWeights.map { requested ->
        val axisWeight = axis.clamp(requested + offset)
        Font(
            file = file,
            weight = FontWeight(requested),
            style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(axisWeight))
        )
    }
    return FontFamily(fonts)
}

internal fun Typography.withAppFontFamily(fontFamily: FontFamily?): Typography {
    if (fontFamily == null) return this
    return copy(
        h1 = h1.copy(fontFamily = fontFamily),
        h2 = h2.copy(fontFamily = fontFamily),
        h3 = h3.copy(fontFamily = fontFamily),
        h4 = h4.copy(fontFamily = fontFamily),
        h5 = h5.copy(fontFamily = fontFamily),
        h6 = h6.copy(fontFamily = fontFamily),
        subtitle1 = subtitle1.copy(fontFamily = fontFamily),
        subtitle2 = subtitle2.copy(fontFamily = fontFamily),
        body1 = body1.copy(fontFamily = fontFamily),
        body2 = body2.copy(fontFamily = fontFamily),
        button = button.copy(fontFamily = fontFamily),
        caption = caption.copy(fontFamily = fontFamily),
        overline = overline.copy(fontFamily = fontFamily)
    )
}

private val StandardFontWeights = (100..900 step 100).toList()
