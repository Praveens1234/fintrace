package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.repository.PriceMonitorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.MY_PACKAGE_REPLACED") {
            Log.d("BootReceiver", "Reboot or update broadcast received: ${intent.action}")

            val monitor = PriceMonitorManager.getInstance(context)
            // goAsync() keeps the receiver alive past onReceive() so this DB read + service
            // start can complete; without it the process may be killed mid-coroutine.
            val pendingResult = goAsync()
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val autoStart = monitor.getSetting("auto_start_on_boot") ?: "true"
                    if (autoStart == "true") {
                        Log.d("BootReceiver", "Starting FinTrace price service on boot trigger...")
                        PriceTrackerService.start(context)
                    }
                    // Always schedule WorkManager periodic tasks on boot to ensure persistent execution
                    PriceWorker.schedulePeriodicWork(context)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Boot initialization failed: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
