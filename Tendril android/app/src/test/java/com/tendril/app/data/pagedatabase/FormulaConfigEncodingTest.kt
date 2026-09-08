package com.tendril.app.data.pagedatabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * §5.4/DB3 wiring — the `"formula:"`-tagged half of a `COMPUTED` property's config, alongside
 * [RollupConfig]'s untagged one. See [PropertyType.COMPUTED]'s own note on why the two are told
 * apart by a tag prefix rather than by, say, trying [parseRollupConfig] first and falling back.
 */
class FormulaConfigEncodingTest {

    @Test
    fun `an expression round-trips through encode and parse`() {
        assertEquals("prop(\"Score\") * 2", parseFormulaConfig(encodeFormulaConfig("prop(\"Score\") * 2")))
    }

    @Test
    fun `a null config parses to null, not a crash`() {
        assertNull(parseFormulaConfig(null))
    }

    @Test
    fun `an untagged rollup config is not mistaken for a formula`() {
        val untagged = encodeRollupConfig("relation-uid", "target-uid", RollupAggregation.SUM)
        assertNull(parseFormulaConfig(untagged))
    }

    @Test
    fun `a formula-tagged config is never mistaken for a rollup`() {
        // Chosen specifically because it has three colon-separated segments after the tag is
        // stripped — the exact shape RollupConfig's own parser looks for — so this proves the
        // tag check really does run first rather than the two parsers racing on field count.
        val tagged = encodeFormulaConfig("a:b:c")
        assertNull(parseRollupConfig(tagged))
        assertEquals("a:b:c", parseFormulaConfig(tagged))
    }
}
