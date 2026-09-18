package com.tendril.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.tendril.app.data.page.PageKind
import com.tendril.app.domain.preview.LineKind
import com.tendril.app.domain.preview.PagePreview
import com.tendril.app.domain.preview.canvasPreview
import com.tendril.app.domain.preview.databasePreview
import com.tendril.app.domain.preview.pagePreview
import com.tendril.app.domain.preview.referencePreview
import com.tendril.app.domain.time.relativeTime
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.nav.LocalDensityProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.tendril.app.ui.theme.caption
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.heading

/**
 * B§13.6 #3 — hover previews. Hold the pointer on an `@mention`, a mention block, a block
 * reference or a Road Map node for [HOVER_DELAY_MS] and a card shows what it points at: the
 * page's kind and title, when it was edited, its first lines (`domain/preview/PagePreview.kt`);
 * a block reference shows its line *in context*, marked. Obsidian's page preview and Notion's,
 * without the modifier key. Desktop only — `LocalDensityProfile.pointer`; a finger has no hover,
 * and on Touch none of this composes.
 *
 * One [HoverPreviewState] per screen (a page, the map; a shelf page and a pop-out each their
 * own): the targets call [hoverPreview] and the screen draws one [HoverPreviewCard] at its root.
 * The card is a non-focusable `Popup`, so typing continues under it; it stays while the pointer
 * is on it (the card reports [HoverPreviewState.overCard]) and a click on it opens the page.
 */
const val HOVER_DELAY_MS = 500L
/** The pointer's leave grace: long enough to cross from the target to the card, short enough that a card never lingers. */
private const val LEAVE_GRACE_MS = 150L
private val CARD_WIDTH = 320.dp
private val CARD_GAP = 8.dp

sealed class PreviewTarget {
    data class Page(val pageId: Long) : PreviewTarget()
    data class Reference(val uid: String, val sourcePageId: Long?) : PreviewTarget()
}

class HoverPreviewState {
    var target by mutableStateOf<PreviewTarget?>(null)
        private set
    /** The target's bounds in window coordinates — the card opens under its line, never over it. */
    var anchor by mutableStateOf<Rect?>(null)
        private set
    var overCard by mutableStateOf(false)

    fun show(target: PreviewTarget, anchor: Rect) { this.target = target; this.anchor = anchor }
    fun hide() { target = null; anchor = null; overCard = false }
    /** Hide only if [target] is still the one up — a pointer that crossed to another target has already replaced it. */
    fun hideIf(target: PreviewTarget) { if (this.target == target && !overCard) hide() }
}

/**
 * The target's half: 500 ms after the pointer settles (an Enter or a Move with no button held)
 * the card opens under this element; a leave, a press or a scroll cancels the wait; a leave while
 * the card is up hides it after the grace unless the pointer is on the card. Observed on the
 * Initial pass without consuming, so the element's own click, drag or text editing is untouched.
 * Returns `this` unchanged on Touch.
 */
@Composable
fun Modifier.hoverPreview(state: HoverPreviewState, target: PreviewTarget?): Modifier {
    if (!LocalDensityProfile.current.pointer || target == null) return this
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return this
        .onGloballyPositioned { coords = it }
        .pointerInput(state, target) {
            coroutineScope {
                var pending: Job? = null
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Enter, PointerEventType.Move -> {
                                if (event.buttons.isPrimaryPressed || event.buttons.isSecondaryPressed) { pending?.cancel(); pending = null }
                                else if (pending == null && state.target != target) {
                                    pending = launch {
                                        delay(HOVER_DELAY_MS)
                                        coords?.takeIf { it.isAttached }?.let { state.show(target, it.boundsInWindow()) }
                                        pending = null
                                    }
                                }
                            }
                            PointerEventType.Exit -> {
                                pending?.cancel(); pending = null
                                launch { delay(LEAVE_GRACE_MS); state.hideIf(target) }
                            }
                            PointerEventType.Press, PointerEventType.Scroll -> {
                                pending?.cancel(); pending = null
                                if (state.target == target) state.hide()
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
}

/**
 * The card: 320 dp, the kind's glyph, the title, *edited 2 h ago*; the lines at `bodySmall`, a
 * heading Medium, a to-do with its box, a count in the dim colour, a marked line on the third
 * hue's tint with a 3 dp bar (the block reference's own mark); a footer only when blocks were left
 * out or the target is a reference. Placed 8 dp under the anchor, flipped above near the window's
 * foot, clamped to the window's width. A click opens the page — Ctrl beside, Shift in a window,
 * when the screen offers those (the workspace does; a shelf, a pop-out and the phone do not).
 */
@Composable
fun HoverPreviewCard(
    core: WorkbenchCore,
    state: HoverPreviewState,
    onOpenPage: (Long) -> Unit,
    onOpenBeside: ((Long) -> Unit)? = null,
    onOpenInWindow: ((Long) -> Unit)? = null,
) {
    val target = state.target ?: return
    val anchor = state.anchor ?: return
    var preview by remember(target) { mutableStateOf<PagePreview?>(null) }
    var pageId by remember(target) { mutableStateOf<Long?>(null) }
    LaunchedEffect(target) {
        val loaded = loadPreview(core, target)
        if (loaded == null) { state.hide(); return@LaunchedEffect }
        preview = loaded.second
        pageId = loaded.first
    }
    val p = preview ?: return
    val gapPx = with(LocalDensity.current) { CARD_GAP.roundToPx() }
    val provider = remember(anchor, gapPx) { UnderAnchor(anchor, gapPx) }
    val windowInfo = LocalWindowInfo.current
    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false)) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .width(CARD_WIDTH)
                .pointerInput(state) {
                    coroutineScope {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                when (event.type) {
                                    PointerEventType.Enter -> state.overCard = true
                                    PointerEventType.Exit -> { state.overCard = false; launch { delay(LEAVE_GRACE_MS); if (!state.overCard) state.hide() } }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
                .clickable {
                    val id = pageId ?: return@clickable
                    val mods = windowInfo.keyboardModifiers
                    state.hide()
                    when {
                        mods.isCtrlPressed && onOpenBeside != null -> onOpenBeside(id)
                        mods.isShiftPressed && onOpenInWindow != null -> onOpenInWindow(id)
                        else -> onOpenPage(id)
                    }
                },
        ) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (p.kind) {
                            PageKind.DATABASE -> Icons.Filled.TableChart
                            PageKind.CANVAS -> Icons.Filled.Dashboard
                            PageKind.PAGE -> Icons.Outlined.Description
                        },
                        contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(p.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.heading, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    // hover-preview-mock.md #1: text, so `onSurfaceVariant` (4.6+), never the faint token (2.8:1 on the mock).
                    Text("edited " + relativeTime(p.editedAt), style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (p.lines.isNotEmpty()) Spacer(Modifier.height(6.dp))
                p.lines.forEach { line ->
                    val marked = line.marked
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (marked) Modifier.background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(4.dp)) else Modifier),
                    ) {
                        if (marked) Box(Modifier.width(3.dp).height(18.dp).background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(2.dp)))
                        val prefix = when (line.kind) { LineKind.TODO_OPEN -> "☐ "; LineKind.TODO_DONE -> "☑ "; else -> "" }
                        Text(
                            prefix + line.text,
                            style = MaterialTheme.typography.description,
                            fontWeight = if (line.kind == LineKind.HEADING) MaterialTheme.typography.heading.fontWeight else null,
                            color = if (line.kind == LineKind.COUNT) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                val footer = when {
                    target is PreviewTarget.Reference && p.lines.any { it.marked } -> "the referenced line · click to open"
                    p.more > 0 -> {
                        val noun = when (p.kind) { PageKind.DATABASE -> "row"; PageKind.CANVAS -> "card"; PageKind.PAGE -> "block" }
                        "${p.more} more " + (if (p.more == 1) noun else noun + "s") + " · click to open"
                    }
                    p.lines.isEmpty() -> "empty page · click to open"
                    else -> null
                }
                if (footer != null) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(footer, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/** The page id the card opens and the preview it draws; null when the target resolves to nothing live. */
private suspend fun loadPreview(core: WorkbenchCore, target: PreviewTarget): Pair<Long, PagePreview>? {
    val pageDao = core.database.pageDao()
    val blockDao = core.database.blockDao()
    return when (target) {
        is PreviewTarget.Page -> {
            val page = pageDao.getById(target.pageId)?.takeIf { it.deletedAt == null } ?: return null
            if (page.kind == PageKind.DATABASE) {
                val db = core.database.pageDatabaseDao().getByPageId(page.id)
                val columns = db?.let { core.database.propertyDao().getForDatabase(it.id).sortedBy { p -> p.order }.map { p -> p.name } }.orEmpty()
                val view = db?.let { core.database.pageDatabaseViewDao().getForDatabase(it.id).firstOrNull() }
                val rows = db?.let { pageDao.observeMembersOf(it.id, it.labelId).first() }.orEmpty()
                page.id to databasePreview(page, view?.viewType?.name?.lowercase()?.replaceFirstChar { c -> c.uppercase() }, columns, rows)
            } else if (page.kind == PageKind.CANVAS) {
                val canvas = core.database.pageCanvasDao().getByPageId(page.id)
                val nodes = canvas?.let { core.database.canvasNodeDao().getForCanvas(it.id) }.orEmpty().sortedWith(compareBy({ it.y }, { it.x }))
                val cards = nodes.map { n -> n.text ?: n.embeddedPageId?.let { id -> pageDao.getById(id)?.title }?.let { "→ $it" } ?: "" }
                val links = canvas?.let { core.database.canvasEdgeDao().getForCanvas(it.id).size } ?: 0
                page.id to canvasPreview(page, cards, links)
            } else {
                page.id to pagePreview(page, blockDao.getForPage(page.id))
            }
        }
        is PreviewTarget.Reference -> {
            val source = blockDao.getByUid(target.uid)
            val pageId = source?.pageId ?: target.sourcePageId ?: return null
            val page = pageDao.getById(pageId)?.takeIf { it.deletedAt == null } ?: return null
            page.id to referencePreview(page, blockDao.getForPage(page.id), target.uid)
        }
    }
}

/** Under the anchor with [gapPx] of air, left-aligned to it; above when the window's foot is nearer; never off the window's sides. */
internal class UnderAnchor(private val anchor: Rect, private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = anchor.left.toInt().coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchor.bottom.toInt() + gapPx
        val y = if (below + popupContentSize.height <= windowSize.height) below
        else (anchor.top.toInt() - gapPx - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
