@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.calendar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.ui.calendar.CalendarOpensOnSection
import com.tendril.app.data.prefs.KeyValueStore
import com.tendril.app.googlecalendar.GoogleCalendarAuthManager
import com.tendril.app.googlecalendar.GoogleCalendarSyncEngine
import com.tendril.app.googlecalendar.SyncOutcome
import com.tendril.app.storage.GoogleCalendarPreferences
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description

/**
 * Google Calendar sync connect/disconnect (§3.2, §9.5) — lives in Calendar's own settings,
 * Android-only (Play Services), handed to the shared `CalendarScreen` as its settings slot.
 * not main Settings, matching the pattern already established for Tasks & Habits' sync-folder
 * picker. No client-ID field: see [GoogleCalendarAuthManager]'s class doc for why.
 */
@Composable
fun CalendarSettingsSheet(
    authManager: GoogleCalendarAuthManager,
    syncEngine: GoogleCalendarSyncEngine,
    preferences: GoogleCalendarPreferences,
    /** 14f·2 — the device preferences, for the *Opens on* row. */
    keyValueStore: KeyValueStore,
    onDismiss: () -> Unit,
) {
    val isConnected by preferences.isConnected.collectAsState()
    val lastSyncedAt by preferences.lastSyncedAt.collectAsState()
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }

    val resolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = { activityResult ->
            runCatching { authManager.resultFromIntent(activityResult.data) }
                .onSuccess { authManager.onAuthorized(it) }
                .onFailure { errorMessage = it.message ?: "Google Calendar connection failed" }
        },
    )

    fun runSync() {
        syncing = true
        scope.launch {
            when (val outcome = syncEngine.sync()) {
                is SyncOutcome.Success -> errorMessage = null
                is SyncOutcome.Failed -> errorMessage = outcome.message
                is SyncOutcome.NeedsResolution -> {
                    // Grant was revoked/expired since Connect — re-launch consent; the person
                    // taps Sync now again once resolutionLauncher's onResult reconnects.
                    errorMessage = "Google Calendar needs to be reconnected — tap Sync now again after granting access"
                    outcome.result.pendingIntent?.let {
                        resolutionLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
                    }
                }
            }
            syncing = false
        }
    }

    TendrilSheet(title = "Calendar settings", onDismiss = onDismiss) {
        Column {
            // 14f·2 — the view the Calendar opens on; the desktop's Settings pane has the same row.
            CalendarOpensOnSection(keyValueStore, modifier = Modifier.padding(0.dp))
            Text("Google Calendar sync", style = MaterialTheme.typography.body)
            Text(
                if (isConnected) "Connected" else "Not connected",
                style = MaterialTheme.typography.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            errorMessage?.let {
                Text(it, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            if (isConnected) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    val lastSyncedText = lastSyncedAt?.let {
                        "Last synced ${DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(java.time.ZoneId.systemDefault()).format(it)}"
                    } ?: "Never synced"
                    Text(lastSyncedText, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(enabled = !syncing, onClick = { runSync() }) { Text(if (syncing) "Syncing…" else "Sync now") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { authManager.disconnect() }) { Text("Disconnect") }
            } else {
                Button(onClick = {
                    scope.launch {
                        runCatching { authManager.authorize() }
                            .onSuccess { result ->
                                if (result.hasResolution()) {
                                    result.pendingIntent?.let {
                                        resolutionLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
                                    }
                                } else {
                                    authManager.onAuthorized(result)
                                }
                            }
                            .onFailure { errorMessage = it.message ?: "Google Calendar connection failed" }
                    }
                }) { Text("Connect Google Calendar") }
            }
        }
    }
}
