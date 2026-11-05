package com.dietoexpress.mobile

import com.dietoexpress.mobile.data.PatientTrainingRecordRequest
import com.dietoexpress.mobile.data.TrainingEnvelope
import com.dietoexpress.mobile.data.TrainingExerciseFeedback
import com.dietoexpress.mobile.data.TrainingSetRecord
import com.dietoexpress.mobile.data.TrainingWorkoutDraft
import com.dietoexpress.mobile.data.TrainingExerciseDraft
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingFeedbackApiContractTest {
    private val gson = Gson()

    @Test
    fun structuredFeedbackUsesBackendJsonFieldNames() {
        val request = PatientTrainingRecordRequest(
            actualDurationMinutes = 42,
            sessionRpe = 7.5,
            notes = "Buen entrenamiento",
            outcome = "partial",
            nonCompletionReason = "fatigue",
            recoveryRating = "partial",
            painSignal = "none",
            progressionTargetAchieved = false,
            exerciseFeedback = listOf(
                TrainingExerciseFeedback("squat-basic", "Sentadilla goblet", 3, 2, "fatigue"),
                TrainingExerciseFeedback("row-basic", "Remo", 4, 4)
            )
        )

        val json = gson.toJson(request)
        val decoded = gson.fromJson(json, PatientTrainingRecordRequest::class.java)

        assertEquals("partial", decoded.outcome)
        assertEquals("fatigue", decoded.nonCompletionReason)
        assertEquals("partial", decoded.recoveryRating)
        assertEquals("none", decoded.painSignal)
        assertEquals(false, decoded.progressionTargetAchieved)
        assertEquals(42, decoded.actualDurationMinutes)
        assertEquals(7.5, decoded.sessionRpe!!, 0.001)
        assertEquals(2, decoded.exerciseFeedback.size)
        assertEquals(3, decoded.exerciseFeedback.first().plannedSets)
        assertEquals(2, decoded.exerciseFeedback.first().completedSets)
        assertEquals("fatigue", decoded.exerciseFeedback.first().skippedSetReason)
        assertEquals("row-basic", decoded.exerciseFeedback.last().exerciseId)
    }

    @Test
    fun perSetWorkoutDataRoundTripsWithLoadRepetitionsRirAndWarmups() {
        val feedback = TrainingExerciseFeedback(
            exerciseId = "bench-press",
            exerciseName = "Press banca",
            plannedSets = 2,
            completedSets = 2,
            sets = listOf(
                TrainingSetRecord(0, "warmup", 20.0, 12, 5.0, true),
                TrainingSetRecord(1, "working", 60.0, 10, 2.0, true),
                TrainingSetRecord(2, "working", 60.0, 9, 1.0, true)
            )
        )
        val json = gson.toJson(feedback)
        val decoded = gson.fromJson(json, TrainingExerciseFeedback::class.java)

        assertEquals(3, decoded.sets.orEmpty().size)
        assertEquals("warmup", decoded.sets.orEmpty().first().kind)
        assertEquals(60.0, decoded.sets.orEmpty()[1].weightKg!!, 0.001)
        assertEquals(10, decoded.sets.orEmpty()[1].repetitions)
        assertEquals(2.0, decoded.sets.orEmpty()[1].rir!!, 0.001)
        assertEquals(true, decoded.sets.orEmpty()[1].completed)
    }

    @Test
    fun activeWorkoutDraftRoundTripsForEncryptedLocalPersistence() {
        val draft = TrainingWorkoutDraft(
            sessionId = 44,
            startedAtEpochMs = 1_799_000_000_000,
            exercises = listOf(TrainingExerciseDraft(
                exerciseId = "lat-pulldown",
                exerciseName = "Jalón al pecho",
                plannedSets = 2,
                minRepetitions = 8,
                maxRepetitions = 12,
                targetRir = 2.0,
                restSeconds = 120,
                sets = listOf(TrainingSetRecord(1, weightKg = 45.0, repetitions = 10, rir = 2.0, completed = true))
            )),
            sessionRpe = 7.5,
            nonCompletionReason = "time",
            recoveryRating = "partial",
            painSignal = "none"
        )
        val decoded = gson.fromJson(gson.toJson(draft), TrainingWorkoutDraft::class.java)

        assertEquals(44, decoded.sessionId)
        assertEquals("lat-pulldown", decoded.exercises.single().exerciseId)
        assertEquals(45.0, decoded.exercises.single().sets.single().weightKg!!, 0.001)
        assertEquals("time", decoded.nonCompletionReason)
        assertEquals("partial", decoded.recoveryRating)
    }

    @Test
    fun trainingResponseReadsStructuredExercisePrescriptionAndKeepsLegacyFallback() {
        val response = gson.fromJson(
            """{"enabled":true,"sessions":[{"id":7,"planId":3,"scheduledDate":"2026-10-28","title":"Cuerpo completo A","sessionType":"strength","exerciseFeedback":[{"exerciseId":"squat-basic","exerciseName":"Sentadilla goblet","plannedSets":3,"completedSets":2,"skippedSetReason":"fatigue","sets":null}],"exercisePrescription":"1. Sentadilla goblet — 3 × 8-12 reps","exercisePrescriptionData":{"engineVersion":"0.5.0","goal":"Hypertrophy","weekNumber":2,"day":"Wednesday","estimatedDurationMinutes":35,"exercises":[{"exerciseId":"squat-basic","catalogVersion":"catalog-2026-10","exerciseNameSnapshot":"Sentadilla goblet","order":1,"sets":3,"minRepetitions":8,"maxRepetitions":12,"targetRepetitionsInReserve":2.0,"restSeconds":120,"progressionRule":1,"primaryMuscleGroups":[11],"secondaryMuscleGroups":["Glutes"]}]}}]}""",
            TrainingEnvelope::class.java
        )
        val session = response.sessions.single()
        val prescription = session.exercisePrescriptionData!!

        assertEquals("0.5.0", prescription.engineVersion)
        assertEquals(2, prescription.weekNumber)
        assertEquals(1, prescription.exercises.size)
        assertEquals("Sentadilla goblet", prescription.exercises.single().exerciseNameSnapshot)
        assertEquals(3, prescription.exercises.single().sets)
        assertEquals(8, prescription.exercises.single().minRepetitions)
        assertEquals(12, prescription.exercises.single().maxRepetitions)
        assertEquals(2.0, prescription.exercises.single().targetRepetitionsInReserve!!, 0.001)
        assertEquals(120, prescription.exercises.single().restSeconds)
        assertEquals(1, prescription.exercises.single().progressionRule)
        assertEquals(listOf("11"), prescription.exercises.single().primaryMuscleGroups)
        assertEquals(listOf("Glutes"), prescription.exercises.single().secondaryMuscleGroups)
        assertEquals(1, session.exerciseFeedback.size)
        assertEquals(2, session.exerciseFeedback.single().completedSets)
        assertEquals("fatigue", session.exerciseFeedback.single().skippedSetReason)
        assertEquals(0, session.exerciseFeedback.single().sets.orEmpty().size)

        val legacy = gson.fromJson(
            """{"enabled":true,"sessions":[{"id":8,"planId":3,"scheduledDate":"2026-10-29","title":"Sesión heredada","sessionType":"strength","exercisePrescription":"Prensa — 3 × 10"}]}""",
            TrainingEnvelope::class.java
        ).sessions.single()
        assertNull(legacy.exercisePrescriptionData)
        assertEquals("Prensa — 3 × 10", legacy.exercisePrescription)
    }

    @Test
    fun trainingResponseReadsExerciseProgressionRecommendationsAndRemainsCompatibleWithOlderPayloads() {
        val response = gson.fromJson(
            """{"enabled":true,"sessions":[],"adaptationRecommendation":{"action":"hold_progression_and_review","explanation":"Revisa la recuperación."},"exerciseProgressionRecommendations":[{"sessionId":7,"exerciseId":"bench-press","exerciseName":"Press banca","action":"increase_load","explanation":"Dos sesiones consecutivas.","suggestedLoadKg":62.5,"loadIncreaseKg":2.5,"suggestedRepetitionsBySet":[8,8,8],"maximumLoadIncreasePercent":5}]}""",
            TrainingEnvelope::class.java
        )
        assertEquals("hold_progression_and_review", response.adaptationRecommendation?.action)
        assertEquals("Revisa la recuperación.", response.adaptationRecommendation?.explanation)
        val progression = response.exerciseProgressionRecommendations.orEmpty().single()
        assertEquals(7, progression.sessionId)
        assertEquals("bench-press", progression.exerciseId)
        assertEquals("increase_load", progression.action)
        assertEquals(62.5, progression.suggestedLoadKg!!, 0.001)
        assertEquals(listOf(8, 8, 8), progression.suggestedRepetitionsBySet)

        val olderResponse = gson.fromJson("""{"enabled":true,"sessions":[]}""", TrainingEnvelope::class.java)
        assertNull(olderResponse.adaptationRecommendation)
        assertEquals(0, olderResponse.exerciseProgressionRecommendations.orEmpty().size)
    }
}
