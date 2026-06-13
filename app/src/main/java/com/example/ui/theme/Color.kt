package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * FinTrace color system.
 *
 * Organised per the design system: a stepped neutral *surface scale* (so elevation reads
 * through lightness, not shadows, which is essential on dark/AMOLED), brand colors that are
 * slightly desaturated in dark mode, and a strict set of *semantic* colors (success / warning /
 * error / info) that are kept separate from brand identity. Color is never the only signal —
 * every state also carries an icon or label in the UI layer.
 */

// ─── DARK (AMOLED) SURFACE SCALE ────────────────────────────────────────────
// Page background stays true black for OLED battery savings; containers step up in lightness
// so cards, sheets and elevated elements separate cleanly from the page.
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF0B0B0D)            // base surface
val DarkSurfaceContainer = Color(0xFF141417)   // cards at rest
val DarkSurfaceContainerHigh = Color(0xFF1C1C21) // elevated (dialogs, menus)
val DarkSurfaceContainerHighest = Color(0xFF26262C)
val DarkOutline = Color(0xFF2A2A31)
val DarkTextPrimary = Color(0xFFFAFAFA)
val DarkTextSecondary = Color(0xFFA1A1AA)
val DarkTextTertiary = Color(0xFF71717A)

// Back-compat alias (referenced by older code)
val DarkSurfaceCard = DarkSurfaceContainer

// ─── LIGHT SURFACE SCALE ────────────────────────────────────────────────────
val LightBackground = Color(0xFFFBFBFD)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceContainer = Color(0xFFF2F3F6)
val LightSurfaceContainerHigh = Color(0xFFEAECF0)
val LightSurfaceContainerHighest = Color(0xFFE2E5EA)
val LightOutline = Color(0xFFD6D9E0)
val LightTextPrimary = Color(0xFF0E1116)
val LightTextSecondary = Color(0xFF51555E)
val LightTextTertiary = Color(0xFF7A7F88)

val LightSurfaceCard = LightSurfaceContainer

// ─── BRAND ───────────────────────────────────────────────────────────────────
// Terminal green is the FinTrace identity. Dark variant is lightened/desaturated for contrast
// against black; light variant is a deep teal for AA contrast on white.
val BrandDark = Color(0xFF5BE9B0)
val BrandDarkContainer = Color(0xFF0E3B2C)
val BrandLight = Color(0xFF00796B)
val BrandLightContainer = Color(0xFFB6E9DF)

// ─── PRICE MOVEMENT (semantic, dual-mode) ─────────────────────────────────────
val PriceUpDark = Color(0xFF34D399)
val PriceUpLight = Color(0xFF059669)
val PriceDownDark = Color(0xFFF87171)
val PriceDownLight = Color(0xFFDC2626)
val NeutralPrice = Color(0xFF8A93A3)

// ─── ALERT PRIORITY / STATUS (aligned to the semantic palette) ────────────────
val AlertActive = Color(0xFFF59E0B)     // warning amber  (HIGH)
val AlertTriggered = Color(0xFF3B82F6)  // info blue       (MEDIUM)
val AlertExpired = Color(0xFF64748B)    // neutral slate   (LOW / paused)
val AlertCritical = Color(0xFFEF4444)   // error red       (CRITICAL)
val AlertLow = Color(0xFF22C55E)         // success green

// ─── CONNECTION STATUS ─────────────────────────────────────────────────────────
val ConnectionLive = Color(0xFF22C55E)
val ConnectionReconnecting = Color(0xFFF59E0B)
val ConnectionOffline = Color(0xFFF97316)

// ─── ACCENT ────────────────────────────────────────────────────────────────────
val DemoPurple = Color(0xFF8B5CF6)
