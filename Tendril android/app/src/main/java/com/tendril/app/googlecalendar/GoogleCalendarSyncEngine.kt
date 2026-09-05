package com.tendril.app.googlecalendar

import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntrySource
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.storage.GoogleCalendarPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
private const val CALENDAR_ID = "primary" // v1 scope limit (§9.5) — no calendar picker
private const val EVENTS_BASE_URL = "https://www.googleapis.com/calendar/v3/calendars/$CALENDAR_ID/events"

class GoogleCalendarApiException(val code: Int, body: String) : Exception("Google Calendar API error $code: $body")

sealed class SyncOutcome {
    data object Success : SyncOutcome()
    /** Interactive consent is needed — launch `result.pendingIntent!!.intentSender` the same
     * way [GoogleCalendarAuthManager.authorize]'s own doc describes, then call [GoogleCalendarSyncEngine.sync] again. */
    data class NeedsResolution(val result: AuthorizationResult) : SyncOutcome()
    data class Failed(val message: String) : SyncOutcome()
}

@Serializable
private data class GoogleEventDateTime(val date: String? = null, val dateTime: String? = null, val timeZone: String? = null)

@Serializable
private data class GoogleEvent(
    val id: String? = null,
    val status: String? = null,
    val summary: String? = null,
    val start: GoogleEventDateTime? = null,
    val end: GoogleEventDateTime? = null,
    val recurrence: List<String>? = null,
    val updated: String? = null,
)

@Serializable
private data class GoogleEventListResponse(val items: List<GoogleEvent> = emptyList(), val nextPageToken: String? = null)

/**
 * Two-way sync between Tendril's local `kind = EVENT` entries and the user's Google Calendar
 * (§3.2, §9.5) — deliberately EVENT-only, never TASK: Google's Event resource has no
 * elastic/streak concept, and §4.1 designed `RecurrenceRule.Fixed`'s RRULE format to match
 * this exact API, which only ever applies to EVENT (§4.1 R2).
 *
 * **v1 scope limits, consistent with this spec's running "obvious 80%-subset" bias**
 * (§5.6, §5.2.2):
 * - Syncs against `"primary"` only — no calendar picker.
 * - Incremental via `updatedMin` + `showDeleted=true`, not the syncToken mechanism — simpler,
 *   and avoids syncToken's own 410-invalidation edge case; sufficient for a personal single
 *   user. `singleEvents=false` keeps a recurring series as one master event with its RRULE,
 *   matching Tendril's own one-row-per-series model rather than expanding every occurrence.
 * - Push runs before pull each sync pass (needed so pull's dedup-by-`googleEventId` doesn't
 *   re-discover and double-insert an event this same pass just created). The accepted
 *   trade-off: a genuine concurrent edit to the same event, made on both this device and
 *   directly in Google Calendar within one sync interval, is resolved by push unconditionally
 *   overwriting Google's copy rather than comparing timestamps first — the same category of
 *   accepted whole-record LWW simplification §9.4 already makes for Pages, not a new risk
 *   class introduced here.
 * - A Trashed (soft-deleted) local EVENT that still has a live [Entry.googleEventId] is
 *   deleted from Google on the next push and the link cleared — restoring it from Trash later
 *   re-creates a fresh remote event rather than writing to a since-deleted id. A
 *   cancelled/deleted remote event moves its local counterpart to Trash, never a hard delete —
 *   consistent with Trash's reversible philosophy (§5.5.1); this app has no reason to trust a
 *   remote delete enough to destroy local data outright.
 *
 * Conflict resolution otherwise mirrors §9.4's own per-record last-write-wins rule exactly,
 * comparing Google's `updated` timestamp against [Entry.updatedAt] — one consistent merge
 * principle across both sync mechanisms this app has.
 */
class GoogleCalendarSyncEngine(
    private val entryDao: EntryDao,
    private val authManager: GoogleCalendarAuthManager,
    private val preferences: GoogleCalendarPreferences,
    private val entryScheduleCoordinator: EntryScheduleCoordinator,
) {
    suspend fun sync(): SyncOutcome = try {
        val authResult = authManager.authorize()
        if (authResult.hasResolution()) {
            SyncOutcome.NeedsResolution(authResult)
        } else {
            val accessToken = authResult.accessToken
                ?: return SyncOutcome.Failed("Google Calendar authorization returned no access token")
            val since = preferences.lastSyncedAt.value
            push(accessToken, since)
            pull(accessToken, since)
            preferences.setLastSyncedAt(Instant.now())
            SyncOutcome.Success
        }
    } catch (e: GoogleCalendarApiException) {
        SyncOutcome.Failed(e.message ?: "Google Calendar sync failed")
    } catch (e: java.io.IOException) {
        SyncOutcome.Failed(e.message ?: "Google Calendar sync failed — check network connectivity")
    }

    private suspend fun push(accessToken: String, since: Instant?) {
        val candidates = entryDao.getAll()
            .filter { it.kind == EntryKind.EVENT && (since == null || it.updatedAt.isAfter(since)) }
        for (entry in candidates) {
            if (entry.deletedAt != null) {
                val eventId = entry.googleEventId ?: continue
                runCatching { deleteEvent(accessToken, eventId) }
                entryDao.setGoogleEventId(entry.id, null)
                continue
            }
            val body = json.encodeToString(entry.toGoogleEvent())
            val existingId = entry.googleEventId
            val remote = if (existingId == null) insertEvent(accessToken, body) else updateEvent(accessToken, existingId, body)
            if (remote.id != null && remote.id != existingId) {
                entryDao.setGoogleEventId(entry.id, remote.id)
            }
        }
    }

    private suspend fun pull(accessToken: String, since: Instant?) {
        for (event in listEvents(accessToken, updatedMin = since)) {
            val eventId = event.id ?: continue
            val local = entryDao.getByGoogleEventId(eventId)
            if (event.status == "cancelled") {
                if (local != null && local.deletedAt == null) {
                    entryDao.softDelete(local.id, Instant.now())
                    // §9.7 — a soft-delete changes what should be scheduled, so it goes
                    // through the coordinator like every other write path. This was the one
                    // that didn't: an Entry cancelled remotely kept its alarms and its
                    // Calendar Provider mirror, so it stayed in the system calendar and could
                    // still fire a reminder for an event that no longer exists.
                    //
                    // Re-read rather than passing `local`, which is the pre-delete snapshot —
                    // the coordinator writes back to this row to clear its mirror id, and a
                    // stale copy would restore `deletedAt = null` and undo the delete.
                    entryDao.getById(local.id)?.let { entryScheduleCoordinator.onEntryRemoved(it) }
                }
                continue
            }
            val remoteUpdatedAt = event.updated?.let(Instant::parse) ?: Instant.now()
            when {
                local == null -> {
                    val newId = entryDao.insert(event.toEntry(eventId))
                    // §9.7 — a pulled EVENT is a schedulable row like any other, so it needs
                    // its Calendar Provider mirror created rather than existing only in Room.
                    // Re-read: the row carries the id Room assigned, which the inserted copy
                    // doesn't.
                    entryDao.getById(newId)?.let { entryScheduleCoordinator.onEntryChanged(it) }
                }
                remoteUpdatedAt.isAfter(local.updatedAt) -> {
                    // providerEventId is per-device only (§3.2) — preserved, never adopted
                    // from Google's own event data (which has no concept of it).
                    // `source` and `createdAt` stay local. Push runs before pull with the
                    // same `since` (§9.5.1), so an event this device *just pushed* comes back
                    // in the same pass with `updated > since` and wins this comparison. Taking
                    // Google's values there flipped every manually-created EVENT to
                    // `source = GOOGLE_CALENDAR` after one sync — which §9.11 then excludes
                    // from the system Calendar Provider mirror, on the reasoning that
                    // Google-sourced events already reach the OS through the device's own
                    // account. A locally-created event doesn't, so it simply vanished from
                    // every system calendar surface. `createdAt` was overwritten with
                    // `Instant.now()` the same way.
                    val updated = event.toEntry(eventId).copy(
                        id = local.id, uid = local.uid, sourceRowId = local.sourceRowId,
                        providerEventId = local.providerEventId,
                        source = local.source, createdAt = local.createdAt,
                    )
                    entryDao.update(updated)
                    // A remote edit can move start_date — exactly §9.7's "every write path
                    // that can move an Entry's start_date must cancel old alarms and schedule
                    // fresh ones". Without this the Entry keeps its alarms, and its Provider
                    // mirror, for the date it no longer has.
                    entryScheduleCoordinator.onEntryChanged(updated)
                }
                // else: local is newer or equal — keep local, it pushes on the next pass.
            }
        }
    }

    private suspend fun listEvents(accessToken: String, updatedMin: Instant?): List<GoogleEvent> {
        val events = mutableListOf<GoogleEvent>()
        var pageToken: String? = null
        do {
            val query = buildString {
                append("showDeleted=true&singleEvents=false")
                updatedMin?.let { append("&updatedMin=").append(URLEncoder.encode(it.toString(), "UTF-8")) }
                pageToken?.let { append("&pageToken=").append(URLEncoder.encode(it, "UTF-8")) }
            }
            val response = json.decodeFromString<GoogleEventListResponse>(
                request("GET", "$EVENTS_BASE_URL?$query", accessToken)
            )
            events += response.items
            pageToken = response.nextPageToken
        } while (pageToken != null)
        return events
    }

    private suspend fun insertEvent(accessToken: String, body: String): GoogleEvent =
        json.decodeFromString(request("POST", EVENTS_BASE_URL, accessToken, body))

    /**
     * `events.patch`, not `events.update`. `PUT` is a full replace, and [toGoogleEvent] only
     * ever sends the four fields Tendril models (summary/start/end/recurrence) — so every push
     * of a locally-edited event silently cleared its `description`, `location`, `attendees`,
     * `reminders`, `colorId` and `conferenceData` on Google's copy. §9.5.1 accepts push
     * overwriting Google "unconditionally" only as the resolution of a genuine *concurrent
     * edit*; destroying fields this app never modelled, on every push, is not that.
     */
    private suspend fun updateEvent(accessToken: String, eventId: String, body: String): GoogleEvent =
        json.decodeFromString(request("PATCH", eventUrl(eventId), accessToken, body))

    private suspend fun deleteEvent(accessToken: String, eventId: String) {
        request("DELETE", eventUrl(eventId), accessToken)
    }

    /** Encoded for the same reason `updatedMin`/`pageToken` are on the list call above:
     * `Entry.googleEventId` isn't necessarily an id this device minted. It travels in the
     * §9.4 snapshot record, so it arrives from a synced folder or an imported archive, and
     * unencoded `../` segments in it would re-target the request at another Calendar
     * resource under the user's own OAuth token. */
    private fun eventUrl(eventId: String): String =
        "$EVENTS_BASE_URL/${URLEncoder.encode(eventId, "UTF-8")}"

    private suspend fun request(method: String, url: String, accessToken: String, body: String? = null): String =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_NO_CONTENT) return@withContext ""
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code !in 200..299) throw GoogleCalendarApiException(code, text)
                text
            } finally {
                connection.disconnect()
            }
        }
}

private fun Entry.toGoogleEvent(): GoogleEvent {
    val zone = ZoneId.systemDefault()
    return GoogleEvent(
        summary = title,
        start = toGoogleDateTime(startDate, startTime, zone),
        end = toGoogleDateTime(endDate ?: startDate, endTime ?: startTime, zone),
        recurrence = (recurrenceRule as? RecurrenceRule.Fixed)?.let { listOf("RRULE:${it.rrule}") },
    )
}

private fun toGoogleDateTime(date: LocalDate?, time: LocalTime?, zone: ZoneId): GoogleEventDateTime? {
    val d = date ?: return null
    return if (time != null) {
        GoogleEventDateTime(
            dateTime = LocalDateTime.of(d, time).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            timeZone = zone.id,
        )
    } else {
        GoogleEventDateTime(date = d.toString())
    }
}

private fun GoogleEvent.toEntry(googleEventId: String): Entry {
    val (startDate, startTime) = start.toLocalDateAndTime()
    val (endDate, endTime) = end.toLocalDateAndTime()
    val rrule = recurrence?.firstOrNull { it.startsWith("RRULE:") }?.removePrefix("RRULE:")
    val now = Instant.now()
    return Entry(
        title = summary ?: "(untitled)",
        kind = EntryKind.EVENT,
        startDate = startDate,
        startTime = startTime,
        endDate = endDate,
        endTime = endTime,
        recurrenceRule = rrule?.let { RecurrenceRule.Fixed(it) },
        source = EntrySource.GOOGLE_CALENDAR,
        googleEventId = googleEventId,
        createdAt = now,
        updatedAt = updated?.let(Instant::parse) ?: now,
    )
}

private fun GoogleEventDateTime?.toLocalDateAndTime(): Pair<LocalDate?, LocalTime?> {
    if (this == null) return null to null
    dateTime?.let {
        val odt = OffsetDateTime.parse(it)
        return odt.toLocalDate() to odt.toLocalTime()
    }
    date?.let { return LocalDate.parse(it) to null }
    return null to null
}
