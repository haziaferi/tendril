package com.tendril.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.domain.ParsedEntry
import com.tendril.app.ui.entries.QuickAddField
import java.time.LocalDate

/**
 * 14f·2 — Quick add on a wide window: a strip under the Calendar's bar (the find bar's pattern,
 * decided 2026-09-16) opened by the bar's button, the same field and chip preview the Day view
 * has inline on the phone, on every view. No chord (Ctrl+Shift+N is *New task* since 14e —
 * `docs/critiques/tasks-calendar-mock.md` #8); Enter writes what the preview shows; Esc closes it
 * through the screen's `BackHandler`. [today] is the day the line means when it names none.
 */
@Composable
internal fun QuickAddBar(today: LocalDate, onQuickAdd: (ParsedEntry) -> Unit, onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
        // B§13.6 #7 — the field and its chips are `QuickAddField`, shared with the desktop's popup.
        QuickAddField(
            today = today,
            onQuickAdd = onQuickAdd,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            fieldModifier = Modifier.padding(vertical = 6.dp),
            previewModifier = Modifier.padding(bottom = 6.dp),
            trailing = {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Close, contentDescription = "Close (Esc)", modifier = Modifier.size(18.dp)) }
            },
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}
