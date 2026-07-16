package com.lhtstudio.kigtts.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal class OverlayTextInputWindow(
    private val context: Context,
    private val windowManager: WindowManager,
    private val styleProvider: () -> OverlayInteractionStyle,
    private val createPreviewCard: () -> OverlaySubtitlePreviewCard,
    private val updatePreviewCard: (OverlaySubtitlePreviewCard, String) -> Unit,
    private val onDraftChanged: (String) -> Unit,
    private val onPlayOnSendChanged: (Boolean) -> Unit,
    private val onSend: (String) -> Unit
) {
    private var root: FrameLayout? = null
    private var input: OverlaySelectionEditText? = null
    private var previewCard: OverlaySubtitlePreviewCard? = null
    private var scrimView: View? = null
    private var inputBarView: View? = null
    private var playButton: TextView? = null
    private var clearButton: TextView? = null
    private var playOnSend = true
    private var suppressTextCallback = false
    private var dismissing = false
    private var lastPreviewText = ""
    private var lastPreviewCursorIndex = 0

    val isShowing: Boolean
        get() = root?.isAttachedToWindow == true

    fun show(
        initialText: String,
        initialPlayOnSend: Boolean
    ) {
        dismiss(immediate = true)
        val style = styleProvider()
        playOnSend = initialPlayOnSend
        val nextRoot = buildRoot(style)
        root = nextRoot
        windowManager.addView(nextRoot, createLayoutParams())
        playEnterAnimation()
        suppressTextCallback = true
        input?.setText(initialText)
        input?.setSelection(initialText.length)
        suppressTextCallback = false
        updateClearButton(initialText)
        updatePreview(initialText, initialText.length)
        updatePlayButton()
        input?.post {
            input?.requestFocus()
            context.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun dismiss(immediate: Boolean = false) {
        val current = root ?: return
        if (immediate || !current.isAttachedToWindow) {
            removeCurrentWindow(current)
            return
        }
        if (dismissing) return
        dismissing = true
        scrimView?.animate()?.cancel()
        scrimView?.animate()
            ?.alpha(0f)
            ?.setDuration(EXIT_SCRIM_DURATION_MS)
            ?.start()
        previewCard?.root?.animate()?.cancel()
        previewCard?.root?.animate()
            ?.alpha(0f)
            ?.translationY(dp(8).toFloat())
            ?.scaleX(0.98f)
            ?.scaleY(0.98f)
            ?.setDuration(EXIT_CONTENT_DURATION_MS)
            ?.start()
        val inputBar = inputBarView
        inputBar?.animate()?.cancel()
        if (inputBar != null) {
            inputBar.animate()
                .alpha(0f)
                .translationY(dp(28).toFloat())
                .setDuration(EXIT_CONTENT_DURATION_MS)
                .withEndAction { removeCurrentWindow(current) }
                .start()
        } else {
            current.postDelayed(
                { removeCurrentWindow(current) },
                EXIT_CONTENT_DURATION_MS
            )
        }
    }

    private fun removeCurrentWindow(current: FrameLayout) {
        if (root !== current) {
            runCatching { windowManager.removeViewImmediate(current) }
            return
        }
        input?.let { field ->
            context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(
                field.windowToken,
                0
            )
        }
        runCatching { windowManager.removeViewImmediate(current) }
        root = null
        input = null
        previewCard = null
        scrimView = null
        inputBarView = null
        playButton = null
        clearButton = null
        dismissing = false
        lastPreviewText = ""
        lastPreviewCursorIndex = 0
    }

    fun updateDraft(text: String) {
        val field = input ?: return
        if (field.text?.toString() == text) return
        suppressTextCallback = true
        field.setText(text)
        field.setSelection(text.length)
        suppressTextCallback = false
        updateClearButton(text)
        updatePreview(text, text.length)
    }

    private fun buildRoot(style: OverlayInteractionStyle): FrameLayout {
        val rootView = FrameLayout(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val scrim = View(context).apply {
            setBackgroundColor(style.previewScrimColor)
            setOnClickListener { dismiss() }
        }
        scrimView = scrim
        rootView.addView(
            scrim,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val previewRegion = FrameLayout(context).apply {
            clipChildren = true
            clipToPadding = true
        }
        val nextPreviewCard = createPreviewCard()
        previewCard = nextPreviewCard
        previewRegion.addView(
            nextPreviewCard.root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        rootView.addView(
            previewRegion,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.TOP
            )
        )

        val inputBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundedDrawable(Color.rgb(26, 27, 30), 0f)
        }
        inputBarView = inputBar
        inputBar.addView(iconButton("arrow_back", "光标左移", style) { moveCursor(-1) })
        inputBar.addView(iconButton("arrow_forward", "光标右移", style) { moveCursor(1) })
        playButton = iconButton("volume_up", "发送时播放语音", style) {
            playOnSend = !playOnSend
            onPlayOnSendChanged(playOnSend)
            updatePlayButton()
        }
        inputBar.addView(playButton)

        val inputContainer = FrameLayout(context)
        input = OverlaySelectionEditText(context, style.accentColor).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 138))
            hint = "请输入文本"
            textSize = 16f
            typeface = style.regularTypeface
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(12), dp(8), dp(48), dp(8))
            background = roundedDrawable(Color.rgb(46, 47, 51), 4f)
            if (style.usesCustomFontMetrics) {
                includeFontPadding = false
            }
            onSelectionChanged = { start, _ ->
                updatePreview(text?.toString().orEmpty(), start)
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val value = s?.toString().orEmpty()
                    updateClearButton(value)
                    updatePreview(value, selectionStart.coerceAtLeast(0))
                    if (!suppressTextCallback) onDraftChanged(value)
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendCurrentText()
                    true
                } else {
                    false
                }
            }
        }
        inputContainer.addView(
            input,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
            )
        )
        clearButton = iconButton("close", "清空输入", style) { input?.setText("") }.apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END or Gravity.CENTER_VERTICAL)
        }
        inputContainer.addView(clearButton)
        inputBar.addView(
            inputContainer,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
            }
        )
        inputBar.addView(iconButton("send", "发送字幕", style, accent = true) { sendCurrentText() })

        rootView.addView(
            inputBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        var systemTop = 0
        var systemBottom = 0
        var imeBottom = 0
        var fallbackKeyboardBottom = 0

        fun updateObstructionLayout() {
            val keyboardBottom = maxOf(imeBottom, fallbackKeyboardBottom)
            val keyboardVisible = keyboardBottom > systemBottom + dp(48)
            val bottomObstruction = if (keyboardVisible) keyboardBottom else 0
            inputBar.setPadding(
                dp(8),
                dp(8),
                dp(8),
                dp(8) + if (keyboardVisible) 0 else systemBottom
            )
            (inputBar.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                if (params.bottomMargin != bottomObstruction) {
                    params.bottomMargin = bottomObstruction
                    inputBar.layoutParams = params
                }
            }
            val inputBarHeight = inputBar.height.coerceAtLeast(dp(60))
            (previewRegion.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                val previewBottomMargin = bottomObstruction + inputBarHeight
                if (params.topMargin != systemTop || params.bottomMargin != previewBottomMargin) {
                    params.topMargin = systemTop
                    params.bottomMargin = previewBottomMargin
                    previewRegion.layoutParams = params
                }
            }
            if (rootView.width <= 0 || rootView.height <= 0) return
            resizePreviewCard(
                card = nextPreviewCard,
                maxWidth = (rootView.width - dp(28)).coerceAtLeast(dp(160)),
                maxHeight = (
                    rootView.height - systemTop - bottomObstruction - inputBarHeight - dp(28)
                    ).coerceAtLeast(dp(120))
            )
        }

        inputBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateObstructionLayout() }
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemTop = systemInsets.top
            systemBottom = systemInsets.bottom
            imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            updateObstructionLayout()
            insets
        }
        val visibleFrame = Rect()
        val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            rootView.getWindowVisibleDisplayFrame(visibleFrame)
            val coveredBottom = (rootView.height - visibleFrame.bottom).coerceAtLeast(0)
            fallbackKeyboardBottom = if (coveredBottom > systemBottom + dp(48)) coveredBottom else 0
            updateObstructionLayout()
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        rootView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                ViewCompat.requestApplyInsets(view)
            }

            override fun onViewDetachedFromWindow(view: View) {
                if (view.viewTreeObserver.isAlive) {
                    view.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
                }
            }
        })
        return rootView
    }

    private fun playEnterAnimation() {
        val scrim = scrimView
        val preview = previewCard?.root
        val inputBar = inputBarView
        scrim?.alpha = 0f
        preview?.apply {
            alpha = 0f
            translationY = dp(12).toFloat()
            scaleX = 0.97f
            scaleY = 0.97f
        }
        inputBar?.apply {
            alpha = 0f
            translationY = dp(32).toFloat()
        }
        root?.post {
            scrim?.animate()?.cancel()
            scrim?.animate()
                ?.alpha(1f)
                ?.setDuration(ENTER_SCRIM_DURATION_MS)
                ?.start()
            preview?.animate()?.cancel()
            preview?.animate()
                ?.alpha(1f)
                ?.translationY(0f)
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.setDuration(ENTER_CONTENT_DURATION_MS)
                ?.start()
            inputBar?.animate()?.cancel()
            inputBar?.animate()
                ?.alpha(1f)
                ?.translationY(0f)
                ?.setStartDelay(ENTER_INPUT_DELAY_MS)
                ?.setDuration(ENTER_CONTENT_DURATION_MS)
                ?.start()
        }
    }

    private fun sendCurrentText() {
        val value = input?.text?.toString()?.trim().orEmpty()
        if (value.isEmpty()) return
        suppressTextCallback = true
        input?.setText("")
        suppressTextCallback = false
        onDraftChanged("")
        updateClearButton("")
        updatePreview("", 0)
        onSend(value)
        dismiss()
    }

    private fun moveCursor(delta: Int) {
        val field = input ?: return
        val current = field.selectionStart.coerceAtLeast(0)
        field.setSelection((current + delta).coerceIn(0, field.text.length))
    }

    private fun updatePreview(text: String, cursorIndex: Int) {
        val card = previewCard ?: return
        val resolvedCursorIndex = cursorIndex.coerceIn(0, text.length)
        val changed = text != lastPreviewText || resolvedCursorIndex != lastPreviewCursorIndex
        updatePreviewCard(card, text)
        (card.textView as? OverlayInputPreviewTextView)?.cursorIndex =
            resolvedCursorIndex
        lastPreviewText = text
        lastPreviewCursorIndex = resolvedCursorIndex
        if (changed && root?.isAttachedToWindow == true && !dismissing) {
            card.textView.animate().cancel()
            card.textView.alpha = 0.52f
            card.textView.animate()
                .alpha(1f)
                .setDuration(PREVIEW_TEXT_FADE_DURATION_MS)
                .start()
        }
    }

    private fun updateClearButton(text: String) {
        clearButton?.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun updatePlayButton() {
        playButton?.text = if (playOnSend) "volume_up" else "volume_off"
        playButton?.contentDescription = if (playOnSend) "发送时播放语音：开" else "发送时播放语音：关"
    }

    private fun iconButton(
        icon: String,
        description: String,
        style: OverlayInteractionStyle,
        accent: Boolean = false,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        text = icon
        contentDescription = description
        gravity = Gravity.CENTER
        textSize = 24f
        typeface = style.iconTypeface
        setTextColor(if (accent) style.onAccentColor else Color.WHITE)
        background = if (accent) roundedDrawable(style.accentColor, 24f) else null
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
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
        softInputMode =
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        title = "KIGTTS overlay text input"
    }

    private fun resizePreviewCard(card: OverlaySubtitlePreviewCard, maxWidth: Int, maxHeight: Int) {
        val params = card.cardView.layoutParams ?: return
        val width = minOf(card.preferredWidth, maxWidth)
        val height = minOf(card.preferredHeight, maxHeight)
        if (params.width == width && params.height == height) return
        params.width = width
        params.height = height
        card.cardView.layoutParams = params
        card.root.requestLayout()
    }

    private fun roundedDrawable(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}

private const val ENTER_SCRIM_DURATION_MS = 160L
private const val ENTER_CONTENT_DURATION_MS = 210L
private const val ENTER_INPUT_DELAY_MS = 24L
private const val EXIT_SCRIM_DURATION_MS = 130L
private const val EXIT_CONTENT_DURATION_MS = 160L
private const val PREVIEW_TEXT_FADE_DURATION_MS = 140L
