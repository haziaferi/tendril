package com.tendril.app.ui.track

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tendril.app.data.track.TimeLog
import com.tendril.app.domain.track.TrackTarget
import com.tendril.app.domain.track.target
import com.tendril.app.ui.WorkbenchCore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.track_stop
import java.time.Duration
import java.time.Instant
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.caption
import com.tendril.app.ui.theme.clock
import com.tendril.app.ui.theme.clockSmall
import com.tendril.app.ui.theme.label

/**
 * §0.6.5 / §0.8 step 7c — the "now": one strip above the bottom bar, on every route on both
 * platforms, while a timer runs. Llama Life's shape (B§10.3): the thing being done and how
 * long, nothing else. Absent when nothing runs — the strip is not a place for a button that
 * starts things; the rows are. Drawn as B§13.4 14a's mock draws it: 36 dp on `accentSoft`,
 * 13 sp, the clock tabular, *Stop* as a word at the right.
 *
 * The elapsed time is derived from the row's `startedAt` each second rather than counted, so a
 * process that was killed and restarted shows the right number the moment it draws.
 */
@Composable
fun RunningTimerBar(core: WorkbenchCore, modifier: Modifier = Modifier) {
    val timer = runningTimer(core) ?: return
    val scope = rememberCoroutineScope()
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .height(36.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val colour = MaterialTheme.colorScheme.onPrimaryContainer
            Text("▶", style = MaterialTheme.typography.body, color = colour)
            Text(timer.title ?: "…", style = MaterialTheme.typography.body, color = colour, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(formatElapsed(timer.log, timer.now), style = MaterialTheme.typography.clock, color = colour)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                stringResource(Res.string.track_stop),
                style = MaterialTheme.typography.label,
                color = colour,
                modifier = Modifier.clickable { scope.launch { core.timeTracker.stop() } }.padding(vertical = 8.dp),
            )
        }
    }
}

/**
 * B§13.4 14a — the same "now" at the foot of the rail, as the mock draws it: a hairline, then
 * "▶ title" in 10.5 sp dim text, the clock under it in the text colour, tabular; *Stop* as a
 * third small line, since the mock's foot shows no control and a timer must be stoppable from
 * where it is shown. Absent when nothing runs, as the strip is.
 */
@Composable
fun RunningTimerRailFoot(core: WorkbenchCore, modifier: Modifier = Modifier) {
    val timer = runningTimer(core) ?: return
    val scope = rememberCoroutineScope()
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "▶ " + (timer.title ?: "…"),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatElapsed(timer.log, timer.now),
                style = MaterialTheme.typography.clockSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(Res.string.track_stop),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { scope.launch { core.timeTracker.stop() } }.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}


/** The running log, its target's title, and a `now` that ticks each second — or null while nothing runs. */
private class RunningTimer(val log: TimeLog, val title: String?, val now: Instant)

@Composable
private fun runningTimer(core: WorkbenchCore): RunningTimer? {
    val running by core.timeTracker.running.collectAsState(initial = null)
    val log = running ?: return null
    val title by produceState<String?>(initialValue = null, log.uid) {
        value = when (val target = log.target()) {
            is TrackTarget.Entry -> core.database.entryDao().getById(target.id)?.title
            is TrackTarget.Habit -> core.database.habitDao().getById(target.id)?.title
        }
    }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(log.uid) {
        while (true) {
            now = Instant.now()
            delay(1_000)
        }
    }
    return RunningTimer(log, title, now)
}

/** "12:07" under an hour, "1:02:07" from then on — a clock, not a total. */
internal fun formatElapsed(log: TimeLog, now: Instant): String {
    val seconds = Duration.between(log.startedAt, now).seconds.coerceAtLeast(0)
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    val mm = m.toString().padStart(2, '0'); val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:$mm:$ss" else "$m:$ss"
}
