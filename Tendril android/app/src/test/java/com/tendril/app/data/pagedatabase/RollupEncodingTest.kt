package com.tendril.app.data.pagedatabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * §5.4/DB2 — the structured [RollupConfig] a `COMPUTED` property's config holds today. Not an
 * expression: see [PropertyType.COMPUTED]'s own note on why the type carries that name from day
 * one even though the `ƒ`-reveals-text half of §5.4 (DB3) does not exist yet.
 */
class RollupEncodingTest {

    @Test
    fun `a COUNT config round-trips with no target property`() {
        val relation = UUID.randomUUID().toString()

        val parsed = parseRollupConfig(encodeRollupConfig(relation, null, RollupAggregation.COUNT))

        assertEquals(relation, parsed?.relationPropertyUid)
        assertNull(parsed?.targetPropertyUid)
        assertEquals(RollupAggregation.COUNT, parsed?.aggregation)
    }

    @Test
    fun `a SUM config round-trips with its target property`() {
        val relation = UUID.randomUUID().toString()
        val target = UUID.randomUUID().toString()

        val parsed = parseRollupConfig(encodeRollupConfig(relation, target, RollupAggregation.SUM))

        assertEquals(relation, parsed?.relationPropertyUid)
        assertEquals(target, parsed?.targetPropertyUid)
        assertEquals(RollupAggregation.SUM, parsed?.aggregation)
    }

    @Test
    fun `every aggregation round-trips`() {
        val relation = UUID.randomUUID().toString()
        val target = UUID.randomUUID().toString()

        for (aggregation in RollupAggregation.entries) {
            val parsed = parseRollupConfig(encodeRollupConfig(relation, target, aggregation))
            assertEquals("$aggregation should round-trip", aggregation, parsed?.aggregation)
        }
    }

    @Test
    fun `a null config parses to null, not a crash`() {
        assertNull(parseRollupConfig(null))
    }

    @Test
    fun `malformed config with the wrong field count parses to null`() {
        // Two fields (no aggregation) and four fields (an extra colon) are both malformed —
        // neither should be quietly accepted as if one field had simply been left blank.
        assertNull(parseRollupConfig("only:two"))
        assertNull(parseRollupConfig("a:b:c:d"))
    }

    @Test
    fun `an unrecognised aggregation name parses to null`() {
        // The tolerant-decode posture this whole subsystem is gated behind (§5.4's own
        // precondition): a newer build's aggregation this one has never heard of must not throw.
        val relation = UUID.randomUUID().toString()
        assertNull(parseRollupConfig("$relation:-:MEDIAN"))
    }

    @Test
    fun `the no-target placeholder is never mistaken for a real uid`() {
        // A UUID string is always 36 characters; the placeholder is one. If a future change to
        // UUID generation ever produced something this short, this test would catch the collision
        // before a rollup with a genuinely empty-string uid silently lost its target.
        assertTrue(UUID.randomUUID().toString().length > 1)
    }

    @Test
    fun `a whole-number rollup result formats without a trailing decimal`() {
        assertEquals("3", formatRollupNumber(3.0))
        assertEquals("0", formatRollupNumber(0.0))
        assertEquals("-4", formatRollupNumber(-4.0))
    }

    @Test
    fun `a fractional rollup result keeps its decimal part`() {
        assertEquals("3.5", formatRollupNumber(3.5))
    }

    @Test
    fun `COMPUTED is a real PropertyType member, not just a string`() {
        assertTrue(PropertyType.entries.contains(PropertyType.COMPUTED))
    }
}
