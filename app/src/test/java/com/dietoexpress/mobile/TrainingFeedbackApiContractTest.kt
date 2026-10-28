package com.dietoexpress.mobile

import com.dietoexpress.mobile.data.PatientTrainingRecordRequest
import com.dietoexpress.mobile.data.TrainingEnvelope
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
            progressionTargetAchieved = false
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
    }

    @Test
    fun trainingResponseReadsStructuredExercisePrescriptionAndKeepsLegacyFallback() {
        val response = gson.fromJson(
            """{"enabled":true,"sessions":[{"id":7,"planId":3,"scheduledDate":"2026-10-28","title":"Cuerpo completo A","sessionType":"strength","exercisePrescription":"1. Sentadilla goblet — 3 × 8-12 reps","exercisePrescriptionData":{"engineVersion":"0.5.0","goal":"Hypertrophy","weekNumber":2,"day":"Wednesday","estimatedDurationMinutes":35,"exercises":[{"exerciseId":"squat-basic","catalogVersion":"catalog-2026-10","exerciseNameSnapshot":"Sentadilla goblet","order":1,"sets":3,"minRepetitions":8,"maxRepetitions":12,"targetRepetitionsInReserve":2.0,"restSeconds":120,"progressionRule":1,"primaryMuscleGroups":["Quadriceps"],"secondaryMuscleGroups":["Glutes"]}]}}]}""",
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
        assertEquals(listOf("Quadriceps"), prescription.exercises.single().primaryMuscleGroups)

        val legacy = gson.fromJson(
            """{"enabled":true,"sessions":[{"id":8,"planId":3,"scheduledDate":"2026-10-29","title":"Sesión heredada","sessionType":"strength","exercisePrescription":"Prensa — 3 × 10"}]}""",
            TrainingEnvelope::class.java
        ).sessions.single()
        assertNull(legacy.exercisePrescriptionData)
        assertEquals("Prensa — 3 × 10", legacy.exercisePrescription)
    }

    @Test
    fun trainingResponseReadsRecommendationAndRemainsCompatibleWithOlderPayloads() {
        val response = gson.fromJson(
            """{"enabled":true,"sessions":[],"adaptationRecommendation":{"action":"hold_progression_and_review","explanation":"Revisa la recuperación."}}""",
            TrainingEnvelope::class.java
        )
        assertEquals("hold_progression_and_review", response.adaptationRecommendation?.action)
        assertEquals("Revisa la recuperación.", response.adaptationRecommendation?.explanation)

        val olderResponse = gson.fromJson("""{"enabled":true,"sessions":[]}""", TrainingEnvelope::class.java)
        assertNull(olderResponse.adaptationRecommendation)
    }
}
