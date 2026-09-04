package com.tendril.app.notionimport

import com.tendril.app.data.page.BlockType

/**
 * §7.4 — intermediate inline-formatting kinds, resolved into a real
 * [com.tendril.app.data.page.SpanStyle] only once every imported page has a Room id: a
 * [PageRef] carries the raw 32-hex Notion id from the source `.md` link, patched during the
 * same pass that already has the full id map built (§7.4's two-pass link remapping — pass 1
 * creates every Page row and the id map, pass 2 parses bodies against it, so no third patching
 * pass is needed).
 */
sealed class ParsedSpanKind {
    data object Bold : ParsedSpanKind()
    data object Italic : ParsedSpanKind()
    data object Strikethrough : ParsedSpanKind()
    data object InlineCode : ParsedSpanKind()
    data class ExternalLink(val url: String) : ParsedSpanKind()
    data class PageRef(val notionId: String) : ParsedSpanKind()
}

data class ParsedSpan(val start: Int, val end: Int, val kind: ParsedSpanKind)

/** One imported block, pre-Room-id. Nesting from the source (sub-list items, toggle children)
 * is deliberately flattened to a flat top-level sequence in document order — see the note on
 * [NotionMarkdownParser.parse] — so there is no parent/depth field here at all. */
data class ParsedBlock(
    val type: BlockType,
    val content: String = "",
    val spans: List<ParsedSpan> = emptyList(),
    val checked: Boolean? = null,
    val codeLanguage: String? = null,
    val calloutIcon: String? = null,
    val imageAssetPath: String? = null,
)

/** A 32-character lowercase hex Notion id, as it appears in exported filenames and link hrefs. */
private val NOTION_ID_REGEX = Regex("([0-9a-fA-F]{32})")

// Hoisted alongside NOTION_ID_REGEX rather than built inside parse()'s per-line loop: each
// `Regex(...)` there recompiled the pattern once per line of every imported page, and the image
// one was compiled twice per matching line (once to test, once to capture).
private val TODO_ITEM_REGEX = Regex("^-\\s\\[( |x|X)]\\s")
private val NUMBERED_ITEM_REGEX = Regex("^\\d+\\.\\s")
private val STANDALONE_IMAGE_REGEX = Regex("^!\\[[^]]*]\\(([^)]+)\\)$")

/**
 * §7.4 — the chosen architecture: a hand-rolled, line-oriented streaming state machine, not a
 * generic Markdown AST library. It emits [ParsedBlock]/[ParsedSpan] directly in the shape this
 * app's own `Block`/`FormattingSpan` model already wants, so there's no separate AST schema to
 * adapt or keep in sync.
 *
 * **Nesting is flattened.** Notion's own toggle/nested-list content has no distinct Markdown
 * marker — it degrades to indented plain content (§7.2: "the collapse becomes permanently
 * open"). The in-app block editor's own nested-block rendering isn't built yet either (only
 * `parentBlockId == null` top-level blocks render today) — assigning `parentBlockId` here would
 * make imported content silently disappear from the page, which is worse than the documented
 * degradation. So every block this parser emits is top-level, in source document order; only
 * the *content* of a nested item is preserved, not its indentation level.
 */
object NotionMarkdownParser {
    fun parse(markdown: String): List<ParsedBlock> {
        val blocks = mutableListOf<ParsedBlock>()
        val lines = markdown.replace("\r\n", "\n").split("\n")
        var i = 0

        while (i < lines.size) {
            val rawLine = lines[i]
            val line = rawLine.trimStart()

            when {
                line.isBlank() -> { i++ }

                // Code fence: ```lang ... ```
                line.startsWith("```") -> {
                    val language = line.removePrefix("```").trim().ifBlank { null }
                    val content = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        if (content.isNotEmpty()) content.append('\n')
                        content.append(lines[i])
                        i++
                    }
                    i++ // consume the closing fence, if present
                    blocks += ParsedBlock(BlockType.CODE, content.toString(), codeLanguage = language)
                }

                // Notion's callout export: <aside>\n💡 text\n</aside> — heuristic reconstruction
                // into a native Callout block (§7.3.3's "nice to have", not just the raw-HTML
                // fallback minimum, since Notion's own output for this is completely regular).
                line.startsWith("<aside>") -> {
                    val body = StringBuilder()
                    i++
                    while (i < lines.size && lines[i].trim() != "</aside>") {
                        if (body.isNotEmpty()) body.append('\n')
                        body.append(lines[i].trim())
                        i++
                    }
                    i++ // consume </aside>, if present
                    val text = body.toString().trim()
                    val firstToken = text.substringBefore(' ')
                    val hasIcon = firstToken.isNotEmpty() && firstToken.codePointCount(0, firstToken.length) <= 2 &&
                        firstToken.codePoints().anyMatch { it > 0x2000 }
                    val icon = if (hasIcon) firstToken else null
                    val remainder = if (hasIcon) text.removePrefix(firstToken).trim() else text
                    val (plain, spans) = parseInline(remainder)
                    blocks += ParsedBlock(BlockType.CALLOUT, plain, spans, calloutIcon = icon)
                }

                // Any other raw embedded HTML — preserved verbatim rather than dropped
                // (§7.3.3's fallback minimum), one HTML element/line at a time.
                line.startsWith("<") -> {
                    blocks += ParsedBlock(BlockType.PARAGRAPH, rawLine.trim())
                    i++
                }

                line.startsWith("### ") -> { val (t, s) = parseInline(line.removePrefix("### ")); blocks += ParsedBlock(BlockType.HEADING_3, t, s); i++ }
                line.startsWith("## ") -> { val (t, s) = parseInline(line.removePrefix("## ")); blocks += ParsedBlock(BlockType.HEADING_2, t, s); i++ }
                line.startsWith("# ") -> { val (t, s) = parseInline(line.removePrefix("# ")); blocks += ParsedBlock(BlockType.HEADING_1, t, s); i++ }

                line == "---" || line == "***" || line == "___" -> { blocks += ParsedBlock(BlockType.DIVIDER); i++ }

                line.startsWith("> ") || line == ">" -> {
                    val (t, s) = parseInline(line.removePrefix(">").trim())
                    blocks += ParsedBlock(BlockType.QUOTE, t, s)
                    i++
                }

                TODO_ITEM_REGEX.containsMatchIn(line) -> {
                    val checked = line[3].lowercaseChar() == 'x'
                    val (t, s) = parseInline(line.substring(6))
                    blocks += ParsedBlock(BlockType.TODO, t, s, checked = checked)
                    i++
                }

                line.startsWith("- ") || line.startsWith("* ") -> {
                    val (t, s) = parseInline(line.substring(2))
                    blocks += ParsedBlock(BlockType.BULLETED_LIST_ITEM, t, s)
                    i++
                }

                NUMBERED_ITEM_REGEX.containsMatchIn(line) -> {
                    val (t, s) = parseInline(line.substringAfter(". "))
                    blocks += ParsedBlock(BlockType.NUMBERED_LIST_ITEM, t, s)
                    i++
                }

                // A standalone image/file line: ![alt](path) — the sole content on its line,
                // matching how Notion always exports embedded media (§7.1's per-page assets
                // folder). The asset's own bytes are extracted separately by [NotionImporter];
                // this only threads the zip-relative path through.
                STANDALONE_IMAGE_REGEX.matches(line) -> {
                    val path = STANDALONE_IMAGE_REGEX.find(line)!!.groupValues[1]
                    blocks += ParsedBlock(BlockType.IMAGE, imageAssetPath = decodeUrlPath(path))
                    i++
                }

                // Pipe-table syntax has no native Block type in this app (§7.4 gap found during
                // implementation — worth a spec note). Preserved verbatim as a Code block rather
                // than silently dropped: degraded, not lost, the same trade-off already accepted
                // for Toggles/Callouts elsewhere in §7.2.
                line.startsWith("|") -> {
                    val tableLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                        tableLines += lines[i]
                        i++
                    }
                    blocks += ParsedBlock(BlockType.CODE, tableLines.joinToString("\n"), codeLanguage = "text")
                }

                else -> {
                    val (t, s) = parseInline(line)
                    blocks += ParsedBlock(BlockType.PARAGRAPH, t, s)
                    i++
                }
            }
        }
        return blocks
    }

    /** Single left-to-right scan so span offsets are computed against the *plain* text being
     * built, not the raw markdown — sequential `Regex.replace` calls would corrupt overlapping
     * offsets the moment more than one style appears on a line. */
    private fun parseInline(raw: String): Pair<String, List<ParsedSpan>> {
        val plain = StringBuilder()
        val spans = mutableListOf<ParsedSpan>()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                raw.startsWith("**", i) -> {
                    val end = raw.indexOf("**", i + 2)
                    if (end == -1) { plain.append(c); i++ } else {
                        val start = plain.length
                        plain.append(raw, i + 2, end)
                        spans += ParsedSpan(start, plain.length, ParsedSpanKind.Bold)
                        i = end + 2
                    }
                }
                raw.startsWith("~~", i) -> {
                    val end = raw.indexOf("~~", i + 2)
                    if (end == -1) { plain.append(c); i++ } else {
                        val start = plain.length
                        plain.append(raw, i + 2, end)
                        spans += ParsedSpan(start, plain.length, ParsedSpanKind.Strikethrough)
                        i = end + 2
                    }
                }
                c == '`' -> {
                    val end = raw.indexOf('`', i + 1)
                    if (end == -1) { plain.append(c); i++ } else {
                        val start = plain.length
                        plain.append(raw, i + 1, end)
                        spans += ParsedSpan(start, plain.length, ParsedSpanKind.InlineCode)
                        i = end + 1
                    }
                }
                (c == '*' || c == '_') -> {
                    val end = raw.indexOf(c, i + 1)
                    if (end == -1) { plain.append(c); i++ } else {
                        val start = plain.length
                        plain.append(raw, i + 1, end)
                        spans += ParsedSpan(start, plain.length, ParsedSpanKind.Italic)
                        i = end + 1
                    }
                }
                c == '[' -> {
                    val closeBracket = raw.indexOf(']', i + 1)
                    if (closeBracket != -1 && closeBracket + 1 < raw.length && raw[closeBracket + 1] == '(') {
                        val closeParen = raw.indexOf(')', closeBracket + 2)
                        if (closeParen != -1) {
                            val label = raw.substring(i + 1, closeBracket)
                            val href = raw.substring(closeBracket + 2, closeParen)
                            val start = plain.length
                            plain.append(label)
                            // The LAST 32-hex run in the (decoded) relative path, not the
                            // first: a nested link like "Parent <id1>/Child <id2>.md" carries
                            // its ancestor's own id too, and the actual link target is always
                            // the segment immediately before ".md".
                            val notionId = NOTION_ID_REGEX.findAll(decodeUrlPath(href)).lastOrNull()?.groupValues?.get(1)?.lowercase()
                            val kind = if (notionId != null) ParsedSpanKind.PageRef(notionId) else ParsedSpanKind.ExternalLink(href)
                            spans += ParsedSpan(start, plain.length, kind)
                            i = closeParen + 1
                        } else { plain.append(c); i++ }
                    } else { plain.append(c); i++ }
                }
                else -> { plain.append(c); i++ }
            }
        }
        return plain.toString() to spans
    }

    private fun decodeUrlPath(path: String): String =
        runCatching { java.net.URLDecoder.decode(path, "UTF-8") }.getOrDefault(path)
}
