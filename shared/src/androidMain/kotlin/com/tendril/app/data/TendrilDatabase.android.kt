package com.tendril.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver

/** Android bootstrap for the shared [TendrilDatabase] — Context-based, same file name as before. */
fun buildTendrilDatabase(context: Context): TendrilDatabase {
    val builder = Room.databaseBuilder<TendrilDatabase>(
        context = context.applicationContext,
        name = "tendril.db",
    )
    return finishBuilding(builder, AndroidSQLiteDriver())
}
