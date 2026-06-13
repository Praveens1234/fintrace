package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SymbolInfo
import com.example.data.provider.PriceProvider
import com.example.ui.theme.AlertCritical
import com.example.ui.theme.ConnectionLive
import com.example.ui.theme.Radius
import com.example.ui.theme.Spacing
import com.example.viewmodel.MainViewModel

// ─── Private helpers ──────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = Spacing.xs)
    )
}

@Composable
private fun SettingCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = content
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Spacing.sm)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        control()
    }
}

// ─── Main Screen ──────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateToAboutApp: () -> Unit,
    onNavigateToAboutDeveloper: () -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    val apiKey by viewModel.apiKey.collectAsState()
    val isEnvApiKeyActive by viewModel.isEnvApiKeyActive.collectAsState()
    val activeProvider by viewModel.activeProvider.collectAsState()
    val finnhubApiKey by viewModel.finnhubApiKey.collectAsState()
    val alphaVantageApiKey by viewModel.alphaVantageApiKey.collectAsState()
    val ttsLanguage by viewModel.ttsLanguage.collectAsState()
    val updateInterval by viewModel.priceUpdateIntervalMs.collectAsState()
    val websocketUseNativeMode by viewModel.websocketUseNativeMode.collectAsState()
    val cardStyle by viewModel.dashboardCardStyle.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val hapticEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val autoStart by viewModel.autoStartOnBoot.collectAsState()
    val liveTickerSub by viewModel.liveTickerSymbols.collectAsState()
    val activePortfolioSymbols by viewModel.activeSymbols.collectAsState()
    val alerts by viewModel.alertList.collectAsState()
    val pricePrecision by viewModel.pricePrecisionOverride.collectAsState()
    val priceTextSizeSetting by viewModel.priceTextSize.collectAsState()
    val symbolIdTextSizeSetting by viewModel.symbolIdTextSize.collectAsState()
    val symbolNameTextSizeSetting by viewModel.symbolNameTextSize.collectAsState()

    val alertSoundUri by viewModel.alertSoundUri.collectAsState()
    val alertSoundTitle by viewModel.alertSoundTitle.collectAsState()
    val alertRingDurationSec by viewModel.alertRingDurationSec.collectAsState()
    val alertSoundMode by viewModel.alertSoundMode.collectAsState()

    val prioritySoundUris by viewModel.prioritySoundUris.collectAsState()
    val prioritySoundTitles by viewModel.prioritySoundTitles.collectAsState()
    val priorityRingDurations by viewModel.priorityRingDurations.collectAsState()
    val prioritySoundModes by viewModel.prioritySoundModes.collectAsState()

    var selectedScope by remember { mutableStateOf("Global") }
    var editingScopeForRingtone by remember { mutableStateOf("Global") }

    val context = androidx.compose.ui.platform.LocalContext.current

    val ringtonePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                @Suppress("DEPRECATION")
                val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
                } else {
                    result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                }
                if (uri != null) {
                    val uriStr = uri.toString()
                    val title = android.media.RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Custom Selected Tone"
                    if (editingScopeForRingtone == "Global") {
                        viewModel.saveAlertSoundUri(uriStr)
                        viewModel.saveAlertSoundTitle(title)
                    } else {
                        viewModel.savePrioritySoundUri(editingScopeForRingtone, uriStr)
                        viewModel.savePrioritySoundTitle(editingScopeForRingtone, title)
                    }
                } else {
                    if (editingScopeForRingtone == "Global") {
                        viewModel.saveAlertSoundUri("")
                        viewModel.saveAlertSoundTitle("Default System Tone")
                    } else {
                        viewModel.savePrioritySoundUri(editingScopeForRingtone, "")
                        viewModel.savePrioritySoundTitle(editingScopeForRingtone, "")
                    }
                }
            }
        }
    )

    val customFilePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                val uriStr = uri.toString()
                var title = "Custom File"
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) title = cursor.getString(nameIndex)
                    }
                } catch (e: Exception) {
                    title = "Selected Custom Audio File"
                }
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                if (editingScopeForRingtone == "Global") {
                    viewModel.saveAlertSoundUri(uriStr)
                    viewModel.saveAlertSoundTitle(title)
                } else {
                    viewModel.savePrioritySoundUri(editingScopeForRingtone, uriStr)
                    viewModel.savePrioritySoundTitle(editingScopeForRingtone, title)
                }
            }
        }
    )

    var showSoundSourceDialog by remember { mutableStateOf(false) }
    var billingExpanded by remember { mutableStateOf(false) }
    var defaultsExpanded by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var textualSizeExpanded by remember { mutableStateOf(false) }
    var dataStorageExpanded by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        contentPadding = PaddingValues(vertical = Spacing.md)
    ) {

        // ── I: Connection & Market Feed ───────────────────────────────────────
        item {
            SettingsGroupHeader(
                "Connection & Market Feed",
                Icons.Default.Sync,
                billingExpanded
            ) { billingExpanded = !billingExpanded }

            AnimatedVisibility(visible = billingExpanded) {
                SettingCard {
                    SectionLabel("DATA PROVIDER")

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        PriceProvider.entries.forEach { provider ->
                            val isSelected = activeProvider == provider.name
                            Surface(
                                onClick = { viewModel.saveActiveProvider(provider.name) },
                                shape = MaterialTheme.shapes.small,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(Spacing.sm),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.saveActiveProvider(provider.name) },
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Column(modifier = Modifier.padding(start = Spacing.xs)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                        ) {
                                            Text(
                                                provider.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                        else MaterialTheme.colorScheme.onSurface
                                            )
                                            Surface(
                                                shape = MaterialTheme.shapes.extraSmall,
                                                color = if (provider.supportsWebSocket)
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    if (provider.supportsWebSocket) "WS" else "REST",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (provider.supportsWebSocket)
                                                        MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            provider.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Per-provider API key field
                    val selectedProvider = PriceProvider.entries.find { it.name == activeProvider }
                        ?: PriceProvider.TWELVE_DATA

                    SectionLabel("${selectedProvider.displayName.uppercase(java.util.Locale.US)} API KEY")

                    when (selectedProvider) {
                        PriceProvider.TWELVE_DATA -> {
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { viewModel.saveApiKey(it) },
                                label = { Text("Twelve Data API Key") },
                                placeholder = { Text("e.g. your_twelve_data_key") },
                                shape = MaterialTheme.shapes.small,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (isEnvApiKeyActive) {
                                Surface(
                                    color = ConnectionLive.copy(alpha = 0.10f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(Spacing.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, null, tint = ConnectionLive, modifier = Modifier.size(16.dp))
                                        Text("API key active via environment variable.", style = MaterialTheme.typography.bodySmall, color = ConnectionLive, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            } else {
                                Text("Get your free API key at twelvedata.com", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        PriceProvider.FINNHUB -> {
                            OutlinedTextField(
                                value = finnhubApiKey,
                                onValueChange = { viewModel.saveFinnhubApiKey(it) },
                                label = { Text("Finnhub API Key") },
                                placeholder = { Text("e.g. your_finnhub_key") },
                                shape = MaterialTheme.shapes.small,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Get your free API key at finnhub.io — includes real-time forex streaming.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PriceProvider.ALPHA_VANTAGE -> {
                            OutlinedTextField(
                                value = alphaVantageApiKey,
                                onValueChange = { viewModel.saveAlphaVantageApiKey(it) },
                                label = { Text("Alpha Vantage API Key") },
                                placeholder = { Text("e.g. your_av_key") },
                                shape = MaterialTheme.shapes.small,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                                    Text(
                                        "Free tier: 25 API calls/day, 5 calls/min. Prices update one symbol every ~13 s. Get key at alphavantage.co",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SectionLabel("STREAM SYNC MODE")

                    val streamOptions = listOf("Native Streaming", "Interval Throttle")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        streamOptions.forEachIndexed { index, option ->
                            val isNative = option == "Native Streaming"
                            SegmentedButton(
                                selected = if (isNative) websocketUseNativeMode else !websocketUseNativeMode,
                                onClick = { viewModel.saveWebsocketUseNativeMode(isNative) },
                                shape = SegmentedButtonDefaults.itemShape(index, streamOptions.size),
                                modifier = Modifier.testTag(if (isNative) "tick_mode_native_button" else "tick_mode_interval_button"),
                                enabled = selectedProvider.supportsWebSocket
                            ) {
                                Text(option, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    if (!selectedProvider.supportsWebSocket) {
                        Text(
                            "Stream sync mode is not applicable for REST polling providers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    } else if (websocketUseNativeMode) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                                Text("Native Mode: prices pushed in real-time, bypassing update intervals.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }

                    SettingRow(
                        title = "Price Update Interval",
                        subtitle = if (!selectedProvider.supportsWebSocket) "N/A — REST polling uses fixed 13 s cadence"
                                   else if (websocketUseNativeMode) "Disable Native Mode to configure"
                                   else "Throttle latency: 100 ms – 10 s",
                        enabled = selectedProvider.supportsWebSocket && !websocketUseNativeMode
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.alpha(if (selectedProvider.supportsWebSocket && !websocketUseNativeMode) 1f else 0.38f)
                        ) {
                            IconButton(
                                onClick = { viewModel.savePriceUpdateInterval(updateInterval - 50) },
                                enabled = selectedProvider.supportsWebSocket && !websocketUseNativeMode
                            ) {
                                Icon(Icons.Default.RemoveCircleOutline, null, modifier = Modifier.size(20.dp))
                            }
                            Text("$updateInterval ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                                modifier = Modifier.widthIn(min = 64.dp), textAlign = TextAlign.Center)
                            IconButton(
                                onClick = { viewModel.savePriceUpdateInterval(updateInterval + 50) },
                                enabled = selectedProvider.supportsWebSocket && !websocketUseNativeMode
                            ) {
                                Icon(Icons.Default.AddCircleOutline, null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Price Decimal Precision", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Limit displayed decimal places. 'MAX' preserves native precision.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Spacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("MAX", "1", "2", "3", "4", "5", "6", "7", "8", "9").forEach { mode ->
                                val isSel = pricePrecision == mode
                                FilterChip(
                                    selected = isSel,
                                    onClick = { viewModel.savePricePrecisionOverride(mode) },
                                    label = { Text(mode, style = MaterialTheme.typography.bodySmall, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SectionLabel("APPLICATION BEHAVIOR")

                    SettingRow(title = "Fluctuation Haptic Feedback", subtitle = "Discreet vibration on price tick updates") {
                        Switch(checked = hapticEnabled, onCheckedChange = { viewModel.saveHapticFeedbackEnabled(it) })
                    }

                    SettingRow(title = "Auto-Start on Boot", subtitle = "Restores the tracker automatically after reboot") {
                        Switch(checked = autoStart, onCheckedChange = { viewModel.saveAutoStartOnBoot(it) })
                    }
                }
            }
        }

        // ── II: Notifications & Speech Alerts ────────────────────────────────
        item {
            SettingsGroupHeader(
                "Notifications & Alerts",
                Icons.Default.Notifications,
                defaultsExpanded
            ) { defaultsExpanded = !defaultsExpanded }

            AnimatedVisibility(visible = defaultsExpanded) {
                val suffix = selectedScope.lowercase(java.util.Locale.US)
                val activeSoundMode = if (selectedScope == "Global") alertSoundMode
                    else prioritySoundModes[suffix]?.ifBlank { "" } ?: ""
                val activeSoundTitle = if (selectedScope == "Global") alertSoundTitle
                    else prioritySoundTitles[suffix]?.ifBlank { "" } ?: ""
                val activeSoundUri = if (selectedScope == "Global") alertSoundUri
                    else prioritySoundUris[suffix] ?: ""
                val activeRingDurationSec = if (selectedScope == "Global") alertRingDurationSec
                    else priorityRingDurations[suffix] ?: alertRingDurationSec

                SettingCard {
                    SectionLabel("NOTIFICATION SHADE LIVE TICKER")

                    SettingRow(
                        title = "Shade Live Price Ticker",
                        subtitle = "Shows selected symbols as a live ticker in the notification drawer"
                    ) {
                        val isEnabled = liveTickerSub.isNotEmpty()
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = {
                                if (it) {
                                    val initialSymbols = if (activePortfolioSymbols.isNotEmpty())
                                        activePortfolioSymbols.take(5)
                                    else listOf("XAU/USD", "EUR/USD")
                                    viewModel.saveLiveTickerSymbols(initialSymbols)
                                } else {
                                    viewModel.saveLiveTickerSymbols(emptyList())
                                }
                            }
                        )
                    }

                    if (liveTickerSub.isNotEmpty()) {
                        Text(
                            "Active Ticker Symbols (up to 5):",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRowLayout(modifier = Modifier.fillMaxWidth()) {
                            SymbolInfo.ALL.forEach { sym ->
                                val isChecked = liveTickerSub.contains(sym.symbol)
                                FilterChip(
                                    selected = isChecked,
                                    onClick = {
                                        val newList = liveTickerSub.toMutableList()
                                        if (isChecked) newList.remove(sym.symbol) else newList.add(sym.symbol)
                                        viewModel.saveLiveTickerSymbols(newList.take(5))
                                    },
                                    label = { Text(sym.symbol, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.padding(Spacing.xxs)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SectionLabel("SOUND & VOICE FEEDBACK")

                    Text(
                        "Configuration scope",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        listOf("Global", "LOW", "MEDIUM", "HIGH", "CRITICAL").forEach { scope ->
                            FilterChip(
                                selected = selectedScope == scope,
                                onClick = { selectedScope = scope },
                                label = {
                                    Text(
                                        scope,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (selectedScope == "Global")
                                "Editing default settings applied as baseline across all alerts."
                            else
                                "Editing override rules applied exclusively to $selectedScope priority alerts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        )
                    }

                    Text(
                        "Sound Playback Mode",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    val modeOptions = if (selectedScope == "Global") {
                        listOf("Both Tone and Voice", "Tone alert only", "TTS voice only", "Silent")
                    } else {
                        listOf("Inherit Global", "Both Tone and Voice", "Tone alert only", "TTS voice only", "Silent")
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        modeOptions.forEach { mode ->
                            val selected = if (selectedScope == "Global") {
                                activeSoundMode == mode
                            } else {
                                if (mode == "Inherit Global") activeSoundMode.isEmpty() else activeSoundMode == mode
                            }
                            val modeDesc = when (mode) {
                                "Inherit Global" -> "Uses global defaults (currently: ${alertSoundMode.ifBlank { "Default" }})"
                                "Both Tone and Voice" -> "Plays custom sound combined with spoken price announcement"
                                "Tone alert only" -> "Warning chime without TTS speech"
                                "TTS voice only" -> "Speaks the price target aloud, no chime"
                                "Silent" -> "Visual notification only — no audio"
                                else -> ""
                            }
                            Surface(
                                onClick = {
                                    val modeToSave = if (mode == "Inherit Global") "" else mode
                                    if (selectedScope == "Global") viewModel.saveAlertSoundMode(modeToSave)
                                    else viewModel.savePrioritySoundMode(suffix, modeToSave)
                                },
                                shape = MaterialTheme.shapes.small,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = {
                                            val modeToSave = if (mode == "Inherit Global") "" else mode
                                            if (selectedScope == "Global") viewModel.saveAlertSoundMode(modeToSave)
                                            else viewModel.savePrioritySoundMode(suffix, modeToSave)
                                        }
                                    )
                                    Column(modifier = Modifier.padding(start = Spacing.xs)) {
                                        Text(
                                            mode,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            modeDesc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        "Alert Ringtone",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        onClick = {
                            editingScopeForRingtone = selectedScope
                            showSoundSourceDialog = true
                        },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        "Selected sound",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (selectedScope != "Global" && activeSoundTitle.isEmpty())
                                            "Inherited: ${alertSoundTitle.ifBlank { "Default System Tone" }}"
                                        else activeSoundTitle.ifBlank { "Default System Tone" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            TextButton(onClick = {
                                editingScopeForRingtone = selectedScope
                                showSoundSourceDialog = true
                            }) {
                                Text("CHANGE", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    if (selectedScope != "Global" && activeSoundUri.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                viewModel.savePrioritySoundUri(suffix, "")
                                viewModel.savePrioritySoundTitle(suffix, "")
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                "Clear override — inherit Global",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Ringtone Duration Limit",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "$activeRingDurationSec s",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = activeRingDurationSec.toFloat(),
                        onValueChange = { v ->
                            val i = v.toInt()
                            if (selectedScope == "Global") viewModel.saveAlertRingDurationSec(i)
                            else viewModel.savePriorityRingDurationSec(suffix, i)
                        },
                        valueRange = 1f..60f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        listOf(2, 5, 10, 15, 30).forEach { sec ->
                            FilterChip(
                                selected = activeRingDurationSec == sec,
                                onClick = {
                                    if (selectedScope == "Global") viewModel.saveAlertRingDurationSec(sec)
                                    else viewModel.savePriorityRingDurationSec(suffix, sec)
                                },
                                label = { Text("${sec}s", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SectionLabel("DIAGNOSTIC TESTS")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.testDemoAlert("DEFAULT_NOTIFICATION") },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("Test Heads-up", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = { viewModel.testDemoAlert("ALARM") },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("Test Alarm", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SectionLabel("VOICE ANNOUNCEMENT LANGUAGE")

                    val ttsOptions = listOf("en-US" to "English", "hi-IN" to "हिन्दी")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ttsOptions.forEachIndexed { index, (tag, label) ->
                            SegmentedButton(
                                selected = ttsLanguage == tag,
                                onClick = { viewModel.saveTtsLanguage(tag) },
                                shape = SegmentedButtonDefaults.itemShape(index, ttsOptions.size)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Text(
                        "Language used for spoken alert announcements (TTS). Hindi requires Google TTS engine installed on device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── III: Customization & Theme ────────────────────────────────────────
        item {
            SettingsGroupHeader(
                "Customization & Theme",
                Icons.Default.Palette,
                appearanceExpanded
            ) { appearanceExpanded = !appearanceExpanded }

            AnimatedVisibility(visible = appearanceExpanded) {
                SettingCard {
                    SectionLabel("APPLICATION THEME")

                    val themeOptions = listOf("Light", "AMOLED", "System")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themeOptions.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { viewModel.saveThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, themeOptions.size)
                            ) {
                                Text(mode, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SectionLabel("DASHBOARD CARD STYLE")

                    val cardOptions = listOf("Standard", "Compact", "Classic Row")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        cardOptions.forEachIndexed { index, style ->
                            SegmentedButton(
                                selected = cardStyle == style,
                                onClick = { viewModel.saveDashboardCardStyle(style) },
                                shape = SegmentedButtonDefaults.itemShape(index, cardOptions.size)
                            ) {
                                Text(style, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // ── IV: Text Size Control ─────────────────────────────────────────────
        item {
            SettingsGroupHeader(
                "Text Size Control",
                Icons.Default.TextFormat,
                textualSizeExpanded
            ) { textualSizeExpanded = !textualSizeExpanded }

            AnimatedVisibility(visible = textualSizeExpanded) {
                SettingCard {
                    Text(
                        "Customize text sizes on the Prices dashboard. Set to the leftmost position (Auto) to use the system type scale.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TextSizeSlider(
                        label = "Price Text Size",
                        value = priceTextSizeSetting,
                        onValueChange = { viewModel.savePriceTextSize(it.toInt().toFloat()) },
                        max = 48f
                    )

                    TextSizeSlider(
                        label = "Symbol ID Text Size",
                        value = symbolIdTextSizeSetting,
                        onValueChange = { viewModel.saveSymbolIdTextSize(it.toInt().toFloat()) },
                        max = 32f
                    )

                    TextSizeSlider(
                        label = "Symbol Name Text Size",
                        value = symbolNameTextSizeSetting,
                        onValueChange = { viewModel.saveSymbolNameTextSize(it.toInt().toFloat()) },
                        max = 24f
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    OutlinedButton(
                        onClick = { viewModel.resetTextualSettings() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Reset Text Sizes to Default", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // ── V: Portfolio Backups & Resets ─────────────────────────────────────
        item {
            SettingsGroupHeader(
                "Portfolio Backups & Resets",
                Icons.Default.Backup,
                dataStorageExpanded
            ) { dataStorageExpanded = !dataStorageExpanded }

            AnimatedVisibility(visible = dataStorageExpanded) {
                SettingCard {
                    SectionLabel("PORTFOLIO DATA PROTECTION")

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedButton(
                            onClick = { showExportDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Export Rules", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Input, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Import Rules", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SectionLabel("STORAGE & RESETS")

                    OutlinedButton(
                        onClick = { viewModel.clearTriggerHistory() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Clear Trigger History Logs", style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = { viewModel.resetAllSettings() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlertCritical,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.SettingsBackupRestore, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Restore Preference Defaults", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // ── VI: Logs & Self-Healing Registry ──────────────────────────────────
        item {
            var diagnosticsExpanded by remember { mutableStateOf(false) }

            SettingsGroupHeader(
                "Logs & Self-Healing Registry",
                Icons.Default.Shield,
                diagnosticsExpanded
            ) { diagnosticsExpanded = !diagnosticsExpanded }

            AnimatedVisibility(visible = diagnosticsExpanded) {
                SettingCard {
                    SectionLabel("SYSTEM HEALING & AUTO PROTECTION")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        StatusChip(
                            label = "Self-Healing Core",
                            status = "ACTIVE",
                            statusColor = ConnectionLive,
                            modifier = Modifier.weight(1f)
                        )
                        StatusChip(
                            label = "Power & Space Shield",
                            status = "SECURE",
                            statusColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        "Intercepts and heals uncaught crashes, blocks telemetry overloads, and auto-prunes the cache database in real-time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    val tickLoggingEnabled by viewModel.tickLoggingEnabled.collectAsState()

                    SettingRow(
                        title = "In-Memory Ticker Logging",
                        subtitle = "Records real-time price updates to RAM cache. Disable to reduce battery usage."
                    ) {
                        Switch(
                            checked = tickLoggingEnabled,
                            onCheckedChange = { viewModel.saveTickLoggingEnabled(it) },
                            modifier = Modifier.scale(0.85f)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    var showInlineTerminalDialog by remember { mutableStateOf(false) }

                    Button(
                        onClick = { showInlineTerminalDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.Terminal, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("View Logs Registry", style = MaterialTheme.typography.labelMedium)
                    }

                    if (showInlineTerminalDialog) {
                        LogsScreenDialog(
                            viewModel = viewModel,
                            onDismiss = { showInlineTerminalDialog = false }
                        )
                    }
                }
            }
        }

        // ── VII: General & Device Diagnosis ───────────────────────────────────
        item {
            SettingsGroupHeader(
                "General & Device Diagnosis",
                Icons.Default.Settings,
                aboutExpanded
            ) { aboutExpanded = !aboutExpanded }

            AnimatedVisibility(visible = aboutExpanded) {
                SettingCard(modifier = Modifier.padding(0.dp)) {
                    NavSettingRow(
                        icon = Icons.Default.Info,
                        title = "About FinTrace",
                        subtitle = "Stack profile, licensing, build diagnostics",
                        onClick = onNavigateToAboutApp
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md)
                    )
                    NavSettingRow(
                        icon = Icons.Default.Person,
                        title = "About Developer",
                        subtitle = "Open-source credits and contact channels",
                        onClick = onNavigateToAboutDeveloper
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md)
                    )
                    NavSettingRow(
                        icon = Icons.Default.VerifiedUser,
                        title = "System Permissions",
                        subtitle = "Battery exemptions, alarm sync verification",
                        onClick = onNavigateToPermissions
                    )
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showExportDialog) {
        val backupJson = remember(alerts) {
            try {
                val rootObj = org.json.JSONObject()
                rootObj.put("app", "FinTrace")
                rootObj.put("export_version", 1)
                rootObj.put("timestamp", System.currentTimeMillis())
                val rulesArray = org.json.JSONArray()
                alerts.forEach { alert ->
                    val alertObj = org.json.JSONObject()
                    alertObj.put("symbol", alert.symbol)
                    alertObj.put("condition", alert.condition)
                    alertObj.put("targetPrice", alert.targetPrice)
                    alertObj.put("title", alert.title)
                    alertObj.put("message", alert.message)
                    alertObj.put("isActive", alert.isActive)
                    alertObj.put("isOneTime", alert.isOneTime)
                    alertObj.put("priority", alert.priority)
                    alertObj.put("colorTagIndex", alert.colorTagIndex)
                    alertObj.put("cooldownDurationMs", alert.cooldownDurationMs)
                    if (alert.expiry != null) alertObj.put("expiry", alert.expiry)
                    rulesArray.put(alertObj)
                }
                rootObj.put("rules", rulesArray)
                rootObj.toString(2)
            } catch (e: Exception) {
                "{\"app\":\"FinTrace\",\"export_version\":1,\"rules\":[]}"
            }
        }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Done") }
            },
            title = { Text("Export Alert Rules") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        "Copy the JSON below to back up your alert rules:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = backupJson,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        shape = MaterialTheme.shapes.small,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(backupJson))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Copy to Clipboard", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        )
    }

    if (showImportDialog) {
        var importInput by remember { mutableStateOf("") }
        var isSuccess by remember { mutableStateOf<Boolean?>(null) }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val ok = viewModel.importAlertsFromJson(importInput)
                        isSuccess = ok
                        if (ok) showImportDialog = false
                    },
                    shape = MaterialTheme.shapes.small
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            },
            title = { Text("Import Alert Rules") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        "Paste your exported JSON to restore alert triggers:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = importInput,
                        onValueChange = {
                            importInput = it
                            isSuccess = null
                        },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        placeholder = { Text("Paste JSON…") },
                        shape = MaterialTheme.shapes.small,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    if (isSuccess == false) {
                        Text(
                            "Invalid JSON — paste the exact exported text.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
    }

    if (showSoundSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSoundSourceDialog = false },
            title = { Text("Select Audio Source") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        "Choose the audio source for $editingScopeForRingtone alert triggers:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val localSuffix = editingScopeForRingtone.lowercase(java.util.Locale.US)
                    val currentUri = if (editingScopeForRingtone == "Global") alertSoundUri
                        else (prioritySoundUris[localSuffix] ?: "")

                    AudioSourceOption(
                        icon = Icons.Default.MusicNote,
                        title = "System Ringtones",
                        subtitle = "Built-in notification or alarm sounds",
                        onClick = {
                            showSoundSourceDialog = false
                            val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALL)
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alert Sound ($editingScopeForRingtone)")
                                try {
                                    if (currentUri.isNotEmpty())
                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.net.Uri.parse(currentUri))
                                } catch (_: Exception) {}
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            }
                            ringtonePickerLauncher.launch(intent)
                        }
                    )
                    AudioSourceOption(
                        icon = Icons.Default.FolderOpen,
                        title = "Custom Audio File",
                        subtitle = "MP3, WAV, etc. from device storage",
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = {
                            showSoundSourceDialog = false
                            try { customFilePickerLauncher.launch("audio/*") }
                            catch (e: Exception) { android.util.Log.e("Settings", "File picker error: ${e.message}") }
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSoundSourceDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ─── Shared composables ───────────────────────────────────────────────────────

@Composable
fun SettingsGroupHeader(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .clickable(onClick = onToggle),
        color = if (expanded) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm + 2.dp, horizontal = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (expanded) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (expanded) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (expanded) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (expanded) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FlowRowLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

@Composable
private fun TextSizeSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    max: Float
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (value == 0f) "Auto" else "${value.toInt()} sp",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..max,
            steps = max.toInt(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatusChip(
    label: String,
    status: String,
    statusColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(statusColor)
                )
                Text(status, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = statusColor)
            }
        }
    }
}

@Composable
private fun NavSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun AudioSourceOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

