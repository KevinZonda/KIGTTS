package com.lhtstudio.kigtts.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lhtstudio.kigtts.app.R
import com.lhtstudio.kigtts.app.overlay.OverlayBridge

class QuickEntryWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, QuickEntryWidgetProvider::class.java)
            onUpdate(context, manager, manager.getAppWidgetIds(component))
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_entry)
            bindEntry(
                context,
                views,
                widgetId,
                R.id.widget_entry_subtitle,
                OverlayBridge.TARGET_OPEN,
                1
            )
            bindEntry(
                context,
                views,
                widgetId,
                R.id.widget_entry_card,
                OverlayBridge.TARGET_OPEN_QUICK_CARD,
                2
            )
            bindEntry(
                context,
                views,
                widgetId,
                R.id.widget_entry_drawing,
                OverlayBridge.TARGET_OPEN_DRAWING,
                3
            )
            bindEntry(
                context,
                views,
                widgetId,
                R.id.widget_entry_soundboard,
                OverlayBridge.TARGET_OPEN_SOUNDBOARD,
                4
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun bindEntry(
        context: Context,
        views: RemoteViews,
        widgetId: Int,
        viewId: Int,
        target: String,
        slot: Int
    ) {
        views.setOnClickPendingIntent(
            viewId,
            WidgetPendingIntents.openPage(
                context = context,
                target = target,
                requestCode = widgetId * 10 + slot
            )
        )
    }
}
