package com.rendy.classnote.data

import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import com.rendy.classnote.data.local.dao.ReminderDao
import com.rendy.classnote.data.local.dao.ReminderNotificationDao
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import com.rendy.classnote.notification.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object LocalCalendarSyncManager {

    sealed class SyncResult {
        data class Success(val imported: Int, val skipped: Int) : SyncResult()
        data class Error(val message: String) : SyncResult()
        object NoPermission : SyncResult()
    }

    private const val TAG = "LocalCalendarSyncManager"
    private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

    private val HOLIDAY_KEYWORDS = listOf("假日", "holiday", "節日", "節假日")

    private fun isHolidayCalendar(displayName: String): Boolean =
        HOLIDAY_KEYWORDS.any { displayName.contains(it, ignoreCase = true) }

    suspend fun sync(
        context: Context,
        dao: ReminderDao,
        notificationDao: ReminderNotificationDao,
        importHolidays: Boolean = false
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val future = now + 60L * 24 * 60 * 60 * 1000   // 60 天
            val zone = ZoneId.systemDefault()

            // 先讀取所有日曆的 displayName，用 calendarId 對照
            val calendarNames = queryCalendarNames(context)
            val holidayCalendarIds = if (!importHolidays)
                calendarNames.filter { (_, name) -> isHolidayCalendar(name) }.keys
            else emptySet()

            val eventProjection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.CALENDAR_ID
            )
            val selection = "${CalendarContract.Events.DTEND} >= ? AND ${CalendarContract.Events.DTSTART} <= ?" +
                " AND ${CalendarContract.Events.DELETED} = 0"
            val selArgs = arrayOf(now.toString(), future.toString())

            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                eventProjection, selection, selArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            ) ?: return@withContext SyncResult.Error("無法讀取日曆")

            var imported = 0
            var skipped = 0

            cursor.use {
                while (it.moveToNext()) {
                    val eventId = it.getLong(0)
                    val title = it.getString(1)?.trim() ?: continue
                    if (title.isBlank()) continue

                    val description = it.getString(2)?.trim() ?: ""
                    val dtStart = it.getLong(3)
                    val dtEnd = it.getLong(4)
                    val allDay = it.getInt(5) != 0
                    val calendarId = it.getLong(6)

                    if (calendarId in holidayCalendarIds) {
                        skipped++
                        continue
                    }

                    val externalId = "lcal:$calendarId:$eventId"
                    if (dao.findByExternalId(externalId) != null) {
                        skipped++
                        continue
                    }

                    val calName = calendarNames[calendarId] ?: "本地日曆"

                    val dueDate: String
                    val dueTime: String?
                    val startDate: String?

                    if (allDay) {
                        val startLocal = Instant.ofEpochMilli(dtStart).atZone(ZoneId.of("UTC")).toLocalDate()
                        // allDay end 是下一天 00:00，減一天才是實際結束日
                        val endLocal = Instant.ofEpochMilli(dtEnd).atZone(ZoneId.of("UTC")).toLocalDate().minusDays(1)
                        dueDate = endLocal.format(DATE_FMT)
                        dueTime = null
                        startDate = if (startLocal != endLocal) startLocal.format(DATE_FMT) else null
                    } else {
                        val startLocal = LocalDateTime.ofInstant(Instant.ofEpochMilli(dtStart), zone)
                        val endLocal = LocalDateTime.ofInstant(Instant.ofEpochMilli(dtEnd), zone)
                        dueDate = endLocal.toLocalDate().format(DATE_FMT)
                        dueTime = endLocal.toLocalTime().format(TIME_FMT)
                        startDate = if (startLocal.toLocalDate() != endLocal.toLocalDate())
                            startLocal.toLocalDate().format(DATE_FMT) else null
                    }

                    val reminder = ReminderEntity(
                        title = title,
                        note = description,
                        dueDate = dueDate,
                        dueTime = dueTime,
                        startDate = startDate,
                        externalId = externalId,
                        syncSource = "local_calendar",
                        sourceName = calName,
                        category = "REMINDER"
                    )
                    val reminderId = dao.insertReminder(reminder)
                    scheduleDefaultNotifications(context, reminderId, dueDate, dueTime, notificationDao)
                    imported++
                }
            }

            Log.d(TAG, "sync done: imported=$imported, skipped=$skipped")
            SyncResult.Success(imported, skipped)
        } catch (e: SecurityException) {
            Log.e(TAG, "no calendar permission", e)
            SyncResult.NoPermission
        } catch (e: Exception) {
            Log.e(TAG, "sync error", e)
            SyncResult.Error(e.message ?: "同步失敗")
        }
    }

    private fun queryCalendarNames(context: Context): Map<Long, String> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection, null, null, null
        ) ?: return emptyMap()
        val map = mutableMapOf<Long, String>()
        cursor.use {
            while (it.moveToNext()) {
                map[it.getLong(0)] = it.getString(1) ?: "日曆"
            }
        }
        return map
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
