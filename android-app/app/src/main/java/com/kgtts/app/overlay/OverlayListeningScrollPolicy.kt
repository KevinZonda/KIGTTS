package com.lhtstudio.kigtts.app.overlay

internal object OverlayListeningScrollPolicy {
    fun maximumScrollY(
        childHeightPx: Int,
        viewportHeightPx: Int,
        paddingTopPx: Int,
        paddingBottomPx: Int
    ): Int {
        return (childHeightPx + paddingTopPx + paddingBottomPx - viewportHeightPx)
            .coerceAtLeast(0)
    }

    fun isNearBottom(
        scrollYPx: Int,
        maximumScrollYPx: Int,
        thresholdPx: Int,
        canScrollForward: Boolean
    ): Boolean {
        return !canScrollForward || maximumScrollYPx - scrollYPx <= thresholdPx
    }
}
