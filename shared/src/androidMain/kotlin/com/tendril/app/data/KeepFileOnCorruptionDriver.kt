package com.tendril.app.data

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver

/**
 * §9.10 — the Android driver, with the framework's corruption handler switched off.
 *
 * [AndroidSQLiteDriver] opens the file with `SQLiteDatabase.openOrCreateDatabase(name, null)`, and
 * that `null` is a [DatabaseErrorHandler] — so the framework supplies its
 * `DefaultDatabaseErrorHandler`, whose `onCorruption` **deletes the database file** (with its
 * `-journal`, `-wal` and `-shm`) and reopens an empty one. `SQLiteDatabase.open()` calls it the
 * moment the connection's setup pragmas report `SQLITE_NOTADB`/`SQLITE_CORRUPT`, which is before
 * any statement of ours runs: [openOrRecover]'s probe then finds a pristine database, reports
 * nothing recovered, and the "set aside, never deleted" rule is true of the helper and false of
 * the app. `DatabaseFileTest` is the proof, and it failed before this class existed.
 *
 * This driver is [AndroidSQLiteDriver] except for the handler: a no-op. (Its connection wrapper
 * is `SupportSQLiteConnection` over an *internal* `FrameworkSQLiteDatabase`; the public route to
 * the same thing is [AndroidSQLiteConnection], restricted to the library group but public, and
 * behaviourally the driver's own wrapper — `prepare` builds the same support statement.) The framework's
 * one retry then throws the same corruption exception out of `open()`, Room surfaces it at the
 * probe, and [openOrRecover] does what it was written to do — close, rename aside, rebuild. The
 * desktop's `BundledSQLiteDriver` has no such handler and never needed this.
 *
 * (The same hole in an app on the `SupportSQLiteOpenHelper` path is closed by overriding the
 * callback's `onCorruption` instead — Lunar's `KeepFileOnCorruptionFactory`. Tendril sets a
 * driver, so there is no open helper to wrap; this is the driver-path shape of the same fix.)
 */
class KeepFileOnCorruptionDriver : SQLiteDriver {

    @Suppress("INAPPLICABLE_JVM_NAME") // Due to KT-31420 — mirrors AndroidSQLiteDriver
    @get:JvmName("hasConnectionPool")
    override val hasConnectionPool: Boolean
        get() = true

    override fun open(fileName: String): SQLiteConnection {
        val database = SQLiteDatabase.openDatabase(
            fileName,
            null,
            SQLiteDatabase.CREATE_IF_NECESSARY,
            DatabaseErrorHandler { /* keep the file: the caller sets it aside, nothing deletes it */ },
        )
        @Suppress("RestrictedApi") // see the class doc: the only public wrapper for a framework SQLiteDatabase
        return AndroidSQLiteConnection(database)
    }
}
