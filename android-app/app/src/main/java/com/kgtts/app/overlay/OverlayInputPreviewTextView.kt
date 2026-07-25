package com.lhtstudio.kigtts.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.TextView

internal class OverlayInputPreviewTextView(
    context: Context,
    cursorColor: Int
) : TextView(context) {
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cursorColor
        strokeWidth = context.resources.displayMetrics.density * 2.5f
    }

    var cursorIndex: Int = 0
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val textLayout = layout ?: return
        val offset = cursorIndex.coerceIn(0, text?.length ?: 0)
        val line = textLayout.getLineForOffset(offset)
        val x = totalPaddingLeft + textLayout.getPrimaryHorizontal(offset) - scrollX
        val top = extendedPaddingTop + textLayout.getLineTop(line) - scrollY
        val bottom = extendedPaddingTop + textLayout.getLineBottom(line) - scrollY
        canvas.drawLine(x, top.toFloat(), x, bottom.toFloat(), cursorPaint)
    }
}
