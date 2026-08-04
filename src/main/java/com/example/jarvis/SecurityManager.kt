package com.example.jarvis

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import android.util.Log

/**
 * Handles Human-in-the-Loop validation to ensure the Agent cannot execute
 * sensitive real-world actions without explicit user consent.
 */
class SecurityManager(private val activity: FragmentActivity) {

    companion object {
        private const val TAG = "SecurityManager"
    }

    /**
     * Checks if biometric hardware is available and enrolled on the device.
     */
    fun isBiometricReady(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> {
                Log.w(TAG, "Biometric authentication not available or not enrolled.")
                false
            }
        }
    }

    /**
     * Prompts the user for biometric authentication before proceeding with a sensitive action.
     *
     * @param actionDescription A description of what the agent is trying to do, shown to the user.
     * @param onSuccess Callback executed if authentication succeeds.
     * @param onFailure Callback executed if authentication fails or is cancelled.
     */
    fun promptBiometricAuth(
        actionDescription: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.e(TAG, "Authentication error: $errString")
                    onFailure(errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.i(TAG, "Authentication succeeded!")
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "Authentication failed (e.g., wrong fingerprint).")
                    onFailure("Authentication failed")
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authorize Agent Action")
            .setSubtitle("Jarvis wants to: $actionDescription")
            .setDescription("Confirm your identity to allow this action.")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
