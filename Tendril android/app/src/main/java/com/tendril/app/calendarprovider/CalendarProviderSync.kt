package com.tendril.app.calendarprovider

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.entry.EntrySource
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.storage.CalendarProviderPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.TimeZone

private const val ACCOUNT_NAME = "Tendril"
private const val CALENDAR_NAME = "tendril_local"

// Ink theme's light-mode accent (§2.3) — a fixed, reasonable default. Not live-themed: a
// Calendars row's color is a one-time property written at creation, not a per-render style
// like everywhere else theming applies, so there's nothing to keep in sync with Settings.
private const val TENDRIL_CALENDAR_COLOR = 0xFF4A5568.toInt()

/**
 * System Calendar Provider registration (§3.2, §9.9 item 3) — a one-directional mirror of
 * Tendril's own dated Entries (both TASK and EVENT — whatever Tendril's own Calendar screen
 * already shows, §4's `WHERE start_date IS NOT NULL` rule) into `CalendarContract`, so they
 * become visible/editable to any other calendar-aware app, widget, or watch face on the
 * device. Room stays the single source of truth for Tendril's own UI at all times (§3.2) —
 * this class only ever writes, never reads Provider data back for display.
 *
 * **Corrects the spec's original "sync-adapter-backed account" wording.** Registering a real
 * `android.accounts.Account` via an `AbstractAccountAuthenticator` service is only required
 * for a calendar an actual remote server syncs (the CalDAV case). A purely local calendar
 * uses `CalendarContract.ACCOUNT_TYPE_LOCAL` instead — Android's own documented mechanism for
 * exactly this case — which only requires each write to carry `CALLER_IS_SYNCADAPTER=true` as
 * a URI query parameter (unlocking the sync-adapter-only write path Calendars/Events require)
 * against an account name that never has to actually exist in `AccountManager`. No
 * authenticator service, no sync-adapter service, no background sync process — this class
 * performs every write directly and synchronously from the app itself, matching the
 * one-directional model already decided.
 *
 * Google-sourced Entries (`source = GOOGLE_CALENDAR`, §9.5.1) are excluded — they already
 * reach the system's calendar surfaces through the device's own real Google account sync, so
 * mirroring them here would just duplicate them.
 *
 * TASK recurrence has no Provider equivalent: `RecurrenceRule.Elastic` never becomes an
 * `RRULE` (unlike `RecurrenceRule.Fixed` for EVENT, which maps directly, §4.1). A recurring
 * TASK is mirrored as a plain single event for its one currently-live occurrence instead,
 * moved forward in place each time it resolves-and-advances — the same "only the current
 * occurrence really exists" model §9.7's `AlarmScheduler` already uses for elastic recurrence.
 */
class CalendarProviderSync(
    private val context: Context,
    private val entryDao: EntryDao,
    private val preferences: CalendarProviderPreferences,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Call once permission is granted — on first grant, and again on every boot/app-open
     * reconciliation sweep alongside [com.tendril.app.notifications.reconcileAlarms]. Fully
     * idempotent: finds the existing local calendar rather than duplicating it, and every
     * per-Entry write below is itself an upsert. */
    suspend fun ensureCalendarAndBackfill() {
        if (!hasPermission()) return
        ensureCalendar() ?: return
        entryDao.getAll()
            .filter { it.deletedAt == null && it.startDate != null && it.source != EntrySource.GOOGLE_CALENDAR }
            .forEach { upsertEntry(it) }
    }

    /**
     * Null when the Calendar Provider won't give us a calendar — absent, restricted, or simply
     * refusing the write, as happens on some devices and inside work profiles. That is a
     * degraded integration, not a fatal condition, so it is reported as a value and every
     * caller below no-ops on it, exactly as they already do for a missing permission.
     *
     * Guarding here rather than at one call site is deliberate: the throw was reachable from
     * four places — the boot receiver, both of MainActivity's launch-time paths, and
     * [upsertEntry], which runs on every checkbox tap through
     * [com.tendril.app.domain.AndroidEntryScheduleCoordinator]. This is where AlarmScheduler
     * puts its own equivalent check.
     */
    private suspend fun ensureCalendar(): Long? = withContext(Dispatchers.IO) {
        preferences.calendarId.value?.takeIf { calendarStillExists(it) }
            ?: createCalendar()?.also { preferences.setCalendarId(it) }
    }

    private fun calendarStillExists(id: Long): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id)
        context.contentResolver.query(uri, arrayOf(CalendarContract.Calendars._ID), null, null, null)?.use {
            return it.moveToFirst()
        }
        return false
    }

    private fun createCalendar(): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Tendril")
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.CALENDAR_COLOR, TENDRIL_CALENDAR_COLOR)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        // A restricted provider both returns null *and* throws (SecurityException,
        // IllegalArgumentException), so catching only one of the two would still let it through.
        val result = runCatching {
            context.contentResolver.insert(asSyncAdapter(CalendarContract.Calendars.CONTENT_URI), values)
        }.getOrNull() ?: return null
        return ContentUris.parseId(result)
    }

    suspend fun upsertEntry(entry: Entry) {
        if (!hasPermission()) return
        if (entry.deletedAt != null || entry.startDate == null || entry.source == EntrySource.GOOGLE_CALENDAR) {
            // No longer (or never) Provider-eligible — remove any stale mirrored copy.
            if (entry.providerEventId != null) removeEntry(entry)
            return
        }
        // Resolved before the block rather than inside it: `ensureCalendar` does its own
        // withContext, and a plain `return` here is unambiguous where a labelled return out of
        // a lambda whose last expression isn't Unit would not be.
        val calendarId = ensureCalendar() ?: return
        withContext(Dispatchers.IO) {
            val values = entry.toCalendarValues(calendarId)
            val existingId = entry.providerEventId
            if (existingId != null && eventStillExists(existingId)) {
                context.contentResolver.update(asSyncAdapter(eventUri(existingId)), values, null, null)
            } else {
                val uri = context.contentResolver.insert(asSyncAdapter(CalendarContract.Events.CONTENT_URI), values)
                val newId = uri?.let(ContentUris::parseId)
                if (newId != null && newId != existingId) {
                    entryDao.setProviderEventId(entry.id, newId)
                }
            }
        }
    }

    suspend fun removeEntry(entry: Entry) {
        val eventId = entry.providerEventId ?: return
        if (hasPermission()) {
            withContext(Dispatchers.IO) {
                context.contentResolver.delete(asSyncAdapter(eventUri(eventId)), null, null)
            }
        }
        entryDao.setProviderEventId(entry.id, null)
    }

    private fun eventStillExists(id: Long): Boolean {
        context.contentResolver.query(eventUri(id), arrayOf(CalendarContract.Events._ID), null, null, null)?.use {
            return it.moveToFirst()
        }
        return false
    }

    private fun eventUri(eventId: Long): Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()
}

private fun Entry.toCalendarValues(calendarId: Long): ContentValues {
    val zone = ZoneId.systemDefault()
    val date = requireNotNull(startDate)
    // Bound to a local val, not used as `startTime` directly below: a nullable property
    // declared in a different module (`:shared`, §12.5) can't be smart-cast across the
    // module boundary, even after this null-check — a local val can.
    val time = startTime
    val allDay = time == null
    val rrule = (recurrenceRule as? RecurrenceRule.Fixed)?.rrule

    return ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, title)
        put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else zone.id)

        if (allDay) {
            // All-day events are UTC midnight, per CalendarContract's own documented convention.
            val startMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            put(CalendarContract.Events.DTSTART, startMillis)
            val endDateExclusive = (endDate ?: date).plusDays(1)
            if (rrule != null) {
                val days = ChronoUnit.DAYS.between(date, endDateExclusive).coerceAtLeast(1)
                put(CalendarContract.Events.RRULE, rrule)
                put(CalendarContract.Events.DURATION, "P${days}D")
            } else {
                put(CalendarContract.Events.DTEND, endDateExclusive.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            }
        } else {
            val startInstant = LocalDateTime.of(date, time).atZone(zone).toInstant()
            put(CalendarContract.Events.DTSTART, startInstant.toEpochMilli())
            val end = endDate ?: date
            val endT = endTime ?: time.plusHours(1)
            val endInstant = LocalDateTime.of(end, endT).atZone(zone).toInstant()
            if (rrule != null) {
                // Events.update/insert reject DTEND alongside RRULE — a recurring event needs
                // DURATION (this occurrence's length) instead, per occurrence, matching §4.1's
                // own "preserve the duration, not the absolute end date" rule for recurring EVENTs.
                val seconds = Duration.between(startInstant, endInstant).seconds.coerceAtLeast(60)
                put(CalendarContract.Events.RRULE, rrule)
                put(CalendarContract.Events.DURATION, "PT${seconds}S")
            } else {
                put(CalendarContract.Events.DTEND, endInstant.toEpochMilli())
            }
        }
    }
}
