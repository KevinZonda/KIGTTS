package com.lhtstudio.kigtts.app.ui

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lhtstudio.kigtts.app.data.DrawingPalette
import com.lhtstudio.kigtts.app.data.DrawingPaletteEntry
import com.lhtstudio.kigtts.app.data.decodeDrawingPalette
import com.lhtstudio.kigtts.app.data.encodeDrawingPalette
import com.lhtstudio.kigtts.app.data.nextDrawingPaletteEntryId

internal data class DrawingPaletteTopBarActions(
    val canConfirm: Boolean,
    val canAdd: Boolean,
    val onAdd: () -> Unit,
    val onConfirm: () -> Unit,
    val onBackRequest: () -> Unit
)

@Composable
internal fun DrawingPaletteEditorScreen(
    savedPalette: DrawingPalette,
    onSave: (DrawingPalette) -> Unit,
    onClose: () -> Unit,
    onTopBarActionsChange: (DrawingPaletteTopBarActions?) -> Unit
) {
    val savedNormalized = remember(savedPalette) { savedPalette.normalized() }
    var draftEncoded by rememberSaveable(savedNormalized) {
        mutableStateOf(encodeDrawingPalette(savedNormalized))
    }
    val draft = remember(draftEncoded) { decodeDrawingPalette(draftEncoded) }
    val hasChanges = draft.entries != savedNormalized.entries
    var pendingColorEntryId by rememberSaveable { mutableLongStateOf(0L) }
    var pendingDarkColor by rememberSaveable { mutableStateOf(false) }
    var showSavePrompt by rememberSaveable { mutableStateOf(false) }

    fun updateEntries(entries: List<DrawingPaletteEntry>) {
        draftEncoded = encodeDrawingPalette(DrawingPalette(entries))
    }

    fun saveAndClose() {
        onSave(draft)
        onClose()
    }

    fun requestClose() {
        if (hasChanges) showSavePrompt = true else onClose()
    }

    fun addEntry() {
        if (draft.entries.size >= DrawingPalette.MAX_ENTRIES) return
        updateEntries(
            draft.entries + DrawingPaletteEntry(
                id = nextDrawingPaletteEntryId(draft.entries),
                lightColorArgb = 0xFF038387.toInt(),
                darkColorArgb = 0xFF7DE8EA.toInt()
            )
        )
    }

    SideEffect {
        onTopBarActionsChange(
            DrawingPaletteTopBarActions(
                canConfirm = hasChanges,
                canAdd = draft.entries.size < DrawingPalette.MAX_ENTRIES,
                onAdd = { addEntry() },
                onConfirm = { saveAndClose() },
                onBackRequest = { requestClose() }
            )
        )
    }
    DisposableEffect(Unit) {
        onDispose { onTopBarActionsChange(null) }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        DrawingPaletteReorderList(
            modifier = Modifier
                .widthIn(max = UiTokens.WideListMaxWidth)
                .fillMaxSize(),
            entries = draft.entries,
            onEditLight = { id ->
                pendingColorEntryId = id
                pendingDarkColor = false
            },
            onEditDark = { id ->
                pendingColorEntryId = id
                pendingDarkColor = true
            },
            onDelete = { id -> updateEntries(draft.entries.filterNot { it.id == id }) },
            onReorder = { reorderedIds ->
                val byId = draft.entries.associateBy { it.id }
                updateEntries(reorderedIds.mapNotNull(byId::get))
            }
        )
        if (draft.entries.isEmpty()) {
            Text(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                text = "暂无调色板颜色",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    draft.entries.firstOrNull { it.id == pendingColorEntryId }?.let { entry ->
        ThemeColorPickerDialog(
            title = if (pendingDarkColor) "暗色主题颜色" else "亮色主题颜色",
            initialColor = Color(if (pendingDarkColor) entry.darkColorArgb else entry.lightColorArgb),
            colorLabel = "候选颜色",
            onDismissRequest = { pendingColorEntryId = 0L },
            onColorSelected = { color ->
                updateEntries(
                    draft.entries.map { current ->
                        if (current.id != entry.id) {
                            current
                        } else if (pendingDarkColor) {
                            current.copy(darkColorArgb = color.toArgb())
                        } else {
                            current.copy(lightColorArgb = color.toArgb())
                        }
                    }
                )
                pendingColorEntryId = 0L
            }
        )
    }

    if (showSavePrompt) {
        KigttsAlertDialog(
            onDismissRequest = { showSavePrompt = false },
            title = { Text("保存调色板") },
            text = { Text("调色板已修改，返回前是否保存？") },
            confirmButton = {
                Md2TextButton(onClick = { saveAndClose() }) { Text("保存") }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Md2TextButton(onClick = { showSavePrompt = false }) { Text("取消") }
                    Md2TextButton(
                        onClick = {
                            showSavePrompt = false
                            onClose()
                        }
                    ) { Text("不保存") }
                }
            }
        )
    }
}

@Composable
private fun DrawingPaletteReorderList(
    modifier: Modifier,
    entries: List<DrawingPaletteEntry>,
    onEditLight: (Long) -> Unit,
    onEditDark: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit
) {
    val parentComposition = rememberCompositionContext()
    val onEditLightState = androidx.compose.runtime.rememberUpdatedState(onEditLight)
    val onEditDarkState = androidx.compose.runtime.rememberUpdatedState(onEditDark)
    val onDeleteState = androidx.compose.runtime.rememberUpdatedState(onDelete)
    val onReorderState = androidx.compose.runtime.rememberUpdatedState(onReorder)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val recycler = RecyclerView(ctx).apply {
                layoutManager = LinearLayoutManager(ctx)
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                clipToPadding = false
                clipChildren = false
                setPadding(14.dpToPx(ctx), 8.dpToPx(ctx), 14.dpToPx(ctx), 24.dpToPx(ctx))
                itemAnimator = DefaultItemAnimator().apply {
                    supportsChangeAnimations = false
                    moveDuration = 160L
                }
            }
            val adapter = DrawingPaletteAdapter(parentComposition)
            recycler.adapter = adapter
            val callback = object : ItemTouchHelper.Callback() {
                private var moved = false
                private var activeViewHolder: RecyclerView.ViewHolder? = null
                private val edgeAutoScroller = DragEdgeAutoScroller()

                override fun isLongPressDragEnabled(): Boolean = false
                override fun isItemViewSwipeEnabled(): Boolean = false

                override fun getMovementFlags(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ): Int = makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val changed = adapter.move(
                        viewHolder.bindingAdapterPosition,
                        target.bindingAdapterPosition
                    )
                    moved = moved || changed
                    return changed
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

                override fun onChildDraw(
                    canvas: android.graphics.Canvas,
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    dX: Float,
                    dY: Float,
                    actionState: Int,
                    isCurrentlyActive: Boolean
                ) {
                    super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
                        edgeAutoScroller.update(recyclerView, viewHolder.itemView, dY)
                    } else {
                        edgeAutoScroller.stop()
                    }
                }

                override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                    super.onSelectedChanged(viewHolder, actionState)
                    adapter.isDragging = actionState == ItemTouchHelper.ACTION_STATE_DRAG
                    adapter.setDraggingPosition(viewHolder?.bindingAdapterPosition ?: RecyclerView.NO_POSITION)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                        if (activeViewHolder !== viewHolder) {
                            activeViewHolder?.let { animateDragElevation(it.itemView, elevated = false) }
                        }
                        activeViewHolder = viewHolder
                        animateDragElevation(viewHolder.itemView, elevated = true)
                    } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                        edgeAutoScroller.stop()
                        activeViewHolder?.let { animateDragElevation(it.itemView, elevated = false) }
                        activeViewHolder = null
                    }
                }

                override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                    edgeAutoScroller.stop()
                    super.clearView(recyclerView, viewHolder)
                    animateDragElevation(viewHolder.itemView, elevated = false)
                    if (activeViewHolder === viewHolder) activeViewHolder = null
                    adapter.isDragging = false
                    adapter.setDraggingPosition(RecyclerView.NO_POSITION)
                    if (moved) {
                        onReorderState.value(adapter.snapshotIds())
                        moved = false
                    }
                }
            }
            val touchHelper = ItemTouchHelper(callback)
            touchHelper.attachToRecyclerView(recycler)
            adapter.onStartDrag = touchHelper::startDrag
            adapter.onEditLight = { onEditLightState.value(it) }
            adapter.onEditDark = { onEditDarkState.value(it) }
            adapter.onDelete = { onDeleteState.value(it) }
            recycler
        },
        update = { recycler ->
            val adapter = recycler.adapter as? DrawingPaletteAdapter ?: return@AndroidView
            adapter.onEditLight = { onEditLightState.value(it) }
            adapter.onEditDark = { onEditDarkState.value(it) }
            adapter.onDelete = { onDeleteState.value(it) }
            adapter.submit(entries)
        }
    )
}

private class DrawingPaletteAdapter(
    private val parentComposition: androidx.compose.runtime.CompositionContext
) : RecyclerView.Adapter<DrawingPaletteAdapter.PaletteViewHolder>() {
    private val items = mutableListOf<DrawingPaletteEntry>()
    private var draggingItemId: Long? = null
    var isDragging: Boolean = false
    var onStartDrag: ((RecyclerView.ViewHolder) -> Unit)? = null
    var onEditLight: ((Long) -> Unit)? = null
    var onEditDark: ((Long) -> Unit)? = null
    var onDelete: ((Long) -> Unit)? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items[position].id
    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaletteViewHolder {
        val composeView = ComposeView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setParentCompositionContext(parentComposition)
        }
        return PaletteViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: PaletteViewHolder, position: Int) {
        if (!isDragging) holder.itemView.translationZ = 0f
        val entry = items[position]
        holder.bind(
            entry = entry,
            dragged = entry.id == draggingItemId,
            onEditLight = { onEditLight?.invoke(entry.id) },
            onEditDark = { onEditDark?.invoke(entry.id) },
            onDelete = { onDelete?.invoke(entry.id) },
            onStartDrag = { onStartDrag?.invoke(holder) }
        )
    }

    override fun onViewRecycled(holder: PaletteViewHolder) {
        holder.itemView.animate().cancel()
        holder.itemView.translationZ = 0f
        super.onViewRecycled(holder)
    }

    fun submit(entries: List<DrawingPaletteEntry>) {
        if (isDragging || items == entries) return
        items.clear()
        items.addAll(entries)
        notifyDataSetChanged()
    }

    fun move(from: Int, to: Int): Boolean {
        if (from == to || from !in items.indices || to !in items.indices) return false
        val moved = items.removeAt(from)
        items.add(to, moved)
        notifyItemMoved(from, to)
        return true
    }

    fun snapshotIds(): List<Long> = items.map { it.id }

    fun setDraggingPosition(position: Int) {
        val newId = items.getOrNull(position)?.id
        if (newId == draggingItemId) return
        val oldId = draggingItemId
        draggingItemId = newId
        oldId?.let { id -> items.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let(::notifyItemChanged) }
        newId?.let { id -> items.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let(::notifyItemChanged) }
    }

    class PaletteViewHolder(private val composeView: ComposeView) : RecyclerView.ViewHolder(composeView) {
        fun bind(
            entry: DrawingPaletteEntry,
            dragged: Boolean,
            onEditLight: () -> Unit,
            onEditDark: () -> Unit,
            onDelete: () -> Unit,
            onStartDrag: () -> Unit
        ) {
            composeView.setContent {
                KigttsFontScaleProvider {
                    DrawingPaletteRow(
                        entry = entry,
                        dragged = dragged,
                        onEditLight = onEditLight,
                        onEditDark = onEditDark,
                        onDelete = onDelete,
                        onStartDrag = onStartDrag
                    )
                }
            }
        }
    }
}

private fun Int.dpToPx(context: android.content.Context): Int =
    (this * context.resources.displayMetrics.density).toInt()
