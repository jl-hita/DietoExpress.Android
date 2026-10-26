package com.dietoexpress.mobile

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAccessWindowTest {
    private val start = "2026-10-26T10:00:00Z"
    private val end = "2026-10-26T10:30:00Z"

    @Test fun hidesEntryBeforeTheFifteenMinuteWindow() {
        assertFalse(isWithinVideoAccessWindow(start, end, Instant.parse("2026-10-26T09:44:59Z")))
    }

    @Test fun allowsEntryAtTheFifteenMinuteBoundary() {
        assertTrue(isWithinVideoAccessWindow(start, end, Instant.parse("2026-10-26T09:45:00Z")))
    }

    @Test fun hidesEntryAtExpiryAndForInvalidDates() {
        assertFalse(isWithinVideoAccessWindow(start, end, Instant.parse("2026-10-27T10:30:00Z")))
        assertFalse(isWithinVideoAccessWindow("invalid", end, Instant.parse("2026-10-26T10:00:00Z")))
    }
}
