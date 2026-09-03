package com.tendril.app.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

/** Desktop bootstrap for the shared [TendrilDatabase] — no Context, a plain file path instead. */
fun buildTendrilDatabase(dbFile: File): TendrilDatabase {
    dbFile.parentFile?.mkdirs()
    val builder = Room.databaseBuilder<TendrilDatabase>(name = dbFile.absolutePath)
    return finishBuilding(builder, BundledSQLiteDriver())
}
