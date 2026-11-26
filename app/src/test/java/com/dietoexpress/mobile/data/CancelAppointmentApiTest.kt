package com.dietoexpress.mobile.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class CancelAppointmentApiTest {
    @Test
    fun cancelSendsThePortalHeaderAndAcceptsNoContent() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()
        try {
            createApi(server).cancelPatientAppointment(7)
            val request = server.takeRequest()
            assertEquals("/api/appointments/7/cancel", request.path)
            assertEquals("1", request.getHeader("X-DietoExpress-Patient-Portal"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rejectedCancellationIsReportedAsAnError() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401))
        server.start()
        try {
            try {
                createApi(server).cancelPatientAppointment(7)
                fail("Un 401 no puede darse por cancelado")
            } catch (ex: HttpException) {
                assertEquals(401, ex.code())
            }
        } finally {
            server.shutdown()
        }
    }

    private fun createApi(server: MockWebServer): PortalApi = Retrofit.Builder()
        .baseUrl(server.url("/api/"))
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(PortalApi::class.java)
}
