package com.tendril.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 14g·2 — a label's hue as a 10 dp disc, for menus and lists that name labels without a chip. */
@Composable
fun LabelDot(hue: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(hue, CircleShape))
}
