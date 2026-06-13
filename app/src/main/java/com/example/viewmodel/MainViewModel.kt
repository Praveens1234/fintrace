package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Alert
import com.example.data.model.PriceTick
import com.example.data.model.SymbolInfo
import com.example.data.model.SymbolState
import com.example.data.model.TriggerHistory
import com.example.data.repository.PriceMonitorManager
import com.example.service.NotificationHelper
import com.example.service.PriceTrackerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val monitor = PriceMonitorManager.getInstance(application)
    private val alertDao = monitor.db.alertDao()
    private val historyDao = monitor.db.triggerHistoryDao()
    private val symbolDao = monitor.db.symbolStateDao()
    private val settingsDao = monitor.db.appSettingDao()

    // ── DATA SOURCES EXPOSED AS REACTIVE FLOWS ──────────────────────────────
    val activeSymbols: StateFlow<List<String>> = monitor.activeSymbols
    val priceState: StateFlow<Map<String, PriceTick>> = monitor.priceState
    val connectionStatus: StateFlow<String> = monitor.connectionStatus
    val latencyMs: StateFlow<Long> = monitor.latencyMs

    // Market session state for the UI (closed banners, "opens in …" countdown).
    val marketOpen: StateFlow<Boolean> = monitor.marketOpen
    val nextMarketChangeAt: StateFlow<Long> = monitor.nextMarketChangeAt

    // Live Alert List Flow
    val alertList: StateFlow<List<Alert>> = alertDao.getAllAlertsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live Trigger History Flow
    val triggerHistory: StateFlow<List<TriggerHistory>> = historyDao.getAllHistoryFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All symbols states configuration
    val symbolStates: StateFlow<List<SymbolState>> = symbolDao.getAllSymbolStatesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Real-time app logs flow
    val allLogs: StateFlow<List<com.example.data.model.AppLog>> = monitor.db.appLogDao().getAllLogsFlow()
        .combine(monitor.tickLogs) { dbLogs, memLogs ->
            memLogs + dbLogs
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // State of key settings
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _isEnvApiKeyActive = MutableStateFlow(false)
    val isEnvApiKeyActive: StateFlow<Boolean> = _isEnvApiKeyActive.asStateFlow()

    private val _priceUpdateIntervalMs = MutableStateFlow(500L)
    val priceUpdateIntervalMs: StateFlow<Long> = _priceUpdateIntervalMs.asStateFlow()

    private val _websocketUseNativeMode = MutableStateFlow(true)
    val websocketUseNativeMode: StateFlow<Boolean> = _websocketUseNativeMode.asStateFlow()

    private val _dashboardCardStyle = MutableStateFlow("Standard") // Standard, Compact, Classic Row
    val dashboardCardStyle: StateFlow<String> = _dashboardCardStyle.asStateFlow()

    private val _priceTextSize = MutableStateFlow(0f)
    val priceTextSize: StateFlow<Float> = _priceTextSize.asStateFlow()

    private val _symbolIdTextSize = MutableStateFlow(0f)
    val symbolIdTextSize: StateFlow<Float> = _symbolIdTextSize.asStateFlow()

    private val _symbolNameTextSize = MutableStateFlow(0f)
    val symbolNameTextSize: StateFlow<Float> = _symbolNameTextSize.asStateFlow()

    private val _themeMode = MutableStateFlow("AMOLED") // Light, AMOLED, System
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()
    
    private val _tickLoggingEnabled = MutableStateFlow(true)
    val tickLoggingEnabled: StateFlow<Boolean> = _tickLoggingEnabled.asStateFlow()

    private val _hapticFeedbackEnabled = MutableStateFlow(true)
    val hapticFeedbackEnabled: StateFlow<Boolean> = _hapticFeedbackEnabled.asStateFlow()

    private val _autoStartOnBoot = MutableStateFlow(true)
    val autoStartOnBoot: StateFlow<Boolean> = _autoStartOnBoot.asStateFlow()

    private val _connectionLossThreshold = MutableStateFlow("30s") // 30s, 1m, 5m, 10m
    val connectionLossThreshold: StateFlow<String> = _connectionLossThreshold.asStateFlow()

    private val _liveTickerSymbols = MutableStateFlow<List<String>>(emptyList())
    val liveTickerSymbols: StateFlow<List<String>> = _liveTickerSymbols.asStateFlow()

    private val _pricePrecisionOverride = MutableStateFlow("MAX")
    val pricePrecisionOverride: StateFlow<String> = _pricePrecisionOverride.asStateFlow()

    private val _symbolDecimals = MutableStateFlow<Map<String, Int>>(emptyMap())
    val symbolDecimals: StateFlow<Map<String, Int>> = _symbolDecimals.asStateFlow()

    private val _alertSoundUri = MutableStateFlow("")
    val alertSoundUri: StateFlow<String> = _alertSoundUri.asStateFlow()

    private val _alertSoundTitle = MutableStateFlow("Default System Tone")
    val alertSoundTitle: StateFlow<String> = _alertSoundTitle.asStateFlow()

    private val _alertRingDurationSec = MutableStateFlow(5)
    val alertRingDurationSec: StateFlow<Int> = _alertRingDurationSec.asStateFlow()

    private val _alertSoundMode = MutableStateFlow("Both Tone and Voice")
    val alertSoundMode: StateFlow<String> = _alertSoundMode.asStateFlow()

    private val _prioritySoundUris = MutableStateFlow<Map<String, String>>(emptyMap())
    val prioritySoundUris = _prioritySoundUris.asStateFlow()

    private val _prioritySoundTitles = MutableStateFlow<Map<String, String>>(emptyMap())
    val prioritySoundTitles = _prioritySoundTitles.asStateFlow()

    private val _priorityRingDurations = MutableStateFlow<Map<String, Int>>(emptyMap())
    val priorityRingDurations = _priorityRingDurations.asStateFlow()

    private val _prioritySoundModes = MutableStateFlow<Map<String, String>>(emptyMap())
    val prioritySoundModes = _prioritySoundModes.asStateFlow()

    init {
        // Start background FGS automatically on launch
        PriceTrackerService.start(application)

        // Load settings values
        viewModelScope.launch(Dispatchers.IO) {
            val dbKey = monitor.getSetting("twelve_data_api_key") ?: ""
            _apiKey.value = dbKey
            val effectiveKey = monitor.getEffectiveApiKey()
            _isEnvApiKeyActive.value = dbKey.isBlank() && !effectiveKey.isNullOrBlank()
            _priceUpdateIntervalMs.value = monitor.getUiPriceIntervalSetting()
            _websocketUseNativeMode.value = (monitor.getSetting("websocket_use_native_mode") ?: "true") == "true"
            _dashboardCardStyle.value = monitor.getSetting("dashboard_card_style") ?: "Standard"
            _priceTextSize.value = monitor.getSetting("price_text_size")?.toFloatOrNull() ?: 0f
            _symbolIdTextSize.value = monitor.getSetting("symbol_id_text_size")?.toFloatOrNull() ?: 0f
            _symbolNameTextSize.value = monitor.getSetting("symbol_name_text_size")?.toFloatOrNull() ?: 0f
            _themeMode.value = monitor.getSetting("theme_mode") ?: "AMOLED"
            _tickLoggingEnabled.value = (monitor.getSetting("tick_logging_enabled") ?: "true") == "true"
            _hapticFeedbackEnabled.value = (monitor.getSetting("haptic_feedback_enabled") ?: "true") == "true"
            _autoStartOnBoot.value = (monitor.getSetting("auto_start_on_boot") ?: "true") == "true"
            _connectionLossThreshold.value = monitor.getSetting("connection_loss_threshold") ?: "30s"
            
            val liveTickerRaw = monitor.getSetting("live_ticker_symbols") ?: "XAU/USD,EUR/USD"
            _liveTickerSymbols.value = liveTickerRaw.split(",").filter { it.isNotBlank() }

            val precisionRaw = monitor.getSetting("price_precision_override") ?: "MAX"
            _pricePrecisionOverride.value = precisionRaw
            com.example.data.model.PricePrecisionConfig.maxPrecision = precisionRaw.toIntOrNull()

            val customDecs = mutableMapOf<String, Int>()
            SymbolInfo.ALL.forEach { s ->
                val key = "price_precision_override_${s.symbol.uppercase()}"
                val overrideRaw = monitor.getSetting(key)
                val overrideInt = overrideRaw?.toIntOrNull()
                com.example.data.model.PricePrecisionConfig.setOverride(s.symbol, overrideInt)
                if (overrideInt != null) {
                    customDecs[s.symbol.uppercase()] = overrideInt
                }
            }
            _symbolDecimals.value = customDecs

            _alertSoundUri.value = monitor.getSetting("alert_sound_uri") ?: ""
            _alertSoundTitle.value = monitor.getSetting("alert_sound_title") ?: "Default System Tone"
            _alertRingDurationSec.value = monitor.getSetting("alert_ring_duration_sec")?.toIntOrNull() ?: 5
            _alertSoundMode.value = monitor.getSetting("alert_sound_mode") ?: "Both Tone and Voice"

            val pUris = mutableMapOf<String, String>()
            val pTitles = mutableMapOf<String, String>()
            val pDurations = mutableMapOf<String, Int>()
            val pModes = mutableMapOf<String, String>()

            listOf("low", "medium", "high", "critical").forEach { p ->
                pUris[p] = monitor.getSetting("alert_sound_uri_$p") ?: ""
                pTitles[p] = monitor.getSetting("alert_sound_title_$p") ?: ""
                pDurations[p] = monitor.getSetting("alert_ring_duration_sec_$p")?.toIntOrNull() ?: 5
                pModes[p] = monitor.getSetting("alert_sound_mode_$p") ?: ""
            }

            _prioritySoundUris.value = pUris
            _prioritySoundTitles.value = pTitles
            _priorityRingDurations.value = pDurations
            _prioritySoundModes.value = pModes
        }
    }

    // ── ALERT CONTROL MUTATIONS ────────────────────────────────────────────
    fun createAlert(
        symbol: String,
        condition: String,
        targetPrice: Double,
        title: String,
        message: String,
        isOneTime: Boolean,
        priority: String,
        colorTagIndex: Int,
        cooldownDurationMs: Long = 300000L,
        expiry: Long? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val alert = Alert(
                symbol = symbol,
                condition = condition,
                targetPrice = targetPrice,
                title = title,
                message = message,
                isActive = true,
                isOneTime = isOneTime,
                priority = priority,
                colorTagIndex = colorTagIndex,
                cooldownDurationMs = cooldownDurationMs,
                expiry = expiry
            )
            alertDao.insertAlert(alert)
        }
    }

    fun updateAlert(
        id: Int,
        symbol: String,
        condition: String,
        targetPrice: Double,
        title: String,
        message: String,
        isActive: Boolean,
        isOneTime: Boolean,
        priority: String,
        cooldownDurationMs: Long = 300000L,
        expiry: Long? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = alertDao.getAlertById(id)
            if (existing != null) {
                val updated = existing.copy(
                    symbol = symbol,
                    condition = condition,
                    targetPrice = targetPrice,
                    title = title,
                    message = message,
                    isActive = isActive,
                    isOneTime = isOneTime,
                    priority = priority,
                    cooldownDurationMs = cooldownDurationMs,
                    expiry = expiry
                )
                alertDao.updateAlert(updated)
            }
        }
    }

    fun toggleAlertActive(alertId: Int, isActive: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            alertDao.updateAlertActiveStatus(alertId, isActive)
        }
    }

    fun deleteAlert(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            alertDao.deleteAlertById(id)
            historyDao.deleteHistoryForAlert(id)
        }
    }

    // ── BULK MANAGEMENT METHODS ───────────────────────────────────────────
    fun activateAllAlerts() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = alertActiveStateList()
            list.forEach { alert ->
                alertDao.updateAlertActiveStatus(alert.id, true)
            }
        }
    }

    fun deactivateAllAlerts() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = alertActiveStateList()
            list.forEach { alert ->
                alertDao.updateAlertActiveStatus(alert.id, false)
            }
        }
    }

    fun deleteAllAlerts() {
        viewModelScope.launch(Dispatchers.IO) {
            alertDao.deleteAllAlerts()
            historyDao.clearAllHistory()
        }
    }

    private suspend fun alertActiveStateList(): List<Alert> = withContext(Dispatchers.IO) {
        alertDao.getAllAlerts()
    }

    // ── SYMBOL CONFIG EVENTS ─────────────────────────────────────────────
    fun toggleSymbolActive(symbol: String, isActive: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = symbolDao.getAllSymbolStates()
            val match = list.find { it.symbol == symbol }
            if (match != null) {
                symbolDao.updateSymbolState(match.copy(isActive = isActive))
            } else {
                val newState = SymbolState(symbol, isActive, list.size)
                symbolDao.insertSymbolStates(listOf(newState))
            }
        }
    }

    // ── SETTINGS MUTATORS ─────────────────────────────────────────────────
    fun saveApiKey(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _apiKey.value = key
            monitor.saveSetting("twelve_data_api_key", key)
            val effectiveKey = monitor.getEffectiveApiKey()
            _isEnvApiKeyActive.value = key.isBlank() && !effectiveKey.isNullOrBlank()
        }
    }

    fun savePriceUpdateInterval(intervalMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val validated = intervalMs.coerceIn(100L, 10000L)
            _priceUpdateIntervalMs.value = validated
            monitor.saveSetting("ui_price_update_interval_ms", validated.toString())
        }
    }

    fun saveWebsocketUseNativeMode(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _websocketUseNativeMode.value = enabled
            monitor.saveSetting("websocket_use_native_mode", enabled.toString())
            monitor.logEvent("SYSTEM", null, "Updated Native Tick Streaming mode: $enabled")
        }
    }

    fun saveDashboardCardStyle(style: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _dashboardCardStyle.value = style
            monitor.saveSetting("dashboard_card_style", style)
        }
    }

    fun savePriceTextSize(size: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            _priceTextSize.value = size
            monitor.saveSetting("price_text_size", size.toString())
        }
    }

    fun saveSymbolIdTextSize(size: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            _symbolIdTextSize.value = size
            monitor.saveSetting("symbol_id_text_size", size.toString())
        }
    }

    fun saveSymbolNameTextSize(size: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            _symbolNameTextSize.value = size
            monitor.saveSetting("symbol_name_text_size", size.toString())
        }
    }

    fun resetTextualSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            _priceTextSize.value = 0f
            _symbolIdTextSize.value = 0f
            _symbolNameTextSize.value = 0f
            monitor.saveSetting("price_text_size", "0.0")
            monitor.saveSetting("symbol_id_text_size", "0.0")
            monitor.saveSetting("symbol_name_text_size", "0.0")
        }
    }

    fun saveThemeMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _themeMode.value = mode
            monitor.saveSetting("theme_mode", mode)
        }
    }

    fun savePricePrecisionOverride(precision: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _pricePrecisionOverride.value = precision
            com.example.data.model.PricePrecisionConfig.maxPrecision = precision.toIntOrNull()
            monitor.saveSetting("price_precision_override", precision)

            // Trigger widget and notification updates so the new layout applies globally immediately
            val currentPrices = monitor.priceState.value
            val liveSymbols = monitor.liveTickerSymbols.value
            com.example.service.NotificationHelper.updateTickerNotification(getApplication(), currentPrices, liveSymbols)
            com.example.service.PriceWidgetProvider.updateWidgets(getApplication(), currentPrices)
            com.example.service.PriceWidgetSingleProvider.updateWidgets(getApplication(), currentPrices)
            com.example.service.PriceWidgetFiveProvider.updateWidgets(getApplication(), currentPrices)
        }
    }

    fun saveSymbolDecimalOverride(symbol: String, decimals: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            val key = "price_precision_override_${symbol.upperCaseSymbol()}"
            if (decimals == null) {
                _symbolDecimals.update { old ->
                    old.toMutableMap().apply { remove(symbol.upperCaseSymbol()) }
                }
                settingsDao.deleteSetting(key)
                com.example.data.model.PricePrecisionConfig.setOverride(symbol, null)
            } else {
                val valCoerced = decimals.coerceIn(1, 9)
                _symbolDecimals.update { old ->
                    old.toMutableMap().apply { put(symbol.upperCaseSymbol(), valCoerced) }
                }
                settingsDao.insertSetting(com.example.data.model.AppSetting(key, valCoerced.toString()))
                com.example.data.model.PricePrecisionConfig.setOverride(symbol, valCoerced)
            }

            // Trigger widget and notification updates so the new layout applies globally immediately
            val currentPrices = monitor.priceState.value
            val liveSymbols = monitor.liveTickerSymbols.value
            com.example.service.NotificationHelper.updateTickerNotification(getApplication(), currentPrices, liveSymbols)
            com.example.service.PriceWidgetProvider.updateWidgets(getApplication(), currentPrices)
            com.example.service.PriceWidgetSingleProvider.updateWidgets(getApplication(), currentPrices)
            com.example.service.PriceWidgetFiveProvider.updateWidgets(getApplication(), currentPrices)
        }
    }

    private fun String.upperCaseSymbol() = this.uppercase(Locale.ROOT)

    fun saveHapticFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _hapticFeedbackEnabled.value = enabled
            monitor.saveSetting("haptic_feedback_enabled", enabled.toString())
        }
    }

    fun saveTickLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _tickLoggingEnabled.value = enabled
            monitor.setTickLoggingEnabled(enabled)
        }
    }

    fun saveAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _autoStartOnBoot.value = enabled
            monitor.saveSetting("auto_start_on_boot", enabled.toString())
        }
    }

    fun saveConnectionLossThreshold(threshold: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _connectionLossThreshold.value = threshold
            monitor.saveSetting("connection_loss_threshold", threshold)
        }
    }

    fun saveLiveTickerSymbols(symbols: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            _liveTickerSymbols.value = symbols
            monitor.saveSetting("live_ticker_symbols", symbols.joinToString(","))
        }
    }

    fun saveAlertSoundUri(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _alertSoundUri.value = uri
            monitor.saveSetting("alert_sound_uri", uri)
        }
    }

    fun saveAlertSoundTitle(title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _alertSoundTitle.value = title
            monitor.saveSetting("alert_sound_title", title)
        }
    }

    fun saveAlertRingDurationSec(duration: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val validated = duration.coerceIn(1, 120)
            _alertRingDurationSec.value = validated
            monitor.saveSetting("alert_ring_duration_sec", validated.toString())
        }
    }

    fun saveAlertSoundMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _alertSoundMode.value = mode
            monitor.saveSetting("alert_sound_mode", mode)
        }
    }

    fun savePrioritySoundUri(priority: String, uri: String) {
        val p = priority.lowercase(Locale.US)
        viewModelScope.launch(Dispatchers.IO) {
            _prioritySoundUris.update { it + (p to uri) }
            monitor.saveSetting("alert_sound_uri_$p", uri)
        }
    }

    fun savePrioritySoundTitle(priority: String, title: String) {
        val p = priority.lowercase(Locale.US)
        viewModelScope.launch(Dispatchers.IO) {
            _prioritySoundTitles.update { it + (p to title) }
            monitor.saveSetting("alert_sound_title_$p", title)
        }
    }

    fun savePriorityRingDurationSec(priority: String, duration: Int) {
        val p = priority.lowercase(Locale.US)
        val validated = duration.coerceIn(1, 120)
        viewModelScope.launch(Dispatchers.IO) {
            _priorityRingDurations.update { it + (p to validated) }
            monitor.saveSetting("alert_ring_duration_sec_$p", validated.toString())
        }
    }

    fun savePrioritySoundMode(priority: String, mode: String) {
        val p = priority.lowercase(Locale.US)
        viewModelScope.launch(Dispatchers.IO) {
            _prioritySoundModes.update { it + (p to mode) }
            monitor.saveSetting("alert_sound_mode_$p", mode)
        }
    }

    fun saveSettingGeneric(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            monitor.saveSetting(key, value)
        }
    }

    fun importAlertsFromJson(jsonStr: String): Boolean {
        return try {
            val root = org.json.JSONObject(jsonStr)
            val arr = root.getJSONArray("rules")
            viewModelScope.launch(Dispatchers.IO) {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val symbol = item.getString("symbol")
                    val condition = item.getString("condition")
                    val targetPrice = item.getDouble("targetPrice")
                    val title = item.optString("title", "$symbol Crossed Target")
                    val message = item.optString("message", "Target price crossed at $targetPrice")
                    val isOneTime = item.optBoolean("isOneTime", true)
                    val priority = item.optString("priority", "HIGH")
                    val colorTagIndex = item.optInt("colorTagIndex", 0)
                    val cooldownDurationMs = item.optLong("cooldownDurationMs", 300000L)
                    val expiry = if (item.has("expiry")) item.getLong("expiry") else null

                    val alert = Alert(
                        symbol = symbol,
                        condition = condition,
                        targetPrice = targetPrice,
                        title = title,
                        message = message,
                        isActive = true,
                        isOneTime = isOneTime,
                        priority = priority,
                        colorTagIndex = colorTagIndex,
                        cooldownDurationMs = cooldownDurationMs,
                        expiry = expiry
                    )
                    alertDao.insertAlert(alert)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── DIAGNOSTIC CLEANERS ────────────────────────────────────────────────
    fun clearTriggerHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.clearAllHistory()
        }
    }

    fun resetAllSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDao.clearSettings()
            _apiKey.value = ""
            _priceUpdateIntervalMs.value = 500L
            _dashboardCardStyle.value = "Standard"
            _themeMode.value = "AMOLED"
            _hapticFeedbackEnabled.value = true
            _autoStartOnBoot.value = true
            _connectionLossThreshold.value = "30s"
            _liveTickerSymbols.value = listOf("XAU/USD", "EUR/USD")
            _pricePrecisionOverride.value = "MAX"
            _priceTextSize.value = 0f
            _symbolIdTextSize.value = 0f
            _symbolNameTextSize.value = 0f
            com.example.data.model.PricePrecisionConfig.maxPrecision = null
            
            // Reinitialize empty settings in database
            monitor.saveSetting("theme_mode", "AMOLED")
            monitor.saveSetting("ui_price_update_interval_ms", "500")
            monitor.saveSetting("live_ticker_symbols", "XAU/USD,EUR/USD")
            monitor.saveSetting("price_precision_override", "MAX")
            monitor.saveSetting("price_text_size", "0.0")
            monitor.saveSetting("symbol_id_text_size", "0.0")
            monitor.saveSetting("symbol_name_text_size", "0.0")
        }
    }

    val storageInfo = MutableStateFlow(monitor.getDatabaseStorageInfo())

    fun updateStorageInfo() {
        storageInfo.value = monitor.getDatabaseStorageInfo()
    }

    // ── DEMORE ALERT TESTING DISPATCH ──────────────────────────────────────
    fun testDemoAlert(method: String) {
        viewModelScope.launch {
            NotificationHelper.fireDemoAlertNotification(getApplication(), method)
        }
    }

    fun logEvent(type: String, message: String) {
        monitor.logEvent(type, null, message)
        updateStorageInfo()
    }

    fun clearAllLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            monitor.clearTickLogs()
            monitor.db.appLogDao().clearAllLogs()
            try {
                monitor.db.openHelper.writableDatabase.execSQL("VACUUM")
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "VACUUM failed in clearAllLogs: ${e.message}")
            }
            monitor.logEvent("SYSTEM", null, "Logs cleared by user")
            updateStorageInfo()
        }
    }

    suspend fun getAllLogsForExport(): List<com.example.data.model.AppLog> {
        val dbLogs = monitor.db.appLogDao().getAllLogsForExport()
        val memLogs = monitor.tickLogs.value
        return (dbLogs + memLogs).sortedByDescending { it.timestamp }
    }
}
