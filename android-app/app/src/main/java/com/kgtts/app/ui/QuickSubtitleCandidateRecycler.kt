package com.lhtstudio.kigtts.app.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max

@Composable
internal fun QuickSubtitleCandidateRecycler(
    items: List<String>,
    itemColors: List<Int?>,
    grid: Boolean,
    dragEnabled: Boolean,
    canMoveToGroup: Boolean,
    onItemClick: (Int, String) -> Unit,
    onItemsReordered: (List<String>, List<Int?>) -> Unit,
    onManualSortRequired: () -> Unit,
    onEditRequested: (Int, String, Int?) -> Unit,
    onMoveRequested: (Int, String) -> Unit,
    onDeleteRequested: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val parentComposition = rememberCompositionContext()
    val currentOnItemClick by rememberUpdatedState(onItemClick)
    val currentOnItemsReordered by rememberUpdatedState(onItemsReordered)
    val currentOnManualSortRequired by rememberUpdatedState(onManualSortRequired)
    val currentOnEditRequested by rememberUpdatedState(onEditRequested)
    val currentOnMoveRequested by rememberUpdatedState(onMoveRequested)
    val currentOnDeleteRequested by rememberUpdatedState(onDeleteRequested)

    BoxWithConstraints(modifier = modifier) {
        val gridSpanCount = quickSubtitleCandidateGridSpanCount(maxWidth.value)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
            val recycler = RecyclerView(context).apply {
                clipToPadding = false
                clipChildren = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                itemAnimator = null
            }
            lateinit var touchHelper: ItemTouchHelper
            val adapter = QuickSubtitleCandidateAdapter(
                parentComposition = parentComposition,
                onItemClick = { index, text -> currentOnItemClick(index, text) },
                onEditRequested = { index, text, color ->
                    currentOnEditRequested(index, text, color)
                },
                onMoveRequested = { index, text -> currentOnMoveRequested(index, text) },
                onDeleteRequested = { index, text -> currentOnDeleteRequested(index, text) }
            )
            recycler.adapter = adapter

            var moved = false
            var activeDragItemId: Long? = null
            val disableDragAnimator = Runnable {
                if (activeDragItemId == null) recycler.itemAnimator = null
            }
            val dragMoveAnimator = DefaultItemAnimator().apply {
                supportsChangeAnimations = false
                addDuration = 0L
                removeDuration = 0L
                changeDuration = 0L
                moveDuration = CANDIDATE_DRAG_MOVE_DURATION_MS
            }
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
                        recycler.removeCallbacks(disableDragAnimator)
                        recycler.itemAnimator = dragMoveAnimator
                        moved = false
                        activeDragItemId = viewHolder.itemId
                        adapter.setDraggingItem(viewHolder.itemId)
                        viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
                    } else {
                        activeDragItemId?.let { itemId ->
                            recycler.post { adapter.openMenu(itemId) }
                        }
                    }
                    moved = false
                    activeDragItemId = null
                    recycler.postDelayed(
                        disableDragAnimator,
                        CANDIDATE_DRAG_MOVE_DURATION_MS + 24L
                    )
                }
            }
            touchHelper = ItemTouchHelper(callback).also { it.attachToRecyclerView(recycler) }

            val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
            var pendingHolder: RecyclerView.ViewHolder? = null
            var pendingItemId: Long? = null
            var downX = 0f
            var downY = 0f
            var longPressReady = false
            var manualSortRequested = false
            val armFrequencyLongPress = Runnable {
                val holder = pendingHolder
                if (holder != null && holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    longPressReady = true
                    adapter.blockClick(holder.itemId)
                    if (adapter.dragEnabled) {
                        touchHelper.startDrag(holder)
                    } else {
                        holder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                }
            }
            fun resetFrequencyLongPress() {
                recycler.removeCallbacks(armFrequencyLongPress)
                pendingHolder = null
                pendingItemId = null
                longPressReady = false
                manualSortRequested = false
            }
            recycler.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, event: android.view.MotionEvent): Boolean {
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            resetFrequencyLongPress()
                            downX = event.x
                            downY = event.y
                            pendingHolder = rv.findChildViewUnder(event.x, event.y)
                                ?.let(rv::getChildViewHolder)
                            pendingItemId = pendingHolder?.itemId
                            if (pendingItemId != null) {
                                recycler.postDelayed(
                                    armFrequencyLongPress,
                                    ViewConfiguration.getLongPressTimeout().toLong()
                                )
                            }
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val movedPastSlop = kotlin.math.hypot(
                                event.x - downX,
                                event.y - downY
                            ) > touchSlop
                            if (!longPressReady && movedPastSlop) {
                                recycler.removeCallbacks(armFrequencyLongPress)
                                pendingItemId = null
                            } else if (
                                !adapter.dragEnabled &&
                                longPressReady &&
                                movedPastSlop &&
                                !manualSortRequested
                            ) {
                                manualSortRequested = true
                                adapter.closeMenu()
                                currentOnManualSortRequired()
                            }
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            recycler.removeCallbacks(armFrequencyLongPress)
                            if (
                                longPressReady &&
                                !manualSortRequested &&
                                (!adapter.dragEnabled || activeDragItemId == null)
                            ) {
                                pendingItemId?.let(adapter::openMenu)
                            }
                            resetFrequencyLongPress()
                        }
                        android.view.MotionEvent.ACTION_CANCEL -> resetFrequencyLongPress()
                    }
                    return false
                }
            })
            recycler
            },
            update = { recycler ->
                val adapter = recycler.adapter as QuickSubtitleCandidateAdapter
                adapter.updateInteractionMode(
                    grid = grid,
                    dragEnabled = dragEnabled,
                    canMoveToGroup = canMoveToGroup
                )
                adapter.submitFromState(items, itemColors)
                applyQuickSubtitleCandidateLayout(recycler, grid, gridSpanCount)
            }
        )
    }
}

private const val CANDIDATE_DRAG_MOVE_DURATION_MS = 180L

internal fun quickSubtitleCandidateGridSpanCount(containerWidthDp: Float): Int {
    val contentWidth = (containerWidthDp - 20f).coerceAtLeast(0f)
    return max(1, ((contentWidth + 8f) / (138f + 8f)).toInt())
}

private fun applyQuickSubtitleCandidateLayout(
    recycler: RecyclerView,
    grid: Boolean,
    gridSpanCount: Int
) {
    val density = recycler.resources.displayMetrics.density
    val insetDp = if (grid) 6f else 10f
    val inset = (insetDp * density).toInt()
    if (recycler.paddingLeft != inset || recycler.paddingTop != inset) {
        recycler.setPadding(inset, inset, inset, inset)
    }
    if (!grid) {
        if (recycler.layoutManager !is LinearLayoutManager ||
            recycler.layoutManager is GridLayoutManager
        ) {
            recycler.layoutManager = LinearLayoutManager(recycler.context)
        }
        return
    }
    val current = recycler.layoutManager as? GridLayoutManager
    if (current == null) {
        recycler.layoutManager = GridLayoutManager(recycler.context, gridSpanCount)
    } else if (current.spanCount != gridSpanCount) {
        current.spanCount = gridSpanCount
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
    private val onMoveRequested: (Int, String) -> Unit,
    private val onDeleteRequested: (Int, String) -> Unit
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
    private var canMoveToGroup: Boolean = false

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
        holder.composeView.setContent {
            KigttsFontScaleProvider {
                QuickSubtitleCandidateItem(
                    text = entry.text,
                    colorArgb = entry.colorArgb,
                    grid = grid,
                    dragged = draggingItemId == entry.id,
                    menuExpanded = menuItemId == entry.id,
                    canDelete = items.size > 1,
                    canMoveToGroup = canMoveToGroup,
                    showDivider = !grid && position < items.lastIndex,
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
                    onMove = {
                        val index = holder.bindingAdapterPosition
                        closeMenu()
                        if (index in items.indices && canMoveToGroup) {
                            onMoveRequested(index, items[index].text)
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

    fun updateInteractionMode(
        grid: Boolean,
        dragEnabled: Boolean,
        canMoveToGroup: Boolean
    ) {
        val changed = this.grid != grid ||
            this.dragEnabled != dragEnabled ||
            this.canMoveToGroup != canMoveToGroup
        this.grid = grid
        this.dragEnabled = dragEnabled
        this.canMoveToGroup = canMoveToGroup
        if (changed) {
            closeMenu()
            notifyDataSetChanged()
        }
    }

    fun submitFromState(newItems: List<String>, newColors: List<Int?>) {
        if (draggingItemId != null) return
        if (
            items.size == newItems.size &&
            items.indices.all { index ->
                items[index].text == newItems[index] &&
                    items[index].colorArgb == newColors.getOrNull(index)
            }
        ) return
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
        notifyItemRangeChanged(minOf(from, to), kotlin.math.abs(to - from) + 1)
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

    fun openMenu(itemId: Long) {
        blockClick(itemId)
        val previous = menuItemId
        menuItemId = itemId
        notifyIdChanged(previous)
        notifyIdChanged(itemId)
    }

    fun closeMenu() {
        val previous = menuItemId ?: return
        menuItemId = null
        notifyIdChanged(previous)
    }

    fun blockClick(itemId: Long) {
        blockedClickItemId = itemId
        blockedClickUntilMs = SystemClock.uptimeMillis() + 3_000L
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

    class CandidateViewHolder(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView)
}
