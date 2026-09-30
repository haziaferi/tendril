package com.tendril.app.domain.plan.export

import com.tendril.app.domain.plan.DayBlock
import com.tendril.app.domain.plan.DayRow
import com.tendril.app.domain.plan.HabitDayView
import com.tendril.app.domain.plan.HabitWeekView
import com.tendril.app.domain.plan.OccurrenceTag
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale

/*
 * §6.3 (plan Phase 6, D11) — the week's plan as a file: Markdown (the week by time, then each day by
 * time and by area) and A4 PDF (a portrait sheet per day, a landscape sheet for the week). Everything
 * comes from the [HabitWeekView] the Week view draws, so an export cannot say something the screen
 * does not. It is a plan sheet (E3): check-ins are not marked, and nothing is marked as missed
 * (§0.5.2). Durations and a total per day are shown (E2); block load against capacity is not (D4).
 *
 * Pure: the words arrive already in the app's language ([PlanWords]), so every line is a test.
 */

/** The export's words in the app's language, templates keeping their `%1$s` places; [locale] formats the dates (L2). */
data class PlanWords(
    val title: String,
    val weekLabel: String,
    val byTime: String,
    val byArea: String,
    val anyTimeToday: String,
    val outsideBlocks: String,
    val noArea: String,
    val nothingPlanned: String,
    val total: String,
    val expected: String,
    val overlapNote: String,
    val outsideBeforeNote: String,
    val outsideAfterNote: String,
    val locale: Locale,
)

/** What the blocks are called (a default block in the app's language, H1) and the Labels as areas, in their order (P4). */
data class PlanNames(val blocks: Map<String, String>, val labels: List<Pair<Long, String>>)

/** A duration as the Habits tab writes one: `10 min`, `1 h`, `1 h 10 min`. */
fun planDuration(minutes: Int): String = when {
    minutes % 60 == 0 && minutes > 0 -> "${minutes / 60} h"
    minutes > 60 -> "${minutes / 60} h ${minutes % 60} min"
    else -> "$minutes min"
}

private fun clock(minute: Int): String = "%02d:%02d".format(Locale.ROOT, minute / 60, minute % 60)

private fun span(start: Int, end: Int) = clock(start) + "–" + clock(end)

/** The entry's minutes: the occurrence's own (an edit may have changed them), else the habit's duration; null when neither says. */
private fun DayRow.minutes(): Int? = occurrence?.minutes?.takeIf { it > 0 } ?: habit.duration?.toMinutes()?.toInt()?.takeIf { it > 0 }

/** `Water (1/3)`: which of the day's (or week's) several it is, as the planner wrote it. */
private fun DayRow.name(): String = when (val t = occurrence?.tag) {
    is OccurrenceTag.Count -> "${habit.title} (${t.i}/${t.n})"
    is OccurrenceTag.Week -> "${habit.title} (${t.i}/${t.n})"
    else -> habit.title
}

private fun HabitDayView.rows(): List<DayRow> = blocks.flatMap { it.timed + it.flexible } + anyTime + outside

private fun HabitDayView.totalMinutes(): Int = rows().sumOf { it.minutes() ?: 0 }

/** Where a row sits, by name: its block, *Any time today*, or *Outside the blocks*. */
private fun placeOf(view: HabitDayView, row: DayRow, words: PlanWords, names: PlanNames): String {
    view.blocks.firstOrNull { b -> row in b.timed || row in b.flexible }?.let { return blockName(it, names) }
    return if (row in view.anyTime) words.anyTimeToday else words.outsideBlocks
}

private fun blockName(b: DayBlock, names: PlanNames) = names.blocks[b.block.uid] ?: b.block.name ?: ""

private fun dayHeading(view: HabitDayView, words: PlanWords): String =
    view.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", words.locale)).replaceFirstChar { it.titlecase(words.locale) }

private fun weekHeading(week: HabitWeekView, words: PlanWords): String {
    val fmt = DateTimeFormatter.ofPattern("d MMM", words.locale)
    return String.format(words.locale, words.weekLabel, week.monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR), week.monday.format(fmt), week.monday.plusDays(6).format(fmt))
}

/** D4 — the day's structural notes, neutral: blocks that overlap, and a set time outside every block. */
private fun notesOf(view: HabitDayView, words: PlanWords, names: PlanNames): List<String> {
    val byUid = view.blocks.associateBy { it.block.uid }
    val overlaps = view.overlaps.mapNotNull { o ->
        val a = byUid[o.first] ?: return@mapNotNull null
        val b = byUid[o.second] ?: return@mapNotNull null
        String.format(words.locale, words.overlapNote, blockName(a, names), span(a.start, a.end), blockName(b, names), span(b.start, b.end), planDuration(a.end - b.start))
    }
    val first = view.blocks.minByOrNull { it.start }
    val last = view.blocks.maxByOrNull { it.end }
    val outside = view.outside.mapNotNull { row ->
        val t = row.time ?: return@mapNotNull null
        when {
            first != null && t < first.start -> String.format(words.locale, words.outsideBeforeNote, row.habit.title, clock(t), blockName(first, names), clock(first.start))
            last != null -> String.format(words.locale, words.outsideAfterNote, row.habit.title, clock(t), blockName(last, names), clock(last.end))
            else -> null
        }
    }
    return overlaps + outside
}

/** P4 — the day's entries by their habit's Label, in the Labels' order; *No area* last (a Label no longer known counts as none). */
private fun areaGroups(view: HabitDayView, words: PlanWords, names: PlanNames): List<Pair<String, List<DayRow>>> {
    val known = names.labels.toMap()
    val byLabel = view.rows().groupBy { row -> row.habit.labelId?.takeIf { it in known } }
    val order = compareBy<DayRow, Int?>(nullsLast()) { it.time }.thenBy { it.habit.title.lowercase(words.locale) }
    val labelled = names.labels.mapNotNull { (id, name) -> byLabel[id]?.let { name to it.sortedWith(order) } }
    val none = byLabel[null]?.let { listOf(words.noArea to it.sortedWith(order)) }.orEmpty()
    return labelled + none
}

// ---------------------------------------------------------------- Markdown

/** One line's text: `07:30 · Stretch · 10 min · _expected_ — _note_`. */
private fun mdEntry(row: DayRow, words: PlanWords): String = buildString {
    row.time?.let { append(clock(it)).append(" · ") }
    append(row.name())
    row.minutes()?.let { append(" · ").append(planDuration(it)) }
    if (row.projected) append(" · _").append(words.expected).append('_')
    row.habit.note?.takeIf { it.isNotBlank() }?.let { append(" — _").append(it.trim()).append('_') }
}

private fun cell(text: String) = text.replace("|", "/").replace("\n", " ")

/** The week as one Markdown file. */
fun planMarkdown(week: HabitWeekView, words: PlanWords, names: PlanNames): String {
    val out = StringBuilder()
    fun line(s: String = "") { out.append(s).append('\n') }
    line("# ${words.title}"); line()
    line("**${weekHeading(week, words)}**"); line()

    // The week by time: a row per block, set times first in each cell (the Week view as a table).
    line("## ${words.byTime}"); line()
    val dayFmt = DateTimeFormatter.ofPattern("EEE d", words.locale)
    line("| | " + week.days.joinToString(" | ") { cell(it.date.format(dayFmt)) } + " |")
    line("|---".repeat(week.days.size + 1) + "|")
    fun tableEntry(r: DayRow) = cell((r.time?.let { clock(it) + " " } ?: "") + r.name() + if (r.projected) " (${words.expected})" else "")
    val blockUids = week.days.flatMap { d -> d.blocks.map { it.block.uid } }.distinct()
    for (uid in blockUids) {
        val any = week.days.firstNotNullOfOrNull { d -> d.blocks.firstOrNull { it.block.uid == uid } } ?: continue
        line("| **${cell(blockName(any, names))}** | " + week.days.joinToString(" | ") { d ->
            d.blocks.firstOrNull { it.block.uid == uid }?.let { b -> (b.timed + b.flexible).joinToString("<br>") { tableEntry(it) } }.orEmpty().ifEmpty { " " }
        } + " |")
    }
    if (week.days.any { it.anyTime.isNotEmpty() }) line("| **${cell(words.anyTimeToday)}** | " + week.days.joinToString(" | ") { d -> d.anyTime.joinToString("<br>") { tableEntry(it) }.ifEmpty { " " } } + " |")
    if (week.days.any { it.outside.isNotEmpty() }) line("| **${cell(words.outsideBlocks)}** | " + week.days.joinToString(" | ") { d -> d.outside.joinToString("<br>") { tableEntry(it) }.ifEmpty { " " } } + " |")
    if (week.days.any { it.totalMinutes() > 0 }) {
        line("| **${cell(words.total)}** | " + week.days.joinToString(" | ") { d -> d.totalMinutes().takeIf { it > 0 }?.let(::planDuration) ?: " " } + " |")
    }
    line()

    for (day in week.days) {
        line("## ${dayHeading(day, words)}"); line()
        line("### ${words.byTime}"); line()
        for (b in day.blocks) {
            line("**${blockName(b, names)}** · ${span(b.start, b.end)}"); line()
            val rows = b.timed + b.flexible
            if (rows.isEmpty()) line("- _${words.nothingPlanned}_") else rows.forEach { line("- " + mdEntry(it, words)) }
            line()
        }
        if (day.anyTime.isNotEmpty()) { line("**${words.anyTimeToday}**"); line(); day.anyTime.forEach { line("- " + mdEntry(it, words)) }; line() }
        if (day.outside.isNotEmpty()) { line("**${words.outsideBlocks}**"); line(); day.outside.forEach { line("- " + mdEntry(it, words)) }; line() }

        line("### ${words.byArea}"); line()
        val groups = areaGroups(day, words, names)
        if (groups.isEmpty()) { line("_${words.nothingPlanned}_"); line() }
        for ((name, rows) in groups) {
            line("**$name**"); line()
            rows.forEach { line("- " + mdEntry(it, words) + " (" + placeOf(day, it, words, names) + ")") }
            line()
        }
        day.totalMinutes().takeIf { it > 0 }?.let { line("${words.total}: ${planDuration(it)}"); line() }
        notesOf(day, words, names).forEach { line("> $it"); line() }
    }
    return out.toString().trimEnd() + "\n"
}

// ---------------------------------------------------------------- PDF

private const val INK = 0x1F2328
private const val MUTED = 0x5F6673
private const val RULE = 0xD5D8DE
private const val ACCENT = 0x4A5568 // Ink's accent (Registers.INK, light)
private const val HEAD_FILL = 0xF1F2F4

/** [text] cut to fit [maxWidth] points, with an ellipsis when it had to be cut. */
private fun fit(text: String, font: PdfFont, size: Float, maxWidth: Float): String {
    if (textWidth(text, font, size) <= maxWidth) return text
    var end = text.length
    while (end > 0 && textWidth(text.substring(0, end).trimEnd() + "…", font, size) > maxWidth) end--
    return text.substring(0, end).trimEnd() + "…"
}

/** [text] broken into lines no wider than [maxWidth]; a word longer than a line is cut to fit. */
private fun wrap(text: String, font: PdfFont, size: Float, maxWidth: Float): List<String> {
    val lines = mutableListOf<String>()
    var current = ""
    for (word in text.split(' ').filter { it.isNotEmpty() }) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (textWidth(candidate, font, size) <= maxWidth) current = candidate
        else { if (current.isNotEmpty()) lines += current; current = fit(word, font, size, maxWidth) }
    }
    if (current.isNotEmpty()) lines += current
    return lines
}

/** Pages collected as drawing steps, so the footer can say "page n of N" once N is known. */
private class Sheets(val width: Float, val height: Float, val margin: Float) {
    val pages = mutableListOf<MutableList<PdfCanvas.() -> Unit>>()
    var y = 0f
    val bottom get() = height - margin - 18f
    fun newPage(header: Sheets.() -> Unit) { pages.add(mutableListOf()); y = margin; header() }
    fun draw(step: PdfCanvas.() -> Unit) { pages.last().add(step) }
    fun room(h: Float) = y + h <= bottom

    fun write(footerLeft: String): ByteArray {
        val w = PdfWriter()
        pages.forEachIndexed { i, steps ->
            w.page(width, height) {
                steps.forEach { it() }
                val fy = height - margin + 4f
                line(margin, fy - 12f, width - margin, fy - 12f, 0.5f, RULE)
                text(margin, fy, fit(footerLeft, PdfFont.REGULAR, 7.5f, width / 2), PdfFont.REGULAR, 7.5f, MUTED)
                val n = "${i + 1} / ${pages.size}"
                text(width - margin - textWidth(n, PdfFont.REGULAR, 7.5f), fy, n, PdfFont.REGULAR, 7.5f, MUTED)
            }
        }
        return w.toByteArray()
    }
}

/** Seven A4 portrait sheets, one per day of the week (more when a day runs over a page): by time, then by area. */
fun planDailyPdf(week: HabitWeekView, words: PlanWords, names: PlanNames): ByteArray {
    val s = Sheets(A4_WIDTH, A4_HEIGHT, 42f)
    val left = s.margin
    val right = s.width - s.margin
    val rowsX = left + 128f
    val weekLine = weekHeading(week, words)
    for (day in week.days) {
        val title = dayHeading(day, words)
        val header: Sheets.() -> Unit = {
            val top = y
            draw { text(left, top + 8f, weekLine, PdfFont.REGULAR, 8.5f, MUTED) }
            draw { text(left, top + 30f, title, PdfFont.BOLD, 18f, INK) }
            draw { line(left, top + 40f, right, top + 40f, 0.75f, ACCENT) }
            y = top + 58f
        }
        s.newPage(header)

        /** A group — a block, *Any time today*, *Outside the blocks*, an area — its name at the left, its rows at the right. */
        fun group(name: String, sub: String?, rows: List<DayRow>, place: ((DayRow) -> String)?) {
            val lines = rows.ifEmpty { listOf(null) }
            var first = true
            for (row in lines) {
                if (!s.room(15f)) { s.newPage(header); first = true }
                val top = s.y
                if (first) {
                    s.draw { text(left, top + 10f, fit(name, PdfFont.BOLD, 10.5f, 120f), PdfFont.BOLD, 10.5f, INK) }
                    if (sub != null) s.draw { text(left, top + 22f, sub, PdfFont.REGULAR, 8.5f, MUTED) }
                    first = false
                }
                if (row == null) {
                    s.draw { text(rowsX, top + 10f, words.nothingPlanned, PdfFont.OBLIQUE, 9.5f, MUTED) }
                } else {
                    val dur = listOfNotNull(row.minutes()?.let(::planDuration), place?.invoke(row)).joinToString(" · ")
                    val durW = textWidth(dur, PdfFont.REGULAR, 8.5f)
                    val nameX = rowsX + 40f
                    val face = if (row.projected) PdfFont.OBLIQUE else PdfFont.REGULAR
                    val label = row.name() + if (row.projected) " (${words.expected})" else ""
                    s.draw {
                        row.time?.let { text(rowsX, top + 10f, clock(it), PdfFont.BOLD, 9f, ACCENT) }
                        text(nameX, top + 10f, fit(label, face, 10f, right - nameX - durW - 10f), face, 10f, INK)
                        if (dur.isNotEmpty()) text(right - durW, top + 10f, dur, PdfFont.REGULAR, 8.5f, MUTED)
                    }
                }
                s.y += 15f
            }
            if (sub != null && rows.size < 2) s.y += 12f // room for the times under the name
            val sep = s.y + 3f
            s.draw { line(left, sep, right, sep, 0.5f, RULE) }
            s.y += 10f
        }

        for (b in day.blocks) group(blockName(b, names), span(b.start, b.end), b.timed + b.flexible, null)
        if (day.anyTime.isNotEmpty()) group(words.anyTimeToday, null, day.anyTime, null)
        if (day.outside.isNotEmpty()) group(words.outsideBlocks, null, day.outside, null)

        val areas = areaGroups(day, words, names)
        if (areas.isNotEmpty()) {
            if (!s.room(40f)) s.newPage(header)
            val top = s.y + 8f
            s.draw { text(left, top + 12f, words.byArea, PdfFont.BOLD, 12f, INK) }
            s.y = top + 24f
            for ((name, rows) in areas) group(name, null, rows) { placeOf(day, it, words, names) }
        }

        day.totalMinutes().takeIf { it > 0 }?.let { total ->
            if (!s.room(20f)) s.newPage(header)
            val top = s.y
            s.draw { text(left, top + 10f, "${words.total}: ${planDuration(total)}", PdfFont.BOLD, 10f, INK) }
            s.y += 20f
        }
        for (note in notesOf(day, words, names)) {
            for (l in wrap(note, PdfFont.REGULAR, 8.5f, right - left)) {
                if (!s.room(12f)) s.newPage(header)
                val top = s.y
                s.draw { text(left, top + 9f, l, PdfFont.REGULAR, 8.5f, MUTED) }
                s.y += 12f
            }
            s.y += 4f
        }
    }
    return s.write("${words.title} · $weekLine")
}

/** One A4 landscape sheet for the week (more if the table runs over): the blocks down, the days across. */
fun planWeeklyPdf(week: HabitWeekView, words: PlanWords, names: PlanNames): ByteArray {
    val s = Sheets(A4_HEIGHT, A4_WIDTH, 36f)
    val left = s.margin
    val right = s.width - s.margin
    val firstCol = 96f
    val colW = (right - left - firstCol) / week.days.size
    val weekLine = weekHeading(week, words)
    val dayFmt = DateTimeFormatter.ofPattern("EEE d", words.locale)
    val lineH = 10f

    val tableHeader: Sheets.() -> Unit = {
        val top = y
        draw { text(left, top + 8f, words.title, PdfFont.REGULAR, 8.5f, MUTED) }
        draw { text(left, top + 28f, weekLine, PdfFont.BOLD, 16f, INK) }
        val h = top + 40f
        draw { rect(left, h, right - left, 18f, HEAD_FILL) }
        week.days.forEachIndexed { i, d ->
            val x = left + firstCol + i * colW
            draw { text(x + 4f, h + 12.5f, d.date.format(dayFmt), PdfFont.BOLD, 9f, INK) }
        }
        y = h + 18f
    }
    s.newPage(tableHeader)

    /** A cell's line: its text, whether it is a projected entry (T3 — said in words and drawn as a guess), and whether it is the day's own block times. */
    class CellLine(val text: String, val projected: Boolean, val times: Boolean = false)

    /** An entry as up to two lines of its column: a long name wraps once, then is cut. */
    fun tableEntry(r: DayRow): List<CellLine> {
        val text = (r.time?.let { clock(it) + " " } ?: "") + r.name() + if (r.projected) " (${words.expected})" else ""
        val face = if (r.projected) PdfFont.OBLIQUE else PdfFont.REGULAR
        val lines = wrap(text, face, 7.5f, colW - 8f)
        val two = if (lines.size <= 2) lines else listOf(lines[0], fit(lines.drop(1).joinToString(" "), face, 7.5f, colW - 8f))
        return two.map { CellLine(it, r.projected) }
    }

    /** One row of the table; [cells] per day, and [ownTimes] a day's block times where they differ from the row's [sub]. */
    fun tableRow(name: String, sub: String?, cells: List<List<DayRow>>, bold: Boolean = false, text: List<String>? = null, ownTimes: List<String?> = emptyList()) {
        val cellLines = text?.map { listOf(CellLine(it, false)) } ?: cells.mapIndexed { i, rows ->
            listOfNotNull(ownTimes.getOrNull(i)?.let { CellLine(it, false, times = true) }) + rows.flatMap { tableEntry(it) }
        }
        val n = maxOf(if (sub != null) 2 else 1, cellLines.maxOfOrNull { it.size } ?: 0)
        val h = n * lineH + 8f
        if (!s.room(h)) s.newPage(tableHeader)
        val top = s.y
        s.draw { text(left + 4f, top + 11f, fit(name, PdfFont.BOLD, 8.5f, firstCol - 8f), PdfFont.BOLD, 8.5f, INK) }
        if (sub != null) s.draw { text(left + 4f, top + 11f + lineH, sub, PdfFont.REGULAR, 7f, MUTED) }
        cellLines.forEachIndexed { i, lines ->
            val x = left + firstCol + i * colW + 4f
            lines.forEachIndexed { j, l ->
                val face = if (bold) PdfFont.BOLD else if (l.projected) PdfFont.OBLIQUE else PdfFont.REGULAR
                val size = if (l.times) 7f else 7.5f
                s.draw { text(x, top + 11f + j * lineH, l.text, face, size, if (l.projected || l.times) MUTED else INK) }
            }
        }
        s.draw { line(left, top + h, right, top + h, 0.5f, RULE) }
        s.y += h
    }

    val blockUids = week.days.flatMap { d -> d.blocks.map { it.block.uid } }.distinct()
    for (uid in blockUids) {
        val perDay = week.days.map { d -> d.blocks.firstOrNull { it.block.uid == uid } }
        val any = perDay.firstNotNullOfOrNull { it } ?: continue
        // A weekday change moves a block on some days (the phone walk: Dawn later at the weekend). The
        // row says the times most days have; a day that differs says its own at the top of its cell.
        val rowSpan = perDay.filterNotNull().groupingBy { span(it.start, it.end) }.eachCount().maxByOrNull { it.value }!!.key
        tableRow(
            blockName(any, names), rowSpan,
            perDay.map { b -> b?.let { it.timed + it.flexible }.orEmpty() },
            ownTimes = perDay.map { b -> b?.let { span(it.start, it.end) }?.takeIf { it != rowSpan } },
        )
    }
    if (week.days.any { it.anyTime.isNotEmpty() }) tableRow(words.anyTimeToday, null, week.days.map { it.anyTime })
    if (week.days.any { it.outside.isNotEmpty() }) tableRow(words.outsideBlocks, null, week.days.map { it.outside })
    if (week.days.any { it.totalMinutes() > 0 }) {
        tableRow(words.total, null, week.days.map { emptyList() }, bold = true, text = week.days.map { d -> d.totalMinutes().takeIf { it > 0 }?.let(::planDuration).orEmpty() })
    }

    val notes = week.days.flatMap { notesOf(it, words, names) }.distinct()
    if (notes.isNotEmpty()) {
        s.y += 10f
        for (note in notes) for (l in wrap(note, PdfFont.REGULAR, 8f, right - left)) {
            if (!s.room(11f)) s.newPage(tableHeader)
            val top = s.y
            s.draw { text(left, top + 8f, l, PdfFont.REGULAR, 8f, MUTED) }
            s.y += 11f
        }
    }
    return s.write(words.title)
}
