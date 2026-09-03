package com.tendril.app.notionimport

/**
 * §7.4 — a small hand-rolled RFC4180 parser rather than a library dependency: the grammar
 * (quoted fields, embedded commas/newlines, doubled-quote escaping) is compact enough that a
 * dependency for it isn't worth this app's near-zero-non-AndroidX-dependency posture.
 */
object NotionCsvParser {
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        var sawAnyField = false

        fun endField() {
            row.add(field.toString())
            field.clear()
        }

        fun endRow() {
            endField()
            rows.add(row)
            row = mutableListOf()
            sawAnyField = false
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> { inQuotes = true; sawAnyField = true }
                    ',' -> { sawAnyField = true; endField() }
                    '\r' -> { /* skip; \n (bare or following \r) ends the row */ }
                    '\n' -> { sawAnyField = true; endRow() }
                    else -> { field.append(c); sawAnyField = true }
                }
            }
            i++
        }
        // A trailing field/row with no final newline is still real data.
        if (sawAnyField || field.isNotEmpty() || row.isNotEmpty()) endRow()

        return rows
    }
}
