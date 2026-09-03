package com.tendril.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/** §9.7 — separate channels per source (Tasks/Events vs. Habits) for independent OS-level control. */
object NotificationChannels {
    const val TASKS_EVENTS = "tasks_events"
    const val HABITS = "habits"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(TASKS_EVENTS, "Tasks & Events", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminders and overdue alerts for tasks and events"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(HABITS, "Habits", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Habit reminders"
            }
        )
    }
}
