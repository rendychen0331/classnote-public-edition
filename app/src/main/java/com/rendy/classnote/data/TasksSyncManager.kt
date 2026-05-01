package com.rendy.classnote.data

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.rendy.classnote.data.local.dao.ReminderDao
import com.rendy.classnote.data.local.dao.ReminderNotificationDao
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import com.rendy.classnote.notification.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

object TasksSyncManager {

    sealed class SyncResult {
        data class Success(val imported: Int, val skipped: Int) : SyncResult()
        data class Error(val message: String) : SyncResult()
        object NoPermission : SyncResult()
    }

    private const val TAG = "TasksSyncManager"

    private fun buildTasksService(context: Context, email: String): Tasks {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(TasksScopes.TASKS_READONLY)
        ).apply { selectedAccountName = email }
        return Tasks.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("ClassNote")
            .build()
    }

    suspend fun sync(
        context: Context,
        email: String,
        dao: ReminderDao,
        notificationDao: ReminderNotificationDao
    ): SyncResult = withContext(Dispatchers.IO) {
        if (email.isBlank()) return@withContext SyncResult.NoPermission
        try {
            val service = buildTasksService(context, email)

            val taskLists = service.tasklists().list()
                .setMaxResults(20)
                .execute()

            var imported = 0
            var skipped = 0

            for (taskList in taskLists.items ?: emptyList()) {
                val listId = taskList.id ?: continue

                val tasks = service.tasks().list(listId)
                    .setShowCompleted(false)
                    .setMaxResults(100)
                    .execute()

                for (task in tasks.items ?: emptyList()) {
                    val taskId = task.id ?: continue
                    val externalId = "gtask:$taskId"

                    if (dao.findByExternalId(externalId) != null) {
                        skipped++
                        continue
                    }

                    val title = task.title?.trim() ?: continue
                    if (title.isBlank()) continue

                    // due 格式: "2025-04-20T00:00:00.000Z"，只取日期部分
                    val dueDate: String? = task.due?.let {
                        try { it.substring(0, 10) } catch (_: Exception) { null }
                    }

                    val reminder = ReminderEntity(
                        title = title,
                        note = task.notes?.trim() ?: "",
                        dueDate = dueDate,
                        dueTime = null,
                        externalId = externalId,
                        syncSource = "gtask",
                        sourceName = taskList.title ?: "Google Tasks",
                        category = "REMINDER"
                    )
                    val reminderId = dao.insertReminder(reminder)

                    if (dueDate != null) {
                        scheduleDefaultNotifications(context, reminderId, dueDate, notificationDao)
                    }

                    imported++
                }
            }

            Log.d(TAG, "sync done: imported=$imported, skipped=$skipped")
            SyncResult.Success(imported, skipped)
        } catch (e: Exception) {
            Log.e(TAG, "sync error", e)
            SyncResult.Error(e.message ?: "同步失敗")
        }
    }

    private suspend fun scheduleDefaultNotifications(
        context: Context,
        reminderId: Long,
        dueDate: String,
        notificationDao: ReminderNotificationDao
    ) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val prefs = AppPreferences(context)

        val base = LocalDate.parse(dueDate).atTime(prefs.defaultRemindHour, prefs.defaultRemindMinute)
        val triggerMillis = base.atZone(zone).toInstant().toEpochMilli()
        val clamped = ReminderScheduler.clampToQuietHours(triggerMillis, prefs, zone)
        if (clamped <= now) return

        val pendingTimes = notificationDao.getAllPendingNotifications().map { it.triggerAt }.toMutableSet()
        var adjusted = clamped
        while (pendingTimes.contains(adjusted)) adjusted += 60_000L

        val entity = ReminderNotificationEntity(reminderId = reminderId, triggerAt = adjusted)
        val id = notificationDao.insertNotification(entity)
        ReminderScheduler.scheduleNotification(context, entity.copy(id = id))
    }
}
