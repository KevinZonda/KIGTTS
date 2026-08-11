package com.lhtstudio.kigtts.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.lhtstudio.kigtts.app.R
import com.lhtstudio.kigtts.app.overlay.OverlayBridge
import com.lhtstudio.kigtts.app.util.QuickCardRenderCache

class QuickCardWidgetProvider : AppWidgetProvider() {
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
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_CONFIGURATION_CHANGED -> refreshAll(context)
            ACTION_PREVIOUS, ACTION_NEXT -> {
                val widgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val cards = WidgetSnapshotStore.readQuickCards(context)?.cards.orEmpty()
                    if (cards.isNotEmpty()) {
                        val current = pageFor(context, widgetId, cards.lastIndex, 0)
                        val delta = if (intent.action == ACTION_PREVIOUS) -1 else 1
                        setPage(context, widgetId, (current + delta).floorMod(cards.size))
                    }
                    updateWidget(context, AppWidgetManager.getInstance(context), widgetId)
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { editor.remove(pageKey(it)) }
        editor.apply()
        super.onDeleted(context, appWidgetIds)
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        val snapshot = WidgetSnapshotStore.readQuickCards(context)
        val cards = snapshot?.cards.orEmpty()
        val views = RemoteViews(context.packageName, R.layout.widget_quick_card)
        if (cards.isEmpty()) {
            bindEmpty(context, views, widgetId)
        } else {
            val page = pageFor(
                context,
                widgetId,
                cards.lastIndex,
                snapshot?.selectedIndex ?: 0
            )
            bindCard(context, manager, views, widgetId, page, cards)
        }
        manager.updateAppWidget(widgetId, views)
    }

    private fun bindEmpty(context: Context, views: RemoteViews, widgetId: Int) {
        val create = WidgetPendingIntents.createCard(context, widgetId * 100)
        views.setViewVisibility(R.id.widget_card_preview, View.GONE)
        views.setViewVisibility(R.id.widget_card_qr, View.GONE)
        views.setViewVisibility(R.id.widget_card_header, View.GONE)
        views.setViewVisibility(R.id.widget_card_open, View.GONE)
        views.setViewVisibility(R.id.widget_card_logo, View.GONE)
        views.setViewVisibility(R.id.widget_card_empty, View.VISIBLE)
        views.setTextViewText(R.id.widget_card_empty, "点击新建快捷名片")
        views.setViewVisibility(R.id.widget_card_pager, View.GONE)
        views.setOnClickPendingIntent(R.id.widget_card_root, create)
        views.setOnClickPendingIntent(R.id.widget_card_empty, create)
    }

    private fun bindCard(
        context: Context,
        manager: AppWidgetManager,
        views: RemoteViews,
        widgetId: Int,
        page: Int,
        cards: List<WidgetCardSnapshot>
    ) {
        val card = cards[page]
        val open = WidgetPendingIntents.openCard(context, card.id, widgetId * 100 + 1)
        views.setViewVisibility(R.id.widget_card_preview, View.VISIBLE)
        if (card.link.isBlank()) {
            views.setViewVisibility(R.id.widget_card_qr, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_card_qr, View.VISIBLE)
            QuickCardRenderCache.loadQr(card.link, 640)?.let { qr ->
                views.setImageViewBitmap(R.id.widget_card_qr, qr)
            }
        }
        views.setViewVisibility(R.id.widget_card_header, View.VISIBLE)
        views.setViewVisibility(R.id.widget_card_open, View.VISIBLE)
        views.setViewVisibility(R.id.widget_card_logo, View.VISIBLE)
        views.setViewVisibility(R.id.widget_card_empty, View.GONE)
        views.setOnClickPendingIntent(R.id.widget_card_root, null)
        views.setOnClickPendingIntent(R.id.widget_card_preview, null)
        val foreground = QuickCardWidgetRenderer.foregroundColor(card)
        views.setTextViewText(R.id.widget_card_title, card.title.ifBlank { "快捷名片" })
        views.setTextColor(R.id.widget_card_title, foreground)
        views.setTextViewText(R.id.widget_card_note, card.note)
        views.setTextColor(R.id.widget_card_note, withAlpha(foreground, 230))
        views.setViewVisibility(R.id.widget_card_note, if (card.note.isBlank()) View.GONE else View.VISIBLE)
        val lightForeground = foreground == Color.WHITE
        views.setImageViewResource(
            R.id.widget_card_open,
            if (lightForeground) R.drawable.ic_widget_open_light else R.drawable.ic_widget_open_dark
        )
        views.setImageViewResource(
            R.id.widget_card_logo,
            if (lightForeground) R.drawable.logo_white else R.drawable.logo_black
        )
        views.setImageViewResource(
            R.id.widget_card_previous,
            if (lightForeground) R.drawable.ic_widget_previous_light else R.drawable.ic_widget_previous_dark
        )
        views.setImageViewResource(
            R.id.widget_card_next,
            if (lightForeground) R.drawable.ic_widget_next_light else R.drawable.ic_widget_next_dark
        )
        val options = manager.getAppWidgetOptions(widgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 250)
        views.setImageViewBitmap(
            R.id.widget_card_preview,
            QuickCardWidgetRenderer.render(
                card = card,
                widgetWidthDp = widthDp,
                widgetHeightDp = heightDp
            )
        )
        views.setOnClickPendingIntent(R.id.widget_card_header, open)
        views.setOnClickPendingIntent(R.id.widget_card_open, open)
        views.setOnClickPendingIntent(R.id.widget_card_qr, open)
        views.setOnClickPendingIntent(R.id.widget_card_logo, open)
        val multiple = cards.size > 1
        views.setViewVisibility(R.id.widget_card_pager, if (multiple) View.VISIBLE else View.GONE)
        if (multiple) {
            views.setOnClickPendingIntent(
                R.id.widget_card_previous,
                WidgetPendingIntents.widgetBroadcast(
                    context,
                    QuickCardWidgetProvider::class.java,
                    ACTION_PREVIOUS,
                    widgetId,
                    widgetId * 100 + 2
                )
            )
            views.setOnClickPendingIntent(
                R.id.widget_card_next,
                WidgetPendingIntents.widgetBroadcast(
                    context,
                    QuickCardWidgetProvider::class.java,
                    ACTION_NEXT,
                    widgetId,
                    widgetId * 100 + 3
                )
            )
        }
    }

    private fun pageFor(context: Context, widgetId: Int, lastIndex: Int, fallback: Int): Int {
        val prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        val page = if (prefs.contains(pageKey(widgetId))) {
            prefs.getInt(pageKey(widgetId), fallback)
        } else {
            fallback
        }
        return page.coerceIn(0, lastIndex.coerceAtLeast(0))
    }

    private fun setPage(context: Context, widgetId: Int, page: Int) {
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(pageKey(widgetId), page)
            .apply()
    }

    private fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, QuickCardWidgetProvider::class.java)
        manager.getAppWidgetIds(component).forEach { updateWidget(context, manager, it) }
    }

    private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    companion object {
        private const val STATE_PREFS = "widget_state"
        private const val ACTION_PREVIOUS = "com.lhtstudio.kigtts.app.widget.CARD_PREVIOUS"
        private const val ACTION_NEXT = "com.lhtstudio.kigtts.app.widget.CARD_NEXT"
        private fun pageKey(widgetId: Int) = "card_page_$widgetId"
    }
}
