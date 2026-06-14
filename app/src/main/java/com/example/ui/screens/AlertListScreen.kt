package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Alert
import com.example.data.model.SymbolInfo
import com.example.data.model.formatPriceDynamic
import com.example.data.model.getDisplayDecimals
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

// ─── PRIORITY COLOR ──────────────────────────────────────────────────────────
@Composable
private fun priorityColor(priority: String): Color = when (priority.uppercase()) {
    "CRITICAL" -> MaterialTheme.colorScheme.error
    "HIGH"     -> Color(0xFFFF8F00)
    "MEDIUM"   -> MaterialTheme.colorScheme.primary
    else       -> MaterialTheme.colorScheme.outline  // LOW
}

// ─── CONDITION TEXT ──────────────────────────────────────────────────────────
private fun conditionText(condition: String, price: String): String = when (condition) {
    "CROSSING_UP"   -> "↑ above $price"
    "CROSSING_DOWN" -> "↓ below $price"
    else            -> "⇅ crosses $price"
}

// ─── SCREEN ──────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertListScreen(viewModel: MainViewModel) {
    val alerts by viewModel.alertList.collectAsState()
    val priceState by viewModel.priceState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingAlert    by remember { mutableStateOf<Alert?>(null) }
    var searchVisible   by remember { mutableStateOf(false) }
    var searchQuery     by remember { mutableStateOf("") }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    val activeCount = alerts.count { it.isActive }
    val pausedCount = alerts.size - activeCount

    val filtered = remember(alerts, searchQuery) {
        if (searchQuery.isBlank()) alerts
        else alerts.filter { a ->
            a.symbol.contains(searchQuery, ignoreCase = true) ||
            a.title.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Alerts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                actions = {
                    IconButton(onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) searchQuery = ""
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Search alerts")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create alert")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Animated search bar
            AnimatedVisibility(
                visible = searchVisible,
                enter = expandVertically(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)) + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by symbol or title") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                )
            }

            if (alerts.isNotEmpty()) {
                // Compact stats chip row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Active: $activeCount", style = MaterialTheme.typography.labelMedium) },
                        icon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = ChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Paused: $pausedCount", style = MaterialTheme.typography.labelMedium) },
                        icon = {
                            Icon(
                                Icons.Default.PauseCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = ChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Total: ${alerts.size}", style = MaterialTheme.typography.labelMedium) },
                        icon = {
                            Icon(
                                Icons.Default.List,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                // Batch action row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { viewModel.activateAllAlerts() }) {
                        Text("Activate All", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { viewModel.deactivateAllAlerts() }) {
                        Text("Pause All", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { showDeleteAllConfirm = true }) {
                        Text(
                            "Delete All",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Content area: empty state or list
            if (filtered.isEmpty() && !searchVisible) {
                AlertListEmptyState(onAdd = { showCreateDialog = true })
            } else if (filtered.isEmpty() && searchVisible) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No alerts match \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp) // clear FAB
                ) {
                    items(filtered, key = { it.id }) { alert ->
                        AlertSwipeDismissItem(
                            alert = alert,
                            onDelete = { viewModel.deleteAlert(alert.id) },
                            onToggle = { viewModel.toggleAlertActive(alert.id, it) },
                            onEdit   = { editingAlert = alert },
                            currentPrice = priceState[alert.symbol]?.price
                        )
                    }
                }
            }
        }
    }

    // ─── Dialogs ─────────────────────────────────────────────────────────────
    if (showCreateDialog) {
        CreateAlertDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { symbol, cond, price, isOneTime, priority, msg ->
                viewModel.createAlert(
                    symbol = symbol,
                    condition = cond,
                    targetPrice = price,
                    title = "$symbol crossed target",
                    message = msg.ifBlank { "Crossing detected. Price exceeded $price threshold." },
                    isOneTime = isOneTime,
                    priority = priority,
                    colorTagIndex = 0
                )
                showCreateDialog = false
            }
        )
    }

    if (editingAlert != null) {
        EditAlertDialog(
            alert = editingAlert!!,
            onDismiss = { editingAlert = null },
            onUpdate = { id, symbol, cond, price, isOneTime, priority, msg ->
                viewModel.updateAlert(
                    id = id,
                    symbol = symbol,
                    condition = cond,
                    targetPrice = price,
                    title = "$symbol crossed target",
                    message = msg.ifBlank { "Crossing detected. Price exceeded $price threshold." },
                    isActive = editingAlert!!.isActive,
                    isOneTime = isOneTime,
                    priority = priority
                )
                editingAlert = null
            }
        )
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAllAlerts(); showDeleteAllConfirm = false }) {
                    Text("Delete all", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text("Keep") }
            },
            icon = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete all alerts?") },
            text = {
                Text(
                    "This permanently removes all ${alerts.size} alert rules and their trigger history. " +
                    "This cannot be undone."
                )
            }
        )
    }
}

// ─── SWIPE-TO-DISMISS WRAPPER ────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertSwipeDismissItem(
    alert: Alert,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    currentPrice: Double? = null
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { it == SwipeToDismissBoxValue.EndToStart }
    )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(end = Spacing.md),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    ) {
        AlertListItem(alert = alert, onToggle = onToggle, onEdit = onEdit, currentPrice = currentPrice)
    }
}

// ─── COMPACT ALERT LIST ITEM (~72dp) ─────────────────────────────────────────
@Composable
internal fun AlertListItem(
    alert: Alert,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    currentPrice: Double? = null
) {
    val info      = SymbolInfo.find(alert.symbol)
    val price     = alert.targetPrice.formatPriceDynamic(info.getDisplayDecimals())
    val condText  = conditionText(alert.condition, price)
    val accentColor = priorityColor(alert.priority)
    val contentAlpha = if (alert.isActive) 1f else 0.5f

    val lastTriggeredText = "Never triggered"

    Surface(
        color = accentColor.copy(alpha = 0.04f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Priority accent strip
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(
                        color = accentColor.copy(alpha = contentAlpha),
                        shape = RoundedCornerShape(Radius.pill)
                    )
            )

            Spacer(modifier = Modifier.width(Spacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                // Line 1: symbol chip + condition text + priority badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = accentColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = alert.symbol,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor.copy(alpha = contentAlpha),
                            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = condText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Priority badge
                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = accentColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = alert.priority.take(4).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor.copy(alpha = contentAlpha),
                            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                        )
                    }
                }

                // Current price row (shown when price data is available)
                val currentPriceText = currentPrice?.let {
                    val priceInfo = SymbolInfo.find(alert.symbol)
                    "Now: ${it.formatPriceDynamic(priceInfo.getDisplayDecimals())}"
                } ?: ""
                if (currentPriceText.isNotEmpty()) {
                    Text(
                        text = currentPriceText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f * contentAlpha),
                        maxLines = 1
                    )
                }

                // Line 2: last triggered text (left) + switch (right)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = lastTriggeredText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = alert.isActive,
                        onCheckedChange = onToggle,
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ─── FULL-SCREEN EMPTY STATE ─────────────────────────────────────────────────
@Composable
private fun AlertListEmptyState(onAdd: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                "No alerts yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Monitor price levels and get instant notifications",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.xl)
            )
            Button(onClick = onAdd) {
                Text("Add Alert")
            }
        }
    }
}

// ─── CREATE / EDIT DIALOGS ────────────────────────────────────────────────────
@Composable
fun CreateAlertDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Double, Boolean, String, String) -> Unit
) {
    AlertEditorDialog(
        dialogTitle    = "New alert",
        confirmLabel   = "Create",
        initialSymbol    = SymbolInfo.ALL.first().symbol,
        initialCondition = "CROSSING",
        initialPrice     = "",
        initialMessage   = "",
        initialOneTime   = true,
        initialPriority  = "HIGH",
        onDismiss  = onDismiss,
        onConfirm  = { symbol, cond, price, isOneTime, priority, msg ->
            onCreate(symbol, cond, price, isOneTime, priority, msg)
        }
    )
}

@Composable
fun EditAlertDialog(
    alert: Alert,
    onDismiss: () -> Unit,
    onUpdate: (id: Int, symbol: String, condition: String, price: Double, isOneTime: Boolean, priority: String, message: String) -> Unit
) {
    AlertEditorDialog(
        dialogTitle    = "Edit alert",
        confirmLabel   = "Save",
        initialSymbol    = alert.symbol,
        initialCondition = alert.condition,
        initialPrice     = alert.targetPrice.toString(),
        initialMessage   = alert.message,
        initialOneTime   = alert.isOneTime,
        initialPriority  = alert.priority,
        onDismiss  = onDismiss,
        onConfirm  = { symbol, cond, price, isOneTime, priority, msg ->
            onUpdate(alert.id, symbol, cond, price, isOneTime, priority, msg)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertEditorDialog(
    dialogTitle: String,
    confirmLabel: String,
    initialSymbol: String,
    initialCondition: String,
    initialPrice: String,
    initialMessage: String,
    initialOneTime: Boolean,
    initialPriority: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double, Boolean, String, String) -> Unit
) {
    var symbol         by remember { mutableStateOf(initialSymbol) }
    var condition      by remember { mutableStateOf(initialCondition) }
    var priceInput     by remember { mutableStateOf(initialPrice) }
    var message        by remember { mutableStateOf(initialMessage) }
    var isOneTime      by remember { mutableStateOf(initialOneTime) }
    var priority       by remember { mutableStateOf(initialPriority) }
    var symbolExpanded by remember { mutableStateOf(false) }

    val parsedPrice = priceInput.toDoubleOrNull()
    val isValid = parsedPrice != null && parsedPrice > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { if (isValid) onConfirm(symbol, condition, parsedPrice!!, isOneTime, priority, message) },
                enabled = isValid
            ) { Text(confirmLabel, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = {
            Text(dialogTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                // Asset picker
                ExposedDropdownMenuBox(
                    expanded = symbolExpanded,
                    onExpandedChange = { symbolExpanded = !symbolExpanded }
                ) {
                    OutlinedTextField(
                        value = symbol,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Asset") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = symbolExpanded) },
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = symbolExpanded,
                        onDismissRequest = { symbolExpanded = false }
                    ) {
                        SymbolInfo.ALL.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${s.symbol} — ${s.name}") },
                                onClick = { symbol = s.symbol; symbolExpanded = false }
                            )
                        }
                    }
                }

                // Condition
                Column {
                    Text(
                        "Condition",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    val conds = listOf(
                        "CROSSING"      to "Any",
                        "CROSSING_UP"   to "Up",
                        "CROSSING_DOWN" to "Down"
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        conds.forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = condition == value,
                                onClick  = { condition = value },
                                shape    = SegmentedButtonDefaults.itemShape(index, conds.size)
                            ) { Text(label) }
                        }
                    }
                }

                // Target price
                OutlinedTextField(
                    value = priceInput,
                    onValueChange = { priceInput = it },
                    label = { Text("Target price") },
                    placeholder = { Text("e.g. 2318.50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = priceInput.isNotEmpty() && !isValid,
                    supportingText = {
                        if (priceInput.isNotEmpty() && !isValid) {
                            Text("Enter a valid price greater than 0", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier.fillMaxWidth()
                )

                // Priority selector
                Column {
                    Text(
                        "Priority",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").forEach { p ->
                            val selected = priority == p
                            val c: Color = when (p) {
                                "CRITICAL" -> MaterialTheme.colorScheme.error
                                "HIGH"     -> Color(0xFFFF8F00)
                                "MEDIUM"   -> MaterialTheme.colorScheme.primary
                                else       -> MaterialTheme.colorScheme.outline
                            }
                            Surface(
                                onClick = { priority = p },
                                shape = RoundedCornerShape(Radius.sm),
                                color = if (selected) c else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = Spacing.sm)
                                ) {
                                    Text(
                                        text = p.take(if (p == "CRITICAL") 4 else 3),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (selected) Color.White
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Optional message
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message (optional)") },
                    shape = RoundedCornerShape(Radius.md),
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                // One-time toggle
                Surface(
                    shape = RoundedCornerShape(Radius.md),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "One-time",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Auto-pause after it fires once",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = isOneTime, onCheckedChange = { isOneTime = it })
                    }
                }
            }
        }
    )
}

@Composable
fun AlertRuleItem(
    alert: Alert,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEditClick: () -> Unit
) {
    AlertListItem(alert = alert, onToggle = onToggleActive, onEdit = onEditClick)
}
