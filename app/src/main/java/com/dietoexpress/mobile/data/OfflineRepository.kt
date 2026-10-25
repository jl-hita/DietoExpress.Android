package com.dietoexpress.mobile.data

import android.content.Context
import androidx.work.*
import com.dietoexpress.mobile.sync.SyncPendingOperationsWorker
import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException
import java.lang.reflect.Type
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min

data class OfflineRead<T>(
    val value: T,
    val fromCache: Boolean,
    /** True when the cached snapshot has passed its freshness deadline. */
    val isStale: Boolean = false,
    val updatedAtEpochMs: Long? = null
)

class OfflineRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = OfflineDatabase.get(appContext).offlineDao()
    private val gson = Gson()

    suspend fun <T> readThrough(accountId: Int, cacheKey: String, type: Type, fetch: suspend () -> T): OfflineRead<T> {
        pruneOldCache()
        try {
            val fresh = fetch()
            cacheValue(accountId, cacheKey, fresh)
            return OfflineRead(fresh, false, false, System.currentTimeMillis())
        } catch (networkError: IOException) {
            return cachedAfterFailure(accountId, cacheKey, type, networkError)
        } catch (httpError: HttpException) {
            // Never mask authentication, authorization, or an authoritative 404 with old clinical data.
            if (httpError.code() == 401 || httpError.code() == 403 || httpError.code() == 404 ||
                (httpError.code() < 500 && httpError.code() != 429)) throw httpError
            return cachedAfterFailure(accountId, cacheKey, type, httpError)
        }
    }

    private suspend fun <T> cachedAfterFailure(accountId: Int, cacheKey: String, type: Type, cause: Exception): OfflineRead<T> {
        val cached = dao.getCachedPayload(accountId, cacheKey) ?: throw cause
        return try {
            OfflineRead(
                gson.fromJson<T>(LocalPayloadCipher.decrypt(cached.encryptedPayload), type),
                true,
                cached.expiresAtEpochMs <= System.currentTimeMillis(),
                cached.updatedAtEpochMs
            )
        } catch (_: Exception) {
            dao.deleteCachedPayload(accountId, cacheKey)
            throw cause
        }
    }

    suspend fun cacheValue(accountId: Int, key: String, value: Any?) {
        val now = System.currentTimeMillis()
        dao.saveCachedPayload(CachedPayloadEntity(
            accountId = accountId,
            cacheKey = key,
            encryptedPayload = LocalPayloadCipher.encrypt(gson.toJson(value)),
            updatedAtEpochMs = now,
            expiresAtEpochMs = now + freshnessTtlMs(key)
        ))
        pruneOldCache()
    }

    suspend fun <T> cachedValue(accountId: Int, key: String, type: Type): T? {
        pruneOldCache()
        val item = dao.getCachedPayload(accountId, key) ?: return null
        return try { gson.fromJson<T>(LocalPayloadCipher.decrypt(item.encryptedPayload), type) }
        catch (_: Exception) { dao.deleteCachedPayload(accountId, key); null }
    }

    suspend fun invalidate(accountId: Int, key: String) = dao.deleteCachedPayload(accountId, key)

    private suspend fun pruneOldCache() {
        dao.pruneOldCachedPayloads(System.currentTimeMillis() - MAX_CACHE_RETENTION_MS)
    }

    private fun freshnessTtlMs(key: String): Long = when {
        key == "profile" -> PROFILE_TTL_MS
        key == "diet" -> DIET_TTL_MS
        key == "appointments" -> APPOINTMENTS_TTL_MS
        key == "documents" -> DOCUMENTS_TTL_MS
        key == "shopping" -> SHOPPING_TTL_MS
        key == "messages" -> MESSAGES_TTL_MS
        key == "training" -> TRAINING_TTL_MS
        key.startsWith("checkin") -> CHECKIN_TTL_MS
        else -> DEFAULT_TTL_MS
    }

    suspend fun saveMessageDraft(accountId: Int, draft: String) = cacheValue(accountId, DRAFT_KEY, draft)
    suspend fun messageDraft(accountId: Int): String = cachedValue<String>(accountId, DRAFT_KEY, String::class.java).orEmpty()

    /** Queue only a message write whose backend endpoint supports Idempotency-Key. */
    suspend fun enqueueSendPatientMessage(accountId: Int, body: String): String {
        require(body.isNotBlank() && body.length <= 5000) { "El mensaje debe contener entre 1 y 5000 caracteres." }
        val id = UUID.randomUUID().toString()
        val payload = gson.toJson(SendPatientMessageRequest(body))
        dao.enqueue(PendingOperationEntity(
            id = id,
            accountId = accountId,
            operationType = OP_SEND_PATIENT_MESSAGE,
            encryptedPayload = LocalPayloadCipher.encrypt(payload),
            createdAtEpochMs = System.currentTimeMillis(),
            idempotencyKey = id
        ))
        scheduleSync(accountId)
        return id
    }

    /**
     * Kept for compatibility with callers, but callers must invoke it only after the user has
     * actually seen the complete message list online. Never enqueue it from an offline cache read.
     */
    suspend fun enqueueMarkMessagesRead(accountId: Int): String {
        val id = UUID.randomUUID().toString()
        dao.enqueue(PendingOperationEntity(
            id = id,
            accountId = accountId,
            operationType = OP_MARK_MESSAGES_READ,
            encryptedPayload = LocalPayloadCipher.encrypt("{}"),
            createdAtEpochMs = System.currentTimeMillis(),
            idempotencyKey = id
        ))
        scheduleSync(accountId)
        return id
    }

    suspend fun pendingOperations(accountId: Int) = dao.pendingOperations(accountId)
    suspend fun allOperations(accountId: Int): List<PendingOperationEntity> = dao.allOperations(accountId)
    suspend fun activeAccountMatches(accountId: Int): Boolean = activeAccountId() == accountId
    suspend fun completeOperation(id: String) = dao.completeOperation(id)
    suspend fun needsAuthentication(id: String) =
        dao.updateOperationStatus(id, PendingOperationEntity.STATUS_NEEDS_AUTH, "Se necesita iniciar sesión para sincronizar.")
    suspend fun retryOperation(id: String, reason: String) {
        val operation = dao.getOperation(id)
        val attempts = (operation?.attemptCount ?: 0) + 1
        val delayMs = min(30_000L * (1L shl attempts.coerceAtMost(10)), MAX_RETRY_DELAY_MS)
        dao.scheduleRetry(id, System.currentTimeMillis() + delayMs, reason)
    }
    suspend fun failOperation(id: String, reason: String) =
        dao.updateOperationStatus(id, PendingOperationEntity.STATUS_FAILED, reason)
    suspend fun conflictOperation(id: String, reason: String, details: String?) =
        dao.markConflict(id, reason, details?.take(1000))
    suspend fun clearAccountCache(accountId: Int) = dao.clearCachedPayloads(accountId)

    fun setActiveAccountId(accountId: Int) {
        appContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit().putInt(ACTIVE_ACCOUNT_KEY, accountId).apply()
    }

    private fun activeAccountId(): Int? =
        appContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getInt(ACTIVE_ACCOUNT_KEY, -1).takeIf { it > 0 }

    suspend fun restoreLastProfile(): PatientProfile? {
        val accountId = activeAccountId() ?: return null
        return cachedValue(accountId, "profile", PatientProfile::class.java)
    }

    fun clearActiveAccountId(accountId: Int) {
        val prefs = appContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(ACTIVE_ACCOUNT_KEY, -1) == accountId) prefs.edit().remove(ACTIVE_ACCOUNT_KEY).apply()
    }

    fun scheduleSync(accountId: Int) {
        val request = OneTimeWorkRequestBuilder<SyncPendingOperationsWorker>()
            .setInputData(workDataOf(SyncPendingOperationsWorker.KEY_ACCOUNT_ID to accountId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(appContext).enqueueUniqueWork("dietoexpress-offline-sync-$accountId", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        const val OP_MARK_MESSAGES_READ = "MARK_MESSAGES_READ"
        const val OP_SEND_PATIENT_MESSAGE = "SEND_PATIENT_MESSAGE"
        private const val DRAFT_KEY = "message-draft"
        private const val STATE_PREFS = "dietoexpress_offline_state"
        private const val ACTIVE_ACCOUNT_KEY = "active_account_id"
        private const val MAX_CACHE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        private const val MAX_RETRY_DELAY_MS = 6L * 60 * 60 * 1000
        private const val PROFILE_TTL_MS = 24L * 60 * 60 * 1000
        private const val DIET_TTL_MS = 24L * 60 * 60 * 1000
        private const val APPOINTMENTS_TTL_MS = 15L * 60 * 1000
        private const val DOCUMENTS_TTL_MS = 24L * 60 * 60 * 1000
        private const val SHOPPING_TTL_MS = 24L * 60 * 60 * 1000
        private const val MESSAGES_TTL_MS = 5L * 60 * 1000
        private const val TRAINING_TTL_MS = 24L * 60 * 60 * 1000
        private const val CHECKIN_TTL_MS = 60L * 60 * 1000
        private const val DEFAULT_TTL_MS = 60L * 60 * 1000
    }
}
