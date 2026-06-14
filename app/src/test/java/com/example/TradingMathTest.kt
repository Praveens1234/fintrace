package com.example

import com.example.data.model.PriceTick
import com.example.data.model.Trade
import com.example.data.trading.TradingMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies PNL, margin and USD-conversion math across asset classes. All amounts are USD;
 * standard lots: 100,000 base units (forex), 100 oz (gold), 5,000 oz (silver).
 */
class TradingMathTest {

    private fun tick(symbol: String, price: Double) = symbol to PriceTick(symbol, price)

    private val prices = mapOf(
        tick("EUR/USD", 1.1000),
        tick("GBP/USD", 1.2500),
        tick("USD/JPY", 150.00),
        tick("XAU/USD", 2300.00),
        tick("XAG/USD", 28.00),
        tick("EUR/GBP", 0.8800)
    )

    @Test fun contract_sizes_per_asset_class() {
        assertEquals(100_000.0, TradingMath.contractSize("EUR/USD"), 0.0)
        assertEquals(100.0, TradingMath.contractSize("XAU/USD"), 0.0)
        assertEquals(5_000.0, TradingMath.contractSize("XAG/USD"), 0.0)
    }

    @Test fun eurusd_long_pnl_is_direct_usd() {
        // 1 lot EUR/USD, +0.0010 move = 100,000 * 0.0010 = $100 (quote is USD → factor 1).
        val t = Trade(symbol = "EUR/USD", side = "LONG", lots = 1.0, entryPrice = 1.1000)
        val pnl = TradingMath.unrealizedPnlUsd(t, 1.1010, prices)
        assertEquals(100.0, pnl, 1e-6)
    }

    @Test fun eurusd_short_pnl_inverts_sign() {
        val t = Trade(symbol = "EUR/USD", side = "SHORT", lots = 1.0, entryPrice = 1.1000)
        val pnl = TradingMath.unrealizedPnlUsd(t, 1.1010, prices)
        assertEquals(-100.0, pnl, 1e-6)
    }

    @Test fun usdjpy_long_pnl_converts_jpy_to_usd() {
        // 1 lot USD/JPY, +0.50 move. Profit in JPY = 100,000 * 0.50 = 50,000 JPY.
        // USD = 50,000 / 150.50 (current price) ≈ 332.23.
        val t = Trade(symbol = "USD/JPY", side = "LONG", lots = 1.0, entryPrice = 150.00)
        val current = 150.50
        val expectedUsd = (100_000.0 * 0.50) / current
        val priceMap = prices + ("USD/JPY" to PriceTick("USD/JPY", current))
        val pnl = TradingMath.unrealizedPnlUsd(t, current, priceMap)
        assertEquals(expectedUsd, pnl, 1e-6)
    }

    @Test fun gold_uses_100oz_contract() {
        // 1 lot XAU/USD, +$5 move = 100 oz * 5 = $500.
        val t = Trade(symbol = "XAU/USD", side = "LONG", lots = 1.0, entryPrice = 2300.00)
        val pnl = TradingMath.unrealizedPnlUsd(t, 2305.00, prices)
        assertEquals(500.0, pnl, 1e-6)
    }

    @Test fun silver_uses_5000oz_contract() {
        // 0.5 lot XAG/USD, +$0.20 move = 5,000 * 0.5 * 0.20 = $500.
        val t = Trade(symbol = "XAG/USD", side = "LONG", lots = 0.5, entryPrice = 28.00)
        val pnl = TradingMath.unrealizedPnlUsd(t, 28.20, prices)
        assertEquals(500.0, pnl, 1e-6)
    }

    @Test fun cross_pair_converts_quote_via_helper_pair() {
        // EUR/GBP, profit is in GBP; convert with GBP/USD = 1.2500.
        // 1 lot, +0.0010 move = 100,000 * 0.0010 = 100 GBP → 125 USD.
        val t = Trade(symbol = "EUR/GBP", side = "LONG", lots = 1.0, entryPrice = 0.8800)
        val pnl = TradingMath.unrealizedPnlUsd(t, 0.8810, prices)
        assertEquals(125.0, pnl, 1e-6)
    }

    @Test fun required_margin_for_usd_quoted_pair() {
        // 1 lot EUR/USD at 1.10, leverage 100. Notional USD = 100,000 * 1.10 = 110,000.
        // Margin = 110,000 / 100 = 1,100.
        val margin = TradingMath.requiredMarginUsd("EUR/USD", 1.0, 1.1000, 100.0, prices)
        assertEquals(1_100.0, margin, 1e-6)
    }

    @Test fun account_snapshot_aggregates_equity_and_margin() {
        val t = Trade(symbol = "EUR/USD", side = "LONG", lots = 1.0, entryPrice = 1.1000, marginUsd = 1_100.0)
        val snap = TradingMath.accountSnapshot(listOf(t), balance = 10_000.0, priceMap = prices)
        // Current EUR/USD = 1.1000 → no unrealized PNL.
        assertEquals(10_000.0, snap.equity, 1e-6)
        assertEquals(1_100.0, snap.usedMargin, 1e-6)
        assertEquals(8_900.0, snap.freeMargin, 1e-6)
        assertTrue(snap.marginLevel > 0)
    }

    @Test fun realized_pnl_for_partial_close() {
        val t = Trade(symbol = "EUR/USD", side = "LONG", lots = 1.0, entryPrice = 1.1000)
        // Close 0.5 lot at 1.1020 → 100,000 * 0.5 * 0.0020 = $100.
        val realized = TradingMath.realizedPnlUsd(t, 1.1020, 0.5, 1.0)
        assertEquals(100.0, realized, 1e-6)
    }
}
