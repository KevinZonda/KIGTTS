@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.lhtstudio.kigtts.app.ui

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog as MaterialAlertDialog
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.contentColorFor
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val LocalKigttsTextToolbarState =
    staticCompositionLocalOf<KigttsTextToolbarState?> { null }
internal val LocalUseSystemTextToolbar = staticCompositionLocalOf { false }

internal class KigttsTextToolbarState(
    private val uptimeMillis: () -> Long = SystemClock::uptimeMillis,
    private val transientHideGraceMillis: Long = 250L
) {
    var rect by mutableStateOf(Rect.Zero)
    var visible by mutableStateOf(false)
    var onCopyRequested by mutableStateOf<(() -> Unit)?>(null)
    var onPasteRequested by mutableStateOf<(() -> Unit)?>(null)
    var onCutRequested by mutableStateOf<(() -> Unit)?>(null)
    var onSelectAllRequested by mutableStateOf<(() -> Unit)?>(null)
    var anchorOriginAtShow by mutableStateOf(IntOffset.Zero)
        private set
    var focusedFieldBounds by mutableStateOf<Rect?>(null)
        private set
    var focusedFieldBoundsAtShow by mutableStateOf<Rect?>(null)
        private set

    private var shownAtUptimeMillis = Long.MIN_VALUE
    private var transientHideAvailable = false
    private var currentHostOrigin = IntOffset.Zero
    private var focusedFieldToken: Any? = null

    fun updateHostOrigin(origin: IntOffset) {
        currentHostOrigin = origin
    }

    fun focusField(token: Any, bounds: Rect?) {
        focusedFieldToken = token
        focusedFieldBounds = bounds
    }

    fun updateFocusedFieldBounds(token: Any, bounds: Rect) {
        if (focusedFieldToken === token) {
            focusedFieldBounds = bounds
        }
    }

    fun clearFocusedField(token: Any) {
        if (focusedFieldToken === token) {
            focusedFieldToken = null
            focusedFieldBounds = null
        }
    }

    fun show(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        this.rect = rect
        this.onCopyRequested = onCopyRequested
        this.onPasteRequested = onPasteRequested
        this.onCutRequested = onCutRequested
        this.onSelectAllRequested = onSelectAllRequested
        anchorOriginAtShow = currentHostOrigin
        focusedFieldBoundsAtShow = focusedFieldBounds
        shownAtUptimeMillis = uptimeMillis()
        transientHideAvailable = true
        visible = true
    }

    fun hide() {
        val elapsed = uptimeMillis() - shownAtUptimeMillis
        if (visible && transientHideAvailable && elapsed in 0..transientHideGraceMillis) {
            transientHideAvailable = false
            return
        }
        dismissImmediately()
    }

    fun dismissImmediately() {
        transientHideAvailable = false
        visible = false
    }
}

internal class KigttsTextToolbar(
    private val state: KigttsTextToolbarState
) : TextToolbar {
    override val status: TextToolbarStatus
        get() = if (state.visible) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        state.show(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
    }

    override fun hide() = state.hide()
}

internal class KigttsTextToolbarPositionProvider(
    private val anchorRect: IntRect,
    private val anchorOriginAtShow: IntOffset,
    private val focusedFieldBoundsAtShow: Rect?,
    private val focusedFieldBounds: Rect?,
    private val marginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val hostDelta = IntOffset(
            x = anchorBounds.left - anchorOriginAtShow.x,
            y = anchorBounds.top - anchorOriginAtShow.y
        )
        val fieldDelta = if (focusedFieldBoundsAtShow != null && focusedFieldBounds != null) {
            IntOffset(
                x = (focusedFieldBounds.left - focusedFieldBoundsAtShow.left).roundToInt(),
                y = (focusedFieldBounds.top - focusedFieldBoundsAtShow.top).roundToInt()
            )
        } else {
            null
        }
        val resolvedDelta = fieldDelta ?: hostDelta
        val resolvedAnchorRect = IntRect(
            left = anchorRect.left + resolvedDelta.x,
            top = anchorRect.top + resolvedDelta.y,
            right = anchorRect.right + resolvedDelta.x,
            bottom = anchorRect.bottom + resolvedDelta.y
        )
        val centeredX = resolvedAnchorRect.left +
            ((resolvedAnchorRect.width - popupContentSize.width) / 2)
        val clampedX = centeredX.coerceIn(
            marginPx,
            (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        )
        val maximumY =
            (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx)
        val aboveY = resolvedAnchorRect.top - popupContentSize.height - marginPx
        val belowY = resolvedAnchorRect.bottom + marginPx
        val targetY = when {
            aboveY >= marginPx -> aboveY
            belowY <= maximumY -> belowY
            resolvedAnchorRect.top >= windowSize.height - resolvedAnchorRect.bottom -> marginPx
            else -> maximumY
        }
        return IntOffset(clampedX, targetY)
    }
}

@Composable
internal fun KigttsTextToolbarHost(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    if (LocalUseSystemTextToolbar.current) {
        content()
        return
    }
    val state = remember { KigttsTextToolbarState() }
    val toolbar = remember(state) { KigttsTextToolbar(state) }
    DisposableEffect(state) {
        onDispose(state::dismissImmediately)
    }
    CompositionLocalProvider(
        LocalTextToolbar provides toolbar,
        LocalKigttsTextToolbarState provides state
    ) {
        Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val origin = coordinates.positionInWindow()
                state.updateHostOrigin(
                    IntOffset(origin.x.roundToInt(), origin.y.roundToInt())
                )
            }
        ) {
            content()
            KigttsTextToolbarPopup(state = state, darkTheme = darkTheme)
        }
    }
}

internal fun Modifier.kigttsTextToolbarAnchor(): Modifier = composed {
    val toolbarState = LocalKigttsTextToolbarState.current
    if (toolbarState == null) {
        this
    } else {
        val token = remember { Any() }
        var focused by remember { mutableStateOf(false) }
        var bounds by remember { mutableStateOf<Rect?>(null) }
        DisposableEffect(toolbarState, token) {
            onDispose { toolbarState.clearFocusedField(token) }
        }
        this
            .onFocusChanged { focusState ->
                focused = focusState.isFocused
                if (focused) {
                    toolbarState.focusField(token, bounds)
                } else {
                    toolbarState.clearFocusedField(token)
                }
            }
            .onGloballyPositioned { coordinates ->
                val fieldBounds = coordinates.boundsInWindow()
                bounds = fieldBounds
                if (focused) {
                    toolbarState.updateFocusedFieldBounds(token, fieldBounds)
                }
            }
    }
}

@Composable
internal fun KigttsDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        content()
    }
}

@Composable
internal fun KigttsAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    backgroundColor: Color = MaterialTheme.colors.surface,
    contentColor: Color = contentColorFor(backgroundColor),
    properties: DialogProperties = DialogProperties()
) {
    MaterialAlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = shape,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        properties = properties
    )
}

private data class KigttsTextToolbarAction(
    val icon: String,
    val contentDescription: String,
    val onClick: (() -> Unit)?
)

@Composable
internal fun KigttsTextToolbarPopup(
    state: KigttsTextToolbarState,
    darkTheme: Boolean
) {
    var rendered by remember { mutableStateOf(state.visible) }
    val menuAlpha by animateFloatAsState(
        targetValue = if (state.visible) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "kigtts_text_toolbar_alpha"
    )
    val menuScale by animateFloatAsState(
        targetValue = if (state.visible) 1f else 0.94f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "kigtts_text_toolbar_scale"
    )
    LaunchedEffect(state.visible) {
        if (state.visible) {
            rendered = true
        } else if (rendered) {
            delay(180L)
            rendered = false
        }
    }
    if (!rendered) return
    val marginPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val anchorRect = remember(state.rect) {
        IntRect(
            left = state.rect.left.toInt(),
            top = state.rect.top.toInt(),
            right = state.rect.right.toInt(),
            bottom = state.rect.bottom.toInt()
        )
    }
    val actions = remember(
        state.onCopyRequested,
        state.onPasteRequested,
        state.onCutRequested,
        state.onSelectAllRequested
    ) {
        listOf(
            KigttsTextToolbarAction("select_all", "全选", state.onSelectAllRequested),
            KigttsTextToolbarAction("content_cut", "剪切", state.onCutRequested),
            KigttsTextToolbarAction("content_copy", "复制", state.onCopyRequested),
            KigttsTextToolbarAction("content_paste", "粘贴", state.onPasteRequested)
        ).filter { it.onClick != null }
    }
    if (actions.isEmpty()) return

    val backgroundColor = if (darkTheme) Color(0xFF2C2F33) else Color.White
    val contentColor = if (darkTheme) Color(0xFFE9EDF1) else Color(0xFF202428)
    val positionProvider = remember(
        anchorRect,
        state.anchorOriginAtShow,
        state.focusedFieldBoundsAtShow,
        state.focusedFieldBounds,
        marginPx
    ) {
        KigttsTextToolbarPositionProvider(
            anchorRect = anchorRect,
            anchorOriginAtShow = state.anchorOriginAtShow,
            focusedFieldBoundsAtShow = state.focusedFieldBoundsAtShow,
            focusedFieldBounds = state.focusedFieldBounds,
            marginPx = marginPx
        )
    }

    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = false)
    ) {
        KigttsFontScaleProvider {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .graphicsLayer {
                        alpha = menuAlpha
                        scaleX = menuScale
                        scaleY = menuScale
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        clip = false
                    }
            ) {
                Card(
                    modifier = Modifier.wrapContentSize(),
                    shape = RoundedCornerShape(UiTokens.Radius),
                    backgroundColor = backgroundColor,
                    elevation = UiTokens.MenuElevation
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        actions.forEach { action ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = rememberRipple(bounded = true, radius = 20.dp)
                                    ) {
                                        action.onClick?.invoke()
                                        state.dismissImmediately()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                MsIcon(
                                    name = action.icon,
                                    contentDescription = action.contentDescription,
                                    tint = contentColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
