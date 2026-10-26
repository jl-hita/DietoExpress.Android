package com.dietoexpress.mobile

import java.time.Duration
import java.time.Instant

/** Mirrors the server's patient room window: 15 minutes before start through 15 minutes after end. */
internal fun isWithinVideoAccessWindow(startsAt: String, endsAt: String, now: Instant): Boolean = runCatching {
    val start = Instant.parse(startsAt)
    val end = Instant.parse(endsAt)
    !now.isBefore(start.minus(Duration.ofMinutes(15))) && now.isBefore(end.plus(Duration.ofMinutes(15)))
}.getOrDefault(false)
