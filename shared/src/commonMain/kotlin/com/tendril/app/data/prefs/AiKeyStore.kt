package com.tendril.app.data.prefs

import kotlinx.coroutines.flow.StateFlow

/**
 * §0.6.15 — where the person's Anthropic API key lives. A secret, so **never** the
 * [KeyValueStore]: Android keeps it in the Keystore-backed `SecretStore` (§3.5), the desktop in
 * a file of its own with owner-only permissions. Nothing reads it until a verb is pressed.
 */
interface AiKeyStore {
    val key: StateFlow<String?>
    /** Null or blank clears it. */
    fun set(value: String?)
}
