package com.tendril.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.domain.ParsedEntry
import com.tendril.app.domain.QuickAddParser
import com.tendril.app.domain.TokenKind
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.calendar_quick_add_hint
import com.tendril.app.ui.entries.QuickAddPreview
import org.jetbrains.compose.resources.stringResource
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
    var text by remember { mutableStateOf("") }
    var ignored by remember { mutableStateOf(emptySet<TokenKind>()) }
    var kindOverride by remember { mutableStateOf<EntryKind?>(null) }
    val parsed = remember(text, ignored, kindOverride) { QuickAddParser.parse(text, today, EntryKind.EVENT, ignored, kindOverride) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; ignored = emptySet(); kindOverride = null },
                modifier = Modifier.weight(1f).padding(vertical = 6.dp).focusRequester(focus),
                placeholder = { Text(stringResource(Res.string.calendar_quick_add_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (text.isNotBlank()) { onQuickAdd(parsed); text = ""; ignored = emptySet(); kindOverride = null }
                }),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Close, contentDescription = "Close (Esc)", modifier = Modifier.size(18.dp)) }
        }
        if (text.isNotBlank()) {
            QuickAddPreview(
                parsed = parsed,
                onFlipKind = { kindOverride = if (parsed.kind == EntryKind.TASK) EntryKind.EVENT else EntryKind.TASK },
                onDrop = { ignored = ignored + it },
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}
