package com.tendril.app.domain.history

import com.tendril.app.data.page.BlockDao
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.PageRevision
import com.tendril.app.data.page.PageRevisionDao
import com.tendril.app.data.page.RevisionReason
import com.tendril.app.sync.BlockSnapshotRecord
import com.tendril.app.sync.replaceBlocks
import com.tendril.app.sync.toSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

/**
 * §0.6.13 / B§6 #12 — a page's kept bodies. Three ways a revision is taken, three rules:
 *
 * - **before an edit** ([captureBeforeEdit]): the body as it stands when an editing window
 *   opens — at most one per [EDIT_WINDOW] per page, so a burst of keystrokes yields one revision,
 *   the state the burst started from; the live page is always the newest state and needs none.
 * - **before a merge** ([captureBeforeMerge]): the body a winning remote record is about to
 *   replace — unthrottled, but skipped when it equals the last kept body, so a page unchanged
 *   here since its last sync produces nothing and §9.4's LWW loser is what gets kept.
 * - **before a restore** ([restore]): unconditional, so a restore is itself undoable.
 *
 * Every capture prunes the page to [KEEP]. Bodies are the sync's own `BlockSnapshotRecord`
 * JSON (page mentions by uid), so [restore] rebuilds through [replaceBlocks] — the merge's
 * proven path — and a mention of a page since purged simply drops its span.
 */
class PageHistory(
    private val pageDao: PageDao,
    private val blockDao: BlockDao,
    private val revisionDao: PageRevisionDao,
    private val clock: () -> Instant = Instant::now,
) {
    fun revisions(pageId: Long): Flow<List<PageRevision>> = revisionDao.observeForPage(pageId)

    suspend fun captureBeforeEdit(pageId: Long) {
        val latest = revisionDao.latestForPage(pageId)
        if (latest != null && Duration.between(latest.takenAt, clock()) < EDIT_WINDOW) return
        capture(pageId, RevisionReason.EDIT)
    }

    suspend fun captureBeforeMerge(pageId: Long) {
        val body = bodyOf(pageId) ?: return
        val latest = revisionDao.latestForPage(pageId)
        if (latest != null && latest.title == body.title && latest.blocksJson == body.blocksJson) return
        store(pageId, RevisionReason.MERGE, body)
    }

    /** The page's title and blocks become [revisionId]'s; what they were is kept first. The caller
     * re-indexes and touches the page as after any edit. */
    suspend fun restore(revisionId: Long) {
        val revision = revisionDao.getById(revisionId) ?: return
        val page = pageDao.getById(revision.pageId) ?: return
        capture(page.id, RevisionReason.RESTORE)
        val records = json.decodeFromString(ListSerializer(BlockSnapshotRecord.serializer()), revision.blocksJson)
        val decoded = records.mapNotNull { r -> BlockType.entries.firstOrNull { it.name == r.type }?.let { r to it } }
        val uidToId = pageDao.getAll().associate { it.uid to it.id }
        replaceBlocks(blockDao, page.id, decoded, uidToId)
        pageDao.update(page.copy(title = revision.title, updatedAt = clock()))
    }

    private suspend fun capture(pageId: Long, reason: RevisionReason) {
        val body = bodyOf(pageId) ?: return
        store(pageId, reason, body)
    }

    private suspend fun store(pageId: Long, reason: RevisionReason, body: Body) {
        revisionDao.insert(PageRevision(pageId = pageId, takenAt = clock(), reason = reason, title = body.title, blocksJson = body.blocksJson, blockCount = body.blockCount))
        revisionDao.pruneBeyond(pageId, KEEP)
    }

    private class Body(val title: String, val blocksJson: String, val blockCount: Int)

    private suspend fun bodyOf(pageId: Long): Body? {
        val page = pageDao.getById(pageId) ?: return null
        val blocks = blockDao.getForPage(pageId)
        val pageIdToUid = pageDao.getAll().associate { it.id to it.uid }
        val blockIdToUid = blocks.associate { it.id to it.uid }
        val records = blocks.map { it.toSnapshot(pageIdToUid, blockIdToUid) }
        return Body(page.title, json.encodeToString(ListSerializer(BlockSnapshotRecord.serializer()), records), blocks.size)
    }

    companion object {
        val EDIT_WINDOW: Duration = Duration.ofMinutes(10)
        const val KEEP = 50
        private val json = Json { ignoreUnknownKeys = true }
    }
}
