package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLog
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: MainViewModel
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Diagnostic Terminal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            TerminalContent(viewModel = viewModel)
        }
    }
}

@Composable
fun LogsScreenDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val isLight = (MaterialTheme.colorScheme.background.red + MaterialTheme.colorScheme.background.green + MaterialTheme.colorScheme.background.blue) > 1.5f
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(4.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (isLight) MaterialTheme.colorScheme.surface else Color(0xFF15181C),
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Unified Diagnostic Terminal",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))
                
                Box(modifier = Modifier.weight(1f)) {
                    TerminalContent(viewModel = viewModel)
                }

                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("terminal_dismiss_button")
                    ) {
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalContent(
    viewModel: MainViewModel
) {
    val logs by viewModel.allLogs.collectAsState()
    val storage by viewModel.storageInfo.collectAsState()
    
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    val sdf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var showExportResultDialog by remember { mutableStateOf(false) }
    var exportResultText by remember { mutableStateOf("") }
    var exportType by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var isToolsExpanded by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val heartbeatAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartbeat"
    )

    val jsonExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(exportResultText.toByteArray(Charsets.UTF_8))
                    }
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "JSON exported and saved successfully!", android.widget.Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to save JSON: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val csvExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(exportResultText.toByteArray(Charsets.UTF_8))
                    }
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "CSV exported and saved successfully!", android.widget.Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to save CSV: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.updateStorageInfo()
    }

    val filteredLogs = remember(logs, selectedFilter, searchQuery) {
        logs.filter { log ->
            val matchesFilter = when (selectedFilter) {
                "Ticks" -> log.type == "TICK"
                "Alerts" -> log.type == "ALERT_TRIGGER"
                "Failures" -> log.type == "ERROR" || log.type == "CRASH"
                "Heal / Protect" -> log.type == "HEALING" || log.type == "PROTECTION" || log.type == "RECOVERY" || log.type == "SYSTEM"
                else -> true
            }
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                log.message.contains(searchQuery, ignoreCase = true) ||
                        (log.symbol?.contains(searchQuery, ignoreCase = true) == true)
            }
            matchesFilter && matchesQuery
        }
    }

    val isLight = (MaterialTheme.colorScheme.background.red + MaterialTheme.colorScheme.background.green + MaterialTheme.colorScheme.background.blue) > 1.5f
    val terminalColor = if (isLight) Color(0xFF00695C) else Color(0xFF00FFC2)
    val consoleBg = if (isLight) Color(0xFFF1F3F5) else Color(0xFF050709)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // High quality telemetry register header with blinking live heart-beat dot
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(terminalColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = terminalColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(RoundedCornerShape(3.5.dp))
                                    .background(terminalColor.copy(alpha = heartbeatAlpha))
                            )
                            Text(
                                text = "TRACER TERMINAL ACTIVE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                ),
                                color = terminalColor
                            )
                        }
                        Text(
                            text = "Live background diagnostics diagnostics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.clearAllLogs() },
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear All Logs",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Search Bar Redesign mimicking a modern console shell prompt
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { 
                Text(
                    "filter-query: run logs parser filter...", 
                    fontSize = 12.sp, 
                    fontFamily = PriceTextFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ) 
            },
            prefix = {
                Text(
                    "$ ", 
                    fontSize = 12.sp, 
                    fontFamily = PriceTextFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = terminalColor
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                    }
                }
            },
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("log_search_input"),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = PriceTextFontFamily, fontSize = 12.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = terminalColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                unfocusedContainerColor = if (isLight) MaterialTheme.colorScheme.surface else Color(0xFF0C0F12),
                focusedContainerColor = if (isLight) MaterialTheme.colorScheme.surface else Color(0xFF0F1216)
            )
        )

        // Filter pills row - Shell styled categories
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("All", "Ticks", "Alerts", "Failures", "Heal / Protect").forEach { category ->
                val isSelected = selectedFilter == category
                val containerColor = if (isSelected) {
                    terminalColor
                } else {
                    if (isLight) Color(0xFFF1F3F5) else Color(0xFF101317)
                }
                val labelColor = if (isSelected) {
                    if (isLight) Color.White else Color.Black
                } else {
                    if (isLight) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f) else Color(0xFF90A4AE)
                }
                val borderStroke = if (isSelected) {
                    null
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(containerColor)
                        .then(if (borderStroke != null) Modifier.border(borderStroke, RoundedCornerShape(8.dp)) else Modifier)
                        .clickable { selectedFilter = category }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("log_filter_${category.lowercase().replace(" ", "_").replace("/", "")}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category.uppercase(),
                        fontSize = 10.sp,
                        fontFamily = PriceTextFontFamily,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        color = labelColor
                    )
                }
            }
        }

        // Collapse or Modular System Utilities Accordion Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLight) Color.White else Color(0xFF101317)
            ),
            border = BorderStroke(1.dp, if (isToolsExpanded) terminalColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header section of tools
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isToolsExpanded = !isToolsExpanded }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsAccessibility,
                            contentDescription = null,
                            tint = if (isToolsExpanded) terminalColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "SYSTEM TOOLS & AUDIT DIAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isToolsExpanded) terminalColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${String.format("%.1f", (1f - storage.usedPercent) * 100f)}% DISK FREE",
                            fontSize = 10.sp,
                            fontFamily = PriceTextFontFamily,
                            fontWeight = FontWeight.Black,
                            color = if (storage.usedPercent > 0.8f) MaterialTheme.colorScheme.error else terminalColor
                        )
                        Icon(
                            imageVector = if (isToolsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand collapsible section",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Expanded diagnostics panel details
                AnimatedVisibility(visible = isToolsExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                        // Progress Audit indicators
                        Column {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "DISK REGISTERS OVERHEAD: ${String.format("%.3f", storage.usedMB)} MB / 10.00 MB",
                                    fontSize = 10.sp,
                                    fontFamily = PriceTextFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${String.format("%.2f", storage.remainingMB)} MB left",
                                    fontSize = 10.sp,
                                    fontFamily = PriceTextFontFamily,
                                    color = terminalColor
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { storage.usedPercent },
                                color = if (storage.usedPercent > 0.8f) MaterialTheme.colorScheme.error else terminalColor,
                                trackColor = if (isLight) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF1E232A),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }

                        // Share Export buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isExporting = true
                                        val allLogsForExport = viewModel.getAllLogsForExport()
                                        val json = try {
                                            val arr = org.json.JSONArray()
                                            allLogsForExport.forEach { log ->
                                                val obj = org.json.JSONObject()
                                                obj.put("id", log.id)
                                                obj.put("timestamp", log.timestamp)
                                                obj.put("type", log.type)
                                                obj.put("symbol", log.symbol ?: "")
                                                obj.put("message", log.message)
                                                arr.put(obj)
                                            }
                                            arr.toString(2)
                                        } catch (e: Exception) {
                                            "[]"
                                        }
                                        exportResultText = json
                                        exportType = "JSON"
                                        clipboardManager.setText(AnnotatedString(json))
                                        shareExportedFile(context, "fintrace_diagnostic_logs.json", json, true)
                                        try {
                                            jsonExporter.launch("fintrace_diagnostic_logs.json")
                                        } catch (ex: Exception) {
                                            android.util.Log.e("LogsScreen", "SAF export launch error: ${ex.message}")
                                        }
                                        showExportResultDialog = true
                                        isExporting = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isLight) Color(0xFF455A64) else Color(0xFF263238),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isExporting
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export JSON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        isExporting = true
                                        val allLogsForExport = viewModel.getAllLogsForExport()
                                        val csv = buildString {
                                            append("ID,Timestamp,Type,Symbol,Message\n")
                                            allLogsForExport.forEach { log ->
                                                val cleanMsg = log.message.replace("\"", "\"\"")
                                                val cleanSymbol = (log.symbol ?: "").replace("\"", "\"\"")
                                                append("${log.id},${log.timestamp},${log.type},\"$cleanSymbol\",\"$cleanMsg\"\n")
                                            }
                                        }
                                        exportResultText = csv
                                        exportType = "CSV"
                                        clipboardManager.setText(AnnotatedString(csv))
                                        shareExportedFile(context, "fintrace_diagnostic_logs.csv", csv, false)
                                        try {
                                            csvExporter.launch("fintrace_diagnostic_logs.csv")
                                        } catch (ex: Exception) {
                                            android.util.Log.e("LogsScreen", "SAF export launch error: ${ex.message}")
                                        }
                                        showExportResultDialog = true
                                        isExporting = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isLight) Color(0xFF455A64) else Color(0xFF263238),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isExporting
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Simulated test crash panel triggers
                        Button(
                            onClick = {
                                throw RuntimeException("FinTrace User-Triggered Diagnostic Stress Test Crash")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLight) Color(0xFFFFEBEE) else Color(0xFF2A0D10),
                                contentColor = if (isLight) Color(0xFFC62828) else Color(0xFFFF8A80)
                            ),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isLight) Color(0xFFFFCDD2) else Color(0xFF5A161C))
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Trigger Simulated Runtime Error", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "active-records: ${filteredLogs.size}",
                fontSize = 11.sp,
                fontFamily = PriceTextFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = "AUTO_ROTATING_LOGGER",
                fontSize = 11.sp,
                fontFamily = PriceTextFontFamily,
                color = terminalColor.copy(alpha = 0.75f),
                fontWeight = FontWeight.Bold
            )
        }

        // Linux Workstation styled Console Terminal Frame
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(consoleBg)
                .border(BorderStroke(1.dp, if (isLight) Color.LightGray else Color(0xFF1E2229)), RoundedCornerShape(12.dp))
        ) {
            // CLI Window top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isLight) Color(0xFFE9ECEF) else Color(0xFF0F1216))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Colored circles dots (Window Controls)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFF5F56)))
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFBD2E)))
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF27C93F)))
                }

                Text(
                    text = "fintrace_monitor://${selectedFilter.lowercase().replace(" ","_")}.log",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontFamily = PriceTextFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Icon(
                    imageVector = Icons.Default.Laptop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(12.dp)
                )
            }

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "[SYSTEM STACK IS SILENT]\nNo log records matching filters in real time.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = PriceTextFontFamily,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    itemsIndexed(
                        items = filteredLogs,
                        key = { _, item -> "${item.id}_${item.type}_${item.timestamp}" }
                    ) { index, logItem ->
                        LogRecordRow(
                            log = logItem, 
                            timeStr = sdf.format(Date(logItem.timestamp)),
                            index = index
                        )
                    }
                }
            }
        }
    }

    // EXPORT SUCCESS AND PREVIEW MODAL
    if (showExportResultDialog) {
        AlertDialog(
            onDismissRequest = { showExportResultDialog = false },
            confirmButton = {
                TextButton(onClick = { showExportResultDialog = false }) { 
                    Text("Dismiss", fontWeight = FontWeight.Bold) 
                }
            },
            title = { Text("$exportType Diagnostic Export") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The full audit trace was compiled successfully into a file, shared with the system, and copied to your clipboard! See preview below:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = exportResultText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = PriceTextFontFamily, fontSize = 9.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isLight) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF0B0D11),
                            unfocusedContainerColor = if (isLight) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF0B0D11),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(exportResultText))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy to Clipboard")
                    }
                }
            }
        )
    }
}

@Composable
fun LogRecordRow(log: AppLog, timeStr: String, index: Int) {
    val isSimulated = log.type == "TICK" && (log.message.contains("simulated", ignoreCase = true) || !log.message.contains("live", ignoreCase = true))
    val isLight = (MaterialTheme.colorScheme.background.red + MaterialTheme.colorScheme.background.green + MaterialTheme.colorScheme.background.blue) > 1.5f

    val termColor = when {
        isSimulated -> if (isLight) Color(0xFFC62828) else Color(0xFFFF4D6A)     // Red simulated ticks
        log.type == "TICK" -> if (isLight) Color(0xFF00796B) else Color(0xFF00FFC2)          // Glowing live ticks
        log.type == "ALERT_TRIGGER" -> if (isLight) Color(0xFFD84315) else Color(0xFFFF3D00) // Alarm alerts
        log.type == "SYSTEM" -> if (isLight) Color(0xFFE65100) else Color(0xFFFFD600)        // Amber system messages
        log.type == "CRASH" -> if (isLight) Color(0xFFAD1457) else Color(0xFFFF1744)         // Uncaught exceptions
        log.type == "ERROR" -> if (isLight) Color(0xFFD84315) else Color(0xFFFF9100)         // Overloaded/recovered loops
        log.type == "HEALING" -> if (isLight) Color(0xFF2E7D32) else Color(0xFF69F0AE)       // Component self-healing
        log.type == "PROTECTION" -> if (isLight) Color(0xFF1565C0) else Color(0xFF00E5FF)    // Security protection overrides
        log.type == "RECOVERY" -> if (isLight) Color(0xFF6A1B9A) else Color(0xFFE040FB)      // Component state retrievals
        else -> if (isLight) MaterialTheme.colorScheme.onSurface else Color(0xFFECEFF1)
    }

    val typePrefix = when {
        isSimulated -> "SIM_TICK"
        log.type == "TICK" -> "LIVE_TICK"
        log.type == "ALERT_TRIGGER" -> "ALERT"
        log.type == "SYSTEM" -> "SYSTEM"
        log.type == "CRASH" -> "CRASH"
        log.type == "ERROR" -> "ERROR"
        log.type == "HEALING" -> "HEAL"
        log.type == "PROTECTION" -> "PROT"
        log.type == "RECOVERY" -> "RECOV"
        else -> "INFO"
    }

    val isEven = index % 2 == 0
    val rowBgColor = if (isEven) {
        if (isLight) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.02f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(rowBgColor)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Line number helper to feel like a high-end IDE terminal
        Text(
            text = String.format("%03d", index + 1),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PriceTextFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
            modifier = Modifier.width(28.dp)
        )

        Text(
            text = "[$timeStr]",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PriceTextFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp
            ),
            color = if (isLight) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else Color(0x66ECEFF1)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Polished level badge
        Box(
            modifier = Modifier
                .width(72.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(termColor.copy(alpha = 0.12f))
                .padding(vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = typePrefix,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = PriceTextFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 9.sp
                ),
                color = termColor
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PriceTextFontFamily,
                fontSize = 11.sp,
                lineHeight = 14.sp
            ),
            color = if (isLight) MaterialTheme.colorScheme.onSurface else Color(0xFFDCDFE4),
            modifier = Modifier.weight(1f)
        )
    }
}

fun shareExportedFile(context: android.content.Context, filename: String, content: String, isJson: Boolean) {
    try {
        val cacheFile = java.io.File(context.cacheDir, filename)
        cacheFile.writeText(content)

        val authority = "com.example.fintrace.fileprovider"
        val fileUri = androidx.core.content.FileProvider.getUriForFile(context, authority, cacheFile)

        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = if (isJson) "application/json" else "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "FinTrace $filename")
            putExtra(android.content.Intent.EXTRA_TEXT, "FinTrace system diagnostic logs exported format.")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(android.content.Intent.createChooser(shareIntent, "Save or Send FinTrace Diagnostic logs..."))
    } catch (e: Exception) {
        android.util.Log.e("LogsScreen", "Failed to share/export file: ${e.message}", e)
        android.widget.Toast.makeText(context, "Export error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}
