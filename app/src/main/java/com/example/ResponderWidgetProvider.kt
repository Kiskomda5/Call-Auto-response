package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class ResponderWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val repository = StatusRepository(context)
        val isEnabled = repository.state.value.isEnabled

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, isEnabled)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "ACTION_TOGGLE_RESPONDER") {
            val repository = StatusRepository(context)
            val currentState = repository.state.value.isEnabled
            repository.setEnabled(!currentState)

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, ResponderWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, !currentState)
            }
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, isEnabled: Boolean) {
            val views = RemoteViews(context.packageName, R.layout.widget_responder)

            if (isEnabled) {
                views.setTextViewText(R.id.widget_text, "Désactiver")
                views.setInt(R.id.widget_root, "setBackgroundColor", android.graphics.Color.parseColor("#4CAF50"))
            } else {
                views.setTextViewText(R.id.widget_text, "Activer")
                views.setInt(R.id.widget_root, "setBackgroundColor", android.graphics.Color.parseColor("#2E2E2E"))
            }

            val intent = Intent(context, ResponderWidgetProvider::class.java)
            intent.action = "ACTION_TOGGLE_RESPONDER"
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
