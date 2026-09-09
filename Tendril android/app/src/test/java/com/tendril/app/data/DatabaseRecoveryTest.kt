package com.tendril.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §9.10 — the snapshot-restore fallback's ordering, pinned.
 *
 * The acceptance criterion asks that the fallback be "wired and manually tested at least once (a
 * deliberately-broken migration on a test device…)". The device half needs hardware and is not
 * what this file claims to do. What it does claim is the half that can be proven here and that
 * a device test would prove badly: the *sequence* — probe, close, set aside, rebuild, re-probe —
 * and the two ways it must refuse to make things worse.
 *
 * [openOrRecover] is generic for exactly this reason. A test that had to stand up Room, a real
 * SQLite file and a deliberately corrupt schema to check "does it close before it renames" would
 * be slow, platform-bound, and would still only observe the outcome.
 */
class DatabaseRecoveryTest {

    /** A fake database whose probe fails for the first [failures] builds. */
    private class Fixture(private val failures: Int) {
        val events = mutableListOf<String>()
        private var builds = 0

        fun open() = openOrRecover(
            build = { events += "build"; ++builds },
            probe = { generation ->
                events += "probe($generation)"
                if (generation <= failures) error("migration didn't properly handle: generation $generation")
            },
            close = { events += "close($it)" },
            setAside = { events += "setAside" },
        )
    }

    @Test
    fun `a database that opens is used as it is, and nothing is set aside`() {
        val f = Fixture(failures = 0)
        val result = f.open()

        assertFalse("a healthy open must not report recovery", result.recovered)
        assertEquals(1, result.database)
        assertEquals(listOf("build", "probe(1)"), f.events)
    }

    @Test
    fun `a database that fails its probe is closed before its file is moved, then rebuilt`() {
        val f = Fixture(failures = 1)
        val result = f.open()

        assertTrue(result.recovered)
        assertEquals("the second build's database must be the one returned", 2, result.database)
        // The ordering is the point. Renaming a file out from under an open handle turns one
        // unreadable database into two, so `close` has to land before `setAside`, and the rebuild
        // has to land after it or it would re-open the same broken file.
        assertEquals(
            listOf("build", "probe(1)", "close(1)", "setAside", "build", "probe(2)"),
            f.events,
        )
    }

    @Test
    fun `a freshly built database that still fails is rethrown, not recovered again`() {
        val f = Fixture(failures = 2)

        val thrown = runCatching { f.open() }.exceptionOrNull()

        assertTrue("a second failure must surface, not loop", thrown is IllegalStateException)
        // Exactly one setAside. If a *new* database cannot be opened either, the old file was not
        // the problem, and moving another one aside would destroy a working copy for nothing.
        assertEquals(1, f.events.count { it == "setAside" })
        assertEquals(2, f.events.count { it == "build" })
    }

    @Test
    fun `a database that cannot even be closed is still recovered`() {
        val events = mutableListOf<String>()
        var builds = 0
        val result = openOrRecover(
            build = { events += "build"; ++builds },
            probe = { if (it == 1) error("broken") },
            // A handle that throws on close is plausible for a database that never opened
            // properly, and it must not stop the recovery it is standing in the way of.
            close = { events += "close"; error("already closed") },
            setAside = { events += "setAside" },
        )

        assertTrue(result.recovered)
        assertEquals(2, result.database)
        assertEquals(listOf("build", "close", "setAside", "build"), events)
    }
}
