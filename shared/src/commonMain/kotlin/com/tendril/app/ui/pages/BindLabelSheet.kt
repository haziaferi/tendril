@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.tendril.app.ui.pages

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.ui.components.LabelDot
import com.tendril.app.ui.theme.LocalTendrilPalette
import com.tendril.app.ui.theme.labelColours
import com.tendril.app.data.page.Label
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.heading

/**
 * §0.6.8 — a database's "Bind a label…". The same type-to-search-or-create the page's label
 * picker uses, with the one paragraph that says what binding does; a person who reads it and
 * binds nothing has today's app (E12). [current] is the label already bound, if any, so the
 * sheet reads as "change" rather than "bind" and offers to unbind.
 */
@Composable
internal fun BindLabelSheet(
    databaseTitle: String,
    current: Label?,
    syncToTasks: Boolean,
    search: suspend (String) -> List<Label>,
    onBind: (String) -> Unit,
    onUnbind: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<Label>>(emptyList()) }
    LaunchedEffect(query) { candidates = search(query).filter { it.id != current?.id } }

    TendrilSheet(onDismiss = onDismiss, modifier = Modifier.fillMaxHeight(0.6f)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (current == null) "Bind a label" else "Bound to #${current.name}",
                    style = MaterialTheme.typography.heading,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            Text(
                buildString {
                    append("Pages carrying the label become rows of ")
                    append(databaseTitle.ifBlank { "this database" })
                    append(" — in every view, with its fields in their header. They stay where they are")
                    if (syncToTasks) append(", and become tasks, because this database syncs to Tasks")
                    append(".")
                },
                style = MaterialTheme.typography.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (current != null) {
                TextButton(onClick = onUnbind) { Text("Unbind #${current.name}") }
                Text(
                    "Unbinding takes the labelled pages out of the views and their tasks to Trash; their values stay with them until the database is deleted forever.",
                    style = MaterialTheme.typography.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textStyle = MaterialTheme.typography.body.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Label name", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                },
            )
            if (query.isNotBlank() && candidates.none { it.name.equals(query.trim(), ignoreCase = true) }) {
                TextButton(onClick = { onBind(query) }) { Text("Create \"${query.trim()}\" and bind") }
            }
            val palette = LocalTendrilPalette.current
            LazyColumn {
                items(candidates, key = { it.id }) { candidate ->
                    Row(
                        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onBind(candidate.name) }).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LabelDot(labelColours(candidate.color, palette).hue)
                        Spacer(Modifier.width(10.dp))
                        Text(candidate.name, style = MaterialTheme.typography.body)
                    }
                }
            }
        }
    }
}
