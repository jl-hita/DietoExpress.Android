package com.dietoexpress.mobile.data

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID

class PortalApiIdempotencyTest {
    @Test
    fun retrySendsSameIdempotencyKeyAndPayload() = runBlocking {
        val server = MockWebServer()
        server.enqueue(successfulMessageResponse())
        server.enqueue(successfulMessageResponse())
        server.start()

        try {
            val api = Retrofit.Builder()
                .baseUrl(server.url("/api/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PortalApi::class.java)

            val operationId = UUID.randomUUID().toString()
            val request = SendPatientMessageRequest("Mensaje que debe llegar una sola vez")

            // Simula dos intentos de la misma operación tras perder la primera respuesta.
            api.sendMessage(operationId, request)
            api.sendMessage(operationId, request)

            val firstAttempt = server.takeRequest()
            val retryAttempt = server.takeRequest()

            assertEquals("POST", firstAttempt.method)
            assertEquals("/api/messages/patient", firstAttempt.path)
            assertEquals(operationId, firstAttempt.getHeader("Idempotency-Key"))
            assertEquals(operationId, retryAttempt.getHeader("Idempotency-Key"))
            assertEquals(firstAttempt.body.readUtf8(), retryAttempt.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    private fun successfulMessageResponse() = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"id":42}""")
}
