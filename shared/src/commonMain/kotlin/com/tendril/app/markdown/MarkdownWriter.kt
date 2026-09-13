package com.tendril.app.markdown

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.domain.outlineOf
import com.tendril.app.data.page.FormattingSpan
import com.tendril.app.data.page.SpanStyle

/**
 * §7 in reverse — a page's blocks as Markdown text.
 *
 * The importer has existed since 2026-08-30 and there has never been an export to match it, which
 * is the asymmetry this closes: notes could come *in* from a portable format and could only leave
 * in `.tendril`, a shape nothing but this app reads. What Markdown buys is not a feature, it is
 * the guarantee that the notes outlive the program.
 *
 * **Deliberately pure, and deliberately not a storage format.** §3.1.1 stores inline formatting as
 * `(start, end, style)` spans over plain text rather than embedded markup, and that decision is
 * not reopened here: keeping the FTS index equal to the block's own content is worth more than
 * matching an export byte for byte. This turns spans into markup at the boundary, once, on the way
 * out. Four of the fourteen block types have no Markdown to be, so this is a lossy direction by
 * construction and says so per case below — the same trade-off §7.1 already documents coming the
 * other way.
 *
 * No DAOs and no platform types: the two things a caller must supply, an asset path for an image
 * and a file name for a page mention, arrive as lambdas. That keeps every case testable without a
 * database, and lets one renderer serve a whole-vault export and a future single-page share
 * without either of them knowing about the other.
 */
object MarkdownWriter {

    /** Notion indents nested content by four columns and `NotionMarkdownParser` accepts anything
     * from two, so four round-trips through our own importer and reads correctly elsewhere. */
    private const val CHILD_INDENT = "    "

    /**
     * [blocks] in document order, as they come from `BlockDao.getForPage`.
     *
     * [assetPathFor] returns the export-relative path for an IMAGE block (`assets/x.png`), or null
     * when this device holds no copy — in which case the image degrades to a comment naming the
     * block rather than an empty `![]()` that renders as a broken image everywhere.
     * [pageLinkFor] turns a mentioned page's local id into a link target, and returns null when
     * the target is not part of this export.
     */
    fun render(
        blocks: List<Block>,
        assetPathFor: (Block) -> String? = { null },
        pageLinkFor: (pageId: Long) -> String? = { null },
    ): String {
        val out = StringBuilder()
        // Numbered lists are written with real ordinals rather than a repeated `1.`. Both render
        // identically, but one of them is readable in the plain text file, which is the entire
        // reason for choosing this format.
        var ordinal = 0
        var previousWasNumbered = false

        // In outline order and at outline depth (§0.6.1): a child is written straight after its
        // parent, indented once per level, and a collapsed toggle's subtree is written too —
        // hiding a branch on screen is not a choice to lose it from the file.
        for (entry in outlineOf(blocks, expandAll = true)) {
            val block = entry.block
            val numbered = block.type == BlockType.NUMBERED_LIST_ITEM
            ordinal = if (numbered && previousWasNumbered) ordinal + 1 else 1
            previousWasNumbered = numbered

            val indent = CHILD_INDENT.repeat(entry.depth)
            for (line in linesFor(block, ordinal, assetPathFor, pageLinkFor)) {
                out.append(indent).append(line).append('\n')
            }
            // A blank line after every block. Markdown needs it between paragraphs and headings,
            // and tolerates it inside lists; emitting it unconditionally is one rule instead of a
            // table of which neighbours need separating.
            out.append('\n')
        }
        return out.toString().trimEnd('\n') + "\n"
    }

    private fun linesFor(
        block: Block,
        ordinal: Int,
        assetPathFor: (Block) -> String?,
        pageLinkFor: (Long) -> String?,
    ): List<String> {
        val text = inline(block.content, block.formattingSpans, pageLinkFor)
        return when (block.type) {
            BlockType.PARAGRAPH -> listOf(escapeLeadingMarkup(text))
            BlockType.HEADING_1 -> listOf("# $text")
            BlockType.HEADING_2 -> listOf("## $text")
            BlockType.HEADING_3 -> listOf("### $text")
            BlockType.BULLETED_LIST_ITEM -> listOf("- $text")
            BlockType.NUMBERED_LIST_ITEM -> listOf("$ordinal. $text")
            BlockType.TODO -> listOf("- [${if (block.checked == true) "x" else " "}] $text")
            BlockType.QUOTE -> text.split('\n').map { "> $it" }
            BlockType.DIVIDER -> listOf("---")

            // The fence carries the language back to `Block.codeLanguage`; a null one is a bare
            // fence, which is exactly what the importer stores as "plain text".
            BlockType.CODE -> listOf("```${block.codeLanguage.orEmpty()}") +
                block.content.split('\n') + listOf("```")

            // Raw HTML rather than a blockquote, because `NotionMarkdownParser` reads `<aside>`
            // back into a real CALLOUT — Notion's own export uses this and our importer already
            // understands it, so a callout survives a round trip through this app intact. It
            // degrades to a quoted block in readers that do not special-case it, which is the
            // better failure than losing the icon and the block type.
            BlockType.CALLOUT -> listOf("<aside>", listOfNotNull(block.calloutIcon, text).joinToString(" "), "</aside>")

            // Markdown has no collapsible section. §7.1 already accepts the mirror of this coming
            // in ("the collapse becomes permanently open, content intact"), so the text is written
            // as an ordinary paragraph and the children follow, indented, on their own rows.
            BlockType.TOGGLE -> listOf(escapeLeadingMarkup(text))

            BlockType.IMAGE -> {
                val path = assetPathFor(block)
                // A block whose picture this device does not hold — it may simply not have synced
                // here yet (§9.4). A comment keeps the block's place in the document without
                // writing a link that every reader would render as a broken image.
                if (path == null) listOf("<!-- image not available on this device: ${block.uid} -->")
                else listOf("![${text.ifBlank { "image" }}](${encodePath(path)})")
            }

            BlockType.PAGE_MENTION -> {
                val target = block.mentionedPageId?.let(pageLinkFor)
                val label = text.ifBlank { "page" }
                if (target == null) listOf(escapeLeadingMarkup(label))
                else listOf("[$label](${encodePath(target)})")
            }

            // §0.6.12 — the words as cached (the live text is a screen concern), quoted so a
            // reader sees they are someone else's, with the source named when it is in the export.
            BlockType.BLOCK_REFERENCE -> {
                val target = block.mentionedPageId?.let(pageLinkFor)
                val quoted = text.ifBlank { "(empty block)" }.split('\n').map { "> $it" }
                if (target == null) quoted else quoted + listOf("> — [source](${encodePath(target)})")
            }

            // §0.6.3 — a canvas is a page, so the block is a link to that page's file, labelled so
            // a reader knows what stood here. Its board is not in Markdown at all (JSON Canvas is
            // §0.6.7); the link is the honest most this format can carry.
            BlockType.CANVAS -> {
                val target = block.mentionedPageId?.let(pageLinkFor)
                val label = "Canvas: " + text.ifBlank { "untitled" }
                if (target == null) listOf(escapeLeadingMarkup(label))
                else listOf("[$label](${encodePath(target)})")
            }
        }
    }

    /**
     * §3.1.1's spans back into markup, in one pass over the plain text.
     *
     * Built as insertions at offsets rather than by repeated `replace`, for the same reason
     * `NotionMarkdownParser.parseInline` reads left to right: every span's offsets are measured
     * against the plain text, so any edit that changed the string's length would invalidate every
     * offset after it.
     *
     * A span whose bounds fall outside the content is dropped rather than clamped or thrown on. It
     * cannot be rendered meaningfully, and an export is the last operation that should fail on one
     * bad row — the point of it is to get the person's data out.
     */
    private fun inline(
        content: String,
        spans: List<FormattingSpan>,
        pageLinkFor: (Long) -> String?,
    ): String {
        if (spans.isEmpty()) return content
        val opens = HashMap<Int, MutableList<String>>()
        val closes = HashMap<Int, MutableList<String>>()
        for (span in spans.sortedBy { it.start }) {
            if (span.start < 0 || span.end > content.length || span.start >= span.end) continue
            val (open, close) = markersFor(span.style, pageLinkFor) ?: continue
            opens.getOrPut(span.start) { mutableListOf() }.add(open)
            closes.getOrPut(span.end) { mutableListOf() }.add(close)
        }

        val out = StringBuilder(content.length)
        for (i in 0..content.length) {
            // Closings first, and in reverse of the order their openings were emitted, so nested
            // spans close innermost-first instead of producing `**_text**_`.
            closes[i]?.asReversed()?.forEach(out::append)
            opens[i]?.forEach(out::append)
            if (i < content.length) out.append(content[i])
        }
        return out.toString()
    }

    /** The opening and closing markup for one style, or null when it cannot be expressed here. */
    private fun markersFor(style: SpanStyle, pageLinkFor: (Long) -> String?): Pair<String, String>? =
        when (style) {
            is SpanStyle.Bold -> "**" to "**"
            is SpanStyle.Italic -> "*" to "*"
            is SpanStyle.Strikethrough -> "~~" to "~~"
            is SpanStyle.InlineCode -> "`" to "`"
            is SpanStyle.Link -> "[" to "](${encodePath(style.url)})"
            // A mention of a page outside this export has no file to point at. The label is kept
            // as plain text rather than linked into nothing.
            is SpanStyle.PageMention -> pageLinkFor(style.pageId)?.let { "[" to "](${encodePath(it)})" }
        }

    /**
     * Stops a paragraph's own text from being read back as a different block.
     *
     * Only the tokens that change *structure*, and only at the start of a line, where Markdown
     * gives them meaning. Escaping every `*` and `_` inside running text would be safer for a
     * round trip and would also make the file unpleasant to read — and being pleasant to read in
     * an ordinary editor is the whole reason this format was chosen over the JSON we already had.
     */
    private fun escapeLeadingMarkup(text: String): String {
        val trimmed = text.trimStart()
        val structural = listOf("#", "- ", "* ", "> ", "|", "```", "---", "===")
        return if (structural.any { trimmed.startsWith(it) }) "\\$text" else text
    }

    /** Spaces are what actually break a `](path)` target, and `NotionMarkdownParser.decodeUrlPath`
     * already reverses this on the way back in. Left otherwise alone: a fully percent-encoded path
     * is unreadable, and readability is the point. */
    private fun encodePath(path: String): String = path.replace(" ", "%20")
}
