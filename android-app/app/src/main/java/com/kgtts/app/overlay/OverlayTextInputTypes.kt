package com.lhtstudio.kigtts.app.overlay

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.EditText
import android.widget.TextView

internal data class OverlayInteractionStyle(
    val accentColor: Int,
    val onAccentColor: Int,
    val surfaceColor: Int,
    val onSurfaceColor: Int,
    val onSurfaceVariantColor: Int,
    val previewScrimColor: Int,
    val regularTypeface: Typeface,
    val boldTypeface: Typeface,
    val iconTypeface: Typeface?,
    val usesCustomFontMetrics: Boolean
)

internal data class OverlaySubtitlePreviewCard(
    val root: View,
    val textView: TextView,
    val viewportView: View,
    val cardView: View,
    val preferredWidth: Int,
    val preferredHeight: Int
)

internal class OverlaySelectionEditText(
    context: Context,
    cursorColor: Int
) : EditText(context) {
    var onSelectionChanged: ((start: Int, end: Int) -> Unit)? = null

    init {
        applyOverlayTextInteractionColor(cursorColor)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChanged?.invoke(selStart, selEnd)
    }
}
