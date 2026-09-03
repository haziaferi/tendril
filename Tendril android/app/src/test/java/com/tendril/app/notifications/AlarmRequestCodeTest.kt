package com.tendril.app.notifications

import com.tendril.app.notifications.AlarmScheduler.Companion.overdueRequestCode
import com.tendril.app.notifications.AlarmScheduler.Companion.reminderRequestCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for ALARM-02.
 *
 * PendingIntent identity ignores extras, so two alarms that share a request code *and* a target
 * receiver are the same PendingIntent: scheduling the second silently replaced the first, and
 * cancelling either cancelled both. The old scheme XOR-folded `reminderId shl 8` into entryId's
 * own bits, so pairs collided — reminders just disappeared with nothing in the logs.
 *
 * These are pure functions on the companion; no Android runtime is involved.
 */
class AlarmRequestCodeTest {

    @Test
    fun `the historical collision no longer happens`() {
        // Under the old `(entryId * 2 + 1) xor (reminderId shl 8)` both of these produced 259.
        assertNotEquals(
            reminderRequestCode(entryId = 1, reminderId = 1),
            reminderRequestCode(entryId = 129, reminderId = 0),
        )
    }

    @Test
    fun `reminder codes are unique across a dense grid of entry and reminder ids`() {
        // The entry range must cross 128. Under the old scheme `reminderId shl 8` only started
        // overlapping entryId's bits once `entryId * 2 + 1` needed a 9th bit, so a grid capped
        // below that passed even against the buggy formula — verified by re-running this test
        // against it.
        val seen = mutableMapOf<Int, Pair<Long, Long>>()
        for (entryId in 1L..400L) {
            for (reminderId in 0L..40L) {
                val code = reminderRequestCode(entryId, reminderId)
                val clash = seen.put(code, entryId to reminderId)
                assertEquals(
                    "request code $code collides: ${entryId to reminderId} and $clash",
                    null,
                    clash,
                )
            }
        }
    }

    @Test
    fun `overdue codes are unique across entry ids`() {
        val seen = mutableSetOf<Int>()
        for (entryId in 1L..5000L) {
            assertTrue("duplicate overdue code for entry $entryId", seen.add(overdueRequestCode(entryId)))
        }
    }

    @Test
    fun `an overdue code can never equal a reminder code`() {
        // Bit 0 is the discriminator: overdue is even, reminder is odd. This is what keeps a
        // task's own due alarm from cancelling one of its reminders.
        for (entryId in 1L..200L) {
            assertEquals(0, overdueRequestCode(entryId) and 1)
            for (reminderId in 0L..10L) {
                assertEquals(1, reminderRequestCode(entryId, reminderId) and 1)
            }
        }
    }

    @Test
    fun `codes are stable across calls so reschedule stays idempotent`() {
        // Rescheduling must reuse the same PendingIntent rather than stacking a second alarm.
        assertEquals(overdueRequestCode(42), overdueRequestCode(42))
        assertEquals(reminderRequestCode(42, 7), reminderRequestCode(42, 7))
    }

    @Test
    fun `overdue codes match the pre-fix scheme so existing alarms stay cancellable`() {
        // Deliberately unchanged from `(entryId * 2)` for entry ids below 65,536: alarms
        // scheduled by an older build must still be cancellable after upgrading. Reminder codes
        // did change — those orphans are reconciled at next launch instead.
        for (entryId in longArrayOf(1, 2, 99, 1000, 65_535)) {
            assertEquals((entryId * 2).toInt(), overdueRequestCode(entryId))
        }
    }

    @Test
    fun `codes stay positive`() {
        // A negative request code is legal but makes logs and `adb shell dumpsys alarm` output
        // needlessly hard to read; the bit layout reserves the sign bit to avoid it.
        assertTrue(overdueRequestCode(65_535) > 0)
        assertTrue(reminderRequestCode(32_767, 32_767) > 0)
    }
}
