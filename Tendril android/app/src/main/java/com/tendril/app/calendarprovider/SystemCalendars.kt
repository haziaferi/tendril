package com.tendril.app.calendarprovider

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events

/** A calendar in the phone's system calendar that §9.12's picker can offer. */
data class SystemCalendar(
    val id: Long,
    /** [calendarKeyOf] — stable across this device's row ids, and the value `Entry.calendarKey` holds. */
    val key: String,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    /** `CALENDAR_ACCESS_LEVEL ≥ CONTRIBUTOR` — events can be written to it. */
    val writable: Boolean,
)

/**
 * The calendar key §9.12 stores: account type, account name, and the calendar's own name within
 * the account — `NAME` where the sync app sets it (the Google account does), else `_SYNC_ID` (DAVx5,
 * whose value is its internal collection number: stable unless DAVx5 itself is reinstalled, since
 * it exposes no collection URL to other apps — read on the phone 2026-09-29).
 */
fun calendarKeyOf(accountType: String, accountName: String, name: String?, syncId: String?, rowId: Long): String =
    listOf(accountType, accountName, name ?: syncId ?: "#$rowId").joinToString("|")

/**
 * §9.12 — Tendril's reads and writes of calendars **other apps own**, as an ordinary app: never
 * `CALLER_IS_SYNCADAPTER`, because that is what leaves a write dirty for the calendar's sync app to
 * upload. Tendril's own LOCAL mirror (§9.11, [CalendarProviderSync]) is excluded from everything
 * here. Returns null or empty rather than throwing when the permission is missing or the provider
 * refuses, like the mirror does; the read-back then does nothing this pass.
 */
class SystemCalendars(private val context: Context) : CalendarStore {

    private val resolver get() = context.contentResolver

    fun calendars(): List<SystemCalendar> = query(
        Calendars.CONTENT_URI,
        arrayOf(Calendars._ID, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE, Calendars.NAME, Calendars._SYNC_ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.CALENDAR_ACCESS_LEVEL),
        null, null,
    ) { c ->
        val id = c.getLong(0)
        val accountName = c.getString(1).orEmpty()
        val accountType = c.getString(2).orEmpty()
        if (accountType == android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL && accountName == ACCOUNT_NAME) return@query null
        SystemCalendar(
            id = id,
            key = calendarKeyOf(accountType, accountName, c.getString(3), c.getString(4), id),
            displayName = c.getString(5) ?: c.getString(3) ?: accountName,
            accountName = accountName,
            accountType = accountType,
            writable = c.getInt(6) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
        )
    }

    fun idForKey(key: String): Long? = calendars().firstOrNull { it.key == key }?.id

    /** Every live row of [calendarId] — series, single events and exceptions. */
    override fun rows(calendarId: Long): List<SystemCalendarRow> = query(
        Events.CONTENT_URI,
        arrayOf(
            Events._ID, Events.UID_2445, Events.ORIGINAL_ID, Events.ORIGINAL_INSTANCE_TIME, Events.TITLE, Events.DTSTART,
            Events.DTEND, Events.DURATION, Events.ALL_DAY, Events.EVENT_TIMEZONE, Events.RRULE, Events.STATUS,
        ),
        "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0", arrayOf(calendarId.toString()),
    ) { c ->
        SystemCalendarRow(
            rowId = c.getLong(0),
            uid = c.getString(1),
            originalId = c.longOrNull(2),
            originalInstanceTime = c.longOrNull(3),
            title = c.getString(4),
            dtStart = c.getLong(5),
            dtEnd = c.longOrNull(6),
            duration = c.getString(7),
            allDay = c.getInt(8) == 1,
            timeZone = c.getString(9),
            rrule = c.getString(10),
            cancelled = !c.isNull(11) && c.getInt(11) == Events.STATUS_CANCELED,
        )
    }

    override fun insert(calendarId: Long, values: Map<String, Any?>): Long? = provider {
        resolver.insert(Events.CONTENT_URI, values.toContentValues().apply { put(Events.CALENDAR_ID, calendarId) })?.let(ContentUris::parseId)
    }

    override fun update(rowId: Long, values: Map<String, Any?>): Boolean =
        provider { resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, rowId), values.toContentValues(), null, null) > 0 } == true

    /** An exception of [seriesRowId], through `CONTENT_EXCEPTION_URI` — the one route the provider
     * accepts for a new exception. */
    override fun insertException(seriesRowId: Long, values: Map<String, Any?>): Long? = provider {
        resolver.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, seriesRowId), values.toContentValues())?.let(ContentUris::parseId)
    }

    /** Marks the row deleted; the calendar's sync app removes it from the server and the phone. */
    override fun delete(rowId: Long): Boolean =
        provider { resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, rowId), null, null) > 0 } == true

    private fun <T> query(uri: Uri, projection: Array<String>, selection: String?, args: Array<String>?, read: (Cursor) -> T?): List<T> =
        provider {
            resolver.query(uri, projection, selection, args, null)?.use { c ->
                buildList { while (c.moveToNext()) read(c)?.let(::add) }
            }
        } ?: emptyList()

    private fun <T> provider(call: () -> T): T? = runCatching(call).getOrNull()
}

private fun Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

private fun Map<String, Any?>.toContentValues() = ContentValues().also { cv ->
    for ((k, v) in this) when (v) {
        null -> cv.putNull(k)
        is String -> cv.put(k, v)
        is Long -> cv.put(k, v)
        is Int -> cv.put(k, v)
        is Boolean -> cv.put(k, if (v) 1 else 0)
        else -> error("unsupported value for $k: $v")
    }
}
