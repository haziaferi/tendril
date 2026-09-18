package com.tendril.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.theme.heading

/**
 * T3 (PR B, 2026-09-18 — `desktop-type-full.md` #3): Material's `DatePicker` draws its headline
 * (*17 set 2026*) at a 32 sp display size — the one 32 in the app — over a supporting *Select
 * date* title. This is the picker every `DatePickerDialog` shows: the title at `heading`, no
 * headline (the chosen day is in the grid, and the dialog's OK is the confirm). Nine sites.
 */
@Composable
fun TendrilDatePicker(state: DatePickerState) {
    DatePicker(
        state = state,
        // The heading rides in the headline slot: with a title alone Material keeps its 120 dp
        // header and leaves the headline's room blank (seen on the walk); with a headline alone
        // the header is the one line.
        title = null,
        headline = { Text("Pick a day", style = MaterialTheme.typography.heading, modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)) },
        showModeToggle = false,
    )
}
