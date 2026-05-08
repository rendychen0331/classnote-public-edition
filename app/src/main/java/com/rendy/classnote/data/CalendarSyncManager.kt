package com.rendy.classnote.data

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.rendy.classnote.data.local.dao.ReminderDao
import com.rendy.classnote.data.local.dao.ReminderNotificationDao
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import com.rendy.classnote.notification.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CalendarSyncManager {

    sealed class SyncResult {
        data class Success(val imported: Int, val skipped: Int) : SyncResult()
        data class Error(val message: String) : SyncResult()
        object NoPermission : SyncResult()
        data class AuthRequired(val intent: android.content.Intent) : SyncResult()
    }

    private const val TAG = "CalendarSyncManager"

    private fun buildCalendarService(context: Context, email: String): Calendar {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(CalendarScopes.CALENDAR_READONLY)
        ).apply { selectedAccountName = email }
        return Calendar.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
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
            val service = buildCalendarService(context, email)

            val now = java.util.Date()
            val future = java.util.Date(now.time + 60L * 24 * 60 * 60 * 1000)

            val events = service.events().list("primary")
                .setTimeMin(DateTime(now))
                .setTimeMax(DateTime(future))
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .setMaxResults(250)
                .execute()

            var imported = 0
            var skipped = 0

            for (event in events.items ?: emptyList()) {
                val eventId = event.id ?: continue
                val externalId = "gcal:$eventId"

                if (dao.findByExternalId(externalId) != null) {
                    skipped++
                    continue
                }

                val title = event.summary?.trim() ?: continue
                if (title.isBlank()) continue

                val startDt = event.start
                val dueDate: String
                val dueTime: String?
                val startDate: String?

                if (startDt?.dateTime != null) {
                    val zone = ZoneId.systemDefault()
                    val localStart = LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(startDt.dateTime.value), zone
                    )
                    val endDt = event.end?.dateTime
                    val localEnd = if (endDt != null)
                        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(endDt.value), zone)
                    else localStart

                    dueDate = localEnd.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    dueTime = localEnd.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                    startDate = if (localStart.toLocalDate() != localEnd.toLocalDate())
                        localStart.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE) else null
                } else if (startDt?.date != null) {
                    val dateStr = startDt.date.toString()
                    val endDateStr = event.end?.date?.toString()
                    dueDate = endDateStr ?: dateStr
                    dueTime = null
                    startDate = if (endDateStr != null && endDateStr != dateStr) dateStr else null
                } else {
                    continue
                }

                val reminder = ReminderEntity(
                    title = title,
                    note = event.description?.trim() ?: "",
                    dueDate = dueDate,
                    dueTime = dueTime,
                    startDate = startDate,
                    externalId = externalId,
                    syncSource = "gcal",
                    sourceName = "Google 日曆",
                    category = "REMINDER"
                )
                val reminderId = dao.insertReminder(reminder)
                scheduleDefaultNotifications(context, reminderId, dueDate, dueTime, notificationDao)
                imported++
            }

            Log.d(TAG, "sync done: imported=$imported, skipped=$skipped")
            SyncResult.Success(imported, skipped)
        } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
            SyncResult.AuthRequired(e.intent)
        } catch (e: Exception) {
            Log.e(TAG, "sync error", e)
            SyncResult.Error(e.message ?: "同步失敗")
        }
    }

    private suspend fun scheduleDefaultNotifications(
        context: Context,
        reminderId: Long,
        dueDate: String,
        dueTime: String?,
        notificationDao: ReminderNotificationDao
    ) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val prefs = AppPreferences(context)

        val triggerTimes: List<Long> = if (dueTime != null) {
            val parts = dueTime.split(":").map { it.toInt() }
            val base = LocalDateTime.of(LocalDate.parse(dueDate), java.time.LocalTime.of(parts[0], parts[1]))
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
            var adjusted = millis
            while (pendingTimes.contains(adjusted)) adjusted += 60_000L
            pendingTimes.add(adjusted)
            val entity = ReminderNotificationEntity(reminderId = reminderId, triggerAt = adjusted)
            val id = notificationDao.insertNotification(entity)
            ReminderScheduler.scheduleNotification(context, entity.copy(id = id))
        }
    }
}
