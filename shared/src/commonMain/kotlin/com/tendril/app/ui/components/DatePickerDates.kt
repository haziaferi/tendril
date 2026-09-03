package com.tendril.app.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Material3's `DatePicker` carries its selection as UTC midnight of the chosen day: the
 * millis are a calendar date in disguise, not a moment in the local zone.
 *
 * Converting a [LocalDate] to those millis with the *system* zone therefore lands on the
 * wrong day anywhere east of UTC — in Europe/Rome in summer, `2026-09-01T00:00+02:00` is
 * `2026-08-31T22:00Z`, so a picker opened on 1 September highlights 31 August. West of UTC
 * it slips the other way once the offset is large enough. Both directions have to agree on
 * UTC, which is what these two functions exist to guarantee: use them as a pair rather than
 * spelling the conversion out at each call site, since only one half being wrong is exactly
 * the bug this replaces.
 */
private val PICKER_ZONE: ZoneId = ZoneId.of("UTC")

/** A [LocalDate] as the millis `DatePicker`'s `initialSelectedDateMillis` expects. */
fun LocalDate.toDatePickerMillis(): Long = atStartOfDay(PICKER_ZONE).toInstant().toEpochMilli()

/** A `DatePicker`'s `selectedDateMillis` as the calendar date that was actually tapped. */
fun datePickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(PICKER_ZONE).toLocalDate()
