package com.tendril.app.data

/**
 * §9.10 — the snapshot-restore fallback, wired.
 *
 * §9.10's acceptance criterion asks that "the snapshot-restore fallback is wired and manually
 * tested at least once… before it's relied on for a real one". It was neither. Nothing anywhere
 * caught a failure opening the database: `AppContainer` and the desktop `main` both assigned
 * `buildTendrilDatabase(...)` straight into a field, and Room opens lazily, so a migration that
 * threw surfaced at the first DAO call and took the app down — on every launch, with no route
 * back.
 *
 * **`fallbackToDestructiveMigration` does not cover this, and §9.10 says so itself: "it only
 * handles version *changes*".** It fires when there is no declared path from the stored version
 * to the current one. A declared migration that *throws* is the opposite case, and until v9
 * (S2) there was no declared migration in this app at all — so this became reachable on the
 * same day the first one shipped.
 *
 * **What recovery means here.** The unopenable file is *renamed aside*, never deleted, and a
 * fresh empty database is created in its place. §9.10 asks for the reset to sit behind a
 * plain-language confirm dialog, and that cannot be honoured as written: a dialog needs a
 * running app, and the app has no database to run on. Setting the file aside answers the same
 * concern the dialog was protecting — nothing the person had is destroyed without their say —
 * and it is the rule this codebase already applies to a snapshot it is about to overwrite (see
 * `SnapshotSyncOrchestrator`'s `.tendril-lost-` marker). The alternative, deleting, would make
 * the confirm dialog genuinely necessary and still leave the app unable to ask.
 *
 * The empty database is then repopulated by the ordinary sync pass, which is the "snapshot-backed
 * recovery" §9.10 chose this policy for. Note what that is and is not: the folder is read
 * *additively*, so everything this device had published comes back and **anything it had never
 * synced does not**. §9.10's own 2026-09-06 correction already records that the "wipe-and-replay
 * against a snapshot-folder export" it describes does not exist; an additive merge onto an empty
 * database reaches the same end state, and only for records the folder holds.
 */
class DatabaseOpen<T>(
    val database: T,
    /** True when [openOrRecover] had to set an unopenable file aside to get here. */
    val recovered: Boolean,
)

/**
 * Build the database, prove it actually opens, and recover once if it does not.
 *
 * Generic over the database type purely so it is testable without Room or a real SQLite file —
 * the ordering it encodes (probe, close, set aside, rebuild, re-probe) is the whole content, and
 * that is worth pinning independently of the two platform bootstraps that supply the lambdas.
 *
 * [probe] must issue a real query. Room opens lazily, so building alone proves nothing: a broken
 * migration throws at the first statement, not at `build()`. Callers pass a cheap existing read
 * rather than a new DAO member, so no test double has to grow a method to satisfy this.
 *
 * A second failure is rethrown rather than looped on. If a *freshly created* database cannot be
 * opened, the problem is not the old file and setting another one aside would destroy a working
 * copy for nothing — that is a crash worth having, and it names itself.
 */
fun <T> openOrRecover(
    build: () -> T,
    probe: (T) -> Unit,
    close: (T) -> Unit,
    setAside: () -> Unit,
): DatabaseOpen<T> {
    val first = build()
    val failure = runCatching { probe(first) }.exceptionOrNull()
    if (failure == null) return DatabaseOpen(first, recovered = false)

    // Close before moving the file: an open handle on a database being renamed is how a
    // recovery turns one unreadable file into two.
    runCatching { close(first) }
    setAside()

    val second = build()
    probe(second)
    return DatabaseOpen(second, recovered = true)
}

/**
 * The suffix an unopenable database is renamed with. Deliberately not `.bak`: this is evidence of
 * a specific failure, and the name should say which one so it is not mistaken for a backup the
 * app makes routinely (it makes none).
 */
const val UNOPENABLE_SUFFIX = ".unopenable-"
