package com.lhtstudio.kigtts.app.ui

import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.lhtstudio.kigtts.app.data.AppFontFamilySource
import com.lhtstudio.kigtts.app.data.AppFontFileSource
import com.lhtstudio.kigtts.app.data.OpenTypeFontParser
import com.lhtstudio.kigtts.app.data.nearestTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun rememberAppFontFamily(
    source: AppFontFamilySource?,
    preferredWeight: Int
): FontFamily? {
    val family by produceState<FontFamily?>(
        initialValue = null,
        key1 = source,
        key2 = preferredWeight
    ) {
        value = withContext(Dispatchers.IO) {
            source?.let {
                runCatching { loadAppFontFamily(it, preferredWeight) }.getOrNull()
            }
        }
    }
    return family
}

@OptIn(ExperimentalTextApi::class)
internal fun loadAppFontFamily(
    source: AppFontFamilySource,
    preferredWeight: Int
): FontFamily {
    val available = source.files.mapNotNull { entry ->
        File(entry.path).takeIf { it.isFile }?.let { entry to it }
    }.distinctBy { it.first.weight }
    require(available.isNotEmpty()) { "字体文件不存在" }
    val primaryFile = available.first().second
    val parsedAxis = OpenTypeFontParser.parse(primaryFile).weightAxis
    if (parsedAxis == null) return loadStaticFontFamily(source, available, preferredWeight)
    val axis = parsedAxis.withDefault(source.defaultWeight)
    val selected = axis.clamp(preferredWeight)
    val offset = selected - axis.default
    val fonts = StandardFontWeights.map { requested ->
        val axisWeight = axis.clamp(requested + offset)
        Font(
            file = primaryFile,
            weight = FontWeight(requested),
            style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(axisWeight))
        )
    }
    return FontFamily(fonts)
}

internal fun loadAppFontFamily(file: File, preferredWeight: Int): FontFamily {
    val defaultWeight = OpenTypeFontParser.parse(file).weightAxis?.default ?: FontWeight.Normal.weight
    return loadAppFontFamily(
        source = AppFontFamilySource(
            files = listOf(AppFontFileSource(file.absolutePath, defaultWeight)),
            defaultWeight = defaultWeight
        ),
        preferredWeight = preferredWeight
    )
}

private fun loadStaticFontFamily(
    source: AppFontFamilySource,
    available: List<Pair<AppFontFileSource, File>>,
    preferredWeight: Int
): FontFamily {
    if (available.size == 1) return FontFamily(Font(available.first().second))
    val byWeight = available.associate { it.first.weight to it.second }
    val weights = byWeight.keys.sorted()
    val selected = weights.nearestTo(preferredWeight) ?: source.defaultWeight
    val default = weights.nearestTo(source.defaultWeight) ?: source.defaultWeight
    val offset = selected - default
    return FontFamily(
        StandardFontWeights.map { requested ->
            val actualWeight = weights.nearestTo(requested + offset) ?: default
            Font(file = requireNotNull(byWeight[actualWeight]), weight = FontWeight(requested))
        }
    )
}

@OptIn(ExperimentalTextApi::class)
internal fun Typography.withAppFontFamily(fontFamily: FontFamily?): Typography {
    if (fontFamily == null) return this
    return copy(
        h1 = h1.withAppFontMetrics(fontFamily, 112.sp),
        h2 = h2.withAppFontMetrics(fontFamily, 72.sp),
        h3 = h3.withAppFontMetrics(fontFamily, 56.sp),
        h4 = h4.withAppFontMetrics(fontFamily, 40.sp),
        h5 = h5.withAppFontMetrics(fontFamily, 32.sp),
        h6 = h6.withAppFontMetrics(fontFamily, 24.sp),
        subtitle1 = subtitle1.withAppFontMetrics(fontFamily, 24.sp),
        subtitle2 = subtitle2.withAppFontMetrics(fontFamily, 20.sp),
        body1 = body1.withAppFontMetrics(fontFamily, 24.sp),
        body2 = body2.withAppFontMetrics(fontFamily, 20.sp),
        button = button.withAppFontMetrics(fontFamily, 20.sp),
        caption = caption.withAppFontMetrics(fontFamily, 16.sp),
        overline = overline.withAppFontMetrics(fontFamily, 16.sp)
    )
}

@OptIn(ExperimentalTextApi::class)
private fun TextStyle.withAppFontMetrics(
    fontFamily: FontFamily,
    stableLineHeight: TextUnit
): TextStyle = copy(
    fontFamily = fontFamily,
    lineHeight = stableLineHeight,
    platformStyle = AppFontPlatformStyle,
    lineHeightStyle = AppFontLineHeightStyle
)

@OptIn(ExperimentalTextApi::class)
private val AppFontPlatformStyle = PlatformTextStyle(includeFontPadding = false)

private val AppFontLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both
)

private val StandardFontWeights = (100..900 step 100).toList()
