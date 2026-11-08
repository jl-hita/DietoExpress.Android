package com.dietoexpress.mobile

import com.dietoexpress.mobile.data.TrainingExerciseFeedback
import com.dietoexpress.mobile.data.TrainingSession
import com.dietoexpress.mobile.data.TrainingSetRecord
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class TrainingWorkoutHelpersTest {
    private fun session(id: Int, date: String, done: Boolean, vararg sets: TrainingSetRecord) = TrainingSession(
        id = id, planId = 1, scheduledDate = date, title = "S", sessionType = "strength",
        completedAt = if (done) "${date}T10:00:00Z" else null,
        exerciseFeedback = listOf(TrainingExerciseFeedback("squat", "Sentadilla", 3, 3, null, sets.toList()))
    )

    private fun set(kg: Double, reps: Int, completed: Boolean = true, kind: String = "working") =
        TrainingSetRecord(1, kind, kg, reps, 2.0, completed)

    @Test
    fun lastWorkingSetUsesTheMostRecentCompletedSessionAndTheHeaviestSet() {
        val history = listOf(
            session(1, "2026-11-01", true, set(50.0, 10)),
            session(2, "2026-11-04", true, set(60.0, 8), set(55.0, 10), set(70.0, 5, kind = "warmup")),
            session(3, "2026-11-06", false, set(90.0, 5))
        )
        val last = lastWorkingSet(history, currentSessionId = 4, exerciseId = "squat")
        assertEquals(60.0, last?.weightKg)
        assertEquals(8, last?.repetitions)
    }

    @Test
    fun lastWorkingSetIgnoresTheCurrentSessionIncompleteSetsAndUnknownExercises() {
        val history = listOf(session(2, "2026-11-04", true, set(60.0, 8, completed = false)))
        assertNull(lastWorkingSet(history, 9, "squat"))
        assertNull(lastWorkingSet(history, 9, "bench"))
        assertNull(lastWorkingSet(history, 9, null))
        assertNull(lastWorkingSet(listOf(session(9, "2026-11-04", true, set(60.0, 8))), 9, "squat"))
    }

    @Test
    fun sessionReasonIsDerivedFromExerciseReasonsUsingOnlyServerAcceptedValues() {
        assertEquals("other", sessionReasonFrom(listOf("load_too_heavy", "load_too_heavy", "fatigue"), "fatigue"))
        assertEquals("time", sessionReasonFrom(listOf("time"), "fatigue"))
        assertEquals("other", sessionReasonFrom(listOf("pain"), "fatigue"))
        assertEquals("fatigue", sessionReasonFrom(emptyList(), "fatigue"))
    }
}

class WeeklyMuscleSetsTest {
    @Test
    fun sumsPrescribedSetsByPrimaryMuscleInsideTheWeekOnly() {
        fun session(date: String, vararg ex: Pair<List<String>, Int>) = TrainingSession(
            id = date.hashCode(), planId = 1, scheduledDate = date, title = "S", sessionType = "strength",
            exercisePrescriptionData = com.dietoexpress.mobile.data.StructuredTrainingPrescription(
                exercises = ex.map { (muscles, sets) ->
                    com.dietoexpress.mobile.data.StructuredTrainingExercise(sets = sets, primaryMuscleGroups = muscles)
                })
        )
        val result = weeklyPrimaryMuscleSets(
            listOf(
                session("2026-11-09", listOf("Chest") to 3, listOf("Chest", "Triceps") to 2),
                session("2026-11-12", listOf("Chest") to 4),
                session("2026-11-16", listOf("Chest") to 9)
            ), java.time.LocalDate.of(2026, 11, 9)
        )
        assertEquals(listOf("Chest" to 9, "Triceps" to 2), result)
    }
}
