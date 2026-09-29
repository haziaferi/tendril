package com.tendril.app.domain.calendar

import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime

/**
 * One event of a synced calendar (§9.12): a series or a single event ([occurrence] null), or one
 * exception of a series, named by the occurrence it replaces. Keyed on the event's iCalendar UID —
 * `UID_2445` on the phone, `Entry.uid` in Tendril — never on the provider's `_id`, which a sync app
 * re-creates (walked 2026-09-29: an exception's row went from 752 to 753 after its first upload).
 */
data class CalendarItemKey(val uid: String, val occurrence: LocalDate?)

/** The fields §9.12 maps between Tendril and the system calendar — and only these. */
data class CalendarItemFields(
    val title: String,
    val startDate: LocalDate?,
    val startTime: LocalTime?,
    val endDate: LocalDate?,
    val endTime: LocalTime?,
    val rrule: String?,
    val isSkip: Boolean,
) {
    /**
     * What both sides last agreed on, as a hash of every mapped field. The system calendar records
     * no change time an app can rely on (§9.12, walked 2026-09-29), so a change is "differs from
     * the fingerprint", on either side.
     */
    fun fingerprint(): String {
        val canonical = listOf(title, startDate, startTime, endDate, endTime, rrule, isSkip).joinToString("\u001F") { it?.toString() ?: "\u0000" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/** One decision of a read-back pass. The caller carries it out; this file touches nothing. */
sealed interface ReconcileStep {
    val key: CalendarItemKey

    /** Store [fields] in Tendril — creating the entry if there is none. [conflict] when Tendril's
     * own change is overridden, so the caller keeps it in the entry's history (§9.12). */
    data class TakeFromProvider(override val key: CalendarItemKey, val fields: CalendarItemFields, val conflict: Boolean) : ReconcileStep

    /** Write [fields] to the system calendar — inserting the event if it has none. */
    data class WriteToProvider(override val key: CalendarItemKey, val fields: CalendarItemFields) : ReconcileStep

    /** Gone from the calendar: move the entry to Trash (§5.5.1), never a hard delete. */
    data class TrashInTendril(override val key: CalendarItemKey) : ReconcileStep

    /** Gone from Tendril: delete the calendar's row, which its sync app turns into a server delete. */
    data class DeleteFromProvider(override val key: CalendarItemKey) : ReconcileStep

    /** Both sides agree; store [fingerprint] as the new agreed state. */
    data class Relink(override val key: CalendarItemKey, val fingerprint: String) : ReconcileStep

    /** Gone from both sides: drop the link. */
    data class Forget(override val key: CalendarItemKey) : ReconcileStep
}

/**
 * §9.12's table. [tendril] holds Tendril's live items in one calendar, [provider] the calendar's,
 * [links] the fingerprints last agreed. Returns a step for every key that needs one; a key both
 * sides agree on and the link already records needs none.
 *
 * The calendar wins a true conflict — it is what every other client of that calendar sees, and
 * there is no timestamp to compare (§9.4's last-write-wins has nothing to go on here). The same
 * holds for an item Tendril deleted while the calendar changed it: the edit is newer information
 * than the delete, and Tendril's delete was a move to Trash, so nothing is lost either way.
 */
fun planCalendarReconcile(
    tendril: Map<CalendarItemKey, CalendarItemFields>,
    provider: Map<CalendarItemKey, CalendarItemFields>,
    links: Map<CalendarItemKey, String>,
): List<ReconcileStep> = (tendril.keys + provider.keys + links.keys).mapNotNull { key ->
    val r = tendril[key]
    val p = provider[key]
    val l = links[key]
    val rChanged = r != null && r.fingerprint() != l
    val pChanged = p != null && p.fingerprint() != l
    when {
        r == null && p == null -> ReconcileStep.Forget(key)
        r != null && p != null && r.fingerprint() == p.fingerprint() ->
            if (l == r.fingerprint()) null else ReconcileStep.Relink(key, r.fingerprint())
        r != null && p != null -> when {
            l == null -> ReconcileStep.TakeFromProvider(key, p, conflict = true)
            rChanged && pChanged -> ReconcileStep.TakeFromProvider(key, p, conflict = true)
            pChanged -> ReconcileStep.TakeFromProvider(key, p, conflict = false)
            else -> ReconcileStep.WriteToProvider(key, r)
        }
        // Only in the calendar: new there, or deleted in Tendril.
        p != null -> when {
            l == null -> ReconcileStep.TakeFromProvider(key, p, conflict = false)
            pChanged -> ReconcileStep.TakeFromProvider(key, p, conflict = true)
            else -> ReconcileStep.DeleteFromProvider(key)
        }
        // Only in Tendril: new here, or deleted in the calendar.
        else -> if (l == null) ReconcileStep.WriteToProvider(key, r!!) else ReconcileStep.TrashInTendril(key)
    }
}
