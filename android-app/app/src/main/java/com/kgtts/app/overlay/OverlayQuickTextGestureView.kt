package com.lhtstudio.kigtts.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.lhtstudio.kigtts.app.data.QuickTextGestureBinding
import com.lhtstudio.kigtts.app.data.QuickTextGesturePoint
import com.lhtstudio.kigtts.app.data.QuickTextGestureSettings
import com.lhtstudio.kigtts.app.ui.QuickTextGestureRecognizer
import kotlin.math.hypot

internal class OverlayQuickTextGestureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var onGesture: ((QuickTextGestureBinding) -> Unit)? = null
    var onTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null
    var onScale: ((Float) -> Unit)? = null
    var onScaleFinished: (() -> Unit)? = null

    var gestureSettings: QuickTextGestureSettings = QuickTextGestureSettings()
        set(value) {
            val normalized = value.normalized()
            if (field == normalized) return
            field = normalized
            gestureInputEnabled = field.enabled && field.activeBindings().isNotEmpty()
            recognizer = QuickTextGestureRecognizer(field)
            clearTrace()
        }

    var traceColor: Int
        get() = tracePaint.color
        set(value) {
            tracePaint.color = value
            invalidate()
        }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()
    private val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xff038387.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 6f * resources.displayMetrics.density
    }
    private val tracePath = Path()
    private val sampledPoints = mutableListOf<QuickTextGesturePoint>()
    private var recognizer = QuickTextGestureRecognizer(gestureSettings)
    private var gestureInputEnabled = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var downTimeMs = 0L
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var pinchActive = false
    private var scaleChanged = false
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleChanged = true
                onScale?.invoke(detector.scaleFactor)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                if (scaleChanged) {
                    onScaleFinished?.invoke()
                    scaleChanged = false
                }
            }
        }
    )

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "大字幕手势区域"
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount >= 2 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            pinchActive = true
            clearTrace()
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                downTimeMs = event.eventTime
                downX = event.x
                downY = event.y
                moved = false
                pinchActive = false
                sampledPoints.clear()
                sampledPoints += QuickTextGesturePoint(event.x, event.y)
                tracePath.reset()
                tracePath.moveTo(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (pinchActive) return true
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return true
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                if (!moved && hypot(x - downX, y - downY) > touchSlop) moved = true
                if (gestureInputEnabled) {
                    appendHistoricalPoints(event, pointerIndex)
                    appendPoint(x, y)
                    if (moved) invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val consumedByPinch = pinchActive
                if (!consumedByPinch) {
                    if (gestureInputEnabled && moved) {
                        appendPoint(event.x, event.y)
                        recognizer.recognize(
                            points = sampledPoints.toList(),
                            durationMs = event.eventTime - downTimeMs,
                            surfaceWidth = width.toFloat(),
                            surfaceHeight = height.toFloat()
                        )?.let { onGesture?.invoke(it) }
                    } else if (!moved) {
                        if (event.eventTime - downTimeMs >= longPressTimeoutMs) {
                            onLongPress?.invoke()
                        } else {
                            performClick()
                        }
                    }
                }
                resetTouchState()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                resetTouchState()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap?.invoke()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (gestureInputEnabled && moved && !pinchActive) {
            canvas.drawPath(tracePath, tracePaint)
        }
    }

    private fun appendHistoricalPoints(event: MotionEvent, pointerIndex: Int) {
        for (historyIndex in 0 until event.historySize) {
            appendPoint(
                event.getHistoricalX(pointerIndex, historyIndex),
                event.getHistoricalY(pointerIndex, historyIndex)
            )
        }
    }

    private fun appendPoint(x: Float, y: Float) {
        val previous = sampledPoints.lastOrNull()
        if (previous != null && hypot(x - previous.x, y - previous.y) < 1f) return
        sampledPoints += QuickTextGesturePoint(x, y)
        tracePath.lineTo(x, y)
    }

    private fun resetTouchState() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        moved = false
        pinchActive = false
        clearTrace()
    }

    private fun clearTrace() {
        sampledPoints.clear()
        tracePath.reset()
        invalidate()
    }
}
