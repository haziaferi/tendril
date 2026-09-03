package com.tendril.app.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val PREFS_NAME = "tendril_secrets"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "tendril_secret_store_key"
private const val TRANSFORMATION = "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
private const val GCM_TAG_LENGTH_BITS = 128

/**
 * Keystore-backed encrypted storage for small local secrets — the Anthropic API key (§3.5)
 * and the sync-folder at-rest passphrase (§9.4.2) — direct `AndroidKeyStore` + `Cipher` use,
 * not `androidx.security:security-crypto` (`EncryptedSharedPreferences`/`MasterKey`), which
 * Google deprecated in 1.1.0 (2025) in favor of exactly this: "existing platform APIs and
 * direct use of Android Keystore."
 *
 * Google Calendar sync (§3.2) deliberately does *not* store anything here: the on-device
 * `AuthorizationClient` flow (§9.5) never yields a persistent refresh token to protect — see
 * [com.tendril.app.googlecalendar.GoogleCalendarAuthManager]'s class doc and the corresponding
 * §9.5 correction for why. Writing a key here does not itself make any network call — the app
 * stays fully local until a feature that actually uses the key is built and invoked (§3.5).
 *
 * Note: this store's key is hardware/Keystore-bound to *this device* — that's correct for
 * the Anthropic key (never meant to leave the device) but means [syncPassphrase] itself is
 * also device-local only, matching §9.4.2's own description: "re-entered once per install"
 * on every device sharing the sync folder, never synced.
 */
class SecretStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _anthropicApiKey = MutableStateFlow(read("anthropic_api_key"))
    val anthropicApiKey: StateFlow<String?> = _anthropicApiKey.asStateFlow()
    fun setAnthropicApiKey(value: String?) {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() }
        _anthropicApiKey.value = normalized
        write("anthropic_api_key", normalized)
    }

    private val _syncPassphrase = MutableStateFlow(read("sync_passphrase"))
    val syncPassphrase: StateFlow<String?> = _syncPassphrase.asStateFlow()
    fun setSyncPassphrase(value: String?) {
        val normalized = value?.takeIf { it.isNotEmpty() } // no trim — a passphrase's whitespace is significant
        _syncPassphrase.value = normalized
        write("sync_passphrase", normalized)
    }

    private fun write(keyName: String, value: String?) {
        if (value == null) {
            prefs.edit { remove("${keyName}_iv"); remove("${keyName}_ciphertext") }
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit {
            putString("${keyName}_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            putString("${keyName}_ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        }
    }

    private fun read(keyName: String): String? {
        val ivB64 = prefs.getString("${keyName}_iv", null) ?: return null
        val ciphertextB64 = prefs.getString("${keyName}_ciphertext", null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(ivB64, Base64.NO_WRAP)),
                )
            }
            String(cipher.doFinal(Base64.decode(ciphertextB64, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
