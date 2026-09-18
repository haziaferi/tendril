package com.tendril.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.components.keyboardFocusRing
import com.tendril.app.ui.theme.Register
import com.tendril.app.ui.theme.TendrilMode
import com.tendril.app.ui.theme.TendrilTypeface
import com.tendril.app.ui.theme.ThemeSettings
import com.tendril.app.ui.theme.paletteFor
import com.tendril.app.ui.theme.resolveDark
import com.tendril.app.ui.theme.caption
import com.tendril.app.ui.theme.eyebrow
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.label

/**
 * 14g·1 — the one theme section both Settings homes render (the phone's Appearance disclosure,
 * the desktop pane between Density and *Opens on*): the register as a row of swatches — each
 * the accent on its own ground in the mode being shown, the name under — then Mode (System ·
 * Light · Dark), Typeface, and on the phone only *Deeper blacks* (B§13.7.3 rule 3: OLED is a
 * device setting, so [showOled] is false on the desktop's LCD).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ThemeSection(settings: ThemeSettings, showOled: Boolean, modifier: Modifier = Modifier) {
    val choice = settings.observe()
    val dark = choice.mode.resolveDark()
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Register", style = MaterialTheme.typography.label, modifier = Modifier.padding(bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Register.ALL.forEach { register ->
                RegisterSwatch(
                    register = register,
                    dark = dark,
                    selected = register == choice.register,
                    onClick = { settings.setRegister(register) },
                )
            }
        }
        Text(
            choice.register.use.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(16.dp))
        Text("Mode", style = MaterialTheme.typography.label, modifier = Modifier.padding(bottom = 8.dp))
        SingleChoiceSegmentedButtonRow {
            TendrilMode.entries.forEachIndexed { index, m ->
                SegmentedButton(
                    selected = choice.mode == m,
                    onClick = { settings.setMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(index, TendrilMode.entries.size),
                ) { Text(m.label) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Typeface", style = MaterialTheme.typography.label, modifier = Modifier.padding(bottom = 8.dp))
        SingleChoiceSegmentedButtonRow {
            TendrilTypeface.entries.forEachIndexed { index, t ->
                SegmentedButton(
                    selected = choice.typeface == t,
                    onClick = { settings.setTypeface(t) },
                    shape = SegmentedButtonDefaults.itemShape(index, TendrilTypeface.entries.size),
                ) { Text(t.label) }
            }
        }

        if (showOled) {
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Deeper blacks", style = MaterialTheme.typography.body)
                    Text(
                        "For an OLED screen: the dark ground at 4 %, every colour re-solved on it.",
                        style = MaterialTheme.typography.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = choice.oled, onCheckedChange = { settings.setOled(it) })
            }
        }
    }
}

/** A 44 dp square of the register's ground with its accent as a disc; a 2 dp accent ring when chosen.
 *  The column is as wide as its name (at least the square), so *Kodachrome* is never clipped. */
@Composable
private fun RegisterSwatch(register: Register, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val palette = remember(register, dark) { paletteFor(register, dark) }
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .defaultMinSize(minWidth = 52.dp)
            .keyboardFocusRing(6, MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = register.label + " register" },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.bg)
                .border(if (selected) 2.dp else 1.dp, ring, RoundedCornerShape(10.dp)),
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(palette.accent))
        }
        Text(  // type: ICON_LABEL — the name under a swatch, the rail's kind
            register.label,
            style = MaterialTheme.typography.caption,
            textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
    }
}
