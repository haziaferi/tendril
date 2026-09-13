package com.tendril.app.storage

import com.tendril.app.data.prefs.AiKeyStore
import kotlinx.coroutines.flow.StateFlow

/** §0.6.15 — the shared key store over the Keystore-backed [SecretStore] field §3.5 has had
 * since before anything used it. */
class AndroidAiKeyStore(private val secretStore: SecretStore) : AiKeyStore {
    override val key: StateFlow<String?> get() = secretStore.anthropicApiKey
    override fun set(value: String?) = secretStore.setAnthropicApiKey(value)
}
