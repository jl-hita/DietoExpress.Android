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
data class CachedPayloadEntity(val accountId: Int, val cacheKey: String, val encryptedPayload: String, val updatedAtEpochMs: Long)

@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey val id: String, val accountId: Int, val operationType: String,
    val encryptedPayload: String, val createdAtEpochMs: Long, val attemptCount: Int = 0,
    val status: String = STATUS_PENDING, val lastError: String? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_NEEDS_AUTH = "NEEDS_AUTH"
        const val STATUS_FAILED = "FAILED"
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
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(operation: PendingOperationEntity): Long
    @Query("SELECT * FROM pending_operations WHERE accountId = :accountId AND status IN ('PENDING', 'NEEDS_AUTH') ORDER BY createdAtEpochMs ASC")
    suspend fun pendingOperations(accountId: Int): List<PendingOperationEntity>
    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun completeOperation(id: String)
    @Query("UPDATE pending_operations SET status = :status, attemptCount = attemptCount + 1, lastError = :error WHERE id = :id")
    suspend fun updateOperationStatus(id: String, status: String, error: String?)
    @Query("UPDATE pending_operations SET status = 'PENDING', lastError = NULL WHERE id = :id")
    suspend fun resetOperationForRetry(id: String)
}

@Database(entities = [CachedPayloadEntity::class, PendingOperationEntity::class], version = 1, exportSchema = false)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao
    companion object {
        @Volatile private var instance: OfflineDatabase? = null
        fun get(context: Context): OfflineDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OfflineDatabase::class.java, "dietoexpress-offline.db")
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
        return String(cipher.doFinal(bytes.copyOfRange(IV_LENGTH, bytes.size)), Charsets.UTF_8)
    }
}
