package com.example.data.trading

import com.example.data.model.AccountSnapshot
import com.example.data.model.PriceTick
import com.example.data.model.SymbolInfo
import com.example.data.model.Trade

/**
 * Pure, side-effect-free trading mathematics. Kept free of Android/Room dependencies so the
 * PNL, margin and currency-conversion logic can be unit-tested deterministically (like
 * [com.example.data.market.MarketSchedule]).
 *
 * Conventions (locked design decisions):
 *  - Account base currency is USD. All PNL/margin/equity are expressed in USD.
 *  - Standard lots: 1.0 lot = 100,000 base units (forex), 100 oz (XAU), 5,000 oz (XAG).
 *  - Internal math is full Double precision; rounding happens only at the display layer.
 */
object TradingMath {

    /** Units of the base asset represented by one standard lot for [symbol]. */
    fun contractSize(symbol: String): Double {
        val s = symbol.uppercase()
        return when {
            s == "XAU/USD" -> 100.0       // 100 troy ounces of gold per lot
            s == "XAG/USD" -> 5_000.0     // 5,000 troy ounces of silver per lot
            else -> 100_000.0             // standard forex lot
        }
    }

    /** "EUR" from "EUR/USD". */
    fun baseCurrency(symbol: String): String = symbol.substringBefore("/").uppercase()

    /** "USD" from "EUR/USD". */
    fun quoteCurrency(symbol: String): String = symbol.substringAfter("/", "USD").uppercase()

    /**
     * Raw profit of a position in the QUOTE currency (before USD conversion).
     * LONG profits when price rises; SHORT profits when price falls.
     */
    fun pnlInQuote(side: String, entry: Double, current: Double, lots: Double, symbol: String): Double {
        val dir = if (side.equals("SHORT", ignoreCase = true)) -1.0 else 1.0
        return (current - entry) * dir * contractSize(symbol) * lots
    }

    /**
     * Convert an amount expressed in [currency] into USD using whatever live pairs are available
     * in [priceMap]. Returns null if no conversion path is available so callers can decide how to
     * surface the gap (rather than silently using a wrong rate).
     */
    fun currencyToUsd(currency: String, priceMap: Map<String, PriceTick>): Double? {
        val cur = currency.uppercase()
        if (cur == "USD") return 1.0
        // Direct pair CUR/USD (e.g. GBP/USD, EUR/USD, XAU/USD).
        priceMap["$cur/USD"]?.price?.let { if (it > 0) return it }
        // Inverse pair USD/CUR (e.g. USD/JPY → 1 / price).
        priceMap["USD/$cur"]?.price?.let { if (it > 0) return 1.0 / it }
        return null
    }

    /** Multiplier to convert one unit of the QUOTE currency of [symbol] into USD. */
    fun quoteToUsdFactor(symbol: String, priceMap: Map<String, PriceTick>): Double? =
        currencyToUsd(quoteCurrency(symbol), priceMap)

    /** Multiplier to convert one unit of the BASE currency of [symbol] into USD. */
    fun baseToUsdFactor(symbol: String, priceMap: Map<String, PriceTick>): Double? =
        currencyToUsd(baseCurrency(symbol), priceMap)

    /**
     * Unrealized PNL of an open [trade] in USD at [currentPrice]. [conversionFactor] is the
     * quote→USD factor (see [quoteToUsdFactor]); pass 1.0 as a fallback when unknown.
     */
    fun unrealizedPnlUsd(trade: Trade, currentPrice: Double, conversionFactor: Double): Double {
        val quotePnl = pnlInQuote(trade.side, trade.entryPrice, currentPrice, trade.lots, trade.symbol)
        return quotePnl * conversionFactor
    }

    /** Convenience overload that derives the conversion factor from [priceMap] (fallback 1.0). */
    fun unrealizedPnlUsd(trade: Trade, currentPrice: Double, priceMap: Map<String, PriceTick>): Double {
        val factor = quoteToUsdFactor(trade.symbol, priceMap) ?: 1.0
        return unrealizedPnlUsd(trade, currentPrice, factor)
    }

    /**
     * Realized PNL in USD for closing [lots] of [trade] at [exitPrice].
     * Used for full and partial closes.
     */
    fun realizedPnlUsd(trade: Trade, exitPrice: Double, lots: Double, conversionFactor: Double): Double {
        val dir = if (trade.side.equals("SHORT", ignoreCase = true)) -1.0 else 1.0
        val quotePnl = (exitPrice - trade.entryPrice) * dir * contractSize(trade.symbol) * lots
        return quotePnl * conversionFactor
    }

    /**
     * USD margin required to open [lots] of [symbol] at [entry] given [leverage] (e.g. 100 = 1:100).
     * Notional value is converted to USD via the base currency. Falls back to entry price when no
     * base conversion is available (correct for USD-quoted pairs).
     */
    fun requiredMarginUsd(
        symbol: String,
        lots: Double,
        entry: Double,
        leverage: Double,
        priceMap: Map<String, PriceTick>
    ): Double {
        if (leverage <= 0) return 0.0
        val units = contractSize(symbol) * lots
        // Notional in USD = units of base * (base→USD). For XXX/USD pairs base→USD == entry price.
        val baseUsd = baseToUsdFactor(symbol, priceMap) ?: run {
            val baseCur = baseCurrency(symbol)
            if (baseCur == "USD") {
                1.0
            } else {
                // No direct base→USD pair cached. Derive it via the quote leg instead of using
                // entry directly: 1 base unit = `entry` quote units, so base→USD = entry * quote→USD.
                // Using entry alone here (as if quote were always USD) silently produced
                // orders-of-magnitude wrong margin for cross pairs like EUR/GBP or GBP/AUD.
                entry * (quoteToUsdFactor(symbol, priceMap) ?: 1.0)
            }
        }
        val notionalUsd = units * baseUsd
        return notionalUsd / leverage
    }

    /** Live SL/TP PNL preview (USD) for an intended order, before it is placed. */
    fun previewPnlUsd(
        side: String,
        symbol: String,
        entry: Double,
        target: Double,
        lots: Double,
        conversionFactor: Double
    ): Double {
        val quotePnl = pnlInQuote(side, entry, target, lots, symbol)
        return quotePnl * conversionFactor
    }

    /**
     * Aggregate live account state. [usedMargin] is the sum of reserved margins on open trades
     * (stored per-trade so it is stable even when conversion pairs flicker).
     */
    fun accountSnapshot(
        openTrades: List<Trade>,
        balance: Double,
        priceMap: Map<String, PriceTick>
    ): AccountSnapshot {
        var unrealized = 0.0
        var usedMargin = 0.0
        for (t in openTrades) {
            val px = priceMap[t.symbol]?.price ?: t.entryPrice
            unrealized += unrealizedPnlUsd(t, px, priceMap)
            usedMargin += t.marginUsd
        }
        val equity = balance + unrealized
        val freeMargin = equity - usedMargin
        val marginLevel = if (usedMargin > 0.0) equity / usedMargin * 100.0 else 0.0
        return AccountSnapshot(
            balance = balance,
            equity = equity,
            usedMargin = usedMargin,
            freeMargin = freeMargin,
            marginLevel = marginLevel,
            unrealizedPnl = unrealized,
            openPositions = openTrades.size
        )
    }

    /** Display decimals for a symbol's price, reused from SymbolInfo metadata. */
    fun priceDecimals(symbol: String): Int = SymbolInfo.find(symbol).decimals
}
