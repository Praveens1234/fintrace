package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

// ─── PRIORITY / CONDITION METADATA ──────────────────────────────────────────
private fun priorityColor(priority: String): Color = when (priority.uppercase()) {
    "LOW" -> AlertExpired
    "MEDIUM" -> AlertTriggered
    "HIGH" -> AlertActive
    "CRITICAL" -> AlertCritical
    else -> AlertActive
}

private data class ConditionMeta(val label: String, val icon: ImageVector)

private fun conditionMeta(condition: String): ConditionMeta = when (condition) {
    "CROSSING_UP" -> ConditionMeta("Crossing up", Icons.AutoMirrored.Filled.TrendingUp)
    "CROSSING_DOWN" -> ConditionMeta("Crossing down", Icons.AutoMirrored.Filled.TrendingDown)
    else -> ConditionMeta("Any crossing", Icons.AutoMirrored.Filled.CompareArrows)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertListScreen(viewModel: MainViewModel) {
    val alerts by viewModel.alertList.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingAlert by remember { mutableStateOf<Alert?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // All, Active, Triggered
    var showBatchMenu by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val total = alerts.size
    val active = alerts.count { it.isActive }
    val paused = total - active

    val filtered = alerts.filter { alert ->
        val q = searchQuery.trim()
        val matchesSearch = q.isEmpty() ||
            alert.symbol.contains(q, ignoreCase = true) ||
            alert.title.contains(q, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            "Active" -> alert.isActive
            "Triggered" -> !alert.isActive
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Price Alerts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "$active active · $paused paused",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (alerts.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showBatchMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Batch actions")
                            }
                            DropdownMenu(
                                expanded = showBatchMenu,
                                onDismissRequest = { showBatchMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Resume all") },
                                    leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                                    onClick = { viewModel.activateAllAlerts(); showBatchMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Pause all") },
                                    leadingIcon = { Icon(Icons.Default.Pause, null) },
                                    onClick = { viewModel.deactivateAllAlerts(); showBatchMenu = false }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete all", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = { showBatchMenu = false; confirmDeleteAll = true }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New alert", fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Compact summary
            if (alerts.isNotEmpty()) {
                AlertsSummaryBar(total = total, active = active, paused = paused)
            }

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by symbol") },
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

            // Filter (segmented, replaces the broken chips)
            val filters = listOf("All", "Active", "Triggered")
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                filters.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedFilter == label,
                        onClick = { selectedFilter = label },
                        shape = SegmentedButtonDefaults.itemShape(index, filters.size)
                    ) { Text(label) }
                }
            }

            if (filtered.isEmpty()) {
                AlertsEmptyState(
                    isFiltered = searchQuery.isNotEmpty() || selectedFilter != "All",
                    onCreate = { showCreateDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.md, end = Spacing.md,
                        top = Spacing.sm, bottom = 96.dp // clear the FAB
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(filtered, key = { it.id }) { alert ->
                        AlertRuleItem(
                            alert = alert,
                            onToggleActive = { viewModel.toggleAlertActive(alert.id, it) },
                            onDelete = { viewModel.deleteAlert(alert.id) },
                            onEditClick = { editingAlert = alert }
                        )
                    }
                }
            }
        }
    }

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

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAllAlerts(); confirmDeleteAll = false }) {
                    Text("Delete all", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Keep") } },
            icon = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete all alerts?") },
            text = { Text("This permanently removes all $total alert rules and their trigger history. This cannot be undone.") }
        )
    }
}

@Composable
private fun AlertsSummaryBar(total: Int, active: Int, paused: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryStat("Total", total.toString(), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            VerticalDivider(modifier = Modifier.height(28.dp))
            SummaryStat("Active", active.toString(), ConnectionLive, Modifier.weight(1f))
            VerticalDivider(modifier = Modifier.height(28.dp))
            SummaryStat("Paused", paused.toString(), MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = PriceTextFontFamily),
            fontWeight = FontWeight.ExtraBold,
            color = valueColor
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AlertsEmptyState(isFiltered: Boolean, onCreate: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .padding(Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(Radius.xl),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxSize()
                ) {}
                Icon(
                    Icons.Default.NotificationsNone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                if (isFiltered) "No matching alerts" else "No alerts yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                if (isFiltered) "Try a different search or filter."
                else "Create a price-crossing rule to get notified the moment a target is hit.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.md)
            )
            if (!isFiltered) {
                Spacer(Modifier.height(Spacing.md))
                Button(onClick = onCreate, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Create your first alert")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertRuleItem(
    alert: Alert,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEditClick: () -> Unit
) {
    val info = SymbolInfo.find(alert.symbol)
    val price = alert.targetPrice.formatPriceDynamic(info.getDisplayDecimals())
    val accent = priorityColor(alert.priority)
    val cond = conditionMeta(alert.condition)
    val active = alert.isActive
    val contentAlpha = if (active) 1f else 0.55f

    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Card(
        onClick = onEditClick,
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = if (active) 2.dp else 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Slim, solid priority accent (no distracting glow)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (active) accent else accent.copy(alpha = 0.4f))
            )
            Column(modifier = Modifier.weight(1f).padding(Spacing.md)) {
                // Top row: identity + controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Surface(
                            shape = RoundedCornerShape(Radius.md),
                            color = accent.copy(alpha = 0.14f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    info.symbol.take(2).uppercase(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = accent
                                )
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(
                                    alert.symbol,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                                )
                                PriorityChip(alert.priority, accent, active)
                            }
                            Text(
                                info.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = active,
                            onCheckedChange = onToggleActive
                        )
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { menuOpen = false; onEditClick() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; confirmDelete = true }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.sm))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Spacing.sm))

                // Bottom row: condition + threshold
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Icon(
                            cond.icon,
                            contentDescription = null,
                            tint = accent.copy(alpha = contentAlpha),
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                cond.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                            )
                            Text(
                                if (alert.isOneTime) "One-time" else "Repeating",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "TARGET",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            price,
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = PriceTextFontFamily),
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                        )
                    }
                }

                // Optional custom memo
                if (alert.message.isNotBlank() && !alert.message.startsWith("Crossing detected.")) {
                    Spacer(Modifier.height(Spacing.sm))
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            alert.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
            title = { Text("Delete this alert?") },
            text = { Text("${alert.symbol} · target $price will be removed.") }
        )
    }
}

@Composable
private fun PriorityChip(priority: String, accent: Color, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(Radius.pill),
        color = accent.copy(alpha = if (active) 0.16f else 0.08f)
    ) {
        Text(
            priority.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = accent.copy(alpha = if (active) 1f else 0.6f),
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
        )
    }
}

// ─── CREATE / EDIT DIALOGS (share one editor form) ───────────────────────────
@Composable
fun CreateAlertDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Double, Boolean, String, String) -> Unit
) {
    AlertEditorDialog(
        dialogTitle = "New alert",
        confirmLabel = "Create",
        initialSymbol = SymbolInfo.ALL.first().symbol,
        initialCondition = "CROSSING",
        initialPrice = "",
        initialMessage = "",
        initialOneTime = true,
        initialPriority = "HIGH",
        onDismiss = onDismiss,
        onConfirm = { symbol, cond, price, isOneTime, priority, msg ->
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
        dialogTitle = "Edit alert",
        confirmLabel = "Save",
        initialSymbol = alert.symbol,
        initialCondition = alert.condition,
        initialPrice = alert.targetPrice.toString(),
        initialMessage = alert.message,
        initialOneTime = alert.isOneTime,
        initialPriority = alert.priority,
        onDismiss = onDismiss,
        onConfirm = { symbol, cond, price, isOneTime, priority, msg ->
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
    var symbol by remember { mutableStateOf(initialSymbol) }
    var condition by remember { mutableStateOf(initialCondition) }
    var priceInput by remember { mutableStateOf(initialPrice) }
    var message by remember { mutableStateOf(initialMessage) }
    var isOneTime by remember { mutableStateOf(initialOneTime) }
    var priority by remember { mutableStateOf(initialPriority) }
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
        title = { Text(dialogTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) },
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
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = symbolExpanded, onDismissRequest = { symbolExpanded = false }) {
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
                    Text("Condition", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.xs))
                    val conds = listOf("CROSSING" to "Any", "CROSSING_UP" to "Up", "CROSSING_DOWN" to "Down")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        conds.forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = condition == value,
                                onClick = { condition = value },
                                shape = SegmentedButtonDefaults.itemShape(index, conds.size)
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

                // Priority
                Column {
                    Text("Priority", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
                        listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").forEach { p ->
                            val selected = priority == p
                            val c = priorityColor(p)
                            Surface(
                                onClick = { priority = p },
                                shape = RoundedCornerShape(Radius.sm),
                                color = if (selected) c else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = Spacing.sm)) {
                                    Text(
                                        p.take(if (p == "CRITICAL") 4 else 3),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Message
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("One-time", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Auto-pause after it fires once", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isOneTime, onCheckedChange = { isOneTime = it })
                    }
                }
            }
        }
    )
}
