package com.tendril.app.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import java.io.File

/** Desktop bootstrap for the shared [TendrilDatabase] — no Context, a plain file path instead. */
fun buildTendrilDatabase(dbFile: File): TendrilDatabase = openTendrilDatabase(dbFile).database

/** §9.10's snapshot-restore fallback on desktop — see [openTendrilDatabase]'s Android twin and
 * [openOrRecover]. Same probe, same set-aside rule, a plain path instead of a Context. */
fun openTendrilDatabase(dbFile: File): DatabaseOpen<TendrilDatabase> {
    dbFile.parentFile?.mkdirs()
    return openOrRecover(
        build = {
            finishBuilding(
                Room.databaseBuilder<TendrilDatabase>(name = dbFile.absolutePath),
                BundledSQLiteDriver(),
            )
        },
        probe = { runBlocking { it.purgedRecordDao().getAll() } },
        close = { it.close() },
        setAside = {
            val stamp = System.currentTimeMillis()
            for (suffix in listOf("", "-wal", "-shm")) {
                val from = File(dbFile.path + suffix)
                if (from.exists()) from.renameTo(File(from.path + UNOPENABLE_SUFFIX + stamp))
            }
        },
    )
}
