package com.dietoexpress.mobile

import android.app.Application
import com.dietoexpress.mobile.data.PortalService
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.dietoexpress.mobile.BuildConfig

class DietoExpressApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PortalService.initialize(this)

        // Firebase is optional for local/CI builds. Configure the four Gradle properties
        // documented in docs/P1_PUSH_SETUP.md to enable FCM without committing config files.
        if (FirebaseApp.getApps(this).isEmpty() &&
            BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_APPLICATION_ID.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            BuildConfig.FIREBASE_SENDER_ID.isNotBlank()
        ) {
            val options = FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build()
            FirebaseApp.initializeApp(this, options)
        }
    }
}
