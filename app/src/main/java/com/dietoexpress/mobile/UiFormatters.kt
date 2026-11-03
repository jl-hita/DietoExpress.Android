package com.dietoexpress.mobile

import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAccessor
import java.util.Locale

private val spanishDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale("es", "ES"))

/** Formats ISO dates as DD/MM/YYYY while preserving unknown server values. */
internal fun formatDateLabel(value: String): String {
    val rawDate = value.take(10)
    return try {
        LocalDate.parse(rawDate, DateTimeFormatter.ISO_LOCAL_DATE).format(spanishDateFormatter)
    } catch (_: DateTimeParseException) {
        value
    }
}

/** Shows a time only when the complete ISO date-time value is valid. */
internal fun formatTimeLabel(value: String): String {
    return try {
        val parsed: TemporalAccessor = DateTimeFormatter.ISO_DATE_TIME.parse(value)
        LocalTime.from(parsed).format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
    } catch (_: java.time.DateTimeException) {
        "Hora pendiente"
    }
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
