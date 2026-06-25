package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.repository.PriceMonitorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PriceTrackerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var monitorManager: PriceMonitorManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == Intent.ACTION_SCREEN_OFF) {
                Log.d("PriceTrackerService", "Screen OFF received. Muting active alert sounds and vibration.")
                PriceMonitorManager.getInstance(context).setScreenState(false)
                
                // Stop audio playback and vibration
                AlertSoundPlayer.stopPlayback()
                NotificationHelper.cancelVibration(context)
            } else if (action == Intent.ACTION_SCREEN_ON) {
                Log.d("PriceTrackerService", "Screen ON received. Restoring standard price trackers.")
                PriceMonitorManager.getInstance(context).setScreenState(true)
            } else if (action == "android.media.VOLUME_CHANGED_ACTION") {
                Log.d("PriceTrackerService", "Alert silencing volume gesture received. Muting active sounds.")
                AlertSoundPlayer.stopPlayback()
                NotificationHelper.cancelVibration(context)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        monitorManager = PriceMonitorManager.getInstance(this)

        // Reactively monitor screen interactive state and active alerts to dynamically lock/unlock CPU
        scope.launch {
            kotlinx.coroutines.flow.combine(
                monitorManager.isScreenOnFlow,
                monitorManager.hasActiveAlertsFlow
            ) { screenOn, alertsActive ->
                Pair(screenOn, alertsActive)
            }.collect {
                synchronizeWakeLockState()
            }
        }

        // Initialize screen state
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            // Initialize current power screen state in manager
            val isScreenInteractive = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH) pm.isInteractive else pm.isScreenOn
            monitorManager.setScreenState(isScreenInteractive)
        } catch (e: Exception) {
            Log.e("PriceTrackerService", "Failed to initialize screen state: ${e.message}")
        }

        // Register dynamic receiver for physical key silencing gestures and screen state alterations.
        // ContextCompat applies RECEIVER_NOT_EXPORTED on Android 14+ (targetSdk 36), which is
        // mandatory there — a flagless registerReceiver() throws SecurityException and crashes.
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction("android.media.VOLUME_CHANGED_ACTION")
            }
            ContextCompat.registerReceiver(
                this,
                screenOffReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.e("PriceTrackerService", "Failed to register screenReceiver: ${e.message}")
        }

        // Keep the foreground-service notification's monitored-symbol summary in sync.
        // Launched once here (NOT in onStartCommand, which can run repeatedly and would
        // otherwise leak a new collector on every service restart).
        scope.launch {
            monitorManager.activeSymbols.collect { active ->
                val fgsNotif = NotificationHelper.buildFgsNotification(this@PriceTrackerService, active)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(NotificationHelper.FGS_NOTIFICATION_ID, fgsNotif)
            }
        }

        Log.d("PriceTrackerService", "Foreground service created successfully")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground with FGS Notification (ID 1001)
        val activeSymbols = monitorManager.activeSymbols.value
        val notification = NotificationHelper.buildFgsNotification(this, activeSymbols)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.FGS_NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.FGS_NOTIFICATION_ID, notification)
        }

        monitorManager.startMonitoringLoop()

        return START_STICKY
    }

    // Synchronized because this runs on the scope's collector thread while onDestroy() (main
    // thread) can concurrently read/release wakeLock — without this, the null-check and the
    // !! access below are not atomic and a racing onDestroy() can null it out in between.
    @Synchronized
    private fun synchronizeWakeLockState() {
        val screenOn = monitorManager.getScreenState()
        val alertsActive = monitorManager.getHasActiveAlerts()
        
        // Dynamic adaptive CPU power scaling:
        // We only hold partial wake lock when screen is off AND active alerts exist.
        // If the screen is on (interactive state keeps system awake) or there are no active alerts,
        // we release the wake lock to allow the system to sleep completely and save up to 100% battery!
        val needsWakeLock = !screenOn && alertsActive
        
        if (needsWakeLock) {
            if (wakeLock == null) {
                try {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.example:PriceTrackerWakeLock").apply {
                        acquire()
                    }
                    Log.d("PriceTrackerService", "Partial WakeLock dynamically ACQUIRED (screen off, active alerts present).")
                } catch (e: Exception) {
                    Log.e("PriceTrackerService", "Failed to acquire dynamic WakeLock: ${e.message}")
                }
            } else if (!wakeLock!!.isHeld) {
                try {
                    wakeLock!!.acquire()
                    Log.d("PriceTrackerService", "Partial WakeLock dynamically ACQUIRED (re-held).")
                } catch (e: Exception) {
                    Log.e("PriceTrackerService", "Failed to acquire dynamic WakeLock: ${e.message}")
                }
            }
        } else {
            wakeLock?.let {
                if (it.isHeld) {
                    try {
                        it.release()
                        Log.d("PriceTrackerService", "Partial WakeLock dynamically RELEASED to save battery.")
                    } catch (e: Exception) {
                        Log.e("PriceTrackerService", "Failed to release dynamic WakeLock: ${e.message}")
                    }
                }
            }
            wakeLock = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @Synchronized
    private fun releaseWakeLockOnDestroy() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
            Log.d("PriceTrackerService", "WakeLock released successfully.")
        } catch (e: Exception) {
            Log.e("PriceTrackerService", "Failed to release WakeLock: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLockOnDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
            Log.e("PriceTrackerService", "Failed to unregister screenOffReceiver: ${e.message}")
        }
        monitorManager.stopMonitoring()
        scope.cancel()
        Log.d("PriceTrackerService", "Foreground service destroyed")
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, PriceTrackerService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PriceTrackerService::class.java)
            context.stopService(intent)
        }
    }
}
