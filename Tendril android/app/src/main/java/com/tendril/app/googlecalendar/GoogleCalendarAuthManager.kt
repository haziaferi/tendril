package com.tendril.app.googlecalendar

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.tendril.app.storage.GoogleCalendarPreferences
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Optional Google Calendar sync (§3.2, §9.5) — a separate integration from Calendar Provider
 * registration; the two stay independent by design (§3.2).
 *
 * Deliberately does **not** use `AuthorizationRequest.requestOfflineAccess()`. That path issues
 * a server auth code meant to be exchanged for a refresh token by a backend holding a Web-type
 * client's secret — this app has no backend and was never going to have one (§9.1, local-first).
 * Instead this requests the Calendar scope against the on-device **Android-type** OAuth client,
 * which Play Services resolves automatically from the app's package name + signing-certificate
 * SHA-1 — no client ID needs to be entered anywhere in the app. That corrects §3.2's original
 * plan for a user-entered "Google OAuth client ID" settings field; nothing else in that section
 * depended on the field itself existing, only on Calendar's settings being where this control
 * lives, which is unchanged. [authorize] is re-called for a fresh short-lived access token
 * before each sync pass — once the scope is granted once, Play Services returns it silently
 * (`hasResolution() == false`) with no further UI, functioning as a standing authorization
 * without this app ever holding a persistent refresh token on disk.
 *
 * One-time setup the user still does themselves in Google Cloud Console (§9.5, unchanged by the
 * above): enable the Calendar API, set the OAuth consent screen to **Production** (avoids the
 * 7-day testing-mode expiry trap), and register an **Android**-type OAuth client with this
 * app's package name and signing-certificate SHA-1 fingerprint.
 *
 * Disconnecting only clears the local [GoogleCalendarPreferences] flag and stops Tendril from
 * syncing — it does not revoke the grant on Google's side. `AuthorizationClient.revokeAccess()`
 * takes a `RevokeAccessRequest` whose exact shape (an `Account`, obtained from where?) isn't
 * pinned down with confidence yet; wiring a real in-app revoke is a follow-up once that's
 * verified against the live SDK rather than guessed. Until then, fully revoking access is a
 * manual step in the person's Google Account settings (Security → Third-party apps & services).
 */
class GoogleCalendarAuthManager(
    context: Context,
    private val preferences: GoogleCalendarPreferences,
) {
    private val client = Identity.getAuthorizationClient(context.applicationContext)
    private val requestedScopes = listOf(Scope(CALENDAR_EVENTS_SCOPE))

    private fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(requestedScopes).build()

    /**
     * Requests (or silently re-confirms) the Calendar scope. When the result's
     * `hasResolution()` is true, launch `result.pendingIntent!!.intentSender` via an
     * `ActivityResultLauncher<IntentSenderRequest>` and feed the returned Intent through
     * [resultFromIntent]; otherwise `result.accessToken` is already usable.
     */
    suspend fun authorize(): AuthorizationResult = suspendCancellableCoroutine { continuation ->
        client.authorize(request())
            .addOnSuccessListener { result -> continuation.resume(result) }
            .addOnFailureListener { error -> continuation.resumeWithException(error) }
    }

    /** Call with the Intent from the ActivityResultLauncher's callback after a resolution. */
    fun resultFromIntent(data: Intent?): AuthorizationResult =
        client.getAuthorizationResultFromIntent(data)

    /** Marks the connection established and returns the short-lived access token for immediate use. */
    fun onAuthorized(result: AuthorizationResult): String? {
        preferences.setConnected(true)
        return result.accessToken
    }

    /** See the class doc — local-only for now, doesn't revoke Google's own grant record. */
    fun disconnect() {
        preferences.setConnected(false)
    }

    companion object {
        // Events-only scope — narrower than full "…/auth/calendar", matching the "obvious
        // 80%-subset" bias already applied elsewhere (§5.6): Tendril only ever needs to
        // create/update/delete events in an existing calendar, never manage calendars themselves.
        const val CALENDAR_EVENTS_SCOPE = "https://www.googleapis.com/auth/calendar.events"
    }
}
