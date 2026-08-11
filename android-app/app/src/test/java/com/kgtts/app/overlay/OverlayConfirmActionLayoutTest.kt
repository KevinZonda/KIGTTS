package com.lhtstudio.kigtts.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayConfirmActionLayoutTest {
    @Test
    fun portraitActionsShareMicrophoneHorizontalAxis() {
        val anchorCenterY = 420f

        val layout = calculateOverlayConfirmActionLayout(
            landscape = false,
            anchorCenterX = 180f,
            anchorCenterY = anchorCenterY,
            anchorWidth = 74f,
            anchorHeight = 74f,
            actionSize = 64f,
            gap = 16f,
            containerWidth = 360f,
            containerHeight = 520f,
            padding = 12f
        )

        assertEquals(anchorCenterY, layout.firstTop + 32f, 0.001f)
        assertEquals(anchorCenterY, layout.secondTop + 32f, 0.001f)
    }

    @Test
    fun landscapeActionsShareMicrophoneVerticalAxis() {
        val anchorCenterX = 330f

        val layout = calculateOverlayConfirmActionLayout(
            landscape = true,
            anchorCenterX = anchorCenterX,
            anchorCenterY = 160f,
            anchorWidth = 74f,
            anchorHeight = 74f,
            actionSize = 64f,
            gap = 16f,
            containerWidth = 400f,
            containerHeight = 320f,
            padding = 12f
        )

        assertEquals(anchorCenterX, layout.firstLeft + 32f, 0.001f)
        assertEquals(anchorCenterX, layout.secondLeft + 32f, 0.001f)
    }

    @Test
    fun actionsRemainAxisAlignedWhenContainerClampsThem() {
        val portrait = calculateOverlayConfirmActionLayout(
            landscape = false,
            anchorCenterX = 180f,
            anchorCenterY = 500f,
            anchorWidth = 74f,
            anchorHeight = 74f,
            actionSize = 64f,
            gap = 16f,
            containerWidth = 360f,
            containerHeight = 520f,
            padding = 12f
        )
        val landscape = calculateOverlayConfirmActionLayout(
            landscape = true,
            anchorCenterX = 390f,
            anchorCenterY = 160f,
            anchorWidth = 74f,
            anchorHeight = 74f,
            actionSize = 64f,
            gap = 16f,
            containerWidth = 400f,
            containerHeight = 320f,
            padding = 12f
        )

        assertEquals(portrait.firstTop, portrait.secondTop, 0.001f)
        assertEquals(landscape.firstLeft, landscape.secondLeft, 0.001f)
    }
}
