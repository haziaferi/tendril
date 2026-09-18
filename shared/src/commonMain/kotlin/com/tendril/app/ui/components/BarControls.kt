package com.tendril.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tendril.app.ui.nav.LocalDensityProfile
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.theme.label

/**
 * L5 (2026-09-17) — the bar's own controls, now that every tab's chrome is one 52 dp bar: a
 * menu button (`Week ▾`, `Layers ▾`, `Today ▾`, `Filter ▾`) and a pill (`Today`), both 28 dp at
 * `label`, the radius-6 family of the bar's icon buttons. The menu itself is `TendrilMenu`,
 * anchored by the caller's `Box`; a check-menu's items keep it open, so several layers or kinds
 * toggle in one visit (Fantastical's calendar-set menu). Under Touch (the phone's second fix PR,
 * L·P2 / L·P3, 2026-09-18) the same controls stand 40 dp tall — the bar and the Tasks' pane header
 * carry them on the phone now, and a 28 dp pill is a finger's miss.
 */
@Composable
fun BarMenuButton(label: String, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(barControlHeight())
            .background(if (open) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.label, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun BarPillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(barControlHeight())
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.label, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 28 dp under a pointer, 40 under Touch — the bar's pills, menu buttons and pane tabs. */
@Composable
fun barControlHeight(): androidx.compose.ui.unit.Dp = if (LocalDensityProfile.current.pointer) 28.dp else 40.dp

/** A check-menu item's trailing mark: present when on, an empty 16 dp box when off, so the labels align. */
@Composable
fun MenuCheck(on: Boolean) {
    if (on) Icon(Icons.Filled.Check, contentDescription = "On", modifier = Modifier.size(16.dp))
    else androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(16.dp))
}
