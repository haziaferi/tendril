package com.tendril.app.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import java.io.File

/** The database file's name under `Context.getDatabasePath` — public so a test can corrupt the real one. */
const val TENDRIL_DB_NAME = "tendril.db"

/** Android bootstrap for the shared [TendrilDatabase] — Context-based, same file name as before. */
fun buildTendrilDatabase(context: Context): TendrilDatabase = openTendrilDatabase(context).database

/**
 * §9.10's snapshot-restore fallback on Android — see [openOrRecover] for what recovery means and
 * why it sets the old file aside rather than deleting it.
 *
 * The probe is `purgedRecordDao().getAll()`: an existing read of a table that holds one row per
 * "delete forever" and is empty on most devices, so it costs nothing at launch while still forcing
 * Room to open and migrate. Deliberately an existing DAO member rather than a new one — adding a
 * method to a DAO interface here would make every test double implement it to satisfy a call none
 * of them make.
 */
fun openTendrilDatabase(context: Context): DatabaseOpen<TendrilDatabase> {
    val app = context.applicationContext
    return openOrRecover(
        build = {
            finishBuilding(
                Room.databaseBuilder<TendrilDatabase>(context = app, name = TENDRIL_DB_NAME),
                KeepFileOnCorruptionDriver(), // not AndroidSQLiteDriver — see that class for why
            )
        },
        probe = { runBlocking { it.purgedRecordDao().getAll() } },
        close = { it.close() },
        setAside = {
            val stamp = System.currentTimeMillis()
            // The write-ahead log and shared-memory files travel with it. Leaving them beside a
            // freshly created `tendril.db` would hand SQLite a WAL belonging to a different
            // database, which is a worse failure than the one being recovered from.
            for (suffix in listOf("", "-wal", "-shm")) {
                val from = File(app.getDatabasePath(TENDRIL_DB_NAME).path + suffix)
                if (from.exists()) from.renameTo(File(from.path + UNOPENABLE_SUFFIX + stamp))
            }
        },
    )
}
