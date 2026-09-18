package com.tendril.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.nav.TOP_BAR_HEIGHT
import com.tendril.app.ui.theme.heading

/**
 * L5 (2026-09-17) — a pane's header row at the bar's height, so the one hairline runs across
 * the bar and every pane under it (the tree header's rule since 14d, now a composable the Tasks
 * tab's list column shares): 52 dp, a bottom hairline, the content start at 10 dp. [PaneTab] is
 * one of the row's tabs — a `heading` in a 28 dp pill, filled when current.
 */
@Composable
fun PaneHeader(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(TOP_BAR_HEIGHT).padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun PaneTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text( // type: SECTION_HEADING — a pane's tab is the tree header's *Pages*: the pane's own title
        label,
        style = MaterialTheme.typography.heading,
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .height(28.dp)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .wrapContentHeight(),
    )
}
