package com.tendril.app.notionimport

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockDao
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.pagedatabase.PageDatabaseDao
import com.tendril.app.data.pagedatabase.PropertyDao
import com.tendril.app.data.pagedatabase.PropertyValueDao
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.sync.FakeContentResolverBacking
import com.tendril.app.sync.fakeContext
import com.tendril.app.sync.zipOf
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for MEM-01's rewrite of the Notion import path.
 *
 * The import used to read every zip entry fully into memory — including a workspace's images,
 * which routinely exceed the heap — and derived an asset's filename extension straight from the
 * untrusted zip entry name. Assets are now streamed to a staging directory and copied out with a
 * sanitised extension.
 *
 * These assert against the real filesystem (via [TemporaryFolder]) rather than a mock, because
 * *where the file physically landed* is the whole question for the traversal case.
 */
class NotionImportAssetTest {

    @get:Rule val temp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var backing: FakeContentResolverBacking
    private lateinit var blockDao: BlockDao
    private val insertedBlocks = mutableListOf<Block>()

    private val assetsDir get() = File(filesDir, "notion_import_assets")

    @Before
    fun setUp() {
        filesDir = temp.newFolder("files")
        cacheDir = temp.newFolder("cache")
        backing = FakeContentResolverBacking()
        insertedBlocks.clear()
    }

    private fun importer(): NotionImporter {
        val pageDao = mockk<PageDao>(relaxed = true)
        var nextPageId = 1L
        coEvery { pageDao.insert(any<Page>()) } answers { nextPageId++ }

        blockDao = mockk(relaxed = true)
        val block = slot<Block>()
        coEvery { blockDao.insert(capture(block)) } answers { insertedBlocks += block.captured; 1L }

        return NotionImporter(
            context = fakeContext(backing, filesDir, cacheDir),
            pageDao = pageDao,
            blockDao = blockDao,
            pageDatabaseDao = mockk<PageDatabaseDao>(relaxed = true),
            propertyDao = mockk<PropertyDao>(relaxed = true),
            propertyValueDao = mockk<PropertyValueDao>(relaxed = true),
            pageContentRepository = mockk<PageContentRepository>(relaxed = true),
        )
    }

    private companion object {
        // Notion exports name files "<Title> <32 hex chars>.md" — the importer keys off that id.
        const val MD_PATH = "Export/Meeting notes 0123456789abcdef0123456789abcdef.md"
        val IMAGE_BYTES = ByteArray(2048) { (it % 251).toByte() }
    }

    private fun markdownReferencing(relativePath: String) =
        "# Meeting notes\n\n![diagram]($relativePath)\n".toByteArray(Charsets.UTF_8)

    // ---------------------------------------------------------------- asset handling

    @Test
    fun `a referenced image is written into the assets directory`() = runBlocking {
        val zip = zipOf(
            MD_PATH to markdownReferencing("assets/diagram.png"),
            "Export/assets/diagram.png" to IMAGE_BYTES,
        )
        val summary = importer().import(backing.givenFile(zip))

        assertEquals(1, summary.assetsImported)
        val written = assetsDir.listFiles().orEmpty()
        assertEquals(1, written.size)
        assertArrayEqualsMessage(IMAGE_BYTES, written.single().readBytes())
        assertTrue("extension should survive", written.single().name.endsWith(".png"))
    }

    @Test
    fun `the staging directory is cleaned up after import`() = runBlocking {
        val zip = zipOf(
            MD_PATH to markdownReferencing("assets/diagram.png"),
            "Export/assets/diagram.png" to IMAGE_BYTES,
        )
        importer().import(backing.givenFile(zip))

        val leftovers = cacheDir.listFiles().orEmpty().filter { it.name.startsWith("notion_import_") }
        assertTrue("staging dirs left behind: ${leftovers.map { it.name }}", leftovers.isEmpty())
    }

    @Test
    fun `unreferenced assets never reach the assets directory`() = runBlocking {
        // They're staged during the zip pass, then dropped with the staging dir — an export's
        // unused images shouldn't accumulate in filesDir forever.
        val zip = zipOf(
            MD_PATH to markdownReferencing("assets/diagram.png"),
            "Export/assets/diagram.png" to IMAGE_BYTES,
            "Export/assets/never-referenced.png" to IMAGE_BYTES,
            "Export/assets/also-unused.jpg" to IMAGE_BYTES,
        )
        val summary = importer().import(backing.givenFile(zip))

        assertEquals(1, summary.assetsImported)
        assertEquals(1, assetsDir.listFiles().orEmpty().size)
    }

    @Test
    fun `a missing image is reported as a notice rather than failing the import`() = runBlocking {
        val zip = zipOf(MD_PATH to markdownReferencing("assets/gone.png"))
        val summary = importer().import(backing.givenFile(zip))

        assertEquals(0, summary.assetsImported)
        assertEquals(1, summary.pagesImported)
        assertTrue(
            "the missing asset should be explained, not swallowed",
            summary.notices.any { it.contains("Couldn't find the image") },
        )
    }

    // ---------------------------------------------------------------- entry-name sanitising

    @Test
    fun `an entry name with separators after the last dot still lands flat in the assets dir`() = runBlocking {
        // The extension is derived from the zip entry name, which is untrusted. `substringAfterLast('.')`
        // on ".../photo.d/subdir/file" yields "d/subdir/file"; unsanitised, that made
        // File(dir, "<uuid>.d/subdir") — and Kotlin's copyTo creates missing parents, so the
        // asset silently landed in a nested directory nobody expected.
        //
        // Note this is *not* a directory escape: resolveRelativePath normalises every ".."
        // away before saveAsset ever sees the path, so nothing can climb above the assets dir.
        val hostile = "Export/assets/photo.d/subdir/file"
        val zip = zipOf(
            MD_PATH to markdownReferencing("assets/photo.d/subdir/file"),
            hostile to IMAGE_BYTES,
        )
        importer().import(backing.givenFile(zip))

        val written = assetsDir.listFiles().orEmpty()
        assertEquals(1, written.size)
        assertTrue("asset should be a plain file, not a directory", written.single().isFile)
        assertEquals(
            "asset must sit directly in the assets dir",
            assetsDir.canonicalPath,
            written.single().canonicalFile.parentFile?.canonicalPath,
        )
    }

    @Test
    fun `resolved asset paths never contain parent-directory segments`() = runBlocking {
        // Pins the property the test above relies on: a "../.." in the markdown reference is
        // normalised, so it cannot reach saveAsset. If this ever regresses, the extension
        // sanitising becomes the only thing standing between a crafted export and filesDir.
        val zip = zipOf(
            MD_PATH to markdownReferencing("../../../outside.png"),
            "outside.png" to IMAGE_BYTES,
        )
        importer().import(backing.givenFile(zip))

        assertFalse("a file escaped into filesDir", File(filesDir, "outside.png").exists())
        for (f in assetsDir.listFiles().orEmpty()) {
            assertFalse("separator survived in ${f.name}", f.name.contains('/'))
            assertFalse("separator survived in ${f.name}", f.name.contains('\\'))
        }
    }

    // ---------------------------------------------------------------- streaming

    @Test
    fun `an export far larger than the text budget still imports when the bulk is assets`() = runBlocking {
        // The point of the rewrite: only Markdown/CSV is capped and resident. A big binary
        // payload streams through staging without ever being held whole, so this must not throw.
        val big = ByteArray(12 * 1024 * 1024) { (it % 251).toByte() }
        val zip = zipOf(
            MD_PATH to markdownReferencing("assets/big.bin"),
            "Export/assets/big.bin" to big,
        )
        val summary = importer().import(backing.givenFile(zip))

        assertEquals(1, summary.assetsImported)
        assertEquals(big.size.toLong(), assetsDir.listFiles().orEmpty().single().length())
    }

    private fun assertArrayEqualsMessage(expected: ByteArray, actual: ByteArray) {
        assertTrue("asset bytes differ", expected.contentEquals(actual))
    }
}
