package com.tendril.app.ui.entries

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.TextFieldValue
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.domain.ParsedEntry
import com.tendril.app.domain.QuickAddParser
import com.tendril.app.domain.TokenKind
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.calendar_quick_add_hint
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate
import com.tendril.app.ui.theme.body
import androidx.compose.material3.MaterialTheme

/**
 * B§13.6 #7 — the quick-add line and its chip preview, one composable for its two homes: the
 * Calendar's strip (`ui/calendar/QuickAddBar.kt`, 14f·2) and the desktop's chord-opened popup.
 * The line is read by [QuickAddParser] as it is typed and previewed as chips; a chip can drop
 * its token or flip the kind; Enter hands the parse to [onQuickAdd] and clears the line. [today]
 * is the day the line means when it names none; [placeholder] defaults to the strip's hint.
 * [trailing] sits at the field's right (the strip's ×); [leadingIcon] inside its left (the
 * popup's + glyph). The field takes focus when it appears.
 */
@Composable
fun QuickAddField(
    today: LocalDate,
    onQuickAdd: (ParsedEntry) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    previewModifier: Modifier = Modifier,
    placeholder: String? = null,
    /** What a line with no kind token becomes: the Calendar's strip says EVENT, the popup TASK. */
    defaultKind: EntryKind = EntryKind.EVENT,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** The popup's focus-loss rule (blank closes, typed stays) needs to know what is in the line. */
    onTextChanged: ((String) -> Unit)? = null,
) {
    // A `TextFieldValue`, not a String: with the String overload the field keeps its own copy and a
    // line typed right after Enter's reset lost every character after the second (the Month grid's
    // walk, 2026-09-17 — *Extra two* arrived as *Ex*).
    var value by remember { mutableStateOf(TextFieldValue("")) }
    val text = value.text
    var ignored by remember { mutableStateOf(emptySet<TokenKind>()) }
    var kindOverride by remember { mutableStateOf<EntryKind?>(null) }
    val parsed = remember(text, ignored, kindOverride) { QuickAddParser.parse(text, today, defaultKind, ignored, kindOverride) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val hint = placeholder ?: stringResource(Res.string.calendar_quick_add_hint)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                textStyle = MaterialTheme.typography.body,
                value = value,
                onValueChange = { value = it; ignored = emptySet(); kindOverride = null; onTextChanged?.invoke(it.text) },
                modifier = fieldModifier.weight(1f).focusRequester(focus),
                placeholder = { Text(hint) },
                leadingIcon = leadingIcon,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (text.isNotBlank()) { onQuickAdd(parsed); value = TextFieldValue(""); ignored = emptySet(); kindOverride = null; onTextChanged?.invoke("") }
                }),
            )
            trailing?.invoke()
        }
        if (text.isNotBlank()) {
            QuickAddPreview(
                parsed = parsed,
                onFlipKind = { kindOverride = if (parsed.kind == EntryKind.TASK) EntryKind.EVENT else EntryKind.TASK },
                onDrop = { ignored = ignored + it },
                modifier = previewModifier,
            )
        }
    }
}
