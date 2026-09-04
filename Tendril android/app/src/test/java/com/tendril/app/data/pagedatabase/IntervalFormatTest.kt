package com.tendril.app.data.pagedatabase

import com.tendril.app.data.entry.IntervalUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Period

/**
 * audit 2.2 — one string form for a bound Recurrence column, not two.
 *
 * `DatabaseSyncManager.crystallize` wrote `formatIntervalValue`'s `"1:WEEK"` when a row was
 * unbound, while `PageDatabaseViewModel.valueForCell` returned `Period.toString()`'s `"P7D"`
 * for the live proxy. Same column, same underlying recurrence, two strings — so a view filter
 * matched one and not the other, and the displayed text changed the moment a row was unbound.
 *
 * The conversion existed only inside `crystallize`, which is how the two drifted; it now lives
 * beside the format it produces and both callers share it. These tests are on that shared
 * function, and the round-trip through `parseIntervalValue` is what pins the two ends together.
 */
class IntervalFormatTest {

    @Test
    fun `whole weeks format as weeks`() {
        assertEquals("1:WEEK", formatPeriodAsInterval(Period.ofDays(7)))
        assertEquals("2:WEEK", formatPeriodAsInterval(Period.ofDays(14)))
    }

    @Test
    fun `days that are not whole weeks stay days`() {
        assertEquals("3:DAY", formatPeriodAsInterval(Period.ofDays(3)))
        assertEquals("10:DAY", formatPeriodAsInterval(Period.ofDays(10)))
    }

    @Test
    fun `months take precedence`() {
        assertEquals("1:MONTH", formatPeriodAsInterval(Period.ofMonths(1)))
        assertEquals("6:MONTH", formatPeriodAsInterval(Period.ofMonths(6)))
    }

    @Test
    fun `zero days is not mistaken for a week`() {
        // `days % 7 == 0` is true of zero, which is why the branch also requires days != 0.
        assertEquals("0:DAY", formatPeriodAsInterval(Period.ZERO))
    }

    @Test
    fun `the output is never Period's own toString`() {
        // The actual regression: "P7D" reaching a cell that elsewhere holds "1:WEEK".
        val formatted = formatPeriodAsInterval(Period.ofDays(7))

        assertEquals("1:WEEK", formatted)
        assert(formatted != Period.ofDays(7).toString())
    }

    @Test
    fun `every produced form parses back`() {
        // The two ends of the same column: whatever crystallize freezes, a filter must be able
        // to read. A form that formats but does not parse would reintroduce the mismatch.
        val periods = listOf(
            Period.ofDays(1), Period.ofDays(3), Period.ofDays(7), Period.ofDays(14),
            Period.ofDays(10), Period.ofMonths(1), Period.ofMonths(6), Period.ZERO,
        )

        for (period in periods) {
            val formatted = formatPeriodAsInterval(period)
            val parsed = parseIntervalValue(formatted)
            assertEquals("$formatted should parse", formatted, parsed?.let { (n, u) -> formatIntervalValue(n, u) })
        }
    }

    @Test
    fun `a value in Period's form does not parse, which is the point`() {
        // If "P7D" ever reaches the column again, it fails to read rather than silently
        // comparing unequal to every properly-formatted value.
        assertNull(parseIntervalValue("P7D"))
    }

    @Test
    fun `round-trips agree with formatIntervalValue for each unit`() {
        assertEquals(formatIntervalValue(2, IntervalUnit.WEEK), formatPeriodAsInterval(Period.ofDays(14)))
        assertEquals(formatIntervalValue(5, IntervalUnit.DAY), formatPeriodAsInterval(Period.ofDays(5)))
        assertEquals(formatIntervalValue(3, IntervalUnit.MONTH), formatPeriodAsInterval(Period.ofMonths(3)))
    }
}
