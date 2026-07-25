package com.lhtstudio.kigtts.app.ui

import com.lhtstudio.kigtts.app.data.QuickTextGesturePoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickTextGestureRecognizerTest {
    @Test
    fun deliberateBentStrokePassesGeometryGuard() {
        val points = sampled(
            QuickTextGesturePoint(30f, 260f),
            QuickTextGesturePoint(30f, 40f),
            QuickTextGesturePoint(160f, 220f),
            QuickTextGesturePoint(290f, 40f),
            QuickTextGesturePoint(290f, 260f)
        )

        assertTrue(isDeliberateQuickTextGesture(points, 720L, 320f, 300f))
    }

    @Test
    fun ordinaryHorizontalSwipeDoesNotPassGeometryGuard() {
        val points = (0..20).map { index ->
            QuickTextGesturePoint(20f + index * 14f, 150f + (index % 2))
        }

        assertFalse(isDeliberateQuickTextGesture(points, 360L, 320f, 300f))
    }

    @Test
    fun shortOrTinyStrokeDoesNotPassGeometryGuard() {
        val points = (0..10).map { index ->
            QuickTextGesturePoint(100f + index, 100f + index)
        }

        assertFalse(isDeliberateQuickTextGesture(points, 80L, 320f, 300f))
        assertFalse(isDeliberateQuickTextGesture(points, 500L, 320f, 300f))
    }

    private fun sampled(vararg anchors: QuickTextGesturePoint): List<QuickTextGesturePoint> =
        anchors.toList().zipWithNext().flatMapIndexed { segmentIndex, (start, end) ->
            (0 until 8).map { step ->
                val amount = step / 8f
                QuickTextGesturePoint(
                    x = start.x + (end.x - start.x) * amount,
                    y = start.y + (end.y - start.y) * amount
                )
            }.let { values ->
                if (segmentIndex == anchors.lastIndex - 1) values + listOf(end) else values
            }
        }
}
