plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val signingStorePath = providers.environmentVariable("ANDROID_SIGNING_KEYSTORE_PATH").orNull
val signingStorePassword = providers.environmentVariable("ANDROID_SIGNING_KEYSTORE_PASSWORD").orNull
val signingKeyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD").orNull
val hasStableSigning = listOf(signingStorePath, signingStorePassword, signingKeyAlias, signingKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.dietoexpress.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dietoexpress.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"" + providers.gradleProperty("dietoexpressApiBaseUrl").orElse("https://jlhitap.duckdns.org/api").get() + "\"")
        fun buildConfigLiteral(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        buildConfigField("String", "FIREBASE_API_KEY", buildConfigLiteral(providers.gradleProperty("dietoexpressFirebaseApiKey").orElse("").get()))
        buildConfigField("String", "FIREBASE_APPLICATION_ID", buildConfigLiteral(providers.gradleProperty("dietoexpressFirebaseApplicationId").orElse("").get()))
        buildConfigField("String", "FIREBASE_PROJECT_ID", buildConfigLiteral(providers.gradleProperty("dietoexpressFirebaseProjectId").orElse("").get()))
        buildConfigField("String", "FIREBASE_SENDER_ID", buildConfigLiteral(providers.gradleProperty("dietoexpressFirebaseSenderId").orElse("").get()))
    }

    signingConfigs {
        if (hasStableSigning) {
            create("stable") {
                storeFile = file(signingStorePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Local builds remain usable without secrets; main CI supplies the stable key.
            if (hasStableSigning) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.livekit:livekit-android:2.29.1")
    implementation("com.google.firebase:firebase-messaging:24.1.2")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.1.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
