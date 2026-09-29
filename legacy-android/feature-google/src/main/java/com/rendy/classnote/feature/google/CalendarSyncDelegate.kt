package com.rendy.classnote.feature.google

import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.rendy.classnote.feature.ReminderInsert
import com.rendy.classnote.feature.SyncBridge
import com.rendy.classnote.feature.SyncOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val CALENDAR_READONLY = "https://www.googleapis.com/auth/calendar.readonly"
private const val TAG = "CalendarSyncDelegate"

object CalendarSyncDelegate {

    suspend fun sync(bridge: SyncBridge, email: String): SyncOutcome = withContext(Dispatchers.IO) {
        if (email.isBlank()) return@withContext SyncOutcome.NoPermission
        try {
            val service = buildService(bridge, email)
            val now = java.util.Date()
            val future = java.util.Date(now.time + 60L * 24 * 60 * 60 * 1000)
            val events = service.events().list("primary")
                .setTimeMin(DateTime(now))
                .setTimeMax(DateTime(future))
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .setMaxResults(250)
                .execute()

            var imported = 0; var skipped = 0
            val zone = ZoneId.systemDefault()

            for (event in events.items ?: emptyList()) {
                val eventId = event.id ?: continue
                val externalId = "gcal:$eventId"
                if (bridge.findByExternalId(externalId)) { skipped++; continue }

                val title = event.summary?.trim()?.takeIf { it.isNotBlank() } ?: continue
                val startDt = event.start ?: continue

                val (dueDate, dueTime, startDate) = when {
                    startDt.dateTime != null -> {
                        val localStart = LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(startDt.dateTime.value), zone)
                        val endDt = event.end?.dateTime
                        val localEnd = if (endDt != null)
                            LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(endDt.value), zone)
                        else localStart
                        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
                        Triple(
                            localEnd.toLocalDate().format(fmt),
                            localEnd.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                            if (localStart.toLocalDate() != localEnd.toLocalDate()) localStart.toLocalDate().format(fmt) else null
                        )
                    }
                    startDt.date != null -> {
                        val dateStr = startDt.date.toString()
                        val endStr = event.end?.date?.toString()
                        Triple(endStr ?: dateStr, null, if (endStr != null && endStr != dateStr) dateStr else null)
                    }
                    else -> continue
                }

                bridge.insertReminderAndSchedule(ReminderInsert(
                    title = title,
                    note = event.description?.trim() ?: "",
                    dueDate = dueDate,
                    dueTime = dueTime,
                    startDate = startDate,
                    externalId = externalId,
                    syncSource = "gcal",
                    sourceName = "Google 日曆",
                    category = "REMINDER"
                ))
                imported++
            }

            bridge.logSync("GoogleCalendar", "sync", "匯入$imported 略過$skipped", true)
            if (imported > 0) bridge.refreshWidget()
            SyncOutcome.Success(imported, skipped)
        } catch (e: UserRecoverableAuthIOException) {
            SyncOutcome.AuthRequired
        } catch (e: Exception) {
            Log.e(TAG, "sync error", e)
            bridge.logSync("GoogleCalendar", "sync", e.message ?: "失敗", false)
            SyncOutcome.Error(e.message ?: "同步失敗")
        }
    }

    private fun buildService(bridge: SyncBridge, email: String): Calendar {
        val credential = GoogleAccountCredential.usingOAuth2(
            bridge.context, listOf(CALENDAR_READONLY)
        ).apply { selectedAccountName = email }
        return Calendar.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("ClassNote").build()
    }
}
