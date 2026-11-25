package com.dietoexpress.mobile

import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import com.dietoexpress.mobile.data.DietMealItem

private val spanishDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale("es", "ES"))
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

/**
 * Wall-clock date and time of a server value in the device time zone. The API sends instants in UTC; values without an
 * offset are already local. Returns null for date-only or invalid values.
 */
internal fun toDeviceDateTime(value: String, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? =
    runCatching { OffsetDateTime.parse(value).atZoneSameInstant(zone).toLocalDateTime() }
        .recoverCatching { LocalDateTime.parse(value) }
        .getOrNull()

/** Calendar day (yyyy-MM-dd) of a server value as seen on the device. */
internal fun localIsoDate(value: String, zone: ZoneId = ZoneId.systemDefault()): String =
    toDeviceDateTime(value, zone)?.toLocalDate()?.toString() ?: value.take(10)

/** Formats ISO dates as DD/MM/YYYY while preserving unknown server values. */
internal fun formatDateLabel(value: String, zone: ZoneId = ZoneId.systemDefault()): String {
    return try {
        LocalDate.parse(localIsoDate(value, zone), DateTimeFormatter.ISO_LOCAL_DATE).format(spanishDateFormatter)
    } catch (_: DateTimeParseException) {
        value
    }
}

/** Shows a time only when the complete ISO date-time value is valid. */
internal fun formatTimeLabel(value: String, zone: ZoneId = ZoneId.systemDefault()): String =
    toDeviceDateTime(value, zone)?.format(timeFormatter) ?: "Hora pendiente"

/** Date and time in the device time zone; only the date when the value carries no time. */
internal fun formatDateTimeLabel(value: String, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = formatDateLabel(value, zone)
    val time = toDeviceDateTime(value, zone)?.format(timeFormatter) ?: return date
    return "$date $time"
}

/** Uses the device locale, avoids grouping, and keeps at most one decimal place. */
internal fun formatNumber(value: Double): String {
    if (!value.isFinite()) return "—"
    return NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        isGroupingUsed = false
        maximumFractionDigits = 1
        minimumFractionDigits = 0
        roundingMode = RoundingMode.HALF_UP
    }.format(value)
}


internal data class MealNutritionTotals(
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?
) {
    val hasAnyValue: Boolean
        get() = kcal != null || protein != null || carbs != null || fat != null
}

/** Adds the known nutrient values for all items in a meal; unknown values stay unknown. */
internal fun calculateMealNutrition(items: List<DietMealItem>): MealNutritionTotals {
    fun total(value: (DietMealItem) -> Double?): Double? {
        val knownValues = items.mapNotNull(value)
        return knownValues.takeIf { it.isNotEmpty() }?.sum()
    }

    return MealNutritionTotals(
        kcal = total { it.kcal },
        protein = total { it.protein },
        carbs = total { it.carbs },
        fat = total { it.fat }
    )
}
