package com.dietoexpress.mobile.data

import android.content.Context
import androidx.work.*
import com.dietoexpress.mobile.sync.SyncPendingOperationsWorker
import com.google.gson.Gson
import java.io.IOException
import java.lang.reflect.Type
import java.util.UUID
import java.util.concurrent.TimeUnit

data class OfflineRead<T>(val value: T, val fromCache: Boolean)

class OfflineRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = OfflineDatabase.get(appContext).offlineDao()
    private val gson = Gson()

    suspend fun <T> readThrough(accountId: Int, cacheKey: String, type: Type, fetch: suspend () -> T): OfflineRead<T> {
        try {
            val fresh = fetch()
            dao.saveCachedPayload(CachedPayloadEntity(accountId, cacheKey, LocalPayloadCipher.encrypt(gson.toJson(fresh)), System.currentTimeMillis()))
            return OfflineRead(fresh, false)
        } catch (networkError: IOException) {
            val cached = dao.getCachedPayload(accountId, cacheKey) ?: throw networkError
            return try {
                OfflineRead(gson.fromJson<T>(LocalPayloadCipher.decrypt(cached.encryptedPayload), type), true)
            } catch (_: Exception) {
                dao.deleteCachedPayload(accountId, cacheKey)
                throw networkError
            }
        }
    }

    suspend fun cacheValue(accountId: Int, key: String, value: Any?) {
        dao.saveCachedPayload(CachedPayloadEntity(accountId, key, LocalPayloadCipher.encrypt(gson.toJson(value)), System.currentTimeMillis()))
    }
    suspend fun <T> cachedValue(accountId: Int, key: String, type: Type): T? {
        val item = dao.getCachedPayload(accountId, key) ?: return null
        return try { gson.fromJson<T>(LocalPayloadCipher.decrypt(item.encryptedPayload), type) }
        catch (_: Exception) { dao.deleteCachedPayload(accountId, key); null }
    }
    suspend fun saveMessageDraft(accountId: Int, draft: String) = cacheValue(accountId, DRAFT_KEY, draft)
    suspend fun messageDraft(accountId: Int): String = cachedValue<String>(accountId, DRAFT_KEY, String::class.java).orEmpty()

    /** Queue a patient message with a durable key that is reused for every HTTP retry. */
    suspend fun enqueueSendPatientMessage(accountId: Int, body: String): String {
        require(body.isNotBlank() && body.length <= 5000) { "El mensaje debe contener entre 1 y 5000 caracteres." }
        val id = UUID.randomUUID().toString()
        val payload = gson.toJson(SendPatientMessageRequest(body))
        dao.enqueue(PendingOperationEntity(id, accountId, OP_SEND_PATIENT_MESSAGE,
            LocalPayloadCipher.encrypt(payload), System.currentTimeMillis()))
        scheduleSync(accountId)
        return id
    }

    /** Only idempotent operations may enter the automatic retry queue. */
    suspend fun enqueueMarkMessagesRead(accountId: Int): String {
        val id = UUID.randomUUID().toString()
        dao.enqueue(PendingOperationEntity(id, accountId, OP_MARK_MESSAGES_READ,
            LocalPayloadCipher.encrypt("{}"), System.currentTimeMillis()))
        scheduleSync(accountId)
        return id
    }
    suspend fun pendingOperations(accountId: Int) = dao.pendingOperations(accountId)
    suspend fun allOperations(accountId: Int): List<PendingOperationEntity> =
        dao.allOperations(accountId)
    suspend fun activeAccountMatches(accountId: Int): Boolean = activeAccountId() == accountId
    suspend fun completeOperation(id: String) = dao.completeOperation(id)
    suspend fun needsAuthentication(id: String) = dao.updateOperationStatus(id, PendingOperationEntity.STATUS_NEEDS_AUTH, "Se necesita iniciar sesión para sincronizar.")
    suspend fun retryOperation(id: String, reason: String) {
        dao.resetOperationForRetry(id)
        dao.updateOperationStatus(id, PendingOperationEntity.STATUS_PENDING, reason)
    }
    suspend fun failOperation(id: String, reason: String) = dao.updateOperationStatus(id, PendingOperationEntity.STATUS_FAILED, reason)
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
    }
}
