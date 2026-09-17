package com.tendril.app.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.theme.caption
import com.tendril.app.ui.theme.tabular
import java.time.LocalTime

/** The chip's height: Notion's calendar chips measure 28 px at 125 % ≈ 22 dp; the pitch is the height plus [CHIP_GAP_DP]. */
const val CHIP_HEIGHT_DP = 22
const val CHIP_GAP_DP = 2

/**
 * One occurrence on one line — the Week's all-day row's chip since 14f·2, extracted for the Month
 * grid (L4, 2026-09-17) so the two draw the same thing: the layer's tint, the urgency stripe at
 * the left edge (14g·3), the title on one line, and — the Month's addition — the time first,
 * tabular, in the same `onSurface` as the title: `onSurfaceVariant` measured 3.0–4.2:1 on the
 * tints (`docs/critiques/month-grid-mock.md` #1), and the register solves `text` on every soft.
 */
@Composable
fun OccurrenceChip(
    title: String,
    tint: Color,
    stripe: Color?,
    time: LocalTime? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = tint,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = CHIP_HEIGHT_DP.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .drawBehind { stripe?.let { drawRect(it, size = Size(4.dp.toPx(), size.height)) } }
                .padding(start = if (stripe != null) 10.dp else 6.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        ) {
            if (time != null) Text(time.toString(), style = MaterialTheme.typography.caption.tabular(), maxLines = 1, modifier = Modifier.padding(end = 4.dp))
            Text(title, style = MaterialTheme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
