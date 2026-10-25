package com.dietoexpress.mobile.data

import kotlinx.coroutines.sync.Mutex

/**
 * Serializes session changes with queued writes that use the shared encrypted cookie jar.
 * Callers must hold this lock for the complete login/profile switch or for one queued request.
 */
object SessionOperationLock {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T {
        mutex.lock()
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}
