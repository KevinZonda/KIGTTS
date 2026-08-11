package com.lhtstudio.kigtts.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
        when (intent.action) {
            ACTION_SELECT_SUBTITLE -> {
                val text = intent.getStringExtra(EXTRA_SUBTITLE_TEXT)?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    saveSubtitle(context, widgetId, text)
                    saveNavigation(
                        context,
                        widgetId,
                        readNavigation(context, widgetId).copy(showItems = false)
                    )
                }
            }
            ACTION_OPEN_GROUP -> {
                val groupId = intent.getLongExtra(EXTRA_GROUP_ID, Long.MIN_VALUE)
                saveNavigation(
                    context,
                    widgetId,
                    readNavigation(context, widgetId).copy(
                        showItems = true,
                        groupId = groupId,
                        itemPage = 0
                    )
                )
            }
            ACTION_BACK_TO_GROUPS -> saveNavigation(
                context,
                widgetId,
                readNavigation(context, widgetId).copy(showItems = false)
            )
            ACTION_GROUP_PREVIOUS -> shiftPage(context, widgetId, groupDelta = -1)
            ACTION_GROUP_NEXT -> shiftPage(context, widgetId, groupDelta = 1)
            ACTION_ITEM_PREVIOUS -> shiftPage(context, widgetId, itemDelta = -1)
            ACTION_ITEM_NEXT -> shiftPage(context, widgetId, itemDelta = 1)
            else -> {
                super.onReceive(context, intent)
                return
            }
        }
        updateWidget(context, AppWidgetManager.getInstance(context), widgetId)
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
        newOptions: android.os.Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences(StatePrefs, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { widgetId ->
            editor.remove(subtitleKey(widgetId))
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
        options: android.os.Bundle = manager.getAppWidgetOptions(widgetId)
    ) {
        manager.updateAppWidget(
            widgetId,
            buildViews(
                context = context,
                widgetId = widgetId,
                options = options,
                snapshot = WidgetSnapshotStore.readQuickSubtitle(context)
            )
        )
    }

    private fun buildViews(
        context: Context,
        widgetId: Int,
        options: android.os.Bundle,
        snapshot: WidgetSubtitleSnapshot?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_subtitle)
        val openIntent = WidgetPendingIntents.openPage(context, OverlayBridge.TARGET_OPEN, widgetId * 100)
        views.setOnClickPendingIntent(R.id.widget_subtitle_logo, openIntent)
        views.setOnClickPendingIntent(R.id.widget_subtitle_open, openIntent)
        views.setTextViewText(R.id.widget_subtitle_text, subtitleFor(context, widgetId))
        views.removeAllViews(R.id.widget_subtitle_groups)
        views.removeAllViews(R.id.widget_subtitle_candidates)

        if (snapshot == null || snapshot.groups.isEmpty()) {
            views.setViewVisibility(R.id.widget_subtitle_empty, View.VISIBLE)
            views.setViewVisibility(R.id.widget_subtitle_overview_panel, View.GONE)
            views.setViewVisibility(R.id.widget_subtitle_items_panel, View.GONE)
            views.setOnClickPendingIntent(R.id.widget_subtitle_empty, openIntent)
        } else {
            views.setViewVisibility(R.id.widget_subtitle_empty, View.GONE)
            val navigation = readNavigation(context, widgetId)
            val selectedGroup = snapshot.groups.firstOrNull { it.id == navigation.groupId }
                ?: snapshot.groups.firstOrNull { it.id == snapshot.selectedGroupId }
                ?: snapshot.groups.first()
            if (navigation.showItems) {
                views.setViewVisibility(R.id.widget_subtitle_overview_panel, View.GONE)
                views.setViewVisibility(R.id.widget_subtitle_items_panel, View.VISIBLE)
                bindItems(context, views, widgetId, options, selectedGroup, navigation)
            } else {
                views.setViewVisibility(R.id.widget_subtitle_overview_panel, View.VISIBLE)
                views.setViewVisibility(R.id.widget_subtitle_items_panel, View.GONE)
                bindGroups(context, views, widgetId, options, snapshot.groups, navigation)
            }
        }

        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        views.setImageViewResource(
            R.id.widget_subtitle_logo,
            if (night) R.drawable.logo_white else R.drawable.logo_black
        )
        return views
    }

    private fun bindGroups(
        context: Context,
        views: RemoteViews,
        widgetId: Int,
        options: android.os.Bundle,
        groups: List<WidgetSubtitleGroupSnapshot>,
        navigation: SubtitleNavigation
    ) {
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val perPage = when {
            widthDp >= 430 -> 4
            widthDp >= 320 -> 3
            else -> 2
        }
        val pageCount = pageCount(groups.size, perPage)
        val page = navigation.groupPage.coerceIn(0, pageCount - 1)
        val visibleGroups = groups.drop(page * perPage).take(perPage)
        visibleGroups.forEachIndexed { index, group ->
            val child = RemoteViews(context.packageName, R.layout.widget_quick_subtitle_group_item)
            child.setTextViewText(R.id.widget_group_item, group.title)
            child.setOnClickPendingIntent(
                R.id.widget_group_item,
                WidgetPendingIntents.openWidgetSubtitleGroup(
                    context,
                    widgetId,
                    group.id,
                    widgetId * 10_000 + 100 + page * perPage + index
                )
            )
            views.addView(R.id.widget_subtitle_groups, child)
        }
        repeat(perPage - visibleGroups.size) {
            val placeholder = RemoteViews(context.packageName, R.layout.widget_quick_subtitle_group_item)
            placeholder.setViewVisibility(R.id.widget_group_item, View.INVISIBLE)
            views.addView(R.id.widget_subtitle_groups, placeholder)
        }
        bindPager(
            context,
            views,
            widgetId,
            previousId = R.id.widget_group_previous,
            nextId = R.id.widget_group_next,
            previousAction = ACTION_GROUP_PREVIOUS,
            nextAction = ACTION_GROUP_NEXT,
            page = page,
            pageCount = pageCount,
            requestOffset = 10
        )
    }

    private fun bindItems(
        context: Context,
        views: RemoteViews,
        widgetId: Int,
        options: android.os.Bundle,
        group: WidgetSubtitleGroupSnapshot,
        navigation: SubtitleNavigation
    ) {
        views.setTextViewText(R.id.widget_subtitle_group_title, group.title)
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
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 300)
        val perPage = ((heightDp - 116).coerceAtLeast(180) / 60).coerceIn(3, 8)
        val pageCount = pageCount(group.items.size, perPage)
        val page = navigation.itemPage.coerceIn(0, pageCount - 1)
        val items = group.items.drop(page * perPage).take(perPage)
        if (items.isEmpty()) {
            val child = RemoteViews(context.packageName, R.layout.widget_quick_subtitle_item)
            child.setTextViewText(R.id.widget_candidate_text, "该分组暂无快捷文本")
            child.setViewVisibility(R.id.widget_candidate_color, View.INVISIBLE)
            views.addView(R.id.widget_subtitle_candidates, child)
            addItemPlaceholders(context, views, perPage - 1)
        } else {
            items.forEachIndexed { index, item ->
                val child = RemoteViews(context.packageName, R.layout.widget_quick_subtitle_item)
                child.setTextViewText(R.id.widget_candidate_text, item.text)
                if (item.colorArgb == null) {
                    child.setViewVisibility(R.id.widget_candidate_color, View.INVISIBLE)
                } else {
                    child.setViewVisibility(R.id.widget_candidate_color, View.VISIBLE)
                    child.setInt(R.id.widget_candidate_color, "setBackgroundColor", item.colorArgb)
                }
                val selectIntent = WidgetPendingIntents.selectWidgetSubtitle(
                    context,
                    widgetId,
                    item.text,
                    widgetId * 10_000 + 300 + page * perPage + index
                )
                child.setOnClickPendingIntent(R.id.widget_candidate_root, selectIntent)
                child.setOnClickPendingIntent(R.id.widget_candidate_text, selectIntent)
                views.addView(R.id.widget_subtitle_candidates, child)
            }
            addItemPlaceholders(context, views, perPage - items.size)
        }
        bindPager(
            context,
            views,
            widgetId,
            previousId = R.id.widget_items_previous,
            nextId = R.id.widget_items_next,
            previousAction = ACTION_ITEM_PREVIOUS,
            nextAction = ACTION_ITEM_NEXT,
            page = page,
            pageCount = pageCount,
            requestOffset = 30
        )
    }

    private fun addItemPlaceholders(context: Context, views: RemoteViews, count: Int) {
        repeat(count.coerceAtLeast(0)) {
            val placeholder = RemoteViews(context.packageName, R.layout.widget_quick_subtitle_item)
            placeholder.setViewVisibility(R.id.widget_candidate_root, View.INVISIBLE)
            views.addView(R.id.widget_subtitle_candidates, placeholder)
        }
    }

    private fun bindPager(
        context: Context,
        views: RemoteViews,
        widgetId: Int,
        previousId: Int,
        nextId: Int,
        previousAction: String,
        nextAction: String,
        page: Int,
        pageCount: Int,
        requestOffset: Int
    ) {
        views.setViewVisibility(previousId, if (page > 0) View.VISIBLE else View.INVISIBLE)
        views.setViewVisibility(nextId, if (page + 1 < pageCount) View.VISIBLE else View.INVISIBLE)
        if (page > 0) {
            views.setOnClickPendingIntent(
                previousId,
                WidgetPendingIntents.widgetBroadcast(
                    context,
                    QuickSubtitleWidgetProvider::class.java,
                    previousAction,
                    widgetId,
                    widgetId * 10_000 + requestOffset
                )
            )
        }
        if (page + 1 < pageCount) {
            views.setOnClickPendingIntent(
                nextId,
                WidgetPendingIntents.widgetBroadcast(
                    context,
                    QuickSubtitleWidgetProvider::class.java,
                    nextAction,
                    widgetId,
                    widgetId * 10_000 + requestOffset + 1
                )
            )
        }
    }

    private fun shiftPage(context: Context, widgetId: Int, groupDelta: Int = 0, itemDelta: Int = 0) {
        val current = readNavigation(context, widgetId)
        saveNavigation(
            context,
            widgetId,
            current.copy(
                groupPage = (current.groupPage + groupDelta).coerceAtLeast(0),
                itemPage = (current.itemPage + itemDelta).coerceAtLeast(0)
            )
        )
    }

    private fun readNavigation(context: Context, widgetId: Int): SubtitleNavigation {
        val prefs = context.getSharedPreferences(StatePrefs, Context.MODE_PRIVATE)
        return SubtitleNavigation(
            showItems = prefs.getBoolean(modeKey(widgetId), false),
            groupId = prefs.getLong(groupIdKey(widgetId), Long.MIN_VALUE),
            groupPage = prefs.getInt(groupPageKey(widgetId), 0).coerceAtLeast(0),
            itemPage = prefs.getInt(itemPageKey(widgetId), 0).coerceAtLeast(0)
        )
    }

    private fun saveNavigation(context: Context, widgetId: Int, value: SubtitleNavigation) {
        context.getSharedPreferences(StatePrefs, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(modeKey(widgetId), value.showItems)
            .putLong(groupIdKey(widgetId), value.groupId)
            .putInt(groupPageKey(widgetId), value.groupPage)
            .putInt(itemPageKey(widgetId), value.itemPage)
            .apply()
    }

    private fun subtitleFor(context: Context, widgetId: Int): String =
        context.getSharedPreferences(StatePrefs, Context.MODE_PRIVATE)
            .getString(subtitleKey(widgetId), DefaultSubtitle)
            ?.ifBlank { DefaultSubtitle }
            ?: DefaultSubtitle

    private fun saveSubtitle(context: Context, widgetId: Int, text: String) {
        context.getSharedPreferences(StatePrefs, Context.MODE_PRIVATE)
            .edit()
            .putString(subtitleKey(widgetId), text)
            .apply()
    }

    private fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, QuickSubtitleWidgetProvider::class.java)
        onUpdate(context, manager, manager.getAppWidgetIds(component))
    }

    private fun pageCount(itemCount: Int, perPage: Int): Int =
        ceil(itemCount.coerceAtLeast(1).toDouble() / perPage.coerceAtLeast(1)).toInt().coerceAtLeast(1)

    private data class SubtitleNavigation(
        val showItems: Boolean,
        val groupId: Long,
        val groupPage: Int,
        val itemPage: Int
    )

    companion object {
        internal const val ACTION_SELECT_SUBTITLE = "com.lhtstudio.kigtts.app.widget.SELECT_SUBTITLE"
        internal const val ACTION_OPEN_GROUP = "com.lhtstudio.kigtts.app.widget.OPEN_SUBTITLE_GROUP"
        internal const val EXTRA_SUBTITLE_TEXT = "subtitle_text"
        internal const val EXTRA_GROUP_ID = "group_id"
        private const val ACTION_BACK_TO_GROUPS = "com.lhtstudio.kigtts.app.widget.BACK_TO_GROUPS"
        private const val ACTION_GROUP_PREVIOUS = "com.lhtstudio.kigtts.app.widget.GROUP_PREVIOUS"
        private const val ACTION_GROUP_NEXT = "com.lhtstudio.kigtts.app.widget.GROUP_NEXT"
        private const val ACTION_ITEM_PREVIOUS = "com.lhtstudio.kigtts.app.widget.ITEM_PREVIOUS"
        private const val ACTION_ITEM_NEXT = "com.lhtstudio.kigtts.app.widget.ITEM_NEXT"
        private const val StatePrefs = "widget_state"
        private const val DefaultSubtitle = "便捷字幕"
        private fun subtitleKey(widgetId: Int) = "subtitle_text_$widgetId"
        private fun modeKey(widgetId: Int) = "subtitle_items_mode_$widgetId"
        private fun groupIdKey(widgetId: Int) = "subtitle_group_id_$widgetId"
        private fun groupPageKey(widgetId: Int) = "subtitle_group_page_$widgetId"
        private fun itemPageKey(widgetId: Int) = "subtitle_item_page_$widgetId"
    }
}
