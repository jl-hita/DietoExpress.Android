package com.dietoexpress.mobile

import com.dietoexpress.mobile.data.TrainingSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TrainingSessionVisibilityTest {
    @Test
    fun showsOnlyCurrentAndFollowingCalendarWeek() {
        val sessions = listOf(
            session(1, "2026-10-31"),
            session(2, "2026-11-02"),
            session(3, "2026-11-08"),
            session(4, "2026-11-09"),
            session(5, "2026-11-15"),
            session(6, "2026-11-16"),
            session(7, "invalid-date")
        )

        val visible = upcomingTrainingSessions(sessions, LocalDate.parse("2026-11-04"))

        assertEquals(listOf(2, 3, 4, 5), visible.map { it.id })
    }

    @Test
    fun startsCurrentWeekOnMondayAndIncludesTheWholeFollowingWeek() {
        val sessions = listOf(
            session(1, "2026-11-01"),
            session(2, "2026-11-02"),
            session(3, "2026-11-15"),
            session(4, "2026-11-16")
        )

        val visible = upcomingTrainingSessions(sessions, LocalDate.parse("2026-11-02"))

        assertEquals(listOf(2, 3), visible.map { it.id })
    }

    private fun session(id: Int, date: String) = TrainingSession(
        id = id,
        planId = 1,
        scheduledDate = date,
        title = "Sesión $id",
        sessionType = "strength"
    )
}
