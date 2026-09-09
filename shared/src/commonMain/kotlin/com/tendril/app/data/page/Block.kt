package com.tendril.app.data.page

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/** §3.1.1 — the v1 block type inventory (Notion-lite scope, §3.1.1's Pareto analysis). */
enum class BlockType {
    PARAGRAPH, HEADING_1, HEADING_2, HEADING_3,
    BULLETED_LIST_ITEM, NUMBERED_LIST_ITEM,
    TODO, QUOTE, CODE, DIVIDER, IMAGE, TOGGLE, CALLOUT,
    /** Standalone `@Page Title` block — inline mentions within any text block are a
     * [SpanStyle.PageMention] span instead, not this block type. */
    PAGE_MENTION,
}

/** §3.1.1 — "inline formatting... is stored as (start, end, style) spans over a block's
 * plain-text content rather than embedded markup." Both a plain link and an inline
 * `@Page Title` mention are span styles, not separate mechanisms. */
@Serializable
sealed class SpanStyle {
    @Serializable data object Bold : SpanStyle()
    @Serializable data object Italic : SpanStyle()
    @Serializable data object Strikethrough : SpanStyle()
    @Serializable data object InlineCode : SpanStyle()
    @Serializable data class Link(val url: String) : SpanStyle()
    /** The primary source of Road Map's edges (§3.4) — no extra UI needed beyond typing `@`. */
    @Serializable data class PageMention(val pageId: Long) : SpanStyle()
}

@Serializable
data class FormattingSpan(val start: Int, val end: Int, val style: SpanStyle)

/**
 * §3.1.1 — one row per content block inside a Page's (or Row's) body. A single wide table
 * with nullable type-specific columns rather than a polymorphic content blob — the block
 * type inventory is fixed (v1 scope decided in §3.1.1), so this stays queryable and doesn't
 * need a JSON-blob-shaped escape hatch the way genuinely open-ended data would.
 */
@Entity(
    tableName = "blocks",
    foreignKeys = [
        ForeignKey(entity = Page::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("uid", unique = true), Index("pageId"), Index("parentBlockId")],
)
data class Block(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val pageId: Long,
    val type: BlockType,
    val order: Int,
    /** One level of nesting for list items and toggle children (§3.1.1 — "matches typical
     * personal-notes depth, not arbitrary nesting"). */
    val parentBlockId: Long? = null,

    val content: String = "",
    val formattingSpans: List<FormattingSpan> = emptyList(),

    /** TODO only — a block-level checkbox, structurally distinct from a Property-level Done
     * checkbox (§3.1.1): checking this never creates a Task. */
    val checked: Boolean? = null,
    /** CODE only — no syntax highlighting required for v1 (§3.1.1). */
    val codeLanguage: String? = null,
    /** CALLOUT only. */
    val calloutIcon: String? = null,
    val calloutColor: String? = null,
    /** PAGE_MENTION block only — the standalone-block form; an inline `@mention` within
     * running text is a [SpanStyle.PageMention] span instead. */
    val mentionedPageId: Long? = null,
    /** TOGGLE only — "actually collapses" (§3.1.1), state persisted so it survives navigation. */
    val toggleExpanded: Boolean = true,
    /** IMAGE only — where *this device* keeps its copy: an app-private absolute path, written on
     * insert (§3.1.1) and never synced, because it is meaningless on any other device.
     *
     * S4 did not change that. What travels is
     * [com.tendril.app.sync.BlockSnapshotRecord.imageName] — `<block uid>.<extension>`, naming
     * *which* image — while the bytes live in the folder's `images/` directory and each device
     * resolves them to a path of its own. The two fields answer different questions, which is why
     * adding the first did not require touching this one. */
    val imagePath: String? = null,

    val createdAt: Instant,
    val updatedAt: Instant,
)
