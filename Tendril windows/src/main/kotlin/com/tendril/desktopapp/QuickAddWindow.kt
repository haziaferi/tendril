package com.tendril.desktopapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.domain.quickAddEntry
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.entries.QuickAddField
import com.tendril.app.ui.nav.LocalSystemTextScale
import com.tendril.app.ui.nav.WorkbenchEnvironment
import com.tendril.app.ui.theme.TendrilTheme
import com.tendril.app.ui.theme.caption
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.resolveDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Whether the chord's popup is up; the hotkey thread and the tray item set it, Enter and Esc clear it. */
internal class QuickAddState {
    var open: Boolean by mutableStateOf(false)
}

private val WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * B§13.6 #7 — the quick-add popup (`docs/mockups/tray.html` frame C; `docs/critiques/tray-mock.md`
 * #1–#3): 560 dp at 22 % of the screen's height, always on top, no title bar, the hover card's
 * radius; the same line and chips as the Calendar's strip (`QuickAddField`) behind the accent's
 * `+`; Enter writes and shows *Added* for 700 ms, Esc closes; focus lost with the line blank
 * closes, with text keeps it (a draft is not thrown away). A line with no kind token is a TASK
 * here (the strip's default is EVENT — it is the Calendar's). Composed only while open, so every
 * open is a fresh line. Draws at the main window's scale — one scale per app (the pop-outs' rule).
 */
@Composable
internal fun QuickAddWindow(core: WorkbenchCore, state: QuickAddState, main: MainWindowActions) {
    if (!state.open) return
    val screen = remember { Toolkit.getDefaultToolkit().screenSize }
    val windowState = rememberWindowState(
        size = DpSize((WIDTH + 2 * SHADOW).dp, 230.dp),
        position = WindowPosition.Absolute(((screen.width - WIDTH - 2 * SHADOW) / 2).dp, (screen.height * 0.22f).dp),
    )
    var text by remember { mutableStateOf("") }
    var added by remember { mutableStateOf<Entry?>(null) }
    /** Audit 1.8 — "a write has started", which is what the one-write guard needs and what
     *  [added] cannot say until the database has answered. Composed only while the popup is
     *  open, so every open starts false without anything having to reset it. */
    var writing by remember { mutableStateOf(false) }
    Window(
        onCloseRequest = { state.open = false },
        state = windowState,
        title = "Quick add — Tendril",
        icon = painterResource("tendril_icon.png"),
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) { state.open = false; true } else false
        },
    ) {
        LaunchedEffect(Unit) { window.toFront(); window.requestFocus() }
        DisposableEffect(Unit) {
            val listener = object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent?) {}
                override fun windowLostFocus(e: WindowEvent?) { if (text.isBlank() && added == null) state.open = false }
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }
        val theme = core.themeSettings.observe()
        val scope = rememberCoroutineScope()
        TendrilTheme(register = theme.register, dark = theme.mode.resolveDark(), typeface = theme.typeface) {
            CompositionLocalProvider(LocalSystemTextScale provides main.systemTextScale) {
            WorkbenchEnvironment(core, shorterSideDp = main.shorterSideDp, wide = true) {
                Box(modifier = Modifier.padding(SHADOW.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shadowElevation = 24.dp,
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    ) {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp)) {
                            QuickAddField(
                                today = LocalDate.now(),
                                onQuickAdd = { parsed ->
                                    // One write per open, guarded by a flag set *here* — on the UI
                                    // thread, before the coroutine starts — rather than by `added`.
                                    //
                                    // Audit 1.8: `added` is only assigned after `quickAddEntry`
                                    // returns from the database, and both Enters of a double tap
                                    // are delivered to this lambda inside that round trip, so both
                                    // saw it null and both launched. Walked 2026-09-22: five
                                    // attempts, five pairs of duplicate entries, 0–3 ms apart. The
                                    // same test through `SendKeys.SendWait`, which waits for each
                                    // keystroke, wrote one — the guard only ever held when the
                                    // second Enter happened to land after the write finished.
                                    //
                                    // `added` stays as it was: it is the *Added* flash's state,
                                    // and what it means (a write completed) is not what a guard
                                    // needs to know (a write started).
                                    if (!writing) {
                                        writing = true
                                        scope.launch {
                                            val entry = quickAddEntry(core.database.entryDao(), core.entryScheduleCoordinator, parsed, LocalDate.now())
                                            added = entry
                                            delay(700)
                                            state.open = false
                                        }
                                    }
                                },
                                placeholder = "Dentist fri 14:30 !",
                                defaultKind = EntryKind.TASK,
                                fieldHeight = 36.dp, // L7 — the switcher's field; the chips under it
                                leadingIcon = { Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                                previewModifier = Modifier.padding(top = 8.dp),
                                onTextChanged = { text = it },
                            )
                            val done = added
                            if (done == null) {
                                Text("Enter adds · Esc closes", style = MaterialTheme.typography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                                    Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    Text(" Added · ${done.title}${whenOf(done)}", style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

private fun whenOf(e: Entry): String {
    val date = e.startDate ?: return ""
    val time = e.startTime?.let { " ${it.format(CLOCK)}" } ?: ""
    return " · ${date.format(WHEN)}$time"
}

private const val WIDTH = 560
private const val SHADOW = 24
