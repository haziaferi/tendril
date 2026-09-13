package com.tendril.app.sync

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockDao
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.FormattingSpan
import com.tendril.app.data.page.SpanStyle
import java.time.Instant

/*
 * The block half of the page snapshot, shared by the sync engine (§9.4) and page history
 * (§0.6.13): a `Block` row to its record and a page's records back to rows. One copy, so a
 * revision restores exactly the way a merge rebuilds.
 */

internal fun Block.toSnapshot(pageIdToUid: Map<Long, String>, blockIdToUid: Map<Long, String>) = BlockSnapshotRecord(
    uid = uid, type = type.name, order = order,
    parentBlockUid = parentBlockId?.let { blockIdToUid[it] },
    content = content,
    formattingSpans = formattingSpans.mapNotNull { it.toSnapshot(pageIdToUid) },
    checked = checked, codeLanguage = codeLanguage, calloutIcon = calloutIcon, calloutColor = calloutColor,
    mentionedPageUid = mentionedPageId?.let { pageIdToUid[it] },
    referencedBlockUid = referencedBlockUid,
    toggleExpanded = toggleExpanded,
    mindMap = mindMap,
    imageName = imagePath?.let { imageNameFor(uid, it) },
    createdAt = createdAt.toEpochMilli(), updatedAt = updatedAt.toEpochMilli(),
)

/**
 * §9.4 / S4 — `<block uid>.<extension>`, the name this block's image travels under.
 *
 * The extension is carried so a reader can tell a PNG from a JPEG without opening the file,
 * and is sanitised rather than trusted: it ends up in a file name inside the synced folder,
 * and `DesktopFileSyncFileStore.resolveInside` exists precisely because a name that reached a
 * store could otherwise address a file outside it. An extension that is not a short run of
 * letters and digits is dropped, leaving a bare uid — a nameless image is a small loss, a
 * traversal is not.
 */
internal fun imageNameFor(blockUid: String, localPath: String): String {
    val extension = localPath.substringAfterLast('.', "")
    val safe = extension.length in 1..8 && extension.all { it.isLetterOrDigit() }
    return if (safe) "$blockUid.${extension.lowercase()}" else blockUid
}

internal fun FormattingSpan.toSnapshot(pageIdToUid: Map<Long, String>): FormattingSpanSnapshot? {
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


internal fun FormattingSpanSnapshot.toEntity(uidToId: Map<String, Long>): FormattingSpan? {
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

/**
 * Replaces [pageId]'s blocks with [blocks] (already decoded to a type), keyed by uid, children
 * relinked by uid in a second pass. [uidToId] resolves the page uids a mention span or a
 * mention block names. `Block.imagePath` is held across the rebuild by uid: it points into this
 * device's storage and no record carries it, so a rebuild that wrote null would unlink the
 * picture with its file still on disk.
 */
internal suspend fun replaceBlocks(
    blockDao: BlockDao,
    pageId: Long,
    blocks: List<Pair<BlockSnapshotRecord, BlockType>>,
    uidToId: Map<String, Long>,
) {
    val imagePathByUid = blockDao.getForPage(pageId).mapNotNull { b -> b.imagePath?.let { b.uid to it } }.toMap()
    blockDao.deleteForPage(pageId)
    val blockUidToId = mutableMapOf<String, Long>()
    for ((b, type) in blocks) {
        blockUidToId[b.uid] = blockDao.insert(
            Block(
                uid = b.uid, pageId = pageId, type = type, order = b.order,
                parentBlockId = null,
                imagePath = imagePathByUid[b.uid],
                content = b.content,
                formattingSpans = b.formattingSpans.mapNotNull { it.toEntity(uidToId) },
                checked = b.checked, codeLanguage = b.codeLanguage, calloutIcon = b.calloutIcon, calloutColor = b.calloutColor,
                mentionedPageId = b.mentionedPageUid?.let { uidToId[it] },
                referencedBlockUid = b.referencedBlockUid,
                toggleExpanded = b.toggleExpanded,
                mindMap = b.mindMap,
                createdAt = Instant.ofEpochMilli(b.createdAt), updatedAt = Instant.ofEpochMilli(b.updatedAt),
            )
        )
    }
    for ((b, _) in blocks) {
        val parentId = b.parentBlockUid?.let { blockUidToId[it] } ?: continue
        val newId = blockUidToId.getValue(b.uid)
        val inserted = blockDao.getById(newId) ?: continue
        blockDao.update(inserted.copy(parentBlockId = parentId))
    }
}
