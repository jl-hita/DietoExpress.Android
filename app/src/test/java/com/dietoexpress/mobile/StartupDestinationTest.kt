package com.dietoexpress.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupDestinationTest {
    @Test
    fun validCachedSessionDoesNotRouteToLoginWhileInitializing() {
        assertEquals(
            StartupDestination.INITIALIZING,
            resolveStartupDestination(initializing = true, hasProfile = false)
        )
        assertEquals(
            StartupDestination.INITIALIZING,
            resolveStartupDestination(initializing = true, hasProfile = true)
        )
    }

    @Test
    fun completedInitializationRoutesToPortalWhenProfileWasRestored() {
        assertEquals(
            StartupDestination.PORTAL,
            resolveStartupDestination(initializing = false, hasProfile = true)
        )
    }

    @Test
    fun completedInitializationRoutesToLoginWhenNoSessionExists() {
        assertEquals(
            StartupDestination.LOGIN,
            resolveStartupDestination(initializing = false, hasProfile = false)
        )
    }
}
