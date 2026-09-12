package com.tendril.app.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * §9.4 — one `pages/<uid>.json` snapshot record per [com.tendril.app.data.page.Page] (a plain
 * page, a database definition, a database row, or a canvas — [kind] distinguishes them the
 * same way it does in Room). Every FK travels as the target's cross-device `uid`, never a
 * local Room id, same rule [EntrySnapshotRecord] already follows.
 *
 * Merge is whole-record LWW, not per-child: when a record wins (new, or `updatedAt` newer
 * than local), its [blocks]/[labels]/[propertyValues]/[database]/[canvas] fully replace
 * whatever's stored locally rather than being diffed in — the same "concurrent edits to the
 * same page before either syncs means one edit is lost" limitation §9.4 already accepts for
 * Page content in general, just made explicit here for every child collection at once.
 */
@Serializable
data class PageSnapshotRecord(
    val uid: String,
    val title: String,
    val icon: String? = null,
    /** [com.tendril.app.data.page.PageKind] name. */
    val kind: String,
    val parentUid: String? = null,
    /** Set when this Page is a Database row — the owning [PageDatabaseSnapshotRecord]'s
     * page's `uid`, not [PageDatabaseSnapshotRecord] itself (a Row's FK points at
     * `PageDatabase.id`, which is one-to-one with a `kind = DATABASE` Page). */
    val databaseUid: String? = null,
    val isTemplate: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** Label *names*, not uids — a name deterministically derives its own color
     * ([com.tendril.app.data.page.LabelColors]), so re-creating a same-named label on another
     * device needs no separate color sync, matching that mechanism's existing design intent. */
    @SerialName("tags") val labels: List<String> = emptyList(),
    val blocks: List<BlockSnapshotRecord> = emptyList(),
    /** This page's cell values, since a value's identity (`propertyId` + `rowPageId`) is really
     * a property of the *page*, not the database. Rows carried these from the start; since
     * §0.6.8 (v13) any page can, because a label can make any page a member of a database. */
    val propertyValues: List<PropertyValueSnapshotRecord> = emptyList(),
    /** `kind = DATABASE` only. */
    val database: PageDatabaseSnapshotRecord? = null,
    /** `kind = CANVAS` only. */
    val canvas: CanvasSnapshotRecord? = null,
)

@Serializable
data class BlockSnapshotRecord(
    val uid: String,
    /** [com.tendril.app.data.page.BlockType] name. */
    val type: String,
    val order: Int,
    val parentBlockUid: String? = null,
    val content: String = "",
    val formattingSpans: List<FormattingSpanSnapshot> = emptyList(),
    val checked: Boolean? = null,
    val codeLanguage: String? = null,
    val calloutIcon: String? = null,
    val calloutColor: String? = null,
    val mentionedPageUid: String? = null,
    val toggleExpanded: Boolean = true,
    /** §0.6.2 (v12). Defaults so a v11 peer's block reads as a list. */
    val mindMap: Boolean = false,
    /**
     * §9.4 / S4 — the image's file name in the folder's `images/` directory, or null for a block
     * that has none. `<block uid>.<extension>`.
     *
     * **`imagePath` is still deliberately excluded, and this is not the same thing.** That field
     * is where *this device* keeps its copy — an app-private absolute path, meaningless anywhere
     * else, and a peer has nothing to say about it. This one names *which image*, which every
     * device can agree on. Separating the two is what lets an image travel without any device
     * dictating another's filesystem layout, and it is why the merge still carries `imagePath`
     * across a rebuild by uid exactly as it did before.
     *
     * Named after the block rather than a hash of the content: a block has exactly one image, so
     * the uid already identifies it, and deleting the block makes its image unreachable by
     * construction. Replacing an image overwrites in place **only when the new file has the same
     * extension** -- swapping a PNG for a JPEG changes the name and leaves the old copy orphaned
     * in local storage. Small, and S4's purge step is where it belongs; noted here because the
     * uid-naming scheme is what makes it possible at all. Content addressing would add
     * deduplication and cost a schema column to store the hash in; Joplin's resource model makes
     * the same trade, with deduplication left to a plugin.
     */
    val imageName: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class FormattingSpanSnapshot(val start: Int, val end: Int, val style: SpanStyleSnapshot)

/** Mirrors [com.tendril.app.data.page.SpanStyle] with [PageMention] carrying a `uid` instead
 * of a local Room id — the only variant that needs a snapshot-specific shape at all. */
@Serializable
sealed class SpanStyleSnapshot {
    @Serializable data object Bold : SpanStyleSnapshot()
    @Serializable data object Italic : SpanStyleSnapshot()
    @Serializable data object Strikethrough : SpanStyleSnapshot()
    @Serializable data object InlineCode : SpanStyleSnapshot()
    @Serializable data class Link(val url: String) : SpanStyleSnapshot()
    @Serializable data class PageMention(val pageUid: String) : SpanStyleSnapshot()
}

@Serializable
data class PropertyValueSnapshotRecord(val propertyUid: String, val value: String? = null)

@Serializable
data class PageDatabaseSnapshotRecord(
    val syncToTasks: Boolean = false,
    val donePropertyUid: String? = null,
    val deadlinePropertyUid: String? = null,
    /** §0.8 step 2b (v11). Defaults so a v10 peer's record reads as "no deadline binding". */
    val dueDatePropertyUid: String? = null,
    val recurrencePropertyUid: String? = null,
    /** §0.6.8 (v13) — the bound label, by *name*, the same way a page's [PageSnapshotRecord.labels]
     * travel: a name resolves on any device with `findByName ?: insert`, a uid would not. Defaults
     * so a v12 peer's record reads as "no label bound". */
    val labelName: String? = null,
    val labelConfirmed: Boolean = false,
    val properties: List<PropertySnapshotRecord> = emptyList(),
    val views: List<ViewSnapshotRecord> = emptyList(),
)

@Serializable
data class PropertySnapshotRecord(
    val uid: String,
    val name: String,
    /** [com.tendril.app.data.pagedatabase.PropertyType] name. */
    val type: String,
    val config: String? = null,
    val order: Int,
)

@Serializable
data class ViewSnapshotRecord(
    val uid: String,
    val name: String,
    /** [com.tendril.app.data.pagedatabase.ViewType] name. */
    val viewType: String,
    val groupByPropertyUid: String? = null,
    val datePropertyUid: String? = null,
    val visiblePropertyUids: List<String> = emptyList(),
    val filter: ViewFilterSnapshot? = null,
    val sortPropertyUid: String? = null,
    /** [com.tendril.app.data.pagedatabase.SortDirection] name. */
    val sortDirection: String? = null,
    val order: Int,
)

@Serializable
data class ViewFilterSnapshot(val propertyUid: String, val comparator: String, val value: String)

@Serializable
data class CanvasSnapshotRecord(
    val nodes: List<CanvasNodeSnapshotRecord> = emptyList(),
    val edges: List<CanvasEdgeSnapshotRecord> = emptyList(),
)

@Serializable
data class CanvasNodeSnapshotRecord(
    val uid: String,
    /** [com.tendril.app.data.canvas.CanvasNodeType] name. */
    val type: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val text: String? = null,
    val embeddedPageUid: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** No `uid` of its own — an edge is never referenced from anywhere else, so its identity only
 * needs to be stable *within* one [CanvasSnapshotRecord], via the nodes it connects. */
@Serializable
data class CanvasEdgeSnapshotRecord(
    val fromNodeUid: String,
    val toNodeUid: String,
    /** [com.tendril.app.data.canvas.CanvasArrowDirection] name. */
    val direction: String,
    val label: String? = null,
)

/** §3.4 — the "Relate to…" manual edge set, one flat `page_relations.json` file (not
 * per-record like Pages) since an edge is lightweight, undirected, and add-only — the same
 * shape [PageRelationDao.addRelation]'s server-side dedup already assumes. */
@Serializable
data class PageRelationSnapshotRecord(val fromPageUid: String, val toPageUid: String, val createdAt: Long)
