package com.tendril.desktopapp

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * §2.2 — “Enter writes once”, the popup's half of the notification-area section.
 *
 * Audit 1.8 — the quick-add popup writes once per open, however fast the second Enter arrives.
 *
 * `spec_trace.py --section 2.2` reported **23 claims, 11 source citations, 0 test citations** on
 * 2026-09-24, which is what put this citation here: the section was already the top row of the
 * unpinned worklist, and a test that pins one of its claims without naming it leaves the count
 * reading zero and the next reader looking for a test that exists.
 *
 * **The row this closes said for five audits that it could not be tested.** The reason given was
 * "the desktop module has no test source set", which was true when written and stopped being true
 * on 2026-09-23; the row was then swept up with three others whose justifications had expired the
 * same way. Row 1.5's survived the sweep — it is a duplicated `Text`, and the desktop
 * `ui-test-junit4` artifact is not in the offline cache — but nothing in *this* one draws. The
 * guard is a flag and a coroutine, so it moved to [QuickAddWriteGate] and is executed here.
 *
 * **Single-threaded on purpose.** The walk that found this defect needed real double-taps through
 * `keybd_event` and got five duplicate pairs out of five attempts, 0–3 ms apart; a test that tried
 * to reproduce that race would be the flake row 2.14 is about. It does not have to. The defect was
 * never a data race — both Enters are delivered to the same lambda on the same thread — it was
 * *check-then-act across a suspend boundary*, and the second check is deterministic once the first
 * write is known to be in flight. [theSecondEnterArrivesWhileTheFirstWriteIsStillInFlight] pins
 * exactly that moment with a [CompletableDeferred], with no timing assumption at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)  // advanceUntilIdle
class QuickAddGateTest {

    private companion object {
        val ENTRY = Entry(
            id = 1,
            uid = "e1",
            title = "Dentist",
            kind = EntryKind.TASK,
            status = EntryStatus.PENDING,
            startDate = LocalDate.of(2026, 9, 25),
            startTime = LocalTime.of(14, 30),
            endDate = null,
            endTime = null,
            recurrenceRule = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }

    /** Collects what the popup would have done, so each test asserts on the whole outcome. */
    private class Popup {
        var writes = 0
        var added: Entry? = null
        var closed = false
    }

    @Test
    fun theSecondEnterArrivesWhileTheFirstWriteIsStillInFlight() = runTest {
        // Audit 1.8 exactly. The old guard was `added`, assigned only once `quickAddEntry` had
        // returned, so throughout the window this test opens it still read null and a second Enter
        // launched a second write. Nothing here depends on how long anything takes: the first write
        // announces that it has started and then blocks until this test lets it finish, so the
        // second Enter is *known* to fall inside the round trip rather than probably falling there.
        val popup = Popup()
        val gate = QuickAddWriteGate()
        val inFlight = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()

        launchQuickAdd(
            gate = gate,
            write = { popup.writes++; inFlight.complete(Unit); finish.await(); ENTRY },
            onAdded = { popup.added = it },
            onClose = { popup.closed = true },
            flashMs = 1,
        )
        inFlight.await()

        // The second Enter of the double tap, mid-write.
        launchQuickAdd(
            gate = gate,
            write = { popup.writes++; ENTRY },
            onAdded = { popup.added = it },
            onClose = { popup.closed = true },
            flashMs = 1,
        )
        finish.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            "audit 1.8: a second Enter inside the write's round trip wrote a second entry",
            1, popup.writes,
        )
        // The guard must not have won by breaking the feature: one write still flashes and closes.
        assertEquals(ENTRY, popup.added)
        assertTrue("the popup never closed", popup.closed)
    }

    @Test
    fun aFreshOpenWritesAgain() = runTest {
        // The contrast that stops the test above being a green test of nothing. A gate that refused
        // every write would satisfy "exactly one write" in the first test while making the popup
        // useless from its second open onward — and the popup composes a new gate per open, so this
        // is the production path, not a hypothetical one.
        val popup = Popup()
        repeat(3) {
            launchQuickAdd(
                gate = QuickAddWriteGate(),
                write = { popup.writes++; ENTRY },
                onAdded = { popup.added = it },
                onClose = { popup.closed = true },
                flashMs = 1,
            )
        }
        advanceUntilIdle()

        assertEquals("three opens, three entries", 3, popup.writes)
    }

    @Test
    fun aBlankLineClaimsTheGateAndClosesWithoutWriting() = runTest {
        // `quickAddEntry` returns null for a blank title, and the popup's lambda always treated
        // that as an ordinary outcome: claim the gate, flash nothing, close. Pinned rather than
        // corrected, because it is the behaviour that shipped and this change is an extraction,
        // not a redesign — the row is about writing twice, not about writing nothing.
        val popup = Popup()
        val gate = QuickAddWriteGate()

        launchQuickAdd(
            gate = gate,
            write = { popup.writes++; null },
            onAdded = { popup.added = it },
            onClose = { popup.closed = true },
            flashMs = 1,
        )
        advanceUntilIdle()

        assertNull("a blank line must not produce an entry", popup.added)
        assertTrue("the popup stayed open on a blank Enter", popup.closed)
        assertFalse("the gate was left unclaimed by a blank Enter", gate.start())
    }

    @Test
    fun theGateIsClaimedBeforeTheCoroutineRunsAtAll() = runTest {
        // Why the claim sits outside `launch` rather than inside it. `runTest`'s dispatcher does
        // not start a launched body until the test body suspends, so both calls below happen with
        // *no* write having begun — the worst case for a guard that checks inside the coroutine,
        // where both bodies would queue and both would find the flag clear. A guard moved inside
        // `launch` still passes the first test and fails this one.
        val popup = Popup()
        val gate = QuickAddWriteGate()
        repeat(2) {
            launchQuickAdd(
                gate = gate,
                write = { popup.writes++; ENTRY },
                onAdded = { popup.added = it },
                onClose = { popup.closed = true },
                flashMs = 1,
            )
        }
        assertEquals("a write ran before the dispatcher was given a chance", 0, popup.writes)

        advanceUntilIdle()
        assertEquals("both Enters queued a write before either could run", 1, popup.writes)
    }
}
