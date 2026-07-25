package com.lhtstudio.kigtts.app.overlay

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

internal class LockScreenHostLayoutController(
    private val context: Context,
    private val root: FrameLayout,
    private val timeView: TextClock,
    private val dateView: TextView,
    private val unlockHint: LinearLayout,
    private val dp: (Int) -> Int
) {
    private var currentMode = LockScreenLayoutMode.PhonePortrait
    private var currentTimeGroup: LinearLayout? = null
    private var miniOverlayVisible = false

    fun apply() {
        val metrics = context.resources.displayMetrics
        val screenWidth = root.width.takeIf { it > 0 } ?: metrics.widthPixels
        val screenHeight = root.height.takeIf { it > 0 } ?: metrics.heightPixels
        val mode = LockScreenLayoutPolicy.mode(
            screenWidthPx = screenWidth,
            screenHeightPx = screenHeight,
            density = metrics.density
        )
        currentMode = mode
        detachReusableViews()
        root.removeAllViews()
        val timeGroup = createTimeGroup()
        currentTimeGroup = timeGroup
        if (mode == LockScreenLayoutMode.PhonePortrait) {
            applyPortrait(timeGroup)
        } else {
            applyWide(mode, screenWidth, screenHeight, metrics.density, timeGroup)
        }
        updateClockVisibility(animate = false)
    }

    fun setMiniOverlayVisible(visible: Boolean) {
        if (miniOverlayVisible == visible) return
        miniOverlayVisible = visible
        updateClockVisibility(animate = true)
    }

    private fun detachReusableViews() {
        (timeView.parent as? ViewGroup)?.removeView(timeView)
        (dateView.parent as? ViewGroup)?.removeView(dateView)
        (unlockHint.parent as? ViewGroup)?.removeView(unlockHint)
    }

    private fun createTimeGroup(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(timeView, wrapContentParams())
        addView(dateView, wrapContentParams().apply { topMargin = dp(8) })
    }

    private fun applyPortrait(timeGroup: LinearLayout) {
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 68f)
        root.addView(
            timeGroup,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = dp(54) }
        )
        root.addView(
            unlockHint,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = dp(34) }
        )
    }

    private fun applyWide(
        mode: LockScreenLayoutMode,
        screenWidth: Int,
        screenHeight: Int,
        density: Float,
        timeGroup: LinearLayout
    ) {
        timeView.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            if (mode == LockScreenLayoutMode.Tablet) 72f else 54f
        )
        val sideMargin = dp(16)
        val overlayWidth = LockScreenLayoutPolicy.overlayWidthPx(
            mode,
            screenWidth,
            screenHeight,
            density,
            sideMargin
        )
        val overlayLeft = LockScreenLayoutPolicy.overlayLeftPx(
            mode,
            screenWidth,
            overlayWidth,
            sideMargin
        )
        val columnWidth = LockScreenLayoutPolicy.hostColumnWidthPx(
            overlayLeftPx = overlayLeft,
            contentStartInsetPx = root.paddingLeft,
            startMarginPx = sideMargin,
            overlayGapPx = dp(24),
            minimumWidthPx = dp(220)
        )
        val leftColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                timeGroup,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                unlockHint,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(44) }
            )
        }
        root.addView(
            leftColumn,
            FrameLayout.LayoutParams(
                columnWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START
            ).apply { leftMargin = sideMargin }
        )
    }

    private fun updateClockVisibility(animate: Boolean) {
        val group = currentTimeGroup ?: return
        val hide = LockScreenLayoutPolicy.hideClock(currentMode, miniOverlayVisible)
        group.animate().cancel()
        if (!animate) {
            group.alpha = if (hide) 0f else 1f
            group.visibility = if (hide) View.INVISIBLE else View.VISIBLE
            return
        }
        if (hide) {
            group.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction { group.visibility = View.INVISIBLE }
                .start()
        } else {
            group.visibility = View.VISIBLE
            group.animate().alpha(1f).setDuration(220L).start()
        }
    }

    private fun wrapContentParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
