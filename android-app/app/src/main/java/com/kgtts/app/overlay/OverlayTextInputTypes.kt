package com.lhtstudio.kigtts.app.overlay

import android.content.Context
import android.graphics.Typeface
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import java.text.BreakIterator
import java.util.Locale

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

internal fun OverlaySubtitlePreviewCard.resizeWithin(maxWidth: Int, maxHeight: Int) {
    val params = cardView.layoutParams ?: return
    val width = minOf(preferredWidth, maxWidth)
    val height = minOf(preferredHeight, maxHeight)
    if (params.width == width && params.height == height) return
    params.width = width
    params.height = height
    cardView.layoutParams = params
    root.requestLayout()
}

internal class OverlaySelectionEditText(
    context: Context,
    cursorColor: Int
) : EditText(context) {
    var onSelectionChanged: ((start: Int, end: Int) -> Unit)? = null
    var onContextMenuRequested: (() -> Unit)? = null
    var onContextMenuDismissRequested: (() -> Unit)? = null
    private var contextMenuTouchX = 0f
    private var contextMenuTouchY = 0f

    private val interceptedActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            post { onContextMenuRequested?.invoke() }
            return false
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false

        override fun onDestroyActionMode(mode: ActionMode?) = Unit
    }

    init {
        applyOverlayTextInteractionColor(cursorColor)
        customSelectionActionModeCallback = interceptedActionModeCallback
        customInsertionActionModeCallback = interceptedActionModeCallback
        setOnLongClickListener {
            selectTouchedWord()
            onContextMenuRequested?.invoke()
            true
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            contextMenuTouchX = event.x
            contextMenuTouchY = event.y
            onContextMenuDismissRequested?.invoke()
        }
        return super.onTouchEvent(event)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChanged?.invoke(selStart, selEnd)
    }

    private fun selectTouchedWord() {
        val value = text?.toString().orEmpty()
        if (value.isEmpty()) {
            setSelection(0)
            return
        }
        val offset = getOffsetForPosition(contextMenuTouchX, contextMenuTouchY)
            .coerceIn(0, value.length)
        val selectedStart = selectionStart.coerceAtLeast(0)
        val selectedEnd = selectionEnd.coerceAtLeast(0)
        if (selectedStart != selectedEnd && offset in minOf(selectedStart, selectedEnd)..maxOf(selectedStart, selectedEnd)) {
            return
        }
        val probe = offset.coerceAtMost(value.length - 1)
        if (value[probe].isWhitespace()) {
            setSelection(offset)
            return
        }
        val iterator = BreakIterator.getWordInstance(Locale.getDefault()).apply { setText(value) }
        val start = iterator.preceding((probe + 1).coerceAtMost(value.length))
            .takeIf { it != BreakIterator.DONE }
            ?: probe
        val end = iterator.following(probe)
            .takeIf { it != BreakIterator.DONE }
            ?: (probe + 1)
        if (start < end && value.substring(start, end).any { !it.isWhitespace() }) {
            setSelection(start, end)
        } else {
            val codePointStart = value.offsetByCodePoints(probe, 0)
            val codePointEnd = value.offsetByCodePoints(codePointStart, 1).coerceAtMost(value.length)
            setSelection(codePointStart, codePointEnd)
        }
    }
}
