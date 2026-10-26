package com.dietoexpress.mobile.data

import android.content.Context
import com.dietoexpress.mobile.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

data class PatientAuthRequest(
    val token: String? = null,
    val emailOrPhone: String? = null,
    val passcode: String? = null
)

data class PatientAuthResponse(
    val clientId: Int,
    val fullName: String,
    val clinicName: String? = null,
    val clinicLogo: String? = null,
    val token: String? = null
)

data class AccessLinkRequest(val email: String)
data class ApiMessage(val message: String? = null)

data class NutritionistRelationship(
    val hasAssignedNutritionist: Boolean,
    val nutritionistId: Int? = null,
    val nutritionistName: String? = null,
    val isClinic: Boolean = false,
    val hasPendingClinicReassignment: Boolean = false
)
data class NutritionistChangeRequest(val type: String, val reason: String? = null)
data class NutritionistChangeResponse(val message: String? = null, val pending: Boolean? = null, val unassigned: Boolean? = null)
data class NutritionistTransfer(
    val pending: Boolean = false,
    val id: Long? = null,
    val status: String? = null,
    val appointmentId: Int? = null,
    val appointmentStartsAt: String? = null,
    val nutritionistName: String? = null,
    val requiresPatientConfirmation: Boolean = false
)
data class ConfirmNutritionistTransferRequest(val consentToHistoryTransfer: Boolean)
data class NutritionistTransferResponse(val completed: Boolean = false, val cancelled: Boolean = false, val message: String? = null)

data class PatientProfile(
    val clientId: Int,
    val fullName: String,
    val age: Int? = null,
    val gender: String? = null,
    val currentWeight: Double? = null,
    val currentHeight: Double? = null,
    val hasActiveDiet: Boolean = false,
    val clinicName: String? = null,
    val clinicPhone: String? = null,
    val nutritionistName: String? = null
)

data class DietDetail(
    val id: Int,
    val name: String,
    val targetKcal: Double? = null,
    val targetProtein: Double? = null,
    val targetCarbs: Double? = null,
    val targetFat: Double? = null,
    val dietType: String? = null,
    val days: List<DietDay> = emptyList()
)

data class DietDay(
    val id: Int,
    val dayIndex: Int,
    val meals: List<DietMeal> = emptyList()
)

data class DietMeal(
    val id: Int,
    val name: String,
    val mealIndex: Int,
    val items: List<DietMealItem> = emptyList()
)

data class DietMealItem(
    val id: Int,
    val foodId: Int? = null,
    val foodName: String? = null,
    val grams: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val recipeName: String? = null,
    val recipeServings: Double? = null,
    val exchangeGroupName: String? = null,
    val exchangeCount: Double? = null
)

data class PatientAppointment(
    val id: Int,
    val startsAt: String,
    val endsAt: String,
    val status: String,
    val nutritionistName: String? = null,
    val modality: String? = null,
    val videoRoomUrl: String? = null
)

data class VideoAccessResponse(
    val provider: String,
    val roomUrl: String,
    val token: String,
    val expiresAt: String,
    val videoStartedAt: String? = null,
    val appointmentStatus: String,
    val canFinalize: Boolean = false,
    val maxCallDurationMinutes: Int = 60
)

data class VideoEventRequest(val event: String)
data class PushTokenRequest(val token: String, val platform: String = "android")

data class AppointmentSlot(
    val startsAt: String,
    val endsAt: String,
    val nutritionistId: Int,
    val nutritionistName: String? = null,
    val onlineConsultationsAvailable: Boolean = false
)

data class CreatePatientAppointmentRequest(
    val startsAt: String,
    val durationMinutes: Int = 30,
    val modality: String = "in_person",
    val patientNotes: String? = null
)

data class PatientDocument(
    val id: Long,
    val name: String,
    val documentType: String? = null,
    val status: String,
    val requiresSignature: Boolean = false,
    val signedAt: String? = null,
    val createdAt: String? = null,
    val originalFileName: String? = null
)

data class ShoppingCategory(
    val category: String,
    val items: List<ShoppingItem> = emptyList()
)

data class ShoppingItem(
    val foodId: Int,
    val foodName: String,
    val category: String,
    val totalGrams: Double,
    val roundedGrams: Double,
    val commercialDescription: String? = null,
    val allergenTags: List<String> = emptyList(),
    val traceTags: List<String> = emptyList(),
    val allergenDataStatus: String? = null,
    val allergenDataAvailable: Boolean = false,
    val requiresAllergenCheck: Boolean = false
)

data class PatientMessage(
    val id: Long,
    val senderType: String,
    val body: String,
    val createdAt: String,
    val readAt: String? = null
)

data class TrainingEnvelope(
    val enabled: Boolean,
    val sessions: List<TrainingSession> = emptyList()
)

data class TrainingSession(
    val id: Int,
    val planId: Int,
    val scheduledDate: String,
    val title: String,
    val sessionType: String,
    val durationMinutes: Int? = null,
    val intensityTarget: String? = null,
    val instructions: String? = null,
    val status: String? = null,
    val completedAt: String? = null,
    val actualDurationMinutes: Int? = null,
    val sessionRpe: Double? = null,
    val recordNotes: String? = null,
    val exercisePrescription: String? = null
)

data class SendPatientMessageRequest(val body: String)

data class CheckinEligibility(
    val eligible: Boolean,
    val message: String? = null
)

data class PatientCheckin(
    val id: Long? = null,
    @SerializedName("week_start") val weekStart: String? = null,
    @SerializedName("submitted_at") val submittedAt: String? = null,
    val weight: Double? = null,
    val adherence: Int? = null,
    val hunger: Int? = null,
    val energy: Int? = null,
    @SerializedName("sleep_quality") val sleepQuality: Int? = null,
    @SerializedName("sleep_hours") val sleepHours: Double? = null,
    val training: Int? = null,
    val difficulties: String? = null,
    val barriers: List<String>? = null,
    val notes: String? = null
)

interface PortalApi {
    @POST("portal/auth")
    suspend fun authenticate(@Body request: PatientAuthRequest): PatientAuthResponse

    @POST("portal/request-access-link")
    suspend fun requestAccessLink(@Body request: AccessLinkRequest): ApiMessage

    @GET("portal/profile")
    suspend fun profile(): PatientProfile

    @GET("portal/nutritionist-relationship")
    suspend fun nutritionistRelationship(): NutritionistRelationship

    @POST("portal/nutritionist-change-requests")
    suspend fun requestNutritionistChange(@Body request: NutritionistChangeRequest): NutritionistChangeResponse

    @GET("portal/nutritionist-transfer")
    suspend fun nutritionistTransfer(): NutritionistTransfer

    @POST("portal/nutritionist-transfer/{id}/confirm")
    suspend fun confirmNutritionistTransfer(@Path("id") id: Long, @Body request: ConfirmNutritionistTransferRequest): NutritionistTransferResponse

    @POST("portal/nutritionist-transfer/{id}/cancel")
    suspend fun cancelNutritionistTransfer(@Path("id") id: Long): NutritionistTransferResponse

    @GET("portal/diet")
    suspend fun activeDiet(): DietDetail

    @GET("appointments/mine")
    suspend fun appointments(): List<PatientAppointment>

    @Headers("X-DietoExpress-Patient-Portal: 1")
    @GET("appointments/{id}/video-access")
    suspend fun videoAccess(@Path("id") id: Int): VideoAccessResponse

    @Headers("X-DietoExpress-Patient-Portal: 1")
    @POST("appointments/{id}/video-event")
    suspend fun recordVideoEvent(@Path("id") id: Int, @Body request: VideoEventRequest)

    @Headers("X-DietoExpress-Patient-Portal: 1")
    @POST("portal/push-tokens")
    suspend fun registerPushToken(@Body request: PushTokenRequest)

    @GET("appointments/slots")
    suspend fun appointmentSlots(@retrofit2.http.Query("days") days: Int = 30): List<AppointmentSlot>

    @POST("appointments")
    suspend fun requestAppointment(@Body request: CreatePatientAppointmentRequest): PatientAppointment

    @GET("portal/documents")
    suspend fun documents(): List<PatientDocument>

    @GET("portal/shopping-list")
    suspend fun shoppingList(): List<ShoppingCategory>

    @GET("messages/patient")
    suspend fun messages(): List<PatientMessage>

    @POST("messages/patient")
    suspend fun sendMessage(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: SendPatientMessageRequest
    ): Map<String, Long>

    @retrofit2.http.PATCH("messages/patient/read")
    suspend fun markMessagesRead(@Header("Idempotency-Key") idempotencyKey: String, @Body body: Map<String, String>)

    @GET("portal/training/sessions")
    suspend fun trainingSessions(): TrainingEnvelope

    @GET("portal/check-ins/eligibility")
    suspend fun checkinEligibility(): CheckinEligibility

    @GET("portal/check-ins/current")
    suspend fun currentCheckin(): PatientCheckin?

    @GET("portal/check-ins")
    suspend fun checkinHistory(): List<PatientCheckin>

    @POST("portal/documents/{documentId}/accept")
    suspend fun acceptDocument(@Path("documentId") documentId: Long)

    @POST("portal/logout")
    suspend fun logout()
}

/**
 * Session cookies are encrypted at rest with an AES-GCM key backed by Android Keystore.
 * Passwords and access codes are never persisted.
 */
private interface ClearableCookieJar : CookieJar { fun clear() }

private data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long,
    val persistent: Boolean,
    val hostOnly: Boolean,
    val secure: Boolean,
    val httpOnly: Boolean
) {
    fun restore(): Cookie? = try {
        Cookie.Builder().name(name).value(value).apply {
            if (hostOnly) hostOnlyDomain(domain) else domain(domain)
            path(path)
            if (persistent) expiresAt(expiresAt)
            if (secure) secure()
            if (httpOnly) httpOnly()
        }.build()
    } catch (_: IllegalArgumentException) { null }

    companion object {
        fun from(cookie: Cookie) = StoredCookie(
            cookie.name, cookie.value, cookie.domain, cookie.path, cookie.expiresAt,
            cookie.persistent, cookie.hostOnly, cookie.secure, cookie.httpOnly
        )
    }
}

private class EncryptedCookieJar(context: Context) : ClearableCookieJar {
    private val preferences = context.applicationContext.getSharedPreferences("dietoexpress_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    @Synchronized private fun readCookies(): MutableList<Cookie> {
        val encrypted = preferences.getString("cookies", null) ?: return mutableListOf()
        return try {
            gson.fromJson(LocalPayloadCipher.decrypt(encrypted), Array<StoredCookie>::class.java)
                .mapNotNull { it.restore() }.filter { it.expiresAt > System.currentTimeMillis() }.toMutableList()
        } catch (_: Exception) {
            preferences.edit().remove("cookies").apply()
            mutableListOf()
        }
    }

    @Synchronized private fun saveCookies(cookies: List<Cookie>) {
        if (cookies.isEmpty()) {
            preferences.edit().remove("cookies").apply()
            return
        }
        val payload = gson.toJson(cookies.map(StoredCookie::from))
        preferences.edit().putString("cookies", LocalPayloadCipher.encrypt(payload)).apply()
    }

    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val current = readCookies()
        for (incoming in cookies) {
            current.removeAll { it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path }
            if (incoming.expiresAt > System.currentTimeMillis()) current.add(incoming)
        }
        saveCookies(current)
    }

    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val current = readCookies()
        val valid = current.filter { it.expiresAt > System.currentTimeMillis() }
        if (valid.size != current.size) saveCookies(valid)
        return valid.filter { it.matches(url) }
    }

    @Synchronized override fun clear() {
        preferences.edit().remove("cookies").apply()
    }
}

private class InMemoryCookieJar : ClearableCookieJar {
    private val cookies = mutableListOf<Cookie>()
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { incoming ->
            this.cookies.removeAll { it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path }
            if (incoming.expiresAt > System.currentTimeMillis()) this.cookies.add(incoming)
        }
        this.cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        return cookies.filter { it.matches(url) }
    }
    @Synchronized override fun clear() = cookies.clear()
}

object PortalService {
    @Volatile private var cookieJar: ClearableCookieJar = InMemoryCookieJar()
    private val client = OkHttpClient.Builder().cookieJar(object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = cookieJar.saveFromResponse(url, cookies)
        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieJar.loadForRequest(url)
    }).build()

    val api: PortalApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(PortalApi::class.java)

    fun initialize(context: Context) { cookieJar = EncryptedCookieJar(context.applicationContext) }
    fun authenticatedClient(): OkHttpClient = client
    fun clearSessionCookies() = cookieJar.clear()
}
