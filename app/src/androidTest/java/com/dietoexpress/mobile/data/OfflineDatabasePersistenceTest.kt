package com.dietoexpress.mobile.data

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OfflineDatabasePersistenceTest {
    @Test
    fun encryptedOutboxSurvivesDatabaseCloseAndReopenAndStaysAccountScoped() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "offline-restart-test-${UUID.randomUUID()}.db"
        val operationId = UUID.randomUUID().toString()
        val payload = """{"body":"mensaje pendiente tras reiniciar"}"""
        try {
            val first = androidx.room.Room.databaseBuilder(context, OfflineDatabase::class.java, databaseName)
                .addMigrations(OfflineDatabase.MIGRATION_1_2)
                .build()
            first.offlineDao().enqueue(
                PendingOperationEntity(
                    id = operationId,
                    accountId = 101,
                    operationType = OfflineRepository.OP_SEND_PATIENT_MESSAGE,
                    encryptedPayload = LocalPayloadCipher.encrypt(payload),
                    createdAtEpochMs = 1234L,
                    idempotencyKey = operationId
                )
            )
            first.close()

            // Reopening the on-disk database simulates process death: only committed Room data remains.
            val reopened = androidx.room.Room.databaseBuilder(context, OfflineDatabase::class.java, databaseName)
                .addMigrations(OfflineDatabase.MIGRATION_1_2)
                .build()
            try {
                val restored = reopened.offlineDao().pendingOperations(101).single()
                assertEquals(operationId, restored.id)
                assertEquals(operationId, restored.idempotencyKey)
                assertEquals(payload, LocalPayloadCipher.decrypt(restored.encryptedPayload))
                assertTrue(reopened.offlineDao().pendingOperations(202).isEmpty())
                assertNotEquals(payload, restored.encryptedPayload)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
            File(context.getDatabasePath(databaseName).path + "-wal").delete()
            File(context.getDatabasePath(databaseName).path + "-shm").delete()
        }
    }

    @Test
    fun migrationFromVersionOnePreservesPendingOperationAndAddsIdempotencyKey() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "offline-migration-test-${UUID.randomUUID()}.db"
        try {
            val helper = object : SQLiteOpenHelper(context, databaseName, null, 1) {
                override fun onCreate(db: SQLiteDatabase) {
                    db.execSQL("CREATE TABLE cached_payloads (accountId INTEGER NOT NULL, cacheKey TEXT NOT NULL, encryptedPayload TEXT NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(accountId, cacheKey))")
                    db.execSQL("CREATE TABLE pending_operations (id TEXT NOT NULL PRIMARY KEY, accountId INTEGER NOT NULL, operationType TEXT NOT NULL, encryptedPayload TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, attemptCount INTEGER NOT NULL, status TEXT NOT NULL, lastError TEXT)")
                }
                override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }
            helper.writableDatabase.execSQL("INSERT INTO pending_operations (id, accountId, operationType, encryptedPayload, createdAtEpochMs, attemptCount, status, lastError) VALUES ('legacy-op', 101, 'SEND_PATIENT_MESSAGE', 'ciphertext', 1000, 0, 'PENDING', NULL)")
            helper.close()

            val db = androidx.room.Room.databaseBuilder(context, OfflineDatabase::class.java, databaseName)
                .addMigrations(OfflineDatabase.MIGRATION_1_2)
                .build()
            try {
                val restored = db.offlineDao().pendingOperations(101).single()
                assertEquals("legacy-op", restored.id)
                assertEquals("legacy-op", restored.idempotencyKey)
                assertEquals(PendingOperationEntity.STATUS_PENDING, restored.status)
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
