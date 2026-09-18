package com.tendril.app.ui.taskshabits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.data.habit.Habit
import com.tendril.app.ui.nav.LocalDensityProfile

/** A habit that counts something: [Habit.amountPerCheckIn] set (§0.10 item 3). */
val Habit.counts: Boolean get() = amountPerCheckIn != null

/**
 * §0.10 item 3 (2026-09-19) — the row's check for a habit. A plain habit keeps its checkbox
 * (one check a day; unchecking undoes). A **counting** habit wears a `+` disc in the checkbox's
 * own slot — 28 dp under a pointer, 40 in a 48 dp target on the phone, so titles stay aligned
 * (`docs/critiques/measurable-habits-mock.md` #1) — and every tap adds one amount; the disc
 * fills once anything was logged today. Undo is the sheet's chip (*Undo the last cup*), not a
 * second tap: a tap is only ever *more*.
 */
@Composable
fun HabitCheck(habit: Habit, doneToday: Boolean, onCheckIn: () -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    if (!habit.counts) {
        Checkbox(checked = doneToday, onCheckedChange = { if (it) onCheckIn() else onUndo() }, modifier = modifier)
        return
    }
    val pointer = LocalDensityProfile.current.pointer
    val disc = if (pointer) 20.dp else 24.dp
    IconButton(onClick = onCheckIn, modifier = modifier.size(if (pointer) 28.dp else 48.dp)) {
        Box(
            modifier = Modifier
                .size(disc)
                .background(if (doneToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, CircleShape)
                .border(1.5.dp, if (doneToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Add one more",
                tint = if (doneToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(disc - 6.dp),
            )
        }
    }
}
