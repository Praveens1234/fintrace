package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.AccountTransaction
import com.example.data.model.PendingOrder
import com.example.data.model.PriceTick
import com.example.data.model.SymbolInfo
import com.example.data.model.Trade
import com.example.data.model.formatPriceDynamic
import com.example.data.model.getDisplayDecimals
import com.example.data.trading.TradingMath
import com.example.ui.theme.Radius
import com.example.ui.theme.Spacing
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.Locale

private val ProfitGreen = Color(0xFF26A69A)
private val LossRed = Color(0xFFEF5350)

private fun pnlColor(v: Double): Color = when {
    v > 0 -> ProfitGreen
    v < 0 -> LossRed
    else -> Color.Gray
}

private fun usd(amount: Double): String {
    val sign = if (amount < 0) "-" else ""
    return "$sign$" + String.format(Locale.US, "%,.2f", kotlin.math.abs(amount))
}

private fun signedUsd(amount: Double): String {
    val sign = if (amount < 0) "-" else if (amount > 0) "+" else ""
    return "$sign$" + String.format(Locale.US, "%,.2f", kotlin.math.abs(amount))
}

private fun lotsStr(lots: Double): String =
    if (lots == lots.toLong().toDouble()) lots.toLong().toString() else String.format(Locale.US, "%.2f", lots)

private fun px(symbol: String, value: Double?): String =
    value?.formatPriceDynamic(SymbolInfo.find(symbol).getDisplayDecimals()) ?: "—"

@Composable
fun TradeScreen(viewModel: MainViewModel) {
    val context = LocalContext.current

    val snapshot by viewModel.accountSnapshot.collectAsState()
    val openTrades by viewModel.openTrades.collectAsState()
    val closedTrades by viewModel.closedTrades.collectAsState()
    val pendingOrders by viewModel.pendingOrders.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val livePnl by viewModel.liveTradePnl.collectAsState()
    val priceMap by viewModel.priceState.collectAsState()
    val activeSymbols by viewModel.activeSymbols.collectAsState()
    val leverage by viewModel.accountLeverage.collectAsState()
    val tradeMessage by viewModel.tradeMessage.collectAsState()

    LaunchedEffect(tradeMessage) {
        tradeMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeTradeMessage()
        }
    }

    var subTab by remember { mutableStateOf("Positions") }
    var showTicket by remember { mutableStateOf(false) }
    var ticketSymbol by remember { mutableStateOf(activeSymbols.firstOrNull() ?: "EUR/USD") }
    var modifyTradeTarget by remember { mutableStateOf<Trade?>(null) }
    var modifyOrderTarget by remember { mutableStateOf<PendingOrder?>(null) }
    var partialTarget by remember { mutableStateOf<Trade?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AccountHeaderCard(snapshot)

            val tabs = listOf("Positions", "Orders", "History", "Account")
            TabRow(
                selectedTabIndex = tabs.indexOf(subTab).coerceAtLeast(0),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEach { t ->
                    Tab(
                        selected = subTab == t,
                        onClick = { subTab = t },
                        text = {
                            val badge = when (t) {
                                "Positions" -> openTrades.size
                                "Orders" -> pendingOrders.size
                                else -> 0
                            }
                            Text(if (badge > 0) "$t ($badge)" else t, fontSize = 13.sp)
                        }
                    )
                }
            }

            when (subTab) {
                "Positions" -> PositionsTab(
                    openTrades = openTrades, livePnl = livePnl, priceMap = priceMap,
                    onClose = { viewModel.closeTrade(it.id) },
                    onModify = { modifyTradeTarget = it },
                    onPartial = { partialTarget = it },
                    onCloseAll = { viewModel.closeAllTrades() },
                    onCloseProfit = { viewModel.closeAllProfitable() },
                    onCloseLoss = { viewModel.closeAllLosing() }
                )
                "Orders" -> OrdersTab(
                    orders = pendingOrders,
                    onCancel = { viewModel.cancelPendingOrder(it.id) },
                    onModify = { modifyOrderTarget = it },
                    onCancelAll = { viewModel.cancelAllPending() }
                )
                "History" -> HistoryTab(viewModel, closedTrades)
                "Account" -> AccountTab(viewModel, snapshot, transactions, leverage)
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                ticketSymbol = activeSymbols.firstOrNull() ?: "EUR/USD"
                showTicket = true
            },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("New Order") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.md)
        )
    }

    if (showTicket) {
        OrderTicketDialog(
            initialSymbol = ticketSymbol,
            activeSymbols = activeSymbols,
            priceMap = priceMap,
            leverage = leverage,
            onDismiss = { showTicket = false },
            onSubmit = { sym, side, type, lots, entry, sl, tp ->
                if (type == "Market") viewModel.placeMarketOrder(sym, side, lots, entry, sl, tp)
                else viewModel.placePendingOrder(sym, side, type.uppercase(Locale.US), lots, entry, sl, tp)
                showTicket = false
            }
        )
    }

    modifyTradeTarget?.let { t ->
        ModifyTradeDialog(t, onDismiss = { modifyTradeTarget = null }) { sl, tp, entry ->
            viewModel.modifyTrade(t.id, sl, tp, entry)
            modifyTradeTarget = null
        }
    }

    modifyOrderTarget?.let { o ->
        ModifyOrderDialog(o, onDismiss = { modifyOrderTarget = null }) { target, lots, sl, tp ->
            viewModel.modifyPendingOrder(o.id, target, lots, sl, tp)
            modifyOrderTarget = null
        }
    }

    partialTarget?.let { t ->
        PartialCloseDialog(t, onDismiss = { partialTarget = null }) { lots ->
            viewModel.partialCloseTrade(t.id, lots)
            partialTarget = null
        }
    }
}

@Composable
private fun AccountHeaderCard(snap: com.example.data.model.AccountSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(Radius.lg)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("EQUITY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        usd(snap.equity),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("UNREALIZED P/L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        signedUsd(snap.unrealizedPnl),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = pnlColor(snap.unrealizedPnl)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("Balance", usd(snap.balance))
                MiniStat("Free Margin", usd(snap.freeMargin))
                MiniStat("Used Margin", usd(snap.usedMargin))
                MiniStat(
                    "Margin Lvl",
                    if (snap.usedMargin > 0) String.format(Locale.US, "%.0f%%", snap.marginLevel) else "—"
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PositionsTab(
    openTrades: List<Trade>,
    livePnl: Map<Int, Double>,
    priceMap: Map<String, PriceTick>,
    onClose: (Trade) -> Unit,
    onModify: (Trade) -> Unit,
    onPartial: (Trade) -> Unit,
    onCloseAll: () -> Unit,
    onCloseProfit: () -> Unit,
    onCloseLoss: () -> Unit
) {
    if (openTrades.isEmpty()) {
        EmptyState("No open positions. Tap New Order to trade.")
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            BatchChip("Close All", Modifier.weight(1f), onCloseAll)
            BatchChip("Profits", Modifier.weight(1f), onCloseProfit)
            BatchChip("Losses", Modifier.weight(1f), onCloseLoss)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.sm, Spacing.xs, Spacing.sm, 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            items(openTrades, key = { it.id }) { t ->
                val pnl = livePnl[t.id] ?: 0.0
                val cur = priceMap[t.symbol]?.price
                PositionCard(t, pnl, cur, onClose = { onClose(t) }, onModify = { onModify(t) }, onPartial = { onPartial(t) })
            }
        }
    }
}

@Composable
private fun PositionCard(
    t: Trade, pnl: Double, currentPrice: Double?,
    onClose: () -> Unit, onModify: () -> Unit, onPartial: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(Radius.md)
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SideBadge(t.side)
                    Spacer(Modifier.width(Spacing.sm))
                    Column {
                        Text("${t.symbol}  ·  ${lotsStr(t.lots)} lot", fontWeight = FontWeight.Bold)
                        Text("Trade #${t.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(signedUsd(pnl), fontWeight = FontWeight.Bold, color = pnlColor(pnl), fontSize = 18.sp)
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                KV("Entry", px(t.symbol, t.entryPrice))
                KV("Price", px(t.symbol, currentPrice))
                KV("SL", px(t.symbol, t.stopLoss))
                KV("TP", px(t.symbol, t.takeProfit))
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedButton(onClick = onModify, modifier = Modifier.weight(1f), contentPadding = PaddingValues(Spacing.xs)) { Text("Modify", fontSize = 13.sp) }
                OutlinedButton(onClick = onPartial, modifier = Modifier.weight(1f), contentPadding = PaddingValues(Spacing.xs)) { Text("Partial", fontSize = 13.sp) }
                Button(
                    onClick = onClose, modifier = Modifier.weight(1f), contentPadding = PaddingValues(Spacing.xs),
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed)
                ) { Text("Close", fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun OrdersTab(
    orders: List<PendingOrder>,
    onCancel: (PendingOrder) -> Unit,
    onModify: (PendingOrder) -> Unit,
    onCancelAll: () -> Unit
) {
    if (orders.isEmpty()) {
        EmptyState("No pending orders.")
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
            BatchChip("Cancel All Orders", Modifier.fillMaxWidth(), onCancelAll)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.sm, Spacing.xs, Spacing.sm, 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            items(orders, key = { it.id }) { o ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(Radius.md)
                ) {
                    Column(Modifier.padding(Spacing.md)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SideBadge(o.side)
                                Spacer(Modifier.width(Spacing.sm))
                                Column {
                                    Text("${o.symbol}  ·  ${lotsStr(o.lots)} lot", fontWeight = FontWeight.Bold)
                                    Text("Order #${o.id} · ${o.orderKind}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            AssistChip(onClick = {}, label = { Text("@ ${px(o.symbol, o.targetPrice)}") })
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            KV("Trigger", px(o.symbol, o.targetPrice))
                            KV("SL", px(o.symbol, o.stopLoss))
                            KV("TP", px(o.symbol, o.takeProfit))
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            OutlinedButton(onClick = { onModify(o) }, modifier = Modifier.weight(1f)) { Text("Modify") }
                            Button(
                                onClick = { onCancel(o) }, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = LossRed)
                            ) { Text("Cancel") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(viewModel: MainViewModel, closedTrades: List<Trade>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showReset by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val csv = viewModel.buildTradeLedgerCsv()
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                scope.launch(kotlinx.coroutines.Dispatchers.Main) { Toast.makeText(context, "Ledger exported.", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                scope.launch(kotlinx.coroutines.Dispatchers.Main) { Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            OutlinedButton(onClick = { exporter.launch("fintrace_trade_ledger.csv") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.xs)); Text("Export CSV")
            }
            OutlinedButton(
                onClick = { scope.launch(kotlinx.coroutines.Dispatchers.IO) { viewModel.shareCsv(context, "fintrace_trade_ledger.csv", viewModel.buildTradeLedgerCsv()) } },
                modifier = Modifier.weight(1f)
            ) { Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.xs)); Text("Share") }
        }
        if (closedTrades.isEmpty()) {
            EmptyState("No closed trades yet.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(Spacing.sm, Spacing.xs, Spacing.sm, 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(closedTrades, key = { it.id }) { t ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(Radius.md)
                    ) {
                        Column(Modifier.padding(Spacing.md)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SideBadge(t.side)
                                    Spacer(Modifier.width(Spacing.sm))
                                    Column {
                                        Text("${t.symbol}  ·  ${lotsStr(t.lots)} lot", fontWeight = FontWeight.Bold)
                                        Text("Trade #${t.id} · closed by ${t.closedBy ?: "—"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(signedUsd(t.realizedPnl), fontWeight = FontWeight.Bold, color = pnlColor(t.realizedPnl))
                            }
                            Spacer(Modifier.height(Spacing.xs))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                KV("Entry", px(t.symbol, t.entryPrice))
                                KV("Exit", px(t.symbol, t.exitPrice))
                            }
                            Spacer(Modifier.height(Spacing.xxs))
                            Text("Open:  ${viewModel.formatTime(t.openTime)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            t.closeTime?.let { Text("Close: ${viewModel.formatTime(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
            TextButton(
                onClick = { showReset = true },
                modifier = Modifier.fillMaxWidth().padding(bottom = 80.dp)
            ) { Text("Reset All Trading Data", color = LossRed) }
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset trading data?") },
            text = { Text("This permanently deletes all trades, orders and transactions and sets the balance to $0.") },
            confirmButton = { Button(onClick = { viewModel.resetTradingData(); showReset = false }, colors = ButtonDefaults.buttonColors(containerColor = LossRed)) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AccountTab(
    viewModel: MainViewModel,
    snap: com.example.data.model.AccountSnapshot,
    transactions: List<AccountTransaction>,
    leverage: Double
) {
    var amount by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.sm, Spacing.xs, Spacing.sm, 96.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(Radius.md)
            ) {
                Column(Modifier.padding(Spacing.md)) {
                    Text("FUNDS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (USD)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(
                            onClick = { amount.toDoubleOrNull()?.let { viewModel.deposit(it); amount = "" } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ProfitGreen)
                        ) { Text("Deposit") }
                        OutlinedButton(
                            onClick = { amount.toDoubleOrNull()?.let { viewModel.withdraw(it); amount = "" } },
                            modifier = Modifier.weight(1f)
                        ) { Text("Withdraw") }
                    }
                }
            }
        }
        item { LeverageCard(viewModel, leverage, snap) }
        item {
            Text("TRANSACTION HISTORY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = Spacing.sm))
        }
        if (transactions.isEmpty()) {
            item { Text("No transactions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.sm)) }
        } else {
            items(transactions, key = { it.id }) { x ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(x.type, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(viewModel.formatTime(x.timestamp) + (if (x.note.isNotBlank()) " · ${x.note}" else ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(signedUsd(x.amount), fontWeight = FontWeight.Bold, color = pnlColor(x.amount))
                        Text("bal ${usd(x.balanceAfter)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun LeverageCard(viewModel: MainViewModel, leverage: Double, snap: com.example.data.model.AccountSnapshot) {
    val stopout by viewModel.accountStopout.collectAsState()
    var levText by remember(leverage) { mutableStateOf(leverage.toInt().toString()) }
    var soText by remember(stopout) { mutableStateOf(stopout.toInt().toString()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(Radius.md)
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text("RISK SETTINGS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = levText, onValueChange = { levText = it },
                    label = { Text("Leverage 1:") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = soText, onValueChange = { soText = it },
                    label = { Text("Stop-out %") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Button(
                onClick = {
                    levText.toDoubleOrNull()?.let { viewModel.setLeverage(it) }
                    soText.toDoubleOrNull()?.let { viewModel.setStopoutLevel(it) }
                },
                modifier = Modifier.align(Alignment.End)
            ) { Text("Apply") }
        }
    }
}

// ── Order ticket ──────────────────────────────────────────────────────────
@Composable
private fun OrderTicketDialog(
    initialSymbol: String,
    activeSymbols: List<String>,
    priceMap: Map<String, PriceTick>,
    leverage: Double,
    onDismiss: () -> Unit,
    onSubmit: (symbol: String, side: String, type: String, lots: Double, entry: Double, sl: Double?, tp: Double?) -> Unit
) {
    var symbol by remember { mutableStateOf(initialSymbol) }
    var side by remember { mutableStateOf("LONG") }   // LONG=BUY, SHORT=SELL
    var orderType by remember { mutableStateOf("Market") } // Market | Limit | Stop
    val curPrice = priceMap[symbol]?.price ?: SymbolInfo.find(symbol).defaultPrice
    var lotsTxt by remember { mutableStateOf("0.10") }
    var entryTxt by remember(symbol) { mutableStateOf(px(symbol, curPrice).replace(",", "")) }
    var slTxt by remember { mutableStateOf("") }
    var tpTxt by remember { mutableStateOf("") }
    var symbolMenu by remember { mutableStateOf(false) }

    val lots = lotsTxt.toDoubleOrNull() ?: 0.0
    val entry = entryTxt.toDoubleOrNull() ?: curPrice
    val sl = slTxt.toDoubleOrNull()
    val tp = tpTxt.toDoubleOrNull()
    val factor = TradingMath.quoteToUsdFactor(symbol, priceMap) ?: 1.0
    val slPnl = sl?.let { TradingMath.previewPnlUsd(side, symbol, entry, it, lots, factor) }
    val tpPnl = tp?.let { TradingMath.previewPnlUsd(side, symbol, entry, it, lots, factor) }
    val margin = TradingMath.requiredMarginUsd(symbol, lots, entry, leverage, priceMap)

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(Radius.lg), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                Modifier.padding(Spacing.md).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text("New Order", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                // Symbol picker
                Box {
                    OutlinedTextField(
                        value = symbol, onValueChange = {}, readOnly = true,
                        label = { Text("Symbol") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable { symbolMenu = true }
                    )
                    // Transparent overlay to capture clicks (readOnly field swallows them)
                    Box(modifier = Modifier.matchParentSize().clickable { symbolMenu = true })
                    DropdownMenu(expanded = symbolMenu, onDismissRequest = { symbolMenu = false }) {
                        (activeSymbols.ifEmpty { SymbolInfo.ALL.map { it.symbol } }).forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = {
                                symbol = s
                                entryTxt = px(s, priceMap[s]?.price ?: SymbolInfo.find(s).defaultPrice).replace(",", "")
                                symbolMenu = false
                            })
                        }
                    }
                }

                // Buy / Sell
                SegmentedToggle(listOf("LONG", "SHORT"), labels = listOf("BUY", "SELL"), selected = side) { side = it }
                // Order type
                SegmentedToggle(listOf("Market", "Limit", "Stop"), labels = listOf("Market", "Limit", "Stop"), selected = orderType) { orderType = it }

                Text("Live price: ${px(symbol, curPrice)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = lotsTxt, onValueChange = { lotsTxt = it },
                    label = { Text("Volume (lots)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = entryTxt, onValueChange = { entryTxt = it },
                    label = { Text(if (orderType == "Market") "Entry price (editable)" else "Trigger price") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = slTxt, onValueChange = { slTxt = it },
                        label = { Text("Stop Loss") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = tpTxt, onValueChange = { tpTxt = it },
                        label = { Text("Take Profit") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Live preview
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(Radius.md)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(Spacing.sm), horizontalArrangement = Arrangement.SpaceBetween) {
                        PreviewCell("SL P/L", slPnl?.let { signedUsd(it) } ?: "—", slPnl?.let { pnlColor(it) } ?: Color.Gray)
                        PreviewCell("TP P/L", tpPnl?.let { signedUsd(it) } ?: "—", tpPnl?.let { pnlColor(it) } ?: Color.Gray)
                        PreviewCell("Margin", usd(margin), MaterialTheme.colorScheme.onSurface)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = { if (lots > 0 && entry > 0) onSubmit(symbol, side, orderType, lots, entry, sl, tp) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (side == "LONG") ProfitGreen else LossRed)
                    ) { Text(if (side == "LONG") "BUY" else "SELL") }
                }
            }
        }
    }
}

@Composable
private fun ModifyTradeDialog(t: Trade, onDismiss: () -> Unit, onConfirm: (sl: Double?, tp: Double?, entry: Double?) -> Unit) {
    var slTxt by remember { mutableStateOf(t.stopLoss?.let { px(t.symbol, it).replace(",", "") } ?: "") }
    var tpTxt by remember { mutableStateOf(t.takeProfit?.let { px(t.symbol, it).replace(",", "") } ?: "") }
    var entryTxt by remember { mutableStateOf(px(t.symbol, t.entryPrice).replace(",", "")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modify Trade #${t.id}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(value = entryTxt, onValueChange = { entryTxt = it }, label = { Text("Entry price") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = slTxt, onValueChange = { slTxt = it }, label = { Text("Stop Loss") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = tpTxt, onValueChange = { tpTxt = it }, label = { Text("Take Profit") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = { Button(onClick = { onConfirm(slTxt.toDoubleOrNull(), tpTxt.toDoubleOrNull(), entryTxt.toDoubleOrNull()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ModifyOrderDialog(o: PendingOrder, onDismiss: () -> Unit, onConfirm: (target: Double, lots: Double, sl: Double?, tp: Double?) -> Unit) {
    var targetTxt by remember { mutableStateOf(px(o.symbol, o.targetPrice).replace(",", "")) }
    var lotsTxt by remember { mutableStateOf(lotsStr(o.lots)) }
    var slTxt by remember { mutableStateOf(o.stopLoss?.let { px(o.symbol, it).replace(",", "") } ?: "") }
    var tpTxt by remember { mutableStateOf(o.takeProfit?.let { px(o.symbol, it).replace(",", "") } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modify Order #${o.id}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(value = targetTxt, onValueChange = { targetTxt = it }, label = { Text("Trigger price") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = lotsTxt, onValueChange = { lotsTxt = it }, label = { Text("Volume (lots)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = slTxt, onValueChange = { slTxt = it }, label = { Text("Stop Loss") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = tpTxt, onValueChange = { tpTxt = it }, label = { Text("Take Profit") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            Button(onClick = {
                val target = targetTxt.toDoubleOrNull(); val lots = lotsTxt.toDoubleOrNull()
                if (target != null && lots != null) onConfirm(target, lots, slTxt.toDoubleOrNull(), tpTxt.toDoubleOrNull())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PartialCloseDialog(t: Trade, onDismiss: () -> Unit, onConfirm: (lots: Double) -> Unit) {
    var lotsTxt by remember { mutableStateOf(lotsStr(t.lots / 2.0)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Partial Close #${t.id}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Open volume: ${lotsStr(t.lots)} lot", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = lotsTxt, onValueChange = { lotsTxt = it }, label = { Text("Lots to close") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = { Button(onClick = { lotsTxt.toDoubleOrNull()?.let { onConfirm(it) } }) { Text("Close Partial") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Small reusable pieces ───────────────────────────────────────────────────
@Composable
private fun SegmentedToggle(values: List<String>, labels: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        values.forEachIndexed { i, v ->
            val isSel = selected == v
            FilterChip(
                selected = isSel,
                onClick = { onSelect(v) },
                label = { Text(labels[i], fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PreviewCell(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SideBadge(side: String) {
    val isLong = side.equals("LONG", true)
    Box(
        modifier = Modifier
            .background(if (isLong) ProfitGreen.copy(alpha = 0.18f) else LossRed.copy(alpha = 0.18f), RoundedCornerShape(Radius.sm))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
    ) {
        Text(if (isLong) "BUY" else "SELL", color = if (isLong) ProfitGreen else LossRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun KV(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun BatchChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(Spacing.xs)) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.lg))
    }
}
