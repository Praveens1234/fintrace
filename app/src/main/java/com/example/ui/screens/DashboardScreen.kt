package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PriceTick
import com.example.data.model.SymbolInfo
import com.example.data.model.formatPriceDynamic
import com.example.data.model.getDisplayDecimals
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.text.DecimalFormat

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onSymbolSelected: (String) -> Unit,
    onQuickAlertRequest: (String) -> Unit
) {
    val activeSub by viewModel.activeSymbols.collectAsState()
    val priceState by viewModel.priceState.collectAsState()
    val connStatus by viewModel.connectionStatus.collectAsState()
    val latency by viewModel.latencyMs.collectAsState()
    val marketOpen by viewModel.marketOpen.collectAsState()
    val nextMarketChangeAt by viewModel.nextMarketChangeAt.collectAsState()
    val cardStyle by viewModel.dashboardCardStyle.collectAsState()
    val priceTextSize by viewModel.priceTextSize.collectAsState()
    val symbolIdTextSize by viewModel.symbolIdTextSize.collectAsState()
    val symbolNameTextSize by viewModel.symbolNameTextSize.collectAsState()
    val alertList by viewModel.alertList.collectAsState()

    val resolvedPriceSize = remember(priceTextSize, cardStyle) {
        if (priceTextSize > 0f) {
            priceTextSize.sp
        } else {
            when (cardStyle) {
                "Classic Row" -> 24.sp
                "Compact" -> 18.sp
                else -> 42.sp
            }
        }
    }

    val resolvedSymbolIdSize = remember(symbolIdTextSize, cardStyle) {
        if (symbolIdTextSize > 0f) {
            symbolIdTextSize.sp
        } else {
            when (cardStyle) {
                "Classic Row" -> 16.sp
                "Compact" -> 14.sp
                else -> 18.sp
            }
        }
    }

    val resolvedSymbolNameSize = remember(symbolNameTextSize, cardStyle) {
        if (symbolNameTextSize > 0f) {
            symbolNameTextSize.sp
        } else {
            when (cardStyle) {
                "Classic Row" -> 12.sp
                "Compact" -> 10.sp
                else -> 12.sp
            }
        }
    }

    var showManageAssetsDialog by remember { mutableStateOf(false) }
    val maxLimit = SymbolInfo.ALL.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Branded Page Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FinTrace Ticker",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Real-time assets monitoring",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Asset manager action — minimum 48dp touch target for accessibility
            Button(
                onClick = { showManageAssetsDialog = true },
                shape = RoundedCornerShape(Radius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                modifier = Modifier.heightIn(min = MinTouchTarget)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = "Manage",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Connection & Diagnostic Status Bar
        ConnectionStatusBar(
            status = connStatus,
            latency = latency,
            activeCount = activeSub.size,
            maxLimit = maxLimit
        )

        // Market-closed banner with a live countdown to the next session open.
        if (!marketOpen) {
            MarketClosedBanner(nextChangeAt = nextMarketChangeAt)
        }

        if (activeSub.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(Radius.xl))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddChart,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    Text(
                        text = "No active symbols yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = "Add assets to your portfolio to start tracking live prices and trigger alerts.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md)
                    )
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    Button(
                        onClick = { showManageAssetsDialog = true },
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier.heightIn(min = MinTouchTarget)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Add your first asset")
                    }
                }
            }
        } else {
            if (cardStyle == "Classic Row") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    val activeTicks = activeSub.map { sym ->
                        priceState[sym] ?: PriceTick(
                            symbol = sym,
                            price = SymbolInfo.find(sym).defaultPrice,
                            bid = SymbolInfo.find(sym).defaultPrice * 0.9996,
                            ask = SymbolInfo.find(sym).defaultPrice * 1.0004,
                            history = listOf(SymbolInfo.find(sym).defaultPrice),
                            openPrice = SymbolInfo.find(sym).defaultPrice
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(activeTicks, key = { it.symbol }) { tick ->
                            val activeAlertsForTick = alertList.filter { it.symbol.equals(tick.symbol, ignoreCase = true) && it.isActive }
                            PriceMetricClassicRow(
                                tick = tick,
                                connectionStatus = connStatus,
                                priceSize = resolvedPriceSize,
                                symbolIdSize = resolvedSymbolIdSize,
                                symbolNameSize = resolvedSymbolNameSize,
                                activeAlerts = activeAlertsForTick,
                                onTap = { onSymbolSelected(tick.symbol) },
                                onLongPress = { onQuickAlertRequest(tick.symbol) }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            } else {
                val gridCells = if (cardStyle == "Compact") GridCells.Fixed(2) else GridCells.Fixed(1)
                LazyVerticalGrid(
                    columns = gridCells,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val activeTicks = activeSub.map { sym ->
                        priceState[sym] ?: PriceTick(
                            symbol = sym,
                            price = SymbolInfo.find(sym).defaultPrice,
                            bid = SymbolInfo.find(sym).defaultPrice * 0.9996,
                            ask = SymbolInfo.find(sym).defaultPrice * 1.0004,
                            history = listOf(SymbolInfo.find(sym).defaultPrice),
                            openPrice = SymbolInfo.find(sym).defaultPrice
                        )
                    }
                    items(activeTicks, key = { it.symbol }) { tick ->
                        val activeAlertsForTick = alertList.filter { it.symbol.equals(tick.symbol, ignoreCase = true) && it.isActive }
                        PriceMetricCard(
                            tick = tick,
                            connectionStatus = connStatus,
                            cardStyle = cardStyle,
                            priceSize = resolvedPriceSize,
                            symbolIdSize = resolvedSymbolIdSize,
                            symbolNameSize = resolvedSymbolNameSize,
                            activeAlerts = activeAlertsForTick,
                            onTap = { onSymbolSelected(tick.symbol) },
                            onLongPress = { onQuickAlertRequest(tick.symbol) }
                        )
                    }
                }
            }
        }
    }

    // Asset Management Dialog
    if (showManageAssetsDialog) {
        ManageAssetsDialog(
            currentActiveSymbols = activeSub,
            allSymbols = SymbolInfo.ALL,
            maxLimit = maxLimit,
            onToggle = { symbol, active ->
                viewModel.toggleSymbolActive(symbol, active)
            },
            onDismiss = { showManageAssetsDialog = false }
        )
    }
}

@Composable
fun ConnectionStatusBar(
    status: String,
    latency: Long,
    activeCount: Int,
    maxLimit: Int
) {
    val (dotColor, statusText) = when (status) {
        "LIVE" -> Pair(ConnectionLive, "LIVE")
        "CONNECTING" -> Pair(ConnectionReconnecting, "CONNECTING")
        "CLOSED" -> Pair(NeutralPrice, "MARKET CLOSED")
        else -> Pair(ConnectionOffline, "OFFLINE")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(dotColor)
            )
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = dotColor,
                    fontSize = 11.sp
                )
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "$activeCount / $maxLimit Monitored",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 11.sp
            )
            
            if (status == "LIVE") {
                val pingColor = if (latency < 300) ConnectionLive else ConnectionReconnecting
                Text(
                    text = "Ping: ${latency}ms",
                    style = MaterialTheme.typography.labelMedium,
                    color = pingColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun MarketClosedBanner(nextChangeAt: Long) {
    // Live 1-second countdown to the next session open.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(nextChangeAt) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val remaining = (nextChangeAt - nowMs).coerceAtLeast(0L)
    val totalMin = remaining / 60000
    val days = totalMin / (60 * 24)
    val hours = (totalMin / 60) % 24
    val minutes = totalMin % 60
    val opensIn = when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Market closed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Live updates paused to save battery · resumes automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "OPENS IN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    opensIn,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = PriceTextFontFamily),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ManageAssetsDialog(
    currentActiveSymbols: List<String>,
    allSymbols: List<SymbolInfo>,
    maxLimit: Int,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", fontWeight = FontWeight.Black)
            }
        },
        title = {
            Column {
                Text(
                    text = "Asset Portfolio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Active Tracking: ${currentActiveSymbols.size} / $maxLimit Max",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentActiveSymbols.size >= maxLimit) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Max limit of $maxLimit reached. Please disable a monitored asset to add a new one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(allSymbols) { info ->
                        val isChecked = currentActiveSymbols.contains(info.symbol)
                        val isLimitReached = currentActiveSymbols.size >= maxLimit && !isChecked
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .clickable(enabled = !isLimitReached) {
                                    onToggle(info.symbol, !isChecked)
                                }
                                .padding(vertical = 6.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = info.symbol,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = info.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { active ->
                                    if (!isLimitReached) {
                                        onToggle(info.symbol, active)
                                    }
                                },
                                enabled = !isLimitReached,
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PriceMetricCard(
    tick: PriceTick,
    connectionStatus: String,
    cardStyle: String,
    priceSize: androidx.compose.ui.unit.TextUnit,
    symbolIdSize: androidx.compose.ui.unit.TextUnit,
    symbolNameSize: androidx.compose.ui.unit.TextUnit,
    activeAlerts: List<com.example.data.model.Alert> = emptyList(),
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val info = SymbolInfo.find(tick.symbol)
    val isLight = (MaterialTheme.colorScheme.background.red + MaterialTheme.colorScheme.background.green + MaterialTheme.colorScheme.background.blue) > 1.5f

    // Flashing animations on price updates
    var prevPrice by remember { mutableStateOf(tick.price) }
    var flashColor by remember { mutableStateOf(Color.Transparent) }

    LaunchedEffect(tick.price) {
        if (tick.price > prevPrice) {
            flashColor = (if (isLight) PriceUpLight else PriceUpDark).copy(alpha = 0.25f)
            delay(120)
            flashColor = Color.Transparent
        } else if (tick.price < prevPrice) {
            flashColor = (if (isLight) PriceDownLight else PriceDownDark).copy(alpha = 0.25f)
            delay(120)
            flashColor = Color.Transparent
        }
        prevPrice = tick.price
    }

    val arrowSymbol = if (tick.change >= 0) "▲" else "▼"
    val changeColor = if (tick.change >= 0) {
        if (isLight) PriceUpLight else PriceUpDark
    } else {
        if (isLight) PriceDownLight else PriceDownDark
    }

    // STATE CONTROLLERS
    val isOffline = connectionStatus == "OFFLINE" || connectionStatus == "CLOSED"
    val isClosed = connectionStatus == "CLOSED"
    val elapsedMs = System.currentTimeMillis() - tick.timestamp
    val isStale = !isOffline && (elapsedMs > 30000)
    val isAlerted = activeAlerts.isNotEmpty()

    val displayOpacity = when {
        isOffline -> 0.5f
        isStale -> 0.6f
        else -> 1.0f
    }

    // HIGHTEST PRIORITY RESOLUTION
    val highestPriority = remember(activeAlerts) {
        if (activeAlerts.isEmpty()) "LOW"
        else {
            val priorities = activeAlerts.map { it.priority.uppercase() }
            when {
                priorities.contains("CRITICAL") -> "CRITICAL"
                priorities.contains("HIGH") -> "HIGH"
                priorities.contains("MEDIUM") -> "MEDIUM"
                else -> "LOW"
            }
        }
    }

    val alertStyleColor = when (highestPriority) {
        "LOW" -> Color(0xFF4CAF50) // Green
        "MEDIUM" -> AlertActive       // Amber #FFB300
        "HIGH" -> Color(0xFFFF6D00)    // Orange #FF6D00
        "CRITICAL" -> AlertCritical    // Red #FF1744
        else -> AlertActive
    }

    // Calm, static accents (no distracting 1Hz pulse; also avoids a continuous animation).
    val cardBorder = when {
        isClosed -> BorderStroke(1.dp, NeutralPrice.copy(alpha = 0.4f))
        isOffline -> BorderStroke(1.dp, ConnectionOffline.copy(alpha = 0.55f))
        isAlerted -> BorderStroke(1.5.dp, alertStyleColor.copy(alpha = 0.5f))
        else -> null
    }

    val staleStroke = if (isStale) Stroke(
        width = 3f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
    ) else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(flashColor)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .then(
                if (isStale) {
                    Modifier.drawBehind {
                        staleStroke?.let { stroke ->
                            drawRoundRect(
                                color = AlertActive,
                                style = stroke,
                                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                            )
                        }
                    }
                } else Modifier
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = cardBorder
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .alpha(displayOpacity)
        ) {
            val displayDecs = info.getDisplayDecimals()

            // Header status / State overlays
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Symbol ID & Full Description
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = tick.symbol,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = symbolIdSize,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Symbol Category Dot Indicator
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when (info.category.lowercase()) {
                                    "metals" -> Color(0xFFFFD700)
                                    "majors" -> MaterialTheme.colorScheme.primary
                                    else -> Color.Gray
                                }
                            )
                    )
                }

                // Status Badge / Overlays matching State requirements
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                isClosed -> NeutralPrice.copy(alpha = 0.12f)
                                isOffline -> ConnectionOffline.copy(alpha = 0.12f)
                                isStale -> AlertActive.copy(alpha = 0.12f)
                                isAlerted -> alertStyleColor.copy(alpha = 0.15f)
                                else -> ConnectionLive.copy(alpha = 0.12f)
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(
                                    when {
                                        isClosed -> NeutralPrice
                                        isOffline -> ConnectionOffline
                                        isStale -> AlertActive
                                        isAlerted -> alertStyleColor
                                        else -> ConnectionLive
                                    }
                                )
                        )
                        Text(
                            text = when {
                                isClosed -> "CLOSED"
                                isOffline -> "OFFLINE"
                                isStale -> "STALE"
                                isAlerted -> "WATCH: $highestPriority"
                                else -> "LIVE ⚡"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = when {
                                isClosed -> NeutralPrice
                                isOffline -> ConnectionOffline
                                isStale -> AlertActive
                                isAlerted -> alertStyleColor
                                else -> ConnectionLive
                            },
                            fontSize = 8.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Large Dominant Price Display (Anchors the Grid cell)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = tick.price.formatPriceDynamic(displayDecs),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = PriceTextFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = priceSize,
                        letterSpacing = (-1).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Net movements and percentage movement block
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "$arrowSymbol " + tick.change.formatPriceDynamic(displayDecs).replace("+", "").replace("-", ""),
                        color = changeColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = PriceTextFontFamily, fontSize = 13.sp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = String.format("%.2f%%", kotlin.math.abs(tick.changePercent)),
                        color = changeColor,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp)
                    )
                }

                Text(
                    text = info.name,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = symbolNameSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }

            // Supporting Info: Bid / Ask / Spread / Sparkline (Hidden in Compact mode)
            if (cardStyle != "Compact") {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "BID / ASK FEED",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "B: ${tick.bid.formatPriceDynamic(displayDecs)}  |  A: ${tick.ask.formatPriceDynamic(displayDecs)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = PriceTextFontFamily, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val spread = if (tick.ask > tick.bid) tick.ask - tick.bid else 0.0001
                    val multiplier = java.lang.Math.pow(10.0, displayDecs.toDouble())
                    val spreadInt = kotlin.math.round(spread * multiplier).toInt()
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "SPREAD",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "$spreadInt PTS",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = PriceTextFontFamily, fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Canvas Sparkline
                if (tick.history.size > 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                            .padding(vertical = 3.dp, horizontal = 10.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val width = size.width
                            val height = size.height

                            val minVal = tick.history.minOrNull() ?: 1.0
                            val maxVal = tick.history.maxOrNull() ?: 1.0
                            val diff = if (maxVal - minVal > 0.0) maxVal - minVal else 1.0

                            val stepX = width / (tick.history.size - 1)
                            val path = Path()

                            tick.history.forEachIndexed { i, p ->
                                val x = i * stepX
                                val normalizeY = (p - minVal) / diff
                                val y = height - (normalizeY * height).toFloat()

                                if (i == 0) {
                                    path.moveTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                }
                            }

                            drawPath(
                                path = path,
                                color = changeColor,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                // Exact state-dependent elapsed time label at bottom
                Text(
                    text = when {
                        isClosed -> "market closed (last price)"
                        isOffline -> "offline (last known price)"
                        isStale -> "⚠ stale (${elapsedMs / 1000}s ago)"
                        else -> "updated ${elapsedMs / 1000}s ago"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (isStale) AlertActive else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun AssetOverlapIcon(symbol: String, modifier: Modifier = Modifier) {
    val parts = symbol.split("/")
    val base = parts.getOrNull(0) ?: symbol.take(3)
    val target = parts.getOrNull(1) ?: symbol.drop(3).take(3)

    val (baseEmoji, baseColor) = when (base.uppercase()) {
        "XAU" -> Pair("🪙", Color(0xFFFFD700))
        "XAG" -> Pair("💿", Color(0xFFC0C0C0))
        "EUR" -> Pair("🇪🇺", Color(0xFF003399))
        "GBP" -> Pair("🇬🇧", Color(0xFFC8102E))
        "USD" -> Pair("🇺🇸", Color(0xFF002868))
        "JPY" -> Pair("🇯🇵", Color(0xFFBC002D))
        "CHF" -> Pair("🇨🇭", Color(0xFFD52B1E))
        "AUD" -> Pair("🇦🇺", Color(0xFF00008B))
        "CAD" -> Pair("🇨🇦", Color(0xFFFF0000))
        "NZD" -> Pair("🇳🇿", Color(0xFF00247D))
        else -> Pair("🌐", Color(0xFF4A4A4A))
    }

    val (targetEmoji, targetColor) = when (target.uppercase()) {
        "USD" -> Pair("🇺🇸", Color(0xFF002868))
        "JPY" -> Pair("🇯🇵", Color(0xFFBC002D))
        "GBP" -> Pair("🇬🇧", Color(0xFFC8102E))
        "AUD" -> Pair("🇦🇺", Color(0xFF00008B))
        "CHF" -> Pair("🇨🇭", Color(0xFFD52B1E))
        else -> Pair("🌐", Color(0xFF4A4A4A))
    }

    Box(modifier = modifier.size(42.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(baseColor.copy(alpha = 0.2f))
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            Text(baseEmoji, fontSize = 20.sp)
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(targetColor.copy(alpha = 0.25f))
                .align(Alignment.BottomEnd),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.dp)
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(targetEmoji, fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun PriceTextWithSuperscript(
    priceStr: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (priceStr.isEmpty()) return
    
    val lastChar = priceStr.last()
    val rest = priceStr.dropLast(1)
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = rest,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = PriceTextFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSize,
                letterSpacing = (-0.5).sp
            ),
            color = color,
            modifier = Modifier.alignByBaseline()
        )
        if (lastChar.isDigit()) {
            Text(
                text = lastChar.toString(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = PriceTextFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = (fontSize.value * 0.65f).sp
                ),
                color = color,
                modifier = Modifier
                    .padding(start = 1.dp)
                    .alignByBaseline()
            )
        } else {
            Text(
                text = lastChar.toString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = PriceTextFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = fontSize
                ),
                color = color,
                modifier = Modifier.alignByBaseline()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PriceMetricClassicRow(
    tick: PriceTick,
    connectionStatus: String,
    priceSize: androidx.compose.ui.unit.TextUnit,
    symbolIdSize: androidx.compose.ui.unit.TextUnit,
    symbolNameSize: androidx.compose.ui.unit.TextUnit,
    activeAlerts: List<com.example.data.model.Alert> = emptyList(),
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val info = SymbolInfo.find(tick.symbol)
    val isLight = (MaterialTheme.colorScheme.background.red + MaterialTheme.colorScheme.background.green + MaterialTheme.colorScheme.background.blue) > 1.5f
    val displayDecs = info.getDisplayDecimals()
    
    val changeColor = if (tick.change >= 0) {
        if (isLight) PriceUpLight else PriceUpDark
    } else {
        if (isLight) PriceDownLight else PriceDownDark
    }
    val changePrefix = if (tick.change >= 0) "+" else ""

    var prevPrice by remember { mutableStateOf(tick.price) }
    var flashColor by remember { mutableStateOf(Color.Transparent) }

    LaunchedEffect(tick.price) {
        if (tick.price > prevPrice) {
            flashColor = (if (isLight) PriceUpLight else PriceUpDark).copy(alpha = 0.15f)
            delay(120)
            flashColor = Color.Transparent
        } else if (tick.price < prevPrice) {
            flashColor = (if (isLight) PriceDownLight else PriceDownDark).copy(alpha = 0.15f)
            delay(120)
            flashColor = Color.Transparent
        }
        prevPrice = tick.price
    }

    // STATE CONTROLLERS
    val isOffline = connectionStatus == "OFFLINE" || connectionStatus == "CLOSED"
    val isClosed = connectionStatus == "CLOSED"
    val elapsedMs = System.currentTimeMillis() - tick.timestamp
    val isStale = !isOffline && (elapsedMs > 30000)
    val isAlerted = activeAlerts.isNotEmpty()

    val displayOpacity = when {
        isOffline -> 0.5f
        isStale -> 0.6f
        else -> 1.0f
    }

    // PRIORITY RESOLUTION FOR ROW
    val highestPriority = remember(activeAlerts) {
        if (activeAlerts.isEmpty()) "LOW"
        else {
            val priorities = activeAlerts.map { it.priority.uppercase() }
            when {
                priorities.contains("CRITICAL") -> "CRITICAL"
                priorities.contains("HIGH") -> "HIGH"
                priorities.contains("MEDIUM") -> "MEDIUM"
                else -> "LOW"
            }
        }
    }

    val alertStyleColor = when (highestPriority) {
        "LOW" -> Color(0xFF4CAF50)
        "MEDIUM" -> AlertActive
        "HIGH" -> Color(0xFFFF6D00)
        "CRITICAL" -> AlertCritical
        else -> AlertActive
    }

    // Static accent for ALERTED state (no continuous pulse animation).
    val rowBorder = when {
        isOffline -> BorderStroke(1.dp, ConnectionOffline.copy(alpha = 0.5f))
        isAlerted -> BorderStroke(1.5.dp, alertStyleColor.copy(alpha = 0.5f))
        else -> null
    }

    val staleStroke = if (isStale) Stroke(
        width = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    ) else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(flashColor)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .then(
                if (isStale) {
                    Modifier.drawBehind {
                        staleStroke?.let { stroke ->
                            drawRoundRect(
                                color = AlertActive.copy(alpha = 0.8f),
                                style = stroke,
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                        }
                    }
                } else Modifier
            )
            .then(if (rowBorder != null) Modifier.border(rowBorder, RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .alpha(displayOpacity),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssetOverlapIcon(symbol = tick.symbol, modifier = Modifier.padding(end = 12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = tick.symbol.replace("/", ""),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = symbolIdSize
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // State indication dot matching specs
                val dotColor = when {
                    isClosed -> NeutralPrice
                    isOffline -> ConnectionOffline
                    isStale -> AlertActive
                    isAlerted -> alertStyleColor
                    else -> ConnectionLive
                }
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(dotColor)
                )

                if (isAlerted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(alertStyleColor.copy(alpha = 0.12f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = highestPriority,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
                            color = alertStyleColor
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = info.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = symbolNameSize
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            val formattedPrice = tick.price.formatPriceDynamic(displayDecs)
            PriceTextWithSuperscript(
                priceStr = formattedPrice,
                fontSize = priceSize,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            
            val formattedChange = tick.change.formatPriceDynamic(displayDecs)
            val formattedPercent = String.format("%.2f%%", kotlin.math.abs(tick.changePercent))
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isStale) {
                    Text(
                        text = "stale",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Black),
                        color = AlertActive
                    )
                }
                Text(
                    text = "$changePrefix$formattedChange ($changePrefix$formattedPercent)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (priceSize.value * 0.55f).sp
                    ),
                    color = changeColor
                )
            }
        }
    }
}
