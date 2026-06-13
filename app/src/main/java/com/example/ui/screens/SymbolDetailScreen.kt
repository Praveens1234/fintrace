package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Alert
import com.example.data.model.SymbolInfo
import com.example.data.model.formatPriceDynamic
import com.example.data.model.getDisplayDecimals
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolDetailScreen(
    symbol: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val info = SymbolInfo.find(symbol)
    val priceState by viewModel.priceState.collectAsState()
    val tick = priceState[symbol]

    val alerts by viewModel.alertList.collectAsState()
    val targetAlerts = alerts.filter { it.symbol == symbol }

    var showQuickCreate by remember { mutableStateOf(false) }
    var editingAlert by remember { mutableStateOf<Alert?>(null) }

    val isLight = (MaterialTheme.colorScheme.background.red + MaterialTheme.colorScheme.background.green + MaterialTheme.colorScheme.background.blue) > 1.5f
    val changeColor = if (tick != null && tick.change >= 0) {
        if (isLight) PriceUpLight else PriceUpDark
    } else {
        if (isLight) PriceDownLight else PriceDownDark
    }
    val arrow = if (tick != null && tick.change >= 0) "▲" else "▼"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$symbol Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Meta Information
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = info.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Live large price digit
                        val displayDecs = info.getDisplayDecimals()
                        Text(
                            text = if (tick != null) tick.price.formatPriceDynamic(displayDecs) else info.defaultPrice.formatPriceDynamic(displayDecs),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = PriceTextFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val changeValRaw = if (tick != null) tick.change else 0.0
                            Text(
                                text = "$arrow " + changeValRaw.formatPriceDynamic(displayDecs).replace("+","").replace("-",""),
                                color = changeColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = PriceTextFontFamily)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = if (tick != null) String.format(java.util.Locale.US, "%.2f%%", kotlin.math.abs(tick.changePercent)) else "0.0%",
                                color = changeColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }

            // Market Mechanics list (Bid / Ask / Spread / High / Low)
            item {
                val displayDecs = info.getDisplayDecimals()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Market Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text("Bid Price", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (tick != null) tick.bid.formatPriceDynamic(displayDecs) else (info.defaultPrice - 0.05).formatPriceDynamic(displayDecs),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = PriceTextFontFamily)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Ask Price", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (tick != null) tick.ask.formatPriceDynamic(displayDecs) else (info.defaultPrice + 0.05).formatPriceDynamic(displayDecs),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = PriceTextFontFamily)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text("Spread (Pts)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val spread = if (tick != null) (tick.ask - tick.bid) else 0.10
                                val multiplier = java.lang.Math.pow(10.0, displayDecs.toDouble())
                                val spreadInt = kotlin.math.round(spread * multiplier).toInt()
                                Text(
                                    text = "$spreadInt pts",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = PriceTextFontFamily),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // High / Low Indicators
                        val highPrice = if (tick != null) tick.price * 1.002 else info.defaultPrice * 1.002
                        val lowPrice = if (tick != null) tick.price * 0.998 else info.defaultPrice * 0.998

                        Text(
                            text = "24h Low / High Range",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(lowPrice.formatPriceDynamic(displayDecs), style = MaterialTheme.typography.labelSmall.copy(fontFamily = PriceTextFontFamily))
                            val sliderColor = MaterialTheme.colorScheme.primary
                            Canvas(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .padding(horizontal = 12.dp)
                            ) {
                                val current = if (tick != null) tick.price else info.defaultPrice
                                val pct = ((current - lowPrice) / (highPrice - lowPrice)).toFloat().coerceIn(0f, 1f)

                                drawRoundRect(
                                    color = Color.LightGray.copy(alpha = 0.3f),
                                    size = size
                                )
                                drawCircle(
                                    color = sliderColor,
                                    radius = 6.dp.toPx(),
                                    center = androidx.compose.ui.geometry.Offset(pct * size.width, size.height / 2f)
                                )
                            }
                            Text(highPrice.formatPriceDynamic(displayDecs), style = MaterialTheme.typography.labelSmall.copy(fontFamily = PriceTextFontFamily))
                        }
                    }
                }
            }

            // Decimal Precision Control Section (Requested Per-Asset Settings)
            item {
                val symbolDecs by viewModel.symbolDecimals.collectAsState()
                val currentOverride = symbolDecs[symbol.uppercase(java.util.Locale.ROOT)]

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Asset Decimals Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = "Customize display precision for $symbol. Default level is ${info.decimals} decimals.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = currentOverride == null,
                                onClick = { viewModel.saveSymbolDecimalOverride(symbol, null) },
                                label = { Text("Default (${info.decimals})", fontSize = 11.sp) }
                            )

                            (1..9).forEach { d ->
                                val isSel = currentOverride == d
                                FilterChip(
                                    selected = isSel,
                                    onClick = { viewModel.saveSymbolDecimalOverride(symbol, d) },
                                    label = { Text("$d Decimals", fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }
                                )
                            }
                        }
                    }
                }
            }

            // Real-time sparkline graph (large profile)
            if (tick != null && tick.history.size > 1) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Realtime Price Curve", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                    .padding(8.dp)
                            ) {
                                val width = size.width
                                val height = size.height
                                val minVal = tick.history.min()
                                val maxVal = tick.history.max()
                                val diff = if (maxVal - minVal > 0) maxVal - minVal else 1.0

                                val stepX = width / (tick.history.size - 1)
                                val path = Path()

                                tick.history.forEachIndexed { i, p ->
                                    val x = i * stepX
                                    val normY = (p - minVal) / diff
                                    val y = height - (normY * height).toFloat()

                                    if (i == 0) {
                                        path.moveTo(x, y)
                                    } else {
                                        path.lineTo(x, y)
                                    }
                                }

                                drawPath(
                                    path = path,
                                    color = changeColor,
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }

            // Inline local Alerts for this selected asset
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rules for $symbol (${targetAlerts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = { showQuickCreate = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add Alert", fontSize = 11.sp)
                    }
                }
            }

            if (targetAlerts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No alerts configured for $symbol. Tap Add Alert to build crossing notification thresholds.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(targetAlerts, key = { it.id }) { alert ->
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

    if (showQuickCreate) {
        var targetPriceStr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showQuickCreate = false },
            confirmButton = {
                Button(
                    onClick = {
                        val priceVal = targetPriceStr.toDoubleOrNull() ?: 0.0
                        if (priceVal > 0) {
                            viewModel.createAlert(
                                symbol = symbol,
                                condition = "CROSSING",
                                targetPrice = priceVal,
                                title = "$symbol Crossed Target",
                                message = "Crossing detected. Price exceeded $priceVal threshold.",
                                isOneTime = true,
                                priority = "HIGH",
                                colorTagIndex = 0
                            )
                            showQuickCreate = false
                        }
                    }
                ) {
                    Text("Save Rule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickCreate = false }) { Text("Cancel") }
            },
            title = { Text("Quick Alert: $symbol") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("FinTrace will trigger a standard high priority notification when prices cross your configured threshold.")
                    OutlinedTextField(
                        value = targetPriceStr,
                        onValueChange = { targetPriceStr = it },
                        label = { Text("Target Threshold Price") },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }
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
}
