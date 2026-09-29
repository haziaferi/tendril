# Calendar sync: Tendril's own Google engine, or the phone's system calendar?

**Status: decided 2026-09-29 — route B, no Google engine, no fallback; specified as §9.12. Built and walked against DAVx5 the same day (#148); what follows is the reasoning as it stood before any code, and "nothing here is built" below is that moment's truth.** The person declined to
set up a Google Cloud project, which route A requires. Still open: which calendars Tendril reads,
and whether tasks stay mirrored (decision 2 below); B's read-back is designed in the spec before
any code. Written 2026-09-29, after a walk on the phone showed what
the current route costs to switch on. Nothing here is built. Every claim about the tree names the
file it was read from; every claim about Android or Google that has not been run on the phone is
marked *unverified* with the step that would settle it.

## What prompted this

Connecting Google Calendar on the debug build failed four times with
`UNREGISTERED_ON_API_CONSOLE` (logcat, 12:03–12:07). §9.5 is explicit that this is expected until
the person registers an **Android-type OAuth client** — package plus signing-key SHA-1 — in their
own Google Cloud project, enables the Calendar API, and sets the consent screen to **Production**
(Testing mode expires the grant every 7 days). It is about ten minutes, once per signing key, and
it is the only way to prove the engine: `GooglePushPlanTest` pins the EXDATE shape sent to Google,
but whether Google accepts it is unwalked (audit 5.4).

The question raised on the walk: would it be simpler to let a sync app — the phone's own Google
account, or DAVx5 for any CalDAV server — carry events to the server, and have Tendril talk only to
the phone's system calendar?

## The two routes

**A. Keep the direct engine (today).** `GoogleCalendarSyncEngine.kt` (375 lines) and
`GoogleCalendarAuthManager.kt` (86): Tendril authorizes through Play Services, pushes and pulls
`primary` over HTTPS, EVENT-only, on a manual *Sync now* (`CalendarSettingsSheet.kt`). Separately,
`CalendarProviderSync.kt` mirrors every dated entry **one way** into a LOCAL calendar Tendril owns,
and excludes `source = GOOGLE_CALENDAR` rows because those reach the system calendar through the
phone's Google account already (§9.11).

**B. Sync through the system calendar.** Tendril drops its Google engine. Events go into a
calendar the person picks from the system calendar — one owned by the phone's Google account, by
DAVx5, or by any other sync app — and that app carries them to the server. Changes made elsewhere
come back the same way and Tendril reads them from the system calendar.

## What B gains

- **No developer setup.** No Cloud project, OAuth client, SHA-1 per signing key, consent-screen mode
  or 7-day trap; the account is added once in Android settings or DAVx5, as for any calendar app.
- **Any server.** Google through the phone's own account; Nextcloud, Fastmail, iCloud, Radicale and
  others through DAVx5 (paid on Play, free on F-Droid). Fits §0's local-first, sideloaded app better
  than a single hard-wired provider.
- **Less code to keep alive.** The HTTP engine, token handling and Google's API quirks go; the sync
  app owns retries, offline queueing and server-side conflicts.
- **Native exceptions.** The system calendar models a moved or cancelled occurrence as an exception
  event of its own (`Events.CONTENT_EXCEPTION_URI`), so audit 5.4's EXDATE workaround for Google
  would not be needed on this path. *Unverified:* that the Google adapter and DAVx5 both carry
  app-created exceptions to their servers. Settled by: one exception written into a throwaway
  synced calendar, then read on the server.
- **Permissions already held.** `READ_CALENDAR` and `WRITE_CALENDAR` are in the manifest
  (`AndroidManifest.xml:36–37`).

## What B costs

1. **A read-back path — the real work.** Tendril only *writes* to the system calendar today, and
   §9.8 R3 decided that on purpose: "Room stays authoritative for Tendril's UI always; Provider
   writes are one-directional." Two-way sync through the system calendar means reading changes back
   into Room, so R3 is amended, not just extended. What makes the read-back hard:
   - **No change timestamp an ordinary app can trust.** Events carry `DIRTY` and sync-adapter
     columns, but nothing that says "changed on the server at time T". Detecting a remote change
     means keeping a per-event fingerprint from the last read and diffing the whole calendar each
     pass. *Unverified* that no usable column exists — settled by dumping `Events` for a
     Google-synced calendar before and after an edit on the web.
   - **Conflicts without last-write-wins.** §9.4 and §9.5.1 resolve on `updatedAt`. With no server
     timestamp, a conflict is resolved by a three-way comparison — Room's row, the system calendar's
     row, and the fingerprint both last agreed on. That is a different rule from the rest of Tendril
     and needs its own spec section.
   - **Knowing which rows are Tendril's.** Tendril must recognise its own events coming back, or
     every pass duplicates them. The candidate is writing Tendril's `uid` into `Events.UID_2445`.
     *Unverified* that a non-sync-adapter write to that column survives Google's and DAVx5's round
     trips. Settled by: write, sync, clear local data, sync down, read the column.
   - **Deletes.** A deleted event on a synced calendar lingers with `DELETED = 1` until the sync app
     runs, then disappears. Read-back must treat "absent" as deleted — the §5.5.1 Trash rule
     applies (a remote delete moves to Trash, never destroys), as §9.5.1 already does.
2. **A calendar picker.** §9.5.1 syncs `primary` only, with no picker, as a v1 limit. B needs one:
   which system calendar holds Tendril's events. A picker is small; which calendars to *read*
   (just that one, or several) is a decision.
3. **§9.11's split changes.** Events would live in the chosen synced calendar, not Tendril's LOCAL
   one. Tasks have no server equivalent — CalDAV's VTODO is not surfaced by the system calendar —
   so either tasks stay in the LOCAL calendar as today, or they stop being mirrored. Tendril would
   then own two kinds of calendar row on the phone; the mirror's "delete every Tendril LOCAL
   calendar and make one" repair (audit 5.11) must not reach the synced one.
4. **Recurrence read-back.** A server's series comes back as RRULE plus exceptions. §4.1.1's
   expander parses a bounded RRULE subset chosen when the Google pull was its only source; DAVx5
   servers will send rules outside that subset. Each unsupported rule is the visible divergence
   §4.1.1 already describes, now reachable from more places.
5. **Timing is the sync app's.** Changes reach the server when Android or DAVx5 syncs, not when
   *Sync now* is tapped. Usually minutes; the person cannot force it from Tendril.
6. **The desktop is unchanged.** It has no Google engine now (Windows spec) and would not gain one;
   events still reach it through the Syncthing folder (§9.4). B does not move this either way.

## Size, roughly

| | A — finish the direct engine | B — system calendar |
|---|---|---|
| Person's setup | Cloud project, API, Android client, Production consent; again for the release key | Add an account in Android or DAVx5 |
| Code | Exists. Owed: the walk; in-app revoke (§9.5, not implemented) | New: picker, read-back with fingerprints, three-way conflicts, UID round trip, delete detection. Removed: the engine (~460 lines) |
| Spec | §9.5.1 amendments only | New section; §9.5/§9.5.1 retired; §9.8 R3 and §9.11 amended; §4.1.1's subset revisited |
| Proof | One walk against a real calendar | Three unverified platform questions above, each a walk, before design is final |
| Servers | Google | Anything the phone can sync |

## Recommendation

**Settle A's walk first, then decide.** Registering the client is ten minutes and proves or refutes
code that already exists; nothing about B needs A to stay or go. **For the long term B fits better**
— no developer setup to keep alive, any server, and a sync app doing work Tendril does by hand — but
only if the read-back is designed as its own section before any code, and only after the three
*unverified* questions are answered on the phone with a throwaway calendar. If those answers are
bad (no stable identity column, exceptions not carried), B shrinks to "write-only into a synced
calendar", which is simpler still but gives up seeing changes made elsewhere.

**Found the same day, on the phone.** The Google account's calendars are already in the system
calendar — three visible and syncing, maintained by the Google Calendar app's own sync — so B
needs neither DAVx5 nor a Cloud project for Google; DAVx5 matters only for another server. Fossify
Calendar's "CalDAV sync" list showed no Google account because Fossify had been denied
`READ_CALENDAR`/`WRITE_CALENDAR` (`dumpsys package`): that list reads the same system calendar B
would use.

**Probed the same day, with DAVx5 and a Google CalDAV calendar** (details in §9.12): all three
*unverified* questions above came back favourable — `UID_2445` survives upload and a server-side
edit; an app-written exception reaches the server (8 Oct empty, 9 Oct moved, on
calendar.google.com); no generic change column exists, so fingerprints it is. Not yet repeated
with the phone's own Google-account sync.

**Decisions this needs from the person** (all answered 2026-09-29: B, the engine removed; ticked
calendars read; per-event calendar incl. phone-only, last choice remembered; tasks stay in the
Tendril calendar):
1. A or B as the long-term route.
2. If B: which calendars Tendril reads from, and whether tasks stay mirrored.
3. If B: whether A is removed or kept as a fallback (keeping both doubles the sync surface).
