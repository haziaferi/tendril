@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tendril.app.ui.nav

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.eyebrow
import com.tendril.app.ui.theme.heading
import com.tendril.app.ui.theme.label
import com.tendril.app.ui.theme.caption

/**
 * 14e — the shortcut list, **generated from [SHORTCUTS]** so what is listed is what is bound
 * (`docs/critiques/keyboard-desktop.md` #2), plus the static rows for the list keys and Esc. A
 * centred card over a scrim — a reference, not a task, so not a slide-over (14b frames tasks);
 * the mock's `help()` geometry: 560 dp at most, two columns of rows from 700 dp of width, one
 * below. Key chips are `onSurface` on `surfaceVariant` at 12 sp — the mock's measured 3.08:1
 * (#1) is the one thing this card must not repeat. Esc, the scrim and × close it.
 */
@Composable
fun ShortcutsOverlay(onDismiss: () -> Unit, quickAddChordLabel: String = QuickAddChord.DEFAULT.label) {
    Popup(properties = PopupProperties(focusable = true), onDismissRequest = onDismiss) {
        BackHandler(enabled = true, onBack = onDismiss)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val twoColumns = maxWidth >= 700.dp
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            ) {
                Column(modifier = Modifier.padding(start = 26.dp, end = 14.dp, top = 14.dp, bottom = 22.dp).verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Keyboard shortcuts", style = MaterialTheme.typography.heading, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    }
                    val groups = shortcutRows(quickAddChordLabel)
                    if (twoColumns) {
                        val half = (groups.size + 1) / 2
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(end = 12.dp)) {
                            Column(modifier = Modifier.weight(1f)) { groups.take(half).forEach { GroupBlock(it) } }
                            Column(modifier = Modifier.weight(1f)) { groups.drop(half).forEach { GroupBlock(it) } }
                        }
                    } else {
                        Column(modifier = Modifier.padding(end = 12.dp)) { groups.forEach { GroupBlock(it) } }
                    }
                }
            }
        }
    }
}

/** One row of the card: what it does, then the keys. */
class ShortcutRow(val label: String, val keys: List<String>)

/**
 * The card's content, **generated from the table** — every bound action appears once, in its
 * group, in the table's order; the five tab chords fold into one row and Back/Forward into one;
 * then the static rows a chord does not own. The audit's fixes (2026-09-17, F2): the rows were
 * hand-written and the shelf's Ctrl+Shift+\ never made the card; `ShortcutsTest` now holds the
 * card to the table so an action cannot be bound and unlisted again.
 */
fun shortcutRows(quickAddChordLabel: String = QuickAddChord.DEFAULT.label): List<Pair<ShortcutGroup, List<ShortcutRow>>> {
    val byAction = SHORTCUTS.toMap()
    fun chord(a: ShortcutAction) = byAction.getValue(a).label()
    val tabs = ShortcutAction.entries.filter { it.tab() != null }
    val folded = tabs.toSet() + setOf(ShortcutAction.BACK, ShortcutAction.FORWARD)
    fun rowsOf(group: ShortcutGroup): List<ShortcutRow> = buildList {
        if (group == ShortcutGroup.NAVIGATE) {
            add(ShortcutRow("Pages · Calendar · Tasks · Road Map · Settings", listOf("Ctrl+1 … 5")))
            add(ShortcutRow("Back · forward · the mouse's side buttons", listOf(chord(ShortcutAction.BACK), chord(ShortcutAction.FORWARD))))
        }
        SHORTCUTS.map { it.first }.filter { it.group == group && it !in folded }.forEach { add(ShortcutRow(it.label, listOf(chord(it)))) }
    }
    val navigate = rowsOf(ShortcutGroup.NAVIGATE) + listOf(
        ShortcutRow("Back · close", listOf("Esc")),
        // B§13.6 #6 — a pop-out window's own key; the table above is the main window's.
        ShortcutRow("Close a pop-out window", listOf("Ctrl+W")),
    )
    val create = rowsOf(ShortcutGroup.CREATE) +
        // B§13.6 #7 — the global chord is the OS's, registered by the desktop, chosen in Settings;
        // not in the table above because the window never sees it.
        ShortcutRow("Quick add from anywhere — set in Settings", listOf(quickAddChordLabel))
    val find = rowsOf(ShortcutGroup.FIND)
    val lists = rowsOf(ShortcutGroup.LISTS) + listOf(
        ShortcutRow("Move · open", listOf("↑", "↓", "↵")),
        ShortcutRow("Expand · collapse in the tree", listOf("→", "←")),
        ShortcutRow("Jump to a row by its first letters", listOf("type")),
        ShortcutRow("Clear the cursor", listOf("Esc")),
    )
    return listOf(ShortcutGroup.NAVIGATE to navigate, ShortcutGroup.CREATE to create, ShortcutGroup.FIND to find, ShortcutGroup.LISTS to lists)
}

@Composable
private fun GroupBlock(group: Pair<ShortcutGroup, List<ShortcutRow>>) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Text(group.first.label.uppercase(), style = MaterialTheme.typography.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        group.second.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(row.label, style = MaterialTheme.typography.body, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.keys.forEach { KeyChip(it) } }
            }
        }
    }
}

@Composable
private fun KeyChip(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(text, style = MaterialTheme.typography.caption, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
