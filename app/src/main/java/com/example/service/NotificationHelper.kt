package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.model.Alert
import com.example.data.repository.PriceMonitorManager
import com.example.data.model.PriceTick
import com.example.data.model.SymbolInfo
import com.example.data.model.formatPriceDynamic
import com.example.data.model.getDisplayDecimals
import java.util.Locale

object NotificationHelper {

    private const val FGS_CHANNEL_ID = "price_ticker_fgs"
    private const val TICKER_CHANNEL_ID = "price_ticker_live"
    private const val ALERT_DEFAULT_CHANNEL_ID = "price_alert_default"
    private const val ALERT_ALARM_CHANNEL_ID = "price_alert_alarm"
    private const val STATUS_CHANNEL_ID = "app_status_alert"
    private const val TRADE_CHANNEL_ID = "trade_events"

    const val FGS_NOTIFICATION_ID = 1001
    const val TICKER_NOTIFICATION_ID = 1002
    const val STATUS_NOTIFICATION_ID = 1003
    const val DEMO_NOTIFICATION_ID = 9001

    private var lastTickerUpdate = 0L
    private val tradeNotifCounter = java.util.concurrent.atomic.AtomicInteger(0)

    // Trade notifications used to derive their id from a timestamp modulo, which let two trades
    // firing within the same window collide and silently overwrite each other's notification.
    private fun nextTradeNotifId(): Int = 40000 + (tradeNotifCounter.getAndIncrement() % 10000)

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Foreground Service Channel
            val fgsChannel = NotificationChannel(
                FGS_CHANNEL_ID,
                "FinTrace Background Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps the background price tracker running sustainably"
                setShowBadge(false)
            }
            nm.createNotificationChannel(fgsChannel)

            // 2. Live Price Ticker Channel
            val tickerChannel = NotificationChannel(
                TICKER_CHANNEL_ID,
                "Live Price Ticker Shade",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live summaries of 1-5 selected symbols directly in your notification shade"
                setShowBadge(false)
            }
            nm.createNotificationChannel(tickerChannel)

            // 3. Default Price Alerts Channel
            val alertDefaultChannel = NotificationChannel(
                ALERT_DEFAULT_CHANNEL_ID,
                "Price Trigger Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Delivers standard heads-up banner notifications when target prices are crossed"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 80, 150)
            }
            nm.createNotificationChannel(alertDefaultChannel)

            // 4. Priority / Alarm Price Alerts Channel
            val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            val alertAlarmChannel = NotificationChannel(
                ALERT_ALARM_CHANNEL_ID,
                "Urgent Alarm and Call Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Delivers wake-screen alerts and persistent overlays for high-priority crossings"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 100, 400, 100, 600)
                setSound(alarmSoundUri, audioAttributes)
            }
            nm.createNotificationChannel(alertAlarmChannel)

            // 5. Connection Status System Alerts
            val statusChannel = NotificationChannel(
                STATUS_CHANNEL_ID,
                "App Connection Status Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts you if the websocket connection goes offline longer than selected limits"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 100, 50, 100)
            }
            nm.createNotificationChannel(statusChannel)

            // 6. Trade Event Alerts
            val tradeChannel = NotificationChannel(
                TRADE_CHANNEL_ID,
                "Trade Event Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when orders fill, stop-loss/take-profit hit, positions close or margin events occur"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            }
            nm.createNotificationChannel(tradeChannel)
        }
    }

    // ── TRADE EVENT NOTIFICATIONS ─────────────────────────────────────────
    fun fireTradeNotification(
        context: Context,
        title: String,
        body: String,
        soundMode: String,
        ttsLanguage: String
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("OPEN_TRADE_TAB", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val notifId = nextTradeNotifId()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, TRADE_CHANNEL_ID)
            .setContentTitle("FinTrace · $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText("TRADE")
            .setSmallIcon(android.R.drawable.ic_menu_sort_by_size)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(notifId, notification)
        if (soundMode != "Silent") triggerDeviceHaptic(context, "HIGH")
        AlertSoundPlayer.playTradeSound(context, "$title. $body", soundMode)
    }

    // ── FOREGROUND SERVICE PERSISTENT NOTIFICATION ────────────────────────
    fun buildFgsNotification(context: Context, activeSymbols: List<String>): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val trackerSummary = if (activeSymbols.isNotEmpty()) {
            "Monitoring: " + activeSymbols.take(5).joinToString(", ") + (if (activeSymbols.size > 5) "..." else "")
        } else {
            "FinTrace background service active (0 symbols)."
        }

        return NotificationCompat.Builder(context, FGS_CHANNEL_ID)
            .setContentTitle("FinTrace Active Monitoring")
            .setContentText(trackerSummary)
            .setSmallIcon(android.R.drawable.ic_notification_overlay)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    // ── LIVE TICKER NOTIFICATION SHADE (UPDATED ONCE EVERY 2 SECONDS) ───────
    fun updateTickerNotification(
        context: Context,
        priceState: Map<String, PriceTick>,
        activeSymbols: List<String>
    ) {
        val now = System.currentTimeMillis()
        if (now - lastTickerUpdate < 2000L) return
        lastTickerUpdate = now

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val selectedSymbols = activeSymbols.take(5)
        if (selectedSymbols.isEmpty()) {
            nm.cancel(TICKER_NOTIFICATION_ID)
            return
        }

        val lines = selectedSymbols.map { sym ->
            val info = SymbolInfo.find(sym)
            val tick = priceState[sym] ?: PriceTick(
                symbol = sym,
                price = info.defaultPrice,
                bid = info.defaultPrice * 0.9996,
                ask = info.defaultPrice * 1.0004,
                history = listOf(info.defaultPrice),
                openPrice = info.defaultPrice
            )
            val priceStr = tick.price.formatPriceDynamic(info.getDisplayDecimals())
            val dir = if (tick.change >= 0) "▲" else "▼"
            val pct = String.format(Locale.US, "%.2f%%", kotlin.math.abs(tick.changePercent))
            "$sym ($dir $priceStr · $pct)"
        }

        val textSummary = lines.joinToString("  |  ")

        val notification = NotificationCompat.Builder(context, TICKER_CHANNEL_ID)
            .setContentTitle("FinTrace Live Tickers")
            .setContentText(textSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(TICKER_NOTIFICATION_ID, notification)
    }

    // ── FIRING ACTUAL ALERTS ────────────────────────────────────────────────
    fun fireAlertNotification(context: Context, alert: Alert, price: Double) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("ALERT_TRIGGERED_ID", alert.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.id + 10000,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Broadcast intent for dismissed action / Off button clicking
        val actionIntent = Intent(context, AlertDismissReceiver::class.java).apply {
            action = "com.example.ACTION_DISMISS_ALERTS"
            putExtra("NOTIFICATION_ID", alert.id + 10000)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            alert.id + 30000,
            actionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val info = SymbolInfo.find(alert.symbol)
        val formattedPrice = price.formatPriceDynamic(info.getDisplayDecimals())
        val heading = "FinTrace Trigger: ${alert.symbol} ${alert.condition.replace("_", " ")}"
        val content = "${alert.title}: crossed target price at $formattedPrice"

        val priorityLevel = when (alert.priority) {
            "LOW" -> NotificationCompat.PRIORITY_LOW
            "MEDIUM" -> NotificationCompat.PRIORITY_DEFAULT
            "HIGH" -> NotificationCompat.PRIORITY_HIGH
            "CRITICAL" -> NotificationCompat.PRIORITY_MAX
            else -> NotificationCompat.PRIORITY_HIGH
        }

        val channelId = if (alert.priority == "CRITICAL" || alert.priority == "HIGH") {
            ALERT_ALARM_CHANNEL_ID
        } else {
            ALERT_DEFAULT_CHANNEL_ID
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(heading)
            .setContentText(content)
            .setSubText("ALERT")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(priorityLevel)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Off", dismissPendingIntent)
            .apply {
                if (alert.priority == "CRITICAL" || alert.priority == "HIGH") {
                    setCategory(NotificationCompat.CATEGORY_ALARM)
                    setFullScreenIntent(pendingIntent, true)
                }
            }
            .build()

        nm.notify(alert.id + 10000, notification)
        triggerDeviceHaptic(context, alert.priority)

        // Play user-selected device tone with voice audio
        val ttsLang = PriceMonitorManager.getInstance(context).getTtsLanguage()
        val voiceMsg = AlertSoundPlayer.buildAlertText(alert.symbol, formattedPrice, ttsLang)
        AlertSoundPlayer.playAlertSound(context, voiceMsg, alert.priority)
    }

    // ── DEMO ALERT SYSTEM DELIVERIES ─────────────────────────────────────
    fun fireDemoAlertNotification(context: Context, type: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            9001,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Broadcast intent for dismissed action / Off button clicking for Demo
        val demoActionIntent = Intent(context, AlertDismissReceiver::class.java).apply {
            action = "com.example.ACTION_DISMISS_ALERTS"
            putExtra("NOTIFICATION_ID", DEMO_NOTIFICATION_ID)
        }
        val demoDismissPendingIntent = PendingIntent.getBroadcast(
            context,
            9005,
            demoActionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = "DEMO: XAU/USD Crossed 2320.00"
        val message = "Your alert delivery system is verified. Test accent color purple."

        when (type) {
            "DEFAULT_NOTIFICATION" -> {
                val notification = NotificationCompat.Builder(context, ALERT_DEFAULT_CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSubText("DEMO")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setDeleteIntent(demoDismissPendingIntent)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Off", demoDismissPendingIntent)
                    .build()
                nm.notify(DEMO_NOTIFICATION_ID, notification)
                triggerDeviceHaptic(context, "HIGH")

                val demoLang = PriceMonitorManager.getInstance(context).getTtsLanguage()
                AlertSoundPlayer.playAlertSound(
                    context,
                    AlertSoundPlayer.buildAlertText("XAU/USD", "2,320.00", demoLang),
                    "HIGH"
                )
            }
            "ALARM", "VIRTUAL_CALL" -> {
                // Urgent Delivery
                val notification = NotificationCompat.Builder(context, ALERT_ALARM_CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSubText("DEMO ALARM")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(pendingIntent, true)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setDeleteIntent(demoDismissPendingIntent)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Off", demoDismissPendingIntent)
                    .build()
                nm.notify(DEMO_NOTIFICATION_ID, notification)
                triggerDeviceHaptic(context, "CRITICAL")

                val demoLang2 = PriceMonitorManager.getInstance(context).getTtsLanguage()
                AlertSoundPlayer.playAlertSound(
                    context,
                    AlertSoundPlayer.buildAlertText("XAU/USD", "2,320.00", demoLang2),
                    "CRITICAL"
                )
            }
        }
    }

    private fun triggerDeviceHaptic(context: Context, priority: String) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (priority) {
                    "LOW" -> VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                    "MEDIUM" -> VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
                    "HIGH" -> VibrationEffect.createWaveform(longArrayOf(0, 150, 80, 150), intArrayOf(0, 200, 0, 200), -1)
                    "CRITICAL" -> VibrationEffect.createWaveform(longArrayOf(0, 450, 100, 450, 100, 600), intArrayOf(0, 255, 0, 255, 0, 255), -1)
                    else -> VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                when (priority) {
                    "LOW" -> vibrator.vibrate(50)
                    "MEDIUM" -> vibrator.vibrate(150)
                    "HIGH" -> vibrator.vibrate(longArrayOf(0, 150, 80, 150), -1)
                    "CRITICAL" -> vibrator.vibrate(longArrayOf(0, 450, 100, 450, 100, 600), -1)
                    else -> vibrator.vibrate(100)
                }
            }
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Vibration failed: ${e.message}")
        }
    }

    fun cancelVibration(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.cancel()
            Log.d("NotificationHelper", "Vibration canceled successfully")
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed to cancel vibration: ${e.message}")
        }
    }
}
