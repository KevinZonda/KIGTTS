package com.lhtstudio.kigtts.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedMarqueeMotionStateTest {
    @Test
    fun automaticMotionResumesAfterDragAndFling() {
        val state = LedMarqueeMotionState()

        state.advance(seconds = 1f, autoVelocityPxPerSecond = 100f)
        assertEquals(100f, state.positionPx)

        state.beginHorizontalDrag()
        state.dragBy(-40f)
        state.advance(seconds = 1f, autoVelocityPxPerSecond = 100f)
        assertEquals(140f, state.positionPx)

        state.endHorizontalDrag(velocityXPxPerSecond = -800f)
        state.advance(seconds = 0.1f, autoVelocityPxPerSecond = 100f)
        assertTrue(state.positionPx > 140f)

        repeat(30) {
            state.advance(seconds = 0.1f, autoVelocityPxPerSecond = 100f)
        }
        val afterFling = state.positionPx
        state.advance(seconds = 1f, autoVelocityPxPerSecond = 100f)
        assertTrue(state.positionPx >= afterFling + 99f)
    }

    @Test
    fun dragDirectionMapsToLoopPhase() {
        val state = LedMarqueeMotionState()

        state.beginHorizontalDrag()
        state.dragBy(25f)
        state.endHorizontalDrag(0f)

        assertEquals(75f, state.phaseFor(100f))
    }
}
