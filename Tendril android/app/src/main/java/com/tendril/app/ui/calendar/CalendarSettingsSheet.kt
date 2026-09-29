@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.calendarprovider.ReadBackResult
import com.tendril.app.calendarprovider.SystemCalendar
import com.tendril.app.calendarprovider.SystemCalendarSync
import com.tendril.app.calendarprovider.SystemCalendars
import com.tendril.app.data.prefs.KeyValueStore
import com.tendril.app.storage.SystemCalendarPreferences
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Calendar's own settings on the phone, handed to the shared `CalendarScreen` as its settings slot:
 * the view it opens on, and §9.12's calendars — every calendar a sync app keeps on this phone
 * (DAVx5, the Google account), grouped by account, ticked to be read into Tendril. Replaced the
 * Google Calendar connect block on 2026-09-29, when Tendril stopped talking to Google itself.
 */
@Composable
fun CalendarSettingsSheet(
    systemCalendars: SystemCalendars,
    sync: SystemCalendarSync,
    preferences: SystemCalendarPreferences,
    hasPermission: () -> Boolean,
    /** 14f·2 — the device preferences, for the *Opens on* row. */
    keyValueStore: KeyValueStore,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ticked by preferences.ticked.collectAsState()
    val lastResult by sync.lastResult.collectAsState()
    var calendars by remember { mutableStateOf<List<SystemCalendar>?>(null) }
    var syncing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { calendars = withContext(Dispatchers.IO) { systemCalendars.calendars() } }

    TendrilSheet(title = "Calendar settings", onDismiss = onDismiss) {
        Column {
            // 14f·2 — the view the Calendar opens on; the desktop's Settings pane has the same row.
            CalendarOpensOnSection(keyValueStore, modifier = Modifier.padding(0.dp))
            Text("Calendars", style = MaterialTheme.typography.body)
            Text(
                "Ticked calendars show in Tendril, and events you put in one reach its server through the app that syncs it.",
                style = MaterialTheme.typography.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val listed = calendars
            when {
                !hasPermission() -> Text(
                    "Tendril has no access to the phone's calendars. Allow Calendar in Android's settings for Tendril.",
                    style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.error,
                )
                listed == null -> Text("Looking…", style = MaterialTheme.typography.description)
                listed.isEmpty() -> Text(
                    "No calendars on this phone yet. Add an account in DAVx5, or in Android's settings for Google.",
                    style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> listed.groupBy { it.accountType to it.accountName }.forEach { (account, inAccount) ->
                    Text("${syncAppName(account.first)} · ${account.second}", style = MaterialTheme.typography.label, modifier = Modifier.padding(top = 8.dp))
                    inAccount.forEach { calendar ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = calendar.key in ticked, onCheckedChange = { on -> scope.launch { sync.setTicked(calendar.key, on) } })
                            Text(
                                calendar.displayName + if (calendar.writable) "" else " (read-only)",
                                style = MaterialTheme.typography.body,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(lastResult?.let(::summary) ?: "Not synced yet", style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Button(enabled = !syncing && ticked.isNotEmpty(), onClick = {
                    syncing = true
                    scope.launch { runCatching { sync.syncNow() }; syncing = false }
                }) { Text(if (syncing) "Syncing…" else "Sync now") }
            }
            lastResult?.conflicts?.takeIf { it.isNotEmpty() }?.let { titles ->
                Text(
                    "Edited in both places — the calendar's version was kept: " + titles.joinToString(", "),
                    style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.error,
                )
            }
            lastResult?.duplicates?.takeIf { it.isNotEmpty() }?.let { titles ->
                Text(
                    "Already in Tendril from another ticked calendar, so not added twice: " + titles.joinToString(", ") +
                        ". The same calendar is probably ticked twice, once per sync app.",
                    style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The sync app behind an account type, as the person knows it. */
private fun syncAppName(accountType: String): String = when (accountType) {
    "bitfire.at.davdroid" -> "DAVx5"
    "com.google" -> "Google"
    android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL -> "Phone"
    else -> accountType
}

private fun summary(r: ReadBackResult): String {
    val parts = listOfNotNull(
        r.created.takeIf { it > 0 }?.let { "$it added" },
        r.updated.takeIf { it > 0 }?.let { "$it updated" },
        r.written.takeIf { it > 0 }?.let { "$it sent" },
        (r.trashed + r.deleted).takeIf { it > 0 }?.let { "$it removed" },
    )
    return if (parts.isEmpty()) "Up to date" else "Last sync: " + parts.joinToString(", ")
}
