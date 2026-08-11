package com.lhtstudio.kigtts.app.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.max

@Composable
internal fun QuickSubtitleCandidateRecycler(
    items: List<String>,
    itemColors: List<Int?>,
    grid: Boolean,
    dragEnabled: Boolean,
    onItemClick: (Int, String) -> Unit,
    onItemsReordered: (List<String>, List<Int?>) -> Unit,
    onManualSortRequired: () -> Unit,
    onEditRequested: (Int, String, Int?) -> Unit,
    onDeleteRequested: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val parentComposition = rememberCompositionContext()
    val currentOnItemClick by rememberUpdatedState(onItemClick)
    val currentOnItemsReordered by rememberUpdatedState(onItemsReordered)
    val currentOnManualSortRequired by rememberUpdatedState(onManualSortRequired)
    val currentOnEditRequested by rememberUpdatedState(onEditRequested)
    val currentOnDeleteRequested by rememberUpdatedState(onDeleteRequested)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val recycler = RecyclerView(context).apply {
                clipToPadding = false
                clipChildren = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                val inset = (6f * resources.displayMetrics.density).toInt()
                setPadding(inset, inset, inset, inset)
            }
            lateinit var touchHelper: ItemTouchHelper
            val adapter = QuickSubtitleCandidateAdapter(
                parentComposition = parentComposition,
                onItemClick = { index, text -> currentOnItemClick(index, text) },
                onEditRequested = { index, text, color ->
                    currentOnEditRequested(index, text, color)
                },
                onDeleteRequested = { index, text -> currentOnDeleteRequested(index, text) },
                onStartDrag = { holder -> touchHelper.startDrag(holder) },
                onManualSortRequired = { currentOnManualSortRequired() }
            )
            recycler.adapter = adapter

            var moved = false
            val callback = object : ItemTouchHelper.Callback() {
                override fun isLongPressDragEnabled(): Boolean = false

                override fun isItemViewSwipeEnabled(): Boolean = false

                override fun getMovementFlags(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ): Int {
                    if (!adapter.dragEnabled) return makeMovementFlags(0, 0)
                    val dragFlags = if (adapter.grid) {
                        ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                    } else {
                        ItemTouchHelper.UP or ItemTouchHelper.DOWN
                    }
                    return makeMovementFlags(dragFlags, 0)
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val changed = adapter.move(
                        viewHolder.bindingAdapterPosition,
                        target.bindingAdapterPosition
                    )
                    if (changed) {
                        moved = true
                        viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    return changed
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

                override fun onSelectedChanged(
                    viewHolder: RecyclerView.ViewHolder?,
                    actionState: Int
                ) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                        adapter.setDraggingItem(viewHolder.itemId)
                        viewHolder.itemView.translationZ = 20f * recycler.resources.displayMetrics.density
                    } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                        adapter.clearDraggingItem()
                    }
                }

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.translationZ = 0f
                    adapter.clearDraggingItem()
                    if (moved) {
                        currentOnItemsReordered(adapter.snapshotTexts(), adapter.snapshotColors())
                        moved = false
                    }
                }
            }
            touchHelper = ItemTouchHelper(callback).also { it.attachToRecyclerView(recycler) }
            recycler.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                applyQuickSubtitleCandidateLayout(view as RecyclerView, adapter.grid)
            }
            recycler
        },
        update = { recycler ->
            val adapter = recycler.adapter as QuickSubtitleCandidateAdapter
            adapter.updateInteractionMode(grid = grid, dragEnabled = dragEnabled)
            adapter.submitFromState(items, itemColors)
            applyQuickSubtitleCandidateLayout(recycler, grid)
        }
    )
}

private fun applyQuickSubtitleCandidateLayout(recycler: RecyclerView, grid: Boolean) {
    if (!grid) {
        if (recycler.layoutManager !is LinearLayoutManager ||
            recycler.layoutManager is GridLayoutManager
        ) {
            recycler.layoutManager = LinearLayoutManager(recycler.context)
        }
        return
    }
    val availableWidth = (recycler.width - recycler.paddingLeft - recycler.paddingRight).coerceAtLeast(1)
    val minimumCellWidth = (146f * recycler.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val spanCount = max(1, availableWidth / minimumCellWidth)
    val current = recycler.layoutManager as? GridLayoutManager
    if (current == null) {
        recycler.layoutManager = GridLayoutManager(recycler.context, spanCount)
    } else if (current.spanCount != spanCount) {
        current.spanCount = spanCount
    }
}

private data class QuickSubtitleCandidateEntry(
    val id: Long,
    var text: String,
    var colorArgb: Int?
)

private class QuickSubtitleCandidateAdapter(
    private val parentComposition: CompositionContext,
    private val onItemClick: (Int, String) -> Unit,
    private val onEditRequested: (Int, String, Int?) -> Unit,
    private val onDeleteRequested: (Int, String) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onManualSortRequired: () -> Unit
) : RecyclerView.Adapter<QuickSubtitleCandidateAdapter.CandidateViewHolder>() {
    private val items = mutableListOf<QuickSubtitleCandidateEntry>()
    private var nextId = 1L
    private var draggingItemId: Long? = null
    private var menuItemId: Long? = null
    private var blockedClickItemId: Long? = null
    private var blockedClickUntilMs = 0L
    var grid: Boolean = false
        private set
    var dragEnabled: Boolean = true
        private set

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items[position].id

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder {
        val view = ComposeView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setParentCompositionContext(parentComposition)
        }
        return CandidateViewHolder(view)
    }

    override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
        val entry = items[position]
        holder.bindGesture(
            dragEnabled = dragEnabled,
            onStartDrag = {
                closeMenu()
                blockNextClick(entry.id)
                onStartDrag(holder)
            },
            onManualSortRequired = {
                blockNextClick(entry.id)
                onManualSortRequired()
            },
            onOpenMenu = {
                blockNextClick(entry.id)
                openMenu(entry.id)
            }
        )
        holder.composeView.setContent {
            KigttsFontScaleProvider {
                QuickSubtitleCandidateItem(
                    text = entry.text,
                    colorArgb = entry.colorArgb,
                    grid = grid,
                    dragged = draggingItemId == entry.id,
                    menuExpanded = menuItemId == entry.id,
                    canDelete = items.size > 1,
                    onClick = {
                        if (consumeBlockedClick(entry.id)) return@QuickSubtitleCandidateItem
                        val index = holder.bindingAdapterPosition
                        if (index in items.indices) onItemClick(index, items[index].text)
                    },
                    onDismissMenu = { closeMenu() },
                    onEdit = {
                        val index = holder.bindingAdapterPosition
                        closeMenu()
                        if (index in items.indices) {
                            val item = items[index]
                            onEditRequested(index, item.text, item.colorArgb)
                        }
                    },
                    onDelete = {
                        val index = holder.bindingAdapterPosition
                        closeMenu()
                        if (index in items.indices && items.size > 1) {
                            onDeleteRequested(index, items[index].text)
                        }
                    }
                )
            }
        }
    }

    override fun onViewRecycled(holder: CandidateViewHolder) {
        holder.cancelGesture()
        super.onViewRecycled(holder)
    }

    fun updateInteractionMode(grid: Boolean, dragEnabled: Boolean) {
        val changed = this.grid != grid || this.dragEnabled != dragEnabled
        this.grid = grid
        this.dragEnabled = dragEnabled
        if (changed) {
            closeMenu()
            notifyDataSetChanged()
        }
    }

    fun submitFromState(newItems: List<String>, newColors: List<Int?>) {
        if (draggingItemId != null) return
        val oldItems = items.toList()
        val used = BooleanArray(oldItems.size)
        val mapped = ArrayList<QuickSubtitleCandidateEntry>(newItems.size)
        newItems.forEachIndexed { index, text ->
            val color = newColors.getOrNull(index)
            val exact = oldItems.indices.firstOrNull { oldIndex ->
                !used[oldIndex] && oldItems[oldIndex].text == text && oldItems[oldIndex].colorArgb == color
            }
            val textOnly = exact ?: oldItems.indices.firstOrNull { oldIndex ->
                !used[oldIndex] && oldItems[oldIndex].text == text
            }
            if (textOnly != null) {
                used[textOnly] = true
                mapped += oldItems[textOnly].copy(text = text, colorArgb = color)
            } else {
                mapped += QuickSubtitleCandidateEntry(nextId++, text, color)
            }
        }
        items.clear()
        items.addAll(mapped)
        if (menuItemId != null && items.none { it.id == menuItemId }) menuItemId = null
        notifyDataSetChanged()
    }

    fun move(from: Int, to: Int): Boolean {
        if (from == to || from !in items.indices || to !in items.indices) return false
        val moved = items.removeAt(from)
        items.add(to, moved)
        notifyItemMoved(from, to)
        return true
    }

    fun snapshotTexts(): List<String> = items.map { it.text }

    fun snapshotColors(): List<Int?> = items.map { it.colorArgb }

    fun setDraggingItem(itemId: Long) {
        if (draggingItemId == itemId) return
        val previous = draggingItemId
        draggingItemId = itemId
        notifyIdChanged(previous)
        notifyIdChanged(itemId)
    }

    fun clearDraggingItem() {
        val previous = draggingItemId ?: return
        draggingItemId = null
        notifyIdChanged(previous)
    }

    private fun openMenu(itemId: Long) {
        val previous = menuItemId
        menuItemId = itemId
        notifyIdChanged(previous)
        notifyIdChanged(itemId)
    }

    private fun closeMenu() {
        val previous = menuItemId ?: return
        menuItemId = null
        notifyIdChanged(previous)
    }

    private fun blockNextClick(itemId: Long) {
        blockedClickItemId = itemId
        blockedClickUntilMs = SystemClock.uptimeMillis() + 400L
    }

    private fun consumeBlockedClick(itemId: Long): Boolean {
        if (blockedClickItemId != itemId || SystemClock.uptimeMillis() > blockedClickUntilMs) {
            return false
        }
        blockedClickItemId = null
        return true
    }

    private fun notifyIdChanged(itemId: Long?) {
        val index = items.indexOfFirst { it.id == itemId }
        if (index >= 0) notifyItemChanged(index)
    }

    class CandidateViewHolder(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView) {
        private var longPressRunnable: Runnable? = null

        fun bindGesture(
            dragEnabled: Boolean,
            onStartDrag: () -> Unit,
            onManualSortRequired: () -> Unit,
            onOpenMenu: () -> Unit
        ) {
            cancelGesture()
            val touchSlop = ViewConfiguration.get(composeView.context).scaledTouchSlop.toFloat()
            var downX = 0f
            var downY = 0f
            var pointerActive = false
            var longPressReady = false
            var dragStarted = false
            val armLongPress = Runnable {
                if (pointerActive) {
                    longPressReady = true
                    itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
            longPressRunnable = armLongPress
            composeView.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        pointerActive = true
                        longPressReady = false
                        dragStarted = false
                        downX = event.x
                        downY = event.y
                        composeView.postDelayed(armLongPress, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val distance = hypot(event.x - downX, event.y - downY)
                        if (!longPressReady && distance > touchSlop) {
                            composeView.removeCallbacks(armLongPress)
                        } else if (longPressReady && !dragStarted && distance > touchSlop) {
                            dragStarted = true
                            if (dragEnabled) onStartDrag() else onManualSortRequired()
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        composeView.removeCallbacks(armLongPress)
                        if (longPressReady && !dragStarted) onOpenMenu()
                        pointerActive = false
                        longPressReady = false
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        composeView.removeCallbacks(armLongPress)
                        pointerActive = false
                        longPressReady = false
                        dragStarted = false
                    }
                }
                false
            }
        }

        fun cancelGesture() {
            longPressRunnable?.let(composeView::removeCallbacks)
            longPressRunnable = null
            composeView.setOnTouchListener(null)
        }
    }
}

@Composable
private fun QuickSubtitleCandidateItem(
    text: String,
    colorArgb: Int?,
    grid: Boolean,
    dragged: Boolean,
    menuExpanded: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(UiTokens.Radius)
    val cardElevation by animateDpAsState(
        targetValue = if (dragged) 10.dp else 0.dp,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "candidate_drag_elevation"
    )
    val cardScale by animateFloatAsState(
        targetValue = if (dragged) 1.02f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "candidate_drag_scale"
    )
    val baseColor = md2CardContainerColor()
    val gridColor = if (currentAppDarkTheme()) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f).compositeOver(baseColor)
    } else {
        baseColor
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .height(if (grid) 76.dp else 64.dp)
            .scale(cardScale)
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            backgroundColor = if (grid) gridColor else baseColor,
            elevation = cardElevation
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .then(
                        if (grid) {
                            Modifier.border(
                                width = 1.dp,
                                color = colorArgb?.let(::Color)
                                    ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                                shape = shape
                            )
                        } else {
                            Modifier
                        }
                    )
                    .quickSubtitleItemColorMarker(
                        colorArgb = colorArgb,
                        edge = if (grid) QuickSubtitleItemColorEdge.Bottom else QuickSubtitleItemColorEdge.Left,
                        crossAxisInset = if (grid) 0.dp else 6.dp
                    )
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = text,
                    maxLines = if (grid) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        QuickSubtitleCandidateActionMenu(
            expanded = menuExpanded,
            canDelete = canDelete,
            onDismissRequest = onDismissMenu,
            onEdit = onEdit,
            onDelete = onDelete
        )
    }
}

@Composable
private fun QuickSubtitleCandidateActionMenu(
    expanded: Boolean,
    canDelete: Boolean,
    onDismissRequest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var rendered by remember { mutableStateOf(expanded) }
    val positionProvider = rememberTopEndDropdownPopupPositionProvider(verticalMargin = 2.dp)
    val alpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "candidate_action_menu_alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.92f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "candidate_action_menu_scale"
    )
    LaunchedEffect(expanded) {
        if (expanded) rendered = true else if (rendered) {
            delay(150L)
            rendered = false
        }
    }
    if (!rendered) return
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        Card(
            modifier = Modifier
                .padding(6.dp)
                .width(104.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
            shape = RoundedCornerShape(4.dp),
            backgroundColor = md2CardContainerColor(),
            elevation = UiTokens.MenuElevation
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.size(52.dp)) {
                    MsIcon("edit", contentDescription = "编辑快捷文本")
                }
                IconButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    modifier = Modifier.size(52.dp)
                ) {
                    MsIcon("delete", contentDescription = "删除快捷文本")
                }
            }
        }
    }
}
