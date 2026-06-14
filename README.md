# FinTrace

> **Professional-grade real-time forex & metals price monitor with virtual trading for Android**

[![Build](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/Praveens1234/fintrace/actions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-blueviolet)](https://kotlinlang.org/)
[![Material 3](https://img.shields.io/badge/Material-3-blue)](https://m3.material.io/)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24%20(Android%207.0)-orange)](https://developer.android.com/studio)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

FinTrace streams live prices for 14 forex pairs and precious metals from **7 data providers**, fires intelligent price-crossing alerts with voice readouts, and provides a complete **virtual trading desk** — all running locally on your Android device with no cloud account required.

---

## ✨ Features

### 📡 Live Price Monitoring
- **7 data providers** — Twelve Data (WebSocket), Finnhub (WebSocket), Alpha Vantage (REST polling), TraderMade (WebSocket), OANDA v20 (HTTP chunked streaming), AllTick (WebSocket), Polygon.io (WebSocket)
- **14 instruments** — EUR/USD, GBP/USD, USD/JPY, USD/CHF, AUD/USD, USD/CAD, NZD/USD, EUR/GBP, EUR/JPY, GBP/JPY, EUR/AUD, GBP/AUD, XAU/USD (Gold), XAG/USD (Silver)
- Real-time bid/ask, spread, sparkline price chart per asset
- Configurable decimal precision per symbol

### 🔔 Smart Alerts
- **Conditions:** Price Crossing, Crossing Up (rises above), Crossing Down (falls below)
- **Priority levels:** LOW · MEDIUM · HIGH · CRITICAL — each with independent sound mode
- One-time or repeating with cooldown timers and optional expiry
- **Sound modes:** Tone + TTS · Tone only · TTS only · Silent
- Full text-to-speech voice readout of symbol and price

### 📈 Virtual Trading
- **Order types:** Market, Limit, Stop
- **Editable entry price** on Market orders — mirror real broker positions exactly
- SL/TP with live P/L preview before placing
- Partial close and position modification
- Batch operations: Close All, Close Profitable, Close Losing

### 💰 Margin & Account Engine
- Configurable leverage (default **1:100**)
- Live dashboard: Equity, Used/Free Margin, Margin Level %
- Auto stop-out at configurable margin level
- Deposit / Withdraw virtual balance
- Running account transaction ledger

### 📋 Trade Ledger & Export
- Full history: Trade ID, Symbol, Side, Lots, Entry, Exit, SL, TP, timestamps, P/L, close reason
- CSV export + system share sheet
- Account transaction history with running balance

### 🕐 Timezone Support
- Any UTC offset — `+5:30`, `-4`, `0`, etc.
- Live clock on Prices screen in your chosen timezone
- All trade timestamps displayed in selected timezone

### 💾 Backup & Restore
- Export **all data** (trades, orders, alerts, API keys, settings, logs) as a single JSON file
- Import and restore on any device
- Full reset option in Settings

### 🎨 Themes
- Dark · AMOLED (true black) · Light · System-adaptive

### 🔋 Reliability
- Persistent foreground service with WorkManager backstop
- Survives Doze, memory pressure, and device reboots
- Live connection status indicator

---

## 📸 Screenshots

<!-- Add screenshots here -->

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Iguana or later
- Android SDK 24+ (Android 7.0+)
- A free API key from at least one supported price provider

### Build & Run

```bash
git clone https://github.com/Praveens1234/fintrace.git
cd fintrace
./gradlew :app:assembleDebug
```

Or open the project in Android Studio and run directly on a device or emulator.

### Run Tests

```bash
./gradlew :app:testDebugUnitTest
```

---

## ⚙️ Configuration

### Setting Up a Price Provider

1. Open **Settings → Data Provider**
2. Select a provider
3. Enter your API key
4. For OANDA v20: also enter your Account ID and select Practice or Live environment

### Provider Comparison

| Provider | Type | Free Tier | Metals | Notes |
|---|---|---|---|---|
| Twelve Data | WebSocket | Yes | ✅ | Recommended; best real-time coverage |
| Finnhub | WebSocket | Yes | ✅ | Good free tier |
| Alpha Vantage | REST polling | Yes (25 req/day) | ✅ | Low-frequency monitoring |
| TraderMade | WebSocket | Yes | ✅ | Mid-price streaming |
| OANDA v20 | HTTP streaming | Practice account (free) | ✅ | Requires Account ID; accurate spreads |
| AllTick | WebSocket | Yes (5 symbols max) | ✅ | XAU→GOLD, XAG→Silver mapping |
| Polygon.io | WebSocket | Delayed/EOD only | ❌ | Forex only on free tier |

---

## 📊 Virtual Trading Guide

### Order Types

| Type | Behaviour |
|---|---|
| **Market** | Opens at live mid-price (entry editable for broker mirroring) |
| **Limit** | Fills when price reaches target from the better side |
| **Stop** | Fills when price breaks through the trigger level |

### Lot Sizes

| Asset | 1 Standard Lot |
|---|---|
| Forex pairs | 100,000 base units |
| Gold XAU/USD | 100 troy oz |
| Silver XAG/USD | 5,000 troy oz |

### Margin
- Default leverage: **1:100** (configurable in Trade → Account tab)
- Default stop-out level: **50% margin level** (configurable)
- All P/L converted to USD using live rates

---

## 🏗️ Architecture

```
MainActivity (single-Activity Compose)
  └── State-based navigation (currentRoute + currentTab)
        ├── DashboardScreen   — Live prices grid + UTC clock
        ├── TradeScreen       — Virtual trading desk (4 sub-tabs)
        ├── AlertListScreen   — Alert management
        ├── LogsScreen        — Diagnostic log viewer
        └── SettingsScreen    — Full configuration + Backup/Restore

PriceMonitorManager (singleton, Application-scoped)
  ├── 7 provider WebSocket / HTTP connections (OkHttp)
  ├── Kotlin Coroutine Isolate pool (price tick processing)
  ├── TradingMath (pure PNL / margin engine, unit-tested)
  ├── MarketSchedule (forex session calendar)
  ├── tradeMutex (all trade mutations serialized)
  └── Room Database v3
        ├── AlertDao + TriggerHistoryDao
        ├── TradeDao + PendingOrderDao + AccountTransactionDao
        ├── AppSettingDao + AppLogDao + SymbolStateDao
        └── MIGRATION_2_3 (non-destructive, preserves existing data)

MainViewModel
  └── StateFlow wrappers + all user actions → PriceMonitorManager
```

**Key libraries:** Kotlin 2.2 · Jetpack Compose + Material 3 · Room 2.7 · OkHttp 4.12 · WorkManager 2.10 · org.json · Android TTS

---

## 🧪 Testing

```bash
./gradlew :app:testDebugUnitTest    # Unit tests
./gradlew :app:lintDebug            # Lint
./gradlew :app:assembleDebug        # Full build
```

Key test coverage:
- **`TradingMathTest`** — PNL/margin math for EUR/USD, USD/JPY, XAU/USD (100 oz), XAG/USD (5,000 oz), EUR/GBP cross-pair
- **`TimeFormatTest`** — UTC offset parsing (`+5:30`, `-4`, `UTC`, blank) and `dd/MM/yyyy HH:mm:ss` formatting
- **`MarketScheduleTest`** — Forex session open/close calendar accuracy

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

## 👨‍💻 Developer

**Praveen Kumar** — Android Systems Architect

📧 [praveens12346@gmail.com](mailto:praveens12346@gmail.com)

---

*FinTrace is a personal market monitoring and virtual trading tool. It does not connect to real brokers, handle real money, or execute real trades. All P/L figures are simulated.*
