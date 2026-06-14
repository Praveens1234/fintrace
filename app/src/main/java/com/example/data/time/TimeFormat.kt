package com.example.data.time

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Timezone-aware timestamp formatting for the app. The user configures a UTC offset as free text
 * (e.g. "+5:30", "-4", "0", "UTC") in Settings; everything defaults to UTC.
 *
 * All trade/order event timestamps are stored as epoch millis from the device clock
 * (System.currentTimeMillis()) — the device clock never stalls, unlike a feed timestamp that can
 * lag behind the websocket update interval — and are formatted here for display.
 */
object TimeFormat {

    private val DISPLAY: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.US)

    private val CLOCK: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

    /**
     * Parse a free-text offset into a [ZoneOffset]. Accepts forms like:
     *   "+5:30", "5:30", "+05:30", "-4", "+0", "0", "UTC", "GMT", "" (→ UTC).
     * Returns null only when the text is non-empty but unparseable, so callers can show an error.
     */
    fun parseOffset(text: String?): ZoneOffset? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty() || raw.equals("UTC", true) || raw.equals("GMT", true)) return ZoneOffset.UTC

        var s = raw.uppercase()
            .removePrefix("UTC").removePrefix("GMT")
            .trim()
        if (s.isEmpty()) return ZoneOffset.UTC

        val sign = when {
            s.startsWith("-") -> -1
            else -> 1
        }
        s = s.removePrefix("+").removePrefix("-")

        val parts = s.split(":", ".")
        val hours = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minutes = if (parts.size > 1) (parts[1].toIntOrNull() ?: return null) else 0
        if (hours !in 0..18 || minutes !in 0..59) return null

        return try {
            ZoneOffset.ofHoursMinutes(sign * hours, sign * minutes)
        } catch (e: Exception) {
            null
        }
    }

    /** Canonical "+HH:MM" string for storage/display of an offset. */
    fun offsetLabel(offset: ZoneOffset): String =
        if (offset == ZoneOffset.UTC) "UTC" else offset.id

    /** Format an epoch-millis timestamp as "dd/MM/yyyy HH:mm:ss" in [offset]. */
    fun format(epochMs: Long, offset: ZoneOffset): String =
        DISPLAY.format(Instant.ofEpochMilli(epochMs).atOffset(offset))

    /** Format an epoch-millis timestamp using a free-text offset (falls back to UTC). */
    fun format(epochMs: Long, offsetText: String?): String =
        format(epochMs, parseOffset(offsetText) ?: ZoneOffset.UTC)

    /** Wall-clock "HH:mm:ss" in [offset] for the live watch on the Prices tab. */
    fun clock(epochMs: Long, offset: ZoneOffset): String =
        CLOCK.format(Instant.ofEpochMilli(epochMs).atOffset(offset))
}
