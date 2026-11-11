package com.dietoexpress.mobile

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricLoginTest {
    @Test
    fun signedMessageMatchesTheServerContract() {
        // Debe coincidir con PatientBiometricCrypto.BuildSignedMessage del backend.
        assertEquals(
            "dietoexpress-biometric-v1|dev1|abc.def",
            String(biometricSignedMessage("dev1", "abc.def"), Charsets.UTF_8)
        )
    }

    @Test
    fun signatureOverTheContractMessageVerifiesOnlyForThatDeviceAndChallenge() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(pair.private)
            update(biometricSignedMessage("dev1", "challenge-1"))
        }.sign()

        fun verify(deviceId: String, challenge: String) = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(pair.public)
            update(biometricSignedMessage(deviceId, challenge))
        }.verify(signature)

        assertTrue(verify("dev1", "challenge-1"))
        assertFalse(verify("dev2", "challenge-1"))
        assertFalse(verify("dev1", "challenge-2"))
    }
}
