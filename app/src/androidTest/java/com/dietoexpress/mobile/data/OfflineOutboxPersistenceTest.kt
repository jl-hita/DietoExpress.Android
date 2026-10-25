package com.dietoexpress.mobile.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OfflineOutboxPersistenceTest {
    @Test
    fun encryptedPendingMessageSurvivesDatabaseReopenAndIsAccountScoped() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "offline-outbox-test-${UUID.randomUUID()}.db"
        val accountId = 71421
        val operationId = UUID.randomUUID().toString()
        val payload = Gson().toJson(SendPatientMessageRequest("Prueba de persistencia offline"))
        val encryptedPayload = LocalPayloadCipher.encrypt(payload)

        var database = Room.databaseBuilder(context, OfflineDatabase::class.java, databaseName).build()
        try {
            runBlocking {
                database.offlineDao().enqueue(
                    PendingOperationEntity(
                        id = operationId,
                        accountId = accountId,
                        operationType = OfflineRepository.OP_SEND_PATIENT_MESSAGE,
                        encryptedPayload = encryptedPayload,
                        createdAtEpochMs = System.currentTimeMillis()
                    )
                )
            }
        } finally {
            database.close()
        }

        // Reabrir el mismo fichero simula la pérdida de la instancia/proceso, no solo otra lectura
        // de la misma RoomDatabase en memoria.
        database = Room.databaseBuilder(context, OfflineDatabase::class.java, databaseName).build()
        try {
            val restored = runBlocking { database.offlineDao().pendingOperations(accountId, System.currentTimeMillis()) }
            assertEquals(1, restored.size)
            assertEquals(operationId, restored.single().id)
            assertEquals(
                payload,
                LocalPayloadCipher.decrypt(restored.single().encryptedPayload)
            )
            assertTrue(
                runBlocking { database.offlineDao().pendingOperations(accountId + 1, System.currentTimeMillis()) }.isEmpty()
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
