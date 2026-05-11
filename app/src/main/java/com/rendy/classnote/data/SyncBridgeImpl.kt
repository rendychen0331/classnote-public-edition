package com.rendy.classnote.data

import android.content.Context
import android.util.Log
import com.rendy.classnote.data.local.ClassNoteDatabase
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import com.rendy.classnote.data.remote.ApiLogger
import com.rendy.classnote.data.remote.GeminiApi
import com.rendy.classnote.feature.KeepEventInfo
import com.rendy.classnote.feature.ReminderInsert
import com.rendy.classnote.feature.SyncBridge
import com.rendy.classnote.notification.ReminderScheduler
import com.rendy.classnote.widget.ClassNoteWidget
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class SyncBridgeImpl(override val context: Context) : SyncBridge {

    private val db by lazy { ClassNoteDatabase.getDatabase(context) }
    private val prefs by lazy { AppPreferences(context) }

    // ── Auth ──────────────────────────────────────────────────────────────

    override fun googleAccountEmails(service: String): Set<String> = when (service) {
        "gmail"     -> GoogleAuthManager.getGmailAccountEmails(context)
        "classroom" -> GoogleAuthManager.getClassroomAccountEmails(context)
        "calendar"  -> GoogleAuthManager.getCalendarAccountEmails(context)
        "tasks"     -> GoogleAuthManager.getTasksAccountEmails(context)
        "keep"      -> GoogleAuthManager.getKeepAccountEmails(context)
        "drive"     -> setOfNotNull(GoogleAuthManager.getAccount(context)?.email)
        else        -> emptySet()
    }

    // ── Database ──────────────────────────────────────────────────────────

    override suspend fun findByExternalId(externalId: String): Boolean =
        db.reminderDao().findByExternalId(externalId) != null

    override suspend fun findByTitleAndDueDate(title: String, dueDate: String): Boolean =
        db.reminderDao().findByTitleAndDueDate(title, dueDate) != null

    override suspend fun findByTitleWithNullDueDate(title: String): Boolean =
        db.reminderDao().findByTitleWithNullDueDate(title) != null

    override suspend fun insertReminderAndSchedule(data: ReminderInsert): Long {
        val entity = ReminderEntity(
            title      = data.title,
            note       = data.note,
            dueDate    = data.dueDate,
            dueTime    = data.dueTime,
            startDate  = data.startDate,
            category   = data.category,
            externalId = data.externalId,
            syncSource = data.syncSource,
            sourceName = data.sourceName
        )
        val reminderId = db.reminderDao().insertReminder(entity)
        if (data.dueDate != null) {
            scheduleDefaultNotifications(reminderId, data.dueDate, data.dueTime)
        }
        return reminderId
    }

    private suspend fun scheduleDefaultNotifications(
        reminderId: Long,
        dueDate: String,
        dueTime: String?
    ) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val notifDao = db.reminderNotificationDao()

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

        val pendingTimes = notifDao.getAllPendingNotifications().map { it.triggerAt }.toMutableSet()
        triggerTimes.map { ReminderScheduler.clampToQuietHours(it, prefs, zone) }
            .filter { it > now }
            .forEach { millis ->
                var adjusted = millis
                while (pendingTimes.contains(adjusted)) adjusted += 60_000L
                pendingTimes.add(adjusted)
                val entity = ReminderNotificationEntity(reminderId = reminderId, triggerAt = adjusted)
                val id = notifDao.insertNotification(entity)
                ReminderScheduler.scheduleNotification(context, entity.copy(id = id))
            }
    }

    // ── Side-effects ──────────────────────────────────────────────────────

    override fun refreshWidget() {
        ClassNoteWidget.refreshAll(context)
    }

    override fun logSync(manager: String, method: String, result: String, success: Boolean) {
        ApiLogger.log(manager, method, result, 0L, success)
    }

    // ── AI helpers ────────────────────────────────────────────────────────

    override suspend fun analyzeKeepNote(title: String, text: String): KeepEventInfo? {
        val apiKey = prefs.geminiApiKey
        if (apiKey.isBlank()) return null
        return try {
            GeminiApi.analyzeKeepNote(apiKey, title, text)?.let { info ->
                KeepEventInfo(
                    title    = info.title,
                    dueDate  = info.dueDate,
                    dueTime  = info.dueTime,
                    category = info.category,
                    note     = info.note
                )
            }
        } catch (e: Exception) {
            Log.e("SyncBridgeImpl", "analyzeKeepNote failed", e)
            null
        }
    }

    // ── Preferences ───────────────────────────────────────────────────────

    override fun gmailClassroomForwardEnabled(): Boolean = prefs.gmailClassroomForwardEnabled
    override fun backupNetworkType(): Int = when (prefs.backupNetworkType) {
        "wifi" -> 1
        else   -> 0
    }
    override fun googleSignedInAccountEmail(): String? = GoogleAuthManager.getAccount(context)?.email
}
