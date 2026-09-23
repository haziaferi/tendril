package com.tendril.app.notifications

import com.tendril.app.data.entry.EntryStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §3.6 — the overdue notification's Done/Skip, with App Lock on.
 *
 * §3.6 recorded this hole itself, on 2026-09-06, and left it open: "The Done/Skip actions on
 * §9.7's overdue notification resolve the Entry through `ResolveEntryUseCase` with no App Lock
 * check anywhere in the notification code. One tap from the keyguard logs a completion, advances
 * a recurring TASK's `start_date`, re-arms its alarms and rewrites the Calendar Provider mirror —
 * exactly the class of write the bullet above exists to stop, reached by a route the bullet above
 * never considered." The Habits widget had been reconciled two days earlier; this path never was.
 *
 * It stayed open partly because nothing could test it. `EntryActionReceiver` is a
 * `BroadcastReceiver` whose body only runs inside a broadcast dispatch — `goAsync()` requires
 * one — so the JVM suite could not reach it, and an instrumented test would run against the real
 * database on the device. [resolveFromNotification] is the rule lifted out of that dispatch, so
 * the refusal is an ordinary assertion.
 *
 * `tools/mutate.py` reports `EntryActionReceiver` as a symbol no test names at all — one of only
 * two in §9.7 with nothing anywhere. These are the first.
 */
class NotificationActionAppLockTest {

    private class Recorder {
        val writes = mutableListOf<Pair<Long, EntryStatus>>()
        suspend fun resolve(id: Long, status: EntryStatus) {
            writes += id to status
        }
    }

    @Test
    fun `Done from the keyguard writes nothing while App Lock is on`() = runBlocking {
        val recorder = Recorder()
        val wrote = resolveFromNotification(
            appLockEnabled = true,
            entryId = 42L,
            action = EntryActionReceiver.ACTION_DONE,
            resolve = recorder::resolve,
        )
        assertFalse("the action reported a write it did not make", wrote)
        assertEquals("a completion was logged from the lock screen", emptyList<Pair<Long, EntryStatus>>(), recorder.writes)
    }

    @Test
    fun `Skip from the keyguard writes nothing while App Lock is on`() = runBlocking {
        // Skip is the one that advances a recurring task's start_date, so it moves data even
        // though it records "not done".
        val recorder = Recorder()
        val wrote = resolveFromNotification(
            appLockEnabled = true,
            entryId = 42L,
            action = EntryActionReceiver.ACTION_SKIP,
            resolve = recorder::resolve,
        )
        assertFalse(wrote)
        assertEquals(emptyList<Pair<Long, EntryStatus>>(), recorder.writes)
    }

    @Test
    fun `Done still resolves when App Lock is off`() = runBlocking {
        // The mirror. Without it the refusal tests would pass just as well against a function
        // that never writes at all, which is the shape of a guard test that has stopped testing
        // the guard.
        val recorder = Recorder()
        val wrote = resolveFromNotification(
            appLockEnabled = false,
            entryId = 42L,
            action = EntryActionReceiver.ACTION_DONE,
            resolve = recorder::resolve,
        )
        assertTrue(wrote)
        assertEquals(listOf(42L to EntryStatus.DONE), recorder.writes)
    }

    @Test
    fun `Skip resolves as SKIPPED, not DONE, when App Lock is off`() = runBlocking {
        val recorder = Recorder()
        resolveFromNotification(
            appLockEnabled = false,
            entryId = 7L,
            action = EntryActionReceiver.ACTION_SKIP,
            resolve = recorder::resolve,
        )
        assertEquals(listOf(7L to EntryStatus.SKIPPED), recorder.writes)
    }

    @Test
    fun `a refused action does not report success, so its notification is not dismissed`() = runBlocking {
        // The return value is what `onReceive` uses to decide whether to cancel the
        // notification. A refusal that returned true would clear the notification and leave the
        // person believing the task was done — worse than the original defect, because the
        // original at least did what it appeared to do.
        val recorder = Recorder()
        assertFalse(
            resolveFromNotification(true, 1L, EntryActionReceiver.ACTION_DONE, recorder::resolve),
        )
    }

    @Test
    fun `checking a habit off from the keyguard writes nothing while App Lock is on`() = runBlocking {
        // The third surface (see `checkInFromNotification`). Added after `tools/mutate.py`
        // reported the first version of this guard SURVIVED: it was written inline in
        // `onReceive`, where nothing could reach it, so a guard added for a security hole had
        // nothing defending it.
        val checked = mutableListOf<Long>()
        val wrote = checkInFromNotification(appLockEnabled = true, habitId = 3L) { checked += it }
        assertFalse("the action reported a check-in it did not make", wrote)
        assertEquals(emptyList<Long>(), checked)
    }

    @Test
    fun `checking a habit off still works when App Lock is off`() = runBlocking {
        val checked = mutableListOf<Long>()
        val wrote = checkInFromNotification(appLockEnabled = false, habitId = 3L) { checked += it }
        assertTrue(wrote)
        assertEquals(listOf(3L), checked)
    }
}
