package com.tendril.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * B§13.4 14a — the shell's chrome, drawn as `docs/mockups/desktop-shell.html` draws it rather
 * than as Material's `NavigationRail`/`NavigationBar`/`TopAppBar` would. The sizes are the mock's
 * pixel values as dp; the colours are the theme's own tokens through the mapping `Theme.kt`
 * already makes (`surface2` → `surfaceVariant`, `border` → `outline`, `accentSoft` →
 * `primaryContainer`, `textDim` → `onSurfaceVariant`), so 14g's re-solved registers reach this
 * chrome without it changing. What 14d owes on top: the density scale, hover-only row controls.
 *
 * - [ShellRail]: 84 dp, on `surface2` with a right hairline; each item a 52×30 pill under an
 *   11.5 sp label, the pill filled when current or hovered; the running timer at the foot.
 * - [ShellBottomBar]: the phone's form, 80 dp on `surface2` with a top hairline, 64×32 pills,
 *   12 sp labels; the running timer as a 36 dp strip above it (`RunningTimerBar`).
 * - [ShellTopBar]: 52 dp, a 17 sp/500 title, a bottom hairline; the same three slots as
 *   Material's `TopAppBar`, so every screen swapped it in by name.
 */
@Composable
fun ShellRail(navState: WorkbenchNavState, foot: @Composable () -> Unit) {
    val currentTab = navState.currentTab
    Row(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(RAIL_WIDTH)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .windowInsetsPadding(shellInsets.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WorkbenchDestination.entries.forEach { destination ->
                ShellItem(
                    icon = destination.icon,
                    label = destinationLabel(destination),
                    selected = currentTab == destination,
                    onClick = { navState.switchTab(destination) },
                    pillWidth = 52.dp, pillHeight = 30.dp, iconSize = 22.dp, labelSize = 11.5f,
                    modifier = Modifier.width(RAIL_WIDTH).padding(vertical = 10.dp),
                )
            }
            Box(modifier = Modifier.weight(1f))
            foot()
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun ShellBottomBar(navState: WorkbenchNavState) {
    val currentTab = navState.currentTab
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(shellInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .height(BOTTOM_BAR_HEIGHT),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkbenchDestination.entries.forEach { destination ->
                ShellItem(
                    icon = destination.icon,
                    label = destinationLabel(destination),
                    selected = currentTab == destination,
                    onClick = { navState.switchTab(destination) },
                    pillWidth = 64.dp, pillHeight = 32.dp, iconSize = 24.dp, labelSize = 12f,
                    // The mock's 96 px items were drawn in a 700 px window; on a 360 dp phone five
                    // of them would run off the edge, so here they share the width equally.
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One destination: a pill around the icon, the label under it; filled when current or hovered. */
@Composable
private fun ShellItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    pillWidth: androidx.compose.ui.unit.Dp,
    pillHeight: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    labelSize: Float,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val colour = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            // No ripple: the pill is the whole of the affordance, as in the mock.
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(pillWidth, pillHeight)
                .background(
                    if (selected || hovered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(pillHeight / 2),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colour, modifier = Modifier.size(iconSize))
        }
        Text(label, fontSize = labelSize.sp, lineHeight = (labelSize * 1.3f).sp, color = colour, maxLines = 1)
    }
}

/**
 * The 52 dp top bar every screen wears: the same three slots as Material's `TopAppBar` (title,
 * navigationIcon, actions) so the swap was a rename at twelve call sites. Handles the status-bar
 * and cutout insets itself, as `TopAppBar` did, because the scaffold gives its content none.
 */
@Composable
fun ShellTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(shellInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                .height(TOP_BAR_HEIGHT)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            navigationIcon()
            Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                ProvideTextStyle(
                    MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface),
                ) { title() }
            }
            // A nested Row, as Material's `TopAppBar` has: a `DropdownMenu` in the actions anchors
            // to its parent node, and the parent must be the actions' own box, not the whole bar.
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

/** The mock's rail is 84 px wide, its bottom bar 80 px tall, its top bar 52 px; dp here. */
val RAIL_WIDTH = 84.dp
val BOTTOM_BAR_HEIGHT = 80.dp
val TOP_BAR_HEIGHT = 52.dp

/** What the platform paints over: status bar, navigation bar, cutout. Zero on desktop. */
private val shellInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

@Composable
private fun destinationLabel(destination: WorkbenchDestination): String =
    org.jetbrains.compose.resources.stringResource(destination.labelRes)
