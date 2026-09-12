package com.tendril.app.data.prefs

import java.io.File
import java.util.Properties

/** [KeyValueStore] as a `.properties` file — the desktop has no preference framework, and one
 * flat file next to the database is all these settings need. Written whole on every put. */
class PropertiesKeyValueStore(private val file: File) : MapKeyValueStore(load(file)) {

    override fun persist(all: Map<String, String>) {
        file.parentFile?.mkdirs()
        val props = Properties().apply { all.forEach { (k, v) -> setProperty(k, v) } }
        file.outputStream().use { props.store(it, "Tendril desktop preferences") }
    }

    private companion object {
        fun load(file: File): Map<String, String> {
            if (!file.exists()) return emptyMap()
            val props = Properties().apply { file.inputStream().use { load(it) } }
            return props.stringPropertyNames().associateWith { props.getProperty(it) }
        }
    }
}
