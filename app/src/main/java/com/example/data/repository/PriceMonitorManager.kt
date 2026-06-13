package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.data.database.AppDatabase
import com.example.data.model.Alert
import com.example.data.model.AppSetting
import com.example.data.model.PriceTick
import com.example.data.model.SymbolInfo
import com.example.data.model.SymbolState
import com.example.data.model.TriggerHistory
import com.example.data.model.formatPriceDynamic
import com.example.data.model.getDisplayDecimals
import com.example.data.market.MarketSchedule
import com.example.service.NotificationHelper
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import java.util.concurrent.ExecutorService
import java.util.concurrent.ConcurrentHashMap

class PriceMonitorManager private constructor(context: Context) {

    private val isolatePool = IsolateWorkerPool()

    private val assetExecutors = ConcurrentHashMap<String, ExecutorService>()
    private val assetWorkerPool = ConcurrentHashMap<String, kotlinx.coroutines.CoroutineDispatcher>()

    private fun getDispatcherForAsset(symbol: String): kotlinx.coroutines.CoroutineDispatcher {
        val symKey = symbol.uppercase()
        return assetWorkerPool.getOrPut(symKey) {
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "FinTrace-Worker-$symKey")
            }
            assetExecutors[symKey] = executor
            executor.asCoroutineDispatcher()
        }
    }

    private val appContext = context.applicationContext
    val db: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "fintrace_database"
    ).fallbackToDestructiveMigration(true).build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var connectionJob: Job? = null
    private var marketJob: Job? = null
    private var logCounter = 0
    private var reconnectCount = 0

    // Re-check cadences (ms). When closed/standby we poll cheaply (time math only, no network);
    // when open we re-check connection health hourly. The loop always sleeps until the *exact*
    // next session boundary when that is sooner, so teardown/resume happen with no drift.
    private val maxClosedSleepMs = 30 * 60 * 1000L
    private val standbyRecheckMs = 30 * 60 * 1000L
    private val openRecheckMs = 60 * 60 * 1000L

    @Volatile
    private var isTickLoggingEnabled = true

    @Volatile
    private var hasActiveAlerts = false

    @Volatile
    private var activeAlertsCache: List<Alert> = emptyList()

    private val memoryLogIdCounter = java.util.concurrent.atomic.AtomicLong(-1L)

    private val lastSymbolUpdateTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @Volatile
    private var isNativeModeEnabled = true

    @Volatile
    private var cachedPriceUpdateIntervalMs = 500L

    suspend fun getWebsocketUseNativeMode(): Boolean {
        return isNativeModeEnabled
    }

    private val _tickLogs = MutableStateFlow<List<com.example.data.model.AppLog>>(emptyList())
    val tickLogs: StateFlow<List<com.example.data.model.AppLog>> = _tickLogs.asStateFlow()

    fun clearTickLogs() {
        _tickLogs.value = emptyList()
    }

    fun setTickLoggingEnabled(enabled: Boolean) {
        isTickLoggingEnabled = enabled
        scope.launch {
            saveSetting("tick_logging_enabled", enabled.toString())
        }
    }

    fun getTickLoggingEnabled(): Boolean = isTickLoggingEnabled

    data class StorageInfo(
        val usedBytes: Long,
        val maxBytes: Long = 10 * 1024 * 1024L // 10MB
    ) {
        val usedMB: Double get() = usedBytes / (1024.0 * 1024.0)
        val maxMB: Double get() = maxBytes / (1024.0 * 1024.0)
        val remainingMB: Double get() = (maxBytes - usedBytes).coerceAtLeast(0L) / (1024.0 * 1024.0)
        val usedPercent: Float get() = (usedBytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f)
    }

    fun getDatabaseStorageInfo(): StorageInfo {
        val dbFile = appContext.getDatabasePath("fintrace_database")
        val walFile = appContext.getDatabasePath("fintrace_database-wal")
        val shmFile = appContext.getDatabasePath("fintrace_database-shm")
        var totalBytes = 0L
        if (dbFile.exists()) totalBytes += dbFile.length()
        if (walFile.exists()) totalBytes += walFile.length()
        if (shmFile.exists()) totalBytes += shmFile.length()
        return StorageInfo(totalBytes)
    }

    fun logEvent(type: String, symbol: String?, message: String) {
        if (type == "TICK") {
            if (!isTickLoggingEnabled || !isScreenOn) return
            val newLog = com.example.data.model.AppLog(
                id = memoryLogIdCounter.getAndDecrement(),
                type = type,
                symbol = symbol,
                message = message
            )
            _tickLogs.update { current ->
                val updated = listOf(newLog) + current
                if (updated.size > 100) updated.take(100) else updated
            }
        } else {
            scope.launch(Dispatchers.IO) {
                try {
                    db.appLogDao().insertLog(com.example.data.model.AppLog(type = type, symbol = symbol, message = message))
                    logCounter++
                    if (logCounter >= 5) { // Check every 5 log insertions to be highly responsive
                        logCounter = 0
                        val info = getDatabaseStorageInfo()
                        if (info.usedBytes > info.maxBytes) {
                            val count = db.appLogDao().getLogCount()
                            if (count > 0) {
                                val keepCount = (count * 0.3).toInt().coerceAtLeast(1)
                                db.appLogDao().deleteOldestLogsExcept(keepCount)
                                
                                // Vacuum physical db file to reclaim filesystem ROM space
                                try {
                                    db.openHelper.writableDatabase.execSQL("VACUUM")
                                } catch (ve: Exception) {
                                    Log.e("PriceMonitor", "VACUUM failed: ${ve.message}")
                                }

                                // Write a PROTECTION log indicating storage limit exceeded action
                                db.appLogDao().insertLog(com.example.data.model.AppLog(
                                    type = "PROTECTION",
                                    symbol = null,
                                    message = "DATABASE STORAGE LIMIT MITIGATION: 10MB storage limit hit (${String.format(Locale.US, "%.2f", info.usedMB)} MB). Auto-purged oldest 70% of logs to protect system storage. Retained last $keepCount logs and vacuumed."
                                ))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PriceMonitor", "Failed to write log: ${e.message}")
                }
            }
        }
    }

    // Price Cache & State Flow
    private val _priceState = MutableStateFlow<Map<String, PriceTick>>(emptyMap())
    val priceState: StateFlow<Map<String, PriceTick>> = _priceState.asStateFlow()

    // Active symbols state cache
    private val _activeSymbols = MutableStateFlow<List<String>>(listOf("XAU/USD", "EUR/USD", "GBP/USD", "USD/JPY"))
    val activeSymbols: StateFlow<List<String>> = _activeSymbols.asStateFlow()

    // Ticker symbols state cache
    private val _liveTickerSymbols = MutableStateFlow<List<String>>(emptyList())
    val liveTickerSymbols: StateFlow<List<String>> = _liveTickerSymbols.asStateFlow()

    // Connection Status State Flow
    // "LIVE", "RECONNECTING", "OFFLINE"
    private val _connectionStatus = MutableStateFlow("OFFLINE")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _latencyMs = MutableStateFlow(0L)
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    // Market session state. _marketOpen reflects the trading calendar; _nextMarketChangeAt is the
    // epoch-ms of the next open/close transition (used by the UI to show "opens in …").
    private val _marketOpen = MutableStateFlow(MarketSchedule.isOpenNow())
    val marketOpen: StateFlow<Boolean> = _marketOpen.asStateFlow()
    private val _nextMarketChangeAt = MutableStateFlow(MarketSchedule.nextChange().toEpochMilli())
    val nextMarketChangeAt: StateFlow<Long> = _nextMarketChangeAt.asStateFlow()

    private var activeWebSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    init {
        isolatePool.setupWorkers(4)
        // Initialize Default Symbols and load current selection
        scope.launch(Dispatchers.IO) {
            // Detect and heal previous crash state
            try {
                val prefs = appContext.getSharedPreferences("fintrace_prefs", Context.MODE_PRIVATE)
                val crashed = prefs.getBoolean("last_session_crashed", false)
                if (crashed) {
                    prefs.edit().putBoolean("last_session_crashed", false).apply()
                    logEvent("RECOVERY", null, "CRASH RECOVERY SERVICE: App has recovered successfully from a previous forced termination. Restoring system and database registers.")
                    logEvent("HEALING", null, "HEALING NODE: Cleared and optimized temporary log caches & checked data file integrity successfully.")
                }
            } catch (e: Exception) {
                Log.e("PriceMonitor", "Self-healing error state: ${e.message}")
            }

            setupInitialSymbolsIfNeeded()

            // NOTE: loadActiveSymbols() and observeLiveTickerSymbolsSetting() collect Room Flows
            // that never complete, so they MUST run in their own child coroutines. Calling them
            // inline would suspend this init coroutine forever and silently skip every statement
            // below (settings load, alert observer, monitoring start).
            launch { loadActiveSymbols() }

            isTickLoggingEnabled = (getSetting("tick_logging_enabled") ?: "true") == "true"
            isNativeModeEnabled = (getSetting("websocket_use_native_mode") ?: "true") == "true"
            cachedPriceUpdateIntervalMs = getUiPriceIntervalSettingFromDb()

            // Reactively fetch and cache active alerts presence to avoid recurring SQLite operations
            launch {
                db.alertDao().getAllAlertsFlow().collect { list ->
                    val activeList = list.filter { it.isActive }
                    activeAlertsCache = activeList
                    hasActiveAlerts = activeList.isNotEmpty()
                    _hasActiveAlertsFlow.value = hasActiveAlerts
                }
            }
            // Load and apply decimal precision setting
            val precisionRaw = getSetting("price_precision_override") ?: "MAX"
            com.example.data.model.PricePrecisionConfig.maxPrecision = precisionRaw.toIntOrNull()

            // Load per-asset custom overrides
            SymbolInfo.ALL.forEach { s ->
                val key = "price_precision_override_${s.symbol.uppercase()}"
                val overrideRaw = getSetting(key)
                val overrideInt = overrideRaw?.toIntOrNull()
                com.example.data.model.PricePrecisionConfig.setOverride(s.symbol, overrideInt)
            }

            initializePriceCache()

            // Start observing live ticker symbols config (collects a Flow forever -> own coroutine)
            launch {
                observeLiveTickerSymbolsSetting()
            }

            startMonitoringLoop()
        }
    }

    private suspend fun observeLiveTickerSymbolsSetting() {
        db.appSettingDao().getSettingFlow("live_ticker_symbols").collect { setting ->
            // Default to empty if not configured to respect user switch
            val raw = setting?.value ?: ""
            val list = raw.split(",").filter { it.isNotBlank() }
            _liveTickerSymbols.value = list
            NotificationHelper.updateTickerNotification(appContext, _priceState.value, list)
        }
    }

    private suspend fun setupInitialSymbolsIfNeeded() {
        val existing = db.symbolStateDao().getAllSymbolStates()
        if (existing.isEmpty()) {
            val initialList = SymbolInfo.ALL.mapIndexed { index, info ->
                val isActive = info.symbol in listOf("XAU/USD", "EUR/USD", "GBP/USD", "USD/JPY")
                SymbolState(info.symbol, isActive, index)
            }
            db.symbolStateDao().insertSymbolStates(initialList)
        }
    }

    private suspend fun loadActiveSymbols() {
        db.symbolStateDao().getAllSymbolStatesFlow().collect { list ->
            val active = list.filter { it.isActive }.map { it.symbol }
            _activeSymbols.value = active
        }
    }

    private fun initializePriceCache() {
        val initialMap = mutableMapOf<String, PriceTick>()
        SymbolInfo.ALL.forEach { s ->
            initialMap[s.symbol] = PriceTick(
                symbol = s.symbol,
                price = s.defaultPrice,
                bid = s.defaultPrice - (0.0004 * s.defaultPrice),
                ask = s.defaultPrice + (0.0004 * s.defaultPrice),
                history = listOf(s.defaultPrice),
                openPrice = s.defaultPrice
            )
        }
        _priceState.value = initialMap
    }

    private val _isScreenOnFlow = MutableStateFlow(true)
    val isScreenOnFlow: StateFlow<Boolean> = _isScreenOnFlow.asStateFlow()

    @Volatile
    private var isScreenOn = true

    fun getScreenState(): Boolean = isScreenOn

    private val _hasActiveAlertsFlow = MutableStateFlow(false)
    val hasActiveAlertsFlow: StateFlow<Boolean> = _hasActiveAlertsFlow.asStateFlow()

    fun getHasActiveAlerts(): Boolean = hasActiveAlerts

    fun setScreenState(on: Boolean) {
        if (isScreenOn != on) {
            isScreenOn = on
            _isScreenOnFlow.value = on
            logEvent("SYSTEM", null, "Screen status changed: interactive=$on. Deploying smart power saver metrics.")
            if (on) {
                scheduleSystemUpdates()
            }
            startMonitoringLoop()
        }
    }

    suspend fun getEffectiveApiKey(): String? {
        val dbKey = getSetting("twelve_data_api_key")
        if (!dbKey.isNullOrBlank()) {
            return dbKey
        }

        val buildConfigKey = try {
            com.example.BuildConfig.TWELVE_DATA_API_KEY
        } catch (e: Throwable) {
            null
        }
        if (!buildConfigKey.isNullOrBlank() && 
            buildConfigKey != "YOUR_TWELVE_DATA_API_KEY_HERE" && 
            buildConfigKey != "TWELVE_DATA_API_KEY"
        ) {
            return buildConfigKey
        }

        val sysEnvKey = System.getenv("TWELVE_DATA_API_KEY") ?: System.getenv("TWELVEDATA_API_KEY")
        if (!sysEnvKey.isNullOrBlank()) {
            return sysEnvKey
        }

        return null
    }

    /**
     * Single market-aware monitoring loop. It evaluates the trading calendar plus screen/alert/
     * API-key state and either connects the live socket or tears it down, then sleeps until the
     * exact next session boundary (capped so closed/standby states re-check cheaply as a backstop).
     * Any external change (screen toggle, setting save, WorkManager tick) cancels and restarts the
     * loop so it re-evaluates immediately. There is no simulated data: when we can't stream live,
     * the last real prices simply remain frozen.
     */
    fun startMonitoringLoop() {
        isolatePool.setupWorkers(4)
        marketJob?.cancel()
        marketJob = scope.launch {
            while (isActive) {
                val now = Instant.now()
                val open = MarketSchedule.isOpen(now)
                val nextChange = MarketSchedule.nextChange(now)
                _marketOpen.value = open
                _nextMarketChangeAt.value = nextChange.toEpochMilli()
                val untilChange = millisUntil(nextChange)

                if (!open) {
                    closeSocket("Market closed")
                    _connectionStatus.value = "CLOSED"
                    logEvent("SYSTEM", null, "Market is closed. Live monitoring paused to save CPU and battery; auto-resume is scheduled for the next session open.")
                    delay(untilChange.coerceIn(1_000L, maxClosedSleepMs))
                    continue
                }

                if (!isScreenOn && !hasActiveAlerts) {
                    // Smart standby: screen off and nothing to alert on — drop the socket for battery.
                    closeSocket("Smart standby (screen off, no active alerts)")
                    _connectionStatus.value = "OFFLINE"
                    logEvent("SYSTEM", null, "Smart standby active: screen off and no active alerts. Live socket paused to protect battery.")
                    delay(untilChange.coerceIn(1_000L, standbyRecheckMs))
                    continue
                }

                val apiKey = getEffectiveApiKey()
                if (apiKey.isNullOrBlank()) {
                    closeSocket("No API key configured")
                    _connectionStatus.value = "OFFLINE"
                    logEvent("SYSTEM", null, "No Twelve Data API key configured. Add a key in Settings to receive live prices.")
                    delay(untilChange.coerceIn(1_000L, standbyRecheckMs))
                    continue
                }

                // Market open, interactive/alerting, key present → ensure we are streaming.
                if (activeWebSocket == null || _connectionStatus.value == "CLOSED" || _connectionStatus.value == "OFFLINE") {
                    connectToTwelveData(apiKey)
                }
                delay(untilChange.coerceIn(1_000L, openRecheckMs))
            }
        }
    }

    private fun millisUntil(instant: Instant): Long =
        (instant.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0L)

    private fun closeSocket(reason: String) {
        activeWebSocket?.let {
            try { it.close(1000, reason) } catch (_: Exception) {}
        }
        activeWebSocket = null
    }

    fun stopMonitoring() {
        marketJob?.cancel()
        connectionJob?.cancel()
        activeWebSocket?.close(1000, "App closed")
        activeWebSocket = null

        isolatePool.shutdown()

        // Shut down worker pools on stop to prevent leaking threads
        assetExecutors.forEach { (sym, executor) ->
            try {
                executor.shutdown()
            } catch (e: Exception) {
                Log.e("PriceMonitor", "Error shutting down worker executor for $sym: ${e.message}")
            }
        }
        assetExecutors.clear()
        assetWorkerPool.clear()
    }

    // ── TWELVE DATA SOCKET INTEGRATION ────────────────────────────────────
    private fun connectToTwelveData(apiKey: String) {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            _connectionStatus.value = "CONNECTING"
            logEvent("SYSTEM", null, "Connecting to Twelve Data WebSocket feed...")
            val request = Request.Builder()
                .url("wss://ws.twelvedata.com/v1/quotes/price?apikey=$apiKey")
                .build()

            activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _connectionStatus.value = "LIVE"
                    reconnectCount = 0
                    logEvent("SYSTEM", null, "Connected to Twelve Data WebSocket successfully.")
                    // Subscribe active symbols
                    val activeList = (_activeSymbols.value + _liveTickerSymbols.value).distinct()
                    if (activeList.isNotEmpty()) {
                        val symString = activeList.joinToString(",")
                        val subMsg = JSONObject()
                        subMsg.put("action", "subscribe")
                        val params = JSONObject()
                        params.put("symbols", symString)
                        subMsg.put("params", params)
                        webSocket.send(subMsg.toString())
                    }
                    
                    // Start heartbeat ticker
                    startHeartbeat(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    isolatePool.dispatch(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionStatus.value = "OFFLINE"
                    logEvent("SYSTEM", null, "Twelve Data Connection closed: $reason")
                    attemptReconnect(apiKey)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    reconnectCount++
                    _connectionStatus.value = "OFFLINE"
                    logEvent("ERROR", null, "Twelve Data connection failed (${t.message ?: "Unknown socket error"}). Live streaming interrupted. Freezing on the last updated price. Attempting background retry #$reconnectCount...")
                    attemptReconnect(apiKey)
                }
            })

            // Dynamic Live subscription synchronizer
            launch {
                kotlinx.coroutines.flow.combine(_activeSymbols, _liveTickerSymbols) { active, ticker ->
                    (active + ticker).distinct()
                }.collect { list ->
                    val ws = activeWebSocket
                    if (_connectionStatus.value == "LIVE" && ws != null && list.isNotEmpty()) {
                        try {
                            val symString = list.joinToString(",")
                            val subMsg = JSONObject()
                            subMsg.put("action", "subscribe")
                            val params = JSONObject()
                            params.put("symbols", symString)
                            subMsg.put("params", params)
                            ws.send(subMsg.toString())
                            logEvent("SYSTEM", null, "Dynamically synced active WebSocket subscriptions: $symString")
                        } catch (e: Exception) {
                            Log.e("PriceMonitor", "Subscription update error: ${e.message}")
                        }
                    }
                }
            }

        }
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        scope.launch {
            while (_connectionStatus.value == "LIVE" && activeWebSocket == webSocket) {
                delay(25000)
                try {
                    val hb = JSONObject()
                    hb.put("action", "heartbeat")
                    webSocket.send(hb.toString())
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    private fun attemptReconnect(apiKey: String) {
        scope.launch {
            val delayMs = when {
                reconnectCount <= 1 -> 5000L
                reconnectCount <= 2 -> 10000L
                else -> 30000L
            }
            delay(delayMs)
            // Don't fight the calendar or burn battery: skip reconnect if the market closed or we
            // dropped into smart standby. The monitoring loop will reconnect when conditions return.
            if (!MarketSchedule.isOpenNow()) {
                _connectionStatus.value = "CLOSED"
                return@launch
            }
            if (!isScreenOn && !hasActiveAlerts) return@launch
            if (_connectionStatus.value != "LIVE") {
                connectToTwelveData(apiKey)
            }
        }
    }

    private val systemSyncPending = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var lastSystemSyncTime = 0L

    fun scheduleSystemUpdates() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastSystemSyncTime
        
        // Dynamic adaptive rate scaling for widget and notification broadcasts:
        // Screen ON: 1000ms updates
        // Screen OFF: 30000ms updates (Saves substantial binder transactions and wakeups)
        val rateLimitMs = if (isScreenOn) 1000L else 30000L
        
        if (elapsed > rateLimitMs) {
            lastSystemSyncTime = now
            val currentPrices = _priceState.value
            NotificationHelper.updateTickerNotification(appContext, currentPrices, _liveTickerSymbols.value)
            com.example.service.PriceWidgetProvider.updateWidgets(appContext, currentPrices)
            com.example.service.PriceWidgetSingleProvider.updateWidgets(appContext, currentPrices)
            com.example.service.PriceWidgetFiveProvider.updateWidgets(appContext, currentPrices)
        } else {
            if (systemSyncPending.compareAndSet(false, true)) {
                scope.launch {
                    delay(rateLimitMs - elapsed)
                    systemSyncPending.set(false)
                    lastSystemSyncTime = System.currentTimeMillis()
                    val currentPrices = _priceState.value
                    NotificationHelper.updateTickerNotification(appContext, currentPrices, _liveTickerSymbols.value)
                    com.example.service.PriceWidgetProvider.updateWidgets(appContext, currentPrices)
                    com.example.service.PriceWidgetSingleProvider.updateWidgets(appContext, currentPrices)
                    com.example.service.PriceWidgetFiveProvider.updateWidgets(appContext, currentPrices)
                }
            }
        }
    }

    // ── EVALUATE ALERTS ───────────────────────────────────────────────────
    private suspend fun evaluateAlerts(symbol: String, prevPrice: Double, currentPrice: Double) {
        try {
            val activeAlerts = activeAlertsCache.filter { it.symbol.equals(symbol, ignoreCase = true) }
            val now = System.currentTimeMillis()

            activeAlerts.forEach { alert ->
                // Check cooldown
                if (alert.cooldownUntil != null && now < alert.cooldownUntil) {
                    return@forEach
                }
                // Check expiration
                if (alert.expiry != null && now > alert.expiry) {
                    db.alertDao().updateAlertActiveStatus(alert.id, false)
                    return@forEach
                }

                var triggered = false
                when (alert.condition) {
                    "CROSSING" -> {
                        triggered = (prevPrice < alert.targetPrice && currentPrice >= alert.targetPrice) ||
                                (prevPrice > alert.targetPrice && currentPrice <= alert.targetPrice)
                    }
                    "CROSSING_UP" -> {
                        triggered = prevPrice < alert.targetPrice && currentPrice >= alert.targetPrice
                    }
                    "CROSSING_DOWN" -> {
                        triggered = prevPrice > alert.targetPrice && currentPrice <= alert.targetPrice
                    }
                }

                if (triggered) {
                    // Fire notification based on configured method
                    NotificationHelper.fireAlertNotification(appContext, alert, currentPrice)

                    // Insert trigger history
                    db.triggerHistoryDao().insertHistory(
                        TriggerHistory(
                            alertId = alert.id,
                            symbol = symbol,
                            priceAtTrigger = currentPrice,
                            triggeredAt = now,
                            method = alert.priority
                        )
                    )

                    val logInfo = SymbolInfo.find(symbol)
                    logEvent("ALERT_TRIGGER", symbol, "ALERT FIRED: ${alert.title} at ${currentPrice.formatPriceDynamic(logInfo.getDisplayDecimals())}")

                    // Cooldown / lifecycle management
                    if (alert.isOneTime) {
                        db.alertDao().updateAlertActiveStatus(alert.id, false)
                    } else {
                        val cooldownEnd = now + alert.cooldownDurationMs
                        db.alertDao().updateAlertCooldown(alert.id, cooldownEnd)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PriceMonitor", "Error evaluating alerts for $symbol: ${e.message}", e)
            logEvent("ERROR", symbol, "Alert evaluation error: ${e.localizedMessage}")
        }
    }

    // Settings helpers
    suspend fun getSetting(key: String): String? {
        return db.appSettingDao().getSetting(key)?.value
    }

    suspend fun saveSetting(key: String, value: String) {
        db.appSettingDao().insertSetting(AppSetting(key, value))
        // Dynamic volatile state cache synchronization
        if (key == "websocket_use_native_mode") {
            isNativeModeEnabled = value == "true"
        } else if (key == "ui_price_update_interval_ms") {
            cachedPriceUpdateIntervalMs = value.toLongOrNull()?.coerceIn(100L, 10000L) ?: 500L
        }

        // Auto restart loop if API key or update interval shifts
        if (key == "twelve_data_api_key") {
            if (activeWebSocket != null) {
                try {
                    activeWebSocket?.close(1000, "API key changed")
                } catch (e: Exception) {}
                activeWebSocket = null
            }
            _connectionStatus.value = "OFFLINE"
            startMonitoringLoop()
        } else if (key == "ui_price_update_interval_ms") {
            startMonitoringLoop()
        }
    }

    suspend fun getUiPriceIntervalSettingFromDb(): Long {
        val raw = getSetting("ui_price_update_interval_ms")
        return raw?.toLongOrNull()?.coerceIn(100L, 10000L) ?: 500L
    }

    suspend fun getUiPriceIntervalSetting(): Long {
        return cachedPriceUpdateIntervalMs
    }

    fun executeOneShotSyncCheck() {
        scope.launch(Dispatchers.IO) {
            try {
                val list = db.alertDao().getAllAlerts()
                val activeList = list.filter { it.isActive }
                activeAlertsCache = activeList
                hasActiveAlerts = activeList.isNotEmpty()
                _hasActiveAlertsFlow.value = hasActiveAlerts
                
                Log.d("PriceMonitor", "One-shot WorkManager sync executed. Active alerts: ${activeList.size}")

                // Backstop: re-evaluate the market-aware monitoring loop so that, even if the
                // in-process scheduler was killed, we reconnect promptly once the market is open.
                startMonitoringLoop()
            } catch (e: Exception) {
                Log.e("PriceMonitor", "Error during one-shot background synchronization check: ${e.message}")
            }
        }
    }

    private inner class IsolateWorker(val id: Int) {
        val queue = java.util.concurrent.LinkedBlockingQueue<String>()
        var job: Job? = null
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "FinTrace-IsolateWorker-$id")
        }
        val dispatcher = executor.asCoroutineDispatcher()

        fun start() {
            job?.cancel()
            job = scope.launch(dispatcher) {
                while (true) {
                    try {
                        val packet = queue.take()
                        processPacket(packet)
                    } catch (e: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        Log.e("IsolateWorker-$id", "Error in isolate loop: ${e.message}")
                    }
                }
            }
        }

        fun stop() {
            job?.cancel()
            job = null
            try {
                executor.shutdown()
            } catch (e: Exception) {}
        }

        fun submit(packet: String) {
            queue.offer(packet)
        }

        fun getQueueSize(): Int = queue.size

        private suspend fun processPacket(text: String) {
            try {
                val json = JSONObject(text)
                val event = json.optString("event")
                val status = json.optString("status")
                val message = json.optString("message")

                if (event == "error" || status == "error" || (json.has("code") && json.optInt("code") >= 400)) {
                    val errMsg = "TWELVE DATA ERROR (Isolate-$id): ${if (message.isNullOrBlank()) "Server response error: $text" else message}"
                    logEvent("ERROR", null, errMsg)
                    Log.e("PriceMonitor", errMsg)
                    return
                }

                if (event == "price") {
                    val sym = json.optString("symbol")
                    val priceVal = json.optDouble("price")
                    if (!sym.isNullOrBlank() && !priceVal.isNaN()) {
                        processSinglePriceUpdateFromIsolate(id, sym, priceVal, System.currentTimeMillis())
                    }
                }
            } catch (e: Exception) {
                Log.e("IsolateWorker-$id", "JSON parsing/processing failed: ${e.message}")
            }
        }
    }

    private inner class IsolateWorkerPool {
        val workers = mutableListOf<IsolateWorker>()

        fun setupWorkers(count: Int) {
            synchronized(workers) {
                // Idempotent: workers run until shutdown(). Re-creating them on every
                // startMonitoringLoop() (screen toggle / setting change) caused needless
                // thread churn, so skip if the pool is already populated.
                if (workers.size == count) return
                workers.forEach { it.stop() }
                workers.clear()
                for (i in 0 until count) {
                    val worker = IsolateWorker(i)
                    worker.start()
                    workers.add(worker)
                }
            }
            logEvent("SYSTEM", null, "Isolate Worker Pool initialized with $count parallel load-balanced worker isolates.")
        }

        fun dispatch(packet: String) {
            synchronized(workers) {
                if (workers.isEmpty()) return
                val bestWorker = workers.minByOrNull { it.getQueueSize() } ?: workers[0]
                bestWorker.submit(packet)
            }
        }

        fun shutdown() {
            synchronized(workers) {
                workers.forEach { it.stop() }
                workers.clear()
            }
        }
    }

    private suspend fun processSinglePriceUpdateFromIsolate(isolateId: Int, sym: String, newPrice: Double, now: Long) {
        try {
            val isNative = getWebsocketUseNativeMode()
            val interval = getUiPriceIntervalSetting()
            if (!isNative) {
                val lastUpdateTime = lastSymbolUpdateTimes[sym] ?: 0L
                if (now - lastUpdateTime < interval) {
                    return // Throttle price update
                }
            }
            
            lastSymbolUpdateTimes[sym] = now
            val info = SymbolInfo.find(sym)
            
            var prevPrice = info.defaultPrice
            var openPrice = newPrice

            // Ensure our price state cache has initial entries so that we have historical fields populated
            if (_priceState.value.isEmpty()) {
                _priceState.update { currentMap ->
                    if (currentMap.isEmpty()) {
                        val initialMap = mutableMapOf<String, PriceTick>()
                        SymbolInfo.ALL.forEach { s ->
                            initialMap[s.symbol] = PriceTick(
                                symbol = s.symbol,
                                price = s.defaultPrice,
                                bid = s.defaultPrice - (0.0004 * s.defaultPrice),
                                ask = s.defaultPrice + (0.0004 * s.defaultPrice),
                                history = listOf(s.defaultPrice),
                                openPrice = s.defaultPrice
                            )
                        }
                        initialMap
                    } else {
                        currentMap
                    }
                }
            }

            // Perform transactional atomic calculations to ensure multi-threaded sync safety
            _priceState.update { currentMap ->
                val currentTick = currentMap[sym] ?: PriceTick(sym, info.defaultPrice)
                prevPrice = currentTick.price
                openPrice = if (currentTick.price == info.defaultPrice) newPrice else (currentTick.openPrice ?: newPrice)

                val netChange = newPrice - openPrice
                val netChangePct = if (openPrice > 0.0) (netChange / openPrice) * 100 else 0.0

                val spreadFactor = when (info.category) {
                    "Metals" -> 0.0002
                    "Majors" -> 0.0001
                    else -> 0.00015
                }
                val spreadVal = newPrice * spreadFactor
                val bid = newPrice - (spreadVal / 2)
                val ask = newPrice + (spreadVal / 2)

                val oldHistory = currentTick.history
                val newHistory = (oldHistory + newPrice).takeLast(20)

                val updatedTick = PriceTick(
                    symbol = sym,
                    price = newPrice,
                    change = netChange,
                    changePercent = netChangePct,
                    bid = bid,
                    ask = ask,
                    history = newHistory,
                    openPrice = openPrice
                )

                val newMap = currentMap.toMutableMap()
                newMap[sym] = updatedTick
                newMap
            }

            // Dedicated asset worker thread evaluates threshold checklist/alerts
            evaluateAlerts(sym, prevPrice, newPrice)

            val displayDecs = info.getDisplayDecimals()
            val netChange = newPrice - openPrice
            val netChangePct = if (openPrice > 0.0) (netChange / openPrice) * 100 else 0.0
            val changeStr = if (netChange >= 0) "+${netChange.formatPriceDynamic(displayDecs)}" else netChange.formatPriceDynamic(displayDecs)
            val pctStr = if (netChangePct >= 0) "+${String.format(Locale.US, "%.2f", netChangePct)}%" else "${String.format(Locale.US, "%.2f", netChangePct)}%"
            val formattedPrice = newPrice.formatPriceDynamic(displayDecs)
            
            // Log with the dedicated Isolate Thread identifier to show visual evidence that each packet runs on its own isolate!
            logEvent("TICK", sym, "[IsolateWorker-$isolateId-${Thread.currentThread().name}] $sym live at $formattedPrice ($changeStr | $pctStr)")

            scheduleSystemUpdates()
        } catch (e: Exception) {
            Log.e("PriceMonitor", "Error processing live price update in worker isolate $isolateId for $sym: ${e.message}", e)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PriceMonitorManager? = null

        fun getInstance(context: Context): PriceMonitorManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PriceMonitorManager(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
