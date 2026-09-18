package com.tendril.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.data.prefs.KeyValueStore
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.heading

/**
 * 14f·2 — the *Opens on* choice, one chips row for the desktop's Settings pane and the phone's
 * Calendar settings sheet. Nothing chosen (the fourth state) means the width's default:
 * [defaultCalendarView]. Writes `calendar_default_view`; read once when the tab opens.
 */
@Composable
fun CalendarOpensOnSection(store: KeyValueStore, modifier: Modifier = Modifier) {
    val stored by store.observe(CALENDAR_DEFAULT_VIEW_KEY).collectAsState(initial = store.get(CALENDAR_DEFAULT_VIEW_KEY))
    val current = CalendarViewKey.fromKey(stored)
    Column(modifier = modifier.padding(16.dp)) {
        Text("Opens on", style = MaterialTheme.typography.heading)
        Text(
            "The view the Calendar shows first. Unset, a wide window opens on the week and a phone on the day.",
            style = MaterialTheme.typography.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        // P7 (item 23's Lows) — four chips do not fit a 288 dp sheet in one row; a `FlowRow` wraps a chip whole.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CalendarViewKey.entries.forEach { key ->
                FilterChip(
                    selected = key == current,
                    onClick = { store.put(CALENDAR_DEFAULT_VIEW_KEY, if (key == current) null else key.key) },
                    label = { Text(key.label) },
                )
            }
        }
    }
}
