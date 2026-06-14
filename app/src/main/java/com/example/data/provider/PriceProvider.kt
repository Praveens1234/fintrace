package com.example.data.provider

enum class PriceProvider(
    val displayName: String,
    val apiKeySettingKey: String,
    val description: String,
    val supportsWebSocket: Boolean,
    val supportsRest: Boolean
) {
    TWELVE_DATA(
        displayName = "Twelve Data",
        apiKeySettingKey = "twelve_data_api_key",
        description = "WebSocket or REST polling — real-time streaming or configurable poll interval; free tier at twelvedata.com",
        supportsWebSocket = true,
        supportsRest = true
    ),
    FINNHUB(
        displayName = "Finnhub",
        apiKeySettingKey = "finnhub_api_key",
        description = "WebSocket or REST polling — real-time streaming or REST quotes; free tier at finnhub.io",
        supportsWebSocket = true,
        supportsRest = true
    ),
    ALPHA_VANTAGE(
        displayName = "Alpha Vantage",
        apiKeySettingKey = "alpha_vantage_api_key",
        description = "REST polling only — 25 calls/day free, 5 calls/min; no WebSocket",
        supportsWebSocket = false,
        supportsRest = true
    ),
    TRADERMADE(
        displayName = "TraderMade",
        apiKeySettingKey = "tradermade_api_key",
        description = "WebSocket or REST polling — login-subscribe feed or live REST quotes; 14-day free trial",
        supportsWebSocket = true,
        supportsRest = true
    ),
    OANDA_V20(
        displayName = "OANDA v20",
        apiKeySettingKey = "oanda_api_key",
        description = "HTTP chunked streaming only — free demo account; needs Account ID (practice/live)",
        supportsWebSocket = false,
        supportsRest = false
    ),
    ALLTICK(
        displayName = "AllTick",
        apiKeySettingKey = "alltick_api_key",
        description = "WebSocket only — cmd_id protocol; free tier streams up to 5 symbols; no public REST",
        supportsWebSocket = true,
        supportsRest = false
    ),
    POLYGON(
        displayName = "Polygon.io",
        apiKeySettingKey = "polygon_api_key",
        description = "WebSocket or REST polling — real-time forex WebSocket (paid) or REST snapshot; free = delayed/EOD",
        supportsWebSocket = true,
        supportsRest = true
    );

    companion object {
        // Extra settings keys used by OANDA (besides the bearer token apiKeySettingKey).
        const val OANDA_ACCOUNT_ID_KEY = "oanda_account_id"
        const val OANDA_ENVIRONMENT_KEY = "oanda_environment" // "practice" | "live"

        fun signUpUrl(provider: PriceProvider): String = when (provider) {
            TWELVE_DATA   -> "https://twelvedata.com"
            FINNHUB       -> "https://finnhub.io"
            ALPHA_VANTAGE -> "https://www.alphavantage.co/support/#api-key"
            TRADERMADE    -> "https://tradermade.com"
            OANDA_V20     -> "https://www.oanda.com/us-en/trading/"
            ALLTICK       -> "https://alltick.co"
            POLYGON       -> "https://polygon.io"
        }
    }
}
