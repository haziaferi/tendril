package com.tendril.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.nav.LocalDensityProfile
import com.tendril.app.ui.theme.body

/**
 * The audit's fixes (2026-09-17, L1 of `docs/critiques/desktop-layout-full.md`) — every menu in
 * the app goes through here, and `tools/audit.py`'s *bare menu* rule keeps it so. Material's
 * `DropdownMenuItem` sizes itself to 48 dp whatever the interactive minimum says, so five menus
 * measured 49 px a row beside Notion's 29 and the app's own rows at 30. Under a pointer profile
 * an item is the app's own row — the profile's height ([DensityProfile.rowHeightDp]), 12 dp
 * sides, `body` text, the 28 dp interactive minimum for anything inside it; under Touch it is
 * Material's item unchanged, 48 dp and all.
 *
 * **A second level under Touch (the phone's fix PR, P3, 2026-09-18):** a [SubmenuItem] on a phone
 * *pushes* its rows into this menu — a back row with the item's own text, then the rows — instead
 * of opening a menu beside it: on a 360 dp window there is no beside, and the flip put the levels
 * over the parent's rows (`phone-catch-up.md` #3). Notion's phone sheet pushes *Duplica ›* the same
 * way. Under a pointer the submenu stays a menu beside its item.
 */
@Composable
fun TendrilMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    val profile = LocalDensityProfile.current
    val level = remember { MenuLevel() }
    LaunchedEffect(expanded) { if (!expanded) level.pushed = null }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier, offset = offset) {
        if (profile.pointer) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides profile.listInteractiveMinDp.dp) { content() }
        } else CompositionLocalProvider(LocalMenuLevel provides level) {
            val pushed = level.pushed
            if (pushed == null) content() else {
                TendrilMenuItem(
                    text = { level.title?.invoke() },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") },
                    onClick = { level.pushed = null },
                )
                pushed(this) { level.pushed = null }
            }
        }
    }
}

/** The pushed level of a Touch menu: the rows shown in place of the parent's, and the item that opened them. */
class MenuLevel {
    var pushed by mutableStateOf<(@Composable ColumnScope.(close: () -> Unit) -> Unit)?>(null)
    var title by mutableStateOf<(@Composable () -> Unit)?>(null)
}

/** Non-null inside a Touch [TendrilMenu]; a [SubmenuItem] pushes into it instead of opening beside. */
val LocalMenuLevel = compositionLocalOf<MenuLevel?> { null }

/** One row of a [TendrilMenu]; the same slots as Material's item, so a site changes only the name. */
@Composable
fun TendrilMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val profile = LocalDensityProfile.current
    if (!profile.pointer) {
        DropdownMenuItem(text = text, onClick = onClick, modifier = modifier, leadingIcon = leadingIcon, trailingIcon = trailingIcon, enabled = enabled)
        return
    }
    val colour = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(min = 112.dp, max = 280.dp)
            .heightIn(min = profile.rowHeightDp.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(PaddingValues(horizontal = 12.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fill = Modifier.weight(1f)
        CompositionLocalProvider(LocalContentColor provides colour) {
            if (leadingIcon != null) { leadingIcon(); Spacer(Modifier.width(10.dp)) }
            ProvideTextStyle(MaterialTheme.typography.body) { Box(modifier = fill) { text() } }
            if (trailingIcon != null) { Spacer(Modifier.width(10.dp)); trailingIcon() }
        }
    }
}
