package com.tendril.app.data

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * A UUID-v4-shaped string built entirely inside SQLite, re-evaluated per row because
 * `randomblob` is non-deterministic — so one `UPDATE` assigns a distinct value to every
 * existing row rather than the same one to all of them.
 *
 * Shape is the contract, not decoration: 36 characters of hex digits and hyphens, matching
 * what [java.util.UUID.randomUUID] produces, because [com.tendril.app.data.pagedatabase]'s
 * uid handling documents both properties (`Property.kt` — "only hex digits and hyphens",
 * "never shorter than 36") and code elsewhere is entitled to rely on them. The four segments
 * are 8-4-4-4-12; the version nibble is a literal `4` and the variant nibble is drawn from
 * `89ab`, so a migrated row is indistinguishable from a natively-generated one.
 *
 * `random() & 3` rather than `abs(random()) % 4`: SQLite's `abs()` raises an integer-overflow
 * error on exactly one of its 2^64 possible inputs, and a migration is the worst place to
 * carry a defect that rare — it would surface once, on someone's real data, unreproducibly.
 */
private const val UUID_V4_SQL =
    "lower(hex(randomblob(4))) || '-' || " +
        "lower(hex(randomblob(2))) || '-4' || " +
        "substr(lower(hex(randomblob(2))), 2) || '-' || " +
        "substr('89ab', (random() & 3) + 1, 1) || " +
        "substr(lower(hex(randomblob(2))), 2) || '-' || " +
        "lower(hex(randomblob(6)))"

/**
 * §9.10 / S2 — v8 → v9. Gives `reminders` and `entry_completions` the `uid` column §9.4's
 * snapshot merge keys on, so a reminder or a completion can travel between devices at all.
 * The first migration this app has ever run: v1–v8 were all destructive (§9.10 pre-v1
 * policy), so no row has previously had to survive a version bump.
 *
 * **Hand-written rather than `@AutoMigration`, which `build-order.md`'s S2 row specifies.**
 * That mechanism cannot express this migration. Room requires a SQL `defaultValue` for a
 * newly-added NOT NULL column, SQLite forbids a non-deterministic expression in a `DEFAULT`
 * clause (which is exactly what generating a per-row UUID needs), and so every pre-existing
 * row would receive the *same* literal — whereupon `index_<table>_uid`, unique here as it is
 * on all eleven other uid-carrying entities, could not be created on any device with two
 * reminders. `AutoMigrationSpec.onPostMigrate` does not rescue it: the indices are created
 * before it runs. Ordering the three steps by hand is the whole reason this exists.
 *
 * The column is added with `DEFAULT ''` purely so the `ALTER` is legal against existing rows;
 * the entity declares no default, and Room's open-time validation only compares a default it
 * learned from the entity, so the two descriptions do not conflict. The backfill replaces
 * every one of those empty strings before the unique index is built over the column.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        for (table in listOf("reminders", "entry_completions")) {
            connection.execSQL("ALTER TABLE `$table` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
            // Concatenated rather than interpolated: `tools/audit.py` strips string literals
            // before counting references, so a `$UUID_V4_SQL` inside a template is invisible
            // to it and the constant reads as a dead declaration. Keeping the reference outside
            // the literal keeps that check honest instead of baselining a false positive.
            connection.execSQL("UPDATE `$table` SET `uid` = " + UUID_V4_SQL)
            connection.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_uid` ON `$table` (`uid`)",
            )
        }
        // Nullable, so it needs no `DEFAULT` and no backfill: every row that predates v9 is
        // live by definition, and NULL is exactly what "live" means (see [Reminder.deletedAt]).
        connection.execSQL("ALTER TABLE `reminders` ADD COLUMN `deletedAt` INTEGER")
    }
}

/**
 * §9.10 / §0.8 step 2 — v9 → v10, two decisions in one bump because a migration is the
 * hardware-verified step and both were ready.
 *
 * **§0.6.4 — four columns on `entries`.** All nullable or defaulted, so no backfill and no
 * decision made on the person's behalf: a task that predates v10 has no deadline, no parent, no
 * estimate and is not flagged, which is exactly the state it was in. `dueDate` is a second date,
 * not a reinterpretation of `startDate` — see [com.tendril.app.data.entry.Entry.dueDate].
 *
 * **§0.6.6 — `habit_completions`.** The table is created as Room would (the `CREATE` matches the
 * exported v10 schema, which Room validates on open), and then **backfilled from the only history
 * a habit ever kept**: `lastCompletedDate` and `previousCompletedDate`. Two rows at most per
 * habit, stamped with the habit's `updatedAt` because the tap's own instant was never stored. It
 * is a thin memory, but it is the person's, and a presence view that opened on "nothing yet" for
 * a habit checked in yesterday would be wrong in the way §0.5.2 forbids. Uids are generated the
 * way [MIGRATION_8_9] generates them, and for the same reason.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `entries` ADD COLUMN `dueDate` INTEGER")
        connection.execSQL("ALTER TABLE `entries` ADD COLUMN `parentEntryId` INTEGER")
        connection.execSQL("ALTER TABLE `entries` ADD COLUMN `estimate` INTEGER")
        connection.execSQL("ALTER TABLE `entries` ADD COLUMN `important` INTEGER NOT NULL DEFAULT 0")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `habit_completions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uid` TEXT NOT NULL, " +
                "`habitId` INTEGER NOT NULL, " +
                "`date` INTEGER NOT NULL, " +
                "`checkedAt` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, " +
                "FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_completions_uid` ON `habit_completions` (`uid`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_completions_habitId` ON `habit_completions` (`habitId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_completions_habitId_date` ON `habit_completions` (`habitId`, `date`)")

        // The backfill. Both dates are epoch days already (see `Converters`), and `updatedAt` is
        // epoch millis, so the columns copy across without conversion. The uid expression is
        // referenced outside the literal for the reason [MIGRATION_8_9] gives.
        for (column in listOf("lastCompletedDate", "previousCompletedDate")) {
            connection.execSQL(
                "INSERT INTO `habit_completions` (`uid`, `habitId`, `date`, `checkedAt`) " +
                    "SELECT " + UUID_V4_SQL + ", `id`, `" + column + "`, `updatedAt` FROM `habits` " +
                    "WHERE `" + column + "` IS NOT NULL",
            )
        }
    }
}

/**
 * §9.10 / §0.8 step 2b — v10 → v11. One nullable column: the fourth binding role, a `DATE`
 * property bound to `Entry.dueDate`. Nullable, so no backfill: no database had a deadline
 * binding before, and none gains one by upgrading.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `page_databases` ADD COLUMN `dueDatePropertyId` INTEGER")
    }
}

/** §9.10 / §0.6.2 — v11 → v12. `Block.mindMap`, a view preference on a block: whether its
 * subtree is drawn as a mind map. Defaults to the list every block was until now. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `blocks` ADD COLUMN `mindMap` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * §9.10 / §0.6.8 — v12 → v13. Schema on a label: the database's bound label, nullable so no
 * database gains one by upgrading, and the once-only acknowledgement, false for the same reason.
 * Membership itself needs no table — it is `page_tags`, which already exists.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `page_databases` ADD COLUMN `labelId` INTEGER")
        connection.execSQL("ALTER TABLE `page_databases` ADD COLUMN `labelConfirmed` INTEGER NOT NULL DEFAULT 0")
    }
}
