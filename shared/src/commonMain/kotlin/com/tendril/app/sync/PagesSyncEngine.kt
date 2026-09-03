package com.tendril.app.sync

import com.tendril.app.data.canvas.CanvasArrowDirection
import com.tendril.app.data.canvas.CanvasEdge
import com.tendril.app.data.canvas.CanvasEdgeDao
import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeDao
import com.tendril.app.data.canvas.CanvasNodeType
import com.tendril.app.data.canvas.PageCanvas
import com.tendril.app.data.canvas.PageCanvasDao
import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockDao
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.FormattingSpan
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.page.PageRelationDao
import com.tendril.app.data.page.PageTag
import com.tendril.app.data.page.SpanStyle
import com.tendril.app.data.page.Tag
import com.tendril.app.data.page.TagDao
import com.tendril.app.data.page.addRelation
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.PageDatabaseDao
import com.tendril.app.data.pagedatabase.PageDatabaseView
import com.tendril.app.data.pagedatabase.PageDatabaseViewDao
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyDao
import com.tendril.app.data.pagedatabase.PropertyType
import com.tendril.app.data.pagedatabase.PropertyValue
import com.tendril.app.data.pagedatabase.PropertyValueDao
import com.tendril.app.data.pagedatabase.SortDirection
import com.tendril.app.data.pagedatabase.ViewFilter
import com.tendril.app.data.pagedatabase.ViewType
import com.tendril.app.domain.PageContentRepository
import java.time.Instant

/**
 * §9.4 — completes the Page/Row/Database/Canvas half of snapshot sync (Entry/Habit's half
 * already lived in [SnapshotMappers]/[SnapshotSyncManager]/[PortableArchive]). Shared by both
 * the continuous folder sync and the one-off portable export/import, matching those callers'
 * own "one schema, reused everywhere" rule — the merge algorithm here is intricate enough
 * (five ordered passes, several cross-page FK resolutions) that duplicating it per caller the
 * way the simpler Entry/Habit merges are today would be a real maintenance risk, not just
 * more typing.
 *
 * Every FK in [PageSnapshotRecord] and friends travels as a `uid`; resolving it back to a
 * local Room id follows the same "drop the link if the target hasn't merged in yet, self-heal
 * on a later sync pass" rule [SnapshotMappers.toEntity] already established for
 * `originalEntryUid` — except for a Page's own tree position (`parentUid`/`databaseUid`),
 * which gets a real two-pass fixup instead of drop-and-heal: unlike a rare recurring-exception
 * backlink, losing a page's parent on every first sync would misplace most of a person's
 * Pages hub, not just one edge case.
 *
 * Merge is whole-record LWW: a winning [PageSnapshotRecord] fully replaces its local
 * blocks/tags/property values/database schema/canvas content rather than diffing them in —
 * the same "concurrent edits to the same page... already an accepted v1 limitation" §9.4
 * documents for Page content generally. The one deliberate exception is
 * [com.tendril.app.data.pagedatabase.Property]: it's upserted by `uid` rather than
 * delete-and-reinsert, because [PropertyValue] cascades off a Property's *local* id — blindly
 * replacing Properties would silently cascade-delete every row's existing cell values even
 * when the schema itself didn't change.
 */
class PagesSyncEngine(
    private val pageDao: PageDao,
    private val blockDao: BlockDao,
    private val tagDao: TagDao,
    private val pageDatabaseDao: PageDatabaseDao,
    private val propertyDao: PropertyDao,
    private val propertyValueDao: PropertyValueDao,
    private val pageDatabaseViewDao: PageDatabaseViewDao,
    private val pageCanvasDao: PageCanvasDao,
    private val canvasNodeDao: CanvasNodeDao,
    private val canvasEdgeDao: CanvasEdgeDao,
    private val pageRelationDao: PageRelationDao,
    private val pageContentRepository: PageContentRepository,
) {
    // ---------------------------------------------------------------- export

    suspend fun exportPages(): List<PageSnapshotRecord> {
        val pages = pageDao.getAll()
        val pageIdToUid = pages.associate { it.id to it.uid }
        val propertyIdToUid = propertyDao.getAll().associate { it.id to it.uid }

        return pages.map { page ->
            val blocks = blockDao.getForPage(page.id)
            val blockIdToUid = blocks.associate { it.id to it.uid }

            PageSnapshotRecord(
                uid = page.uid,
                title = page.title,
                icon = page.icon,
                kind = page.kind.name,
                parentUid = page.parentId?.let { pageIdToUid[it] },
                databaseUid = page.databaseId?.let { dbId -> pageDatabaseDao.getById(dbId)?.let { pageIdToUid[it.pageId] } },
                isTemplate = page.isTemplate,
                deletedAt = page.deletedAt?.toEpochMilli(),
                createdAt = page.createdAt.toEpochMilli(),
                updatedAt = page.updatedAt.toEpochMilli(),
                tags = tagDao.getForPage(page.id).map { it.name },
                blocks = blocks.map { it.toSnapshot(pageIdToUid, blockIdToUid) },
                propertyValues = if (page.databaseId != null) {
                    propertyValueDao.getForRow(page.id).mapNotNull { pv ->
                        propertyIdToUid[pv.propertyId]?.let { PropertyValueSnapshotRecord(it, pv.value) }
                    }
                } else emptyList(),
                database = if (page.kind == PageKind.DATABASE) exportDatabase(page.id, propertyIdToUid) else null,
                canvas = if (page.kind == PageKind.CANVAS) exportCanvas(page.id, pageIdToUid) else null,
            )
        }
    }

    private suspend fun exportDatabase(pageId: Long, propertyIdToUid: Map<Long, String>): PageDatabaseSnapshotRecord? {
        val db = pageDatabaseDao.getByPageId(pageId) ?: return null
        val properties = propertyDao.getForDatabase(db.id)
        return PageDatabaseSnapshotRecord(
            syncToTasks = db.syncToTasks,
            donePropertyUid = db.donePropertyId?.let { propertyIdToUid[it] },
            deadlinePropertyUid = db.deadlinePropertyId?.let { propertyIdToUid[it] },
            recurrencePropertyUid = db.recurrencePropertyId?.let { propertyIdToUid[it] },
            properties = properties.map { PropertySnapshotRecord(it.uid, it.name, it.type.name, it.config, it.order) },
            views = pageDatabaseViewDao.getForDatabase(db.id).map { v ->
                ViewSnapshotRecord(
                    uid = v.uid,
                    name = v.name,
                    viewType = v.viewType.name,
                    groupByPropertyUid = v.groupByPropertyId?.let { propertyIdToUid[it] },
                    datePropertyUid = v.datePropertyId?.let { propertyIdToUid[it] },
                    visiblePropertyUids = v.visiblePropertyIds.mapNotNull { propertyIdToUid[it] },
                    filter = v.filter?.let { f -> propertyIdToUid[f.propertyId]?.let { ViewFilterSnapshot(it, f.comparator.name, f.value) } },
                    sortPropertyUid = v.sortPropertyId?.let { propertyIdToUid[it] },
                    sortDirection = v.sortDirection?.name,
                    order = v.order,
                )
            },
        )
    }

    private suspend fun exportCanvas(pageId: Long, pageIdToUid: Map<Long, String>): CanvasSnapshotRecord? {
        val canvas = pageCanvasDao.getByPageId(pageId) ?: return null
        val nodes = canvasNodeDao.getForCanvas(canvas.id)
        val nodeIdToUid = nodes.associate { it.id to it.uid }
        return CanvasSnapshotRecord(
            nodes = nodes.map { n ->
                CanvasNodeSnapshotRecord(
                    uid = n.uid, type = n.type.name, x = n.x, y = n.y, width = n.width, height = n.height,
                    text = n.text, embeddedPageUid = n.embeddedPageId?.let { pageIdToUid[it] },
                    createdAt = n.createdAt.toEpochMilli(), updatedAt = n.updatedAt.toEpochMilli(),
                )
            },
            edges = canvasEdgeDao.getForCanvas(canvas.id).mapNotNull { e ->
                val from = nodeIdToUid[e.fromNodeId] ?: return@mapNotNull null
                val to = nodeIdToUid[e.toNodeId] ?: return@mapNotNull null
                CanvasEdgeSnapshotRecord(from, to, e.direction.name, e.label)
            },
        )
    }

    suspend fun exportRelations(): List<PageRelationSnapshotRecord> {
        val idToUid = pageDao.getAll().associate { it.id to it.uid }
        return pageRelationDao.getAll().mapNotNull { r ->
            val from = idToUid[r.fromPageId] ?: return@mapNotNull null
            val to = idToUid[r.toPageId] ?: return@mapNotNull null
            PageRelationSnapshotRecord(from, to, r.createdAt.toEpochMilli())
        }
    }

    private fun Block.toSnapshot(pageIdToUid: Map<Long, String>, blockIdToUid: Map<Long, String>) = BlockSnapshotRecord(
        uid = uid, type = type.name, order = order,
        parentBlockUid = parentBlockId?.let { blockIdToUid[it] },
        content = content,
        formattingSpans = formattingSpans.mapNotNull { it.toSnapshot(pageIdToUid) },
        checked = checked, codeLanguage = codeLanguage, calloutIcon = calloutIcon, calloutColor = calloutColor,
        mentionedPageUid = mentionedPageId?.let { pageIdToUid[it] },
        toggleExpanded = toggleExpanded,
        createdAt = createdAt.toEpochMilli(), updatedAt = updatedAt.toEpochMilli(),
    )

    private fun FormattingSpan.toSnapshot(pageIdToUid: Map<Long, String>): FormattingSpanSnapshot? {
        val snapStyle = when (val s = style) {
            is SpanStyle.Bold -> SpanStyleSnapshot.Bold
            is SpanStyle.Italic -> SpanStyleSnapshot.Italic
            is SpanStyle.Strikethrough -> SpanStyleSnapshot.Strikethrough
            is SpanStyle.InlineCode -> SpanStyleSnapshot.InlineCode
            is SpanStyle.Link -> SpanStyleSnapshot.Link(s.url)
            is SpanStyle.PageMention -> pageIdToUid[s.pageId]?.let { SpanStyleSnapshot.PageMention(it) } ?: return null
        }
        return FormattingSpanSnapshot(start, end, snapStyle)
    }

    // ----------------------------------------------------------------- merge

    /** Full folder-wide (or archive-wide) merge in five ordered passes — see the class doc for
     * why this can't be a simple per-record loop the way Entry/Habit's merge is. */
    suspend fun mergePages(records: List<PageSnapshotRecord>) {
        if (records.isEmpty()) return
        val uidToId = pageDao.getAll().associate { it.uid to it.id }.toMutableMap()
        val wonUids = mutableSetOf<String>()

        // Pass 1 — upsert bare Page rows (tree position deferred to Pass 2); ensure every
        // DATABASE-kind page in the batch has a PageDatabase shell row to hang properties off.
        for (record in records) {
            val local = pageDao.getByUid(record.uid)
            val remoteUpdatedAt = Instant.ofEpochMilli(record.updatedAt)
            val id: Long
            if (local == null) {
                id = pageDao.insert(record.toBareEntity())
                uidToId[record.uid] = id
                wonUids += record.uid
            } else if (remoteUpdatedAt.isAfter(local.updatedAt)) {
                id = local.id
                pageDao.update(record.toBareEntity().copy(id = id, parentId = local.parentId, databaseId = local.databaseId))
                wonUids += record.uid
            } else {
                id = local.id
            }
            if (record.kind == PageKind.DATABASE.name && pageDatabaseDao.getByPageId(id) == null) {
                val now = Instant.ofEpochMilli(record.updatedAt)
                pageDatabaseDao.insert(PageDatabase(pageId = id, createdAt = now, updatedAt = now))
            }
        }

        // Pass 2 — every page (and every DATABASE page's PageDatabase shell) in the batch now
        // has a stable id, so a winner's parent/database FK can resolve for real.
        for (record in records) {
            if (record.uid !in wonUids) continue
            val id = uidToId.getValue(record.uid)
            val parentId = record.parentUid?.let { uidToId[it] }
            val databaseId = record.databaseUid?.let { dbPageUid -> uidToId[dbPageUid]?.let { pageDatabaseDao.getByPageId(it)?.id } }
            pageDao.updateParentAndDatabase(id, parentId, databaseId)
        }

        // Pass 3 — Properties/Views for winning DATABASE records. Properties are upserted by
        // uid, not replaced (see class doc); propertyUidToId seeds from every existing
        // Property so Pass 5's row values can resolve even against a database this batch
        // never touched.
        val propertyUidToId = propertyDao.getAll().associate { it.uid to it.id }.toMutableMap()
        for (record in records) {
            if (record.uid !in wonUids) continue
            val db = record.database ?: continue
            val pageId = uidToId.getValue(record.uid)
            val pageDatabaseId = pageDatabaseDao.getByPageId(pageId)?.id ?: continue

            val remoteUids = db.properties.map { it.uid }.toSet()
            val existingByUid = propertyDao.getForDatabase(pageDatabaseId).associateBy { it.uid }
            for (p in db.properties) {
                val existing = existingByUid[p.uid]
                val id = if (existing != null) {
                    propertyDao.update(existing.copy(name = p.name, type = PropertyType.valueOf(p.type), config = p.config, order = p.order))
                    existing.id
                } else {
                    propertyDao.insert(Property(uid = p.uid, databaseId = pageDatabaseId, name = p.name, type = PropertyType.valueOf(p.type), config = p.config, order = p.order))
                }
                propertyUidToId[p.uid] = id
            }
            existingByUid.values.filter { it.uid !in remoteUids }.forEach { propertyDao.delete(it.id) }

            pageDatabaseViewDao.deleteAllForDatabase(pageDatabaseId)
            for (v in db.views) {
                pageDatabaseViewDao.insert(
                    PageDatabaseView(
                        uid = v.uid, databaseId = pageDatabaseId, name = v.name, viewType = ViewType.valueOf(v.viewType),
                        groupByPropertyId = v.groupByPropertyUid?.let { propertyUidToId[it] },
                        datePropertyId = v.datePropertyUid?.let { propertyUidToId[it] },
                        visiblePropertyIds = v.visiblePropertyUids.mapNotNull { propertyUidToId[it] },
                        filter = v.filter?.let { f -> propertyUidToId[f.propertyUid]?.let { ViewFilter(it, ViewFilter.Comparator.valueOf(f.comparator), f.value) } },
                        sortPropertyId = v.sortPropertyUid?.let { propertyUidToId[it] },
                        sortDirection = v.sortDirection?.let { SortDirection.valueOf(it) },
                        order = v.order,
                    )
                )
            }
            pageDatabaseDao.update(
                pageDatabaseDao.getByPageId(pageId)!!.copy(
                    syncToTasks = db.syncToTasks,
                    donePropertyId = db.donePropertyUid?.let { propertyUidToId[it] },
                    deadlinePropertyId = db.deadlinePropertyUid?.let { propertyUidToId[it] },
                    recurrencePropertyId = db.recurrencePropertyUid?.let { propertyUidToId[it] },
                    updatedAt = Instant.ofEpochMilli(record.updatedAt),
                )
            )
        }

        // Pass 4 — Canvas nodes/edges for winning CANVAS records. Safe as blind
        // delete-and-reinsert (unlike Properties): nothing outside this subsystem references
        // a node's id.
        for (record in records) {
            if (record.uid !in wonUids) continue
            val canvas = record.canvas ?: continue
            val pageId = uidToId.getValue(record.uid)
            val now = Instant.ofEpochMilli(record.updatedAt)
            val pageCanvasId = pageCanvasDao.getByPageId(pageId)?.also { pageCanvasDao.update(it.copy(updatedAt = now)) }?.id
                ?: pageCanvasDao.insert(PageCanvas(pageId = pageId, createdAt = now, updatedAt = now))
            canvasEdgeDao.deleteForCanvas(pageCanvasId)
            canvasNodeDao.deleteForCanvas(pageCanvasId)
            val nodeUidToId = mutableMapOf<String, Long>()
            for (n in canvas.nodes) {
                nodeUidToId[n.uid] = canvasNodeDao.insert(
                    CanvasNode(
                        uid = n.uid, canvasId = pageCanvasId, type = CanvasNodeType.valueOf(n.type),
                        x = n.x, y = n.y, width = n.width, height = n.height, text = n.text,
                        embeddedPageId = n.embeddedPageUid?.let { uidToId[it] },
                        createdAt = Instant.ofEpochMilli(n.createdAt), updatedAt = Instant.ofEpochMilli(n.updatedAt),
                    )
                )
            }
            for (e in canvas.edges) {
                val from = nodeUidToId[e.fromNodeUid] ?: continue
                val to = nodeUidToId[e.toNodeUid] ?: continue
                canvasEdgeDao.insert(CanvasEdge(canvasId = pageCanvasId, fromNodeId = from, toNodeId = to, direction = CanvasArrowDirection.valueOf(e.direction), label = e.label))
            }
        }

        // Pass 5 — Blocks/Tags/PropertyValues for every winner. Runs last since row
        // PropertyValues resolve against Pass 3's now-final propertyUidToId.
        for (record in records) {
            if (record.uid !in wonUids) continue
            val pageId = uidToId.getValue(record.uid)

            blockDao.deleteForPage(pageId)
            val blockUidToId = mutableMapOf<String, Long>()
            for (b in record.blocks) {
                blockUidToId[b.uid] = blockDao.insert(
                    Block(
                        uid = b.uid, pageId = pageId, type = BlockType.valueOf(b.type), order = b.order,
                        parentBlockId = null,
                        content = b.content,
                        formattingSpans = b.formattingSpans.mapNotNull { it.toEntity(uidToId) },
                        checked = b.checked, codeLanguage = b.codeLanguage, calloutIcon = b.calloutIcon, calloutColor = b.calloutColor,
                        mentionedPageId = b.mentionedPageUid?.let { uidToId[it] },
                        toggleExpanded = b.toggleExpanded,
                        createdAt = Instant.ofEpochMilli(b.createdAt), updatedAt = Instant.ofEpochMilli(b.updatedAt),
                    )
                )
            }
            for (b in record.blocks) {
                val parentId = b.parentBlockUid?.let { blockUidToId[it] } ?: continue
                val newId = blockUidToId.getValue(b.uid)
                val inserted = blockDao.getById(newId) ?: continue
                blockDao.update(inserted.copy(parentBlockId = parentId))
            }
            pageContentRepository.rebuildFtsForPage(pageId)

            tagDao.clearForPage(pageId)
            for (tagName in record.tags) {
                val tagId = tagDao.findByName(tagName)?.id ?: tagDao.insert(Tag(name = tagName))
                tagDao.addToPage(PageTag(pageId = pageId, tagId = tagId))
            }

            if (record.databaseUid != null) {
                propertyValueDao.deleteAllForRow(pageId)
                for (pv in record.propertyValues) {
                    val propertyId = propertyUidToId[pv.propertyUid] ?: continue
                    propertyValueDao.insert(PropertyValue(propertyId = propertyId, rowPageId = pageId, value = pv.value))
                }
            }
        }
    }

    suspend fun mergeRelations(records: List<PageRelationSnapshotRecord>) {
        if (records.isEmpty()) return
        val uidToId = pageDao.getAll().associate { it.uid to it.id }
        for (r in records) {
            val fromId = uidToId[r.fromPageUid] ?: continue
            val toId = uidToId[r.toPageUid] ?: continue
            pageRelationDao.addRelation(fromId, toId, Instant.ofEpochMilli(r.createdAt))
        }
    }

    private fun PageSnapshotRecord.toBareEntity(): Page = Page(
        uid = uid, title = title, icon = icon, kind = PageKind.valueOf(kind),
        parentId = null, databaseId = null,
        isTemplate = isTemplate, deletedAt = deletedAt?.let(Instant::ofEpochMilli),
        createdAt = Instant.ofEpochMilli(createdAt), updatedAt = Instant.ofEpochMilli(updatedAt),
    )

    private fun FormattingSpanSnapshot.toEntity(uidToId: Map<String, Long>): FormattingSpan? {
        val entityStyle = when (val s = style) {
            is SpanStyleSnapshot.Bold -> SpanStyle.Bold
            is SpanStyleSnapshot.Italic -> SpanStyle.Italic
            is SpanStyleSnapshot.Strikethrough -> SpanStyle.Strikethrough
            is SpanStyleSnapshot.InlineCode -> SpanStyle.InlineCode
            is SpanStyleSnapshot.Link -> SpanStyle.Link(s.url)
            is SpanStyleSnapshot.PageMention -> uidToId[s.pageUid]?.let { SpanStyle.PageMention(it) } ?: return null
        }
        return FormattingSpan(start, end, entityStyle)
    }
}
