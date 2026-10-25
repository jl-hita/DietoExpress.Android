package com.dietoexpress.mobile.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dietoexpress.mobile.data.OfflineRepository
import com.dietoexpress.mobile.data.PortalService
import java.io.IOException
import retrofit2.HttpException

class SyncPendingOperationsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val accountId = inputData.getInt(KEY_ACCOUNT_ID, -1)
        if (accountId <= 0) return Result.failure()
        PortalService.initialize(applicationContext)
        val repository = OfflineRepository(applicationContext)
        for (operation in repository.pendingOperations(accountId)) {
            when (operation.operationType) {
                OfflineRepository.OP_MARK_MESSAGES_READ -> try {
                    PortalService.api.markMessagesRead(operation.id, emptyMap())
                    repository.completeOperation(operation.id)
                } catch (ex: HttpException) {
                    when {
                        ex.code() == 401 || ex.code() == 403 -> { repository.needsAuthentication(operation.id); return Result.success() }
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
                else -> repository.failOperation(operation.id, "Tipo de operación offline no reconocido.")
            }
        }
        return Result.success()
    }
    companion object { const val KEY_ACCOUNT_ID = "account_id" }
}
