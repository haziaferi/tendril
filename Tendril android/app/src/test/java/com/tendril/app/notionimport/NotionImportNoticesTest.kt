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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * audit 2.3 — a `.md` the importer skips is now reported rather than dropped in silence.
 *
 * `NotionImporter` `continue`s on any markdown file whose name lacks a trailing 32-hex Notion
 * id. Skipping is correct — such a file genuinely is not a page from the export — but §7.3.7
 * requires losses to be communicated plainly, and `notices` exists for exactly that. Without a
 * word, the person sees a success screen and discovers the absence later, if at all.
 */
class NotionImportNoticesTest {

    @get:Rule val temp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var backing: FakeContentResolverBacking

    @Before
    fun setUp() {
        filesDir = temp.newFolder("files")
        cacheDir = temp.newFolder("cache")
        backing = FakeContentResolverBacking()
    }

    private fun importer(): NotionImporter {
        val pageDao = mockk<PageDao>(relaxed = true)
        var nextPageId = 1L
        coEvery { pageDao.insert(any<Page>()) } answers { nextPageId++ }
        val blockDao = mockk<BlockDao>(relaxed = true)
        coEvery { blockDao.insert(any<Block>()) } answers { 1L }

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
        const val VALID_MD = "Export/Meeting notes 0123456789abcdef0123456789abcdef.md"
        val BODY = "# Meeting notes\n\nSome text\n".toByteArray(Charsets.UTF_8)
    }

    private fun skipNotice(notices: List<String>) = notices.singleOrNull { "skipped" in it }

    @Test
    fun `a markdown file with no Notion id is reported, not dropped in silence`() = runBlocking {
        val zip = zipOf(
            VALID_MD to BODY,
            "Export/README.md" to BODY,
        )

        val summary = importer().import(backing.givenFile(zip))

        assertEquals("only the real page imports", 1, summary.pagesImported)
        val notice = skipNotice(summary.notices)
        assertTrue("the skip must be reported at all", notice != null)
        assertTrue("and must name the file: $notice", notice!!.contains("README.md"))
    }

    @Test
    fun `a clean export produces no skip notice`() = runBlocking {
        val zip = zipOf(VALID_MD to BODY)

        val summary = importer().import(backing.givenFile(zip))

        assertEquals(1, summary.pagesImported)
        assertEquals("nothing was skipped, so nothing should be claimed", null, skipNotice(summary.notices))
    }

    @Test
    fun `several skipped files are counted and listed`() = runBlocking {
        val zip = zipOf(
            VALID_MD to BODY,
            "Export/README.md" to BODY,
            "Export/notes.md" to BODY,
            "Export/scratch.md" to BODY,
        )

        val summary = importer().import(backing.givenFile(zip))

        val notice = skipNotice(summary.notices)!!
        assertTrue("should count them: $notice", notice.contains("3 files"))
        assertTrue(notice.contains("README.md"))
        assertTrue(notice.contains("notes.md"))
        assertTrue(notice.contains("scratch.md"))
    }

    @Test
    fun `one skipped file reads as singular`() = runBlocking {
        val zip = zipOf(VALID_MD to BODY, "Export/README.md" to BODY)

        val notice = skipNotice(importer().import(backing.givenFile(zip)).notices)!!

        assertTrue("should not say \"1 files were\": $notice", notice.contains("1 file was"))
    }

    @Test
    fun `the standing export-format notice is still present alongside it`() = runBlocking {
        // The skip notice is added to the existing list, not substituted for it.
        val zip = zipOf(VALID_MD to BODY, "Export/README.md" to BODY)

        val notices = importer().import(backing.givenFile(zip)).notices

        assertTrue(notices.any { "Database views" in it })
        assertTrue(notices.any { "skipped" in it })
    }
}
