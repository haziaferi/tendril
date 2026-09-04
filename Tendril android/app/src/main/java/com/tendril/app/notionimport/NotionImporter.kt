package com.tendril.app.notionimport

import android.content.Context
import android.net.Uri
import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockDao
import com.tendril.app.data.page.FormattingSpan
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.page.SpanStyle
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.PageDatabaseDao
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyDao
import com.tendril.app.data.pagedatabase.PropertyType
import com.tendril.app.data.pagedatabase.PropertyValue
import com.tendril.app.data.pagedatabase.PropertyValueDao
import com.tendril.app.domain.PageContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

/** One database created from an imported `.csv` — handed back so the Settings UI can offer the
 * post-import "Sync to Tasks?" step (§5.2) for it, reusing [com.tendril.app.domain.DatabaseSyncManager]
 * exactly as any locally-created to-do database would. */
data class ImportedDatabase(val pageId: Long, val databaseId: Long, val name: String)

data class NotionImportSummary(
    val pagesImported: Int,
    val rowsImported: Int,
    val assetsImported: Int,
    val databasesImported: List<ImportedDatabase>,
    /** §7.3.7 — "communicate plainly, not silently" what the export format itself can't carry,
     * plus anything this specific import run couldn't resolve (a dangling link, a missing
     * asset). Always non-empty: the format-limitation notice is unconditional. */
    val notices: List<String>,
)

/** A `.md`/`.csv` entry. Held in memory because both parser passes need its text. */
private data class TextEntry(val path: String, val text: String)

/**
 * Anything else in the export — images, PDFs, attachments. Streamed straight to a staging
 * file during the zip pass rather than buffered: a real workspace export's assets are
 * routinely larger than the app's heap, and holding them all was a guaranteed
 * OutOfMemoryError before a single page was written.
 */
private data class AssetEntry(val path: String, val staged: File)

private class ZipContents(val texts: List<TextEntry>, val assets: List<AssetEntry>)

/** Ceiling on the Markdown/CSV held in memory. Assets don't count toward it — they never
 * enter the heap — so this bounds the one part that genuinely has to be resident. */
private const val MAX_TEXT_BYTES = 64L * 1024 * 1024

private val NOTION_ID_REGEX = Regex("[0-9a-fA-F]{32}")
private val TRAILING_ID_REGEX = Regex("\\s[0-9a-fA-F]{32}$")

/**
 * §7.4 — the Notion Markdown & CSV export importer. Two ordered passes over the zip, matching
 * the architecture decision: pass 1 creates every `.md` page's `Page` row (title + tree
 * position only) and builds the Notion-id → Room-id map; pass 2 parses each page's actual body
 * against that already-complete map, so internal links resolve at import time with no further
 * patching pass needed. Always additive (§9.4.1's Import path) — never touches existing data.
 */
class NotionImporter(
    private val context: Context,
    private val pageDao: PageDao,
    private val blockDao: BlockDao,
    private val pageDatabaseDao: PageDatabaseDao,
    private val propertyDao: PropertyDao,
    private val propertyValueDao: PropertyValueDao,
    private val pageContentRepository: PageContentRepository,
) {
    /** §7.3.6 — the data an imported database's post-import "Sync to Tasks?" step needs, so the
     * Settings UI can reuse [com.tendril.app.ui.pages.EnableSyncSheet] and
     * [com.tendril.app.domain.DatabaseSyncManager.enableSync] verbatim rather than building a
     * second binding mechanism. */
    suspend fun propertiesFor(databaseId: Long) = propertyDao.getForDatabase(databaseId)
    suspend fun rowsFor(databaseId: Long) = pageDao.getRowsOf(databaseId)
    suspend fun databaseFor(databaseId: Long) = pageDatabaseDao.getById(databaseId)

    /**
     * Runs on [Dispatchers.IO] — zip inflation, asset writes and hundreds of Room inserts are
     * not main-thread work, and this was previously launched straight from a Compose
     * `rememberCoroutineScope()`.
     *
     * Assets are staged into a temp directory for the duration and deleted afterwards, so an
     * export's images never all sit in memory at once and unreferenced ones don't accumulate
     * in `filesDir`.
     */
    suspend fun import(zipUri: Uri): NotionImportSummary = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "notion_import_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            importStaged(readZip(zipUri, stagingDir))
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private suspend fun importStaged(contents: ZipContents): NotionImportSummary {
        val mdEntries = contents.texts.filter { it.path.endsWith(".md", ignoreCase = true) }
        val csvEntries = contents.texts.filter { it.path.endsWith(".csv", ignoreCase = true) }
        val assetEntries = contents.assets

        val now = Instant.now()
        val warnings = mutableListOf<String>()
        var assetsImported = 0
        var rowsImported = 0

        // ---- Pass 1: every Page shell + the Notion-id -> Room-id / title maps ----
        val pathWithoutExt = mdEntries.associate { stripExtension(it.path) to extractNotionId(it.path) }
        val idToTitle = mutableMapOf<String, String>()
        val idToRoomId = mutableMapOf<String, Long>()
        // Carries its own markdown so pass 2 doesn't re-scan mdEntries for it — that lookup
        // made the import O(pages²) on a workspace with thousands of pages.
        data class PageMeta(val notionId: String, val path: String, val roomId: Long, val text: String)
        val metas = mutableListOf<PageMeta>()

        for (entry in mdEntries) {
            val notionId = extractNotionId(entry.path) ?: continue
            val title = titleFromFilename(entry.path)
            val roomId = pageDao.insert(Page(title = title, createdAt = now, updatedAt = now))
            idToTitle[notionId] = title
            idToRoomId[notionId] = roomId
            metas += PageMeta(notionId, entry.path, roomId, entry.text)
        }

        val assetsByPath = assetEntries.associateBy { it.path }

        // Fix up parent/child links now that every page id exists — a page's parent is
        // whichever other page's own (extensionless) path exactly matches this page's
        // containing folder, the recursive "folder mirrors the page tree" structure §7.1
        // describes. No match (the common case: everything sits under one export-root wrapper
        // folder that isn't itself a page) means a true top-level page.
        for (meta in metas) {
            val dir = meta.path.substringBeforeLast("/", missingDelimiterValue = "")
            val parentNotionId = pathWithoutExt[dir]
            val parentRoomId = parentNotionId?.let { idToRoomId[it] }
            if (parentRoomId != null) pageDao.updateParentAndDatabase(meta.roomId, parentRoomId, null)
        }

        // ---- Pass 2: parse each page's body, now that every link target may already exist ----
        for (meta in metas) {
            val parsedBlocks = NotionMarkdownParser.parse(meta.text)
            var order = 0
            for (parsed in parsedBlocks) {
                val imagePath = parsed.imageAssetPath?.let { relative ->
                    val resolved = resolveRelativePath(meta.path, relative)
                    val asset = assetsByPath[resolved]
                    if (asset != null) {
                        assetsImported++
                        saveAsset(asset.staged, resolved)
                    } else {
                        warnings += "Couldn't find the image for \"${idToTitle[meta.notionId]}\" ($relative) — the block was imported without it."
                        null
                    }
                }
                val spans = parsed.spans.mapNotNull { span ->
                    val style = when (val kind = span.kind) {
                        is ParsedSpanKind.Bold -> SpanStyle.Bold
                        is ParsedSpanKind.Italic -> SpanStyle.Italic
                        is ParsedSpanKind.Strikethrough -> SpanStyle.Strikethrough
                        is ParsedSpanKind.InlineCode -> SpanStyle.InlineCode
                        is ParsedSpanKind.ExternalLink -> SpanStyle.Link(kind.url)
                        is ParsedSpanKind.PageRef -> idToRoomId[kind.notionId]?.let(SpanStyle::PageMention)
                    }
                    style?.let { FormattingSpan(span.start, span.end, it) }
                }
                blockDao.insert(
                    Block(
                        pageId = meta.roomId,
                        type = parsed.type,
                        order = order++,
                        content = parsed.content,
                        formattingSpans = spans,
                        checked = parsed.checked,
                        codeLanguage = parsed.codeLanguage,
                        calloutIcon = parsed.calloutIcon,
                        imagePath = imagePath,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
            pageContentRepository.rebuildFtsForPage(meta.roomId)
        }

        // ---- CSV databases ----
        val importedDatabases = mutableListOf<ImportedDatabase>()
        for (entry in csvEntries) {
            val rows = NotionCsvParser.parse(entry.text)
            if (rows.isEmpty()) continue
            val headers = rows.first()
            val dataRows = rows.drop(1)
            if (headers.isEmpty()) continue

            val dbTitle = titleFromFilename(entry.path).ifBlank { "Untitled database" }
            val dbPageId = pageDao.insert(Page(title = dbTitle, kind = PageKind.DATABASE, createdAt = now, updatedAt = now))
            val databaseId = pageDatabaseDao.insert(PageDatabase(pageId = dbPageId, createdAt = now, updatedAt = now))

            // Column 0 is always Notion's own title/"Name" column — it becomes each row Page's
            // own title, never a Property, matching how Row-as-page's title already works (§5.1).
            val propertyColumns = headers.indices.drop(1)
            val propertyIdByIndex = mutableMapOf<Int, Long>()
            val inferenceByIndex = mutableMapOf<Int, NotionPropertyTypeInference.Inference>()

            for ((order, colIndex) in propertyColumns.withIndex()) {
                val header = headers[colIndex].ifBlank { "Column ${colIndex + 1}" }
                val samples = dataRows.mapNotNull { it.getOrNull(colIndex) }
                val inference = NotionPropertyTypeInference.infer(samples)
                inferenceByIndex[colIndex] = inference
                propertyIdByIndex[colIndex] = propertyDao.insert(
                    Property(databaseId = databaseId, name = header, type = inference.type, config = inference.config, order = order)
                )
            }

            for (dataRow in dataRows) {
                val rowTitle = dataRow.getOrNull(0)?.trim()?.ifBlank { null } ?: "Untitled"
                val rowPageId = pageDao.insert(Page(title = rowTitle, databaseId = databaseId, createdAt = now, updatedAt = now))
                rowsImported++
                for (colIndex in propertyColumns) {
                    val raw = dataRow.getOrNull(colIndex)?.trim().orEmpty()
                    if (raw.isEmpty()) continue
                    val propertyId = propertyIdByIndex[colIndex] ?: continue
                    val inference = inferenceByIndex.getValue(colIndex)
                    // §7.3.5 — best-effort relation resolution: a raw Notion-id token that
                    // happens to match a page discovered in pass 1 (e.g. that related database
                    // was also exported "with subpages") resolves to its title; anything else
                    // is left as the raw id rather than dropped.
                    val resolved = NOTION_ID_REGEX.replace(raw) { match -> idToTitle[match.value.lowercase()] ?: match.value }
                    val formatted = formatCellValue(resolved, inference.type)
                    propertyValueDao.insert(PropertyValue(propertyId = propertyId, rowPageId = rowPageId, value = formatted))
                }
            }

            importedDatabases += ImportedDatabase(dbPageId, databaseId, dbTitle)
        }

        val notices = listOf(
            "Database views, filters, sorts, live formulas, and comments couldn't be recovered — " +
                "Notion's own export format doesn't include them, not something a better importer could fix.",
        ) + warnings

        return NotionImportSummary(
            pagesImported = metas.size,
            rowsImported = rowsImported,
            assetsImported = assetsImported,
            databasesImported = importedDatabases,
            notices = notices,
        )
    }

    /**
     * One pass over the zip. Markdown/CSV is decoded into memory (both parser passes need
     * it, and it's small); everything else is copied straight to a staging file in fixed-size
     * chunks and never held whole. Only the text total is capped — that's the part that has
     * to be resident.
     */
    private fun readZip(uri: Uri, stagingDir: File): ZipContents {
        val texts = mutableListOf<TextEntry>()
        val assets = mutableListOf<AssetEntry>()
        var textBytes = 0L

        val input = context.contentResolver.openInputStream(uri)
            ?: error("Couldn't open the chosen file — nothing was imported.")
        input.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name
                        if (name.endsWith(".md", ignoreCase = true) || name.endsWith(".csv", ignoreCase = true)) {
                            val bytes = zip.readBytes()
                            textBytes += bytes.size
                            require(textBytes <= MAX_TEXT_BYTES) {
                                "This export's text content exceeds ${MAX_TEXT_BYTES / (1024 * 1024)} MB — too large to import in one pass."
                            }
                            texts += TextEntry(name, bytes.toString(Charsets.UTF_8))
                        } else {
                            val staged = File(stagingDir, UUID.randomUUID().toString())
                            staged.outputStream().use { zip.copyTo(it) }
                            assets += AssetEntry(name, staged)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return ZipContents(texts, assets)
    }

    /** Copies a staged asset into its permanent home under `filesDir`. The extension is taken
     * from the zip entry name, which is untrusted input — stripping it to alphanumerics keeps
     * path separators in a crafted name (`x.../../y`) from placing the file outside [dir]. */
    private fun saveAsset(staged: File, originalPath: String): String {
        val dir = File(context.filesDir, "notion_import_assets").apply { mkdirs() }
        val ext = originalPath.substringAfterLast('.', "").take(8).filter { it.isLetterOrDigit() }
        val file = File(dir, UUID.randomUUID().toString() + if (ext.isNotEmpty()) ".$ext" else "")
        staged.copyTo(file, overwrite = true)
        return file.absolutePath
    }

    private fun formatCellValue(raw: String, type: PropertyType): String = when (type) {
        PropertyType.CHECKBOX -> (raw.lowercase(Locale.ROOT) in setOf("yes", "true", "x", "checked")).toString()
        PropertyType.DATE -> NotionPropertyTypeInference.parseNotionDate(raw)?.toString() ?: raw
        else -> raw
    }

    private fun stripExtension(path: String): String =
        path.removeSuffix(".md").removeSuffix(".MD").removeSuffix(".csv").removeSuffix(".CSV")

    private fun extractNotionId(path: String): String? {
        val filename = stripExtension(path.substringAfterLast("/"))
        return TRAILING_ID_REGEX.find(filename)?.value?.trim()?.lowercase()
    }

    private fun titleFromFilename(path: String): String =
        stripExtension(path.substringAfterLast("/")).replace(TRAILING_ID_REGEX, "").trim()

    /** Markdown image/file references are relative to the referencing page's own zip directory
     * (§7.1's per-page assets folder), not to the zip root. */
    private fun resolveRelativePath(basePath: String, relative: String): String {
        val baseDir = basePath.substringBeforeLast("/", "")
        val combined = if (baseDir.isEmpty()) relative else "$baseDir/$relative"
        val segments = mutableListOf<String>()
        for (segment in combined.split("/")) {
            when (segment) {
                "", "." -> {}
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.size - 1)
                else -> segments.add(segment)
            }
        }
        return segments.joinToString("/")
    }
}
