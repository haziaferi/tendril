package com.tendril.app.data.prefs

import android.content.Context

/** [KeyValueStore] on `SharedPreferences` — the same file family every Android preference here uses. */
class AndroidKeyValueStore(context: Context) : MapKeyValueStore(
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all
        .mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap(),
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun persist(all: Map<String, String>) {
        prefs.edit().clear().apply { all.forEach { (k, v) -> putString(k, v) } }.apply()
    }

    private companion object {
        const val PREFS_NAME = "tendril_kv"
    }
}
