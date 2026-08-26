package com.lhtstudio.kigtts.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.lhtstudio.kigtts.app.R

class QuickSubtitleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        QuickSubtitleFactory(applicationContext, intent)
}

private class QuickSubtitleFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {
    private val widgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )
    private var items: List<WidgetSubtitleItem> = emptyList()

    override fun onCreate() = onDataSetChanged()

    override fun onDataSetChanged() {
        val snapshot = WidgetSnapshotStore.readQuickSubtitle(context)
        val selectedId = QuickSubtitleWidgetProvider.selectedGroupIdFor(context, widgetId)
        val selectedGroup = snapshot?.groups?.firstOrNull { it.id == selectedId }
            ?: snapshot?.groups?.firstOrNull { it.id == snapshot.selectedGroupId }
            ?: snapshot?.groups?.firstOrNull()
        items = selectedGroup?.items.orEmpty()
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        val item = items.getOrNull(position) ?: return null
        return RemoteViews(context.packageName, R.layout.widget_quick_subtitle_item).apply {
            setTextViewText(R.id.widget_candidate_text, item.text)
            if (item.colorArgb == null) {
                setViewVisibility(R.id.widget_candidate_color, View.INVISIBLE)
            } else {
                setViewVisibility(R.id.widget_candidate_color, View.VISIBLE)
                setInt(R.id.widget_candidate_color, "setBackgroundColor", item.colorArgb)
            }
            setOnClickFillInIntent(
                R.id.widget_candidate_root,
                Intent().putExtra(QuickSubtitleWidgetProvider.EXTRA_SUBTITLE_TEXT, item.text)
            )
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
