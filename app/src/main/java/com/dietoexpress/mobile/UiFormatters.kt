package com.dietoexpress.mobile

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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

/** Displays a time only when a timestamp contains a valid HH:mm portion. */
internal fun formatTimeLabel(value: String): String {
    val time = value.substringAfter("T", "").take(5)
    if (!Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(time)) return "Hora pendiente"
    return time
}

/** Keeps compact one-decimal display and respects the device locale. */
internal fun formatNumber(value: Double): String =
    if (value.isFinite() && value % 1.0 == 0.0 && value >= Int.MIN_VALUE && value <= Int.MAX_VALUE) {
        value.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }
