package com.rendy.classnote.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.local.ClassNoteDatabase
import com.rendy.classnote.data.local.dao.ReminderNotificationDao
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AiConfirmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = AppPreferences(context)
        when (intent.action) {
            ACTION_CONFIRM -> {
                val json = prefs.pendingAiEvents
                prefs.pendingAiEvents = ""
                NotificationHelper.cancelPendingConfirmation(context)
                if (json.isBlank()) return
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        val arr = JSONArray(json)
                        val db = ClassNoteDatabase.getDatabase(context)
                        val dao = db.reminderDao()
                        val notifDao = db.reminderNotificationDao()
                        val addedTitles = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val title = obj.optString("title", "").trim()
                            if (title.isBlank()) continue
                            val dueDate = obj.optString("dueDate").takeIf { it != "null" && it.isNotBlank() }
                            val dueTime = obj.optString("dueTime").takeIf { it != "null" && it.isNotBlank() }
                            val category = obj.optString("category", "REMINDER")
                            val note = obj.optString("note", "")
                            val appLabel = obj.optString("appLabel", "")
                            val notifTitle = obj.optString("notifTitle", "")
                            val notifText = obj.optString("notifText", "")
                            val duplicate = if (dueDate != null) {
                                dao.findByTitleAndDueDate(title, dueDate) != null
                            } else {
                                dao.findByTitleWithNullDueDate(title) != null
                            }
                            if (duplicate) continue
                            val sourceName = notifTitle.trim().let { groupName ->
                                if (groupName.isNotBlank() && groupName != appLabel) "$appLabel・$groupName"
                                else appLabel
                            }
                            val rawNotification = buildString {
                                append("[$appLabel]")
                                if (notifTitle.isNotBlank()) append("\n$notifTitle")
                                if (notifText.isNotBlank()) append("\n$notifText")
                            }
                            val reminderId = dao.insertReminder(
                                ReminderEntity(
                                    title = title,
                                    note = note.ifBlank { notifText.take(300) },
                                    dueDate = dueDate,
                                    dueTime = dueTime,
                                    category = category,
                                    syncSource = "notify",
                                    sourceName = sourceName,
                                    rawNotification = rawNotification
                                )
                            )
                            if (dueDate != null) {
                                scheduleDefaultNotifications(context, prefs, reminderId, dueDate, dueTime, notifDao)
                            }
                            addedTitles.add(title)
                        }
                        if (addedTitles.isNotEmpty()) {
                            NotificationHelper.createAiStatusChannel(context)
                            NotificationHelper.showAiResult(context, addedTitles.size, addedTitles)
                            com.rendy.classnote.widget.ClassNoteWidget.refreshAll(context)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "confirm failed", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_CANCEL -> {
                prefs.pendingAiEvents = ""
                NotificationHelper.cancelPendingConfirmation(context)
            }
        }
    }

    private suspend fun scheduleDefaultNotifications(
        context: Context,
        prefs: AppPreferences,
        reminderId: Long,
        dueDate: String,
        dueTime: String?,
        notificationDao: ReminderNotificationDao
    ) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val triggerTimes: List<Long> = if (dueTime != null) {
            val (h, m) = dueTime.split(":").map { it.toInt() }
            val base = LocalDateTime.of(LocalDate.parse(dueDate), LocalTime.of(h, m))
            listOf(
                base.minusDays(1).atZone(zone).toInstant().toEpochMilli(),
                base.minusHours(1).atZone(zone).toInstant().toEpochMilli(),
                base.minusMinutes(1).atZone(zone).toInstant().toEpochMilli()
            )
        } else {
            val base = LocalDate.parse(dueDate).atTime(prefs.defaultRemindHour, prefs.defaultRemindMinute)
            listOf(base.atZone(zone).toInstant().toEpochMilli())
        }
        val pendingTimes = notificationDao.getAllPendingNotifications().map { it.triggerAt }.toMutableSet()
        triggerTimes.map { ReminderScheduler.clampToQuietHours(it, prefs, zone) }.filter { it > now }.forEach { millis ->
            var adjustedMillis = millis
            while (pendingTimes.contains(adjustedMillis)) adjustedMillis += 60_000L
            pendingTimes.add(adjustedMillis)
            val entity = ReminderNotificationEntity(reminderId = reminderId, triggerAt = adjustedMillis)
            val id = notificationDao.insertNotification(entity)
            ReminderScheduler.scheduleNotification(context, entity.copy(id = id))
        }
    }

    companion object {
        private const val TAG = "AiConfirmReceiver"
        const val ACTION_CONFIRM = "com.rendy.classnote.AI_CONFIRM"
        const val ACTION_CANCEL = "com.rendy.classnote.AI_CANCEL"
    }
}
