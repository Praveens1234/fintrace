package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.data.model.SymbolInfo
import com.example.ui.theme.AlertCritical
import com.example.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateToAboutApp: () -> Unit,
    onNavigateToAboutDeveloper: () -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    // Collect all setting values from the ViewModel
    val apiKey by viewModel.apiKey.collectAsState()
    val isEnvApiKeyActive by viewModel.isEnvApiKeyActive.collectAsState()
    val updateInterval by viewModel.priceUpdateIntervalMs.collectAsState()
    val websocketUseNativeMode by viewModel.websocketUseNativeMode.collectAsState()
    val cardStyle by viewModel.dashboardCardStyle.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val hapticEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val autoStart by viewModel.autoStartOnBoot.collectAsState()
    val connLossThreshold by viewModel.connectionLossThreshold.collectAsState()
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

    var selectedScope by remember { mutableStateOf("Global") } // "Global", "Low", "Medium", "High", "Critical"
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
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            title = cursor.getString(nameIndex)
                        }
                    }
                } catch (e: Exception) {
                    title = "Selected Custom Audio File"
                }

                // Persist permission for custom files so we don't block service replay restarts
                try {
                    val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {}

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
    var notificationExpanded by remember { mutableStateOf(false) }
    var defaultsExpanded by remember { mutableStateOf(false) }
    var testingExpanded by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var textualSizeExpanded by remember { mutableStateOf(false) }
    var behaviourExpanded by remember { mutableStateOf(false) }
    var dataStorageExpanded by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // SECTION I: Connection & Market Feed
        item {
            SettingsGroupHeader("Connection & Market Feed", Icons.Default.Sync, billingExpanded) { billingExpanded = !billingExpanded }
            if (billingExpanded) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("CONNECTION PARAMETERS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { viewModel.saveApiKey(it) },
                            label = { Text("Twelve Data API Key") },
                            placeholder = { Text("e.g. your_twelve_data_key") },
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isEnvApiKeyActive) {
                            Text(
                                "Twelve Data API Key provided via system environment variables is currently active.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if ((MaterialTheme.colorScheme.background.red + MaterialTheme.colorScheme.background.green + MaterialTheme.colorScheme.background.blue) > 1.5f) Color(0xFF2E7D32) else Color(0xFF69F0AE)
                            )
                        } else {
                            Text(
                                "Provide your own Twelve Data key for real-time market pricing feeds. If left blank, Fintrace operates in client-side Simulated Demo Mode with localized synthetic tickers.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        // Modern styled Segmented Switch or Selector
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("STREAM SYNC MODE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val optionColorNative = if (websocketUseNativeMode) MaterialTheme.colorScheme.primary else Color.Transparent
                                val optionColorInterval = if (!websocketUseNativeMode) MaterialTheme.colorScheme.primary else Color.Transparent
                                val textColorNative = if (websocketUseNativeMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                val textColorInterval = if (!websocketUseNativeMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(optionColorNative)
                                        .clickable { viewModel.saveWebsocketUseNativeMode(true) }
                                        .padding(vertical = 8.dp)
                                        .testTag("tick_mode_native_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Native Streaming", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textColorNative)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(optionColorInterval)
                                        .clickable { viewModel.saveWebsocketUseNativeMode(false) }
                                        .padding(vertical = 8.dp)
                                        .testTag("tick_mode_interval_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Interval Throttle", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textColorInterval)
                                }
                            }
                        }

                        if (websocketUseNativeMode) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Native Mode active: Twelve Data stream pushes prices in real-time, bypassing update intervals.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (websocketUseNativeMode) 0.5f else 1.0f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Price Update Interval", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    text = if (websocketUseNativeMode) "Disable Native Mode to configure" else "Set latency throttle (100ms - 10s)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.savePriceUpdateInterval(updateInterval - 50) },
                                    enabled = !websocketUseNativeMode
                                ) {
                                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                                Text(
                                    text = "$updateInterval ms",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    color = if (websocketUseNativeMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = { viewModel.savePriceUpdateInterval(updateInterval + 50) },
                                    enabled = !websocketUseNativeMode
                                ) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Default Asset Decimal Override", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Limit the maximum decimal places displayed. We recommend using 'MAX' to preserve native, native precision counts.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("MAX", "1", "2", "3", "4", "5", "6", "7", "8", "9").forEach { mode ->
                                    val isSel = pricePrecision == mode
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { viewModel.savePricePrecisionOverride(mode) },
                                        label = { Text(mode, style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)) }
                                    )
                                }
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Text("APPLICATIONS BEHAVIOR OPERATIONS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fluctuation Haptic Tics", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Discreetly vibrate on ticks movement updates", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = hapticEnabled,
                                onCheckedChange = { viewModel.saveHapticFeedbackEnabled(it) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-start after boot up", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Restores FGS tracker automatically on reboot", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = autoStart,
                                onCheckedChange = { viewModel.saveAutoStartOnBoot(it) }
                            )
                        }
                    }
                }
            }
        }

        // SECTION II: Notifications & Speech Alerts
        item {
            SettingsGroupHeader("Notifications & Speech Alerts", Icons.Default.Notifications, defaultsExpanded) { defaultsExpanded = !defaultsExpanded }
            if (defaultsExpanded) {
                val suffix = selectedScope.lowercase(java.util.Locale.US)
                val activeSoundMode = if (selectedScope == "Global") {
                    alertSoundMode
                } else {
                    prioritySoundModes[suffix]?.ifBlank { "" } ?: ""
                }

                val activeSoundTitle = if (selectedScope == "Global") {
                    alertSoundTitle
                } else {
                    prioritySoundTitles[suffix]?.ifBlank { "" } ?: ""
                }

                val activeSoundUri = if (selectedScope == "Global") {
                    alertSoundUri
                } else {
                    prioritySoundUris[suffix] ?: ""
                }

                val activeRingDurationSec = if (selectedScope == "Global") {
                    alertRingDurationSec
                } else {
                    priorityRingDurations[suffix] ?: alertRingDurationSec
                }

                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("PERSISTENT STREAM SHADE LIVE TICKER", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Shade Live Price Ticker", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Renders selected symbols dynamically inside notifications drawer", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val isEnabled = liveTickerSub.isNotEmpty()
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = {
                                    if (it) {
                                        val initialSymbols = if (activePortfolioSymbols.isNotEmpty()) {
                                            activePortfolioSymbols.take(5)
                                        } else {
                                            listOf("XAU/USD", "EUR/USD")
                                        }
                                        viewModel.saveLiveTickerSymbols(initialSymbols)
                                    } else {
                                        viewModel.saveLiveTickerSymbols(emptyList())
                                    }
                                }
                            )
                        }

                        if (liveTickerSub.isNotEmpty()) {
                            Text("Active Ticker Symbols (Select up to 5 max):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            FlowRowLayout(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SymbolInfo.ALL.forEach { sym ->
                                    val isChecked = liveTickerSub.contains(sym.symbol)
                                    FilterChip(
                                        selected = isChecked,
                                        onClick = {
                                            val newList = liveTickerSub.toMutableList()
                                            if (isChecked) newList.remove(sym.symbol) else newList.add(sym.symbol)
                                            viewModel.saveLiveTickerSymbols(newList.take(5))
                                        },
                                        label = { Text(sym.symbol, fontSize = 11.sp) },
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Text("VOICE FEEDBACK & AUDIBLE WARNING TUNING", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)

                        // Scope Selector (Global vs Low vs Medium vs High vs Critical)
                        Text(
                            "Configuration Focus Target Scope",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Global", "LOW", "MEDIUM", "HIGH", "CRITICAL").forEach { scope ->
                                val isSelected = selectedScope == scope
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedScope = scope },
                                    label = { Text(scope, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }

                        Text(
                            text = if (selectedScope == "Global") {
                                "Editing default settings applied as the baseline across all alerts."
                            } else {
                                "Editing custom override rules applied exclusively to $selectedScope priority alerts."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        // Sound Playback Mode Choices
                        Text(
                            "Sound Playback Style Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )

                        val modeOptions = if (selectedScope == "Global") {
                            listOf("Both Tone and Voice", "Tone alert only", "TTS voice only", "Silent")
                        } else {
                            listOf("Inherit Global", "Both Tone and Voice", "Tone alert only", "TTS voice only", "Silent")
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            modeOptions.forEach { mode ->
                                val selected = if (selectedScope == "Global") {
                                    activeSoundMode == mode
                                } else {
                                    if (mode == "Inherit Global") activeSoundMode.isEmpty() else activeSoundMode == mode
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            val modeToSave = if (mode == "Inherit Global") "" else mode
                                            if (selectedScope == "Global") {
                                                viewModel.saveAlertSoundMode(modeToSave)
                                            } else {
                                                viewModel.savePrioritySoundMode(suffix, modeToSave)
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = {
                                            val modeToSave = if (mode == "Inherit Global") "" else mode
                                            if (selectedScope == "Global") {
                                                viewModel.saveAlertSoundMode(modeToSave)
                                            } else {
                                                viewModel.savePrioritySoundMode(suffix, modeToSave)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = mode,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                        val desc = when (mode) {
                                            "Inherit Global" -> "Default settings are inherited from global alert behavior (${alertSoundMode})."
                                            "Both Tone and Voice" -> "Plays custom sound combined with spoken price announcements."
                                            "Tone alert only" -> "Plays warning chime sound without TTS speech reading."
                                            "TTS voice only" -> "Speaks the price targets aloud with no sound chimes."
                                            "Silent" -> "Disables sound elements (visual device push notification only)."
                                            else -> ""
                                        }
                                        Text(
                                            text = desc,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Custom Device Ringtone Picker
                        Text(
                            "Selected Alarm Trigger Ringtone",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    editingScopeForRingtone = selectedScope
                                    showSoundSourceDialog = true
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Device Custom Music Sound Track", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (selectedScope != "Global" && activeSoundTitle.isEmpty()) {
                                            "Inherited baseline (${alertSoundTitle.ifBlank { "Default System Tone" }})"
                                        } else {
                                            activeSoundTitle.ifBlank { "Default System Tone" }
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                "PICK TONE",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                        }

                        if (selectedScope != "Global" && activeSoundUri.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    viewModel.savePrioritySoundUri(suffix, "")
                                    viewModel.savePrioritySoundTitle(suffix, "")
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Clear Override (Inherit Global Sound)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Dynamic Alert Ringing Playback limits
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Ringtone Playback Limit Time",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                "$activeRingDurationSec seconds",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Slider(
                            value = activeRingDurationSec.toFloat(),
                            onValueChange = {
                                val valInt = it.toInt()
                                if (selectedScope == "Global") {
                                    viewModel.saveAlertRingDurationSec(valInt)
                                } else {
                                    viewModel.savePriorityRingDurationSec(suffix, valInt)
                                }
                            },
                            valueRange = 1f..60f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(2, 5, 10, 15, 30).forEach { sec ->
                                val isSel = activeRingDurationSec == sec
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        if (selectedScope == "Global") {
                                            viewModel.saveAlertRingDurationSec(sec)
                                        } else {
                                            viewModel.savePriorityRingDurationSec(suffix, sec)
                                        }
                                    },
                                    label = { Text("${sec}s", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Text("VERIFICATION SYSTEM DIAGNOSTIC TESTS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.testDemoAlert("DEFAULT_NOTIFICATION") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Test Heads-up Push", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewModel.testDemoAlert("ALARM") },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Test System Alarm", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // SECTION III: Appearance & Dashboard Styling
        item {
            SettingsGroupHeader("Customization & Theme Styling", Icons.Default.Palette, appearanceExpanded) { appearanceExpanded = !appearanceExpanded }
            if (appearanceExpanded) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text("Application Theme Model", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Light", "AMOLED", "System").forEach { mode ->
                                    val isSel = themeMode == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { viewModel.saveThemeMode(mode) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(mode, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Column {
                            Text("Dashboard Metrics Card Style", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Standard", "Compact", "Classic Row").forEach { style ->
                                    val isSel = cardStyle == style
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { viewModel.saveDashboardCardStyle(style) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(style, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION III.B: Textual Size Control
        item {
            SettingsGroupHeader("Textual Size Control", Icons.Default.TextFormat, textualSizeExpanded) { textualSizeExpanded = !textualSizeExpanded }
            if (textualSizeExpanded) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "Customize text sizes across the Prices dashboard screens. Set to the far left (Auto) to use native dynamic text scaling per style.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 1. Live Price Font Size
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Prices Text Size", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    if (priceTextSizeSetting == 0f) "Auto (Default)" else "${priceTextSizeSetting.toInt()} sp",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = priceTextSizeSetting,
                                onValueChange = { viewModel.savePriceTextSize(it.toInt().toFloat()) },
                                valueRange = 0f..48f,
                                steps = 48,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // 2. Symbol ID Ticker Font Size
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Symbol ID Text Size", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    if (symbolIdTextSizeSetting == 0f) "Auto (Default)" else "${symbolIdTextSizeSetting.toInt()} sp",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = symbolIdTextSizeSetting,
                                onValueChange = { viewModel.saveSymbolIdTextSize(it.toInt().toFloat()) },
                                valueRange = 0f..32f,
                                steps = 32,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // 3. Symbol Name Font Size
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Symbol Name Text Size", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    if (symbolNameTextSizeSetting == 0f) "Auto (Default)" else "${symbolNameTextSizeSetting.toInt()} sp",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = symbolNameTextSizeSetting,
                                onValueChange = { viewModel.saveSymbolNameTextSize(it.toInt().toFloat()) },
                                valueRange = 0f..24f,
                                steps = 24,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Button(
                            onClick = { viewModel.resetTextualSettings() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Textual Settings to Default", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // SECTION IV: Portfolio Backups & Resets
        item {
            SettingsGroupHeader("Portfolio Backups & Resets", Icons.Default.Backup, dataStorageExpanded) { dataStorageExpanded = !dataStorageExpanded }
            if (dataStorageExpanded) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("PORTFOLIO DATA PROTECTION", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { showExportDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export Rules", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { showImportDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Input, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import Rules", fontSize = 11.sp)
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Text("STORAGE DIAGNOSTICS & RETRIEVALS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)

                        Button(
                            onClick = { viewModel.clearTriggerHistory() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f), contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Clear Trigger History History Logs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.resetAllSettings() },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertCritical, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Restore Preferences Defaults", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // SECTION V: Logs, Self-Healing & Crash Recovery
        item {
            var diagnosticsExpanded by remember { mutableStateOf(false) }
            SettingsGroupHeader("Logs & Self-Healing Registry", Icons.Default.Shield, diagnosticsExpanded) { diagnosticsExpanded = !diagnosticsExpanded }
            if (diagnosticsExpanded) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("SYSTEM HEALING & AUTO PROTECTION", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)

                        // Status Indicator Widgets
                        val isLight = (MaterialTheme.colorScheme.background.red + MaterialTheme.colorScheme.background.green + MaterialTheme.colorScheme.background.blue) > 1.5f
                        val activeColor = if (isLight) Color(0xFF2E7D32) else Color(0xFF69F0AE)
                        val secureColor = if (isLight) Color(0xFF0277BD) else Color(0xFF00E5FF)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Self-Healing Core", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(activeColor))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ACTIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = activeColor)
                                    }
                                }
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Power & Space Shield", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(secureColor))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("SECURE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secureColor)
                                    }
                                }
                            }
                        }

                        // Short description
                        Text(
                            text = "Unified log diagnostics and self-healing telemetry. Automatically intercepts and heals uncaught crashes, blocks telemetry overloads, and auto-prunes cache database size in real-time.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        val tickLoggingEnabled by viewModel.tickLoggingEnabled.collectAsState()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "In-Memory Ticker Logging",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Tracks real-time asset price updates in our active RAM cache. Disable this to stop all transient tick telemetry to preserve battery.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = tickLoggingEnabled,
                                onCheckedChange = { viewModel.saveTickLoggingEnabled(it) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        var showInlineTerminalDialog by remember { mutableStateOf(false) }

                        Button(
                            onClick = { showInlineTerminalDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Logs Registry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        }

        // SECTION VI: General & Device Diagnosis
        item {
            SettingsGroupHeader("General & Device Diagnosis", Icons.Default.Settings, aboutExpanded) { aboutExpanded = !aboutExpanded }
            if (aboutExpanded) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column {
                        ListItem(
                            headlineContent = { Text("About FinTrace Info", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                            supportingContent = { Text("Stack profile, licensing, build diagnostics", fontSize = 11.sp) },
                            leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                            modifier = Modifier.clickable(onClick = onNavigateToAboutApp)
                        )
                        ListItem(
                            headlineContent = { Text("About Developer Contact", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                            supportingContent = { Text("Open-source credits summary and contact channels", fontSize = 11.sp) },
                            leadingContent = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                            modifier = Modifier.clickable(onClick = onNavigateToAboutDeveloper)
                        )
                        ListItem(
                            headlineContent = { Text("Check System Permissions", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                            supportingContent = { Text("Exemptions whitelist, alarm sync indicators verification", fontSize = 11.sp) },
                            leadingContent = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                            modifier = Modifier.clickable(onClick = onNavigateToPermissions)
                        )
                    }
                }
            }
        }
    }

    // Export rule Dialog
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
                    if (alert.expiry != null) {
                        alertObj.put("expiry", alert.expiry)
                    }
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
                Button(onClick = { showExportDialog = false }) { Text("Dismiss") }
            },
            title = { Text("Clipboard Backup Export") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Copy your exported crossing backup JSON below to safe-keep your rules:")
                    OutlinedTextField(
                        value = backupJson,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(backupJson))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy to Clipboard")
                    }
                }
            }
        )
    }

    // Import rule Dialog
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
                        if (ok) {
                            showImportDialog = false
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            },
            title = { Text("Backup Crossing Import") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste your exported configuration JSON backup below to restore your triggers:")
                    OutlinedTextField(
                        value = importInput,
                        onValueChange = { 
                            importInput = it
                            isSuccess = null
                        },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("Paste JSON backing string...") },
                        shape = RoundedCornerShape(8.dp)
                    )
                    if (isSuccess == false) {
                        Text("Invalid JSON structure! Make sure to copy the exact exported text.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "How would you like to select your alert alarm tone for $editingScopeForRingtone triggers?",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Option 1: System ringtones
                    Surface(
                        onClick = {
                            showSoundSourceDialog = false
                            val suffix = editingScopeForRingtone.lowercase(java.util.Locale.US)
                            val currentSoundUri = if (editingScopeForRingtone == "Global") {
                                alertSoundUri
                            } else {
                                prioritySoundUris[suffix] ?: ""
                            }
                            val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALL)
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alert Sound ($editingScopeForRingtone)")
                                try {
                                    if (currentSoundUri.isNotEmpty()) {
                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.net.Uri.parse(currentSoundUri))
                                    }
                                } catch (e: Exception) {}
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            }
                            ringtonePickerLauncher.launch(intent)
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("System Ringtones & Tones", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Built-in notification or alarm sounds", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Option 2: Custom audio file
                    Surface(
                        onClick = {
                            showSoundSourceDialog = false
                            try {
                                customFilePickerLauncher.launch("audio/*")
                            } catch (e: Exception) {
                                android.util.Log.e("Settings", "Failed to launch custom file picker: ${e.message}")
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Custom Audio Files", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Choose MP3, WAV, etc., from device storage", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSoundSourceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsGroupHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle),
        color = if (expanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Icon in a tinted rounded badge — consistent with alert coins / hero icons.
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
                        tint = if (expanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (expanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (expanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}
