package com.lhtstudio.kigtts.app.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.lhtstudio.kigtts.app.R

internal class OverlayTextContextMenuController(
    private val context: Context,
    private val host: FrameLayout,
    private val field: OverlaySelectionEditText,
    private val styleProvider: () -> OverlayInteractionStyle
) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private var menuView: LinearLayout? = null
    private var hideGeneration = 0

    init {
        field.onContextMenuRequested = ::show
        field.onContextMenuDismissRequested = { hide() }
    }

    fun show() {
        if (!host.isAttachedToWindow || !field.isAttachedToWindow) return
        val actions = availableActions()
        if (actions.isEmpty()) {
            hide()
            return
        }
        val menu = menuView ?: createMenu().also {
            menuView = it
            host.addView(
                it,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        populateMenu(menu, actions)
        menu.visibility = View.VISIBLE
        menu.bringToFront()
        hideGeneration++
        menu.animate().cancel()
        menu.alpha = 0f
        menu.scaleX = 0.94f
        menu.scaleY = 0.94f
        positionMenu(menu)
        menu.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(ANIMATION_DURATION_MS)
            .start()
    }

    fun hide(immediate: Boolean = false) {
        val menu = menuView ?: return
        if (menu.visibility != View.VISIBLE) return
        val generation = ++hideGeneration
        menu.animate().cancel()
        if (immediate || !host.isAttachedToWindow) {
            menu.visibility = View.GONE
            menu.alpha = 0f
            return
        }
        menu.animate()
            .alpha(0f)
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(ANIMATION_DURATION_MS)
            .withEndAction {
                if (generation == hideGeneration) menu.visibility = View.GONE
            }
            .start()
    }

    fun dispose() {
        field.onContextMenuRequested = null
        field.onContextMenuDismissRequested = null
        hide(immediate = true)
        menuView?.let { menu ->
            if (menu.parent === host) host.removeView(menu)
        }
        menuView = null
    }

    private fun availableActions(): List<MenuAction> {
        val value = field.text?.toString().orEmpty()
        val start = minOf(field.selectionStart, field.selectionEnd).coerceAtLeast(0)
        val end = maxOf(field.selectionStart, field.selectionEnd).coerceAtLeast(0)
        val hasSelection = end > start
        return listOfNotNull(
            MenuAction("select_all", "全选", ::selectAll).takeIf {
                value.isNotEmpty() && (start != 0 || end != value.length)
            },
            MenuAction("content_cut", "剪切", ::cut).takeIf { hasSelection && field.isEnabled },
            MenuAction("content_copy", "复制", ::copy).takeIf { hasSelection },
            MenuAction("content_paste", "粘贴", ::paste).takeIf {
                field.isEnabled && clipboard?.hasPrimaryClip() == true
            }
        )
    }

    private fun createMenu(): LinearLayout {
        val style = styleProvider()
        val dark = ColorUtils.calculateLuminance(style.surfaceColor) < 0.5
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = roundedDrawable(if (dark) DARK_BACKGROUND else Color.WHITE, 4f)
            elevation = dp(8).toFloat()
            isClickable = true
            isFocusable = false
            pivotY = 0f
        }
    }

    private fun populateMenu(menu: LinearLayout, actions: List<MenuAction>) {
        menu.removeAllViews()
        val style = styleProvider()
        val dark = ColorUtils.calculateLuminance(style.surfaceColor) < 0.5
        val contentColor = if (dark) DARK_CONTENT else LIGHT_CONTENT
        val rippleColor = if (dark) {
            ColorUtils.setAlphaComponent(Color.WHITE, 40)
        } else {
            ColorUtils.setAlphaComponent(style.accentColor, 38)
        }
        actions.forEach { action ->
            menu.addView(
                TextView(context).apply {
                    text = action.icon
                    contentDescription = action.description
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    textSize = 24f
                    typeface = style.iconTypeface
                    setTag(R.id.overlay_font_excluded, true)
                    fontFeatureSettings = "liga"
                    isSingleLine = true
                    letterSpacing = 0f
                    setTextColor(contentColor)
                    includeFontPadding = false
                    background = rippleDrawable(rippleColor, 8f)
                    setOnClickListener {
                        action.onClick()
                        hide()
                        field.requestFocus()
                    }
                },
                LinearLayout.LayoutParams(dp(40), dp(40))
            )
        }
    }

    private fun positionMenu(menu: LinearLayout) {
        menu.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val hostLocation = IntArray(2).also(host::getLocationOnScreen)
        val fieldLocation = IntArray(2).also(field::getLocationOnScreen)
        val layout = field.layout
        val start = minOf(field.selectionStart, field.selectionEnd).coerceAtLeast(0)
        val end = maxOf(field.selectionStart, field.selectionEnd).coerceAtLeast(start)
        val anchorOffset = start.coerceAtMost(field.text?.length ?: 0)
        val line = layout?.getLineForOffset(anchorOffset) ?: 0
        val startX = layout?.getPrimaryHorizontal(anchorOffset)?.toInt() ?: 0
        val endX = if (layout != null && end > start && layout.getLineForOffset(end) == line) {
            layout.getPrimaryHorizontal(end).toInt()
        } else {
            startX
        }
        val fieldLeft = fieldLocation[0] - hostLocation[0]
        val fieldTop = fieldLocation[1] - hostLocation[1]
        val anchorCenterX = fieldLeft + field.totalPaddingLeft - field.scrollX + (startX + endX) / 2
        val anchorTop = fieldTop + field.totalPaddingTop - field.scrollY + (layout?.getLineTop(line) ?: 0)
        val anchorBottom = fieldTop + field.totalPaddingTop - field.scrollY +
            (layout?.getLineBottom(line) ?: field.height)
        val margin = dp(8)
        val maxLeft = (host.width - menu.measuredWidth - margin).coerceAtLeast(margin)
        val left = (anchorCenterX - menu.measuredWidth / 2).coerceIn(margin, maxLeft)
        val above = anchorTop - menu.measuredHeight - margin
        val maxTop = (host.height - menu.measuredHeight - margin).coerceAtLeast(margin)
        val top = if (above >= margin) above else (anchorBottom + margin).coerceAtMost(maxTop)
        (menu.layoutParams as FrameLayout.LayoutParams).apply {
            leftMargin = left
            topMargin = top
            gravity = Gravity.TOP or Gravity.START
            menu.layoutParams = this
        }
        menu.pivotX = menu.measuredWidth / 2f
    }

    private fun selectAll() {
        field.selectAll()
    }

    private fun copy() {
        val range = selectedRange() ?: return
        val selected = field.text?.subSequence(range.first, range.last + 1)?.toString().orEmpty()
        clipboard?.setPrimaryClip(ClipData.newPlainText("文本", selected))
    }

    private fun cut() {
        val range = selectedRange() ?: return
        copy()
        field.text?.delete(range.first, range.last + 1)
    }

    private fun paste() {
        val pasted = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: return
        val start = minOf(field.selectionStart, field.selectionEnd).coerceAtLeast(0)
        val end = maxOf(field.selectionStart, field.selectionEnd).coerceAtLeast(start)
        field.text?.replace(start, end, pasted)
    }

    private fun selectedRange(): IntRange? {
        val start = minOf(field.selectionStart, field.selectionEnd).coerceAtLeast(0)
        val end = maxOf(field.selectionStart, field.selectionEnd).coerceAtLeast(0)
        return if (end > start) start until end else null
    }

    private fun roundedDrawable(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun rippleDrawable(color: Int, radiusDp: Float): RippleDrawable = RippleDrawable(
        ColorStateList.valueOf(color),
        ColorDrawable(Color.TRANSPARENT),
        roundedDrawable(Color.WHITE, radiusDp)
    )

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private data class MenuAction(
        val icon: String,
        val description: String,
        val onClick: () -> Unit
    )

    private companion object {
        const val ANIMATION_DURATION_MS = 180L
        const val DARK_BACKGROUND = 0xFF2C2F33.toInt()
        const val DARK_CONTENT = 0xFFE9EDF1.toInt()
        const val LIGHT_CONTENT = 0xFF202428.toInt()
    }
}
