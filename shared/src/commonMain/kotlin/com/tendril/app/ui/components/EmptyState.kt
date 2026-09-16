package com.tendril.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.theme.body

/**
 * The single reusable empty-state composable decided in §2.5 — every list-shaped screen in
 * the spec (Pages, Tasks, Habits, Road Map, Trash) renders through this rather than a bespoke
 * per-screen layout. [ctaLabel]/[onCta] are both null for a screen with no action (e.g. a
 * search-with-no-matches state), otherwise both must be provided together.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    ctaLabel: String? = null,
    onCta: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(32.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // decorative — message text below carries the meaning
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(text = message, style = MaterialTheme.typography.body)
        if (ctaLabel != null && onCta != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCta) {
                Text(ctaLabel)
            }
        }
    }
}
