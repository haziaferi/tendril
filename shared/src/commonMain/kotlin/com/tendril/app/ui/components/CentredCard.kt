@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tendril.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tendril.app.ui.theme.Scrim
import com.tendril.app.ui.theme.caption

/**
 * L6 (2026-09-17) — the frame the F1 card and the quick switcher share: a `Popup` over the
 * [Scrim], one card at most [cardMaxWidth] wide and never more than 60 % of the window, radius
 * 10 (the hover card's — the audit's radius family), a hairline and a soft shadow. [top] places
 * the card's top edge at that share of the window's height (the switcher's 0.2 — Notion
 * Calendar's command menu measured at 21 %); null centres it (the F1 card, a reference). Esc,
 * the scrim and whatever the caller draws as × close it. [content] receives the window's width
 * so a caller can choose its columns.
 */
@Composable
fun CentredCard(
    onDismiss: () -> Unit,
    top: Float? = null,
    cardMaxWidth: Dp = 560.dp,
    content: @Composable (windowWidth: Dp) -> Unit,
) {
    Popup(properties = PopupProperties(focusable = true, dismissOnBackPress = false), onDismissRequest = onDismiss) {
        BackHandler(enabled = true, onBack = onDismiss)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = if (top == null) Alignment.Center else Alignment.TopCenter,
        ) {
            val windowWidth = maxWidth
            val windowHeight = maxHeight
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .padding(top = if (top == null) 24.dp else windowHeight * top, start = 24.dp, end = 24.dp, bottom = 24.dp)
                    .widthIn(max = minOf(cardMaxWidth, windowWidth * 0.6f))
                    .fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            ) { content(windowWidth) }
        }
    }
}

/** A key's chip: `onSurface` on `surfaceVariant` at caption size, radius 4 — the F1 card's and the switcher's. */
@Composable
fun KeyChip(text: String) {
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
