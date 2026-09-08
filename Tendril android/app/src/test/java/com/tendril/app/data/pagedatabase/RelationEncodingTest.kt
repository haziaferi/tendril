package com.tendril.app.data.pagedatabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * §5.4/DB1 — the string encodings a `RELATION` property's [Property.config] and a relation
 * cell's [PropertyValue.value] use, exercised without any of [PageDatabaseViewModel]'s
 * database/ViewModel machinery. The colon in [RelationConfig]'s encoding is only safe because
 * both halves are always [UUID] strings; these tests pin that down rather than assume it.
 */
class RelationEncodingTest {

    @Test
    fun `a relation config round-trips through encode and parse`() {
        val target = UUID.randomUUID().toString()
        val reverse = UUID.randomUUID().toString()

        val parsed = parseRelationConfig(encodeRelationConfig(target, reverse))

        assertEquals(target, parsed?.targetDatabasePageUid)
        assertEquals(reverse, parsed?.reversePropertyUid)
    }

    @Test
    fun `a null config parses to null, not a crash`() {
        // The state of every non-relation property, and of a relation property whose config
        // was cleared by the generic type-change path this feature deliberately excludes
        // itself from (see PageDatabaseScreen's `ChangeTypeDialog` offeredTypes filter).
        assertNull(parseRelationConfig(null))
    }

    @Test
    fun `a config with no colon parses to null rather than a garbage split`() {
        // Legacy or malformed data should degrade to "no relation config", the same posture
        // every other tolerant decode in this codebase takes on a value it cannot read (§9.4).
        assertNull(parseRelationConfig("not-a-relation-config"))
    }

    @Test
    fun `an empty config parses to null`() {
        assertNull(parseRelationConfig(""))
    }

    @Test
    fun `a relation value round-trips as a comma-joined uid set`() {
        val a = UUID.randomUUID().toString()
        val b = UUID.randomUUID().toString()

        val encoded = encodeRelationValue(setOf(a, b))

        assertEquals(setOf(a, b), parseRelationValue(encoded))
    }

    @Test
    fun `a null value parses to an empty set, not a crash`() {
        // The state of every relation cell before it is ever edited.
        assertEquals(emptySet<String>(), parseRelationValue(null))
    }

    @Test
    fun `an empty value parses to an empty set`() {
        assertEquals(emptySet<String>(), parseRelationValue(""))
    }

    @Test
    fun `a single related row round-trips`() {
        val a = UUID.randomUUID().toString()
        assertEquals(setOf(a), parseRelationValue(encodeRelationValue(setOf(a))))
    }

    @Test
    fun `stray whitespace around a uid is trimmed on parse`() {
        val a = UUID.randomUUID().toString()
        val b = UUID.randomUUID().toString()
        assertEquals(setOf(a, b), parseRelationValue(" $a , $b "))
    }

    @Test
    fun `blank entries from a trailing separator are dropped, not kept as empty uids`() {
        val a = UUID.randomUUID().toString()
        assertEquals(setOf(a), parseRelationValue("$a,"))
    }

    @Test
    fun `RELATION is a real PropertyType member, not just a string`() {
        // The precondition §5.4 states in prose — Milestone 0's tolerant PropertyType decode
        // must reach every device before this exists — only matters if the member is real.
        assertTrue(PropertyType.entries.contains(PropertyType.RELATION))
    }
}
