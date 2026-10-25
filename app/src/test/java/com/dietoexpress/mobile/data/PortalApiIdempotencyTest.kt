package com.dietoexpress.mobile.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.UUID

class PortalApiIdempotencyTest {
    @Test
    fun retryAfterLostResponseReusesIdempotencyKeyAndPayload() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(successfulMessageResponse())
        server.start()

        try {
            val api = Retrofit.Builder()
                .baseUrl(server.url("/api/"))
                .client(OkHttpClient.Builder().retryOnConnectionFailure(false).build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PortalApi::class.java)

            val operationId = UUID.randomUUID().toString()
            val request = SendPatientMessageRequest("Mensaje que debe llegar una sola vez")

            // La primera petición llega al servidor pero la respuesta se pierde.
            try {
                api.sendMessage(operationId, request)
                fail("La primera petición debía fallar al perderse la respuesta")
            } catch (_: IOException) {
                // El reintento de la misma operación debe conservar clave y cuerpo.
            }

            val firstAttempt = server.takeRequest()
            val retryResult = api.sendMessage(operationId, request)
            val retryAttempt = server.takeRequest()

            assertEquals(42L, retryResult["id"])
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
