package dev.astoris.ursa.ui.lock

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.astoris.ursa.R

/**
 * Thin wrapper over BiometricPrompt for the app-lock gate. Prefers a strong biometric
 * with device-credential (PIN/pattern/password) fallback where the platform allows
 * combining them (API 30+); below that it uses a biometric prompt with a Cancel
 * button. Authentication here only gates the UI; it does not wrap any crypto key.
 */
object BiometricGate {

    /** Authenticators to request, chosen for the running platform version. */
    private fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        else BIOMETRIC_WEAK

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
                    setAllowedAuthenticators(BIOMETRIC_WEAK)
                    setNegativeButtonText(activity.getString(R.string.lock_prompt_cancel))
                }
            }
            .build()

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString) // cancel/lockout: stay locked, let the user retry
                }
            },
        )
        prompt.authenticate(info)
    }
}
