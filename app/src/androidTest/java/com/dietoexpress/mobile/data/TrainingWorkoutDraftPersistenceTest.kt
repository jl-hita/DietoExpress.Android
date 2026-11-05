package com.dietoexpress.mobile.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingWorkoutDraftPersistenceTest {
    @Test
    fun encryptedDraftSurvivesRepositoryRecreationAndIsAccountScoped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = OfflineRepository(context)
        val accountId = 987654321
        val draft = TrainingWorkoutDraft(
            sessionId = 4321,
            startedAtEpochMs = 1_799_000_000_000,
            exercises = listOf(
                TrainingExerciseDraft(
                    exerciseId = "bench-press",
                    exerciseName = "Press banca",
                    plannedSets = 2,
                    sets = listOf(
                        TrainingSetRecord(1, weightKg = 60.0, repetitions = 10, rir = 2.0, completed = true),
                        TrainingSetRecord(2, weightKg = 60.0, repetitions = 9, rir = 1.0)
                    )
                )
            ),
            sessionRpe = 8.0,
            notes = "Borrador de prueba"
        )

        try {
            assertTrue(repository.saveTrainingDraftImmediately(accountId, draft))

            // A new repository instance simulates a fresh process reading the durable local copy.
            val restored = OfflineRepository(context).trainingDraftImmediately(accountId)
            assertEquals(draft, restored)
            assertNull(OfflineRepository(context).trainingDraftImmediately(accountId + 1))
        } finally {
            repository.clearTrainingDraftImmediately(accountId)
        }
    }
}
