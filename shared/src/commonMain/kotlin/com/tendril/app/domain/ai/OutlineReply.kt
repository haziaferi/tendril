package com.tendril.app.domain.ai

/**
 * §0.6.15's fourth verb (2026-09-22) — a *Mind map* reply is a nested list, and §0.6.2 says a mind
 * map is a rendering of one, so the parse is the whole model: each line's marker and indent give
 * a depth, the text after the marker is the row. Tolerant of what a model writes — `-`, `*`, `•`,
 * `1.` / `1)` markers, tabs or spaces (two a level, but any consistent step) — and strict about
 * nothing else: unmarked lines are rows at their indent, blank lines are skipped, a preamble
 * before the first marked line is dropped, depths are normalised so the first row is the root
 * and no row is deeper than its parent + 1.
 */
data class OutlineRow(val depth: Int, val text: String)

private val MARKER = Regex("""^([-*•+]|\d+[.)])\s+""")

fun parseOutline(reply: String): List<OutlineRow> {
    val raw = reply.lines().mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val indent = line.takeWhile { it == ' ' || it == '\t' }.sumOf { if (it == '\t') 2 else 1 }
        val marked = MARKER.containsMatchIn(line.trim())
        val text = MARKER.replace(line.trim(), "").trim().trimEnd(':')
        if (text.isEmpty()) null else Triple(indent, text, marked)
    }.let { lines -> if (lines.any { it.third }) lines.dropWhile { !it.third } else lines }
    if (raw.isEmpty()) return emptyList()
    // The indent step is the smallest non-zero indent seen (two spaces, a tab, four spaces…).
    val base = raw.first().first
    val step = raw.map { it.first - base }.filter { it > 0 }.minOrNull() ?: 1
    val rows = mutableListOf<OutlineRow>()
    for ((indent, text) in raw.map { it.first to it.second }) {
        val level = ((indent - base).coerceAtLeast(0) / step)
        val depth = minOf(level, (rows.lastOrNull()?.depth ?: -1) + 1)
        rows += OutlineRow(depth, text)
    }
    return rows
}
