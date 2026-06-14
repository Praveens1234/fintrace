package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLog
import com.example.ui.theme.Radius
import com.example.ui.theme.Spacing
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

// ─── Filter chip definitions ─────────────────────────────────────────────────

private data class FilterChip(val label: String, val types: Set<String>?)

private val LOG_FILTERS = listOf(
    FilterChip("All", null),
    FilterChip("TICK", setOf("TICK")),
    FilterChip("ALERT", setOf("ALERT_TRIGGER")),
    FilterChip("INFO", setOf("INFO")),
    FilterChip("ERROR", setOf("ERROR", "CRASH")),
    FilterChip("SYSTEM", setOf("SYSTEM", "HEALING", "PROTECTION", "RECOVERY")),
)

// ─── Color helpers ────────────────────────────────────────────────────────────

@Composable
private fun borderColorForType(type: String): Color = when (type) {
    "TICK" -> MaterialTheme.colorScheme.primary
    "ALERT_TRIGGER" -> MaterialTheme.colorScheme.tertiary
    "ERROR", "CRASH" -> MaterialTheme.colorScheme.error
    "SYSTEM", "HEALING", "PROTECTION", "RECOVERY" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.outlineVariant
}

@Composable
private fun chipColorForType(type: String): Color = when (type) {
    "TICK" -> MaterialTheme.colorScheme.primary
    "ALERT_TRIGGER" -> MaterialTheme.colorScheme.tertiary
    "ERROR", "CRASH" -> MaterialTheme.colorScheme.error
    "SYSTEM", "HEALING", "PROTECTION", "RECOVERY" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.outline
}

private fun labelForType(type: String): String = when (type) {
    "TICK" -> "TICK"
    "ALERT_TRIGGER" -> "ALERT"
    "ERROR" -> "ERROR"
    "CRASH" -> "CRASH"
    "SYSTEM" -> "SYSTEM"
    "HEALING" -> "HEAL"
    "PROTECTION" -> "PROT"
    "RECOVERY" -> "RECOV"
    else -> "INFO"
}

// ─── Public composables ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(viewModel: MainViewModel) {
    val logs by viewModel.allLogs.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var exportMenuExpanded by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    // Holds the content to write when a SAF URI is available
    var pendingExportContent by remember { mutableStateOf("") }

    val jsonExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val content = pendingExportContent
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "JSON saved successfully", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Export failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val csvExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val content = pendingExportContent
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "CSV saved successfully", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Export failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun buildJson(exportLogs: List<AppLog>): String {
        val arr = org.json.JSONArray()
        exportLogs.forEach { log ->
            val obj = org.json.JSONObject()
            obj.put("id", log.id)
            obj.put("timestamp", log.timestamp)
            obj.put("type", log.type)
            obj.put("symbol", log.symbol ?: "")
            obj.put("message", log.message)
            arr.put(obj)
        }
        return arr.toString(2)
    }

    fun buildCsv(exportLogs: List<AppLog>): String = buildString {
        append("id,timestamp,type,symbol,message\n")
        exportLogs.forEach { log ->
            val msg = log.message.replace("\"", "\"\"")
            val sym = (log.symbol ?: "").replace("\"", "\"\"")
            append("${log.id},${log.timestamp},${log.type},\"$sym\",\"$msg\"\n")
        }
    }

    val filteredLogs = remember(logs, selectedFilter, searchQuery) {
        val chipFilter = LOG_FILTERS.find { it.label == selectedFilter }
        logs.filter { log ->
            val typeMatch = chipFilter?.types?.contains(log.type) ?: true
            val queryMatch = searchQuery.isBlank() ||
                log.message.contains(searchQuery, ignoreCase = true) ||
                (log.symbol?.contains(searchQuery, ignoreCase = true) == true)
            typeMatch && queryMatch
        }
    }

    // Count per chip
    val countPerFilter = remember(logs) {
        LOG_FILTERS.associateWith { chip ->
            if (chip.types == null) logs.size
            else logs.count { chip.types.contains(it.type) }
        }
    }

    // Auto-scroll to top when new entries arrive — only when already at top
    val listState = rememberLazyListState()
    val prevSize = remember { mutableIntStateOf(filteredLogs.size) }
    LaunchedEffect(filteredLogs.size) {
        val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        if (filteredLogs.size > prevSize.intValue && isAtTop) {
            listState.animateScrollToItem(0)
        }
        prevSize.intValue = filteredLogs.size
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Logs",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${logs.size} entries",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        // Search toggle
                        IconButton(onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) searchQuery = ""
                        }) {
                            Icon(
                                imageVector = if (searchVisible) Icons.Default.SearchOff else Icons.Default.Search,
                                contentDescription = if (searchVisible) "Close search" else "Search logs"
                            )
                        }

                        // Export dropdown
                        Box {
                            IconButton(
                                onClick = { exportMenuExpanded = true },
                                enabled = !isExporting
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = "Export logs")
                            }
                            DropdownMenu(
                                expanded = exportMenuExpanded,
                                onDismissRequest = { exportMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export as JSON") },
                                    leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                                    onClick = {
                                        exportMenuExpanded = false
                                        scope.launch {
                                            isExporting = true
                                            val exportLogs = viewModel.getAllLogsForExport()
                                            pendingExportContent = buildJson(exportLogs)
                                            try {
                                                jsonExporter.launch("fintrace_logs.json")
                                            } catch (e: Exception) {
                                                android.util.Log.e("LogsScreen", "JSON export error: ${e.message}")
                                            }
                                            isExporting = false
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export as CSV") },
                                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                                    onClick = {
                                        exportMenuExpanded = false
                                        scope.launch {
                                            isExporting = true
                                            val exportLogs = viewModel.getAllLogsForExport()
                                            pendingExportContent = buildCsv(exportLogs)
                                            try {
                                                csvExporter.launch("fintrace_logs.csv")
                                            } catch (e: Exception) {
                                                android.util.Log.e("LogsScreen", "CSV export error: ${e.message}")
                                            }
                                            isExporting = false
                                        }
                                    }
                                )
                            }
                        }

                        // Clear with confirmation
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear logs",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Collapsible search bar
                AnimatedVisibility(
                    visible = searchVisible,
                    enter = expandVertically(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)) + fadeOut()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by message or symbol…") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                            .testTag("log_search_input"),
                        shape = MaterialTheme.shapes.medium
                    )
                }

                // Filter chips row
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(LOG_FILTERS) { chip ->
                        val count = countPerFilter[chip] ?: 0
                        val selected = selectedFilter == chip.label
                        FilterChipItem(
                            label = "${chip.label} ($count)",
                            selected = selected,
                            onClick = { selectedFilter = chip.label },
                            testTag = "log_filter_${chip.label.lowercase()}"
                        )
                    }
                }

                HorizontalDivider()
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->

        if (filteredLogs.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = if (searchQuery.isNotBlank() || selectedFilter != "All")
                            "No logs match your filters"
                        else
                            "No logs yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isNotBlank() || selectedFilter != "All") {
                        TextButton(onClick = {
                            searchQuery = ""
                            selectedFilter = "All"
                        }) {
                            Text("Clear filters")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = Spacing.lg)
            ) {
                itemsIndexed(
                    items = filteredLogs,
                    key = { _, item -> "${item.id}_${item.timestamp}" }
                ) { index, log ->
                    LogRow(
                        log = log,
                        timeStr = sdf.format(Date(log.timestamp))
                    )
                    if (index < filteredLogs.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = (Spacing.md + 3.dp)),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }

    // Confirmation dialog for clear
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Clear all logs?") },
            text = { Text("This will permanently delete all log entries. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllLogs()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Variant for displaying inside a dialog (e.g. from another screen).
 */
@Composable
fun LogsScreenDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss")
                    }
                }
                HorizontalDivider()
                Box(modifier = Modifier.weight(1f)) {
                    LogsScreen(viewModel = viewModel)
                }
            }
        }
    }
}

// ─── Internal composables ─────────────────────────────────────────────────────

@Composable
private fun FilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
private fun LogRow(log: AppLog, timeStr: String) {
    var expanded by remember { mutableStateOf(false) }

    val borderColor = borderColorForType(log.type)
    val chipColor = chipColorForType(log.type)
    val typeLabel = labelForType(log.type)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        // Colored left border strip
        Box(
            modifier = Modifier
                .padding(start = Spacing.md)
                .width(3.dp)
                .heightIn(min = 40.dp)
                .fillMaxHeight()
                .background(
                    color = borderColor,
                    shape = MaterialTheme.shapes.extraSmall
                )
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.sm, end = Spacing.md)
        ) {
            // Meta row: timestamp + type chip + symbol chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline()
                )

                // Type chip
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = chipColor.copy(alpha = 0.12f),
                    modifier = Modifier.alignByBaseline()
                ) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = chipColor,
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                    )
                }

                // Symbol chip (only when non-null)
                log.symbol?.let { sym ->
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.alignByBaseline()
                    ) {
                        Text(
                            text = sym,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xxs))

            // Message — 3 lines collapsed, fully expanded on tap
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
