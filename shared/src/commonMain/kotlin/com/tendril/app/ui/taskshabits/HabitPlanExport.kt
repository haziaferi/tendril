package com.tendril.app.ui.taskshabits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tendril.app.data.page.Label
import com.tendril.app.domain.plan.export.PlanNames
import com.tendril.app.domain.plan.export.PlanWords
import com.tendril.app.domain.plan.export.planDailyPdf
import com.tendril.app.domain.plan.export.planMarkdown
import com.tendril.app.domain.plan.export.planWeeklyPdf
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.plan_any_time_today
import com.tendril.app.generated.resources.plan_by_area
import com.tendril.app.generated.resources.plan_by_time
import com.tendril.app.generated.resources.plan_export_body
import com.tendril.app.generated.resources.plan_export_doc_title
import com.tendril.app.generated.resources.plan_export_failed
import com.tendril.app.generated.resources.plan_export_markdown
import com.tendril.app.generated.resources.plan_export_pdf_days
import com.tendril.app.generated.resources.plan_export_pdf_week
import com.tendril.app.generated.resources.plan_export_title
import com.tendril.app.generated.resources.plan_export_total
import com.tendril.app.generated.resources.plan_no_area
import com.tendril.app.generated.resources.plan_note_outside_after
import com.tendril.app.generated.resources.plan_note_outside_before
import com.tendril.app.generated.resources.plan_note_overlap
import com.tendril.app.generated.resources.plan_nothing_planned
import com.tendril.app.generated.resources.plan_outside_blocks
import com.tendril.app.generated.resources.plan_projected
import com.tendril.app.generated.resources.plan_week_label
import com.tendril.app.ui.components.SaveResult
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.ui.components.rememberFileSaver
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.util.Locale

/*
 * §6.3 (plan Phase 6, D11) — *Export…* in the Day and Week headers: the shown week as Markdown, as a
 * PDF sheet per day (A4 portrait) or as one for the week (A4 landscape), saved where the person
 * chooses. The files are made by `domain/plan/export` from the view the Week view draws, in the
 * app's language (L2).
 */

private enum class PlanExportKind(val suffix: String, val mime: String) {
    MARKDOWN(".md", "text/markdown"),
    DAYS_PDF("-days.pdf", "application/pdf"),
    WEEK_PDF("-week.pdf", "application/pdf"),
}

/** The export's words, read now in the app's language — the same strings the Habits tab shows. */
private suspend fun planWords(): PlanWords = PlanWords(
    title = getString(Res.string.plan_export_doc_title),
    weekLabel = getString(Res.string.plan_week_label),
    byTime = getString(Res.string.plan_by_time),
    byArea = getString(Res.string.plan_by_area),
    anyTimeToday = getString(Res.string.plan_any_time_today),
    outsideBlocks = getString(Res.string.plan_outside_blocks),
    noArea = getString(Res.string.plan_no_area),
    nothingPlanned = getString(Res.string.plan_nothing_planned),
    total = getString(Res.string.plan_export_total),
    expected = getString(Res.string.plan_projected),
    overlapNote = getString(Res.string.plan_note_overlap),
    outsideBeforeNote = getString(Res.string.plan_note_outside_before),
    outsideAfterNote = getString(Res.string.plan_note_outside_after),
    locale = Locale.getDefault(),
)

/**
 * The export's launcher: call it with any date of the week to export. [labels] are the areas, in the
 * order the Day view by area uses (by name).
 */
@Composable
internal fun rememberPlanExport(viewModel: TasksHabitsViewModel, labels: List<Label>): (LocalDate) -> Unit {
    var monday by remember { mutableStateOf<LocalDate?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val save = rememberFileSaver { result ->
        busy = false
        failed = result == SaveResult.FAILED
        if (result == SaveResult.SAVED) monday = null
    }

    monday?.let { m ->
        TendrilSheet(onDismiss = { if (!busy) { monday = null; failed = false } }, title = stringResource(Res.string.plan_export_title)) {
            Text(stringResource(Res.string.plan_export_body), style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant) // type: EXPLAINER — a plan sheet, no check-ins
            Spacer(Modifier.size(8.dp))
            for (kind in PlanExportKind.entries) {
                val label = when (kind) {
                    PlanExportKind.MARKDOWN -> Res.string.plan_export_markdown
                    PlanExportKind.DAYS_PDF -> Res.string.plan_export_pdf_days
                    PlanExportKind.WEEK_PDF -> Res.string.plan_export_pdf_week
                }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !busy, role = Role.Button) {
                        busy = true; failed = false
                        scope.launch {
                            val bytes = runCatching {
                                val week = viewModel.planWeek(m)
                                val words = planWords()
                                val blocks = week.days.flatMap { d -> d.blocks.map { it.block } }.distinctBy { it.uid }
                                val names = PlanNames(
                                    blocks = blocks.associate { b -> b.uid to (b.name ?: defaultBlockName(b.uid)?.let { getString(it) } ?: "") },
                                    labels = labels.sortedBy { it.name.lowercase() }.map { it.id to it.name },
                                )
                                withContext(Dispatchers.Default) {
                                    when (kind) {
                                        PlanExportKind.MARKDOWN -> planMarkdown(week, words, names).toByteArray(Charsets.UTF_8)
                                        PlanExportKind.DAYS_PDF -> planDailyPdf(week, words, names)
                                        PlanExportKind.WEEK_PDF -> planWeeklyPdf(week, words, names)
                                    }
                                }
                            }.getOrNull()
                            if (bytes == null) { busy = false; failed = true; return@launch }
                            val name = "habit-plan-%d-W%02d".format(Locale.ROOT, m.get(IsoFields.WEEK_BASED_YEAR), m.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)) + kind.suffix
                            save(name, kind.mime, bytes)
                        }
                    }.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(label), style = MaterialTheme.typography.body, modifier = Modifier.weight(1f)) // type: BODY_LINE — one export
                }
            }
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(top = 4.dp))
            if (failed) Text(stringResource(Res.string.plan_export_failed), style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant) // type: EXPLAINER — the save did not happen
        }
    }
    return { date -> monday = com.tendril.app.domain.plan.mondayOf(date); failed = false }
}
