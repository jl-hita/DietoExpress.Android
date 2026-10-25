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

        // PortalService tiene una cookie compartida por instalación. La comprobación inicial
        // también debe coordinarse con login/logout: si se hace fuera del lock, puede marcar
        // operaciones como NEEDS_AUTH justo cuando la sesión correcta está terminando de activarse.
        var initialSessionMismatch = false
        SessionOperationLock.withLock {
            if (!repository.activeAccountMatches(accountId)) {
                operations.forEach { repository.needsAuthentication(it.id) }
                initialSessionMismatch = true
            }
        }
        if (initialSessionMismatch) return Result.success()

        for (operation in operations) {
            try {
                var sessionMismatch = false
                var unknownOperation = false
                SessionOperationLock.withLock {
                    // Revalidar dentro del mismo bloqueo que protege el inicio/cierre de sesión.
                    // Así, una operación de A nunca utiliza la cookie recién recibida para B.
                    if (!repository.activeAccountMatches(accountId)) {
                        // La sesión pudo cambiar después de la comprobación inicial. Como seguimos
                        // bajo el lock, aparcamos todas las operaciones de esta cuenta de forma
                        // coherente antes de permitir que login/logout cambie de sesión.
                        operations.forEach { repository.needsAuthentication(it.id) }
                        sessionMismatch = true
                    } else {
                        when (operation.operationType) {
                            OfflineRepository.OP_MARK_MESSAGES_READ -> {
                                PortalService.api.markMessagesRead(operation.id, emptyMap())
                            }
                            OfflineRepository.OP_SEND_PATIENT_MESSAGE -> {
                                val json = LocalPayloadCipher.decrypt(operation.encryptedPayload)
                                val request = Gson().fromJson(json, SendPatientMessageRequest::class.java)
                                    ?: throw IllegalArgumentException("El mensaje pendiente no contiene un cuerpo válido.")
                                // operation.id se creó y persistió antes del primer envío; se reutiliza incluso
                                // tras timeout, muerte del proceso o reintento de WorkManager.
                                PortalService.api.sendMessage(operation.id, request)
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
                // WorkManager puede cancelar el worker al sustituir o detener trabajo. No marcar
                // como fallida una operación cuyo envío puede haber llegado al servidor.
                throw ex
            } catch (ex: HttpException) {
                when {
                    ex.code() == 401 || ex.code() == 403 -> {
                        repository.needsAuthentication(operation.id)
                        return Result.success()
                    }
                    ex.code() >= 500 || ex.code() == 429 -> {
                        repository.retryOperation(operation.id, "El servidor rechazó temporalmente la sincronización.")
                        return Result.retry()
                    }
                    else -> repository.failOperation(operation.id, "El servidor rechazó la operación (HTTP ${ex.code()}).")
                }
            } catch (_: IOException) {
                repository.retryOperation(operation.id, "Sin conexión; se volverá a intentar.")
                return Result.retry()
            } catch (_: Exception) {
                repository.failOperation(operation.id, "No se ha podido sincronizar esta operación.")
            }
        }
        return Result.success()
    }

    companion object { const val KEY_ACCOUNT_ID = "account_id" }
}
