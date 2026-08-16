package com.lhtstudio.kigtts.app.ui

import android.content.Context
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

private const val GROUP_DRAG_MOVE_DURATION_MS = 180L

@Composable
internal fun QuickSubtitleCandidateGroupRecycler(
    groups: List<QuickSubtitleGroup>,
    selectedGroupId: Long?,
    vertical: Boolean,
    showLabels: Boolean,
    onSelectGroup: (Int) -> Unit,
    onGroupsReordered: (List<Long>) -> Unit,
    onEditRequested: (Long) -> Unit,
    onGroupBoundsChanged: (Int, Rect) -> Unit,
    onCanScrollForwardChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val parentComposition = rememberCompositionContext()
    val currentOnSelectGroup by rememberUpdatedState(onSelectGroup)
    val currentOnGroupsReordered by rememberUpdatedState(onGroupsReordered)
    val currentOnEditRequested by rememberUpdatedState(onEditRequested)
    val currentOnGroupBoundsChanged by rememberUpdatedState(onGroupBoundsChanged)
    val currentOnCanScrollForwardChanged by rememberUpdatedState(onCanScrollForwardChanged)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val recycler = QuickSubtitleGroupRecyclerView(context).apply {
                clipToPadding = false
                clipChildren = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                itemAnimator = null
            }
            lateinit var touchHelper: ItemTouchHelper
            val adapter = QuickSubtitleCandidateGroupAdapter(
                parentComposition = parentComposition,
                onSelectGroup = { index -> currentOnSelectGroup(index) },
                onEditRequested = { groupId -> currentOnEditRequested(groupId) },
                onGroupBoundsChanged = { index, bounds ->
                    currentOnGroupBoundsChanged(index, bounds)
                }
            )
            recycler.adapter = adapter

            fun reportCanScrollForward() {
                val canScrollForward = if (adapter.vertical) {
                    recycler.canScrollVertically(1)
                } else {
                    recycler.canScrollHorizontally(1)
                }
                currentOnCanScrollForwardChanged(canScrollForward)
            }
            recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    reportCanScrollForward()
                }
            })
            recycler.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                reportCanScrollForward()
            }

            var moved = false
            var activeDragGroupId: Long? = null
            val disableDragAnimator = Runnable {
                if (activeDragGroupId == null) recycler.itemAnimator = null
            }
            val dragMoveAnimator = DefaultItemAnimator().apply {
                supportsChangeAnimations = false
                addDuration = 0L
                removeDuration = 0L
                changeDuration = 0L
                moveDuration = GROUP_DRAG_MOVE_DURATION_MS
            }
            val callback = object : ItemTouchHelper.Callback() {
                override fun isLongPressDragEnabled(): Boolean = false

                override fun isItemViewSwipeEnabled(): Boolean = false

                override fun getMovementFlags(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ): Int {
                    val flags = if (adapter.vertical) {
                        ItemTouchHelper.UP or ItemTouchHelper.DOWN
                    } else {
                        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                    }
                    return makeMovementFlags(flags, 0)
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
                        activeDragGroupId = viewHolder.itemId
                        adapter.closeMenu()
                        adapter.setDraggingGroup(viewHolder.itemId)
                        viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        viewHolder.itemView.translationZ = 20f * recycler.resources.displayMetrics.density
                    } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                        adapter.clearDraggingGroup()
                    }
                }

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.translationZ = 0f
                    adapter.clearDraggingGroup()
                    if (moved) {
                        currentOnGroupsReordered(adapter.snapshotGroupIds())
                    } else {
                        activeDragGroupId?.let { groupId ->
                            recycler.post { adapter.openMenu(groupId) }
                        }
                    }
                    moved = false
                    activeDragGroupId = null
                    recycler.postDelayed(
                        disableDragAnimator,
                        GROUP_DRAG_MOVE_DURATION_MS + 24L
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
            val adapter = recycler.adapter as QuickSubtitleCandidateGroupAdapter
            adapter.updateState(
                groups = groups,
                selectedGroupId = selectedGroupId,
                vertical = vertical,
                showLabels = showLabels
            )
            applyQuickSubtitleGroupLayout(recycler, vertical)
            recycler.post {
                val canScrollForward = if (vertical) {
                    recycler.canScrollVertically(1)
                } else {
                    recycler.canScrollHorizontally(1)
                }
                currentOnCanScrollForwardChanged(canScrollForward)
            }
        }
    )
}

private fun applyQuickSubtitleGroupLayout(recycler: RecyclerView, vertical: Boolean) {
    val orientation = if (vertical) RecyclerView.VERTICAL else RecyclerView.HORIZONTAL
    val current = recycler.layoutManager as? LinearLayoutManager
    if (current == null || current.orientation != orientation) {
        recycler.layoutManager = LinearLayoutManager(recycler.context, orientation, false)
    }
    val density = recycler.resources.displayMetrics.density
    val horizontalInset = ((if (vertical) 3f else 4f) * density).toInt()
    val verticalInset = (4f * density).toInt()
    recycler.setPadding(horizontalInset, verticalInset, horizontalInset, verticalInset)
}

private class QuickSubtitleCandidateGroupAdapter(
    private val parentComposition: CompositionContext,
    private val onSelectGroup: (Int) -> Unit,
    private val onEditRequested: (Long) -> Unit,
    private val onGroupBoundsChanged: (Int, Rect) -> Unit
) : RecyclerView.Adapter<QuickSubtitleCandidateGroupAdapter.GroupViewHolder>() {
    private val groups = mutableListOf<QuickSubtitleGroup>()
    private var selectedGroupId: Long? = null
    private var draggingGroupId: Long? = null
    private var menuGroupId: Long? = null
    private var blockedClickGroupId: Long? = null
    private var blockedClickUntilMs = 0L
    var vertical: Boolean = false
        private set
    private var showLabels: Boolean = true

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = groups[position].id

    override fun getItemCount(): Int = groups.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = ComposeView(parent.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setParentCompositionContext(parentComposition)
        }
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        holder.composeView.layoutParams = RecyclerView.LayoutParams(
            if (vertical) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
            if (vertical) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
        )
        holder.composeView.setContent {
            KigttsFontScaleProvider {
                QuickSubtitleCandidateGroupItem(
                    group = group,
                    selected = selectedGroupId == group.id,
                    vertical = vertical,
                    showLabel = !vertical || showLabels,
                    dragged = draggingGroupId == group.id,
                    menuExpanded = menuGroupId == group.id,
                    onClick = {
                        if (consumeBlockedClick(group.id)) return@QuickSubtitleCandidateGroupItem
                        val index = holder.bindingAdapterPosition
                        if (index in groups.indices) onSelectGroup(index)
                    },
                    onBoundsChanged = { bounds ->
                        val index = holder.bindingAdapterPosition
                        if (index in groups.indices) onGroupBoundsChanged(index, bounds)
                    },
                    onDismissMenu = { closeMenu() },
                    onEdit = {
                        closeMenu()
                        onEditRequested(group.id)
                    }
                )
    }
}

    }

    fun updateState(
        groups: List<QuickSubtitleGroup>,
        selectedGroupId: Long?,
        vertical: Boolean,
        showLabels: Boolean
    ) {
        if (draggingGroupId != null) return
        val structureChanged = this.groups != groups ||
            this.vertical != vertical ||
            this.showLabels != showLabels
        val previousSelectedId = this.selectedGroupId
        this.selectedGroupId = selectedGroupId
        this.vertical = vertical
        this.showLabels = showLabels
        if (structureChanged) {
            this.groups.clear()
            this.groups.addAll(groups)
            if (menuGroupId != null && this.groups.none { it.id == menuGroupId }) menuGroupId = null
            notifyDataSetChanged()
        } else if (previousSelectedId != selectedGroupId) {
            notifyIdChanged(previousSelectedId)
            notifyIdChanged(selectedGroupId)
        }
    }

    fun move(from: Int, to: Int): Boolean {
        if (from == to || from !in groups.indices || to !in groups.indices) return false
        val moved = groups.removeAt(from)
        groups.add(to, moved)
        notifyItemMoved(from, to)
        return true
    }

    fun snapshotGroupIds(): List<Long> = groups.map { it.id }

    fun setDraggingGroup(groupId: Long) {
        if (draggingGroupId == groupId) return
        val previous = draggingGroupId
        draggingGroupId = groupId
        notifyIdChanged(previous)
        notifyIdChanged(groupId)
    }

    fun clearDraggingGroup() {
        val previous = draggingGroupId ?: return
        draggingGroupId = null
        notifyIdChanged(previous)
    }

    fun openMenu(groupId: Long) {
        blockClick(groupId)
        val previous = menuGroupId
        menuGroupId = groupId
        notifyIdChanged(previous)
        notifyIdChanged(groupId)
    }

    fun closeMenu() {
        val previous = menuGroupId ?: return
        menuGroupId = null
        notifyIdChanged(previous)
    }

    fun blockClick(groupId: Long) {
        blockedClickGroupId = groupId
        blockedClickUntilMs = SystemClock.uptimeMillis() + 3_000L
    }

    private fun consumeBlockedClick(groupId: Long): Boolean {
        if (blockedClickGroupId != groupId || SystemClock.uptimeMillis() > blockedClickUntilMs) {
            return false
        }
        blockedClickGroupId = null
        return true
    }

    private fun notifyIdChanged(groupId: Long?) {
        val index = groups.indexOfFirst { it.id == groupId }
        if (index >= 0) notifyItemChanged(index)
    }

    class GroupViewHolder(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView)
}

private class QuickSubtitleGroupRecyclerView(context: Context) : RecyclerView(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f
    private var downY = 0f

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = kotlin.math.abs(event.x - downX)
                val dy = kotlin.math.abs(event.y - downY)
                if (dx > touchSlop || dy > touchSlop) {
                    val canScrollHorizontally =
                        canScrollHorizontally(-1) || canScrollHorizontally(1)
                    val scrollsAlongAxis = if (canScrollHorizontally) dx >= dy else dy >= dx
                    parent?.requestDisallowInterceptTouchEvent(scrollsAlongAxis)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(event)
    }
}
