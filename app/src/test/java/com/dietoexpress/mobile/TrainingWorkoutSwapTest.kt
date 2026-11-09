package com.dietoexpress.mobile

import com.dietoexpress.mobile.data.TrainingExerciseDraft
import com.dietoexpress.mobile.data.TrainingSetRecord
import com.dietoexpress.mobile.data.TrainingWorkoutDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingWorkoutSwapTest {
    private fun exercise(id: String, completed: Boolean = false) = TrainingExerciseDraft(
        exerciseId = id, exerciseName = id, plannedSets = 2, minRepetitions = 8, maxRepetitions = 10, targetRir = 2.0,
        restSeconds = 90, targetLoadKg = 60.0, loadBasis = "history",
        sets = listOf(
            TrainingSetRecord(0, "warmup", 20.0, 10, null, false),
            TrainingSetRecord(1, "working", 60.0, 8, 2.0, completed),
            TrainingSetRecord(2, "working", 60.0, 8, 2.0, false)
        )
    )

    private fun draft(vararg e: TrainingExerciseDraft) = TrainingWorkoutDraft(1, 0L, e.toList(),
        exerciseSkipReasons = mapOf("squat" to "fatigue"))

    @Test
    fun anExerciseWithoutRegisteredSetsCanBeSwapped() {
        assertTrue(canSwapWorkoutExercise(exercise("squat")))
    }

    @Test
    fun anExerciseWithACompletedSetOrAnswersCannotBeSwapped() {
        assertFalse(canSwapWorkoutExercise(exercise("squat", completed = true)))
        assertFalse(canSwapWorkoutExercise(exercise("squat").copy(stimulus = "high")))
        assertFalse(canSwapWorkoutExercise(exercise("squat").copy(exerciseId = null)))
    }

    @Test
    fun swapKeepsPrescriptionResetsLoadAndLeavesOtherExercisesUntouched() {
        val original = draft(exercise("squat"), exercise("bench", completed = true))
        val result = swapWorkoutExercise(original, 0, "leg-press", "Prensa", emptyList(), 1)
        val swapped = result.exercises[0]
        assertEquals("leg-press", swapped.exerciseId)
        assertEquals("Prensa", swapped.exerciseName)
        assertEquals(2, swapped.plannedSets)
        assertEquals(8, swapped.minRepetitions)
        assertNull(swapped.targetLoadKg)
        assertNull(swapped.loadBasis)
        assertTrue(swapped.sets.all { it.weightKg == null })
        assertEquals(original.exercises[1], result.exercises[1])
        assertFalse(result.exerciseSkipReasons.containsKey("squat"))
    }

    @Test
    fun swapIsIgnoredWhenTheExerciseAlreadyHasRegisteredSets() {
        val original = draft(exercise("squat", completed = true))
        assertEquals(original, swapWorkoutExercise(original, 0, "leg-press", "Prensa", emptyList(), 1))
    }
}
