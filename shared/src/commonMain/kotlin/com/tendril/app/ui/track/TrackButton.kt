package com.tendril.app.ui.track

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.tendril.app.domain.track.TimeTracker
import com.tendril.app.domain.track.TrackTarget
import com.tendril.app.domain.track.target
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.track_start
import com.tendril.app.generated.resources.track_stop

/** What runs right now, as a target, for every row that draws a [TrackButton]. */
@Composable
fun TimeTracker.runningTargetState(): State<TrackTarget?> =
    running.map { it?.target() }.collectAsState(initial = null)

/**
 * §0.6.5 / §0.8 step 7c — the start/stop on a task or habit row. One glyph: ▶ when this is not
 * what runs, ■ when it is. Starting here stops whatever ran before ([TimeTracker.start]), so
 * there is no "already running" state to explain — the strip above the bottom bar shows which.
 */
@Composable
fun TrackButton(
    target: TrackTarget,
    runningTarget: TrackTarget?,
    onToggle: (TrackTarget) -> Unit,
    modifier: Modifier = Modifier,
    /** The glyph's size where a row draws its buttons small (the desktop's one-line rows). */
    iconModifier: Modifier = Modifier,
) {
    val isRunning = runningTarget == target
    IconButton(onClick = { onToggle(target) }, modifier = modifier) {
        if (isRunning) {
            Icon(Icons.Filled.Stop, contentDescription = stringResource(Res.string.track_stop), tint = MaterialTheme.colorScheme.primary, modifier = iconModifier)
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(Res.string.track_start), modifier = iconModifier)
        }
    }
}
