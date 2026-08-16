package com.lhtstudio.kigtts.app.overlay

import android.content.Context
import android.text.TextUtils
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
    private val backgroundView: View,
    private val timeView: TextClock,
    private val dateView: TextView,
    private val batteryGroup: LinearLayout,
    private val batteryView: TextView,
    private val unlockHint: LinearLayout,
    private val unlockIcon: TextView,
    private val unlockText: TextView,
    private val dp: (Int) -> Int
) {
    private var currentMode = LockScreenLayoutMode.PhonePortrait
    private var currentTimeGroup: LinearLayout? = null
    private var miniOverlayVisible = false
    private var listeningOverlayVisible = false
    private var listeningTopClearancePx: Int? = null
    private var batteryVisible = false
    private var timeAndDateAlignedStart = false

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
        unlockText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        unlockIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f)
        detachReusableViews()
        root.removeAllViews()
        root.addView(
            backgroundView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                leftMargin = -root.paddingLeft
                rightMargin = -root.paddingRight
                bottomMargin = -root.paddingBottom
            }
        )
        val compactClock = useCompactClock(mode)
        val timeGroup = if (compactClock) createCompactTimeGroup() else createTimeGroup()
        currentTimeGroup = timeGroup
        when {
            compactClock -> applyCompact(mode, screenWidth, metrics.density, timeGroup)
            mode.isPortrait -> applyPortrait(mode, timeGroup)
            mode == LockScreenLayoutMode.LargeSquare -> applyLargeSquare(timeGroup)
            else -> applyWide(mode, screenWidth, metrics.density, timeGroup)
        }
        updateClockVisibility(animate = false)
    }

    fun setMiniOverlayVisible(visible: Boolean) {
        if (miniOverlayVisible == visible) return
        miniOverlayVisible = visible
        if (listeningOverlayVisible) apply() else updateClockVisibility(animate = true)
    }

    fun setListeningOverlayVisible(visible: Boolean) {
        if (listeningOverlayVisible == visible) return
        listeningOverlayVisible = visible
        if (!visible) listeningTopClearancePx = null
        apply()
    }

    fun setListeningTopClearance(clearancePx: Int?) {
        val wasCompact = useCompactClock(currentMode)
        listeningTopClearancePx = clearancePx
        if (wasCompact != useCompactClock(currentMode)) apply()
    }

    fun setBatteryVisible(visible: Boolean) {
        if (batteryVisible == visible) return
        batteryVisible = visible
        if (useCompactClock(currentMode)) apply()
    }

    fun setTimeAndDateAlignedStart(alignedStart: Boolean) {
        if (timeAndDateAlignedStart == alignedStart) return
        timeAndDateAlignedStart = alignedStart
        apply()
    }

    private fun detachReusableViews() {
        (timeView.parent as? ViewGroup)?.removeView(timeView)
        (dateView.parent as? ViewGroup)?.removeView(dateView)
        (batteryGroup.parent as? ViewGroup)?.removeView(batteryGroup)
        (unlockHint.parent as? ViewGroup)?.removeView(unlockHint)
        (backgroundView.parent as? ViewGroup)?.removeView(backgroundView)
    }

    private fun createTimeGroup(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val horizontalGravity = if (timeAndDateAlignedStart) Gravity.START else Gravity.CENTER
        gravity = horizontalGravity
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        batteryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        timeView.gravity = horizontalGravity
        dateView.gravity = horizontalGravity
        batteryGroup.gravity = horizontalGravity or Gravity.CENTER_VERTICAL
        val horizontalPadding = if (timeAndDateAlignedStart) dp(28) else 0
        setPadding(horizontalPadding, 0, horizontalPadding, 0)
        addView(timeView, wrapContentParams())
        addView(dateView, wrapContentParams().apply { topMargin = dp(8) })
        addView(batteryGroup, wrapContentParams().apply { topMargin = dp(4) })
    }

    private fun createCompactTimeGroup(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, 0)
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        batteryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        timeView.setSingleLine(true)
        dateView.setSingleLine(true)
        dateView.ellipsize = TextUtils.TruncateAt.END
        batteryView.setSingleLine(true)
        timeView.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        dateView.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        batteryView.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        addView(
            compactSlot(timeView, Gravity.START),
            compactFixedSlotParams()
        )
        addView(
            compactSlot(
                dateView,
                if (batteryVisible) Gravity.CENTER else Gravity.END
            ),
            compactFlexibleSlotParams().apply {
                marginStart = dp(12)
                if (batteryVisible) marginEnd = dp(12)
            }
        )
        if (batteryVisible) {
            addView(
                compactSlot(batteryGroup, Gravity.END),
                compactFixedSlotParams()
            )
        }
    }

    private fun compactSlot(child: View, gravity: Int): FrameLayout = FrameLayout(context).apply {
        addView(
            child,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                gravity or Gravity.CENTER_VERTICAL
            )
        )
    }

    private fun compactFixedSlotParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        dp(34)
    )

    private fun compactFlexibleSlotParams() = LinearLayout.LayoutParams(
        0,
        dp(34),
        1f
    )

    private fun applyCompact(
        mode: LockScreenLayoutMode,
        screenWidth: Int,
        density: Float,
        timeGroup: LinearLayout
    ) {
        configureUnlockHint(horizontal = false)
        val frame = LockScreenLayoutPolicy.compactClockFrame(
            mode = mode,
            screenWidthPx = screenWidth,
            density = density,
            sideMarginPx = dp(16),
            overlayHorizontalPaddingPx = dp(10)
        )
        root.addView(
            timeGroup,
            FrameLayout.LayoutParams(
                frame.widthPx,
                dp(34),
                Gravity.TOP or Gravity.START
            ).apply {
                leftMargin = frame.leftPx
                topMargin = dp(48)
            }
        )
        addPortraitUnlockHint()
    }

    private fun applyPortrait(mode: LockScreenLayoutMode, timeGroup: LinearLayout) {
        configureUnlockHint(horizontal = false)
        timeView.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            if (mode == LockScreenLayoutMode.TabletPortrait) 72f else 68f
        )
        root.addView(
            timeGroup,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = dp(54) }
        )
        addPortraitUnlockHint()
    }

    private fun applyLargeSquare(timeGroup: LinearLayout) {
        configureUnlockHint(horizontal = false)
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
        root.addView(
            timeGroup,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = dp(44) }
        )
        addPortraitUnlockHint()
    }

    private fun applyWide(
        mode: LockScreenLayoutMode,
        screenWidth: Int,
        density: Float,
        timeGroup: LinearLayout
    ) {
        val sideMargin = dp(16)
        val overlayWidth = LockScreenLayoutPolicy.overlayWidthPx(
            mode,
            screenWidth,
            density,
            sideMargin
        )
        val overlayLeft = LockScreenLayoutPolicy.overlayLeftPx(
            mode,
            screenWidth,
            overlayWidth,
            sideMargin
        )
        val phoneLandscapeListening =
            mode == LockScreenLayoutMode.PhoneLandscape && listeningOverlayVisible
        val contentBoundary = if (phoneLandscapeListening) {
            LockScreenLayoutPolicy.phoneLandscapeListeningSafeLeftPx(
                safeLeftPx = root.paddingLeft,
                safeRightPx = screenWidth - root.paddingRight,
                density = density
            )
        } else {
            overlayLeft
        }
        val columnWidth = if (mode == LockScreenLayoutMode.TabletLandscape) {
            (screenWidth / 2 - sideMargin * 2).coerceAtLeast(dp(220))
        } else {
            LockScreenLayoutPolicy.hostColumnWidthPx(
                overlayLeftPx = contentBoundary,
                contentStartInsetPx = root.paddingLeft,
                startMarginPx = sideMargin,
                overlayGapPx = dp(24),
                minimumWidthPx = if (phoneLandscapeListening) dp(84) else dp(220)
            )
        }
        val infoScale = if (phoneLandscapeListening) {
            LockScreenLayoutPolicy.phoneLandscapeInfoScale(columnWidth, density)
        } else {
            1f
        }
        timeView.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            (if (mode == LockScreenLayoutMode.TabletLandscape) 72f else 54f) * infoScale
        )
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * maxOf(infoScale, 0.8f))
        batteryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * maxOf(infoScale, 0.8f))
        unlockText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * infoScale)
        unlockIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f * infoScale)
        timeGroup.setPadding(0, 0, 0, 0)
        configureUnlockHint(horizontal = timeAndDateAlignedStart)
        val infoColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (timeAndDateAlignedStart) Gravity.START else Gravity.CENTER
            addView(
                timeGroup,
                LinearLayout.LayoutParams(
                    if (phoneLandscapeListening) {
                        ViewGroup.LayoutParams.MATCH_PARENT
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    },
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                unlockHint,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(44) }
            )
        }
        val leftColumn = FrameLayout(context).apply {
            addView(
                infoColumn,
                FrameLayout.LayoutParams(
                    if (phoneLandscapeListening) {
                        ViewGroup.LayoutParams.MATCH_PARENT
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    },
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
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

    private fun useCompactClock(mode: LockScreenLayoutMode): Boolean =
        LockScreenLayoutPolicy.useCompactClock(
            mode = mode,
            miniOverlayVisible = miniOverlayVisible,
            listeningOverlayVisible = listeningOverlayVisible,
            listeningTopClearancePx = listeningTopClearancePx,
            normalClockRequiredHeightPx = dp(186)
        )

    private fun configureUnlockHint(horizontal: Boolean) {
        (unlockIcon.parent as? ViewGroup)?.removeView(unlockIcon)
        (unlockText.parent as? ViewGroup)?.removeView(unlockText)
        unlockHint.removeAllViews()
        unlockHint.orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        unlockHint.gravity = if (horizontal) {
            Gravity.START or Gravity.CENTER_VERTICAL
        } else {
            Gravity.CENTER
        }
        unlockIcon.text = if (horizontal) "chevron_right" else "keyboard_arrow_up"
        if (horizontal) {
            unlockHint.addView(
                unlockText,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            unlockHint.addView(
                unlockIcon,
                LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(6) }
            )
        } else {
            unlockHint.addView(unlockIcon, LinearLayout.LayoutParams(dp(36), dp(32)))
            unlockHint.addView(
                unlockText,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun addPortraitUnlockHint() {
        root.addView(
            unlockHint,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = dp(34) }
        )
    }

    private fun wrapContentParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
