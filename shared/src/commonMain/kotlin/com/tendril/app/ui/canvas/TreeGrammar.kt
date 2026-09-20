package com.tendril.app.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.tendril.app.data.canvas.CanvasArrowDirection
import com.tendril.app.data.canvas.CanvasEdge
import com.tendril.app.domain.canvas.NodeBox
import com.tendril.app.domain.canvas.RelationRoute
import com.tendril.app.domain.canvas.TreeSide
import com.tendril.app.domain.canvas.branchAnchors

/**
 * The mind-map pass (2026-09-20) — **one grammar's painters**, drawn by the Canvas page's layer and
 * by the outline's mind map (`ui/pages/MindMap.kt`) alike: a branch (headless, a curve or an elbow,
 * the main branch's colour, 2 dp at the first level and 1.5 deeper), a relationship (dashed, `dim`,
 * a head on the target's edge), the arrowhead. Content dp × density → px.
 */
/** The mind-map pass — a branch: a headless curve from the parent's side to the child's (an elbow under DOWN), in the main branch's colour. */
internal fun DrawScope.drawBranch(parent: NodeBox, child: NodeBox, side: TreeSide, curved: Boolean, density: Float, ink: Color, width: Float) {
    val a = branchAnchors(parent, child, side)
    val x1 = a.x1 * density; val y1 = a.y1 * density; val x2 = a.x2 * density; val y2 = a.y2 * density
    val path = Path().apply {
        moveTo(x1, y1)
        if (curved) {
            if (side == TreeSide.DOWN) { val my = (y1 + y2) / 2f; cubicTo(x1, my, x2, my, x2, y2) }
            else { val mx = (x1 + x2) / 2f; cubicTo(mx, y1, mx, y2, x2, y2) }
        } else {
            if (side == TreeSide.DOWN) { val my = (y1 + y2) / 2f; lineTo(x1, my); lineTo(x2, my); lineTo(x2, y2) }
            else { val mx = (x1 + x2) / 2f; lineTo(mx, y1); lineTo(mx, y2); lineTo(x2, y2) }
        }
    }
    drawPath(path, ink, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 14g·2 — a relationship is drawn in the register's dim (B§13.8.3): dashed, 1.5 dp, a head on the target's edge (`ONE_WAY`), both ends (`TWO_WAY`) or none. */
internal fun DrawScope.drawRelationship(route: RelationRoute, edge: CanvasEdge, density: Float, ink: Color) {
    val path = Path().apply {
        moveTo(route.x1 * density, route.y1 * density)
        cubicTo(route.c1x * density, route.c1y * density, route.c2x * density, route.c2y * density, route.x2 * density, route.y2 * density)
    }
    drawPath(path, ink, style = Stroke(width = 1.5f * density, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * density, 4f * density))))
    if (edge.direction == CanvasArrowDirection.ONE_WAY || edge.direction == CanvasArrowDirection.TWO_WAY) {
        drawCanvasArrowhead(Offset(route.c2x * density, route.c2y * density), Offset(route.x2 * density, route.y2 * density), 0f, ink, density)
    }
    if (edge.direction == CanvasArrowDirection.TWO_WAY) {
        drawCanvasArrowhead(Offset(route.c1x * density, route.c1y * density), Offset(route.x1 * density, route.y1 * density), 0f, ink, density)
    }
}

internal fun DrawScope.drawCanvasArrowhead(from: Offset, to: Offset, pullBack: Float, ink: Color, density: Float = 1f) {
    val delta = to - from
    val len = delta.getDistance()
    if (len < 1f) return
    val unit = delta / len
    val perp = Offset(-unit.y, unit.x)
    val tip = to - unit * pullBack
    val l = 10f * density; val w = 5f * density
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo((tip - unit * l + perp * w).x, (tip - unit * l + perp * w).y)
        lineTo((tip - unit * l - perp * w).x, (tip - unit * l - perp * w).y)
        close()
    }
    drawPath(path, color = ink)
}

