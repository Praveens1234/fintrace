package com.example.data.provider

enum class PriceProvider(
    val displayName: String,
    val apiKeySettingKey: String,
    val description: String,
    val supportsWebSocket: Boolean
) {
    TWELVE_DATA(
        displayName = "Twelve Data",
        apiKeySettingKey = "twelve_data_api_key",
        description = "WebSocket — real-time streaming, low-latency forex & metals",
        supportsWebSocket = true
    ),
    FINNHUB(
        displayName = "Finnhub",
        apiKeySettingKey = "finnhub_api_key",
        description = "WebSocket — real-time streaming, free tier available at finnhub.io",
        supportsWebSocket = true
    ),
    ALPHA_VANTAGE(
        displayName = "Alpha Vantage",
        apiKeySettingKey = "alpha_vantage_api_key",
        description = "REST polling — 25 calls/day free, 5 calls/min rate limit",
        supportsWebSocket = false
    ),
    TRADERMADE(
        displayName = "TraderMade",
        apiKeySettingKey = "tradermade_api_key",
        description = "WebSocket — login-then-subscribe FX & metals feed (14-day trial)",
        supportsWebSocket = true
    ),
    OANDA_V20(
        displayName = "OANDA v20",
        apiKeySettingKey = "oanda_api_key",
        description = "HTTP streaming — free demo account, needs Account ID (practice/live)",
        supportsWebSocket = false
    ),
    ALLTICK(
        displayName = "AllTick",
        apiKeySettingKey = "alltick_api_key",
        description = "WebSocket — cmd_id protocol, free tier streams up to 5 symbols",
        supportsWebSocket = true
    ),
    POLYGON(
        displayName = "Polygon.io",
        apiKeySettingKey = "polygon_api_key",
        description = "WebSocket — auth-then-subscribe; real-time forex needs a paid plan (free = delayed/EOD)",
        supportsWebSocket = true
    );

    companion object {
        // Extra settings keys used by OANDA (besides the bearer token apiKeySettingKey).
        const val OANDA_ACCOUNT_ID_KEY = "oanda_account_id"
        const val OANDA_ENVIRONMENT_KEY = "oanda_environment" // "practice" | "live"
    }
}
