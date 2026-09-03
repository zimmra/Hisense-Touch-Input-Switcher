package com.zimindustries.inputswitcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews

/**
 * "Input Switcher" home-screen widget: one rounded card with three equal-width buttons.
 * Each button fires a PendingIntent at [SwitchReceiver].
 */
class InputSwitcherWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = buildRemoteViews(context)
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context))
    }

    companion object {
        fun buildRemoteViews(context: Context): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_input_switcher).apply {
                setOnClickPendingIntent(R.id.button_conference_hub, SwitchReceiver.pendingIntent(context, Source.CONFERENCE_HUB))
                setOnClickPendingIntent(R.id.button_front_usbc, SwitchReceiver.pendingIntent(context, Source.FRONT_USBC))
                setOnClickPendingIntent(R.id.button_front_hdmi, SwitchReceiver.pendingIntent(context, Source.FRONT_HDMI))
            }

        /** Re-push the layout to every placed instance (e.g. after an app update). */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, InputSwitcherWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, buildRemoteViews(context))
        }

        /**
         * Ask the launcher to place the widget (Launcher3 shows a confirmation sheet).
         * Returns false if the launcher does not support pinning.
         */
        fun requestPin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            return manager.requestPinAppWidget(ComponentName(context, InputSwitcherWidget::class.java), null, null)
        }
    }
}
