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
    )
}
