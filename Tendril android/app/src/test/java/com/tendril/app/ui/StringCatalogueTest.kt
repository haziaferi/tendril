package com.tendril.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Plan Phase 4 (L3) — the habit planner's strings land in both languages the first time. Read from
 * the shared resource files themselves, so a string added in English alone, an Italian string left
 * behind when its English goes, or a placeholder that differs between the two (a crash or a wrong
 * argument at run time) fails here rather than on a screen.
 */
class StringCatalogueTest {

    /** The planner's strings and the language setting's: every one owes an Italian. */
    private fun owesItalian(key: String) = key.startsWith("plan_") || key.startsWith("settings_language")

    /** A language's own name reads the same in both, as does a pattern shown to be copied. */
    private val sameInBoth = setOf("settings_language_english", "settings_language_italian", "plan_time_hint")

    private fun resources(dir: String): File {
        var d: File? = File("").absoluteFile
        while (d != null) {
            val f = File(d, "shared/src/commonMain/composeResources/$dir/strings.xml")
            if (f.isFile) return f
            d = d.parentFile
        }
        error("no $dir/strings.xml under any ancestor of ${File("").absolutePath}")
    }

    /** name -> every text it holds (a string's one, a plural's each quantity), in order. */
    private fun read(dir: String): Map<String, List<String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resources(dir))
        val out = linkedMapOf<String, List<String>>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            out[e.getAttribute("name")] = when (e.tagName) {
                "plurals" -> (0 until e.getElementsByTagName("item").length).map { e.getElementsByTagName("item").item(it).textContent }
                else -> listOf(e.textContent)
            }
        }
        return out
    }

    private val placeholder = Regex("%(\\d+)\\$[sd]")

    private fun placeholders(texts: List<String>) = texts.flatMap { t -> placeholder.findAll(t).map { it.value } }.toSortedSet()

    private val english = read("values")
    private val italian = read("values-it")

    @Test
    fun `every Italian string has its English`() {
        assertEquals(emptySet<String>(), italian.keys - english.keys)
    }

    @Test
    fun `every planner string has its Italian`() {
        val missing = english.keys.filter { owesItalian(it) && it !in sameInBoth && it !in italian }
        assertEquals(emptyList<String>(), missing)
        assertTrue("the catalogue is not empty", english.keys.count(::owesItalian) > 50)
    }

    /** H1 — a default block with no name is shown by its uid's string; one without would show blank. */
    @Test
    fun `every default block has a name in both languages`() {
        for (b in com.tendril.app.data.habit.DEFAULT_HABIT_BLOCKS) {
            val key = com.tendril.app.ui.taskshabits.defaultBlockNameKey(b.uid)
            assertTrue(b.uid, key != null && key in english && key in italian)
        }
        assertEquals(null, com.tendril.app.ui.taskshabits.defaultBlockNameKey("a-block-someone-made"))
    }

    @Test
    fun `the two languages take the same arguments`() {
        val differing = italian.keys.filter { placeholders(english.getValue(it)) != placeholders(italian.getValue(it)) }
        assertEquals(emptyList<String>(), differing)
    }
}
