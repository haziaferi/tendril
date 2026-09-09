package com.tendril.app.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteDriver
import com.tendril.app.data.canvas.CanvasEdge
import com.tendril.app.data.canvas.CanvasEdgeDao
import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeDao
import com.tendril.app.data.canvas.PageCanvas
import com.tendril.app.data.canvas.PageCanvasDao
import com.tendril.app.data.completion.EntryCompletion
import com.tendril.app.data.completion.EntryCompletionDao
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitDao
import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockDao
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.PageFtsDao
import com.tendril.app.data.page.PageFtsEntry
import com.tendril.app.data.page.PageRelation
import com.tendril.app.data.page.PageRelationDao
import com.tendril.app.data.page.PageTag
import com.tendril.app.data.page.Tag
import com.tendril.app.data.page.TagDao
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.PageDatabaseDao
import com.tendril.app.data.pagedatabase.PageDatabaseView
import com.tendril.app.data.pagedatabase.PageDatabaseViewDao
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyDao
import com.tendril.app.data.pagedatabase.PropertyValue
import com.tendril.app.data.pagedatabase.PropertyValueDao
import com.tendril.app.data.purge.PurgedRecord
import com.tendril.app.data.purge.PurgedRecordDao
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.ReminderDao
import kotlinx.coroutines.Dispatchers

/**
 * §9.10 (Decided 2026-08-26, revised at v9) — Room migration policy. v1–v8 were
 * `fallbackToDestructiveMigration()` pre-v1: nothing stored was anything but trivially
 * re-creatable and the schema was still visibly moving. **v9 is the first version with a
 * real migration path** ([MIGRATION_8_9], S2), so a bump from v8 preserves data instead of
 * clearing it.
 *
 * The destructive fallback below is deliberately still in place, and is not dead code: it
 * catches a jump from any version with *no* declared path — v1–v7, and any future bump
 * that forgets its migration. Removing it is S1b, which stays open on its own terms: the
 * snapshot-restore recovery meant to replace it has never been exercised against a
 * deliberately-broken migration on a populated device (§9.10's own 2026-09-06 correction),
 * so removing it today would trade a working safety net for an unverified one. v9 is what
 * makes that test possible for the first time — until now there was no migration to break.
 *
 * §12.5 — moved into `:shared` (KMP): the platform-specific builder (`Context` on Android,
 * a file path on desktop) lives in each target's own source set; `@ConstructedBy` lets Room's
 * KSP codegen supply the per-platform constructor Room needs to build this class outside of
 * Android's Context-bound APIs.
 */
@Database(
    entities = [
        Entry::class, Habit::class, Reminder::class, EntryCompletion::class,
        Page::class, Tag::class, PageTag::class, Block::class, PageFtsEntry::class,
        PageDatabase::class, Property::class, PropertyValue::class, PageDatabaseView::class,
        PageRelation::class, PageCanvas::class, CanvasNode::class, CanvasEdge::class,
        PurgedRecord::class,
    ],
    // Bump this on ANY change to the entity set or to a column — Room hashes the schema
    // and compares it against the hash stored in `room_master_table` at open time. A hash
    // that moved while `version` stayed put throws IllegalStateException ("you've changed
    // schema but forgot to update the version number") *before* migration runs, so
    // fallbackToDestructiveMigration below never gets the chance to recover: it only
    // handles version changes.
    //
    // v8 is a merge of two independent bumps that each reached a different number from a
    // shared v5: the Canvas tables took it to 6 on one branch, purge tombstones to 7 on
    // the other. The combined entity set hashes to neither, so it has to clear both.
    //
    // v9 adds `uid` to `reminders` and `entry_completions` (S2). Unlike every bump
    // before it, it is migrated rather than destructive — see [MIGRATION_8_9].
    version = 9, // §3.2/§9.9/§5.5.1.1/§9.4 — v5 providerEventId; Canvas tables; purge tombstones; v9 reminder+completion uid
    exportSchema = true, // §9.10 — see `shared/schemas/`; a version with no JSON cannot be migrated from
)
@TypeConverters(Converters::class)
@ConstructedBy(TendrilDatabaseConstructor::class)
abstract class TendrilDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun habitDao(): HabitDao
    abstract fun reminderDao(): ReminderDao
    abstract fun entryCompletionDao(): EntryCompletionDao

    abstract fun pageDao(): PageDao
    abstract fun tagDao(): TagDao
    abstract fun blockDao(): BlockDao
    abstract fun pageFtsDao(): PageFtsDao
    abstract fun pageDatabaseDao(): PageDatabaseDao
    abstract fun propertyDao(): PropertyDao
    abstract fun propertyValueDao(): PropertyValueDao
    abstract fun pageDatabaseViewDao(): PageDatabaseViewDao
    abstract fun pageRelationDao(): PageRelationDao
    abstract fun purgedRecordDao(): PurgedRecordDao
    abstract fun pageCanvasDao(): PageCanvasDao
    abstract fun canvasNodeDao(): CanvasNodeDao
    abstract fun canvasEdgeDao(): CanvasEdgeDao
}

// expect object, actual generated by Room's KSP codegen per target — not hand-written.
expect object TendrilDatabaseConstructor : RoomDatabaseConstructor<TendrilDatabase>

/** Shared finish-and-build step; each platform's own `buildTendrilDatabase` supplies the driver. */
internal fun finishBuilding(
    builder: RoomDatabase.Builder<TendrilDatabase>,
    driver: SQLiteDriver,
): TendrilDatabase =
    builder
        .setDriver(driver)
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(MIGRATION_8_9) // §9.10 — the declared v8 → v9 path, tried before any fallback
        .fallbackToDestructiveMigration(dropAllTables = true) // §9.10 — only for a version with no declared path (S1b)
        .build()
