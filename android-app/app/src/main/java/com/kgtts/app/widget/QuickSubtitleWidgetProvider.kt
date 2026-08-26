package com.lhtstudio.kigtts.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.lhtstudio.kigtts.app.R
import com.lhtstudio.kigtts.app.overlay.OverlayBridge
import kotlin.math.ceil

class QuickSubtitleWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            refreshAll(context)
            return
        }
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            super.onReceive(context, intent)
            return
        }
        val manager = AppWidgetManager.getInstance(context)
        when (intent.action) {
            ACTION_SELECT_SUBTITLE -> selectSubtitle(
                context,
                manager,
                widgetId,
                intent.getStringExtra(EXTRA_SUBTITLE_TEXT).orEmpty()
            )
            ACTION_OPEN_GROUP -> openGroup(
                context,
                manager,
                widgetId,
                intent.getLongExtra(EXTRA_GROUP_ID, Long.MIN_VALUE)
            )
            ACTION_BACK_TO_GROUPS -> showGroups(context, manager, widgetId)
            ACTION_GROUP_PREVIOUS -> shiftGroupPage(context, manager, widgetId, -1)
            ACTION_GROUP_NEXT -> shiftGroupPage(context, manager, widgetId, 1)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences(StatePrefs, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { widgetId ->
            editor.remove(subtitleKey(widgetId))
            editor.remove(subtitleSlotKey(widgetId))
            editor.remove(modeKey(widgetId))
            editor.remove(groupIdKey(widgetId))
            editor.remove(groupPageKey(widgetId))
            editor.remove(itemPageKey(widgetId))
        }
        editor.apply()
        super.onDeleted(context, appWidgetIds)
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        options: Bundle = manager.getAppWidgetOptions(widgetId)
    ) {
        val snapshot = WidgetSnapshotStore.readQuickSubtitle(context)
        manager.updateAppWidget(widgetId, buildViews(context, widgetId, options, snapshot))
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_subtitle_candidates)
    }

    private fun buildViews(
        context: Context,
        widgetId: Int,
        options: Bundle,
        snapshot: WidgetSubtitleSnapshot?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_subtitle)
        val openIntent = WidgetPendingIntents.openPage(context, OverlayBridge.TARGET_OPEN, widgetId * 100)
        val subtitle = subtitleFor(context, widgetId)
        val subtitleSlot = subtitleSlotFor(context, widgetId)

        views.setOnClickPendingIntent(R.id.widget_subtitle_logo, openIntent)
        views.setOnClickPendingIntent(R.id.widget_subtitle_open, openIntent)
        views.setTextViewText(R.id.widget_subtitle_text, subtitle)
        views.setTextViewText(R.id.widget_subtitle_text_alternate, subtitle)
        views.setDisplayedChild(R.id.widget_subtitle_text_flipper, subtitleSlot)
        views.removeAllViews(R.id.widget_subtitle_groups_flipper)
        bindItemCollection(context, views, widgetId)

        if (snapshot == null || snapshot.groups.isEmpty()) {
            views.setViewVisibility(R.id.widget_subtitle_empty, View.VISIBLE)
            views.setViewVisibility(R.id.widget_subtitle_page_flipper, View.GONE)
            views.setOnClickPendingIntent(R.id.widget_subtitle_empty, openIntent)
        } else {
            views.setViewVisibility(R.id.widget_subtitle_empty, View.GONE)
            views.setViewVisibility(R.id.widget_subtitle_page_flipper, View.VISIBLE)
            val navigation = readNavigation(context, widgetId)
            val selectedGroup = selectedGroup(snapshot, navigation.groupId)
            views.setDisplayedChild(
                R.id.widget_subtitle_page_flipper,
                if (navigation.showItems) ItemsPage else GroupsPage
            )
            views.setTextViewText(R.id.widget_subtitle_group_title, selectedGroup.title)
            views.setOnClickPendingIntent(
                R.id.widget_items_back,
                WidgetPendingIntents.widgetBroadcast(
                    context,
                    QuickSubtitleWidgetProvider::class.java,
                    ACTION_BACK_TO_GROUPS,
                    widgetId,
                    widgetId * 10_000 + 20
                )
            )
            bindGroups(context, views, widgetId, options, snapshot.groups, navigation.groupPage)
        }

        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        views.setImageViewResource(
            R.id.widget_subtitle_logo,
            if (night) R.drawable.logo_white else R.drawable.logo_black
        )
        return views
    }

    private fun bindItemCollection(context: Context, views: RemoteViews, widgetId: Int) {
        val adapterIntent = Intent(context, QuickSubtitleWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse("kigtts://widget/quick-subtitle/$widgetId")
        }
        views.setRemoteAdapter(R.id.widget_subtitle_candidates, adapterIntent)
        views.setEmptyView(R.id.widget_subtitle_candidates, R.id.widget_subtitle_items_empty)
        views.setPendingIntentTemplate(
            R.id.widget_subtitle_candidates,
            WidgetPendingIntents.selectWidgetSubtitleTemplate(
                context,
                widgetId,
                widgetId * 10_000 + 300
            )
        )
    }

    private fun bindGroups(
        context: Context,
        views: RemoteViews,
        widgetId: Int,
        options: Bundle,
        groups: List<WidgetSubtitleGroupSnapshot>,
        requestedPage: Int
    ) {
        val perPage = groupsPerPage(options)
        val pageCount = pageCount(groups.size, perPage)
        val page = requestedPage.coerceIn(0, pageCount - 1)
        for (pageIndex in 0 until pageCount) {
            val pageViews = RemoteViews(
                context.packageName,
                R.layout.widget_quick_subtitle_group_page
            )
            val visibleGroups = groups.drop(pageIndex * perPage).take(perPage)
            visibleGroups.forEachIndexed { index, group ->
                val child = RemoteViews(
                    context.packageName,
                    R.layout.widget_quick_subtitle_group_item
                )
                child.setTextViewText(R.id.widget_group_item, group.title)
                child.setOnClickPendingIntent(
                    R.id.widget_group_item,
                    WidgetPendingIntents.openWidgetSubtitleGroup(
                        context,
                        widgetId,
                        group.id,
                        widgetId * 10_000 + 100 + pageIndex * perPage + index
                    )
                )
                pageViews.addView(R.id.widget_subtitle_groups, child)
            }
            repeat(perPage - visibleGroups.size) {
                val placeholder = RemoteViews(
                    context.packageName,
                    R.layout.widget_quick_subtitle_group_item
                )
                placeholder.setViewVisibility(R.id.widget_group_item, View.INVISIBLE)
                pageViews.addView(R.id.widget_subtitle_groups, placeholder)
            }
            views.addView(R.id.widget_subtitle_groups_flipper, pageViews)
        }
        views.setDisplayedChild(R.id.widget_subtitle_groups_flipper, page)
        bindGroupPager(context, views, widgetId, page, pageCount)
    }

    private fun bindGroupPager(
        context: Context,
        views: RemoteViews,
        widgetId: Int,
        page: Int,
        pageCount: Int
    ) {
        views.setViewVisibility(
            R.id.widget_group_previous,
            if (page > 0) View.VISIBLE else View.INVISIBLE
        )
        views.setViewVisibility(
            R.id.widget_group_next,
            if (page + 1 < pageCount) View.VISIBLE else View.INVISIBLE
        )
        if (page > 0) {
            views.setOnClickPendingIntent(
                R.id.widget_group_previous,
                WidgetPendingIntents.widgetBroadcast(
                    context,
                    QuickSubtitleWidgetProvider::class.java,
                    ACTION_GROUP_PREVIOUS,
                    widgetId,
                    widgetId * 10_000 + 10
                )
            )
        }
        if (page + 1 < pageCount) {
            views.setOnClickPendingIntent(
                R.id.widget_group_next,
                WidgetPendingIntents.widgetBroadcast(
                    context,
                    QuickSubtitleWidgetProvider::class.java,
                    ACTION_GROUP_NEXT,
                    widgetId,
                    widgetId * 10_000 + 11
                )
            )
        }
    }

    private fun selectSubtitle(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        rawText: String
    ) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        val nextSlot = 1 - subtitleSlotFor(context, widgetId)
        saveSubtitle(context, widgetId, text, nextSlot)
        saveNavigation(context, widgetId, readNavigation(context, widgetId).copy(showItems = false))
        val views = RemoteViews(context.packageName, R.layout.widget_quick_subtitle)
        views.setTextViewText(
            if (nextSlot == 0) R.id.widget_subtitle_text else R.id.widget_subtitle_text_alternate,
            text
        )
        views.setDisplayedChild(R.id.widget_subtitle_text_flipper, nextSlot)
        views.setDisplayedChild(R.id.widget_subtitle_page_flipper, GroupsPage)
        manager.partiallyUpdateAppWidget(widgetId, views)
    }

    private fun openGroup(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        requestedGroupId: Long
    ) {
        val snapshot = WidgetSnapshotStore.readQuickSubtitle(context) ?: return
        val group = selectedGroup(snapshot, requestedGroupId)
        saveNavigation(
            context,
            widgetId,
            readNavigation(context, widgetId).copy(showItems = true, groupId = group.id)
        )
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_subtitle_candidates)
        val views = RemoteViews(context.packageName, R.layout.widget_quick_subtitle)
        views.setTextViewText(R.id.widget_subtitle_group_title, group.title)
        views.setScrollPosition(R.id.widget_subtitle_candidates, 0)
        views.setDisplayedChild(R.id.widget_subtitle_page_flipper, ItemsPage)
        manager.partiallyUpdateAppWidget(widgetId, views)
    }

    private fun showGroups(context: Context, manager: AppWidgetManager, widgetId: Int) {
        saveNavigation(context, widgetId, readNavigation(context, widgetId).copy(showItems = false))
        val views = RemoteViews(context.packageName, R.layout.widget_quick_subtitle)
        views.setDisplayedChild(R.id.widget_subtitle_page_flipper, GroupsPage)
        manager.partiallyUpdateAppWidget(widgetId, views)
    }

    private fun shiftGroupPage(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        delta: Int
    ) {
        val snapshot = WidgetSnapshotStore.readQuickSubtitle(context) ?: return
        val options = manager.getAppWidgetOptions(widgetId)
        val count = pageCount(snapshot.groups.size, groupsPerPage(options))
        val navigation = readNavigation(context, widgetId)
        val currentPage = navigation.groupPage.coerceIn(0, count - 1)
        val nextPage = (currentPage + delta).coerceIn(0, count - 1)
        if (nextPage == currentPage) return
        saveNavigation(context, widgetId, navigation.copy(groupPage = nextPage))
        val views = RemoteViews(context.packageName, R.layout.widget_quick_subtitle)
        views.setDisplayedChild(R.id.widget_subtitle_groups_flipper, nextPage)
        bindGroupPager(context, views, widgetId, nextPage, count)
        manager.partiallyUpdateAppWidget(widgetId, views)
    }

    private fun readNavigation(context: Context, widgetId: Int): SubtitleNavigation {
        val prefs = context.getSharedPreferences(StatePrefs, Context.MODE_PRIVATE)
        return SubtitleNavigation(
            showItems = prefs.getBoolean(modeKey(widgetId), false),
            groupId = prefs.getLong(groupIdKey(widgetId), Long.MIN_VALUE),
            groupPage = prefs.getInt(groupPageKey(widgetId), 0).coerceAtLeast(0)
        )
    }

    private fun saveNavigation(context: Context, widgetId: Int, value: SubtitleNavigation) {
        context.getSharedPreferences(StatePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(modeKey(widgetId), value.showItems)
            .putLong(groupIdKey(widgetId), value.groupId)
            .putInt(groupPageKey(widgetId), value.groupPage)
            .apply()
    }

    private fun subtitleFor(context: Context, widgetId: Int): String =
        context.getSharedPreferences(StatePrefs, Context.MODE_PRIVATE)
            .getString(subtitleKey(widgetId), DefaultSubtitle)
            ?.ifBlank { DefaultSubtitle }
            ?: DefaultSubtitle

    private fun subtitleSlotFor(context: Context, widgetId: Int): Int =
        context.getSharedPreferences(StatePrefs, Context.MODE_PRIVATE)
            .getInt(subtitleSlotKey(widgetId), 0)
            .coerceIn(0, 1)

    private fun saveSubtitle(context: Context, widgetId: Int, text: String, slot: Int) {
        context.getSharedPreferences(StatePrefs, Context.MODE_PRIVATE)
            .edit()
            .putString(subtitleKey(widgetId), text)
            .putInt(subtitleSlotKey(widgetId), slot.coerceIn(0, 1))
            .apply()
    }

    private fun selectedGroup(
        snapshot: WidgetSubtitleSnapshot,
        requestedId: Long
    ): WidgetSubtitleGroupSnapshot =
        snapshot.groups.firstOrNull { it.id == requestedId }
            ?: snapshot.groups.firstOrNull { it.id == snapshot.selectedGroupId }
            ?: snapshot.groups.first()

    private fun groupsPerPage(options: Bundle): Int {
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        return when {
            widthDp >= 430 -> 4
            widthDp >= 320 -> 3
            else -> 2
        }
    }

    private fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, QuickSubtitleWidgetProvider::class.java)
        onUpdate(context, manager, manager.getAppWidgetIds(component))
    }

    private fun pageCount(itemCount: Int, perPage: Int): Int =
        ceil(itemCount.coerceAtLeast(1).toDouble() / perPage.coerceAtLeast(1)).toInt()
            .coerceAtLeast(1)

    private data class SubtitleNavigation(
        val showItems: Boolean,
        val groupId: Long,
        val groupPage: Int
    )

    companion object {
        internal const val ACTION_SELECT_SUBTITLE = "com.lhtstudio.kigtts.app.widget.SELECT_SUBTITLE"
        internal const val ACTION_OPEN_GROUP = "com.lhtstudio.kigtts.app.widget.OPEN_SUBTITLE_GROUP"
        internal const val EXTRA_SUBTITLE_TEXT = "subtitle_text"
        internal const val EXTRA_GROUP_ID = "group_id"
        private const val ACTION_BACK_TO_GROUPS =
            "com.lhtstudio.kigtts.app.widget.BACK_TO_GROUPS"
        private const val ACTION_GROUP_PREVIOUS =
            "com.lhtstudio.kigtts.app.widget.GROUP_PREVIOUS"
        private const val ACTION_GROUP_NEXT =
            "com.lhtstudio.kigtts.app.widget.GROUP_NEXT"
        private const val StatePrefs = "widget_state"
        private const val DefaultSubtitle = "便捷字幕"
        private const val GroupsPage = 0
        private const val ItemsPage = 1

        internal fun selectedGroupIdFor(context: Context, widgetId: Int): Long =
            context.getSharedPreferences(StatePrefs, Context.MODE_PRIVATE)
                .getLong(groupIdKey(widgetId), Long.MIN_VALUE)

        private fun subtitleKey(widgetId: Int) = "subtitle_text_$widgetId"
        private fun subtitleSlotKey(widgetId: Int) = "subtitle_text_slot_$widgetId"
        private fun modeKey(widgetId: Int) = "subtitle_items_mode_$widgetId"
        private fun groupIdKey(widgetId: Int) = "subtitle_group_id_$widgetId"
        private fun groupPageKey(widgetId: Int) = "subtitle_group_page_$widgetId"
        private fun itemPageKey(widgetId: Int) = "subtitle_item_page_$widgetId"
    }
}
