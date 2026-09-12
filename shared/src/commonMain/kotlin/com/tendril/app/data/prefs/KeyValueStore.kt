package com.tendril.app.data.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * §0.10 item 12 (resolved 2026-09-12) — the one cross-platform place for a *device* preference:
 * which calendar layers are on, how often Review asks, a chosen model name. Until this existed
 * every such setting was Android `SharedPreferences` (`TaskPreferences`, `ThemePreferences`…)
 * and the desktop had nowhere to keep one, which is why the layer chips forgot themselves on
 * every start (§0.8 step 6d).
 *
 * Strings only, by design: every consumer encodes its own value (a boolean as "true", an int as
 * digits, a set as a joined list), so the two platform stores are trivial and identical. **Not
 * for secrets** — Android keeps those in `SecretStore` (Keystore), and the desktop's key file
 * (§0.8 step 8g) is its own thing with its own permissions.
 *
 * The platform stores: Android — `SharedPreferences` (`tendril_kv`); desktop — a
 * `java.util.Properties` file under `~/.tendril-desktop-dev/`. Both are in `shared/`'s own
 * source sets, so a third platform is one more file and no change to any consumer.
 */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
    /** The value now and every later [put] to [key] — what a ViewModel collects. */
    fun observe(key: String): Flow<String?>

    fun getBoolean(key: String, default: Boolean): Boolean = get(key)?.toBooleanStrictOrNull() ?: default
    fun putBoolean(key: String, value: Boolean) = put(key, value.toString())
    fun getInt(key: String, default: Int): Int = get(key)?.toIntOrNull() ?: default
    fun putInt(key: String, value: Int) = put(key, value.toString())
}

/**
 * The shape both platform stores share: a map behind [MutableStateFlow]s, one per key ever
 * observed, with [load]/[persist] as the only platform-specific parts. Also the test double —
 * with no persistence it is simply an in-memory store, which is what a unit test wants.
 */
open class MapKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
    private val values = initial.toMutableMap()
    private val flows = mutableMapOf<String, MutableStateFlow<String?>>()

    override fun get(key: String): String? = synchronized(values) { values[key] }

    override fun put(key: String, value: String?) {
        synchronized(values) {
            if (value == null) values.remove(key) else values[key] = value
            persist(values.toMap())
        }
        flows[key]?.value = value
    }

    override fun observe(key: String): StateFlow<String?> =
        synchronized(values) { flows.getOrPut(key) { MutableStateFlow(values[key]) } }

    /** Called under the lock with the whole map after every [put]; the platform writes it. */
    protected open fun persist(all: Map<String, String>) = Unit
}
