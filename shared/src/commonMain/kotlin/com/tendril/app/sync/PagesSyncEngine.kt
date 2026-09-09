package com.tendril.app.sync

import com.tendril.app.data.canvas.CanvasArrowDirection
import com.tendril.app.data.canvas.CanvasEdge
import com.tendril.app.data.canvas.CanvasEdgeDao
import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeDao
import com.tendril.app.data.canvas.CanvasNodeType
import com.tendril.app.data.canvas.PageCanvas
import com.tendril.app.data.canvas.PageCanvasDao
import com.tendril.app.data.enumOrNull
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
import com.tendril.app.data.purge.PurgedKind
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.PurgeRegistry
import java.time.Instant

private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

/**
 * §9.4 — one record a merge pass could not fully decode, and therefore did not apply.
 *
 * [kind] is the record family ("page", "entry", "habit"), [uid] the record itself, and [detail]
 * the value that could not be read, quoted. Carried out to the caller rather than logged and
 * forgotten: a device that silently drops one of its peer's pages on every pass, forever, looks
 * exactly like a device that is in sync, and the person finds out on the day they go looking for
 * the page.
 */
data class QuarantinedRecord(val kind: String, val uid: String, val detail: String) {
    override fun toString(): String = "$kind $uid — unrecognised $detail"

    companion object {
        /** The record families, spelled once: the write pass matches on [PAGE] to decide which
         * page files it must not overwrite, and a literal in two files is a literal that ends up
         * spelled two ways. */
        const val PAGE = "page"
        const val ENTRY = "entry"
        const val HABIT = "habit"
        const val REMINDER = "reminder"
        const val COMPLETION = "completion"

        /**
         * Not a record family at all: the whole-file case, where [uid] is a file name rather than
         * a uid.
         *
         * A folder-wide array file whose *outer* JSON this build cannot read has no records in it
         * to name — that is the point, nothing in it could be reached. It is still the single most
         * consequential thing a pass can fail to read, because that one file carries every entry
         * or every habit in the folder, so it travels out on the same channel as the rest rather
         * than being handled silently by the write pass. The write pass's own answer to it is in
         * `SnapshotSyncOrchestrator.publishArrayFile`.
         */
        const val FILE = "file"
    }
}

/**
 * The one spelling of [QuarantinedRecord.detail] — `label "value"`, the unreadable value quoted
 * so it can be told apart from the prose around it in Settings' error line.
 *
 * Two quarantine paths fill that field, and two implementers each spelled the format for
 * themselves: pages come through `PageDecode.Unreadable` below, Entries and Habits through
 * [SnapshotDecodeException.detail] in `SnapshotMappers.kt`. That is how one of them eventually
 * gains a colon or loses the quotes and the same Settings sentence starts reading two ways
 * depending on which record family tripped it. The canonical spelling lives here, beside the
 * field whose shape it is; `SnapshotMappers`' own `undecodable` builds its exception from this
 * rather than from a second literal, so both families quote the same way by construction.
 */
fun unrecognisedValueDetail(label: String, raw: String?): String = "$label \"$raw\""

/**
 * What one [PagesSyncEngine.mergePages] call did.
 *
 * [lost] is §5.2's losing-record set, which the caller preserves beside the winner. [quarantined]
 * is the set this pass refused to read at all — and the caller has a second obligation for those,
 * beyond reporting them: **it must not export the page's local copy over the file it could not
 * read.** See [PagesSyncEngine.mergePages] for why a plain skip is worse than the bug it fixes.
 */
data class PageMergeOutcome(
    val lost: List<PageSnapshotRecord> = emptyList(),
    val quarantined: List<QuarantinedRecord> = emptyList(),
)

/**
 * §9.4 — completes the Page/Row/Database/Canvas half of snapshot sync (Entry/Habit's half
 * already lived in `SnapshotMappers.kt`, `SnapshotSyncOrchestrator` and `PortableArchive`).
 * Shared by both the continuous folder sync and the one-off portable export/import, matching
 * those callers' own "one schema, reused everywhere" rule — the merge
 * algorithm here is intricate enough (five ordered passes, several cross-page FK resolutions)
 * that duplicating it per caller the way the simpler Entry/Habit merges are today would be a
 * real maintenance risk, not just more typing.
 *
 * Every FK in [PageSnapshotRecord] and friends travels as a `uid`; resolving it back to a
 * local Room id follows the same "drop the link if the target hasn't merged in yet, self-heal
 * on a later sync pass" rule `SnapshotMappers.kt`'s `toEntity` already established for
 * `originalEntryUid` — except for a Page's own tree position (`parentUid`/`databaseUid`),
 * which gets a real two-pass fixup instead of drop-and-heal: unlike a rare recurring-exception
 * backlink, losing a page's parent on every first sync would misplace most of a person's
 * Pages hub, not just one edge case. That fixup used to hold only *within* one batch — a parent
 * arriving in a later one never healed the child, because by then the child was no longer a
 * winner and Pass 2 skipped it. It now repairs unresolved positions on every pass, for winners
 * and non-winners alike; see Pass 2 for why the non-winner case fills nulls only.
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
    private val purgeRegistry: PurgeRegistry,
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
     * why this can't be a simple per-record loop the way Entry/Habit's merge is.
     *
     * Records whose `uid` isn't a plain UUID are dropped before anything else looks at them.
     * A page's uid is reflected back out as a *filename* on the next write pass
     * (`pages/<uid>.json`, see the snapshot orchestrator), and these records arrive from a
     * file someone was handed — an archive, or whatever a sync peer put in the folder. A uid
     * of `../../…` therefore escaped the sync folder entirely on the desktop store, whose
     * `Path.resolve` honours `..`. Every uid this app generates is
     * `UUID.randomUUID().toString()`, so nothing legitimate is turned away. */
    /**
     * §5.2 / §9.4 — merges [allRecords], and reports back the two sets of records that would
     * otherwise have been discarded silently: [PageMergeOutcome.lost] and
     * [PageMergeOutcome.quarantined].
     *
     * Whole-page LWW is a deliberate v1 decision and this does not change it: the winner still
     * wins outright. What changes is that the loser stops vanishing. The caller writes each
     * returned record beside the winner, the way Syncthing itself keeps the copy that lost a
     * race, so a concurrent edit made on another device is recoverable by hand instead of gone.
     *
     * A record is reported as lost only when its content actually *differs* from what this device holds.
     * Without vector clocks there is no way to tell a genuine concurrent edit from a merely
     * stale copy of a version we already have — but an identical copy has nothing in it to
     * preserve, and that check alone is what keeps the folder from filling with duplicates of
     * itself on every sync. An equal `updatedAt` is the same version and never counts as a loss.
     *
     * ### Quarantine: every record decodes before anything local is touched
     *
     * Enums travel as `.name` Strings, so a newer build writes values this one has never heard
     * of — the ordinary case for two devices on one folder, not corruption. Those values used to
     * be parsed *in the middle of* the passes, which was worse than merely fragile: Pass 5 called
     * `blockDao.deleteForPage(pageId)` and only then `BlockType.valueOf`, so an unrecognised block
     * type destroyed this device's own blocks and *then* threw, aborting the pass for every other
     * page in the batch. Two losses from one string nobody could read.
     *
     * So the whole record — kind, blocks, properties, views, canvas nodes and edges — is decoded
     * up front, and a record with anything unreadable in it is quarantined: skipped entire, with
     * this device's local copy left exactly as it was, reported in [PageMergeOutcome.quarantined],
     * and (the caller's half, and the clause that makes this quarantine rather than a plain skip)
     * **suppressed from the pass's export**. Skipping alone would be worse than the bug: the write
     * pass republishes every local page unconditionally, so this device's stale copy would land on
     * top of the peer's newer file, unrecoverably and with no `.tendril-lost-` copy kept.
     *
     * ### …and quarantine travels along references
     *
     * Per-record quarantine is not enough, because records point at each other. The damage does
     * not have to arrive in the unreadable record: it arrives in a *perfectly readable* one that
     * merely names the unreadable one, and adopting that record means resolving the reference to
     * null and then republishing the nulled result over the folder. Two ways that has already
     * cost real content:
     *
     *  - a peer writes `P` with a `PageKind` this build has no member for and `Q` with
     *    `parentUid = P`. `P` is quarantined, so it is never inserted and `uidToId["P"]` is empty;
     *    `Q` wins on time, Pass 2 writes `parentId = null`, and the next export publishes `Q` at
     *    the root. One unreadable string re-parents a subtree, folder-wide.
     *  - a peer writes DATABASE page `D` with a column of a `PropertyType` this build has no
     *    member for, plus its row pages `R1..Rn` carrying the cells under it. `D` is quarantined,
     *    so neither it nor its Properties are inserted and `propertyUidToId` cannot resolve that
     *    column; the rows merge anyway, Pass 5 drops the cell values it cannot place, and the
     *    export republishes the rows without them. Somebody typed those cells.
     *
     * So a record whose referent this pass quarantined is itself quarantined, to a fixpoint — the
     * references being `parentUid`, `databaseUid`, a block's `mentionedPageUid`, a
     * [SpanStyleSnapshot.PageMention] inside a block's formatting spans, a canvas node's
     * `embeddedPageUid`, and the `propertyUid` on each of a row's `propertyValues`. §3.4's manual
     * edges need no clause here: [mergeRelations] already hands back every edge whose endpoint is
     * missing locally, and a quarantined page is missing locally by construction.
     *
     * **Only when the reference would genuinely dangle.** A quarantined page that this device
     * *already holds a row for* resolves perfectly well — the peer's newer copy is unreadable, but
     * the id the reference needs is right there, and adopting the referring record loses nothing
     * and republishes nothing nulled. Quarantining on the reference alone would freeze whole
     * subtrees over a page every device has had for months. The test is therefore "referent
     * quarantined **and** absent from local state", which is also what keeps the blast radius
     * proportional to the actual novelty.
     *
     * **…and "already holds it" is a question about the thing referenced, not about the record
     * that carries it.** A page reference resolves when the page is local; a *column* reference
     * resolves only when that column is local, and against a DATABASE page this device has had
     * for months those are not the same question. The peer rewrites `D` in a shape this build
     * cannot parse into a record at all, adding a column `C` to it, and sends row `R` with a cell
     * under `C`. `D` is local, so the page-level test above lets `R` straight through; `C` exists
     * only inside the file that would not parse, so Pass 5 cannot place the cell, drops it, and
     * the export republishes `R` without it. Somebody typed that value, and it is gone from every
     * device. (The same schema race with `D` merely carrying an unreadable `PropertyType` is
     * already covered, because a record that *decoded* still names its columns — see
     * [quarantineSweep]'s `withhold`. It is the file that never became a record that this clause
     * is for, and those are the files furthest ahead of this build.) So a row whose own database
     * was withheld this pass is quarantined when it carries a cell for a column nothing in the
     * batch defines and this device does not hold — and only then, because a row of that same `D`
     * whose cells are all columns this device already has resolves completely and must merge.
     *
     * @param unreadableUids page uids the *caller* already knows it could not read. A
     * `pages/<uid>.json` whose JSON will not decode into a [PageSnapshotRecord] at all never
     * reaches this function as a record, so the propagation above would otherwise be blind to
     * exactly the file that is furthest ahead of this build. A uid with a tombstone against it is
     * deliberately *not* treated as unreadable: that page is gone on purpose (§5.5.1.1) and a
     * reference to it is meant to resolve to nothing — the same distinction [mergeRelations]'
     * caller draws before it republishes a dropped edge.
     */
    suspend fun mergePages(
        allRecords: List<PageSnapshotRecord>,
        unreadableUids: Set<String> = emptySet(),
    ): PageMergeOutcome {
        // A purged page stays purged: its own snapshot file is still sitting in the folder, and
        // without this the very next merge inserts it straight back (§5.5.1.1). A record edited
        // after the purge is the one exception, and `isPurged` handles it by superseding.
        val tombstones = purgeRegistry.tombstones(PurgedKind.PAGE)
        val admitted = allRecords.filter {
            isSafeUid(it.uid) && !purgeRegistry.isPurged(PurgedKind.PAGE, it.uid, Instant.ofEpochMilli(it.updatedAt), tombstones)
        }
        if (admitted.isEmpty()) return PageMergeOutcome()

        // Decode first, mutate second — the ordering *is* the fix; see the doc above. Local state
        // is read before the sweep rather than after it because the sweep needs it: whether a
        // reference to a quarantined page would actually dangle is a question about what this
        // device already holds, not about what arrived.
        val localPages = pageDao.getAll()
        val sweep = quarantineSweep(
            admitted = admitted,
            localPageUids = localPages.mapTo(mutableSetOf()) { it.uid },
            localPropertyUids = propertyDao.getAll().mapTo(mutableSetOf()) { it.uid },
            // A page deliberately purged is not a page this build failed to read — see the
            // @param note on this function.
            unreadableUids = unreadableUids - tombstones.keys,
        )
        val quarantined = sweep.reports
        val records = sweep.readable
        if (records.isEmpty()) return PageMergeOutcome(quarantined = quarantined)
        val uidToId = localPages.associate { it.uid to it.id }.toMutableMap()
        val wonUids = mutableSetOf<String>()
        // The local copies about to be replaced. A winning record overwrites the page row and
        // Pass 5 clears and rebuilds its blocks, tags and cell values, so whatever this device
        // held is gone by the time the passes finish -- and unlike a losing *remote* record it
        // is not sitting in a file anywhere. Captured here, before Pass 1, because afterwards
        // there is nothing left to capture. Same deferral as `candidateLosers` below: the
        // export is only paid for when a record actually stands to overwrite something.
        val localUpdatedAt = localPages.associate { it.uid to it.updatedAt }
        val overwritten: Map<String, PageSnapshotRecord> =
            if (records.any { r -> localUpdatedAt[r.uid]?.let { Instant.ofEpochMilli(r.updatedAt).isAfter(it) } == true }) {
                exportPages().associateBy { it.uid }
            } else {
                emptyMap()
            }
        val overwrittenUids = mutableSetOf<String>()
        // Records that lost on timestamp. Whether each is a real loss depends on its content
        // differing, but that comparison needs exportPages(), which walks every page on the
        // device -- so it is deferred until something is actually a candidate, and skipped
        // entirely when nothing is. mergePages runs once per conflict file as well as once per
        // pass, and paying for a full export each time would make the merge O(files x pages).
        val candidateLosers = mutableListOf<PageSnapshotRecord>()

        // Pass 1 — upsert bare Page rows (tree position deferred to Pass 2); ensure every
        // DATABASE-kind page in the batch has a PageDatabase shell row to hang properties off.
        for (page in records) {
            val local = pageDao.getByUid(page.uid)
            val remoteUpdatedAt = Instant.ofEpochMilli(page.updatedAt)
            val id: Long
            if (local == null) {
                id = pageDao.insert(page.toBareEntity())
                uidToId[page.uid] = id
                wonUids += page.uid
            } else if (remoteUpdatedAt.isAfter(local.updatedAt)) {
                id = local.id
                pageDao.update(page.toBareEntity().copy(id = id, parentId = local.parentId, databaseId = local.databaseId))
                wonUids += page.uid
                overwrittenUids += page.uid
            } else {
                id = local.id
                // Strictly older: an equal timestamp is the same version, not a race.
                if (remoteUpdatedAt.isBefore(local.updatedAt)) candidateLosers += page.record
            }
            if (page.kind == PageKind.DATABASE && pageDatabaseDao.getByPageId(id) == null) {
                val now = Instant.ofEpochMilli(page.updatedAt)
                pageDatabaseDao.insert(PageDatabase(pageId = id, createdAt = now, updatedAt = now))
            }
        }

        // Pass 2 — every page (and every DATABASE page's PageDatabase shell) in the batch now
        // has a stable id, so a parent/database FK can resolve for real.
        //
        // This used to run for winners only, and that made a lost parent permanent. A child
        // arriving before its parent finds `uidToId[parentUid]` empty and drops to root; on the
        // next pass the parent is finally present, but the child's `updatedAt` is no longer newer
        // than the local row it just wrote, so it is not a winner, so it is skipped — and nothing
        // ever revisits it. The class doc's "a real two-pass fixup instead of drop-and-heal" was
        // true only within a single batch.
        //
        // So the loop now visits every record, and does one of two different things:
        //
        //  - a winner has its tree position written outright, as before;
        //  - a non-winner is only *repaired* — a null parent or database that can now resolve is
        //    filled in, and an already-resolved one is left exactly as it is.
        //
        // The asymmetry is the point. Letting a non-winner overwrite a resolved position would
        // hand a stale record the power to move a page, which is precisely what losing on
        // `updatedAt` is supposed to deny it. Filling a null takes nothing away from anyone: the
        // position was unknown, and now it is known.
        for (page in records) {
            val id = uidToId[page.uid] ?: continue
            val parentId = page.record.parentUid?.let { uidToId[it] }
            val databaseId = page.record.databaseUid?.let { dbPageUid -> uidToId[dbPageUid]?.let { pageDatabaseDao.getByPageId(it)?.id } }
            if (page.uid in wonUids) {
                pageDao.updateParentAndDatabase(id, parentId, databaseId)
                continue
            }
            val local = pageDao.getById(id) ?: continue
            val healedParent = local.parentId ?: parentId
            val healedDatabase = local.databaseId ?: databaseId
            if (healedParent != local.parentId || healedDatabase != local.databaseId) {
                pageDao.updateParentAndDatabase(id, healedParent, healedDatabase)
            }
        }

        // Pass 3 — Properties/Views for winning DATABASE records. Properties are upserted by
        // uid, not replaced (see class doc); propertyUidToId seeds from every existing
        // Property so Pass 5's row values can resolve even against a database this batch
        // never touched.
        val propertyUidToId = propertyDao.getAll().associate { it.uid to it.id }.toMutableMap()
        // A peer that has not seen the deletion still carries the column in its schema, and this
        // pass upserts by uid — so without this the tombstone is undone on the very next merge.
        val propertyTombstones = purgeRegistry.tombstones(PurgedKind.PROPERTY)
        for (page in records) {
            if (page.uid !in wonUids) continue
            val db = page.record.database ?: continue
            val pageId = uidToId.getValue(page.uid)
            val pageDatabaseId = pageDatabaseDao.getByPageId(pageId)?.id ?: continue

            val existingByUid = propertyDao.getForDatabase(pageDatabaseId).associateBy { it.uid }
            for ((p, type) in page.properties) {
                if (p.uid in propertyTombstones) continue
                val existing = existingByUid[p.uid]
                val id = if (existing != null) {
                    propertyDao.update(existing.copy(name = p.name, type = type, config = p.config, order = p.order))
                    existing.id
                } else {
                    propertyDao.insert(Property(uid = p.uid, databaseId = pageDatabaseId, name = p.name, type = type, config = p.config, order = p.order))
                }
                propertyUidToId[p.uid] = id
            }
            // A property absent from this record is NOT deleted. Absence means "hasn't arrived"
            // -- the rule SnapshotSyncOrchestrator.readAndMerge states for every other record,
            // and which this pass used to be the single exception to. A device that had not yet
            // seen a new column re-exported the schema without it, and the column plus every
            // row's value under it (`property_values` cascades off `properties`) was destroyed
            // on an ordinary two-device schema race, silently and with no `.tendril-lost` copy.
            // A real deletion travels as a `PurgedKind.PROPERTY` tombstone instead, the same
            // signal §5.5.1.1 already uses to tell a deliberate destruction from a slow arrival.

            pageDatabaseViewDao.deleteAllForDatabase(pageDatabaseId)
            for (view in page.views) {
                val v = view.record
                pageDatabaseViewDao.insert(
                    PageDatabaseView(
                        uid = v.uid, databaseId = pageDatabaseId, name = v.name, viewType = view.viewType,
                        groupByPropertyId = v.groupByPropertyUid?.let { propertyUidToId[it] },
                        datePropertyId = v.datePropertyUid?.let { propertyUidToId[it] },
                        visiblePropertyIds = v.visiblePropertyUids.mapNotNull { propertyUidToId[it] },
                        // `comparator` is non-null exactly when the record carries a filter — the
                        // decode pass guarantees it, and an unreadable one quarantined the whole
                        // page long before this line.
                        filter = v.filter?.let { f ->
                            val comparator = view.comparator ?: return@let null
                            propertyUidToId[f.propertyUid]?.let { ViewFilter(it, comparator, f.value) }
                        },
                        sortPropertyId = v.sortPropertyUid?.let { propertyUidToId[it] },
                        sortDirection = view.sortDirection,
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
                    updatedAt = Instant.ofEpochMilli(page.updatedAt),
                )
            )
        }

        // Pass 4 — Canvas nodes/edges for winning CANVAS records. Safe as blind
        // delete-and-reinsert (unlike Properties): nothing outside this subsystem references
        // a node's id.
        for (page in records) {
            if (page.uid !in wonUids) continue
            if (page.record.canvas == null) continue
            val pageId = uidToId.getValue(page.uid)
            val now = Instant.ofEpochMilli(page.updatedAt)
            val pageCanvasId = pageCanvasDao.getByPageId(pageId)?.also { pageCanvasDao.update(it.copy(updatedAt = now)) }?.id
                ?: pageCanvasDao.insert(PageCanvas(pageId = pageId, createdAt = now, updatedAt = now))
            // The clear below is reached only because every node type and arrow direction in this
            // record already decoded — the same decode-then-mutate rule Pass 5 exists to enforce.
            canvasEdgeDao.deleteForCanvas(pageCanvasId)
            canvasNodeDao.deleteForCanvas(pageCanvasId)
            val nodeUidToId = mutableMapOf<String, Long>()
            for ((n, type) in page.nodes) {
                nodeUidToId[n.uid] = canvasNodeDao.insert(
                    CanvasNode(
                        uid = n.uid, canvasId = pageCanvasId, type = type,
                        x = n.x, y = n.y, width = n.width, height = n.height, text = n.text,
                        embeddedPageId = n.embeddedPageUid?.let { uidToId[it] },
                        createdAt = Instant.ofEpochMilli(n.createdAt), updatedAt = Instant.ofEpochMilli(n.updatedAt),
                    )
                )
            }
            for ((e, direction) in page.edges) {
                val from = nodeUidToId[e.fromNodeUid] ?: continue
                val to = nodeUidToId[e.toNodeUid] ?: continue
                canvasEdgeDao.insert(CanvasEdge(canvasId = pageCanvasId, fromNodeId = from, toNodeId = to, direction = direction, label = e.label))
            }
        }

        // Pass 5 — Blocks/Tags/PropertyValues for every winner. Runs last since row
        // PropertyValues resolve against Pass 3's now-final propertyUidToId.
        for (page in records) {
            if (page.uid !in wonUids) continue
            val pageId = uidToId.getValue(page.uid)

            // `Block.imagePath` points into this device's app-private storage and is deliberately
            // excluded from the snapshot (see its own doc comment), so an incoming record can
            // never carry one. Rebuilding from that record therefore writes null over whatever
            // was here, and the picture is unlinked with the file still on disk. Held by uid
            // across the delete-and-reinsert instead -- the block is the same block, and the
            // remote simply has nothing to say about where this device keeps its copy.
            val imagePathByUid = blockDao.getForPage(pageId).mapNotNull { b -> b.imagePath?.let { b.uid to it } }.toMap()
            // This delete is the reason the decode happens up front. It used to run *before*
            // `BlockType.valueOf`, so a block type this build had never heard of destroyed the
            // page's local blocks and only then threw -- taking the rest of the pass with it. By
            // the time this line runs now, every block in the record has already decoded.
            blockDao.deleteForPage(pageId)
            val blockUidToId = mutableMapOf<String, Long>()
            for ((b, type) in page.blocks) {
                blockUidToId[b.uid] = blockDao.insert(
                    Block(
                        uid = b.uid, pageId = pageId, type = type, order = b.order,
                        parentBlockId = null,
                        imagePath = imagePathByUid[b.uid],
                        content = b.content,
                        formattingSpans = b.formattingSpans.mapNotNull { it.toEntity(uidToId) },
                        checked = b.checked, codeLanguage = b.codeLanguage, calloutIcon = b.calloutIcon, calloutColor = b.calloutColor,
                        mentionedPageId = b.mentionedPageUid?.let { uidToId[it] },
                        toggleExpanded = b.toggleExpanded,
                        createdAt = Instant.ofEpochMilli(b.createdAt), updatedAt = Instant.ofEpochMilli(b.updatedAt),
                    )
                )
            }
            for ((b, _) in page.blocks) {
                val parentId = b.parentBlockUid?.let { blockUidToId[it] } ?: continue
                val newId = blockUidToId.getValue(b.uid)
                val inserted = blockDao.getById(newId) ?: continue
                blockDao.update(inserted.copy(parentBlockId = parentId))
            }
            pageContentRepository.rebuildFtsForPage(pageId)

            tagDao.clearForPage(pageId)
            for (tagName in page.record.tags) {
                val tagId = tagDao.findByName(tagName)?.id ?: tagDao.insert(Tag(name = tagName))
                tagDao.addToPage(PageTag(pageId = pageId, tagId = tagId))
            }

            if (page.record.databaseUid != null) {
                propertyValueDao.deleteAllForRow(pageId)
                for (pv in page.record.propertyValues) {
                    val propertyId = propertyUidToId[pv.propertyUid] ?: continue
                    propertyValueDao.insert(PropertyValue(propertyId = propertyId, rowPageId = pageId, value = pv.value))
                }
            }
        }

        // Compared against the page as it stands once the pass is done, which is exactly what
        // replaced the loser. An identical body has nothing in it to preserve.
        if (candidateLosers.isEmpty() && overwrittenUids.isEmpty()) return PageMergeOutcome(quarantined = quarantined)
        val localByUid = exportPages().associateBy { it.uid }
        val lost = candidateLosers.filter { localByUid[it.uid] != it } +
            overwrittenUids.mapNotNull { uid ->
                val before = overwritten[uid] ?: return@mapNotNull null
                val after = localByUid[uid] ?: return@mapNotNull null
                // Content only. The timestamps necessarily differ -- that is why the remote won --
                // so comparing the records whole would preserve a copy on every routine catch-up,
                // where a peer re-exported the same content under a newer stamp and nothing was
                // lost at all.
                if (before.copy(updatedAt = after.updatedAt) == after) null else before
            }
        return PageMergeOutcome(lost = lost, quarantined = quarantined)
    }

    /**
     * §3.4 — takes in the manual edge set. Add-only and undirected, so there is no last-write-wins
     * comparison to make: an edge either resolves to two local pages and is adopted, or it does
     * not and is skipped.
     *
     * @return the records that were **not** adopted, which is the part the caller needs and the
     * part this used to swallow. An edge is dropped when either endpoint is missing locally, and a
     * page quarantined by [mergePages] is missing locally by construction — so a peer running a
     * newer build can link to a page this build cannot read, and every dropped edge here is one
     * the snapshot writer must republish rather than erase. Returning them lets
     * `SnapshotSyncOrchestrator` do that, and lets its conflict sweep tell "absorbed the file"
     * apart from "parsed the file and discarded everything in it".
     */
    suspend fun mergeRelations(records: List<PageRelationSnapshotRecord>): Set<PageRelationSnapshotRecord> {
        if (records.isEmpty()) return emptySet()
        val uidToId = pageDao.getAll().associate { it.uid to it.id }
        val unabsorbed = mutableSetOf<PageRelationSnapshotRecord>()
        for (r in records) {
            val fromId = uidToId[r.fromPageUid]
            val toId = uidToId[r.toPageUid]
            if (fromId == null || toId == null) {
                unabsorbed += r
                continue
            }
            pageRelationDao.addRelation(fromId, toId, Instant.ofEpochMilli(r.createdAt))
        }
        return unabsorbed
    }

    /**
     * Which of [records] this build cannot fully decode — the same check [mergePages] applies,
     * offered on its own for a caller that has to decide *before* it destroys anything.
     *
     * Quarantine works for the folder sync because there is always a local copy left standing to
     * fall back on. Restore-from-backup has no such copy: it clears the database and then applies
     * the archive, so "skip the record I can't read" there means the record is simply gone. Such a
     * caller wants to refuse the whole archive up front, and this is how it asks.
     *
     * Both empty-set arguments are the restore case stated as data rather than left implied.
     * Restore clears the database first, so *nothing* is resolvable locally — which makes every
     * reference to a quarantined record dangle, and makes this the strictest reading of the
     * transitive sweep [mergePages] applies. That is the right strictness here: the archive is
     * all there is, and a row whose column definition the archive carries in an unreadable page
     * would restore with the cell blank and nothing left to heal from.
     */
    fun undecodablePages(records: List<PageSnapshotRecord>): List<QuarantinedRecord> =
        quarantineSweep(
            admitted = records,
            localPageUids = emptySet(),
            localPropertyUids = emptySet(),
            unreadableUids = emptySet(),
        ).reports

    private fun isSafeUid(uid: String): Boolean = UUID_PATTERN.matches(uid)

    /** The per-page snapshot files the write pass should drop — every page known to be purged,
     * whose file nothing else in the folder will ever clear. */
    suspend fun purgedPageFileNames(): Set<String> =
        purgeRegistry.tombstones(PurgedKind.PAGE).keys.mapTo(mutableSetOf()) { "$it.json" }

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

// ------------------------------------------------------------------ decode-before-mutate
//
// One record, with every enum on it already resolved. The passes above take these rather than
// raw [PageSnapshotRecord]s, which is what makes "nothing local is touched until the whole
// record has decoded" a property of the *types* instead of a rule five loops have to remember.

private class DecodedPage(
    val record: PageSnapshotRecord,
    val kind: PageKind,
    /** Parallel to `record.blocks`, in order. */
    val blocks: List<Pair<BlockSnapshotRecord, BlockType>>,
    /** Parallel to `record.database?.properties`. */
    val properties: List<Pair<PropertySnapshotRecord, PropertyType>>,
    val views: List<DecodedView>,
    /** Parallel to `record.canvas?.nodes`. */
    val nodes: List<Pair<CanvasNodeSnapshotRecord, CanvasNodeType>>,
    /** Parallel to `record.canvas?.edges`. */
    val edges: List<Pair<CanvasEdgeSnapshotRecord, CanvasArrowDirection>>,
) {
    val uid: String get() = record.uid
    val updatedAt: Long get() = record.updatedAt

    fun toBareEntity(): Page = Page(
        uid = record.uid, title = record.title, icon = record.icon, kind = kind,
        parentId = null, databaseId = null,
        isTemplate = record.isTemplate, deletedAt = record.deletedAt?.let(Instant::ofEpochMilli),
        createdAt = Instant.ofEpochMilli(record.createdAt), updatedAt = Instant.ofEpochMilli(record.updatedAt),
    )
}

private class DecodedView(
    val record: ViewSnapshotRecord,
    val viewType: ViewType,
    /** Non-null exactly when `record.filter` is. */
    val comparator: ViewFilter.Comparator?,
    val sortDirection: SortDirection?,
)

private sealed interface PageDecode {
    class Readable(val page: DecodedPage) : PageDecode

    /** [detail] names the value, quoted, for the person who has to work out why a page stopped
     * updating on this device — not for a log nobody reads. */
    class Unreadable(val detail: String) : PageDecode
}

private fun unreadable(label: String, raw: String): PageDecode.Unreadable =
    PageDecode.Unreadable(unrecognisedValueDetail(label, raw))

/** What [quarantineSweep] settled: the records a pass may act on, and one report per record it
 * may not — whether it could not read that record itself or could not read something it names. */
private class PageQuarantineSweep(
    val readable: List<DecodedPage>,
    val reports: List<QuarantinedRecord>,
)

/**
 * Decides, for a whole batch at once, which records this pass is allowed to touch.
 *
 * Two rounds, and the second is the one that had been missing. The first decodes each record on
 * its own, exactly as before. The second propagates: a record that *points at* something the
 * first round withheld cannot be adopted either, because adopting it means resolving that pointer
 * to null and then republishing the nulled result over the folder. See [PagesSyncEngine.mergePages]
 * for the two ways that has destroyed content — a re-parented subtree and a database row's cells.
 *
 * Propagation runs to a fixpoint rather than one level deep: quarantining `Q` makes `Q` itself
 * unresolvable for anything pointing at `Q`, and a hierarchy is exactly the shape where that
 * matters. The loop terminates because each round either withholds at least one more record from
 * a finite batch or stops.
 *
 * [localPageUids] and [localPropertyUids] are what makes this proportionate instead of a
 * stampede. A reference is only *dangling* when its target was withheld **and** this device holds
 * no row for it already — a page every device has had for months resolves fine even when the
 * peer's newest copy of it is unreadable, and quarantining its children over that would freeze a
 * subtree for nothing.
 *
 * That refinement is per *referenced thing*, not per referring record, and the two sets above are
 * separate for exactly that reason: a page reference resolves against [localPageUids], a column
 * reference against [localPropertyUids], and a locally-held DATABASE page satisfies the first
 * while saying nothing whatever about the second. [withheldPages] below is what keeps that
 * question askable — it holds every uid this pass withheld, local ones included, where
 * `danglingPages` deliberately drops those.
 */
private fun quarantineSweep(
    admitted: List<PageSnapshotRecord>,
    localPageUids: Set<String>,
    localPropertyUids: Set<String>,
    unreadableUids: Set<String>,
): PageQuarantineSweep {
    val reports = mutableListOf<QuarantinedRecord>()
    // The uids that will resolve to nothing this pass: withheld here, and with no local row or
    // column standing behind them.
    val danglingPages = unreadableUids.filterNotTo(mutableSetOf()) { it in localPageUids }
    val danglingProperties = mutableSetOf<String>()
    // Every uid this pass withheld, *including* the ones this device holds a row for. Those are
    // not dangling as pages — but a database page is not only a page, and a row of one this pass
    // could not read has to be asked a second question about its cells. See
    // [PageSnapshotRecord.danglingReferenceDetail].
    val withheldPages = unreadableUids.toMutableSet()
    // The columns a cell could still be placed against: everything this device already holds,
    // plus everything any record in this batch defines. A record withheld later still counts here
    // — its columns are named in `danglingProperties` by `withhold` below, which is the sharper
    // check and reports the column by uid. What is left over is a cell whose column *nothing this
    // pass can see* defines, which is only possible when the record that would have defined it
    // never decoded far enough to be in `admitted` at all.
    val knownColumns = localPropertyUids +
        admitted.flatMap { r -> r.database?.properties.orEmpty().map { it.uid } }

    // A withheld record takes its own uid out of circulation, and — if it is a DATABASE page —
    // every column it defines with it. The columns are still legible even on a record that would
    // not decode: only `PropertyType` failed, so the uids are right there in the JSON. That is
    // what lets a row's cell values be checked at all.
    fun withhold(record: PageSnapshotRecord) {
        withheldPages += record.uid
        if (record.uid !in localPageUids) danglingPages += record.uid
        for (p in record.database?.properties.orEmpty()) {
            if (p.uid !in localPropertyUids) danglingProperties += p.uid
        }
    }

    var pending = mutableListOf<DecodedPage>()
    for (record in admitted) {
        when (val decoded = decodePage(record)) {
            is PageDecode.Readable -> pending += decoded.page
            is PageDecode.Unreadable -> {
                reports += QuarantinedRecord(QuarantinedRecord.PAGE, record.uid, decoded.detail)
                withhold(record)
            }
        }
    }

    while (true) {
        val survivors = mutableListOf<DecodedPage>()
        var propagated = false
        for (page in pending) {
            val detail = page.record.danglingReferenceDetail(
                pages = danglingPages,
                properties = danglingProperties,
                withheldPages = withheldPages,
                knownColumns = knownColumns,
            )
            if (detail == null) {
                survivors += page
                continue
            }
            reports += QuarantinedRecord(QuarantinedRecord.PAGE, page.uid, detail)
            withhold(page.record)
            propagated = true
        }
        pending = survivors
        if (!propagated) break
    }
    return PageQuarantineSweep(pending, reports)
}

/**
 * The first reference on this record that would resolve to nothing, named the way the person will
 * read it, or null when every reference it carries can still be resolved.
 *
 * The list below is the answer to "what can one page record point at", read off
 * `PageSnapshotRecords.kt` rather than remembered: [PageSnapshotRecord.parentUid],
 * [PageSnapshotRecord.databaseUid], [BlockSnapshotRecord.mentionedPageUid],
 * [SpanStyleSnapshot.PageMention] inside a block's formatting spans,
 * [CanvasNodeSnapshotRecord.embeddedPageUid], and [PropertyValueSnapshotRecord.propertyUid].
 *
 * Deliberately *not* included are the uids a record can only point at **within itself** — a
 * block's `parentBlockUid`, a canvas edge's endpoints, and every property uid a
 * [PageDatabaseSnapshotRecord] names in its own views and its done/deadline/recurrence columns.
 * Those resolve against the same record's own contents, and the record is all-or-nothing: if it
 * is here at all, they are here with it.
 *
 * [pages] and [properties] are the withheld referents that also fail to resolve locally. The
 * third clause is the one that cannot be expressed as a set of withheld uids, because the uids in
 * question were never legible: [withheldPages] holds *every* uid this pass withheld, local rows
 * included, and a row whose own `databaseUid` is one of them is asked whether its cells can still
 * be placed at all. A column that is neither local nor defined by any record in this batch
 * ([knownColumns]) can only have come from the withheld database page — so Pass 5 will drop that
 * cell and the export will republish the row without it. That is the loss the page-level test
 * cannot see, precisely because the database page itself resolves: this device has held it for
 * months, and the file it could not read is the one that added the column.
 */
private fun PageSnapshotRecord.danglingReferenceDetail(
    pages: Set<String>,
    properties: Set<String>,
    withheldPages: Set<String>,
    knownColumns: Set<String>,
): String? {
    referencedPageUids().firstOrNull { it in pages }
        ?.let { return unrecognisedValueDetail("linked page", it) }
    propertyValues.firstOrNull { it.propertyUid in properties }
        ?.let { return unrecognisedValueDetail("database column", it.propertyUid) }
    if (databaseUid?.let { it in withheldPages } == true) {
        propertyValues.firstOrNull { it.propertyUid !in knownColumns }
            ?.let { return unrecognisedValueDetail("database column", it.propertyUid) }
    }
    return null
}

/** Every *other* page this record names. See [danglingReferenceDetail] for where the list comes
 * from and what is deliberately left out of it. */
private fun PageSnapshotRecord.referencedPageUids(): Sequence<String> = sequence {
    parentUid?.let { yield(it) }
    databaseUid?.let { yield(it) }
    for (block in blocks) {
        block.mentionedPageUid?.let { yield(it) }
        for (span in block.formattingSpans) {
            (span.style as? SpanStyleSnapshot.PageMention)?.let { yield(it.pageUid) }
        }
    }
    for (node in canvas?.nodes.orEmpty()) {
        node.embeddedPageUid?.let { yield(it) }
    }
}

/**
 * Resolves every enum a page record carries, or says which one it could not.
 *
 * All-or-nothing on purpose. A partial decode would mean a page committed with some of its
 * content — Pass 1's bare row with no schema, no views and no blocks — which is worse than not
 * merging it at all: on every later pass the same record is no longer *newer* than the row that
 * partial merge wrote, so it never wins again, and the empty page is permanent.
 */
private fun decodePage(record: PageSnapshotRecord): PageDecode {
    val kind = enumOrNull<PageKind>(record.kind) ?: return unreadable("page kind", record.kind)
    val blocks = record.blocks.map { b ->
        b to (enumOrNull<BlockType>(b.type) ?: return unreadable("block type", b.type))
    }
    val properties = record.database?.properties.orEmpty().map { p ->
        p to (enumOrNull<PropertyType>(p.type) ?: return unreadable("property type", p.type))
    }
    val views = record.database?.views.orEmpty().map { v ->
        DecodedView(
            record = v,
            viewType = enumOrNull<ViewType>(v.viewType) ?: return unreadable("view type", v.viewType),
            comparator = v.filter?.let { f ->
                enumOrNull<ViewFilter.Comparator>(f.comparator) ?: return unreadable("view filter comparator", f.comparator)
            },
            sortDirection = v.sortDirection?.let {
                enumOrNull<SortDirection>(it) ?: return unreadable("sort direction", it)
            },
        )
    }
    val nodes = record.canvas?.nodes.orEmpty().map { n ->
        n to (enumOrNull<CanvasNodeType>(n.type) ?: return unreadable("canvas node type", n.type))
    }
    val edges = record.canvas?.edges.orEmpty().map { e ->
        e to (enumOrNull<CanvasArrowDirection>(e.direction) ?: return unreadable("canvas arrow direction", e.direction))
    }
    return PageDecode.Readable(DecodedPage(record, kind, blocks, properties, views, nodes, edges))
}
