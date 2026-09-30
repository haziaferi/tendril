package com.tendril.app.domain.plan

/*
 * §6.3 (plan Phase 5b) — edit mode: an entry selected in the Day view moves with ↑ and ↓ to a
 * pending place, drawn there, and only *Done* works out the net move — asking for a time, or
 * whether to remove one, or the scope — and writes it (the planner's `app.js`: `moveUp`,
 * `moveDown`, `displayBlocks`, `finishEdit`). Pure, so every rule of the walk is a test.
 *
 * The planner's day was its blocks alone; Tendril's also has *Any time* and *Outside the blocks*.
 * They form one last group here, present only for an entry that starts in it: such an entry can
 * move out into a block and back, and nothing moves in, because leaving every block is not a move
 * the planner had (the Edit sheet clears a block or a time instead).
 */

/** One group of the day as edit mode sees it: a block's set times and the rest, or the last group (Any time and Outside). */
data class EditGroup(val blockUid: String?, val timed: List<DayRow>, val flexible: List<DayRow>)

/** Where the selected entry is: which group, set times or the rest, and its index there. */
data class EditPos(val group: Int, val timed: Boolean, val index: Int)

/** The day's groups without [except] — the lists the selected entry moves among. */
fun editGroups(view: HabitDayView, except: DayRow): List<EditGroup> {
    val blocks = view.blocks.map { b -> EditGroup(b.block.uid, b.timed - except, b.flexible - except) }
    return if (inLastGroup(view, except)) blocks + EditGroup(null, view.outside - except, view.anyTime - except) else blocks
}

private fun inLastGroup(view: HabitDayView, row: DayRow) = row in view.anyTime || row in view.outside

/** [row]'s own place: where it is before any move. */
fun originOf(view: HabitDayView, row: DayRow): EditPos {
    view.blocks.forEachIndexed { g, b ->
        b.timed.indexOf(row).takeIf { it >= 0 }?.let { return EditPos(g, timed = true, index = it) }
        b.flexible.indexOf(row).takeIf { it >= 0 }?.let { return EditPos(g, timed = false, index = it) }
    }
    val last = view.blocks.size
    view.outside.indexOf(row).takeIf { it >= 0 }?.let { return EditPos(last, timed = true, index = it) }
    return EditPos(last, timed = false, index = view.anyTime.indexOf(row).coerceAtLeast(0))
}

/** ↑: up one; from the top of the rest, into the end of the set times; from the top of a block, to the end of the block above's rest. */
fun moveUp(groups: List<EditGroup>, pos: EditPos): EditPos = when {
    pos.index > 0 -> pos.copy(index = pos.index - 1)
    !pos.timed && groups[pos.group].timed.isNotEmpty() -> pos.copy(timed = true, index = groups[pos.group].timed.size)
    pos.group > 0 -> EditPos(pos.group - 1, timed = false, index = groups[pos.group - 1].flexible.size)
    else -> pos
}

/** ↓: down one; from the end of the set times, into the top of the rest; from the end of a block, to the top of the block below (its set times first). */
fun moveDown(groups: List<EditGroup>, pos: EditPos): EditPos {
    val here = groups[pos.group]
    val count = if (pos.timed) here.timed.size else here.flexible.size
    return when {
        pos.index < count -> pos.copy(index = pos.index + 1)
        pos.timed && here.flexible.isNotEmpty() -> EditPos(pos.group, timed = false, index = 0)
        pos.group < groups.size - 1 -> EditPos(pos.group + 1, timed = groups[pos.group + 1].timed.isNotEmpty(), index = 0)
        else -> pos
    }
}

/** The groups as drawn during edit mode: [row] shown at [pos]. */
fun displayedGroups(view: HabitDayView, row: DayRow, pos: EditPos): List<EditGroup> =
    editGroups(view, row).mapIndexed { g, x ->
        if (g != pos.group) x
        else if (pos.timed) x.copy(timed = x.timed.toMutableList().apply { add(pos.index.coerceAtMost(size), row) })
        else x.copy(flexible = x.flexible.toMutableList().apply { add(pos.index.coerceAtMost(size), row) })
    }

/**
 * What *Done* has to settle for a move from [origin] to [pos]: null when the entry is back where it
 * started. [askTime] — it lands among set times and its own does not hold its place there (the
 * planner's rule: no suggestion, the person types one); [askRemoveTime] — it leaves the set times;
 * [blockUid] — the block it now sits in when that changed (a set time chooses its block itself);
 * [orders] — the rest's manual order renumbered in tens, only the entries whose order changes,
 * the moved one always among them.
 */
data class MovePlan(
    val row: DayRow,
    val askTime: Boolean,
    val askRemoveTime: Boolean,
    val blockUid: String?,
    val orders: List<Pair<DayRow, Double>>,
)

fun planMove(view: HabitDayView, row: DayRow, origin: EditPos, pos: EditPos): MovePlan? {
    if (origin == pos) return null
    val target = editGroups(view, row)[pos.group]
    if (pos.timed) {
        // the planner's rule: a time is asked for unless the entry only passed others at its own time
        val passed = if (origin.timed && origin.group == pos.group) {
            if (pos.index < origin.index) target.timed.subList(pos.index, origin.index) else target.timed.subList(origin.index, pos.index)
        } else null
        val needTime = passed == null || passed.any { it.time != row.time }
        return if (needTime) MovePlan(row, askTime = true, askRemoveTime = false, blockUid = null, orders = emptyList()) else null
    }
    val blockUid = if (origin.timed || origin.group != pos.group) target.blockUid else null
    val flexible = target.flexible.toMutableList().apply { add(pos.index.coerceAtMost(size), row) }
    val orders = flexible.mapIndexed { i, x -> x to (i + 1) * 10.0 }.filter { (x, o) -> x == row || x.sortOrder != o }
    return MovePlan(row, askTime = false, askRemoveTime = origin.timed, blockUid = blockUid, orders = orders)
}

/** The planner's `askTime` check: a time is accepted only inside one of the day's blocks. */
fun timeFitsDay(view: HabitDayView, minute: Int): Boolean = view.blocks.any { it.start <= minute && minute < it.end }

/**
 * What a change writes: scoped edits for calendar habits, and — for an interval habit, which has no
 * occurrences to scope — the habit rows themselves, changed. A move of an interval habit is therefore
 * always "from now on", and asks nothing.
 */
data class PlanWrites(val edits: List<ScheduleEdit>, val habits: List<com.tendril.app.data.habit.Habit>)

/** The scopes the sheet offers for [plan] (D10, identical choices hidden); empty for an interval habit, which is not asked. */
fun moveChoices(view: HabitDayView, plan: MovePlan, time: Int?, edits: List<ScheduleEdit>): List<ScopeChoice> {
    val occ = plan.row.occurrence ?: return emptyList()
    val change = placement(view, plan, time, edits) ?: return listOf(ScopeChoice.ONLY_THIS_ENTRY)
    return scopeChoices(view.week ?: return listOf(ScopeChoice.ONLY_THIS_ENTRY), occ, change.allowed)
}

/** [habit] as it stands on [date], every edit covering that day applied — what a change on that day starts from (the planner's `itemOf`). */
internal fun effectiveOn(habit: com.tendril.app.data.habit.Habit, date: java.time.LocalDate, edits: List<ScheduleEdit>): PlanHabit? =
    habit.toPlanHabit()?.let { dayHabit(it, date, EditIndex(edits)).habit }

private fun placement(view: HabitDayView, plan: MovePlan, time: Int?, edits: List<ScheduleEdit>): PlacementChange? {
    val occ = plan.row.occurrence ?: return null
    val habit = effectiveOn(plan.row.habit, view.date, edits) ?: return null
    val timePatch = when {
        plan.askTime -> Patch(time)
        plan.askRemoveTime -> Patch(null)
        else -> null
    }
    val order = plan.orders.firstOrNull { it.first == plan.row }?.second
    return placementChange(habit, occ, view.blocks.map { PlanBlock(it.block.uid, it.start, it.end) }, timePatch, plan.blockUid, order)
}

/** [plan] settled: [time] typed when it asked for one; [choice] and [days] from the scope sheet (ignored for an interval habit). */
fun moveWrites(view: HabitDayView, plan: MovePlan, time: Int?, choice: ScopeChoice?, days: Set<java.time.DayOfWeek>?, edits: List<ScheduleEdit>, now: java.time.Instant, newUid: () -> String): PlanWrites {
    val row = plan.row
    val occ = row.occurrence
    val outEdits = mutableListOf<ScheduleEdit>()
    val outHabits = mutableListOf<com.tendril.app.data.habit.Habit>()
    if (occ == null) {
        val h = row.habit
        outHabits += h.copy(
            time = when {
                plan.askTime -> time?.let { java.time.LocalTime.ofSecondOfDay(it * 60L) }
                plan.askRemoveTime -> null
                else -> h.time
            },
            blockUid = plan.blockUid ?: h.blockUid,
            sortOrder = plan.orders.firstOrNull { it.first == row }?.second ?: h.sortOrder,
            updatedAt = now,
        )
    } else {
        val c = choice ?: ScopeChoice.ONLY_THIS_ENTRY
        val change = placement(view, plan, time, edits) ?: return PlanWrites(emptyList(), emptyList())
        outEdits += ScheduleEdit(newUid(), EditTarget.HABIT, row.habit.uid, scopeFor(c, occ, days), changesFor(change, c), now)
    }
    // The rest's order renumbered: each neighbour under the moved entry's scope — or, when the moved
    // entry was an interval habit (a change for good), from this day on.
    for ((other, order) in plan.orders) {
        if (other == row) continue
        val o = other.occurrence
        if (o == null) {
            outHabits += other.habit.copy(sortOrder = order, updatedAt = now)
        } else {
            val scope = if (occ == null) EditScope.From(view.date) else scopeFor(choice ?: ScopeChoice.ONLY_THIS_ENTRY, o, days)
            outEdits += ScheduleEdit(newUid(), EditTarget.HABIT, other.habit.uid, scope, EditChanges(sortOrder = order), now)
        }
    }
    return PlanWrites(outEdits, outHabits)
}

/** *Skip this one…* — the scopes offered; empty for an interval habit, which has no occurrence to skip (the toolbar does not offer it). */
fun skipChoices(view: HabitDayView, row: DayRow): List<ScopeChoice> {
    val occ = row.occurrence ?: return emptyList()
    return scopeChoices(view.week ?: return listOf(ScopeChoice.ONLY_THIS_ENTRY), occ)
}

fun skipWrites(view: HabitDayView, row: DayRow, choice: ScopeChoice, days: Set<java.time.DayOfWeek>?, now: java.time.Instant, newUid: () -> String): ScheduleEdit =
    ScheduleEdit(newUid(), EditTarget.HABIT, row.habit.uid, scopeFor(choice, requireNotNull(row.occurrence) { "an interval habit has no occurrence to skip" }, days), EditChanges(skip = true), now)

/** *Move to…* another day: offered for a calendar habit; "every week from now on" only for a weekday rule's own entry (the planner's rule). */
fun canMoveWeekly(row: DayRow, edits: List<ScheduleEdit>, date: java.time.LocalDate): Boolean {
    val occ = row.occurrence ?: return false
    return occ.tag == null && !occ.moved && effectiveOn(row.habit, date, edits)?.rule is CalendarRule.Weekdays
}

/**
 * *Move to…* [target] (another day) into [blockUid]: this entry alone — a skip here and an added entry
 * there — or, [weekly], the weekday rule changed from the earlier of the two days on. [removeTime]
 * when its set time does not fall in that block on that day (the sheet asked first).
 */
fun moveToDayWrites(view: HabitDayView, row: DayRow, target: java.time.LocalDate, blockUid: String, removeTime: Boolean, weekly: Boolean, edits: List<ScheduleEdit>, now: java.time.Instant, newUid: () -> String): List<ScheduleEdit> {
    val occ = requireNotNull(row.occurrence) { "an interval habit moves by its interval, not to a day" }
    val set = when {
        row.time == null -> EditChanges(blockUid = Patch(blockUid))
        removeTime -> EditChanges(time = Patch(null), blockUid = Patch(blockUid))
        else -> EditChanges(time = Patch(row.time)) // its time still chooses its block
    }
    val u = row.habit.uid
    if (!weekly) {
        return listOf(
            ScheduleEdit(newUid(), EditTarget.HABIT, u, EditScope.Occurrence(view.date, occ.key), EditChanges(skip = true), now),
            ScheduleEdit(newUid(), EditTarget.HABIT, u, EditScope.Extra(target), set, now),
        )
    }
    val rule = effectiveOn(row.habit, view.date, edits)?.rule as? CalendarRule.Weekdays ?: error("only a weekday rule moves every week")
    val traded = rule.days - view.date.dayOfWeek + target.dayOfWeek
    val from = if (target.isBefore(view.date)) target else view.date
    return listOf(ScheduleEdit(newUid(), EditTarget.HABIT, u, EditScope.From(from), set.copy(rule = CalendarRule.Weekdays(traded)), now))
}
