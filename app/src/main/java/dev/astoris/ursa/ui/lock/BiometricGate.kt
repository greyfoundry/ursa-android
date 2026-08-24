package dev.astoris.ursa.ui.lock

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.astoris.ursa.R
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * BiometricPrompt wrapper for the app-lock gate. Unlock requires a signature from an
 * Android Keystore key whose use is bound to a strong biometric or device credential.
 */
object BiometricGate {

    /** Authenticators to request, chosen for the running platform version. */
    private fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        else BIOMETRIC_STRONG

    /** True if the device can satisfy the app lock (enrolled biometric or credential). */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (CharSequence) -> Unit = {},
    ) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.lock_prompt_title))
            .setSubtitle(activity.getString(R.string.lock_prompt_subtitle))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                } else {
                    // A negative button is required (and only allowed) when device
                    // credential is not among the allowed authenticators.
                    setAllowedAuthenticators(BIOMETRIC_STRONG)
                    setNegativeButtonText(activity.getString(R.string.lock_prompt_cancel))
                }
            }
            .build()

        val signingSignature = try {
            signingSignature()
        } catch (_: KeyPermanentlyInvalidatedException) {
            deleteKey()
            runCatching { signingSignature() }.getOrElse {
                onError(AUTHENTICATION_ERROR)
                return
            }
        } catch (_: Exception) {
            onError(AUTHENTICATION_ERROR)
            return
        }
        val challenge = ByteArray(32).also(SecureRandom()::nextBytes)

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val signature = result.cryptoObject?.signature
                    val proof = runCatching {
                        requireNotNull(signature)
                        signature.update(challenge)
                        signature.sign()
                    }.getOrNull()
                    val verified = runCatching {
                        proof != null && verifyProof(proof, challenge)
                    }.getOrDefault(false)
                    if (verified) onSuccess() else onError(AUTHENTICATION_ERROR)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString) // cancel/lockout: stay locked, let the user retry
                }
            },
        )
        prompt.authenticate(info, BiometricPrompt.CryptoObject(signingSignature))
    }

    private fun signingSignature(): Signature {
        val keyStore = keyStore()
        if (!keyStore.containsAlias(KEY_ALIAS)) generateKey()
        val privateKey = keyStore.getKey(KEY_ALIAS, null)
        return Signature.getInstance(SIGNATURE_ALGORITHM).apply { initSign(privateKey as java.security.PrivateKey) }
    }

    private fun verifyProof(proof: ByteArray, challenge: ByteArray): Boolean {
        val certificate = keyStore().getCertificate(KEY_ALIAS) ?: return false
        return Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initVerify(certificate.publicKey)
            update(challenge)
            verify(proof)
        }
    }

    private fun generateKey() {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
            builder.setInvalidatedByBiometricEnrollment(true)
        }

        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
            initialize(builder.build())
            generateKeyPair()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun deleteKey() {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "ursa_app_lock"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val AUTHENTICATION_ERROR = "Authentication could not be verified"
}
