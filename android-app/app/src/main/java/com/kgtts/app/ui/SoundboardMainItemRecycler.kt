package com.lhtstudio.kigtts.app.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lhtstudio.kigtts.app.audio.SoundboardPlaybackState
import com.lhtstudio.kigtts.app.data.SoundboardItem
import kotlin.math.max

private const val SOUNDBOARD_DRAG_MOVE_DURATION_MS = 180L

@Composable
internal fun SoundboardMainItemRecycler(
    items: List<SoundboardItem>,
    playbackStates: Map<Long, SoundboardPlaybackState>,
    grid: Boolean,
    onPlay: (SoundboardItem) -> Unit,
    onStop: (Long) -> Unit,
    onItemsReordered: (List<SoundboardItem>) -> Unit,
    onEditRequested: (Long) -> Unit,
    onDeleteRequested: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val parentComposition = rememberCompositionContext()
    val currentOnPlay by rememberUpdatedState(onPlay)
    val currentOnStop by rememberUpdatedState(onStop)
    val currentOnItemsReordered by rememberUpdatedState(onItemsReordered)
    val currentOnEditRequested by rememberUpdatedState(onEditRequested)
    val currentOnDeleteRequested by rememberUpdatedState(onDeleteRequested)

    BoxWithConstraints(modifier = modifier) {
        val spanCount = soundboardGridSpanCount(maxWidth.value)
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
                val adapter = SoundboardMainItemAdapter(
                    parentComposition = parentComposition,
                    onPlay = { item -> currentOnPlay(item) },
                    onStop = { itemId -> currentOnStop(itemId) },
                    onEditRequested = { itemId -> currentOnEditRequested(itemId) },
                    onDeleteRequested = { itemId -> currentOnDeleteRequested(itemId) }
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
                    moveDuration = SOUNDBOARD_DRAG_MOVE_DURATION_MS
                }
                val callback = object : ItemTouchHelper.Callback() {
                    override fun isLongPressDragEnabled(): Boolean = false

                    override fun isItemViewSwipeEnabled(): Boolean = false

                    override fun getMovementFlags(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder
                    ): Int {
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
                            adapter.closeMenu()
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
                            currentOnItemsReordered(adapter.snapshotItems())
                        } else {
                            activeDragItemId?.let { itemId ->
                                recycler.post { adapter.openMenu(itemId) }
                            }
                        }
                        moved = false
                        activeDragItemId = null
                        recycler.postDelayed(
                            disableDragAnimator,
                            SOUNDBOARD_DRAG_MOVE_DURATION_MS + 24L
                        )
                    }
                }
                touchHelper = ItemTouchHelper(callback).also { it.attachToRecyclerView(recycler) }

                val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                var pendingHolder: RecyclerView.ViewHolder? = null
                var downX = 0f
                var downY = 0f
                var longPressReady = false
                val armLongPress = Runnable {
                    val holder = pendingHolder
                    if (holder != null && holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                        longPressReady = true
                        adapter.blockClick(holder.itemId)
                        touchHelper.startDrag(holder)
                    }
                }
                fun resetLongPress() {
                    recycler.removeCallbacks(armLongPress)
                    pendingHolder = null
                    longPressReady = false
                }
                recycler.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                    override fun onInterceptTouchEvent(
                        recyclerView: RecyclerView,
                        event: android.view.MotionEvent
                    ): Boolean {
                        when (event.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                resetLongPress()
                                downX = event.x
                                downY = event.y
                                pendingHolder = recyclerView.findChildViewUnder(event.x, event.y)
                                    ?.let(recyclerView::getChildViewHolder)
                                if (pendingHolder != null) {
                                    recycler.postDelayed(
                                        armLongPress,
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
                                    recycler.removeCallbacks(armLongPress)
                                    pendingHolder = null
                                }
                            }

                            android.view.MotionEvent.ACTION_UP,
                            android.view.MotionEvent.ACTION_CANCEL -> resetLongPress()
                        }
                        return false
                    }
                })
                recycler
            },
            update = { recycler ->
                val adapter = recycler.adapter as SoundboardMainItemAdapter
                adapter.updateState(items, playbackStates, grid)
                applySoundboardMainLayout(recycler, grid, spanCount)
            }
        )
    }
}

private fun soundboardGridSpanCount(containerWidthDp: Float): Int {
    val contentWidth = (containerWidthDp - 16f).coerceAtLeast(0f)
    return max(1, ((contentWidth + 8f) / (156f + 8f)).toInt())
}

private fun applySoundboardMainLayout(
    recycler: RecyclerView,
    grid: Boolean,
    spanCount: Int
) {
    val density = recycler.resources.displayMetrics.density
    val horizontalInset = ((if (grid) 4f else 8f) * density).toInt()
    val verticalInset = (4f * density).toInt()
    recycler.setPadding(horizontalInset, verticalInset, horizontalInset, verticalInset)
    if (grid) {
        val current = recycler.layoutManager as? GridLayoutManager
        if (current == null) {
            recycler.layoutManager = GridLayoutManager(recycler.context, spanCount)
        } else if (current.spanCount != spanCount) {
            current.spanCount = spanCount
        }
    } else if (
        recycler.layoutManager !is LinearLayoutManager ||
        recycler.layoutManager is GridLayoutManager
    ) {
        recycler.layoutManager = LinearLayoutManager(recycler.context)
    }
}

private class SoundboardMainItemAdapter(
    private val parentComposition: CompositionContext,
    private val onPlay: (SoundboardItem) -> Unit,
    private val onStop: (Long) -> Unit,
    private val onEditRequested: (Long) -> Unit,
    private val onDeleteRequested: (Long) -> Unit
) : RecyclerView.Adapter<SoundboardMainItemAdapter.SoundboardViewHolder>() {
    private val items = mutableListOf<SoundboardItem>()
    private var playbackStates: Map<Long, SoundboardPlaybackState> = emptyMap()
    private var draggingItemId: Long? = null
    private var menuItemId: Long? = null
    private var blockedClickItemId: Long? = null
    private var blockedClickUntilMs = 0L
    var grid: Boolean = false
        private set

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items[position].id

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SoundboardViewHolder {
        val view = ComposeView(parent.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setParentCompositionContext(parentComposition)
        }
        return SoundboardViewHolder(view)
    }

    override fun onBindViewHolder(holder: SoundboardViewHolder, position: Int) {
        val item = items[position]
        holder.composeView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        holder.composeView.setContent {
            KigttsFontScaleProvider {
                val state = playbackStates[item.id]
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (grid) Modifier.padding(4.dp) else Modifier)
                ) {
                    if (grid) {
                        SoundboardGridItem(
                            item = item,
                            playing = state?.playing == true,
                            progress = state?.progress ?: 0f,
                            dragged = draggingItemId == item.id,
                            menuExpanded = menuItemId == item.id,
                            onPlay = { if (!consumeBlockedClick(item.id)) onPlay(item) },
                            onStop = { onStop(item.id) },
                            onDismissMenu = ::closeMenu,
                            onEdit = {
                                closeMenu()
                                onEditRequested(item.id)
                            },
                            onDelete = {
                                closeMenu()
                                onDeleteRequested(item.id)
                            }
                        )
                    } else {
                        SoundboardListItem(
                            item = item,
                            playing = state?.playing == true,
                            progress = state?.progress ?: 0f,
                            dragged = draggingItemId == item.id,
                            menuExpanded = menuItemId == item.id,
                            onPlay = { if (!consumeBlockedClick(item.id)) onPlay(item) },
                            onStop = { onStop(item.id) },
                            onDismissMenu = ::closeMenu,
                            onEdit = {
                                closeMenu()
                                onEditRequested(item.id)
                            },
                            onDelete = {
                                closeMenu()
                                onDeleteRequested(item.id)
                            }
                        )
                    }
                }
            }
        }
    }

    fun updateState(
        newItems: List<SoundboardItem>,
        newPlaybackStates: Map<Long, SoundboardPlaybackState>,
        grid: Boolean
    ) {
        val layoutChanged = this.grid != grid
        val previousPlayback = playbackStates
        playbackStates = newPlaybackStates
        this.grid = grid
        if (draggingItemId == null && (layoutChanged || items != newItems)) {
            items.clear()
            items.addAll(newItems)
            if (menuItemId != null && items.none { it.id == menuItemId }) menuItemId = null
            notifyDataSetChanged()
            return
        }
        if (draggingItemId != null) return
        items.forEachIndexed { index, item ->
            if (previousPlayback[item.id] != newPlaybackStates[item.id]) notifyItemChanged(index)
        }
    }

    fun move(from: Int, to: Int): Boolean {
        if (from == to || from !in items.indices || to !in items.indices) return false
        val moved = items.removeAt(from)
        items.add(to, moved)
        notifyItemMoved(from, to)
        return true
    }

    fun snapshotItems(): List<SoundboardItem> = items.toList()

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

    class SoundboardViewHolder(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView)
}
