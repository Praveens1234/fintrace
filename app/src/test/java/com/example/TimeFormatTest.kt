package com.example

import com.example.data.time.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class TimeFormatTest {

    @Test fun parses_positive_offset_with_minutes() {
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), TimeFormat.parseOffset("+5:30"))
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), TimeFormat.parseOffset("5:30"))
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), TimeFormat.parseOffset("+05:30"))
    }

    @Test fun parses_negative_and_zero_offsets() {
        assertEquals(ZoneOffset.ofHours(-4), TimeFormat.parseOffset("-4"))
        assertEquals(ZoneOffset.UTC, TimeFormat.parseOffset("0"))
        assertEquals(ZoneOffset.UTC, TimeFormat.parseOffset("+0"))
    }

    @Test fun blank_and_named_default_to_utc() {
        assertEquals(ZoneOffset.UTC, TimeFormat.parseOffset(""))
        assertEquals(ZoneOffset.UTC, TimeFormat.parseOffset(null))
        assertEquals(ZoneOffset.UTC, TimeFormat.parseOffset("UTC"))
        assertEquals(ZoneOffset.UTC, TimeFormat.parseOffset("GMT"))
    }

    @Test fun rejects_garbage() {
        assertNull(TimeFormat.parseOffset("abc"))
        assertNull(TimeFormat.parseOffset("+99"))
    }

    @Test fun formats_epoch_in_offset() {
        // Known instant: 2021-01-01T00:00:00Z = 1609459200000
        val utc = TimeFormat.format(1609459200000L, ZoneOffset.UTC)
        assertEquals("01/01/2021 00:00:00", utc)
        // +5:30 → 05:30:00 same day
        val ist = TimeFormat.format(1609459200000L, ZoneOffset.ofHoursMinutes(5, 30))
        assertEquals("01/01/2021 05:30:00", ist)
    }
}
