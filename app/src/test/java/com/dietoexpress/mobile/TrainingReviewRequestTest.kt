package com.dietoexpress.mobile

import com.dietoexpress.mobile.data.TrainingReviewPlan
import com.dietoexpress.mobile.data.TrainingReviewRequestState
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingReviewRequestTest {
    private fun plan(status: String?) = TrainingReviewPlan(
        planId = 1, name = "Rutina A",
        reviewRequest = status?.let { TrainingReviewRequestState(1, it, "2026-11-09T10:00:00Z") }
    )

    @Test
    fun buttonIsOfferedUnlessARequestIsAlreadyPending() {
        assertTrue(canRequestTrainingReview(plan(null)))
        assertTrue(canRequestTrainingReview(plan("attended")))
        assertTrue(canRequestTrainingReview(plan("rejected")))
        assertFalse(canRequestTrainingReview(plan("pending")))
    }

    @Test
    fun statusLabelsAreClearSpanishAndUnknownStatesShowNothing() {
        assertEquals("Solicitud enviada · pendiente de revisión", trainingReviewStatusLabel("pending"))
        assertEquals("Revisión atendida por tu entrenador", trainingReviewStatusLabel("attended"))
        assertEquals("Tu entrenador ha respondido sin cambios", trainingReviewStatusLabel("rejected"))
        assertNull(trainingReviewStatusLabel(null))
        assertNull(trainingReviewStatusLabel("otro"))
    }

    @Test
    fun offlineAndUnknownErrorsHaveSpanishMessages() {
        assertEquals("Sin conexión: la solicitud de revisión necesita internet.", trainingReviewErrorMessage(IOException()))
        assertTrue(trainingReviewErrorMessage(IllegalStateException()).startsWith("No se pudo enviar"))
    }
}
