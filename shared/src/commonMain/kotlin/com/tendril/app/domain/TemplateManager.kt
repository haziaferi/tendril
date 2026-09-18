package com.tendril.app.domain

import com.tendril.app.data.canvas.CanvasEdgeDao
import com.tendril.app.data.canvas.CanvasNodeDao
import com.tendril.app.data.canvas.PageCanvas
import com.tendril.app.data.canvas.PageCanvasDao
import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockDao
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.PageDatabaseDao
import com.tendril.app.data.pagedatabase.PropertyDao
import java.time.Instant
import java.util.UUID

/**
 * §3.1.3 — "Save as template" / "New from template". A template captures a page's block
 * structure (for a Notion-like page) or its schema (for a Database) — "not a specific content
 * instance" — so this always deep-clones into a brand-new [Page] row rather than flagging the
 * live page itself as a template, which would repurpose the original instead of preserving it.
 * Row data (`property_values`, actual rows) is deliberately never cloned, matching that same
 * "structure, not instance" rule.
 */
class TemplateManager(
    private val pageDao: PageDao,
    private val blockDao: BlockDao,
    private val pageDatabaseDao: PageDatabaseDao,
    private val propertyDao: PropertyDao,
    private val pageCanvasDao: PageCanvasDao,
    private val canvasNodeDao: CanvasNodeDao,
    private val canvasEdgeDao: CanvasEdgeDao,
) {
    suspend fun saveAsTemplate(source: Page): Long {
        val now = Instant.now()
        val templateId = pageDao.insert(
            source.copy(id = 0, uid = UUID.randomUUID().toString(), isTemplate = true, parentId = null, databaseId = null, createdAt = now, updatedAt = now)
        )
        cloneStructure(source, templateId, now)
        return templateId
    }

    /** The reverse: a saved template becomes a new live, non-template page. Sync-to-Tasks
     * bindings are a relationship over live rows/Entries, not schema, so a template Database's
     * clone is never pre-synced — matches §5.3's own built-in "To-do database" shortcut, which
     * wires its Done property + binding through [DatabaseSyncManager] directly rather than
     * through a template at all. */
    suspend fun createFromTemplate(template: Page, title: String): Long {
        val now = Instant.now()
        val newId = pageDao.insert(
            template.copy(id = 0, uid = UUID.randomUUID().toString(), title = title.ifBlank { "Untitled" }, isTemplate = false, parentId = null, databaseId = null, createdAt = now, updatedAt = now)
        )
        cloneStructure(template, newId, now)
        return newId
    }

    private suspend fun cloneStructure(source: Page, targetPageId: Long, now: Instant) {
        if (source.kind == PageKind.DATABASE) {
            val sourceDb = pageDatabaseDao.getByPageId(source.id) ?: return
            val newDbId = pageDatabaseDao.insert(PageDatabase(pageId = targetPageId, syncToTasks = false, createdAt = now, updatedAt = now))
            propertyDao.getForDatabase(sourceDb.id).forEach { property ->
                propertyDao.insert(property.copy(id = 0, uid = UUID.randomUUID().toString(), databaseId = newDbId))
            }
        } else if (source.kind == PageKind.CANVAS) {
            // §0.10 item 15 (2026-09-18) — a canvas template is its board: every node (a card, a
            // frame, a page card still pointing at its page — a board of the same pages, again)
            // and every arrow, ids remapped so the arrows join the clones. A canvas has no blocks.
            val sourceCanvas = pageCanvasDao.getByPageId(source.id) ?: return
            val newCanvasId = pageCanvasDao.insert(PageCanvas(pageId = targetPageId, createdAt = now, updatedAt = now))
            val idMap = mutableMapOf<Long, Long>()
            canvasNodeDao.getForCanvas(sourceCanvas.id).forEach { node ->
                idMap[node.id] = canvasNodeDao.insert(node.copy(id = 0, uid = UUID.randomUUID().toString(), canvasId = newCanvasId, createdAt = now, updatedAt = now))
            }
            canvasEdgeDao.getForCanvas(sourceCanvas.id).forEach { edge ->
                val from = idMap[edge.fromNodeId] ?: return@forEach
                val to = idMap[edge.toNodeId] ?: return@forEach
                canvasEdgeDao.insert(edge.copy(id = 0, uid = UUID.randomUUID().toString(), canvasId = newCanvasId, fromNodeId = from, toNodeId = to))
            }
        } else {
            cloneBlocks(source.id, targetPageId)
        }
    }

    /** Block ids are remapped in a first pass, then [Block.parentBlockId] is fixed up in a
     * second pass once every clone's new id is known — a naive single-pass copy would leave
     * nested list-item/toggle-child blocks pointing at the *original* page's block ids. */
    private suspend fun cloneBlocks(sourcePageId: Long, targetPageId: Long) {
        val sourceBlocks = blockDao.getForPage(sourcePageId).sortedBy { it.order }
        val idMap = mutableMapOf<Long, Long>()
        for (block in sourceBlocks) {
            val newId = blockDao.insert(block.copy(id = 0, uid = UUID.randomUUID().toString(), pageId = targetPageId, parentBlockId = null))
            idMap[block.id] = newId
        }
        for (block in sourceBlocks) {
            val parentId = block.parentBlockId ?: continue
            val newParentId = idMap[parentId] ?: continue
            val newId = idMap.getValue(block.id)
            val cloned = blockDao.getById(newId) ?: continue
            blockDao.update(cloned.copy(parentBlockId = newParentId))
        }
    }
}
