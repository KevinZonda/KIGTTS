package com.lhtstudio.kigtts.app.overlay

import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat

internal fun EditText.applyOverlayTextInteractionColor(color: Int) {
    isCursorVisible = true
    highlightColor = ColorUtils.setAlphaComponent(color, 92)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching { applyPublicTextInteractionColor(color) }
        post { runCatching { applyPublicTextInteractionColor(color) } }
    } else {
        applyLegacyTextInteractionColor(color)
        post { applyLegacyTextInteractionColor(color) }
    }
}

internal fun EditText.applyOverlayTextFieldBackground(
    surfaceColor: Int,
    outlineColor: Int,
    accentColor: Int,
    radiusDp: Float = 4f
) {
    val density = resources.displayMetrics.density
    fun fieldDrawable(strokeColor: Int, strokeWidthDp: Float) = GradientDrawable().apply {
        setColor(surfaceColor)
        cornerRadius = radiusDp * density
        setStroke((strokeWidthDp * density + 0.5f).toInt().coerceAtLeast(1), strokeColor)
    }
    background = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), fieldDrawable(accentColor, 2f))
        addState(intArrayOf(), fieldDrawable(outlineColor, 1f))
    }
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
private fun EditText.applyPublicTextInteractionColor(color: Int) {
    textCursorDrawable = textCursorDrawable?.tinted(color) ?: createCursorDrawable(color)
    textSelectHandle?.tinted(color)?.let(::setTextSelectHandle)
    textSelectHandleLeft?.tinted(color)?.let(::setTextSelectHandleLeft)
    textSelectHandleRight?.tinted(color)?.let(::setTextSelectHandleRight)
}

private fun EditText.applyLegacyTextInteractionColor(color: Int) {
    runCatching {
        val editorField = TextView::class.java.getDeclaredField("mEditor").apply {
            isAccessible = true
        }
        val editor = editorField.get(this) ?: return@runCatching
        tintLegacyEditorField(
            editor = editor,
            fieldNames = listOf("mCursorDrawable", "mDrawableForCursor"),
            fallback = loadLegacyDrawable("mCursorDrawableRes", color),
            color = color
        )
        tintLegacyEditorField(
            editor = editor,
            fieldNames = listOf("mSelectHandleCenter", "mSelectHandle"),
            fallback = loadLegacyDrawable("mTextSelectHandleRes", color),
            color = color
        )
        tintLegacyEditorField(
            editor = editor,
            fieldNames = listOf("mSelectHandleLeft"),
            fallback = loadLegacyDrawable("mTextSelectHandleLeftRes", color),
            color = color
        )
        tintLegacyEditorField(
            editor = editor,
            fieldNames = listOf("mSelectHandleRight"),
            fallback = loadLegacyDrawable("mTextSelectHandleRightRes", color),
            color = color
        )
    }
}

private fun EditText.tintLegacyEditorField(
    editor: Any,
    fieldNames: List<String>,
    fallback: Drawable?,
    color: Int
) {
    val field = fieldNames.firstNotNullOfOrNull { name ->
        runCatching { editor.javaClass.getDeclaredField(name) }.getOrNull()
    } ?: return
    field.isAccessible = true
    when (val current = field.get(editor)) {
        is Drawable -> field.set(editor, current.tinted(color))
        is Array<*> -> {
            val source = current.filterIsInstance<Drawable>()
            val drawables = if (source.isNotEmpty()) source else listOfNotNull(fallback, fallback)
            field.set(editor, drawables.map { it.constantState?.newDrawable()?.tinted(color) ?: it.tinted(color) }.toTypedArray())
        }
        else -> fallback?.let {
            if (field.type.isArray) {
                val first = it.constantState?.newDrawable()?.tinted(color) ?: it.tinted(color)
                val second = it.constantState?.newDrawable()?.tinted(color) ?: it.tinted(color)
                field.set(editor, arrayOf(first, second))
            } else {
                field.set(editor, it)
            }
        }
    }
}

private fun EditText.loadLegacyDrawable(fieldName: String, color: Int): Drawable? {
    val resourceId = runCatching {
        TextView::class.java.getDeclaredField(fieldName).apply { isAccessible = true }.getInt(this)
    }.getOrNull()?.takeIf { it != 0 } ?: return null
    return ContextCompat.getDrawable(context, resourceId)?.tinted(color)
}

private fun EditText.createCursorDrawable(color: Int): Drawable = GradientDrawable().apply {
    setColor(color)
    setSize(
        (resources.displayMetrics.density * 2.5f).toInt().coerceAtLeast(1),
        (resources.displayMetrics.density * 24f).toInt().coerceAtLeast(1)
    )
}

private fun Drawable.tinted(color: Int): Drawable = DrawableCompat.wrap(mutate()).apply {
    DrawableCompat.setTint(this, color)
    DrawableCompat.setTintMode(this, PorterDuff.Mode.SRC_IN)
}
