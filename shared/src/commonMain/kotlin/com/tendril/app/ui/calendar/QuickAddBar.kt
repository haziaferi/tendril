package com.tendril.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.domain.ParsedEntry
import com.tendril.app.ui.entries.QuickAddField
import com.tendril.app.ui.nav.LocalDensityProfile
import java.time.LocalDate

/**
 * 14f·2 — Quick add on a wide window: a strip under the Calendar's bar (the find bar's pattern,
 * and since L7 its frame too,
 * decided 2026-09-16) opened by the bar's button, the same field and chip preview the Day view
 * has inline on the phone, on every view. No chord (Ctrl+Shift+N is *New task* since 14e —
 * `docs/critiques/tasks-calendar-mock.md` #8); Enter writes what the preview shows; Esc closes it
 * through the screen's `BackHandler`. [today] is the day the line means when it names none.
 */
@Composable
internal fun QuickAddBar(today: LocalDate, onQuickAdd: (ParsedEntry) -> Unit, onClose: () -> Unit) {
    // L7 (2026-09-17) — the find bar's frame: 44 dp, a 28 dp `TendrilField`, the chips inline at its
    // right (a wrapped chip row grows the strip by one row, never more); the chips' targets at 28 under
    // a pointer, as the menus'.
    val minTarget = if (LocalDensityProfile.current.pointer) 28.dp else 48.dp
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides minTarget) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = STRIP_HEIGHT).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            // B§13.6 #7 — the field and its chips are `QuickAddField`, shared with the desktop's popup.
            QuickAddField(
                today = today,
                onQuickAdd = onQuickAdd,
                modifier = Modifier.weight(1f),
                fieldHeight = STRIP_FIELD,
                inlinePreview = true,
                trailing = {
                    IconButton(onClick = onClose, modifier = Modifier.size(STRIP_FIELD)) { Icon(Icons.Filled.Close, contentDescription = "Close (Esc)", modifier = Modifier.size(18.dp)) }
                },
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

/** The find bar's 44 dp and its 28 dp field / buttons. */
private val STRIP_HEIGHT = 44.dp
private val STRIP_FIELD = 28.dp
