package com.example.service

import java.util.Locale
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.model.PriceTick
import com.example.data.model.SymbolInfo
import com.example.data.model.formatPriceDynamic
import com.example.data.model.getDisplayDecimals
import com.example.data.repository.PriceMonitorManager
import java.util.Date

class PriceWidgetFiveProvider : AppWidgetProvider() {

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
                remove("widget_five_sym_$id")
            }
            apply()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.ACTION_REFRESH_WIDGET_FIVE" || intent.action == "com.example.ACTION_REFRESH_WIDGET") {
            val monitor = PriceMonitorManager.getInstance(context)
            monitor.startMonitoringLoop()
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, PriceWidgetFiveProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            updateAllWidgets(context, appWidgetManager, ids, monitor.priceState.value)
        }
    }

    companion object {
        fun updateWidgets(context: Context, priceState: Map<String, PriceTick>) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, PriceWidgetFiveProvider::class.java)
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
                val views = RemoteViews(context.packageName, R.layout.price_widget_five)

                // Bind click to open app
                val mainIntent = Intent(context, MainActivity::class.java)
                val mainPI = PendingIntent.getActivity(
                    context, widgetId + 60000, mainIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widget_root, mainPI)

                // Bind click to manual sync refresh
                val refreshIntent = Intent(context, PriceWidgetFiveProvider::class.java).apply {
                    action = "com.example.ACTION_REFRESH_WIDGET_FIVE"
                }
                val refreshPI = PendingIntent.getBroadcast(
                    context, widgetId + 70000, refreshIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPI)

                // Title show status/time
                views.setTextViewText(R.id.widget_title, "FinTrace Live 5 · $timeString")

                // Read selected symbols from config
                val symsString = prefs.getString("widget_five_sym_$widgetId", "XAU/USD,EUR/USD,GBP/USD,USD/JPY,AUD/USD") ?: "XAU/USD,EUR/USD,GBP/USD,USD/JPY,AUD/USD"
                val selectedSymbols = symsString.split(",").filter { it.isNotBlank() }

                val rows = listOf(R.id.row_1, R.id.row_2, R.id.row_3, R.id.row_4, R.id.row_5)
                val names = listOf(R.id.text_symbol_1_name, R.id.text_symbol_2_name, R.id.text_symbol_3_name, R.id.text_symbol_4_name, R.id.text_symbol_5_name)
                val prices = listOf(R.id.text_symbol_1_price, R.id.text_symbol_2_price, R.id.text_symbol_3_price, R.id.text_symbol_4_price, R.id.text_symbol_5_price)
                val changes = listOf(R.id.text_symbol_1_change, R.id.text_symbol_2_change, R.id.text_symbol_3_change, R.id.text_symbol_4_change, R.id.text_symbol_5_change)

                for (i in 0 until 5) {
                    if (i < selectedSymbols.size) {
                        val sym = selectedSymbols[i]
                        val tick = priceState[sym]
                        val info = SymbolInfo.find(sym)

                        views.setViewVisibility(rows[i], View.VISIBLE)
                        views.setTextViewText(names[i], sym)

                        val resolvedTick = tick ?: PriceTick(
                            symbol = sym,
                            price = info.defaultPrice,
                            bid = info.defaultPrice * 0.9996,
                            ask = info.defaultPrice * 1.0004,
                            history = listOf(info.defaultPrice),
                            openPrice = info.defaultPrice
                        )
                        views.setTextViewText(prices[i], resolvedTick.price.formatPriceDynamic(info.getDisplayDecimals()))
                        val sign = if (resolvedTick.change >= 0) "+" else ""
                        views.setTextViewText(changes[i], "$sign${String.format(Locale.US, "%.2f%%", resolvedTick.changePercent)}")
                        views.setTextColor(changes[i], if (resolvedTick.change >= 0) 0xFF69F0AE.toInt() else 0xFFFF5252.toInt())
                    } else {
                        views.setViewVisibility(rows[i], View.GONE)
                    }
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
