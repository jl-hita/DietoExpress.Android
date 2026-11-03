package com.dietoexpress.mobile

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UiFormattersTest {
    @Test
    fun formatsIsoDateAsSpanishDayMonthYear() {
        assertEquals("29/10/2026", formatDateLabel("2026-10-29"))
        assertEquals("29/10/2026", formatDateLabel("2026-10-29T14:05:00+02:00"))
    }

    @Test
    fun leavesUnknownOrInvalidDatesVisible() {
        assertEquals("fecha no disponible", formatDateLabel("fecha no disponible"))
        assertEquals("2026-13-40", formatDateLabel("2026-13-40"))
    }

    @Test
    fun formatsOnlyCompleteValidIsoDateTimes() {
        assertEquals("14:05", formatTimeLabel("2026-10-29T14:05:00+02:00"))
        assertEquals("14:05", formatTimeLabel("2026-10-29T14:05:00Z"))
        assertEquals("Hora pendiente", formatTimeLabel(""))
        assertEquals("Hora pendiente", formatTimeLabel("2026-10-29"))
        assertEquals("Hora pendiente", formatTimeLabel("2026-10-29T29:99:00"))
        assertEquals("Hora pendiente", formatTimeLabel("2026-13-40T14:05:00"))
        assertEquals("Hora pendiente", formatTimeLabel("2026-10-29T14:05:garbage"))
    }

    @Test
    fun numberFormattingIsCompactAndUsesDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("es", "ES"))
            assertEquals("12", formatNumber(12.0))
            assertEquals("12,5", formatNumber(12.5))
            assertEquals("2147483648", formatNumber(2147483648.0))
            assertEquals("—", formatNumber(Double.NaN))
            assertEquals("—", formatNumber(Double.POSITIVE_INFINITY))
        } finally {
            Locale.setDefault(original)
        }
    }
}
