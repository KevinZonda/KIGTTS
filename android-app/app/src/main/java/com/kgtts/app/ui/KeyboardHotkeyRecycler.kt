package com.lhtstudio.kigtts.app.ui

import android.view.MotionEvent
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lhtstudio.kigtts.app.util.KeyboardHotkeyEntry
import com.lhtstudio.kigtts.app.util.KeyboardHotkeys

@Composable
internal fun KeyboardHotkeyRecyclerList(
    modifier: Modifier,
    entries: List<KeyboardHotkeyEntry>,
    bottomBlankHeight: Dp,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onEdit: (KeyboardHotkeyEntry) -> Unit,
    onEnterSelection: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onToggleEnabled: (KeyboardHotkeyEntry) -> Unit,
    onReorder: (List<Long>) -> Unit
) {
    val parentComposition = rememberCompositionContext()
    val bottomPx = with(LocalDensity.current) { bottomBlankHeight.roundToPx() }
    val callbacks = KeyboardHotkeyCallbacks(
        onEdit = rememberUpdatedState(onEdit),
        onEnterSelection = rememberUpdatedState(onEnterSelection),
        onToggleSelection = rememberUpdatedState(onToggleSelection),
        onToggleEnabled = rememberUpdatedState(onToggleEnabled),
        onReorder = rememberUpdatedState(onReorder)
    )
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { context ->
            val adapter = KeyboardHotkeyAdapter(parentComposition, callbacks)
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                clipToPadding = false
                clipChildren = false
                itemAnimator = DefaultItemAnimator().apply {
                    supportsChangeAnimations = false
                    moveDuration = 160L
                    changeDuration = 0L
                }
                setPadding(0, 0, 0, bottomPx)
                this.adapter = adapter
                val helper = ItemTouchHelper(KeyboardHotkeyTouchCallback(adapter, callbacks))
                helper.attachToRecyclerView(this)
                adapter.onStartDrag = helper::startDrag
            }
        },
        update = { recycler ->
            val adapter = recycler.adapter as KeyboardHotkeyAdapter
            adapter.selectionMode = selectionMode
            adapter.selectedIds = selectedIds
            adapter.submit(entries)
            recycler.setPadding(0, 0, 0, bottomPx)
        }
    )
}

private class KeyboardHotkeyCallbacks(
    val onEdit: androidx.compose.runtime.State<(KeyboardHotkeyEntry) -> Unit>,
    val onEnterSelection: androidx.compose.runtime.State<(Long) -> Unit>,
    val onToggleSelection: androidx.compose.runtime.State<(Long) -> Unit>,
    val onToggleEnabled: androidx.compose.runtime.State<(KeyboardHotkeyEntry) -> Unit>,
    val onReorder: androidx.compose.runtime.State<(List<Long>) -> Unit>
)

private class KeyboardHotkeyTouchCallback(
    private val adapter: KeyboardHotkeyAdapter,
    private val callbacks: KeyboardHotkeyCallbacks
) : ItemTouchHelper.Callback() {
    private var moved = false
    override fun isLongPressDragEnabled() = false
    override fun isItemViewSwipeEnabled() = false
    override fun getMovementFlags(rv: RecyclerView, holder: RecyclerView.ViewHolder): Int =
        makeMovementFlags(if (adapter.selectionMode) 0 else ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
    override fun onMove(rv: RecyclerView, holder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        val ok = adapter.move(holder.bindingAdapterPosition, target.bindingAdapterPosition)
        moved = moved || ok
        return ok
    }
    override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit
    override fun clearView(rv: RecyclerView, holder: RecyclerView.ViewHolder) {
        super.clearView(rv, holder)
        adapter.draggingId = null
        if (moved) callbacks.onReorder.value(adapter.ids())
        moved = false
    }
    override fun onSelectedChanged(holder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(holder, actionState)
        adapter.draggingId = if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) holder?.itemId else null
    }
}

private class KeyboardHotkeyAdapter(
    private val parentComposition: CompositionContext,
    private val callbacks: KeyboardHotkeyCallbacks
) : RecyclerView.Adapter<KeyboardHotkeyAdapter.Holder>() {
    private val items = mutableListOf<KeyboardHotkeyEntry>()
    var selectionMode = false
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }
    var selectedIds: Set<Long> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }
    var draggingId: Long? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var onStartDrag: ((RecyclerView.ViewHolder) -> Unit)? = null
    init { setHasStableIds(true) }
    override fun getItemId(position: Int) = items[position].id
    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        ComposeView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setParentCompositionContext(parentComposition)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        }
    )
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = items[position]
        holder.bind(
            entry,
            draggingId == entry.id,
            selectionMode,
            entry.id in selectedIds,
            callbacks,
            { onStartDrag?.invoke(holder) }
        )
    }
    fun submit(entries: List<KeyboardHotkeyEntry>) {
        if (items == entries) return
        items.clear()
        items.addAll(entries)
        notifyDataSetChanged()
    }
    fun move(from: Int, to: Int): Boolean {
        if (from == to || from !in items.indices || to !in items.indices) return false
        items.add(to, items.removeAt(from))
        notifyItemMoved(from, to)
        return true
    }
    fun ids() = items.map { it.id }
    class Holder(private val view: ComposeView) : RecyclerView.ViewHolder(view) {
        private val entryState = mutableStateOf<KeyboardHotkeyEntry?>(null)
        private val draggedState = mutableStateOf(false)
        private val selectionModeState = mutableStateOf(false)
        private val selectedState = mutableStateOf(false)
        private val callbacksState = mutableStateOf<KeyboardHotkeyCallbacks?>(null)
        private val onStartDragState = mutableStateOf<() -> Unit>({})

        init {
            view.setContent {
                KigttsFontScaleProvider {
                    val entry = entryState.value
                    val callbacks = callbacksState.value
                    if (entry != null && callbacks != null) {
                        KeyboardHotkeyRow(
                            entry = entry,
                            dragged = draggedState.value,
                            selectionMode = selectionModeState.value,
                            selected = selectedState.value,
                            callbacks = callbacks,
                            onStartDrag = onStartDragState.value
                        )
                    }
                }
            }
        }

        fun bind(
            entry: KeyboardHotkeyEntry,
            dragged: Boolean,
            selectionMode: Boolean,
            selected: Boolean,
            callbacks: KeyboardHotkeyCallbacks,
            onStartDrag: () -> Unit
        ) {
            entryState.value = entry
            draggedState.value = dragged
            selectionModeState.value = selectionMode
            selectedState.value = selected
            callbacksState.value = callbacks
            onStartDragState.value = onStartDrag
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
private fun KeyboardHotkeyRow(
    entry: KeyboardHotkeyEntry,
    dragged: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    callbacks: KeyboardHotkeyCallbacks,
    onStartDrag: () -> Unit
) {
    val localView = LocalView.current
    val elevation by animateDpAsState(if (dragged) 10.dp else UiTokens.CardElevation, tween(140), label = "keyboard_hotkey_elevation")
    val overlay by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
        tween(140, easing = FastOutSlowInEasing),
        label = "keyboard_hotkey_selection"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .heightIn(min = 72.dp)
            .combinedClickable(
                onClick = {
                    if (selectionMode) callbacks.onToggleSelection.value(entry.id)
                    else callbacks.onEdit.value(entry)
                },
                onLongClick = {
                    localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    callbacks.onEnterSelection.value(entry.id)
                }
            ),
        shape = RoundedCornerShape(UiTokens.Radius),
        backgroundColor = md2CardContainerColor(),
        elevation = elevation
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(overlay).padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = selectionMode,
                enter = fadeIn(tween(140)) + expandHorizontally(tween(160)),
                exit = fadeOut(tween(100)) + shrinkHorizontally(tween(140))
            ) {
                Checkbox(selected, onCheckedChange = { callbacks.onToggleSelection.value(entry.id) })
            }
            Column(Modifier.weight(1f)) {
                Text(KeyboardHotkeys.labelOf(entry.keyCode, entry.modifiers), fontWeight = FontWeight.SemiBold)
                Text(entry.text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Md2Switch(checked = entry.enabled, onCheckedChange = { callbacks.onToggleEnabled.value(entry) })
            AnimatedVisibility(
                visible = !selectionMode,
                enter = fadeIn(tween(140)) + expandHorizontally(tween(160)),
                exit = fadeOut(tween(100)) + shrinkHorizontally(tween(140))
            ) {
                Md2IconButton(
                    "drag_indicator",
                    "拖动排序",
                    onClick = {},
                    modifier = Modifier.pointerInteropFilter { event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onStartDrag()
                                true
                            }
                            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                            else -> false
                        }
                    }
                )
            }
        }
    }
}
