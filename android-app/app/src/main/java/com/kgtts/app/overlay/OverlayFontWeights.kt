package com.lhtstudio.kigtts.app.overlay

import com.lhtstudio.kigtts.app.data.AppFontWeightAxis
import com.lhtstudio.kigtts.app.data.nearestTo

internal data class OverlayFontWeights(
    val regular: Int,
    val bold: Int
)

internal fun resolveVariableOverlayFontWeights(
    axis: AppFontWeightAxis,
    sourceDefaultWeight: Int,
    preferredWeight: Int
): OverlayFontWeights {
    val adjustedAxis = axis.withDefault(sourceDefaultWeight)
    val selected = adjustedAxis.clamp(preferredWeight)
    val offset = selected - adjustedAxis.default
    return OverlayFontWeights(
        regular = adjustedAxis.clamp(RegularWeight + offset),
        bold = adjustedAxis.clamp(BoldWeight + offset)
    )
}

internal fun resolveStaticOverlayFontWeights(
    availableWeights: List<Int>,
    sourceDefaultWeight: Int,
    preferredWeight: Int
): OverlayFontWeights {
    val weights = availableWeights.distinct().sorted()
    require(weights.isNotEmpty()) { "字体字重文件不存在" }
    val default = weights.nearestTo(sourceDefaultWeight) ?: weights.first()
    val selected = weights.nearestTo(preferredWeight) ?: default
    val offset = selected - default
    return OverlayFontWeights(
        regular = weights.nearestTo(RegularWeight + offset) ?: default,
        bold = weights.nearestTo(BoldWeight + offset) ?: weights.last()
    )
}

private const val RegularWeight = 400
private const val BoldWeight = 700
