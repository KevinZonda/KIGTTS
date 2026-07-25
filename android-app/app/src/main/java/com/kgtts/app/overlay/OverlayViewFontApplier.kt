package com.lhtstudio.kigtts.app.overlay

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.lhtstudio.kigtts.app.R
import kotlin.math.abs

internal class OverlayViewFontApplier {
    private var typefaces: OverlayTypefaces? = null

    private val hierarchyListener =
        object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                child?.let(::applyTree)
            }

            override fun onChildViewRemoved(parent: View?, child: View?) = Unit
        }

    fun updateTypefaces(next: OverlayTypefaces?) {
        typefaces = next
    }

    val hasCustomTypeface: Boolean
        get() = typefaces != null

    fun applyTo(vararg roots: View?) {
        roots.forEach { root -> root?.let(::applyTree) }
    }

    fun apply(textView: TextView, bold: Boolean) {
        textView.setTag(R.id.overlay_font_requested_bold, bold)
        textView.typeface = typeface(bold)
        applyFontMetrics(textView)
    }

    fun applyStableLineHeight(textView: TextView, multiplier: Float) {
        textView.setTag(R.id.overlay_font_line_height_multiplier, multiplier)
        applyFontMetrics(textView)
    }

    fun lineHeightPxFor(
        textSizePx: Float,
        scaledDensity: Float,
        multiplier: Float
    ): Int? =
        if (hasCustomTypeface) {
            resolveOverlayStableLineHeightPx(
                textSizePx = textSizePx,
                scaledDensity = scaledDensity,
                explicitMultiplier = multiplier
            )
        } else {
            null
        }

    fun styleText(text: CharSequence, bold: Boolean = false): CharSequence {
        if (!hasCustomTypeface || text.isEmpty()) return text
        return SpannableString(text).apply {
            setSpan(
                OverlayTypefaceSpan(typeface(bold)),
                0,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    fun exclude(textView: TextView) {
        textView.setTag(R.id.overlay_font_excluded, true)
    }

    fun typeface(bold: Boolean): Typeface =
        typefaces?.let { if (bold) it.bold else it.regular }
            ?: if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

    private fun applyFontMetrics(textView: TextView) {
        if (!hasCustomTypeface) {
            restoreFontMetrics(textView)
            return
        }
        captureOriginalFontMetrics(textView)
        textView.includeFontPadding = false
        ensureLineHeightListener(textView)
        updateStableLineHeight(textView)
    }

    private fun captureOriginalFontMetrics(textView: TextView) {
        if (textView.getTag(R.id.overlay_font_original_include_padding) != null) return
        textView.setTag(R.id.overlay_font_original_include_padding, textView.includeFontPadding)
        textView.setTag(R.id.overlay_font_original_line_spacing_extra, textView.lineSpacingExtra)
        textView.setTag(
            R.id.overlay_font_original_line_spacing_multiplier,
            textView.lineSpacingMultiplier
        )
    }

    private fun restoreFontMetrics(textView: TextView) {
        (textView.getTag(R.id.overlay_font_line_height_listener) as? View.OnLayoutChangeListener)
            ?.let(textView::removeOnLayoutChangeListener)
        textView.setTag(R.id.overlay_font_line_height_listener, null)
        (textView.getTag(R.id.overlay_font_original_include_padding) as? Boolean)?.let {
            textView.includeFontPadding = it
        }
        val extra = textView.getTag(R.id.overlay_font_original_line_spacing_extra) as? Float
        val multiplier =
            textView.getTag(R.id.overlay_font_original_line_spacing_multiplier) as? Float
        if (extra != null && multiplier != null) {
            textView.setLineSpacing(extra, multiplier)
        }
        textView.setTag(R.id.overlay_font_original_include_padding, null)
        textView.setTag(R.id.overlay_font_original_line_spacing_extra, null)
        textView.setTag(R.id.overlay_font_original_line_spacing_multiplier, null)
    }

    private fun ensureLineHeightListener(textView: TextView) {
        if (textView.getTag(R.id.overlay_font_line_height_listener) != null) return
        val listener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            updateStableLineHeight(view as TextView)
        }
        textView.addOnLayoutChangeListener(listener)
        textView.setTag(R.id.overlay_font_line_height_listener, listener)
    }

    private fun updateStableLineHeight(textView: TextView) {
        if (!hasCustomTypeface || textView.textSize <= 0f) return
        val explicitMultiplier =
            textView.getTag(R.id.overlay_font_line_height_multiplier) as? Float
        val targetLineHeight = resolveOverlayStableLineHeightPx(
            textSizePx = textView.textSize,
            scaledDensity = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                1f,
                textView.resources.displayMetrics
            ),
            explicitMultiplier = explicitMultiplier
        )
        val metrics = textView.paint.fontMetricsInt
        val fontHeight = (metrics.descent - metrics.ascent).coerceAtLeast(1)
        val targetExtra = (targetLineHeight - fontHeight).toFloat()
        if (
            abs(textView.lineSpacingExtra - targetExtra) < 0.5f &&
            abs(textView.lineSpacingMultiplier - 1f) < 0.001f
        ) {
            return
        }
        textView.setLineSpacing(targetExtra, 1f)
    }

    private fun applyTree(view: View) {
        if (view is TextView && view.getTag(R.id.overlay_font_excluded) != true) {
            val requestedBold = view.getTag(R.id.overlay_font_requested_bold) as? Boolean
                ?: (view.typeface?.isBold == true)
            apply(view, requestedBold)
        }
        if (view is ViewGroup) {
            view.setOnHierarchyChangeListener(hierarchyListener)
            for (index in 0 until view.childCount) {
                applyTree(view.getChildAt(index))
            }
        }
    }
}

private class OverlayTypefaceSpan(
    private val typeface: Typeface
) : MetricAffectingSpan() {
    override fun updateMeasureState(textPaint: TextPaint) = applyTypeface(textPaint)

    override fun updateDrawState(textPaint: TextPaint) = applyTypeface(textPaint)

    private fun applyTypeface(textPaint: TextPaint) {
        val oldStyle = textPaint.typeface?.style ?: Typeface.NORMAL
        val missingStyle = oldStyle and typeface.style.inv()
        textPaint.isFakeBoldText = missingStyle and Typeface.BOLD != 0
        textPaint.textSkewX = if (missingStyle and Typeface.ITALIC != 0) -0.25f else 0f
        textPaint.typeface = typeface
    }
}
