package com.tendril.app.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.domain.ai.AiVerb
import com.tendril.app.ui.components.TendrilSheet

/**
 * §0.6.15 — a verb's answer, and the choice of what to do with it. Nothing has been written
 * when this opens: *Replace selection* and *Insert below* are the only writes, Cancel is free.
 * The caption says what left the device, because that is the question a person has.
 */
@Composable
internal fun AiResultSheet(
    verb: AiVerb,
    result: Result<String>?,
    onReplace: (String) -> Unit,
    onInsertBelow: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    TendrilSheet(title = verb.label, onDismiss = onDismiss) {
        Column {
            when {
                result == null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Asking Claude…", style = MaterialTheme.typography.bodyMedium)
                }
                result.isSuccess -> {
                    val text = result.getOrThrow()
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
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
                else -> {
                    Text(result.exceptionOrNull()?.message ?: "Request failed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(onClick = onRetry) { Text("Try again") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Only the selected text and the verb's instruction were sent to Anthropic, with your own key.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
