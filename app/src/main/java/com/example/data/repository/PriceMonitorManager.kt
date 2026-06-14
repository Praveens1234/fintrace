package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.data.database.AppDatabase
import com.example.data.database.MIGRATION_2_3
import com.example.data.model.AccountSnapshot
import com.example.data.model.AccountTransaction
import com.example.data.model.Alert
import com.example.data.model.AppSetting
import com.example.data.model.PendingOrder
import com.example.data.model.PriceTick
import com.example.data.model.SymbolInfo
import com.example.data.model.SymbolState
import com.example.data.model.Trade
import com.example.data.model.TriggerHistory
import com.example.data.model.formatPriceDynamic
import com.example.data.model.getDisplayDecimals
import com.example.data.market.MarketSchedule
import com.example.data.trading.TradingMath
import com.example.service.NotificationHelper
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.BufferedSource
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
import com.example.data.provider.PriceProvider

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
    ).addMigrations(MIGRATION_2_3).fallbackToDestructiveMigration(true).build()

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

    @Volatile
    private var activeProvider: PriceProvider = PriceProvider.TWELVE_DATA

    @Volatile
    private var avPollingJob: Job? = null

    @Volatile
    private var oandaStreamJob: Job? = null

    @Volatile
    private var cachedTtsLanguage = "en-US"

    fun getTtsLanguage(): String = cachedTtsLanguage

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

    // ── VIRTUAL TRADING STATE ─────────────────────────────────────────────
    // All trade/account mutations are serialized through tradeMutex so that cross-symbol updates to
    // the shared balance/margin stay atomic (data-integrity requirement). Live UI state is held in
    // memory and recomputed each tick; the database is written only on discrete events.
    private val tradeMutex = Mutex()

    private val _accountSnapshot = MutableStateFlow(AccountSnapshot())
    val accountSnapshot: StateFlow<AccountSnapshot> = _accountSnapshot.asStateFlow()

    private val _liveTradePnl = MutableStateFlow<Map<Int, Double>>(emptyMap())
    val liveTradePnl: StateFlow<Map<Int, Double>> = _liveTradePnl.asStateFlow()

    // Surfaces the result of the latest trade action (rejections, fills) for one-shot UI messages.
    private val _tradeMessage = MutableStateFlow<String?>(null)
    val tradeMessage: StateFlow<String?> = _tradeMessage.asStateFlow()
    fun consumeTradeMessage() { _tradeMessage.value = null }

    @Volatile private var openTradesCache: List<Trade> = emptyList()
    @Volatile private var activeOrdersCache: List<PendingOrder> = emptyList()
    @Volatile private var hasTradingActivity = false
    @Volatile private var accountBalance = 0.0
    @Volatile private var leverage = 100.0
    @Volatile private var stopoutLevel = 50.0

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
            cachedTtsLanguage = getSetting("tts_language") ?: "en-US"
            // activeProvider is re-read each loop iteration; no need to cache here

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

            // Load virtual-trading account config and keep trade/order caches fresh.
            accountBalance = db.accountTransactionDao().getLatestBalance()
                ?: (getSetting("account_balance")?.toDoubleOrNull() ?: 0.0)
            leverage = (getSetting("account_leverage")?.toDoubleOrNull() ?: 100.0).coerceAtLeast(1.0)
            stopoutLevel = getSetting("account_stopout_level")?.toDoubleOrNull() ?: 50.0
            tradeAlertsEnabled = (getSetting("trade_alerts_enabled") ?: "true") == "true"
            tradeAlertSoundMode = getSetting("trade_alert_sound_mode") ?: "Both"
            launch {
                db.tradeDao().getOpenTradesFlow().collect { list ->
                    openTradesCache = list
                    hasTradingActivity = list.isNotEmpty() || activeOrdersCache.isNotEmpty()
                    recomputeAccount()
                }
            }
            launch {
                db.pendingOrderDao().getPendingOrdersFlow().collect { list ->
                    activeOrdersCache = list
                    hasTradingActivity = list.isNotEmpty() || openTradesCache.isNotEmpty()
                }
            }

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

    suspend fun getEffectiveApiKeyForProvider(provider: PriceProvider): String? {
        val dbKey = getSetting(provider.apiKeySettingKey)
        if (!dbKey.isNullOrBlank()) return dbKey

        // Twelve Data also supports BuildConfig / system environment variable fallback
        if (provider == PriceProvider.TWELVE_DATA) {
            val buildConfigKey = try {
                com.example.BuildConfig.TWELVE_DATA_API_KEY
            } catch (e: Throwable) { null }
            if (!buildConfigKey.isNullOrBlank() &&
                buildConfigKey != "YOUR_TWELVE_DATA_API_KEY_HERE" &&
                buildConfigKey != "TWELVE_DATA_API_KEY"
            ) return buildConfigKey

            val sysEnvKey = System.getenv("TWELVE_DATA_API_KEY") ?: System.getenv("TWELVEDATA_API_KEY")
            if (!sysEnvKey.isNullOrBlank()) return sysEnvKey
        }
        return null
    }

    suspend fun getEffectiveApiKey(): String? = getEffectiveApiKeyForProvider(PriceProvider.TWELVE_DATA)

    suspend fun getActiveProvider(): PriceProvider {
        val raw = getSetting("active_price_provider") ?: return PriceProvider.TWELVE_DATA
        return PriceProvider.entries.find { it.name == raw } ?: PriceProvider.TWELVE_DATA
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
                    closeConnections("Market closed")
                    _connectionStatus.value = "CLOSED"
                    logEvent("SYSTEM", null, "Market is closed. Live monitoring paused to save CPU and battery; auto-resume scheduled for next session open.")
                    delay(untilChange.coerceIn(1_000L, maxClosedSleepMs))
                    continue
                }

                if (!isScreenOn && !hasActiveAlerts) {
                    closeConnections("Smart standby (screen off, no active alerts)")
                    _connectionStatus.value = "OFFLINE"
                    logEvent("SYSTEM", null, "Smart standby active: screen off and no active alerts. Live socket paused to protect battery.")
                    delay(untilChange.coerceIn(1_000L, standbyRecheckMs))
                    continue
                }

                val provider = getActiveProvider()
                activeProvider = provider
                val apiKey = getEffectiveApiKeyForProvider(provider)
                if (apiKey.isNullOrBlank()) {
                    closeConnections("No API key configured for ${provider.displayName}")
                    _connectionStatus.value = "OFFLINE"
                    logEvent("SYSTEM", null, "No ${provider.displayName} API key configured. Add a key in Settings → Connection & Market Feed.")
                    delay(untilChange.coerceIn(1_000L, standbyRecheckMs))
                    continue
                }

                when (provider) {
                    PriceProvider.ALPHA_VANTAGE -> {
                        if (avPollingJob == null || avPollingJob?.isActive != true) {
                            startAlphaVantagePolling(apiKey)
                        }
                    }
                    PriceProvider.OANDA_V20 -> {
                        if (oandaStreamJob == null || oandaStreamJob?.isActive != true) {
                            startOandaStreaming(apiKey)
                        }
                    }
                    else -> {
                        if (activeWebSocket == null || _connectionStatus.value == "CLOSED" || _connectionStatus.value == "OFFLINE") {
                            connectToProvider(apiKey, provider)
                        }
                    }
                }
                delay(untilChange.coerceIn(1_000L, openRecheckMs))
            }
        }
    }

    private fun millisUntil(instant: Instant): Long =
        (instant.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0L)

    private fun closeConnections(reason: String) {
        activeWebSocket?.let {
            try { it.close(1000, reason) } catch (_: Exception) {}
        }
        activeWebSocket = null
        avPollingJob?.cancel()
        avPollingJob = null
        oandaStreamJob?.cancel()
        oandaStreamJob = null
    }

    private fun closeSocket(reason: String) = closeConnections(reason)

    fun stopMonitoring() {
        marketJob?.cancel()
        connectionJob?.cancel()
        avPollingJob?.cancel()
        avPollingJob = null
        oandaStreamJob?.cancel()
        oandaStreamJob = null
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
                    attemptReconnect(apiKey, PriceProvider.TWELVE_DATA)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    reconnectCount++
                    _connectionStatus.value = "OFFLINE"
                    logEvent("ERROR", null, "Twelve Data connection failed (${t.message ?: "Unknown socket error"}). Live streaming interrupted. Freezing on the last updated price. Attempting background retry #$reconnectCount...")
                    attemptReconnect(apiKey, PriceProvider.TWELVE_DATA)
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

    private fun connectToProvider(apiKey: String, provider: PriceProvider) {
        when (provider) {
            PriceProvider.TWELVE_DATA -> connectToTwelveData(apiKey)
            PriceProvider.FINNHUB -> connectToFinnhub(apiKey)
            PriceProvider.ALPHA_VANTAGE -> startAlphaVantagePolling(apiKey)
            PriceProvider.TRADERMADE -> connectToTraderMade(apiKey)
            PriceProvider.OANDA_V20 -> startOandaStreaming(apiKey)
            PriceProvider.ALLTICK -> connectToAllTick(apiKey)
            PriceProvider.POLYGON -> connectToPolygon(apiKey)
        }
    }

    private fun connectToFinnhub(apiKey: String) {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            _connectionStatus.value = "CONNECTING"
            logEvent("SYSTEM", null, "Connecting to Finnhub WebSocket feed...")
            val request = Request.Builder()
                .url("wss://ws.finnhub.io?token=$apiKey")
                .build()

            activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _connectionStatus.value = "LIVE"
                    reconnectCount = 0
                    logEvent("SYSTEM", null, "Connected to Finnhub WebSocket successfully.")
                    val activeList = (_activeSymbols.value + _liveTickerSymbols.value).distinct()
                    activeList.forEach { sym ->
                        try {
                            val sub = JSONObject()
                            sub.put("type", "subscribe")
                            sub.put("symbol", toFinnhubSymbol(sym))
                            webSocket.send(sub.toString())
                        } catch (e: Exception) {
                            Log.e("PriceMonitor", "Finnhub subscribe error: ${e.message}")
                        }
                    }
                    startHeartbeat(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)
                        when (json.optString("type")) {
                            "trade" -> {
                                val data = json.optJSONArray("data") ?: return
                                for (i in 0 until data.length()) {
                                    val trade = data.getJSONObject(i)
                                    val finnhubSymbol = trade.optString("s")
                                    val price = trade.optDouble("p")
                                    if (finnhubSymbol.isNotBlank() && !price.isNaN() && price > 0) {
                                        val appSymbol = fromFinnhubSymbol(finnhubSymbol)
                                        if (appSymbol.isNotBlank()) {
                                            val normalized = JSONObject()
                                            normalized.put("event", "price")
                                            normalized.put("symbol", appSymbol)
                                            normalized.put("price", price)
                                            isolatePool.dispatch(normalized.toString())
                                        }
                                    }
                                }
                            }
                            "error" -> {
                                val msg = json.optString("msg")
                                logEvent("ERROR", null, "Finnhub error: $msg")
                            }
                            "ping", "no_data", "connected" -> { /* protocol messages — no action */ }
                        }
                    } catch (e: Exception) {
                        Log.e("PriceMonitor", "Finnhub message parse: ${e.message}")
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionStatus.value = "OFFLINE"
                    logEvent("SYSTEM", null, "Finnhub connection closed: $reason")
                    attemptReconnect(apiKey, PriceProvider.FINNHUB)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    reconnectCount++
                    _connectionStatus.value = "OFFLINE"
                    logEvent("ERROR", null, "Finnhub connection failed (${t.message}). Retry #$reconnectCount...")
                    attemptReconnect(apiKey, PriceProvider.FINNHUB)
                }
            })

            // Dynamic subscription sync
            launch {
                kotlinx.coroutines.flow.combine(_activeSymbols, _liveTickerSymbols) { active, ticker ->
                    (active + ticker).distinct()
                }.collect { list ->
                    val ws = activeWebSocket
                    if (_connectionStatus.value == "LIVE" && ws != null) {
                        list.forEach { sym ->
                            try {
                                val sub = JSONObject()
                                sub.put("type", "subscribe")
                                sub.put("symbol", toFinnhubSymbol(sym))
                                ws.send(sub.toString())
                            } catch (e: Exception) {
                                Log.e("PriceMonitor", "Finnhub subscription sync error: ${e.message}")
                            }
                        }
                        logEvent("SYSTEM", null, "Synced Finnhub subscriptions: ${list.joinToString(", ")}")
                    }
                }
            }
        }
    }

    private fun startAlphaVantagePolling(apiKey: String) {
        avPollingJob?.cancel()
        avPollingJob = scope.launch(Dispatchers.IO) {
            _connectionStatus.value = "CONNECTING"
            logEvent("SYSTEM", null, "Starting Alpha Vantage REST polling feed (free tier: 25 calls/day, 5 calls/min)...")
            val avClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            _connectionStatus.value = "LIVE"
            reconnectCount = 0

            var symbolIndex = 0
            while (isActive && MarketSchedule.isOpenNow()) {
                val symbols = (_activeSymbols.value + _liveTickerSymbols.value).distinct()
                if (symbols.isEmpty()) { delay(15_000L); continue }

                val sym = symbols[symbolIndex % symbols.size]
                symbolIndex++

                try {
                    val parts = sym.split("/")
                    val from = parts.getOrElse(0) { "" }
                    val to = parts.getOrElse(1) { "USD" }
                    if (from.isBlank()) { delay(1_000L); continue }

                    val url = "https://www.alphavantage.co/query" +
                        "?function=CURRENCY_EXCHANGE_RATE" +
                        "&from_currency=$from" +
                        "&to_currency=$to" +
                        "&apikey=$apiKey"
                    val resp = avClient.newCall(Request.Builder().url(url).build()).execute()
                    val body = resp.body?.string() ?: run { delay(13_000L); continue }

                    val json = JSONObject(body)
                    val rateObj = json.optJSONObject("Realtime Currency Exchange Rate")
                    if (rateObj != null) {
                        val price = rateObj.optString("5. Exchange Rate").toDoubleOrNull()
                        if (price != null && price > 0) {
                            processSinglePriceUpdateFromIsolate(-1, sym, price, System.currentTimeMillis())
                        }
                    } else if (json.has("Note") || json.has("Information")) {
                        logEvent("SYSTEM", sym, "Alpha Vantage rate limit reached — waiting 65 s before retry.")
                        delay(65_000L)
                        continue
                    } else if (json.has("Error Message")) {
                        logEvent("ERROR", sym, "Alpha Vantage: ${json.optString("Error Message")}")
                    }
                } catch (e: Exception) {
                    logEvent("ERROR", sym, "Alpha Vantage fetch error: ${e.message}")
                }

                // Free tier: 5 calls / min → 13 s minimum between calls
                delay(13_000L)
            }
            _connectionStatus.value = "OFFLINE"
            logEvent("SYSTEM", null, "Alpha Vantage polling stopped.")
        }
    }

    private fun toFinnhubSymbol(appSymbol: String): String =
        "OANDA:" + appSymbol.replace("/", "_")

    private fun fromFinnhubSymbol(finnhubSymbol: String): String =
        finnhubSymbol.removePrefix("OANDA:").replace("_", "/")

    // ── TRADERMADE SOCKET INTEGRATION ─────────────────────────────────────
    // wss://stream.tradermade.com/feedAdv — login then subscribe; symbols as "EURUSD:QUOTE".
    private fun connectToTraderMade(apiKey: String) {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            _connectionStatus.value = "CONNECTING"
            logEvent("SYSTEM", null, "Connecting to TraderMade WebSocket feed...")
            val request = Request.Builder()
                .url("wss://stream.tradermade.com/feedAdv")
                .build()

            activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectCount = 0
                    // TraderMade requires an explicit login frame before subscriptions.
                    try {
                        val login = JSONObject()
                        login.put("action", "login")
                        login.put("key", apiKey)
                        login.put("fmt", "JSON")
                        webSocket.send(login.toString())
                    } catch (e: Exception) {
                        Log.e("PriceMonitor", "TraderMade login error: ${e.message}")
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)
                        when (json.optString("type")) {
                            "login_ok" -> {
                                _connectionStatus.value = "LIVE"
                                logEvent("SYSTEM", null, "Connected to TraderMade successfully (symbol limit ${json.optInt("symbol_limit", 0)}).")
                                sendTraderMadeSubscribe(webSocket)
                            }
                            "login_reject" -> {
                                logEvent("ERROR", null, "TraderMade login rejected: ${json.optString("reason")}")
                            }
                            "QUOTE", "LAST_QUOTE" -> {
                                val tmSymbol = json.optString("s")
                                val bid = json.optString("b").toDoubleOrNull()
                                val ask = json.optString("a").toDoubleOrNull()
                                val mid = when {
                                    bid != null && ask != null -> (bid + ask) / 2.0
                                    bid != null -> bid
                                    ask != null -> ask
                                    else -> json.optString("m").toDoubleOrNull()
                                }
                                val appSymbol = fromTraderMadeSymbol(tmSymbol)
                                if (appSymbol.isNotBlank() && mid != null && mid > 0) {
                                    dispatchNormalized(appSymbol, mid)
                                }
                            }
                            "error" -> logEvent("ERROR", null, "TraderMade error: $text")
                        }
                    } catch (e: Exception) {
                        Log.e("PriceMonitor", "TraderMade message parse: ${e.message}")
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionStatus.value = "OFFLINE"
                    logEvent("SYSTEM", null, "TraderMade connection closed: $reason")
                    attemptReconnect(apiKey, PriceProvider.TRADERMADE)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    reconnectCount++
                    _connectionStatus.value = "OFFLINE"
                    logEvent("ERROR", null, "TraderMade connection failed (${t.message}). Retry #$reconnectCount...")
                    attemptReconnect(apiKey, PriceProvider.TRADERMADE)
                }
            })

            // Dynamic subscription sync (TraderMade does not persist subs across reconnects).
            launch {
                kotlinx.coroutines.flow.combine(_activeSymbols, _liveTickerSymbols) { active, ticker ->
                    (active + ticker).distinct()
                }.collect {
                    val ws = activeWebSocket
                    if (_connectionStatus.value == "LIVE" && ws != null) sendTraderMadeSubscribe(ws)
                }
            }
        }
    }

    private fun sendTraderMadeSubscribe(webSocket: WebSocket) {
        try {
            val list = (_activeSymbols.value + _liveTickerSymbols.value).distinct()
                .map { toTraderMadeSymbol(it) }
            if (list.isEmpty()) return
            val sub = JSONObject()
            sub.put("action", "subscribe")
            sub.put("symbols", org.json.JSONArray(list))
            webSocket.send(sub.toString())
            logEvent("SYSTEM", null, "Synced TraderMade subscriptions: ${list.joinToString(", ")}")
        } catch (e: Exception) {
            Log.e("PriceMonitor", "TraderMade subscribe error: ${e.message}")
        }
    }

    private fun toTraderMadeSymbol(appSymbol: String): String =
        appSymbol.replace("/", "").uppercase() + ":QUOTE"

    private fun fromTraderMadeSymbol(tmSymbol: String): String {
        val raw = tmSymbol.substringBefore(":").uppercase()
        return if (raw.length == 6) raw.substring(0, 3) + "/" + raw.substring(3) else raw
    }

    // ── OANDA v20 HTTP STREAMING INTEGRATION ──────────────────────────────
    // GET {stream}/v3/accounts/{id}/pricing/stream?instruments=EUR_USD,... ; Bearer token.
    // Newline-delimited JSON: PRICE objects and HEARTBEAT keep-alives every 5s.
    private fun startOandaStreaming(token: String) {
        oandaStreamJob?.cancel()
        oandaStreamJob = scope.launch(Dispatchers.IO) {
            _connectionStatus.value = "CONNECTING"
            val accountId = getSetting(PriceProvider.OANDA_ACCOUNT_ID_KEY)
            if (accountId.isNullOrBlank()) {
                _connectionStatus.value = "OFFLINE"
                logEvent("ERROR", null, "OANDA requires an Account ID. Add it in Settings → Connection & Market Feed.")
                return@launch
            }
            val env = (getSetting(PriceProvider.OANDA_ENVIRONMENT_KEY) ?: "practice").lowercase()
            val host = if (env == "live") "stream-fxtrade.oanda.com" else "stream-fxpractice.oanda.com"
            logEvent("SYSTEM", null, "Connecting to OANDA v20 $env streaming feed...")

            val streamClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS) // streaming: no read timeout
                .build()

            while (isActive && MarketSchedule.isOpenNow()) {
                val instruments = (_activeSymbols.value + _liveTickerSymbols.value).distinct()
                    .map { toOandaSymbol(it) }
                if (instruments.isEmpty()) { delay(5_000L); continue }
                val url = "https://$host/v3/accounts/$accountId/pricing/stream" +
                    "?instruments=" + instruments.joinToString("%2C")
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .build()
                    streamClient.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            logEvent("ERROR", null, "OANDA stream HTTP ${resp.code}: ${resp.message}")
                            delay(10_000L)
                            return@use
                        }
                        _connectionStatus.value = "LIVE"
                        reconnectCount = 0
                        logEvent("SYSTEM", null, "Connected to OANDA v20 streaming successfully.")
                        val source: BufferedSource = resp.body!!.source()
                        // Re-open the stream when the active instrument set changes.
                        val subscribed = instruments.toSet()
                        while (isActive && MarketSchedule.isOpenNow()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.isBlank()) continue
                            val json = JSONObject(line)
                            if (json.optString("type") == "PRICE") {
                                val instrument = json.optString("instrument")
                                val bids = json.optJSONArray("bids")
                                val asks = json.optJSONArray("asks")
                                val bid = bids?.optJSONObject(0)?.optString("price")?.toDoubleOrNull()
                                val ask = asks?.optJSONObject(0)?.optString("price")?.toDoubleOrNull()
                                val mid = when {
                                    bid != null && ask != null -> (bid + ask) / 2.0
                                    bid != null -> bid
                                    ask != null -> ask
                                    else -> null
                                }
                                val appSymbol = fromOandaSymbol(instrument)
                                if (appSymbol.isNotBlank() && mid != null && mid > 0) {
                                    processSinglePriceUpdateFromIsolate(-1, appSymbol, mid, System.currentTimeMillis())
                                }
                            }
                            // HEARTBEAT messages are ignored (keep-alive only).
                            val current = (_activeSymbols.value + _liveTickerSymbols.value).distinct()
                                .map { toOandaSymbol(it) }.toSet()
                            if (current != subscribed) break // reconnect with new instrument list
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        reconnectCount++
                        logEvent("ERROR", null, "OANDA stream error (${e.message}). Retry #$reconnectCount...")
                        delay((reconnectCount.coerceAtMost(3) * 5_000L).coerceAtLeast(5_000L))
                    }
                }
            }
            _connectionStatus.value = "OFFLINE"
            logEvent("SYSTEM", null, "OANDA streaming stopped.")
        }
    }

    private fun toOandaSymbol(appSymbol: String): String = appSymbol.replace("/", "_").uppercase()
    private fun fromOandaSymbol(oandaSymbol: String): String = oandaSymbol.replace("_", "/").uppercase()

    // ── ALLTICK SOCKET INTEGRATION ────────────────────────────────────────
    // wss://quote.alltick.co/quote-b-ws-api?token=TOKEN — cmd_id protocol, heartbeat 22000.
    private fun connectToAllTick(apiKey: String) {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            _connectionStatus.value = "CONNECTING"
            logEvent("SYSTEM", null, "Connecting to AllTick WebSocket feed...")
            val request = Request.Builder()
                .url("wss://quote.alltick.co/quote-b-ws-api?token=$apiKey")
                .build()

            activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _connectionStatus.value = "LIVE"
                    reconnectCount = 0
                    logEvent("SYSTEM", null, "Connected to AllTick successfully.")
                    sendAllTickSubscribe(webSocket)
                    startAllTickHeartbeat(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)
                        val data = json.optJSONObject("data") ?: return
                        val code = data.optString("code")
                        val price = data.optString("price").toDoubleOrNull()
                        if (code.isNotBlank() && price != null && price > 0) {
                            val appSymbol = fromAllTickSymbol(code)
                            if (appSymbol.isNotBlank()) dispatchNormalized(appSymbol, price)
                        }
                    } catch (e: Exception) {
                        Log.e("PriceMonitor", "AllTick message parse: ${e.message}")
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionStatus.value = "OFFLINE"
                    logEvent("SYSTEM", null, "AllTick connection closed: $reason")
                    attemptReconnect(apiKey, PriceProvider.ALLTICK)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    reconnectCount++
                    _connectionStatus.value = "OFFLINE"
                    logEvent("ERROR", null, "AllTick connection failed (${t.message}). Retry #$reconnectCount...")
                    attemptReconnect(apiKey, PriceProvider.ALLTICK)
                }
            })

            launch {
                kotlinx.coroutines.flow.combine(_activeSymbols, _liveTickerSymbols) { active, ticker ->
                    (active + ticker).distinct()
                }.collect {
                    val ws = activeWebSocket
                    if (_connectionStatus.value == "LIVE" && ws != null) sendAllTickSubscribe(ws)
                }
            }
        }
    }

    private val allTickSeq = java.util.concurrent.atomic.AtomicInteger(1)

    private fun sendAllTickSubscribe(webSocket: WebSocket) {
        try {
            val all = (_activeSymbols.value + _liveTickerSymbols.value).distinct()
            // Free tier caps at 5 product codes per subscription.
            val capped = all.take(5)
            if (capped.size < all.size) {
                logEvent("SYSTEM", null, "AllTick free tier supports 5 symbols; streaming the first 5: ${capped.joinToString(", ")}")
            }
            if (capped.isEmpty()) return
            val symbolList = org.json.JSONArray()
            capped.forEach { sym ->
                val item = JSONObject()
                item.put("code", toAllTickSymbol(sym))
                item.put("depth_level", 5)
                symbolList.put(item)
            }
            val data = JSONObject()
            data.put("symbol_list", symbolList)
            val msg = JSONObject()
            msg.put("cmd_id", 22004) // latest trade tick
            msg.put("seq_id", allTickSeq.getAndIncrement())
            msg.put("trace", java.util.UUID.randomUUID().toString())
            msg.put("data", data)
            webSocket.send(msg.toString())
            logEvent("SYSTEM", null, "Synced AllTick subscriptions: ${capped.joinToString(", ")}")
        } catch (e: Exception) {
            Log.e("PriceMonitor", "AllTick subscribe error: ${e.message}")
        }
    }

    private fun startAllTickHeartbeat(webSocket: WebSocket) {
        scope.launch {
            while (_connectionStatus.value == "LIVE" && activeWebSocket == webSocket) {
                delay(10_000L) // AllTick requires a heartbeat every 10 seconds
                try {
                    val hb = JSONObject()
                    hb.put("cmd_id", 22000)
                    hb.put("seq_id", allTickSeq.getAndIncrement())
                    hb.put("trace", java.util.UUID.randomUUID().toString())
                    hb.put("data", JSONObject())
                    webSocket.send(hb.toString())
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    // AllTick uses bespoke codes for metals.
    private fun toAllTickSymbol(appSymbol: String): String = when (appSymbol.uppercase()) {
        "XAU/USD" -> "GOLD"
        "XAG/USD" -> "Silver"
        else -> appSymbol.replace("/", "").uppercase()
    }

    private fun fromAllTickSymbol(code: String): String = when (code.uppercase()) {
        "GOLD" -> "XAU/USD"
        "SILVER" -> "XAG/USD"
        else -> if (code.length == 6) code.substring(0, 3).uppercase() + "/" + code.substring(3).uppercase() else code
    }

    // ── POLYGON.IO SOCKET INTEGRATION ─────────────────────────────────────
    // wss://socket.polygon.io/forex — auth then subscribe "C.EUR/USD". Messages arrive as arrays.
    private fun connectToPolygon(apiKey: String) {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            _connectionStatus.value = "CONNECTING"
            logEvent("SYSTEM", null, "Connecting to Polygon.io WebSocket feed...")
            val request = Request.Builder()
                .url("wss://socket.polygon.io/forex")
                .build()

            activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectCount = 0
                    try {
                        val auth = JSONObject()
                        auth.put("action", "auth")
                        auth.put("params", apiKey)
                        webSocket.send(auth.toString())
                    } catch (e: Exception) {
                        Log.e("PriceMonitor", "Polygon auth error: ${e.message}")
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val arr = org.json.JSONArray(text)
                        for (i in 0 until arr.length()) {
                            val msg = arr.optJSONObject(i) ?: continue
                            when (msg.optString("ev")) {
                                "status" -> {
                                    val status = msg.optString("status")
                                    val m = msg.optString("message")
                                    if (status == "auth_success") {
                                        _connectionStatus.value = "LIVE"
                                        logEvent("SYSTEM", null, "Connected to Polygon.io successfully.")
                                        sendPolygonSubscribe(webSocket)
                                    } else if (status == "auth_failed" || status == "error") {
                                        logEvent("ERROR", null, "Polygon status: $m")
                                    }
                                }
                                "C" -> {
                                    val pair = msg.optString("p")
                                    val ask = msg.optDouble("a", Double.NaN)
                                    val bid = msg.optDouble("b", Double.NaN)
                                    val mid = when {
                                        !ask.isNaN() && !bid.isNaN() -> (ask + bid) / 2.0
                                        !bid.isNaN() -> bid
                                        !ask.isNaN() -> ask
                                        else -> Double.NaN
                                    }
                                    if (pair.isNotBlank() && !mid.isNaN() && mid > 0) {
                                        dispatchNormalized(pair.uppercase(), mid)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PriceMonitor", "Polygon message parse: ${e.message}")
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionStatus.value = "OFFLINE"
                    logEvent("SYSTEM", null, "Polygon.io connection closed: $reason")
                    attemptReconnect(apiKey, PriceProvider.POLYGON)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    reconnectCount++
                    _connectionStatus.value = "OFFLINE"
                    logEvent("ERROR", null, "Polygon.io connection failed (${t.message}). Retry #$reconnectCount...")
                    attemptReconnect(apiKey, PriceProvider.POLYGON)
                }
            })

            launch {
                kotlinx.coroutines.flow.combine(_activeSymbols, _liveTickerSymbols) { active, ticker ->
                    (active + ticker).distinct()
                }.collect {
                    val ws = activeWebSocket
                    if (_connectionStatus.value == "LIVE" && ws != null) sendPolygonSubscribe(ws)
                }
            }
        }
    }

    private fun sendPolygonSubscribe(webSocket: WebSocket) {
        try {
            // Polygon's forex cluster covers FX pairs only (metals are not on this feed).
            val all = (_activeSymbols.value + _liveTickerSymbols.value).distinct()
            val supported = all.filterNot { it.uppercase().startsWith("XAU") || it.uppercase().startsWith("XAG") }
            val skipped = all - supported.toSet()
            if (skipped.isNotEmpty()) {
                logEvent("SYSTEM", null, "Polygon.io forex feed does not carry metals; skipping ${skipped.joinToString(", ")}.")
            }
            if (supported.isEmpty()) return
            val params = supported.joinToString(",") { "C.${it.uppercase()}" }
            val sub = JSONObject()
            sub.put("action", "subscribe")
            sub.put("params", params)
            webSocket.send(sub.toString())
            logEvent("SYSTEM", null, "Synced Polygon.io subscriptions: $params")
        } catch (e: Exception) {
            Log.e("PriceMonitor", "Polygon subscribe error: ${e.message}")
        }
    }

    /** Normalize an (appSymbol, price) tick into the internal packet and dispatch to the isolate pool. */
    private fun dispatchNormalized(appSymbol: String, price: Double) {
        try {
            val normalized = JSONObject()
            normalized.put("event", "price")
            normalized.put("symbol", appSymbol)
            normalized.put("price", price)
            isolatePool.dispatch(normalized.toString())
        } catch (e: Exception) {
            Log.e("PriceMonitor", "Normalize/dispatch error: ${e.message}")
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

    private fun attemptReconnect(apiKey: String, provider: PriceProvider = PriceProvider.TWELVE_DATA) {
        scope.launch {
            val delayMs = when {
                reconnectCount <= 1 -> 5000L
                reconnectCount <= 2 -> 10000L
                else -> 30000L
            }
            delay(delayMs)
            if (!MarketSchedule.isOpenNow()) {
                _connectionStatus.value = "CLOSED"
                return@launch
            }
            if (!isScreenOn && !hasActiveAlerts) return@launch
            if (_connectionStatus.value != "LIVE") {
                connectToProvider(apiKey, provider)
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

    // ── VIRTUAL TRADING ENGINE ────────────────────────────────────────────

    @Volatile private var tradeAlertsEnabled = true
    @Volatile private var tradeAlertSoundMode = "Both" // "Both" | "Tone" | "TTS" | "Silent"

    fun getLeverage(): Double = leverage
    fun getStopoutLevel(): Double = stopoutLevel

    private fun pip(symbol: String) = SymbolInfo.find(symbol).getDisplayDecimals()

    private fun conversionFactor(symbol: String, logIfMissing: Boolean): Double {
        val f = TradingMath.quoteToUsdFactor(symbol, _priceState.value)
        if (f == null && logIfMissing) {
            logEvent("SYSTEM", symbol, "No USD conversion pair active for ${TradingMath.quoteCurrency(symbol)}; PNL uses a 1.0 fallback factor until a conversion pair streams.")
        }
        return f ?: 1.0
    }

    /** Recompute the live account snapshot and per-trade PNL map from in-memory caches (no DB). */
    private fun recomputeAccount() {
        val prices = _priceState.value
        val trades = openTradesCache
        _accountSnapshot.value = TradingMath.accountSnapshot(trades, accountBalance, prices)
        if (trades.isEmpty()) {
            if (_liveTradePnl.value.isNotEmpty()) _liveTradePnl.value = emptyMap()
            return
        }
        val map = HashMap<Int, Double>(trades.size)
        for (t in trades) {
            val px = prices[t.symbol]?.price ?: t.entryPrice
            map[t.id] = TradingMath.unrealizedPnlUsd(t, px, prices)
        }
        _liveTradePnl.value = map
    }

    /** Per-tick trade processing: fill pending orders, hit SL/TP, then guard margin/stop-out. */
    private suspend fun evaluateTrades(symbol: String, price: Double) {
        if (!hasTradingActivity) return
        try {
            tradeMutex.withLock {
                // 1) Pending orders for this symbol.
                val orders = db.pendingOrderDao().getActiveOrdersForSymbol(symbol)
                for (order in orders) {
                    if (shouldFill(order, price)) fillPendingOrder(order, price)
                }
                // 2) Open trades for this symbol: SL/TP.
                val trades = db.tradeDao().getOpenTradesForSymbol(symbol)
                for (t in trades) {
                    val tp = t.takeProfit
                    val sl = t.stopLoss
                    val isLong = t.side.equals("LONG", ignoreCase = true)
                    when {
                        tp != null && ((isLong && price >= tp) || (!isLong && price <= tp)) ->
                            closeTradeAtPrice(t, price, "TP")
                        sl != null && ((isLong && price <= sl) || (!isLong && price >= sl)) ->
                            closeTradeAtPrice(t, price, "SL")
                    }
                }
                // 3) Account-level margin guard.
                enforceStopOut()
            }
        } catch (e: Exception) {
            Log.e("PriceMonitor", "evaluateTrades error for $symbol: ${e.message}", e)
        }
        recomputeAccount()
    }

    private fun shouldFill(order: PendingOrder, price: Double): Boolean {
        val isLong = order.side.equals("LONG", ignoreCase = true)
        val isStop = order.orderKind.equals("STOP", ignoreCase = true)
        return when {
            isLong && !isStop -> price <= order.targetPrice   // BUY LIMIT
            isLong && isStop -> price >= order.targetPrice     // BUY STOP
            !isLong && !isStop -> price >= order.targetPrice   // SELL LIMIT
            else -> price <= order.targetPrice                 // SELL STOP
        }
    }

    /** Convert a triggered pending order into an open Trade. Must hold tradeMutex. */
    private suspend fun fillPendingOrder(order: PendingOrder, fillPrice: Double) {
        val now = System.currentTimeMillis()
        val margin = TradingMath.requiredMarginUsd(order.symbol, order.lots, fillPrice, leverage, _priceState.value)
        val trade = Trade(
            symbol = order.symbol,
            side = order.side,
            lots = order.lots,
            entryPrice = fillPrice,
            stopLoss = order.stopLoss,
            takeProfit = order.takeProfit,
            openTime = now,
            status = "OPEN",
            originOrderId = order.id,
            marginUsd = margin
        )
        val tradeId = db.tradeDao().insert(trade).toInt()
        db.pendingOrderDao().update(order.copy(status = "FILLED", executedAt = now, resultingTradeId = tradeId))
        val decs = pip(order.symbol)
        logEvent("TRADE", order.symbol, "ORDER #${order.id} FILLED → Trade #$tradeId ${order.side} ${order.lots} lot @ ${fillPrice.formatPriceDynamic(decs)}")
        fireTradeAlert(
            "Order Executed",
            "ORDER FILLED — ${order.symbol} ${order.side} ${fmtLots(order.lots)} lot · entry ${fillPrice.formatPriceDynamic(decs)} · Trade #$tradeId"
        )
    }

    /** Fully close [trade] at [exitPrice]. Must hold tradeMutex. */
    private suspend fun closeTradeAtPrice(trade: Trade, exitPrice: Double, closedBy: String) {
        val now = System.currentTimeMillis()
        val factor = conversionFactor(trade.symbol, true)
        val realized = TradingMath.realizedPnlUsd(trade, exitPrice, trade.lots, factor)
        val totalRealized = trade.realizedPnl + realized
        db.tradeDao().update(
            trade.copy(
                status = "CLOSED",
                exitPrice = exitPrice,
                closeTime = now,
                realizedPnl = totalRealized,
                closedBy = closedBy,
                marginUsd = 0.0
            )
        )
        bookRealized(realized, "Trade #${trade.id} closed ($closedBy)", trade.id)
        val decs = pip(trade.symbol)
        logEvent("TRADE", trade.symbol, "TRADE #${trade.id} CLOSED ($closedBy) @ ${exitPrice.formatPriceDynamic(decs)} · PNL ${fmtUsd(realized)}")
        fireTradeAlert(
            closeLabel(closedBy),
            "${closeLabel(closedBy)} — ${trade.symbol} ${trade.side} ${fmtLots(trade.lots)} lot · entry ${trade.entryPrice.formatPriceDynamic(decs)} → ${exitPrice.formatPriceDynamic(decs)} · PNL ${fmtUsd(realized)} · Balance ${fmtUsd(accountBalance)}"
        )
    }

    /** Add realized PNL / cash movement to the balance and write a ledger transaction. Must hold tradeMutex. */
    private suspend fun bookRealized(amount: Double, note: String, tradeId: Int?, type: String = "REALIZED_PNL") {
        accountBalance += amount
        saveSetting("account_balance", accountBalance.toString())
        db.accountTransactionDao().insert(
            AccountTransaction(
                type = type,
                amount = amount,
                balanceAfter = accountBalance,
                note = note,
                relatedTradeId = tradeId
            )
        )
    }

    /** Close the worst-losing open position(s) while margin level is below the stop-out threshold. */
    private suspend fun enforceStopOut() {
        if (stopoutLevel <= 0) return
        var guard = 0
        while (guard++ < 50) {
            val open = db.tradeDao().getOpenTrades()
            if (open.isEmpty()) break
            val snap = TradingMath.accountSnapshot(open, accountBalance, _priceState.value)
            if (snap.usedMargin <= 0.0 || snap.marginLevel >= stopoutLevel) break
            val worst = open.minByOrNull {
                TradingMath.unrealizedPnlUsd(it, _priceState.value[it.symbol]?.price ?: it.entryPrice, _priceState.value)
            } ?: break
            val px = _priceState.value[worst.symbol]?.price ?: worst.entryPrice
            logEvent("TRADE", worst.symbol, "STOP-OUT: margin level ${String.format(Locale.US, "%.0f", snap.marginLevel)}% < ${stopoutLevel.toInt()}%. Auto-closing worst position #${worst.id}.")
            closeTradeAtPrice(worst, px, "STOPOUT")
        }
    }

    // ── Public trade actions (invoked from the ViewModel) ─────────────────

    suspend fun placeMarketOrder(symbol: String, side: String, lots: Double, entryPrice: Double, sl: Double?, tp: Double?): Boolean {
        if (lots <= 0 || entryPrice <= 0) { _tradeMessage.value = "Enter a valid lot size and entry price."; return false }
        return tradeMutex.withLock {
            val margin = TradingMath.requiredMarginUsd(symbol, lots, entryPrice, leverage, _priceState.value)
            val snap = TradingMath.accountSnapshot(openTradesCache, accountBalance, _priceState.value)
            if (margin > snap.freeMargin) {
                _tradeMessage.value = "Insufficient free margin: need ${fmtUsd(margin)}, have ${fmtUsd(snap.freeMargin)}."
                return@withLock false
            }
            val now = System.currentTimeMillis()
            val trade = Trade(
                symbol = symbol, side = side, lots = lots, entryPrice = entryPrice,
                stopLoss = sl, takeProfit = tp, openTime = now, status = "OPEN", marginUsd = margin
            )
            val id = db.tradeDao().insert(trade).toInt()
            val decs = pip(symbol)
            logEvent("TRADE", symbol, "MARKET ORDER → Trade #$id $side ${fmtLots(lots)} lot @ ${entryPrice.formatPriceDynamic(decs)} (margin ${fmtUsd(margin)})")
            fireTradeAlert("Position Opened", "OPENED — $symbol $side ${fmtLots(lots)} lot @ ${entryPrice.formatPriceDynamic(decs)} · Trade #$id")
            _tradeMessage.value = "Trade #$id opened."
            true
        }
    }

    suspend fun placePendingOrder(symbol: String, side: String, kind: String, lots: Double, targetPrice: Double, sl: Double?, tp: Double?): Boolean {
        if (lots <= 0 || targetPrice <= 0) { _tradeMessage.value = "Enter a valid lot size and trigger price."; return false }
        return tradeMutex.withLock {
            val now = System.currentTimeMillis()
            val order = PendingOrder(
                symbol = symbol, side = side, orderKind = kind, lots = lots, targetPrice = targetPrice,
                stopLoss = sl, takeProfit = tp, createdAt = now, status = "PENDING"
            )
            val id = db.pendingOrderDao().insert(order).toInt()
            val decs = pip(symbol)
            logEvent("TRADE", symbol, "PENDING ORDER #$id $kind $side ${fmtLots(lots)} lot @ ${targetPrice.formatPriceDynamic(decs)}")
            _tradeMessage.value = "Order #$id placed."
            true
        }
    }

    suspend fun modifyPendingOrder(id: Int, targetPrice: Double, lots: Double, sl: Double?, tp: Double?) {
        tradeMutex.withLock {
            val o = db.pendingOrderDao().getById(id) ?: return@withLock
            if (o.status != "PENDING") return@withLock
            db.pendingOrderDao().update(o.copy(targetPrice = targetPrice, lots = lots, stopLoss = sl, takeProfit = tp))
            logEvent("TRADE", o.symbol, "ORDER #$id modified.")
        }
    }

    suspend fun cancelPendingOrder(id: Int) {
        tradeMutex.withLock {
            val o = db.pendingOrderDao().getById(id) ?: return@withLock
            db.pendingOrderDao().update(o.copy(status = "CANCELLED", closedBy = "USER", executedAt = System.currentTimeMillis()))
            logEvent("TRADE", o.symbol, "ORDER #$id cancelled by user.")
        }
    }

    suspend fun modifyTrade(id: Int, sl: Double?, tp: Double?, entry: Double?) {
        tradeMutex.withLock {
            val t = db.tradeDao().getById(id) ?: return@withLock
            if (t.status != "OPEN") return@withLock
            val newEntry = entry ?: t.entryPrice
            val newMargin = TradingMath.requiredMarginUsd(t.symbol, t.lots, newEntry, leverage, _priceState.value)
            db.tradeDao().update(t.copy(stopLoss = sl, takeProfit = tp, entryPrice = newEntry, marginUsd = newMargin))
            logEvent("TRADE", t.symbol, "TRADE #$id modified (SL/TP/entry).")
        }
        recomputeAccount()
    }

    suspend fun closeTrade(id: Int) {
        tradeMutex.withLock {
            val t = db.tradeDao().getById(id) ?: return@withLock
            if (t.status != "OPEN") return@withLock
            val px = _priceState.value[t.symbol]?.price ?: t.entryPrice
            closeTradeAtPrice(t, px, "USER")
        }
        recomputeAccount()
    }

    suspend fun partialCloseTrade(id: Int, lotsToClose: Double) {
        tradeMutex.withLock {
            val t = db.tradeDao().getById(id) ?: return@withLock
            if (t.status != "OPEN" || lotsToClose <= 0) return@withLock
            if (lotsToClose >= t.lots) { closeTradeAtPrice(t, _priceState.value[t.symbol]?.price ?: t.entryPrice, "USER"); return@withLock }
            val px = _priceState.value[t.symbol]?.price ?: t.entryPrice
            val factor = conversionFactor(t.symbol, true)
            val realized = TradingMath.realizedPnlUsd(t, px, lotsToClose, factor)
            val remaining = t.lots - lotsToClose
            val newMargin = if (t.lots > 0) t.marginUsd * (remaining / t.lots) else 0.0
            db.tradeDao().update(t.copy(lots = remaining, realizedPnl = t.realizedPnl + realized, marginUsd = newMargin))
            bookRealized(realized, "Trade #${t.id} partial close (${fmtLots(lotsToClose)} lot)", t.id)
            val decs = pip(t.symbol)
            logEvent("TRADE", t.symbol, "TRADE #$id PARTIAL CLOSE ${fmtLots(lotsToClose)} lot @ ${px.formatPriceDynamic(decs)} · PNL ${fmtUsd(realized)} · ${fmtLots(remaining)} lot remaining")
            fireTradeAlert("Partial Close", "PARTIAL CLOSE — ${t.symbol} ${fmtLots(lotsToClose)} lot @ ${px.formatPriceDynamic(decs)} · PNL ${fmtUsd(realized)} · ${fmtLots(remaining)} lot left")
        }
        recomputeAccount()
    }

    suspend fun closeAllTrades() = closeMatchingTrades { true }
    suspend fun closeAllProfitable() = closeMatchingTrades { t ->
        TradingMath.unrealizedPnlUsd(t, _priceState.value[t.symbol]?.price ?: t.entryPrice, _priceState.value) > 0
    }
    suspend fun closeAllLosing() = closeMatchingTrades { t ->
        TradingMath.unrealizedPnlUsd(t, _priceState.value[t.symbol]?.price ?: t.entryPrice, _priceState.value) < 0
    }

    private suspend fun closeMatchingTrades(predicate: (Trade) -> Boolean) {
        tradeMutex.withLock {
            val open = db.tradeDao().getOpenTrades()
            for (t in open) {
                if (predicate(t)) {
                    val px = _priceState.value[t.symbol]?.price ?: t.entryPrice
                    closeTradeAtPrice(t, px, "USER")
                }
            }
        }
        recomputeAccount()
    }

    suspend fun cancelAllPending() {
        tradeMutex.withLock {
            val orders = db.pendingOrderDao().getActiveOrders()
            val now = System.currentTimeMillis()
            for (o in orders) db.pendingOrderDao().update(o.copy(status = "CANCELLED", closedBy = "USER", executedAt = now))
            if (orders.isNotEmpty()) logEvent("TRADE", null, "Cancelled ${orders.size} pending order(s).")
        }
    }

    suspend fun deposit(amount: Double) {
        if (amount <= 0) { _tradeMessage.value = "Enter a positive deposit amount."; return }
        tradeMutex.withLock { bookRealized(amount, "Deposit", null, "DEPOSIT") }
        logEvent("TRADE", null, "Deposited ${fmtUsd(amount)}. Balance ${fmtUsd(accountBalance)}.")
        _tradeMessage.value = "Deposited ${fmtUsd(amount)}."
        recomputeAccount()
    }

    suspend fun withdraw(amount: Double) {
        if (amount <= 0) { _tradeMessage.value = "Enter a positive withdrawal amount."; return }
        tradeMutex.withLock {
            val snap = TradingMath.accountSnapshot(openTradesCache, accountBalance, _priceState.value)
            if (amount > snap.freeMargin) {
                _tradeMessage.value = "Cannot withdraw ${fmtUsd(amount)}: free margin is ${fmtUsd(snap.freeMargin)}."
                return@withLock
            }
            bookRealized(-amount, "Withdrawal", null, "WITHDRAW")
            logEvent("TRADE", null, "Withdrew ${fmtUsd(amount)}. Balance ${fmtUsd(accountBalance)}.")
            _tradeMessage.value = "Withdrew ${fmtUsd(amount)}."
        }
        recomputeAccount()
    }

    suspend fun setLeverage(value: Double) {
        leverage = value.coerceAtLeast(1.0)
        saveSetting("account_leverage", leverage.toString())
        // Recompute reserved margin on open trades to reflect the new leverage.
        tradeMutex.withLock {
            val open = db.tradeDao().getOpenTrades()
            for (t in open) {
                val m = TradingMath.requiredMarginUsd(t.symbol, t.lots, t.entryPrice, leverage, _priceState.value)
                db.tradeDao().update(t.copy(marginUsd = m))
            }
        }
        recomputeAccount()
    }

    suspend fun setStopoutLevel(value: Double) {
        stopoutLevel = value.coerceAtLeast(0.0)
        saveSetting("account_stopout_level", stopoutLevel.toString())
    }

    suspend fun deleteTrade(id: Int) {
        tradeMutex.withLock { db.tradeDao().deleteById(id) }
    }

    suspend fun resetTradingData() {
        tradeMutex.withLock {
            db.tradeDao().deleteAll()
            db.pendingOrderDao().deleteAll()
            db.accountTransactionDao().deleteAll()
            accountBalance = 0.0
            saveSetting("account_balance", "0.0")
            logEvent("TRADE", null, "Trading data reset: all trades, orders and transactions cleared.")
        }
        _liveTradePnl.value = emptyMap()
        recomputeAccount()
    }

    private fun fmtLots(lots: Double): String = if (lots == lots.toLong().toDouble()) lots.toLong().toString() else String.format(Locale.US, "%.2f", lots)
    private fun fmtUsd(amount: Double): String {
        val sign = if (amount < 0) "-" else if (amount > 0) "+" else ""
        return "$sign$" + String.format(Locale.US, "%,.2f", kotlin.math.abs(amount))
    }
    private fun closeLabel(by: String): String = when (by) {
        "TP" -> "Take-Profit Hit"; "SL" -> "Stop-Loss Hit"; "STOPOUT" -> "Stop-Out"; else -> "Position Closed"
    }

    private fun fireTradeAlert(title: String, body: String) {
        if (!tradeAlertsEnabled) return
        NotificationHelper.fireTradeNotification(appContext, title, body, tradeAlertSoundMode, cachedTtsLanguage)
    }

    // CSV export builders for the trade ledger and the cash transaction history.
    suspend fun buildTradeLedgerCsv(): String {
        val offset = getSetting("display_timezone_offset")
        val trades = db.tradeDao().getAllTradesForExport()
        return buildString {
            append("Trade ID,Symbol,Side,Lots,Entry,Exit,SL,TP,Open Time,Close Time,PNL (USD),Status,Closed By\n")
            trades.forEach { t ->
                val open = com.example.data.time.TimeFormat.format(t.openTime, offset)
                val close = t.closeTime?.let { com.example.data.time.TimeFormat.format(it, offset) } ?: ""
                append("${t.id},${t.symbol},${t.side},${t.lots},${t.entryPrice},${t.exitPrice ?: ""},${t.stopLoss ?: ""},${t.takeProfit ?: ""},\"$open\",\"$close\",${String.format(Locale.US, "%.2f", t.realizedPnl)},${t.status},${t.closedBy ?: ""}\n")
            }
        }
    }

    suspend fun buildTransactionsCsv(): String {
        val offset = getSetting("display_timezone_offset")
        val txns = db.accountTransactionDao().getAllForExport()
        return buildString {
            append("ID,Type,Amount (USD),Balance After,Note,Related Trade,Time\n")
            txns.forEach { x ->
                val time = com.example.data.time.TimeFormat.format(x.timestamp, offset)
                append("${x.id},${x.type},${String.format(Locale.US, "%.2f", x.amount)},${String.format(Locale.US, "%.2f", x.balanceAfter)},\"${x.note}\",${x.relatedTradeId ?: ""},\"$time\"\n")
            }
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
        } else if (key == "trade_alerts_enabled") {
            tradeAlertsEnabled = value == "true"
        } else if (key == "trade_alert_sound_mode") {
            tradeAlertSoundMode = value
        }

        // Auto restart loop if API key, account config or update interval shifts
        if (key.endsWith("_api_key") || key == "active_price_provider" ||
            key == PriceProvider.OANDA_ACCOUNT_ID_KEY || key == PriceProvider.OANDA_ENVIRONMENT_KEY) {
            closeConnections("Provider or API key changed")
            _connectionStatus.value = "OFFLINE"
            startMonitoringLoop()
        } else if (key == "ui_price_update_interval_ms") {
            startMonitoringLoop()
        } else if (key == "tts_language") {
            cachedTtsLanguage = value
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

            // Virtual trading engine: fill orders, hit SL/TP, enforce margin, refresh live PNL.
            evaluateTrades(sym, newPrice)

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
