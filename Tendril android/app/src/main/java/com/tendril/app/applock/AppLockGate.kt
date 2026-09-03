package com.tendril.app.applock

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * App Lock (§3.6): `BiometricPrompt` with the device's own PIN/pattern/password as the
 * built-in fallback — no separate in-app PIN to build or store. `BIOMETRIC_STRONG or
 * DEVICE_CREDENTIAL` and `setNegativeButtonText` are mutually exclusive in this API; the
 * system supplies its own "Use PIN" / cancel affordances when DEVICE_CREDENTIAL is allowed.
 */
fun showAppUnlockPrompt(activity: FragmentActivity, onResult: (unlocked: Boolean) -> Unit) {
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Tendril")
        .setAllowedAuthenticators(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()

    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(false)
            }

            override fun onAuthenticationFailed() {
                // A single failed attempt (e.g. wrong fingerprint) — the prompt stays open
                // for another try; only a final error/cancel resolves onResult.
            }
        },
    )
    prompt.authenticate(promptInfo)
}
