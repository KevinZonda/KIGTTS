package com.lhtstudio.kigtts.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.lhtstudio.kigtts.app.overlay.OverlayBridge

internal object WidgetPendingIntents {
    private const val FLAG = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun openPage(context: Context, target: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            OverlayBridge.buildOpenPageIntent(context, target),
            FLAG
        )

    fun selectWidgetSubtitle(
        context: Context,
        widgetId: Int,
        text: String,
        requestCode: Int
    ): PendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, QuickSubtitleWidgetProvider::class.java).apply {
                action = QuickSubtitleWidgetProvider.ACTION_SELECT_SUBTITLE
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(QuickSubtitleWidgetProvider.EXTRA_SUBTITLE_TEXT, text)
            },
            FLAG
        )

    fun openWidgetSubtitleGroup(
        context: Context,
        widgetId: Int,
        groupId: Long,
        requestCode: Int
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, QuickSubtitleWidgetProvider::class.java).apply {
            action = QuickSubtitleWidgetProvider.ACTION_OPEN_GROUP
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(QuickSubtitleWidgetProvider.EXTRA_GROUP_ID, groupId)
        },
        FLAG
    )

    fun openCard(context: Context, cardId: Long, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            OverlayBridge.buildOpenQuickCardIntent(context, cardId),
            FLAG
        )

    fun createCard(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            OverlayBridge.buildOpenPageIntent(context, OverlayBridge.TARGET_CREATE_QUICK_CARD),
            FLAG
        )

    fun widgetBroadcast(
        context: Context,
        providerClass: Class<*>,
        action: String,
        widgetId: Int,
        requestCode: Int
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, providerClass).apply {
            this.action = action
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        },
        FLAG
    )
}
