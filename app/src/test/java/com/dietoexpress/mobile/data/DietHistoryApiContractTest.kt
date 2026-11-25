package com.dietoexpress.mobile.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DietHistoryApiContractTest {
    @Test
    fun historyDetailAndPdfUseTheAssignmentRoutes() = runBlocking {
        val server = MockWebServer()
        server.enqueue(json("""[{"assignmentId":8,"dietName":"Actual","startDate":"2026-11-01","endDate":null,"isActive":true},
            {"assignmentId":3,"dietName":"Anterior","startDate":"2026-09-01","endDate":"2026-11-01","isActive":false}]"""))
        server.enqueue(json("""{"id":5,"name":"Anterior","targetKcal":1800,"days":[{"id":1,"dayIndex":0,"meals":[]}]}"""))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf").setBody("%PDF-1.7"))
        server.start()

        try {
            val api = Retrofit.Builder()
                .baseUrl(server.url("/api/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PortalApi::class.java)

            val history = api.dietHistory()
            assertNull(history[0].endDate)
            assertEquals("2026-11-01", history[1].endDate)
            assertEquals(1, api.pastDiet(history[1].assignmentId).days.size)
            assertEquals("%PDF-1.7", api.pastDietPdf(3).use { it.string() })

            assertEquals("/api/portal/diet/history", server.takeRequest().path)
            assertEquals("/api/portal/diet/history/3", server.takeRequest().path)
            assertEquals("/api/portal/diet/history/3/pdf", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
