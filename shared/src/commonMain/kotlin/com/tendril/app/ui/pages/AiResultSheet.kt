package com.tendril.app.ui.pages

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tendril.app.domain.ai.AiVerb
import com.tendril.app.domain.ai.OutlineRow
import com.tendril.app.domain.ai.parseOutline
import com.tendril.app.ui.nav.DensityProfile
import com.tendril.app.ui.nav.LocalDensityProfile
import com.tendril.app.ui.theme.heading
import com.tendril.app.ui.theme.label
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description

/**
 * §0.6.15 — a verb's answer, and the choice of what to do with it. Nothing has been written
 * when this opens: *Replace selection* and *Insert below* are the only writes, Cancel is free.
 * The caption says what left the device, because that is the question a person has.
 *
 * The fourth verb (2026-09-22, `docs/critiques/ai-mind-map-mock.md`): a *Mind map* reply is
 * parsed to rows and previewed as the rows it will become — the root at Medium, the tree's
 * 18 dp indent, one line each — with *Insert as a mind map* (§0.6.2's flag on the root) or
 * *Insert as a list*; no *Replace selection*, since a nested list cannot live in one field. A
 * reply with no list in it is a failure with *Try again*. Under Touch the sheet's dismiss is
 * the cancel, so *Cancel* is not a third button (the critique's #3).
 */
@Composable
internal fun AiResultSheet(
    verb: AiVerb,
    result: Result<String>?,
    onReplace: (String) -> Unit,
    onInsertBelow: (String) -> Unit,
    onInsertOutline: (List<OutlineRow>, Boolean) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pointer = LocalDensityProfile.current != DensityProfile.TOUCH
    val rows = if (verb == AiVerb.MIND_MAP && result?.isSuccess == true) parseOutline(result.getOrThrow()) else null
    TendrilSheet(title = verb.label, onDismiss = onDismiss) {
        Column {
            when {
                result == null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Asking Claude…", style = MaterialTheme.typography.body)
                }
                rows != null && rows.isEmpty() -> FailureRows("The reply held no list", pointer, onRetry, onDismiss)
                rows != null -> {
                    OutlinePreview(rows)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(onClick = { onInsertOutline(rows, true) }) { Text("Insert as a mind map") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onInsertOutline(rows, false) }) { Text("Insert as a list") }
                        if (pointer) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = onDismiss) { Text("Cancel") }
                        }
                    }
                }
                result.isSuccess -> {
                    val text = result.getOrThrow()
                    Text(
                        text,
                        style = MaterialTheme.typography.body,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(onClick = { onReplace(text) }) { Text("Replace selection") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onInsertBelow(text) }) { Text("Insert below") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                    }
                }
                else -> FailureRows(result.exceptionOrNull()?.message ?: "Request failed", pointer, onRetry, onDismiss)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Only the selected text and the verb's instruction were sent to Anthropic, with your own key.",
                style = MaterialTheme.typography.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FailureRows(message: String, pointer: Boolean, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Text(message, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(12.dp))
    Row {
        Button(onClick = onRetry) { Text("Try again") }
        if (pointer) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
}

/** The rows as the page will hold them: a hairline box, the tree's indent a level, the root at
 * Medium with the ordinary bullet (the accent stays reserved — the critique's #1), each row one
 * line ellipsised (#2); scrolls past 320 dp. */
@Composable
private fun OutlinePreview(rows: List<OutlineRow>) {
    val profile = LocalDensityProfile.current
    Column(
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .heightIn(max = 320.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        rows.forEach { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = profile.rowHeightDp.dp).padding(start = (4 + 18 * row.depth).dp),
            ) {
                Text("\u2022", style = MaterialTheme.typography.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    row.text,
                    style = if (row.depth == 0) MaterialTheme.typography.heading else MaterialTheme.typography.body,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
