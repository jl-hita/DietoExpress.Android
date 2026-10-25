package com.dietoexpress.mobile.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dietoexpress.mobile.data.LocalPayloadCipher
import com.dietoexpress.mobile.data.OfflineRepository
import com.dietoexpress.mobile.data.PortalService
import com.dietoexpress.mobile.data.SendPatientMessageRequest
import com.dietoexpress.mobile.data.SessionOperationLock
import com.google.gson.Gson
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

class SyncPendingOperationsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val accountId = inputData.getInt(KEY_ACCOUNT_ID, -1)
        if (accountId <= 0) return Result.failure()
        PortalService.initialize(applicationContext)
        val repository = OfflineRepository(applicationContext)
        val operations = repository.pendingOperations(accountId)

        var initialSessionMismatch = false
        SessionOperationLock.withLock {
            if (!repository.activeAccountMatches(accountId)) {
                operations.forEach { repository.needsAuthentication(it.id) }
                initialSessionMismatch = true
            }
        }
        if (initialSessionMismatch) return Result.success()

        for (operation in operations) {
            if (operation.attemptCount >= MAX_ATTEMPTS) {
                repository.failOperation(operation.id, "Se ha alcanzado el máximo de reintentos. Revisa la operación antes de volver a enviarla.")
                continue
            }
            try {
                var sessionMismatch = false
                var unknownOperation = false
                SessionOperationLock.withLock {
                    if (!repository.activeAccountMatches(accountId)) {
                        operations.forEach { repository.needsAuthentication(it.id) }
                        sessionMismatch = true
                    } else {
                        when (operation.operationType) {
                            OfflineRepository.OP_MARK_MESSAGES_READ ->
                                PortalService.api.markMessagesRead(operation.idempotencyKey, emptyMap())
                            OfflineRepository.OP_SEND_PATIENT_MESSAGE -> {
                                val json = LocalPayloadCipher.decrypt(operation.encryptedPayload)
                                val request = Gson().fromJson(json, SendPatientMessageRequest::class.java)
                                    ?: throw IllegalArgumentException("El mensaje pendiente no contiene un cuerpo válido.")
                                // The key and encrypted body were committed before the first request.
                                PortalService.api.sendMessage(operation.idempotencyKey, request)
                            }
                            else -> {
                                repository.failOperation(operation.id, "Tipo de operación offline no reconocido.")
                                unknownOperation = true
                            }
                        }
                    }
                }
                if (sessionMismatch) return Result.success()
                if (unknownOperation) continue
                repository.completeOperation(operation.id)
            } catch (ex: CancellationException) {
                // A cancelled worker may have reached the server. Keep the durable operation and key.
                throw ex
            } catch (ex: HttpException) {
                when {
                    ex.code() == 401 || ex.code() == 403 -> {
                        repository.needsAuthentication(operation.id)
                        return Result.success()
                    }
                    ex.code() == 409 -> {
                        val details = runCatching { ex.response()?.errorBody()?.string() }.getOrNull()
                        repository.conflictOperation(operation.id, "El servidor detectó un conflicto. No se repetirá automáticamente.", details)
                    }
                    ex.code() >= 500 || ex.code() == 429 -> {
                        if (operation.attemptCount + 1 >= MAX_ATTEMPTS) {
                            repository.failOperation(operation.id, "Se ha alcanzado el máximo de reintentos tras un error temporal.")
                            continue
                        }
                        repository.retryOperation(operation.id, "El servidor rechazó temporalmente la sincronización (HTTP ${ex.code()}).")
                        return Result.retry()
                    }
                    else -> repository.failOperation(operation.id, "El servidor rechazó la operación (HTTP ${ex.code()}).")
                }
            } catch (_: IOException) {
                if (operation.attemptCount + 1 >= MAX_ATTEMPTS) {
                    repository.failOperation(operation.id, "Se ha alcanzado el máximo de reintentos sin conexión.")
                    continue
                }
                repository.retryOperation(operation.id, "Sin conexión; se volverá a intentar.")
                return Result.retry()
            } catch (_: Exception) {
                repository.failOperation(operation.id, "No se ha podido sincronizar esta operación.")
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_ACCOUNT_ID = "account_id"
        private const val MAX_ATTEMPTS = 10
    }
}
