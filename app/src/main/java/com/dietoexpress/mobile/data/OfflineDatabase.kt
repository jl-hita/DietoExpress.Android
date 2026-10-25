package com.dietoexpress.mobile.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.*
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Entity(tableName = "cached_payloads", primaryKeys = ["accountId", "cacheKey"])
data class CachedPayloadEntity(
    val accountId: Int,
    val cacheKey: String,
    val encryptedPayload: String,
    val updatedAtEpochMs: Long,
    /** Freshness deadline, not a hard deletion deadline: stale data may be shown offline with a warning. */
    @ColumnInfo(defaultValue = "9223372036854775807") val expiresAtEpochMs: Long = Long.MAX_VALUE
)

@Entity(tableName = "pending_operations", indices = [Index(value = ["accountId", "status", "createdAtEpochMs"])])
data class PendingOperationEntity(
    @PrimaryKey val id: String,
    val accountId: Int,
    val operationType: String,
    val encryptedPayload: String,
    val createdAtEpochMs: Long,
    val attemptCount: Int = 0,
    val status: String = STATUS_PENDING,
    val lastError: String? = null,
    /** Stable idempotency key, persisted before the first network request. */
    @ColumnInfo(defaultValue = "''") val idempotencyKey: String = id,
    @ColumnInfo(defaultValue = "0") val nextAttemptAtEpochMs: Long = 0,
    val lastAttemptAtEpochMs: Long? = null,
    val conflictDetails: String? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_NEEDS_AUTH = "NEEDS_AUTH"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CONFLICT = "CONFLICT"
    }
}

@Dao
interface OfflineDao {
    @Query("SELECT * FROM cached_payloads WHERE accountId = :accountId AND cacheKey = :cacheKey LIMIT 1")
    suspend fun getCachedPayload(accountId: Int, cacheKey: String): CachedPayloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCachedPayload(payload: CachedPayloadEntity)

    @Query("DELETE FROM cached_payloads WHERE accountId = :accountId AND cacheKey = :cacheKey")
    suspend fun deleteCachedPayload(accountId: Int, cacheKey: String)

    @Query("DELETE FROM cached_payloads WHERE accountId = :accountId")
    suspend fun clearCachedPayloads(accountId: Int)

    /** Retain expired entries for offline display, but bound local retention to 30 days. */
    @Query("DELETE FROM cached_payloads WHERE updatedAtEpochMs < :retentionCutoffEpochMs")
    suspend fun pruneOldCachedPayloads(retentionCutoffEpochMs: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(operation: PendingOperationEntity): Long

    @Query("SELECT * FROM pending_operations WHERE accountId = :accountId AND status IN ('PENDING', 'NEEDS_AUTH') AND nextAttemptAtEpochMs <= :nowEpochMs ORDER BY createdAtEpochMs ASC")
    suspend fun pendingOperations(accountId: Int, nowEpochMs: Long): List<PendingOperationEntity>

    @Query("SELECT * FROM pending_operations WHERE accountId = :accountId ORDER BY createdAtEpochMs ASC")
    suspend fun allOperations(accountId: Int): List<PendingOperationEntity>

    @Query("SELECT * FROM pending_operations WHERE id = :id LIMIT 1")
    suspend fun getOperation(id: String): PendingOperationEntity?

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun completeOperation(id: String)

    @Query("UPDATE pending_operations SET status = :status, attemptCount = attemptCount + 1, lastAttemptAtEpochMs = :attemptAtEpochMs, lastError = :error, conflictDetails = NULL WHERE id = :id")
    suspend fun updateOperationStatus(id: String, status: String, error: String?, attemptAtEpochMs: Long = System.currentTimeMillis())

    @Query("UPDATE pending_operations SET status = 'PENDING', attemptCount = attemptCount + 1, nextAttemptAtEpochMs = :nextAttemptAtEpochMs, lastError = :error, lastAttemptAtEpochMs = :attemptAtEpochMs WHERE id = :id")
    suspend fun scheduleRetry(id: String, nextAttemptAtEpochMs: Long, error: String, attemptAtEpochMs: Long = System.currentTimeMillis())

    @Query("UPDATE pending_operations SET status = 'CONFLICT', lastError = :error, conflictDetails = :details, lastAttemptAtEpochMs = :attemptAtEpochMs WHERE id = :id")
    suspend fun markConflict(id: String, error: String, details: String?, attemptAtEpochMs: Long = System.currentTimeMillis())

    @Query("UPDATE pending_operations SET status = 'PENDING', lastError = NULL, conflictDetails = NULL, nextAttemptAtEpochMs = 0 WHERE id = :id")
    suspend fun resetOperationForRetry(id: String)
}

@Database(entities = [CachedPayloadEntity::class, PendingOperationEntity::class], version = 2, exportSchema = false)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao

    companion object {
        @Volatile private var instance: OfflineDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_payloads ADD COLUMN expiresAtEpochMs INTEGER NOT NULL DEFAULT 9223372036854775807")
                db.execSQL("UPDATE cached_payloads SET expiresAtEpochMs = updatedAtEpochMs + 86400000")
                db.execSQL("ALTER TABLE pending_operations ADD COLUMN idempotencyKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE pending_operations SET idempotencyKey = id WHERE idempotencyKey = ''")
                db.execSQL("ALTER TABLE pending_operations ADD COLUMN nextAttemptAtEpochMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pending_operations ADD COLUMN lastAttemptAtEpochMs INTEGER")
                db.execSQL("ALTER TABLE pending_operations ADD COLUMN conflictDetails TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_operations_accountId_status_createdAtEpochMs ON pending_operations(accountId, status, createdAtEpochMs)")
            }
        }

        fun get(context: Context): OfflineDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OfflineDatabase::class.java, "dietoexpress-offline.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}

/** AES-GCM payload encryption; the AES key is non-exportable and held by Android Keystore. */
internal object LocalPayloadCipher {
    private const val ALIAS = "dietoexpress_offline_payload_v1"
    private const val IV_LENGTH = 12

    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(ByteBuffer.allocate(cipher.iv.size + body.size).put(cipher.iv).put(body).array(), Base64.NO_WRAP)
    }

    fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > IV_LENGTH)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, IV_LENGTH)))
        return String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8)
    }
}
