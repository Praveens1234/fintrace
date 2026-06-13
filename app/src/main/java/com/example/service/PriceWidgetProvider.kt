package com.example.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.model.PriceTick
import com.example.data.model.SymbolInfo
import com.example.data.model.formatPriceDynamic
import com.example.data.model.getDisplayDecimals
import com.example.data.repository.PriceMonitorManager
import java.util.Locale

class PriceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val monitor = PriceMonitorManager.getInstance(context)
        val prices = monitor.priceState.value
        updateAllWidgets(context, appWidgetManager, appWidgetIds, prices)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.ACTION_REFRESH_WIDGET") {
            val monitor = PriceMonitorManager.getInstance(context)
            monitor.startMonitoringLoop()
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, PriceWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            updateAllWidgets(context, appWidgetManager, ids, monitor.priceState.value)
        }
    }

    companion object {
        fun updateWidgets(context: Context, priceState: Map<String, PriceTick>) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, PriceWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            updateAllWidgets(context, appWidgetManager, ids, priceState)
        }

        private fun updateAllWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            priceState: Map<String, PriceTick>
        ) {
            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.price_widget)

                // Bind click to open app
                val mainIntent = Intent(context, MainActivity::class.java)
                val mainPI = PendingIntent.getActivity(
                    context, 0, mainIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widget_root, mainPI)

                // Bind click to manual sync refresh
                val refreshIntent = Intent(context, PriceWidgetProvider::class.java).apply {
                    action = "com.example.ACTION_REFRESH_WIDGET"
                }
                val refreshPI = PendingIntent.getBroadcast(
                    context, 1, refreshIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPI)

                // Select top 3 symbols
                val activeSymbols = priceState.keys.toList().take(3)

                // Row 1
                if (activeSymbols.isNotEmpty()) {
                    val sym = activeSymbols[0]
                    val tick = priceState[sym]
                    val info = SymbolInfo.find(sym)
                    
                    views.setViewVisibility(R.id.row_symbol_1, View.VISIBLE)
                    views.setTextViewText(R.id.text_symbol_1_name, sym)
                    if (tick != null) {
                        views.setTextViewText(R.id.text_symbol_1_price, tick.price.formatPriceDynamic(info.getDisplayDecimals()))
                        val sign = if (tick.change >= 0) "+" else ""
                        views.setTextViewText(R.id.text_symbol_1_change, "$sign${String.format(Locale.US, "%.2f%%", tick.changePercent)}")
                        views.setTextColor(R.id.text_symbol_1_change, if (tick.change >= 0) 0xFF69F0AE.toInt() else 0xFFFF5252.toInt())
                    }
                } else {
                    views.setViewVisibility(R.id.row_symbol_1, View.GONE)
                }

                // Row 2
                if (activeSymbols.size > 1) {
                    val sym = activeSymbols[1]
                    val tick = priceState[sym]
                    val info = SymbolInfo.find(sym)
                    
                    views.setViewVisibility(R.id.row_symbol_2, View.VISIBLE)
                    views.setTextViewText(R.id.text_symbol_2_name, sym)
                    if (tick != null) {
                        views.setTextViewText(R.id.text_symbol_2_price, tick.price.formatPriceDynamic(info.getDisplayDecimals()))
                        val sign = if (tick.change >= 0) "+" else ""
                        views.setTextViewText(R.id.text_symbol_2_change, "$sign${String.format(Locale.US, "%.2f%%", tick.changePercent)}")
                        views.setTextColor(R.id.text_symbol_2_change, if (tick.change >= 0) 0xFF69F0AE.toInt() else 0xFFFF5252.toInt())
                    }
                } else {
                    views.setViewVisibility(R.id.row_symbol_2, View.GONE)
                }

                // Row 3
                if (activeSymbols.size > 2) {
                    val sym = activeSymbols[2]
                    val tick = priceState[sym]
                    val info = SymbolInfo.find(sym)
                    
                    views.setViewVisibility(R.id.row_symbol_3, View.VISIBLE)
                    views.setTextViewText(R.id.text_symbol_3_name, sym)
                    if (tick != null) {
                        views.setTextViewText(R.id.text_symbol_3_price, tick.price.formatPriceDynamic(info.getDisplayDecimals()))
                        val sign = if (tick.change >= 0) "+" else ""
                        views.setTextViewText(R.id.text_symbol_3_change, "$sign${String.format(Locale.US, "%.2f%%", tick.changePercent)}")
                        views.setTextColor(R.id.text_symbol_3_change, if (tick.change >= 0) 0xFF69F0AE.toInt() else 0xFFFF5252.toInt())
                    }
                } else {
                    views.setViewVisibility(R.id.row_symbol_3, View.GONE)
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
