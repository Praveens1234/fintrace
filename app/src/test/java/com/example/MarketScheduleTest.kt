package com.example

import com.example.data.market.MarketSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class MarketScheduleTest {

    private val ny = ZoneId.of("America/New_York")

    private fun et(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Instant =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ny).toInstant()

    @Test fun saturday_is_closed() {
        assertFalse(MarketSchedule.isOpen(et(2026, 6, 13, 12))) // Saturday
    }

    @Test fun sunday_opens_at_17_et() {
        assertFalse(MarketSchedule.isOpen(et(2026, 6, 14, 16, 59))) // Sunday before open
        assertTrue(MarketSchedule.isOpen(et(2026, 6, 14, 17, 0)))   // Sunday open
    }

    @Test fun weekday_open_but_daily_break_closed() {
        assertTrue(MarketSchedule.isOpen(et(2026, 6, 15, 10)))      // Monday mid-day
        assertFalse(MarketSchedule.isOpen(et(2026, 6, 15, 17, 30))) // Monday daily break
        assertTrue(MarketSchedule.isOpen(et(2026, 6, 15, 18, 0)))   // Monday after break
    }

    @Test fun friday_closes_at_17_et() {
        assertTrue(MarketSchedule.isOpen(et(2026, 6, 19, 16, 59)))  // Friday before close
        assertFalse(MarketSchedule.isOpen(et(2026, 6, 19, 17, 0)))  // Friday closed
    }

    @Test fun holiday_is_closed() {
        assertFalse(MarketSchedule.isOpen(et(2026, 12, 25, 12)))    // Christmas
    }

    @Test fun nextChange_from_saturday_is_sunday_open() {
        val next = MarketSchedule.nextChange(et(2026, 6, 13, 12))   // from Saturday
        assertEquals(et(2026, 6, 14, 17, 0), next)                  // Sunday 17:00 ET
    }

    @Test fun nextChange_while_open_is_daily_break() {
        val next = MarketSchedule.nextChange(et(2026, 6, 15, 10))   // Monday open
        assertEquals(et(2026, 6, 15, 17, 0), next)                  // -> daily break start
    }
}
