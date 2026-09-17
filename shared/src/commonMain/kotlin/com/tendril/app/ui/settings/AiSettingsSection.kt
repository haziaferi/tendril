package com.tendril.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tendril.app.data.prefs.AiKeyStore
import com.tendril.app.data.prefs.KeyValueStore
import com.tendril.app.domain.ai.AI_MODEL_KEY
import com.tendril.app.domain.ai.AiModels
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.label
import com.tendril.app.ui.components.BarPillButton
import com.tendril.app.ui.components.TendrilField
import com.tendril.app.ui.components.TendrilMenu
import com.tendril.app.ui.components.TendrilMenuItem

/**
 * §0.6.15 / §3.5 — the Anthropic key (masked, reveal, Save, Clear) and the model. Saving a
 * key makes no network call: the app stays local until a verb is pressed on a selection. The
 * key goes to [AiKeyStore] (a secret's home on each platform), the model to [KeyValueStore].
 */
@Composable
fun AiSettingsSection(aiKeyStore: AiKeyStore, keyValueStore: KeyValueStore) {
    val storedKey by aiKeyStore.key.collectAsState()
    var draft by remember(storedKey) { mutableStateOf(storedKey ?: "") }
    var revealed by remember { mutableStateOf(false) }
    val storedModel by keyValueStore.observe(AI_MODEL_KEY).collectAsState(initial = keyValueStore.get(AI_MODEL_KEY))
    val model = storedModel?.takeIf { it in AiModels } ?: AiModels.first()
    var modelMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Key, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Claude (opt-in)", style = MaterialTheme.typography.body)
                Text(
                    text = if (storedKey != null) "Key saved — Rewrite, Expand and Summarise appear on a selection" else "No key — nothing is sent anywhere",
                    style = MaterialTheme.typography.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // L6 — the desktop's 36 dp field (the switcher's frame); the label leads inside the hairline.
            TendrilField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = "sk-ant-…",
                leading = { Text("API key", style = MaterialTheme.typography.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp)) },
                visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
            )
            BarPillButton(label = if (revealed) "Hide" else "Reveal", onClick = { revealed = !revealed })
            if (draft.trim() != (storedKey ?: "")) BarPillButton(label = "Save", onClick = { aiKeyStore.set(draft) })
            if (storedKey != null) BarPillButton(label = "Clear", onClick = { aiKeyStore.set(null); draft = "" })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Model", style = MaterialTheme.typography.body, modifier = Modifier.weight(1f))
            Box {
                AssistChip(
                    onClick = { modelMenu = true },
                    label = { Text(model) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                )
                TendrilMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                    AiModels.forEach { id ->
                        TendrilMenuItem(text = { Text(id) }, onClick = { keyValueStore.put(AI_MODEL_KEY, id); modelMenu = false })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "What is sent: only the text you selected and the verb's instruction, to Anthropic, with this key. " +
                "Never the page title, other blocks, or anything about you. Nothing leaves the device until you press a verb.",
            style = MaterialTheme.typography.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
