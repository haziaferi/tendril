package com.tendril.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tendril.app.domain.urgency.Urgency
import com.tendril.app.ui.theme.LocalTendrilPalette

/**
 * B§13.8.1 (14g·3) — the urgency ladder drawn as shapes, never a glyph (the mock's star measured
 * 3.05:1 as text — `docs/critiques/urgency-ladder-mock.md` #1): a 4 dp stripe on a row or a
 * calendar block, a disc in the picker and menus. *None* draws nothing, but the stripe keeps its
 * width so titles align down a list.
 */
@Composable
fun UrgencyStripe(urgency: Urgency, height: Dp, modifier: Modifier = Modifier) {
    val colour = LocalTendrilPalette.current.urgencyColour(urgency.level)
    Box(
        modifier
            .width(4.dp)
            .height(height)
            .then(if (colour != null) Modifier.background(colour, RoundedCornerShape(2.dp)).semantics { contentDescription = "Urgency: " + urgency.label } else Modifier),
    )
}

/** The level as a 10 dp disc — menus and rows that name a level without a stripe. */
@Composable
fun UrgencyDot(urgency: Urgency, modifier: Modifier = Modifier) {
    val palette = LocalTendrilPalette.current
    val colour = palette.urgencyColour(urgency.level)
    Box(
        modifier.size(10.dp).then(
            if (colour != null) Modifier.background(colour, CircleShape) else Modifier.border(1.5.dp, palette.textDim, CircleShape),
        ),
    )
}

/** Five discs, none → urgent; the chosen one ringed. Each disc is a radio with its level's name. */
@Composable
fun UrgencyPicker(value: Urgency, onPick: (Urgency) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalTendrilPalette.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Urgency.entries.forEach { u ->
            val colour = palette.urgencyColour(u.level)
            val chosen = u == value
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .then(if (chosen) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable(role = Role.RadioButton) { onPick(u) }
                    .semantics { selected = chosen; contentDescription = u.label },
            ) {
                Box(
                    Modifier.size(18.dp).align(androidx.compose.ui.Alignment.Center).then(
                        if (colour != null) Modifier.background(colour, CircleShape) else Modifier.border(1.5.dp, palette.textDim, CircleShape),
                    ),
                )
            }
        }
    }
}
