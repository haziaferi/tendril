package com.tendril.app.notionimport

import com.tendril.app.data.pagedatabase.PropertyType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * §7.3.4/§7.4 — CSV cells are all strings natively; this is the heuristic half of "hybrid infer
 * then confirm." A guess here is never final — [NotionImporter] always routes the guessed
 * schema through the same property-binding confirmation step §5.2 already built, so a wrong
 * guess degrades to an editable Text column rather than silently mistyping data.
 */
object NotionPropertyTypeInference {
    private val BOOLISH = setOf("yes", "no", "true", "false", "x", "checked", "unchecked")
    private val NUMBER_REGEX = Regex("^-?\\d+(\\.\\d+)?$")
    private val URL_REGEX = Regex("^https?://\\S+$")
    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}")
    private val LONG_DATE_FORMATS = listOf(
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US),
    )

    data class Inference(val type: PropertyType, val config: String? = null)

    fun infer(values: List<String>): Inference {
        val samples = values.map { it.trim() }.filter { it.isNotEmpty() }.take(50)
        if (samples.isEmpty()) return Inference(PropertyType.TEXT)

        if (samples.all { it.lowercase(Locale.ROOT) in BOOLISH }) return Inference(PropertyType.CHECKBOX)
        if (samples.all { NUMBER_REGEX.matches(it) }) return Inference(PropertyType.NUMBER)
        if (samples.all { URL_REGEX.matches(it) }) return Inference(PropertyType.URL)
        if (samples.all { EMAIL_REGEX.matches(it) }) return Inference(PropertyType.EMAIL)
        if (samples.all { parseNotionDate(it) != null }) return Inference(PropertyType.DATE)

        // Multi-value tags: most cells carry more than one comma-separated token, and the
        // vocabulary across every cell is small relative to the row count.
        val splitTokens = samples.map { cell -> cell.split(",").map(String::trim).filter(String::isNotEmpty) }
        val looksMultiValued = samples.count { it.contains(",") } > samples.size / 2
        val distinctTokens = splitTokens.flatten().distinct()
        if (looksMultiValued && distinctTokens.size in 1..20) {
            return Inference(PropertyType.MULTI_SELECT, distinctTokens.joinToString(","))
        }

        // Single-value, small closed vocabulary, short strings — a Select column.
        val distinctValues = samples.distinct()
        if (distinctValues.size in 1 until minOf(10, maxOf(2, samples.size)) && samples.all { it.length <= 40 && !it.contains("\n") }) {
            return Inference(PropertyType.SELECT, distinctValues.joinToString(","))
        }

        return Inference(PropertyType.TEXT)
    }

    /** Normalizes a recognized date into the same ISO `LocalDate.toString()` form the rest of
     * the app already stores dates in (§4's `PropertyValue.value` convention), so a Notion-
     * imported date property behaves identically to a locally-created one. Returns null rather
     * than guessing when the text isn't confidently a date — the caller then falls back to
     * storing it as plain text rather than corrupting an unparseable value. */
    fun parseNotionDate(text: String): LocalDate? {
        if (ISO_DATE_REGEX.containsMatchIn(text)) {
            return runCatching { LocalDate.parse(text.take(10)) }.getOrNull()
        }
        // Notion's long form carries a time as "March 3, 2026 at 9:00 AM"; only the date half
        // is kept.
        val datePart = text.substringBefore(" at ").trim()
        return LONG_DATE_FORMATS.firstNotNullOfOrNull {
            runCatching { LocalDate.parse(datePart, it) }.getOrNull()
        }
    }
}
