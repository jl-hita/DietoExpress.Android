package com.dietoexpress.mobile

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Acceso biométrico: el móvil genera un par EC P-256 en el Keystore cuya clave privada solo puede usarse justo después
 * de una autenticación biométrica fuerte y nunca sale del dispositivo. El servidor guarda únicamente la clave pública y
 * para iniciar sesión emite un desafío de un solo uso que se firma aquí. No se almacena ningún PIN ni token reutilizable.
 */
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val SIGNED_MESSAGE_PREFIX = "dietoexpress-biometric-v1"

/** Texto exacto que firma la app y que comprueba el servidor (ver PatientBiometricCrypto.BuildSignedMessage). */
internal fun biometricSignedMessage(deviceId: String, challenge: String): ByteArray =
    "$SIGNED_MESSAGE_PREFIX|$deviceId|$challenge".toByteArray(Charsets.UTF_8)

internal data class BiometricEnrollment(val deviceId: String, val keyAlias: String, val clientId: Int)

internal class BiometricEnrollmentStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("dietoexpress_biometric", Context.MODE_PRIVATE)

    fun read(): BiometricEnrollment? {
        val deviceId = preferences.getString("deviceId", null) ?: return null
        val alias = preferences.getString("keyAlias", null) ?: return null
        val clientId = preferences.getInt("clientId", -1).takeIf { it > 0 } ?: return null
        return BiometricEnrollment(deviceId, alias, clientId)
    }

    fun save(enrollment: BiometricEnrollment) {
        preferences.edit()
            .putString("deviceId", enrollment.deviceId)
            .putString("keyAlias", enrollment.keyAlias)
            .putInt("clientId", enrollment.clientId)
            .apply()
    }

    fun clear() = preferences.edit().clear().apply()
}

internal object BiometricKeys {
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Crea la clave y devuelve su parte pública (X.509 SubjectPublicKeyInfo en Base64). */
    @Suppress("DEPRECATION")
    fun createKey(alias: String): String {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            // Si el usuario añade otra huella o rostro, la clave se invalida y hay que volver a activar el acceso.
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Autenticación biométrica fuerte obligatoria en cada uso de la clave.
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        generator.initialize(spec)
        return Base64.encodeToString(generator.generateKeyPair().public.encoded, Base64.NO_WRAP)
    }

    /** Devuelve una firma lista para el BiometricPrompt, o null si la clave ya no existe o fue invalidada. */
    fun prepareSignature(alias: String): Signature? = try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val key = keyStore.getKey(alias, null) as? PrivateKey
        key?.let { Signature.getInstance("SHA256withECDSA").apply { initSign(it) } }
    } catch (_: KeyPermanentlyInvalidatedException) {
        null
    }

    fun delete(alias: String) {
        runCatching { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(alias) }
    }
}

/** Muestra el diálogo del sistema; la firma solo se puede usar si la autenticación biométrica tiene éxito. */
internal fun showBiometricSignPrompt(
    activity: FragmentActivity,
    signature: Signature,
    title: String,
    subtitle: String,
    onSigned: (Signature) -> Unit,
    onFailure: (cancelled: Boolean, message: String) -> Unit
) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            val authenticated = result.cryptoObject?.signature
            if (authenticated != null) onSigned(authenticated) else onFailure(false, "No se pudo completar la verificación.")
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_CANCELED
            onFailure(cancelled, errString.toString())
        }
    }
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText("Usar mi código")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()
    BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        .authenticate(info, BiometricPrompt.CryptoObject(signature))
}
