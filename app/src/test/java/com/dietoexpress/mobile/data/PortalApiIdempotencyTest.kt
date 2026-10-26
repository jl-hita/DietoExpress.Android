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
    fun retryAfterLostMessageResponseReusesIdempotencyKeyAndPayload() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(successfulMessageResponse())
        server.start()

        try {
            val api = createApi(server)
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

    @Test
    fun retryAfterLostMarkReadResponseReusesIdempotencyKeyAndPayload() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()

        try {
            val api = createApi(server)
            val operationId = UUID.randomUUID().toString()

            try {
                api.markMessagesRead(operationId, emptyMap())
                fail("La primera petición debía fallar al perderse la respuesta")
            } catch (_: IOException) {
                // La operación sigue pendiente y debe reintentarse con la misma clave.
            }

            val firstAttempt = server.takeRequest()
            api.markMessagesRead(operationId, emptyMap())
            val retryAttempt = server.takeRequest()

            assertEquals("PATCH", firstAttempt.method)
            assertEquals("/api/messages/patient/read", firstAttempt.path)
            assertEquals(operationId, firstAttempt.getHeader("Idempotency-Key"))
            assertEquals(operationId, retryAttempt.getHeader("Idempotency-Key"))
            assertEquals(firstAttempt.body.readUtf8(), retryAttempt.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun weeklyCheckinRetryAfterLostResponseReusesKeyAndPayload() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
            .setBody("""{"id":7,"week_start":"2026-10-26","submitted_at":"2026-10-26T10:00:00Z","weight":90.5,"adherence":80}"""))
        server.start()
        try {
            val api = createApi(server)
            val key = UUID.randomUUID().toString()
            val request = PatientCheckinRequest(
                weight = 90.5, adherence = 80, sleepQuality = 7, sleepHours = 7.5,
                barriers = listOf("planning"), notes = "Semana de prueba"
            )
            try {
                api.submitCheckin(key, request)
                fail("La primera petición debía fallar al perderse la respuesta")
            } catch (_: IOException) {
                // Reintentar con la misma clave evita crear una segunda revisión semanal.
            }
            val first = server.takeRequest()
            val result = api.submitCheckin(key, request)
            val retry = server.takeRequest()
            assertEquals(7L, result.id)
            assertEquals("POST", first.method)
            assertEquals("/api/portal/check-ins", first.path)
            assertEquals(key, first.getHeader("Idempotency-Key"))
            assertEquals(key, retry.getHeader("Idempotency-Key"))
            val firstBody = first.body.readUtf8()
            val retryBody = retry.body.readUtf8()
            assertEquals(firstBody, retryBody)
            assertEquals(7, com.google.gson.JsonParser.parseString(retryBody).asJsonObject.get("sleep_quality").asInt)
        } finally {
            server.shutdown()
        }
    }

    private fun createApi(server: MockWebServer): PortalApi =
        Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PortalApi::class.java)

    private fun successfulMessageResponse() = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"id":42}""")
}
