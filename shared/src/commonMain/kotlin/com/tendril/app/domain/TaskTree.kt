package com.tendril.app.domain

import com.tendril.app.data.entry.Entry

/**
 * §0.6.4 — a task with its checklist-style sub-tasks under it. One level deep by decision
 * (Things' checklist, not TickTick's tree): a child's own `parentEntryId` is never followed.
 */
data class TaskWithSubtasks(val task: Entry, val subtasks: List<Entry>) {
    val done: Int get() = subtasks.count { it.status == com.tendril.app.data.entry.EntryStatus.DONE }
}

/**
 * Groups a flat task list into parents with their children, preserving the list's own order
 * for the parents. A child whose parent is not in the list — trashed, or filtered out by the
 * caller — is promoted to a top-level row rather than dropped: a sub-task that vanished
 * because its parent was hidden would be the "silently dropped item" §2.2's undated toggle
 * exists to prevent, one level down.
 */
fun List<Entry>.withSubtasks(): List<TaskWithSubtasks> {
    val byId = associateBy { it.id }
    val childrenOf = filter { it.parentEntryId != null && it.parentEntryId in byId }.groupBy { it.parentEntryId!! }
    return filter { it.parentEntryId == null || it.parentEntryId !in byId }
        .map { TaskWithSubtasks(it, childrenOf[it.id].orEmpty()) }
}
