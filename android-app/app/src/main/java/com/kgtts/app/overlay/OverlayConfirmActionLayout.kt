package com.lhtstudio.kigtts.app.overlay

internal data class OverlayConfirmActionLayout(
    val firstLeft: Float,
    val firstTop: Float,
    val secondLeft: Float,
    val secondTop: Float
)

internal fun calculateOverlayConfirmActionLayout(
    landscape: Boolean,
    anchorCenterX: Float,
    anchorCenterY: Float,
    anchorWidth: Float,
    anchorHeight: Float,
    actionSize: Float,
    gap: Float,
    containerWidth: Float,
    containerHeight: Float,
    padding: Float
): OverlayConfirmActionLayout {
    val minLeft = padding
    val maxLeft = maxOf(minLeft, containerWidth - actionSize - padding)
    val minTop = padding
    val maxTop = maxOf(minTop, containerHeight - actionSize - padding)

    return if (landscape) {
        val axisLeft = (anchorCenterX - actionSize / 2f).coerceIn(minLeft, maxLeft)
        val centerOffset = anchorHeight / 2f + gap + actionSize / 2f
        OverlayConfirmActionLayout(
            firstLeft = axisLeft,
            firstTop = (anchorCenterY - centerOffset - actionSize / 2f).coerceIn(minTop, maxTop),
            secondLeft = axisLeft,
            secondTop = (anchorCenterY + centerOffset - actionSize / 2f).coerceIn(minTop, maxTop)
        )
    } else {
        val axisTop = (anchorCenterY - actionSize / 2f).coerceIn(minTop, maxTop)
        val centerOffset = anchorWidth / 2f + gap + actionSize / 2f
        OverlayConfirmActionLayout(
            firstLeft = (anchorCenterX - centerOffset - actionSize / 2f).coerceIn(minLeft, maxLeft),
            firstTop = axisTop,
            secondLeft = (anchorCenterX + centerOffset - actionSize / 2f).coerceIn(minLeft, maxLeft),
            secondTop = axisTop
        )
    }
}
