package com.dietoexpress.mobile.data

import com.dietoexpress.mobile.BuildConfig
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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

interface PortalApi {
    @POST("portal/auth")
    suspend fun authenticate(@Body request: PatientAuthRequest): PatientAuthResponse

    @POST("portal/request-access-link")
    suspend fun requestAccessLink(@Body request: AccessLinkRequest): ApiMessage

    @GET("portal/profile")
    suspend fun profile(): PatientProfile

    @GET("portal/diet")
    suspend fun activeDiet(): DietDetail

    @POST("portal/logout")
    suspend fun logout()
}

/**
 * Cookie de sesión mantenida solo en memoria. No persiste JWT/cookies en almacenamiento
 * no cifrado y nunca registra credenciales. Al cerrar completamente el proceso habrá que
 * iniciar sesión otra vez; la persistencia cifrada con Android Keystore se decidirá aparte.
 */
private class InMemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { incoming ->
            this.cookies.removeAll {
                it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path
            }
            if (incoming.expiresAt > System.currentTimeMillis()) this.cookies.add(incoming)
        }
        this.cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() = cookies.clear()
}

object PortalService {
    private val cookieJar = InMemoryCookieJar()
    private val client = OkHttpClient.Builder().cookieJar(cookieJar).build()

    val api: PortalApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(PortalApi::class.java)

    fun clearSessionCookies() = cookieJar.clear()
}
