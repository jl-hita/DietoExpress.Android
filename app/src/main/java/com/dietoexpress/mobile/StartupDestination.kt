package com.dietoexpress.mobile

internal enum class StartupDestination {
    INITIALIZING,
    LOGIN,
    PORTAL
}

internal fun resolveStartupDestination(initializing: Boolean, hasProfile: Boolean): StartupDestination =
    when {
        initializing -> StartupDestination.INITIALIZING
        hasProfile -> StartupDestination.PORTAL
        else -> StartupDestination.LOGIN
    }
