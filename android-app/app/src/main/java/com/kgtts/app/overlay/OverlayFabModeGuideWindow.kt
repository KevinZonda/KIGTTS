package com.lhtstudio.kigtts.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils

internal class OverlayFabModeGuideWindow(
    private val context: Context,
    private val windowManager: WindowManager,
    private val styleProvider: () -> OverlayInteractionStyle,
    private val onModeSelected: (keyboardFirst: Boolean) -> Unit
) {
    private var root: FrameLayout? = null

    val isShowing: Boolean
        get() = root?.isAttachedToWindow == true

    fun show(pushToTalkEnabled: Boolean) {
        if (isShowing) return
        val style = styleProvider()
        val nextRoot = FrameLayout(context).apply {
            setBackgroundColor(ColorUtils.setAlphaComponent(android.graphics.Color.BLACK, 150))
            isClickable = true
            isFocusable = true
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(16))
            background = roundedDrawable(style.surfaceColor, 4f)
            elevation = dp(8).toFloat()
        }
        card.addView(text("悬浮窗 FAB 操作方式", 20f, style.onSurfaceColor, style.boldTypeface))
        card.addView(space(dp(10)))
        val voiceDescription = if (pushToTalkEnabled) {
            "语音优先：按住说话；竖屏下滑或横屏右滑可打开键盘输入。"
        } else {
            "语音优先：点按开关语音识别，长按打开键盘输入。"
        }
        val keyboardDescription = if (pushToTalkEnabled) {
            "键盘优先：点按打开键盘输入，长按进行按住说话。"
        } else {
            "键盘优先：点按打开键盘输入，长按开关语音识别并显示状态提示。"
        }
        card.addView(text(voiceDescription, 14f, style.onSurfaceVariantColor, style.regularTypeface))
        card.addView(space(dp(6)))
        card.addView(text(keyboardDescription, 14f, style.onSurfaceVariantColor, style.regularTypeface))
        card.addView(space(dp(16)))
        card.addView(modeButton("keyboard", "键盘输入优先", style) { selectMode(true) })
        card.addView(space(dp(8)))
        card.addView(modeButton("mic", "语音识别优先", style) { selectMode(false) })
        nextRoot.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                leftMargin = dp(24)
                rightMargin = dp(24)
            }
        )
        root = nextRoot
        windowManager.addView(nextRoot, createLayoutParams())
    }

    fun dismiss() {
        val current = root ?: return
        runCatching { windowManager.removeViewImmediate(current) }
        root = null
    }

    private fun selectMode(keyboardFirst: Boolean) {
        onModeSelected(keyboardFirst)
        dismiss()
    }

    private fun modeButton(
        icon: String,
        label: String,
        style: OverlayInteractionStyle,
        onClick: () -> Unit
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = roundedDrawable(style.accentColor, 4f)
        addView(TextView(context).apply {
            text = icon
            typeface = style.iconTypeface
            textSize = 22f
            setTextColor(style.onAccentColor)
            gravity = Gravity.CENTER
        })
        addView(space(dp(8), horizontal = true))
        addView(text(label, 15f, style.onAccentColor, style.boldTypeface))
        setOnClickListener { onClick() }
    }

    private fun text(value: String, sizeSp: Float, color: Int, typeface: android.graphics.Typeface): TextView =
        TextView(context).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            this.typeface = typeface
        }

    private fun space(size: Int, horizontal: Boolean = false): android.view.View =
        android.view.View(context).apply {
            layoutParams = if (horizontal) {
                LinearLayout.LayoutParams(size, 1)
            } else {
                LinearLayout.LayoutParams(1, size)
            }
        }

    private fun roundedDrawable(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun createLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.FILL
        title = "KIGTTS overlay FAB guide"
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
