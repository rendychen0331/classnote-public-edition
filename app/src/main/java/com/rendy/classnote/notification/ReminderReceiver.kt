package com.rendy.classnote.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.ui.ReminderAlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getLongExtra(EXTRA_NOTIFICATION_ID, -1L)
        val isSnooze = intent.getBooleanExtra("is_snooze", false)

        // Snooze 情境：title/note/category 直接從 intent 取（不查 DB）
        if (isSnooze) {
            val title = intent.getStringExtra(ReminderAlarmActivity.EXTRA_TITLE) ?: return
            val note = intent.getStringExtra(ReminderAlarmActivity.EXTRA_NOTE) ?: ""
            val category = intent.getStringExtra(ReminderAlarmActivity.EXTRA_CATEGORY)
            NotificationHelper.showReminderNotification(
                context,
                notificationId.toInt(),
                title,
                note,
                category
            )
            return
        }

        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (notificationId < 0 || reminderId < 0) return

        val app = context.applicationContext as ClassNoteApplication
        val reminderRepo = app.reminderRepository

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = reminderRepo.getReminderById(reminderId) ?: return@launch
                if (reminder.isCompleted) return@launch

                NotificationHelper.showReminderNotification(
                    context,
                    notificationId.toInt(),
                    reminder.title,
                    reminder.note,
                    reminder.category
                )

                reminderRepo.markNotificationFired(notificationId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
