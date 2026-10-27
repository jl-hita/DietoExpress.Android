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
