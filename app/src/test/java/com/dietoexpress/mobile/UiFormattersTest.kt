package com.dietoexpress.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.dietoexpress.mobile.data.DietMealItem
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
        val madrid = java.time.ZoneId.of("Europe/Madrid")
        assertEquals("14:05", formatTimeLabel("2026-10-29T14:05:00+01:00", madrid))
        // El servidor envía UTC: en Madrid se suma una hora en invierno y dos en verano.
        assertEquals("15:05", formatTimeLabel("2026-10-29T14:05:00Z", madrid))
        assertEquals("16:05", formatTimeLabel("2026-07-29T14:05:00.000Z", madrid))
        assertEquals("14:05", formatTimeLabel("2026-10-29T14:05:00", madrid))
        assertEquals("2026-10-30", localIsoDate("2026-10-29T23:30:00Z", madrid))
        assertEquals("30/10/2026", formatDateLabel("2026-10-29T23:30:00Z", madrid))
        assertEquals("30/10/2026 00:30", formatDateTimeLabel("2026-10-29T23:30:00Z", madrid))
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
    @Test
    fun calculatesMealNutritionTotalsWithoutTreatingUnknownValuesAsZero() {
        val totals = calculateMealNutrition(
            listOf(
                DietMealItem(id = 1, kcal = 120.0, protein = 10.0, carbs = 15.0, fat = 2.0),
                DietMealItem(id = 2, kcal = 80.5, protein = 4.0, carbs = null, fat = 3.5),
                DietMealItem(id = 3, kcal = null, protein = null, carbs = null, fat = null)
            )
        )

        assertEquals(200.5, totals.kcal!!, 0.001)
        assertEquals(14.0, totals.protein!!, 0.001)
        assertEquals(15.0, totals.carbs!!, 0.001)
        assertEquals(5.5, totals.fat!!, 0.001)
        assertEquals(true, totals.hasAnyValue)
    }

    @Test
    fun mealNutritionIsEmptyWhenNoItemHasNutrientData() {
        val totals = calculateMealNutrition(listOf(DietMealItem(id = 1)))

        assertNull(totals.kcal)
        assertNull(totals.protein)
        assertNull(totals.carbs)
        assertNull(totals.fat)
        assertEquals(false, totals.hasAnyValue)
    }

}

