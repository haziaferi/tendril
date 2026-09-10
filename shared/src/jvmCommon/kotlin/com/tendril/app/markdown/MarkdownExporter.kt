package com.tendril.app.markdown

import com.tendril.app.data.page.BlockDao
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageDao
import com.tendril.app.sync.LocalImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** What an export actually wrote, for the sentence the person is shown afterwards. */
data class MarkdownExportResult(val pages: Int, val images: Int)

/**
 * §7 in reverse, the I/O half — every live page as a `.md` file in a zip, with its pictures.
 *
 * **A zip rather than a directory grant**, deliberately. The app already holds one folder
 * permission, for §9.4's continuous sync, and a second tree grant offered a few rows below it in
 * the same Settings screen is an easy thing to confuse with it — picking the sync folder here
 * would scatter `.md` files through the directory Syncthing replicates. `CreateDocument` asks for
 * one new file, cannot be aimed at an existing folder's contents, and matches what `.tendril`
 * already does. It is also the shape Notion's own export takes, which is the format this app
 * already consumes. A directory export is a reasonable later addition, not a precondition.
 *
 * **In `jvmCommon` over an [OutputStream]**, unlike `PortableArchive`, which is Android-only
 * because it reaches for `Context`/`Uri` directly. Nothing here needs a platform — the caller
 * opens the stream — so desktop gets this without a second copy written against the same spec.
 * That is the same reasoning that moved `SnapshotEncryption` into shared code (§12.5).
 *
 * Not gated by §3.1.2's View-Only lock, matching `PortableArchive.export`: it reads local data and
 * writes outside the app, and a lock that stopped someone taking a copy of their own notes would
 * be the toggle working against the data it exists to protect.
 *
 * **Known limitation, stated rather than discovered:** a Database's table and a Canvas's nodes are
 * not represented. Their rows are ordinary pages and do get exported, but the structure around
 * them is tabular and belongs in CSV — which is exactly the split §7 already makes coming the
 * other way, where Notion hands this app Markdown *and* CSV as two different things.
 */
class MarkdownExporter(
    private val pageDao: PageDao,
    private val blockDao: BlockDao,
    private val localImages: LocalImageStore,
) {

    suspend fun export(destination: OutputStream): MarkdownExportResult = withContext(Dispatchers.IO) {
        // Templates are excluded (§3.1.3 keeps them out of the Pages list for the same reason) and
        // so is the Trash: an export is a copy of what the person has, not of what they discarded.
        val pages = pageDao.getAll().filter { it.deletedAt == null && !it.isTemplate }
        val paths = filePathsFor(pages)
        val writtenAssets = mutableSetOf<String>()
        var images = 0

        ZipOutputStream(destination).use { zip ->
            for (page in pages) {
                val here = paths.getValue(page.id)
                val blocks = blockDao.getForPage(page.id)

                // Every picture is read *before* rendering, because `assetPathFor` is an ordinary
                // lambda and reading a local image is a suspend call. Collecting them here also
                // means the link written into the document and the file written into the zip come
                // from one map and cannot disagree.
                val assetNames = mutableMapOf<String, String>()
                val assetBytes = mutableMapOf<String, ByteArray>()
                for (block in blocks) {
                    val localPath = block.imagePath ?: continue
                    val bytes = localImages.read(localPath) ?: continue
                    // Named for the block uid — the same rule §9.4's folder and `.tendril` both
                    // use, so one picture carries one name everywhere it can appear.
                    val name = "assets/${block.uid}${extensionOf(localPath)}"
                    assetNames[block.uid] = name
                    assetBytes[name] = bytes
                }

                val body = MarkdownWriter.render(
                    blocks = blocks,
                    assetPathFor = { block -> assetNames[block.uid]?.let { relativeTo(here, it) } },
                    pageLinkFor = { id -> paths[id]?.let { relativeTo(here, it) } },
                )

                zip.writeEntry(here, header(page) + body)
                for ((name, bytes) in assetBytes) {
                    // A picture two blocks share is stored once; the links both point at it.
                    if (writtenAssets.add(name)) {
                        zip.writeEntry(name, bytes)
                        images++
                    }
                }
            }
        }
        MarkdownExportResult(pages = pages.size, images = images)
    }

    /**
     * One zip path per page, mirroring §4's page tree, with collisions resolved per directory.
     *
     * The tree is mirrored rather than flattened because it is information the person built and
     * because it makes collisions rarer — two pages called "Notes" under different parents are
     * simply two different paths. Where a real collision remains, the second gets ` (2)`,
     * compared case-insensitively: a zip extracted onto Windows or macOS lands on a filesystem
     * where `Notes.md` and `notes.md` are the same file, and one silently overwriting the other
     * is the worst outcome available here.
     *
     * A page whose parent is excluded (trashed, or a template) is written at the level its chain
     * ran out at rather than being dropped with it — losing a live page because of where it
     * happened to sit is not a trade this should make.
     */
    private fun filePathsFor(pages: List<Page>): Map<Long, String> {
        val byId = pages.associateBy { it.id }
        val usedPerDirectory = mutableMapOf<String, MutableSet<String>>()
        val paths = mutableMapOf<Long, String>()
        // Ordered by id so the same database always produces the same file names: whoever existed
        // first keeps the unsuffixed one, rather than it depending on row order.
        for (page in pages.sortedBy { it.id }) {
            val directory = ancestorsOf(page, byId).joinToString("/") { safeName(it.title, it.uid) }
            val base = safeName(page.title, page.uid)
            val used = usedPerDirectory.getOrPut(directory) { mutableSetOf() }
            var candidate = base
            var suffix = 2
            while (!used.add(candidate.lowercase())) {
                candidate = "$base ($suffix)"
                suffix++
            }
            paths[page.id] = (if (directory.isEmpty()) "" else "$directory/") + candidate + ".md"
        }
        return paths
    }

    /** The page's ancestors, outermost first. Guarded against a parent cycle, which no UI can
     * create but which a merged snapshot from a newer build is not structurally prevented from
     * describing — and an export that hung on one would be a poor way to find out. */
    private fun ancestorsOf(page: Page, byId: Map<Long, Page>): List<Page> {
        val chain = ArrayDeque<Page>()
        val seen = mutableSetOf(page.id)
        var parent = page.parentId?.let(byId::get)
        while (parent != null && seen.add(parent.id)) {
            chain.addFirst(parent)
            parent = parent.parentId?.let(byId::get)
        }
        return chain.toList()
    }

    /**
     * A page title as a file name that survives every filesystem this zip might be opened on.
     *
     * Windows is the strict one and so sets the rules: its forbidden characters, no trailing dot
     * or space, and its reserved device names. A title that reduces to nothing falls back to the
     * uid — an oddly named file is recoverable, a file that could not be created is not.
     */
    private fun safeName(title: String, uid: String): String {
        val cleaned = title
            .map { if (it in ILLEGAL_NAME_CHARS || it.isISOControl()) ' ' else it }
            .joinToString("")
            .trim()
            .take(60)
            .trimEnd('.', ' ')
        if (cleaned.isBlank()) return uid
        // `CON.md` is still CON to Windows, so the check is on the stem.
        return if (cleaned.uppercase() in RESERVED_NAMES) "$cleaned-page" else cleaned
    }

    /**
     * [toFile] as a link target from inside [fromFile] — Markdown resolves relative to the file
     * holding the link, so a page two levels down needs `../../assets/x.png` and not `assets/x.png`.
     */
    private fun relativeTo(fromFile: String, toFile: String): String {
        val fromDirectories = fromFile.split('/').dropLast(1)
        val toParts = toFile.split('/')
        val toDirectories = toParts.dropLast(1)
        var shared = 0
        while (shared < fromDirectories.size && shared < toDirectories.size &&
            fromDirectories[shared] == toDirectories[shared]
        ) {
            shared++
        }
        val up = List(fromDirectories.size - shared) { ".." }
        return (up + toDirectories.drop(shared) + toParts.last()).joinToString("/")
    }

    /** The title as an H1. Every Markdown reader looks there for a document's name, and the file
     * name is lost the moment the text is pasted somewhere else. */
    private fun header(page: Page): String =
        "# " + listOfNotNull(page.icon, page.title.ifBlank { "Untitled" }).joinToString(" ") + "\n\n"

    private fun extensionOf(localPath: String): String {
        val extension = localPath.substringAfterLast('.', "")
        val usable = extension.length in 1..8 && extension.all { it.isLetterOrDigit() }
        return if (usable) "." + extension.lowercase() else ""
    }

    private fun ZipOutputStream.writeEntry(name: String, text: String) =
        writeEntry(name, text.toByteArray(Charsets.UTF_8))

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private companion object {
        val ILLEGAL_NAME_CHARS = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|').toSet()
        val RESERVED_NAMES = setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
        )
    }
}
