package com.tendril.app.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.tendril.app.domain.plan.BlockKind
import com.tendril.app.ui.theme.LocalTendrilPalette

/**
 * B§13.8.3 (14g·2) — the calendar's layers by token, not by Material role: an event block is the
 * register's event hue at its tint, a habit block the habit hue's; a task block keeps the
 * selection tint until 14g·3 gives it its category's fill and the urgency stripe; anything else
 * (a database date, until the slider PR gives a database its own hue) the third hue's tint.
 * The block's text is the register's own text on every one of them (≥ 4.6:1, tested).
 */
@Composable
fun layerTint(kind: BlockKind): Color {
    val p = LocalTendrilPalette.current
    return when (kind) {
        BlockKind.EVENT -> p.eventSoft
        BlockKind.TASK -> p.accentSoft
        BlockKind.HABIT -> p.habitSoft
        BlockKind.OTHER -> p.thirdSoft
    }
}
