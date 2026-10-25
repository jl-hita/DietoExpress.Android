package com.dietoexpress.mobile.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionOperationLockTest {
    @Test
    fun sessionSwitchAndQueuedRequestCannotRunConcurrently() = runBlocking {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()

        val login = async {
            SessionOperationLock.withLock {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()

        val queuedWrite = async {
            SessionOperationLock.withLock {
                secondEntered.complete(Unit)
            }
        }

        assertFalse("El envío debe esperar mientras cambia la sesión", secondEntered.isCompleted)
        releaseFirst.complete(Unit)
        login.await()
        queuedWrite.await()
        assertTrue(secondEntered.isCompleted)
    }
}
