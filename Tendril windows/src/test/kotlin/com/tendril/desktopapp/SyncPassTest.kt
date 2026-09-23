package com.tendril.desktopapp

import com.tendril.app.sync.SnapshotMergeResult
import com.tendril.app.sync.SnapshotSyncOrchestrator
import com.tendril.app.sync.SyncFileStore
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit 1.17 — a folder merge on the desktop re-plans the reminders it merged in.
 *
 * The row was closed with a fix nothing could execute: `replan` is reached from the five
 * `EntryScheduleCoordinator` callbacks and once at startup, a merge passes neither, and a reminder
 * set on the phone therefore sat here unplanned until the next launch. The fix threaded
 * `rearmReminders` from `main()` through `App` to `SyncBar`; what pinned it was the compiler and a
 * walk, because this module had no test source set and the call lived inside a composable's click
 * lambda.
 *
 * Both of those are gone — the source set since 2026-09-23, and the placement since `runSyncPass`
 * was lifted out of the lambda. The Compose half is still unreachable (no desktop
 * `ui-test-junit4` in the offline cache), so what stays walk-pinned is that the button is wired to
 * this function at all; what it *does* is now gated.
 *
 * **The second test is the one worth having.** "Does it call the re-plan?" passes against a
 * function that calls it unconditionally — including on a pass that decrypted nothing and wrote
 * nothing, where re-planning would be wrong. Asserting the negative branch is what makes the
 * positive one mean something, the same argument as the contrast case in
 * `DesktopReminderSchedulerTest`.
 */
class SyncPassTest {

    /**
     * A real result, not a mock. `SnapshotMergeResult` is a data class whose `passphraseMismatch`
     * is a derived getter (`undecryptableFiles > 0`), so stubbing it would have let the two
     * fields disagree in a way the production type cannot — a fixture describing a state that
     * never occurs.
     */
    private fun merge(undecryptable: Int = 0) = SnapshotMergeResult(undecryptableFiles = undecryptable)

    @Test
    fun `a clean pass writes, re-plans, and reports nothing`() = runBlocking {
        val store = mockk<SyncFileStore>()
        val orchestrator = mockk<SnapshotSyncOrchestrator>()
        coEvery { orchestrator.readAndMerge(store, null) } returns merge()
        coEvery { orchestrator.writeSnapshots(any(), any(), any()) } just Runs
        var replans = 0

        val notice = runSyncPass(orchestrator, store, null) { replans++ }

        assertNull("a clean pass should report nothing: $notice", notice)
        assertEquals("a clean pass must re-plan exactly once", 1, replans)
        coVerify(exactly = 1) { orchestrator.writeSnapshots(store, null, any()) }
    }

    @Test
    fun `a passphrase mismatch neither writes nor re-plans`() = runBlocking {
        // §9.4.2 — nothing merged in, so writing would replace the folder's only copy with this
        // device's state under the wrong key, and there is nothing to re-plan either. This is the
        // case that separates a real assertion from a test of a function that always re-plans.
        val store = mockk<SyncFileStore>()
        val orchestrator = mockk<SnapshotSyncOrchestrator>()
        coEvery { orchestrator.readAndMerge(store, "wrong") } returns merge(undecryptable = 24)
        coEvery { orchestrator.writeSnapshots(any(), any(), any()) } just Runs
        var replans = 0

        val notice = runSyncPass(orchestrator, store, "wrong") { replans++ }

        assertEquals("the folder must not be re-planned from a pass that merged nothing", 0, replans)
        coVerify(exactly = 0) { orchestrator.writeSnapshots(any(), any(), any()) }
        assertTrue("the notice should name the count and say nothing was written: $notice",
            notice != null && notice.contains("24") && notice.contains("Nothing was written"))
    }

    @Test
    fun `a throwing store reports its message and does not re-plan`() = runBlocking {
        // SyncFileStore's contract is that a write which cannot complete throws. The bar used to
        // catch this itself; moving it here is what keeps `syncing = false` the caller's only job.
        val store = mockk<SyncFileStore>()
        val orchestrator = mockk<SnapshotSyncOrchestrator>()
        coEvery { orchestrator.readAndMerge(store, null) } throws IllegalStateException("disk gone")
        var replans = 0

        val notice = runSyncPass(orchestrator, store, null) { replans++ }

        assertEquals("disk gone", notice)
        assertEquals("a failed pass must not re-plan", 0, replans)
    }
}
