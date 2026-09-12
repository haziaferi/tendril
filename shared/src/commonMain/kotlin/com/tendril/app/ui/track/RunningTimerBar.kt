package com.tendril.app.ui.track

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/**
 * §0.6.5 / §0.8 step 7c — the "now": one line above the bottom bar, on every route on both
 * platforms, while a timer runs. Llama Life's shape (B§10.3): the thing being done and how
 * long, nothing else. Absent when nothing runs — the strip is not a place for a button that
 * starts things; the rows are.
 *
 * The elapsed time is derived from the row's `startedAt` each second rather than counted, so a
 * process that was killed and restarted shows the right number the moment it draws.
 */
@Composable
fun RunningTimerBar(core: WorkbenchCore, modifier: Modifier = Modifier) {
    val running by core.timeTracker.running.collectAsState(initial = null)
    val log = running ?: return
    val scope = rememberCoroutineScope()
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
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title ?: "…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatElapsed(log, now),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            IconButton(onClick = { scope.launch { core.timeTracker.stop() } }) {
                Icon(Icons.Filled.Stop, contentDescription = stringResource(Res.string.track_stop), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/** "12:07" under an hour, "1:02:07" from then on — a clock, not a total. */
internal fun formatElapsed(log: TimeLog, now: Instant): String {
    val seconds = Duration.between(log.startedAt, now).seconds.coerceAtLeast(0)
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    val mm = m.toString().padStart(2, '0'); val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:$mm:$ss" else "$m:$ss"
}
