package com.lhtstudio.kigtts.app.ui

import android.gesture.Gesture
import android.gesture.GesturePoint
import android.gesture.GestureStore
import android.gesture.GestureStroke
import com.lhtstudio.kigtts.app.data.QuickTextGestureBinding
import com.lhtstudio.kigtts.app.data.QuickTextGesturePoint
import com.lhtstudio.kigtts.app.data.QuickTextGestureSettings
import com.lhtstudio.kigtts.app.data.QuickTextGestures
import java.util.ArrayList
import kotlin.math.hypot

internal class QuickTextGestureRecognizer(settings: QuickTextGestureSettings) {
    private val activeBindings = settings.activeBindings().associateBy { it.gestureId }
    private val store = GestureStore().apply {
        orientationStyle = GestureStore.ORIENTATION_SENSITIVE
        sequenceType = GestureStore.SEQUENCE_SENSITIVE
        QuickTextGestures.templates.forEach { template ->
            if (template.id in activeBindings) {
                addGesture(template.id, template.points.toAndroidGesture())
            }
        }
    }

    fun recognize(
        points: List<QuickTextGesturePoint>,
        durationMs: Long,
        surfaceWidth: Float,
        surfaceHeight: Float
    ): QuickTextGestureBinding? {
        if (
            activeBindings.isEmpty() ||
            !isDeliberateQuickTextGesture(points, durationMs, surfaceWidth, surfaceHeight)
        ) {
            return null
        }
        val predictions = store.recognize(points.toAndroidGesture()).sortedByDescending { it.score }
        val best = predictions.firstOrNull() ?: return null
        if (best.score < MIN_RECOGNITION_SCORE) return null
        val runnerUpScore = predictions.getOrNull(1)?.score ?: 0.0
        if (runnerUpScore > 0.0 && best.score / runnerUpScore < MIN_SCORE_RATIO) return null
        return activeBindings[best.name]
    }

    private fun List<QuickTextGesturePoint>.toAndroidGesture(): Gesture {
        val gesturePoints = ArrayList<GesturePoint>(size)
        forEachIndexed { index, point ->
            gesturePoints += GesturePoint(point.x, point.y, index * SAMPLE_INTERVAL_MS)
        }
        return Gesture().apply {
            addStroke(GestureStroke(gesturePoints))
        }
    }

    private companion object {
        const val MIN_RECOGNITION_SCORE = 1.8
        const val MIN_SCORE_RATIO = 1.12
        const val SAMPLE_INTERVAL_MS = 16L
    }
}

internal fun isDeliberateQuickTextGesture(
    points: List<QuickTextGesturePoint>,
    durationMs: Long,
    surfaceWidth: Float,
    surfaceHeight: Float
): Boolean {
    if (points.size < 8 || durationMs !in 120L..5_000L) return false
    if (surfaceWidth <= 0f || surfaceHeight <= 0f) return false

    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val spanX = maxX - minX
    val spanY = maxY - minY
    if (spanX < surfaceWidth * 0.12f || spanY < surfaceHeight * 0.14f) return false

    val pathLength = points.zipWithNext().sumOf { (start, end) ->
        hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble())
    }.toFloat()
    val boundsDiagonal = hypot(spanX.toDouble(), spanY.toDouble()).toFloat()
    val minSurfaceSide = minOf(surfaceWidth, surfaceHeight)
    return pathLength >= minSurfaceSide * 0.72f &&
        boundsDiagonal > 0f &&
        pathLength / boundsDiagonal >= 1.32f
}
