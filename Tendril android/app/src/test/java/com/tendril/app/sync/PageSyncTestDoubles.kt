package com.tendril.app.sync

import com.tendril.app.data.canvas.CanvasEdge
import com.tendril.app.data.canvas.CanvasEdgeDao
import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeDao
import com.tendril.app.data.canvas.PageCanvas
import com.tendril.app.data.canvas.PageCanvasDao
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockDao
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.page.PageFtsDao
import com.tendril.app.data.page.PageFtsEntry
import com.tendril.app.data.page.PageRelation
import com.tendril.app.data.page.PageRelationDao
import com.tendril.app.data.page.PageSearchHit
import com.tendril.app.data.page.PageLabel
import com.tendril.app.data.page.Label
import com.tendril.app.data.page.LabelDao
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.PageDatabaseDao
import com.tendril.app.data.pagedatabase.PageDatabaseView
import com.tendril.app.data.pagedatabase.PageDatabaseViewDao
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyDao
import com.tendril.app.data.pagedatabase.PropertyValue
import com.tendril.app.data.pagedatabase.PropertyValueDao
import com.tendril.app.data.purge.PurgedKind
import com.tendril.app.data.purge.PurgedRecord
import com.tendril.app.data.purge.PurgedRecordDao
import com.tendril.app.domain.EntryScheduleCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/**
 * In-memory doubles for the Page half of snapshot sync, in the same hand-written style as
 * [FakeEntryDao] and for the same reason: the merge in
 * [com.tendril.app.sync.PagesSyncEngine] is what the assertions are about, so its
 * collaborators' behaviour should be readable rather than assembled out of stubs.
 *
 * The twelve DAOs share one [FakePageStore] instead of each holding its own map. That is the
 * whole point of the design: Room's schema hangs blocks, labels, property values, views and
 * canvas content off a Page by `ON DELETE CASCADE`, so a fake where deleting a Page leaves its
 * blocks behind would let a merge test pass against behaviour the real database does not have.
 * Since the change under test *deletes rows during a merge*, that difference is exactly the one
 * that would matter.
 */
class FakePageStore {
    val pages = linkedMapOf<Long, Page>()
    val blocks = linkedMapOf<Long, Block>()
    val labels = linkedMapOf<Long, Label>()
    val pageTags = mutableListOf<PageLabel>()
    val databases = linkedMapOf<Long, PageDatabase>()
    val properties = linkedMapOf<Long, Property>()
    val propertyValues = linkedMapOf<Long, PropertyValue>()
    val views = linkedMapOf<Long, PageDatabaseView>()
    val canvases = linkedMapOf<Long, PageCanvas>()
    val canvasNodes = linkedMapOf<Long, CanvasNode>()
    val canvasEdges = linkedMapOf<Long, CanvasEdge>()
    val relations = linkedMapOf<Long, PageRelation>()

    /** `page_fts` keyed by pageId, maintained by [com.tendril.app.domain.PageContentRepository]
     * rather than by a cascade — an FTS4 table has no foreign keys. */
    val fts = linkedMapOf<Long, String>()

    private var next = 1L

    fun nextId(): Long = next++

    /** Mirrors the `ON DELETE CASCADE` chain the Room entities declare, so a test that deletes
     * a Page sees the same wreckage the real database would leave. */
    fun deletePageCascade(pageId: Long) {
        pages.remove(pageId)
        blocks.values.filter { it.pageId == pageId }.forEach { blocks.remove(it.id) }
        pageTags.removeAll { it.pageId == pageId }
        propertyValues.values.filter { it.rowPageId == pageId }.forEach { propertyValues.remove(it.id) }
        databases.values.filter { it.pageId == pageId }.forEach { deleteDatabaseCascade(it.id) }
        canvases.values.filter { it.pageId == pageId }.forEach { deleteCanvasCascade(it.id) }
        canvasNodes.values.filter { it.embeddedPageId == pageId }.forEach { deleteNodeCascade(it.id) }
        relations.values.filter { it.fromPageId == pageId || it.toPageId == pageId }.forEach { relations.remove(it.id) }
    }

    fun deleteDatabaseCascade(databaseId: Long) {
        databases.remove(databaseId)
        properties.values.filter { it.databaseId == databaseId }.forEach { deletePropertyCascade(it.id) }
        views.values.filter { it.databaseId == databaseId }.forEach { views.remove(it.id) }
    }

    fun deletePropertyCascade(propertyId: Long) {
        properties.remove(propertyId)
        propertyValues.values.filter { it.propertyId == propertyId }.forEach { propertyValues.remove(it.id) }
    }

    fun deleteCanvasCascade(canvasId: Long) {
        canvases.remove(canvasId)
        canvasNodes.values.filter { it.canvasId == canvasId }.forEach { deleteNodeCascade(it.id) }
        canvasEdges.values.filter { it.canvasId == canvasId }.forEach { canvasEdges.remove(it.id) }
    }

    fun deleteNodeCascade(nodeId: Long) {
        canvasNodes.remove(nodeId)
        canvasEdges.values.filter { it.fromNodeId == nodeId || it.toNodeId == nodeId }.forEach { canvasEdges.remove(it.id) }
    }

    /** Convenience for arranging a test's starting state without going through a merge. */
    fun seedPage(page: Page): Long {
        val id = nextId()
        pages[id] = page.copy(id = id)
        return id
    }
}

class FakePageDao(private val store: FakePageStore) : PageDao {
    override suspend fun insert(page: Page): Long {
        val id = store.nextId()
        store.pages[id] = page.copy(id = id)
        return id
    }

    override suspend fun update(page: Page) { store.pages[page.id] = page }
    override suspend fun getById(id: Long): Page? = store.pages[id]
    override suspend fun getByUid(uid: String): Page? = store.pages.values.firstOrNull { it.uid == uid }
    override suspend fun getAll(): List<Page> = store.pages.values.toList()

    override suspend fun getRowsOf(databaseId: Long): List<Page> =
        store.pages.values.filter { it.databaseId == databaseId && it.deletedAt == null }

    override suspend fun getMembersOf(databaseId: Long, labelId: Long?): List<Page> = membersOf(databaseId, labelId)
    override fun observeMembersOf(databaseId: Long, labelId: Long?): Flow<List<Page>> = flowOf(membersOf(databaseId, labelId))
    private fun membersOf(databaseId: Long, labelId: Long?): List<Page> {
        val labelled = if (labelId == null) emptySet() else store.pageTags.filter { it.tagId == labelId }.map { it.pageId }.toSet()
        return store.pages.values
            .filter { it.deletedAt == null && !it.isTemplate && (it.databaseId == databaseId || it.id in labelled) }
            .sortedBy { it.id }
    }

    override suspend fun getByKind(kind: PageKind): List<Page> =
        store.pages.values.filter { it.kind == kind && it.deletedAt == null }.sortedBy { it.title }

    override suspend fun updateParentAndDatabase(id: Long, parentId: Long?, databaseId: Long?) {
        store.pages[id]?.let { store.pages[id] = it.copy(parentId = parentId, databaseId = databaseId) }
    }

    /** Column-scoped like the real query: a `touch` that rewrote the whole row would hide
     * exactly the clobbering the narrow `UPDATE` exists to prevent. */
    override suspend fun touch(id: Long, at: Instant) {
        store.pages[id]?.let { store.pages[id] = it.copy(updatedAt = at) }
    }

    override suspend fun softDelete(id: Long, deletedAt: Instant) {
        store.pages[id]?.let { store.pages[id] = it.copy(deletedAt = deletedAt, updatedAt = deletedAt) }
    }

    override suspend fun restore(id: Long, restoredAt: Instant) {
        store.pages[id]?.let { store.pages[id] = it.copy(deletedAt = null, updatedAt = restoredAt) }
    }

    override suspend fun deleteForever(id: Long) = store.deletePageCascade(id)

    override suspend fun findRootByTitle(title: String): Page? =
        store.pages.values.firstOrNull { it.title == title && it.parentId == null && it.deletedAt == null }

    override suspend fun findChildByTitle(parentId: Long, title: String): Page? =
        store.pages.values.firstOrNull { it.parentId == parentId && it.title == title && it.deletedAt == null }

    override fun observeByIds(ids: List<Long>): Flow<List<Page>> = flowOf(ids.mapNotNull { store.pages[it] })
    override fun observeById(id: Long): Flow<Page?> = flowOf(store.pages[id])
    override fun observeRootPages(): Flow<List<Page>> = flowOf(
        store.pages.values.filter { it.parentId == null && !it.isTemplate && it.databaseId == null && it.deletedAt == null }
    )
    override fun observeTemplates(): Flow<List<Page>> = flowOf(store.pages.values.filter { it.isTemplate && it.deletedAt == null })
    override fun observeTrash(): Flow<List<Page>> = flowOf(store.pages.values.filter { it.deletedAt != null })
}

class FakeBlockDao(private val store: FakePageStore) : BlockDao {
    override suspend fun insert(block: Block): Long {
        val id = store.nextId()
        store.blocks[id] = block.copy(id = id)
        return id
    }

    override suspend fun update(block: Block) { store.blocks[block.id] = block }
    override suspend fun delete(id: Long) { store.blocks.remove(id) }
    override suspend fun deleteForPage(pageId: Long) {
        store.blocks.values.filter { it.pageId == pageId }.forEach { store.blocks.remove(it.id) }
    }

    override suspend fun getForPage(pageId: Long): List<Block> =
        store.blocks.values.filter { it.pageId == pageId }.sortedBy { it.order }

    override suspend fun getById(id: Long): Block? = store.blocks[id]

    override suspend fun getByUid(uid: String): Block? = store.blocks.values.firstOrNull { it.uid == uid }

    override suspend fun getWithLocalImage(): List<Block> =
        store.blocks.values.filter { it.imagePath != null }

    /** Column-scoped like the real query: only `imagePath` moves, so a test that expected a
     * whole-row write would fail here rather than passing on a fake that was more generous than
     * the DAO it stands in for. */
    override suspend fun attachImagePath(uid: String, path: String) {
        store.blocks.values.firstOrNull { it.uid == uid }
            ?.let { store.blocks[it.id] = it.copy(imagePath = path) }
    }

    override suspend fun getStandaloneMentionsOf(pageId: Long): List<Block> =
        store.blocks.values.filter { it.mentionedPageId == pageId }

    override suspend fun getAllStandaloneMentions(): List<Block> =
        store.blocks.values.filter { it.mentionedPageId != null }

    override suspend fun getBlocksWithAnySpans(): List<Block> =
        store.blocks.values.filter { it.formattingSpans.isNotEmpty() }

    override fun observeForPage(pageId: Long): Flow<List<Block>> =
        flowOf(store.blocks.values.filter { it.pageId == pageId }.sortedBy { it.order })

    override fun observeImagesForPages(pageIds: List<Long>): Flow<List<Block>> = flowOf(emptyList())
}

class FakeLabelDao(private val store: FakePageStore) : LabelDao {
    override suspend fun insert(label: Label): Long {
        val id = store.nextId()
        store.labels[id] = label.copy(id = id)
        return id
    }

    override suspend fun findByName(name: String): Label? = store.labels.values.firstOrNull { it.name == name }
    override suspend fun getById(id: Long): Label? = store.labels[id]
    override suspend fun search(query: String): List<Label> = store.labels.values.filter { it.name.contains(query) }

    override suspend fun getForPage(pageId: Long): List<Label> =
        store.pageTags.filter { it.pageId == pageId }.mapNotNull { store.labels[it.tagId] }

    override suspend fun addToPage(pageTag: PageLabel) {
        if (store.pageTags.none { it.pageId == pageTag.pageId && it.tagId == pageTag.tagId }) store.pageTags += pageTag
    }

    override suspend fun removeFromPage(pageId: Long, tagId: Long) {
        store.pageTags.removeAll { it.pageId == pageId && it.tagId == tagId }
    }

    override suspend fun clearForPage(pageId: Long) { store.pageTags.removeAll { it.pageId == pageId } }

    override fun observeAll(): Flow<List<Label>> = flowOf(store.labels.values.sortedBy { it.name })
    override fun observeForPage(pageId: Long): Flow<List<Label>> =
        flowOf(store.pageTags.filter { it.pageId == pageId }.mapNotNull { store.labels[it.tagId] })
    override fun observePageIdsForTags(tagIds: List<Long>): Flow<List<Long>> =
        flowOf(store.pageTags.filter { it.tagId in tagIds }.map { it.pageId }.distinct())
}

class FakePageDatabaseDao(private val store: FakePageStore) : PageDatabaseDao {
    override suspend fun insert(database: PageDatabase): Long {
        val id = store.nextId()
        store.databases[id] = database.copy(id = id)
        return id
    }

    override suspend fun update(database: PageDatabase) { store.databases[database.id] = database }
    override suspend fun getById(id: Long): PageDatabase? = store.databases[id]
    override suspend fun getByPageId(pageId: Long): PageDatabase? = store.databases.values.firstOrNull { it.pageId == pageId }
    override fun observeById(id: Long): Flow<PageDatabase?> = flowOf(store.databases[id])
    override fun observeByPageId(pageId: Long): Flow<PageDatabase?> =
        flowOf(store.databases.values.firstOrNull { it.pageId == pageId })
    override suspend fun getByLabelId(labelId: Long): List<PageDatabase> = store.databases.values.filter { it.labelId == labelId }
    override fun observeDatabasesForLabels(labelIds: List<Long>): Flow<List<PageDatabase>> =
        flowOf(store.databases.values.filter { it.labelId in labelIds && store.pages[it.pageId]?.deletedAt == null })
    override fun observeBoundLabelIds(): Flow<List<Long>> = flowOf(store.databases.values.mapNotNull { it.labelId })
}

class FakePropertyDao(private val store: FakePageStore) : PropertyDao {
    override suspend fun getByUid(uid: String): Property? = store.properties.values.find { it.uid == uid }

    override suspend fun insert(property: Property): Long {
        val id = store.nextId()
        store.properties[id] = property.copy(id = id)
        return id
    }

    override suspend fun update(property: Property) { store.properties[property.id] = property }

    /** Cascades to `property_values`, which is the reason the merge upserts Properties by uid
     * instead of replacing them — see the class doc on [com.tendril.app.sync.PagesSyncEngine]. */
    override suspend fun delete(id: Long) = store.deletePropertyCascade(id)

    override suspend fun getForDatabase(databaseId: Long): List<Property> =
        store.properties.values.filter { it.databaseId == databaseId }.sortedBy { it.order }

    override suspend fun getById(id: Long): Property? = store.properties[id]
    override suspend fun getAll(): List<Property> = store.properties.values.toList()

    override fun observeForDatabase(databaseId: Long): Flow<List<Property>> =
        flowOf(store.properties.values.filter { it.databaseId == databaseId }.sortedBy { it.order })
}

class FakePropertyValueDao(private val store: FakePageStore) : PropertyValueDao {
    override suspend fun insert(value: PropertyValue): Long {
        val id = store.nextId()
        store.propertyValues[id] = value.copy(id = id)
        return id
    }

    override suspend fun update(value: PropertyValue) { store.propertyValues[value.id] = value }

    override suspend fun getForRow(rowPageId: Long): List<PropertyValue> =
        store.propertyValues.values.filter { it.rowPageId == rowPageId }

    override suspend fun getForPropertyAndRow(propertyId: Long, rowPageId: Long): PropertyValue? =
        store.propertyValues.values.firstOrNull { it.propertyId == propertyId && it.rowPageId == rowPageId }

    override suspend fun getAllForProperty(propertyId: Long): List<PropertyValue> =
        store.propertyValues.values.filter { it.propertyId == propertyId }

    override suspend fun deleteAllForProperty(propertyId: Long) {
        store.propertyValues.values.filter { it.propertyId == propertyId }.forEach { store.propertyValues.remove(it.id) }
    }

    override suspend fun deleteAllForRow(rowPageId: Long) {
        store.propertyValues.values.filter { it.rowPageId == rowPageId }.forEach { store.propertyValues.remove(it.id) }
    }

    override fun observeForRow(rowPageId: Long): Flow<List<PropertyValue>> =
        flowOf(store.propertyValues.values.filter { it.rowPageId == rowPageId })

    override fun observeDateCells(): Flow<List<com.tendril.app.data.pagedatabase.DateCell>> = flowOf(emptyList())
    override fun observeForDatabase(databaseId: Long): Flow<List<PropertyValue>> = flowOf(
        store.propertyValues.values.filter { pv -> store.properties[pv.propertyId]?.databaseId == databaseId }
    )
}

class FakePageDatabaseViewDao(private val store: FakePageStore) : PageDatabaseViewDao {
    override suspend fun insert(view: PageDatabaseView): Long {
        val id = store.nextId()
        store.views[id] = view.copy(id = id)
        return id
    }

    override suspend fun update(view: PageDatabaseView) { store.views[view.id] = view }
    override suspend fun delete(id: Long) { store.views.remove(id) }

    override suspend fun getForDatabase(databaseId: Long): List<PageDatabaseView> =
        store.views.values.filter { it.databaseId == databaseId }.sortedBy { it.order }

    override suspend fun deleteAllForDatabase(databaseId: Long) {
        store.views.values.filter { it.databaseId == databaseId }.forEach { store.views.remove(it.id) }
    }

    override suspend fun countForDatabase(databaseId: Long): Int = store.views.values.count { it.databaseId == databaseId }

    override fun observeForDatabase(databaseId: Long): Flow<List<PageDatabaseView>> =
        flowOf(store.views.values.filter { it.databaseId == databaseId }.sortedBy { it.order })
}

class FakePageCanvasDao(private val store: FakePageStore) : PageCanvasDao {
    override suspend fun insert(canvas: PageCanvas): Long {
        val id = store.nextId()
        store.canvases[id] = canvas.copy(id = id)
        return id
    }

    override suspend fun update(canvas: PageCanvas) { store.canvases[canvas.id] = canvas }
    override suspend fun getByPageId(pageId: Long): PageCanvas? = store.canvases.values.firstOrNull { it.pageId == pageId }
    override fun observeByPageId(pageId: Long): Flow<PageCanvas?> =
        flowOf(store.canvases.values.firstOrNull { it.pageId == pageId })
}

class FakeCanvasNodeDao(private val store: FakePageStore) : CanvasNodeDao {
    override suspend fun insert(node: CanvasNode): Long {
        val id = store.nextId()
        store.canvasNodes[id] = node.copy(id = id)
        return id
    }

    override suspend fun update(node: CanvasNode) { store.canvasNodes[node.id] = node }
    override suspend fun delete(id: Long) = store.deleteNodeCascade(id)
    override suspend fun deleteForCanvas(canvasId: Long) {
        store.canvasNodes.values.filter { it.canvasId == canvasId }.forEach { store.deleteNodeCascade(it.id) }
    }

    override suspend fun getForCanvas(canvasId: Long): List<CanvasNode> =
        store.canvasNodes.values.filter { it.canvasId == canvasId }

    override fun observeForCanvas(canvasId: Long): Flow<List<CanvasNode>> =
        flowOf(store.canvasNodes.values.filter { it.canvasId == canvasId })
}

class FakeCanvasEdgeDao(private val store: FakePageStore) : CanvasEdgeDao {
    override suspend fun insert(edge: CanvasEdge): Long {
        val id = store.nextId()
        store.canvasEdges[id] = edge.copy(id = id)
        return id
    }

    override suspend fun update(edge: CanvasEdge) { store.canvasEdges[edge.id] = edge }
    override suspend fun delete(id: Long) { store.canvasEdges.remove(id) }
    override suspend fun deleteForCanvas(canvasId: Long) {
        store.canvasEdges.values.filter { it.canvasId == canvasId }.forEach { store.canvasEdges.remove(it.id) }
    }

    override suspend fun getForCanvas(canvasId: Long): List<CanvasEdge> =
        store.canvasEdges.values.filter { it.canvasId == canvasId }

    override fun observeForCanvas(canvasId: Long): Flow<List<CanvasEdge>> =
        flowOf(store.canvasEdges.values.filter { it.canvasId == canvasId })
}

class FakePageRelationDao(private val store: FakePageStore) : PageRelationDao {
    override suspend fun insert(relation: PageRelation): Long {
        val id = store.nextId()
        store.relations[id] = relation.copy(id = id)
        return id
    }

    override suspend fun getAll(): List<PageRelation> = store.relations.values.toList()

    override suspend fun countBetween(pageIdA: Long, pageIdB: Long): Int = store.relations.values.count {
        (it.fromPageId == pageIdA && it.toPageId == pageIdB) || (it.fromPageId == pageIdB && it.toPageId == pageIdA)
    }
}

class FakePageFtsDao(private val store: FakePageStore) : PageFtsDao {
    override suspend fun insert(entry: PageFtsEntry) { store.fts[entry.pageId] = entry.plainText }
    override suspend fun deleteForPage(pageId: Long) { store.fts.remove(pageId) }
    override suspend fun search(query: String): List<PageSearchHit> =
        store.fts.filter { it.value.contains(query.removeSuffix("*"), ignoreCase = true) }
            // The real query joins `pages` for title and icon (§3.1.5's search overlay shows
            // both); this store holds only indexed text, so the page's own row supplies them.
            .map { PageSearchHit(it.key, store.pages[it.key]?.title.orEmpty(), store.pages[it.key]?.icon, it.value) }
}

/**
 * Honours the real DAO's `OnConflictStrategy.IGNORE`: two devices purging the same record is
 * not a conflict, and the *first* purge's timestamp is the one that has to keep winning
 * comparisons. Replacing on insert here would quietly make every "stale purge" assertion pass
 * for the wrong reason.
 */
class FakePurgedRecordDao(seed: List<PurgedRecord> = emptyList()) : PurgedRecordDao {
    private val rows = linkedMapOf<Pair<PurgedKind, String>, PurgedRecord>()

    init { seed.forEach { rows.putIfAbsent(it.kind to it.uid, it) } }

    override suspend fun insert(record: PurgedRecord) { rows.putIfAbsent(record.kind to record.uid, record) }
    override suspend fun getAll(): List<PurgedRecord> = rows.values.toList()
    override suspend fun getForKind(kind: PurgedKind): List<PurgedRecord> = rows.values.filter { it.kind == kind }
    override suspend fun clear(kind: PurgedKind, uid: String) { rows.remove(kind to uid) }
    override suspend fun deleteAll() { rows.clear() }
}

/** Records what the registry tore down, so a test can assert an Entry's alarms came down
 * *before* its row did — the concern [com.tendril.app.domain.PurgeRegistry] documents for
 * purges arriving from another device. */
class RecordingEntryScheduleCoordinator : EntryScheduleCoordinator {
    val changed = mutableListOf<Entry>()
    val removed = mutableListOf<Entry>()

    override suspend fun onEntryChanged(entry: Entry) { changed += entry }
    override suspend fun onEntryRemoved(entry: Entry) { removed += entry }
}
