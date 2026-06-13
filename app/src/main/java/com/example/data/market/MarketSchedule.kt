package com.example.data.market

import java.time.Duration
import java.time.DayOfWeek
import java.time.Instant
import java.time.MonthDay
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Trading calendar for spot FX and precious metals (XAU/XAG).
 *
 * The retail spot market runs continuously through the week and is anchored to New York time,
 * so the schedule is evaluated in `America/New_York` and DST is handled automatically by
 * java.time — no manual offset maths, no twice-a-year breakage.
 *
 * Open model (ET):
 *  - Opens Sunday 17:00, closes Friday 17:00 (the weekly session).
 *  - Monday–Thursday a one-hour daily maintenance/rollover break 17:00–18:00 (CME metals
 *    maintenance + FX daily rollover with effectively no liquidity) is treated as closed.
 *  - Saturday is fully closed; Sunday before 17:00 is closed.
 *  - A small, extensible set of full-day holidays (ET) is treated as closed.
 *
 * The object is pure and deterministic (time in → state out), which makes it unit-testable and
 * lets the monitor schedule an exact resume at the next open with no polling drift.
 */
object MarketSchedule {

    private val NY: ZoneId = ZoneId.of("America/New_York")

    /** Full-day closures, evaluated in ET. Extend as needed (e.g. from a remote calendar). */
    private val holidays: Set<MonthDay> = setOf(
        MonthDay.of(1, 1),   // New Year's Day
        MonthDay.of(12, 25)  // Christmas Day
    )

    private const val DAILY_BREAK_HOUR = 17 // 17:00–18:00 ET maintenance/rollover break
    private const val WEEK_OPEN_HOUR = 17   // Sunday 17:00 ET
    private const val WEEK_CLOSE_HOUR = 17  // Friday 17:00 ET

    /** True if the spot market is open at [instant]. */
    fun isOpen(instant: Instant): Boolean {
        val zdt = instant.atZone(NY)
        if (MonthDay.from(zdt) in holidays) return false
        val hour = zdt.hour
        return when (zdt.dayOfWeek) {
            DayOfWeek.SATURDAY -> false
            DayOfWeek.SUNDAY -> hour >= WEEK_OPEN_HOUR
            DayOfWeek.FRIDAY -> hour < WEEK_CLOSE_HOUR
            else -> hour != DAILY_BREAK_HOUR // Mon–Thu: open except the daily break hour
        }
    }

    fun isOpenNow(): Boolean = isOpen(Instant.now())

    /**
     * The next instant (minute precision) at which the open/closed state flips.
     * All session boundaries land on whole minutes, so a minute-granular scan is exact; it is
     * bounded to 8 days (covers the longest weekend/holiday gap) and only runs on transitions.
     */
    fun nextChange(from: Instant = Instant.now()): Instant {
        val start = from.truncatedTo(ChronoUnit.MINUTES)
        val initial = isOpen(start)
        val limit = start.plus(Duration.ofDays(8))
        var t = start
        while (t.isBefore(limit)) {
            t = t.plus(Duration.ofMinutes(1))
            if (isOpen(t) != initial) return t
        }
        return limit
    }

    /** Milliseconds from [from] until the next open→closed / closed→open transition. */
    fun millisUntilNextChange(from: Instant = Instant.now()): Long =
        Duration.between(from, nextChange(from)).toMillis().coerceAtLeast(0L)
}
