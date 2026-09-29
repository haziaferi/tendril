package com.tendril.app.data.calendar

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert

/**
 * §9.12 — what Tendril and one synced calendar last agreed on, for one event: a series or single
 * event ([occurrence] empty), or one exception of a series ([occurrence] the ISO date it replaces).
 *
 * **Per device, never synced**, like `Entry.providerEventId`: it describes this phone's system
 * calendar, which no other device shares. Losing it (a reinstall) is safe — the read-back then
 * finds each event on both sides with no link and relinks it without writing (§9.12's table).
 *
 * The three names are the primary key, so `@Upsert` replaces a link rather than adding one.
 * [occurrence] is text, empty for a series, because a primary-key column cannot be null.
 */
@Entity(tableName = "calendar_links", primaryKeys = ["calendarKey", "entryUid", "occurrence"])
data class CalendarLink(
    val calendarKey: String,
    val entryUid: String,
    val occurrence: String,
    /** [com.tendril.app.domain.calendar.CalendarItemFields.fingerprint] as both sides last agreed. */
    val fingerprint: String,
)

@Dao
interface CalendarLinkDao {
    @Query("SELECT * FROM calendar_links WHERE calendarKey = :calendarKey")
    suspend fun getForCalendar(calendarKey: String): List<CalendarLink>

    @Upsert
    suspend fun upsert(link: CalendarLink)

    @Query("DELETE FROM calendar_links WHERE calendarKey = :calendarKey AND entryUid = :entryUid AND occurrence = :occurrence")
    suspend fun delete(calendarKey: String, entryUid: String, occurrence: String)

    /** A calendar un-ticked in the picker: its links mean nothing any more. */
    @Query("DELETE FROM calendar_links WHERE calendarKey = :calendarKey")
    suspend fun deleteForCalendar(calendarKey: String)
}
