package com.example.service

import java.util.Locale
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.model.PriceTick
import com.example.data.model.SymbolInfo
import com.example.data.model.formatPriceDynamic
import com.example.data.model.getDisplayDecimals
import com.example.data.repository.PriceMonitorManager
import java.util.Date

class PriceWidgetSingleProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val monitor = PriceMonitorManager.getInstance(context)
        updateAllWidgets(context, appWidgetManager, appWidgetIds, monitor.priceState.value)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = context.getSharedPreferences("fintrace_widgets", Context.MODE_PRIVATE)
        prefs.edit().apply {
            for (id in appWidgetIds) {
                remove("widget_single_sym_$id")
            }
            apply()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.ACTION_REFRESH_WIDGET_SINGLE" || intent.action == "com.example.ACTION_REFRESH_WIDGET") {
            val monitor = PriceMonitorManager.getInstance(context)
            monitor.startMonitoringLoop()
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, PriceWidgetSingleProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            updateAllWidgets(context, appWidgetManager, ids, monitor.priceState.value)
        }
    }

    companion object {
        fun updateWidgets(context: Context, priceState: Map<String, PriceTick>) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, PriceWidgetSingleProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            updateAllWidgets(context, appWidgetManager, ids, priceState)
        }

        private fun updateAllWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            priceState: Map<String, PriceTick>
        ) {
            val prefs = context.getSharedPreferences("fintrace_widgets", Context.MODE_PRIVATE)
            val timeString = DateFormat.getTimeFormat(context).format(Date())

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.price_widget_single)

                // Bind click to open app
                val mainIntent = Intent(context, MainActivity::class.java)
                val mainPI = PendingIntent.getActivity(
                    context, widgetId + 40000, mainIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widget_root, mainPI)

                // Bind click to manual sync refresh
                val refreshIntent = Intent(context, PriceWidgetSingleProvider::class.java).apply {
                    action = "com.example.ACTION_REFRESH_WIDGET_SINGLE"
                }
                val refreshPI = PendingIntent.getBroadcast(
                    context, widgetId + 50000, refreshIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPI)

                // Show status/time of sync
                views.setTextViewText(R.id.widget_title, "LIVE $timeString")

                // Read selected symbol from config
                val sym = prefs.getString("widget_single_sym_$widgetId", "XAU/USD") ?: "XAU/USD"
                val tick = priceState[sym]
                val info = SymbolInfo.find(sym)

                views.setTextViewText(R.id.text_single_name, sym)

                if (tick != null) {
                    views.setTextViewText(R.id.text_single_price, tick.price.formatPriceDynamic(info.getDisplayDecimals()))
                    val sign = if (tick.change >= 0) "+" else ""
                    views.setTextViewText(R.id.text_single_change, "$sign${String.format(Locale.US, "%.2f%%", tick.changePercent)}")
                    views.setTextColor(R.id.text_single_change, if (tick.change >= 0) 0xFF69F0AE.toInt() else 0xFFFF5252.toInt())
                } else {
                    views.setTextViewText(R.id.text_single_price, "...")
                    views.setTextViewText(R.id.text_single_change, "0.0%")
                    views.setTextColor(R.id.text_single_change, 0xFF9E9E9E.toInt())
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
