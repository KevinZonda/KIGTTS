package com.lhtstudio.kigtts.app.overlay

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max

internal enum class OverlayQuickPanelGestureAction {
    OpenCandidates,
    OpenInput
}

internal fun resolveOverlayQuickPanelGesture(
    primaryDelta: Float,
    horizontalLayout: Boolean,
    reversed: Boolean
): OverlayQuickPanelGestureAction {
    val towardCandidates = if (horizontalLayout) primaryDelta > 0f else primaryDelta < 0f
    val effectiveTowardCandidates = if (reversed) !towardCandidates else towardCandidates
    return if (effectiveTowardCandidates) {
        OverlayQuickPanelGestureAction.OpenCandidates
    } else {
        OverlayQuickPanelGestureAction.OpenInput
    }
}

internal class OverlayQuickPanelGestureFrame @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    var gesturesEnabled: Boolean = true
    var landscapeGesture: Boolean = false
    var reversedGesture: Boolean = false
    var onOpenCandidates: (() -> Unit)? = null
    var onOpenInput: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val triggerDistance = max(touchSlop * 2.5f, 44f * resources.displayMetrics.density)
    private var downX = 0f
    private var downY = 0f
    private var intercepting = false
    private var triggered = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!gesturesEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                intercepting = false
                triggered = false
            }

            MotionEvent.ACTION_MOVE -> {
                val primary = primaryDelta(event)
                val secondary = secondaryDelta(event)
                if (abs(primary) > touchSlop && abs(primary) > abs(secondary) * 1.2f) {
                    intercepting = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                if (abs(secondary) > touchSlop && abs(secondary) > abs(primary)) {
                    return false
                }
            }
        }
        return intercepting
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!gesturesEnabled) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                intercepting = false
                triggered = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                triggerIfNeeded(event)
                return true
            }

            MotionEvent.ACTION_UP -> {
                triggerIfNeeded(event)
                parent?.requestDisallowInterceptTouchEvent(false)
                intercepting = false
                triggered = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                intercepting = false
                triggered = false
                return true
            }
        }
        return true
    }

    private fun primaryDelta(event: MotionEvent): Float =
        if (landscapeGesture) event.x - downX else event.y - downY

    private fun secondaryDelta(event: MotionEvent): Float =
        if (landscapeGesture) event.y - downY else event.x - downX

    private fun triggerIfNeeded(event: MotionEvent) {
        if (triggered) return
        val primary = primaryDelta(event)
        val secondary = secondaryDelta(event)
        if (abs(primary) < triggerDistance || abs(primary) <= abs(secondary) * 1.2f) return
        when (resolveOverlayQuickPanelGesture(primary, landscapeGesture, reversedGesture)) {
            OverlayQuickPanelGestureAction.OpenCandidates -> onOpenCandidates?.invoke()
            OverlayQuickPanelGestureAction.OpenInput -> onOpenInput?.invoke()
        }
        triggered = true
    }
}
