package com.rendy.classnote.data

import android.content.Context
import android.util.Log
import com.rendy.classnote.data.local.dao.ReminderDao
import com.rendy.classnote.data.local.dao.ReminderNotificationDao
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import com.rendy.classnote.data.remote.ApiLogger
import com.rendy.classnote.data.remote.GeminiApi
import com.rendy.classnote.notification.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object OneNoteSyncManager {

    sealed class SyncResult {
        data class Success(val imported: Int, val skipped: Int) : SyncResult()
        data class Error(val message: String) : SyncResult()
        object NoPermission : SyncResult()
    }

    private const val TAG = "OneNoteSyncManager"
    private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"

    suspend fun sync(
        context: Context,
        token: String,
        dao: ReminderDao,
        notificationDao: ReminderNotificationDao
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            val pages = fetchPages(token)
            val prefs = AppPreferences(context)
            var imported = 0
            var skipped = 0

            for (page in pages) {
                val pageId = page.getString("id")
                val externalId = "onenote:$pageId"

                if (dao.findByExternalId(externalId) != null) {
                    skipped++
                    continue
                }

                val title = page.optString("title", "").trim()
                val contentUrl = page.optString("contentUrl", "")

                val bodyText = if (contentUrl.isNotBlank()) {
                    fetchPageContent(token, contentUrl)
                } else ""

                val noteBody = "$title\n$bodyText".trim()
                if (noteBody.isBlank()) { skipped++; continue }

                val apiKey = prefs.geminiApiKey
                val eventInfo = if (apiKey.isNotBlank()) {
                    GeminiApi.analyzeKeepNote(apiKey, title, bodyText)
                } else null

                val reminder = ReminderEntity(
                    title = eventInfo?.title?.takeIf { it.isNotBlank() } ?: title,
                    note = eventInfo?.note?.takeIf { it.isNotBlank() } ?: bodyText.take(500),
                    dueDate = eventInfo?.dueDate,
                    dueTime = eventInfo?.dueTime,
                    externalId = externalId,
                    syncSource = "onenote",
                    sourceName = "OneNote",
                    category = eventInfo?.category ?: "REMINDER"
                )
                val id = dao.insertReminder(reminder)
                if (id > 0) {
                    ApiLogger.log("OneNote", "匯入：$title", "OK", 0L, true)
                    scheduleDefaultNotifications(
                        context, reminder.copy(id = id),
                        reminder.dueDate, reminder.dueTime, notificationDao
                    )
                    imported++
                }
            }
            SyncResult.Success(imported, skipped)
        } catch (e: Exception) {
            Log.e(TAG, "sync error", e)
            val code = (e as? java.io.IOException)?.message ?: e.message ?: ""
            if (code.contains("401") || code.contains("403")) return@withContext SyncResult.NoPermission
            ApiLogger.log("OneNote", "sync", e.message ?: "error", 0L, false)
            SyncResult.Error(e.message ?: "同步失敗")
        }
    }

    private fun fetchPages(token: String): List<JSONObject> {
        val url = URL("$GRAPH_BASE/me/onenote/pages?\$select=id,title,contentUrl&\$top=100")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        val code = conn.responseCode
        if (code == 401 || code == 403) throw IOException("$code Unauthorized")
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = JSONObject(body).optJSONArray("value") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun fetchPageContent(token: String, contentUrl: String): String {
        return try {
            val conn = URL(contentUrl).openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "text/html")
            val html = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            stripHtml(html)
        } catch (_: Exception) { "" }
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .take(2000)

    private suspend fun scheduleDefaultNotifications(
        context: Context,
        reminder: ReminderEntity,
        dueDate: String?,
        dueTime: String?,
        notificationDao: ReminderNotificationDao
    ) {
        if (dueDate == null) return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val prefs = AppPreferences(context)
        val triggerTimes: List<Long> = if (dueTime != null) {
            val timeParts = dueTime.split(":").map { it.toInt() }
            val base = LocalDateTime.of(LocalDate.parse(dueDate), LocalTime.of(timeParts[0], timeParts[1]))
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
            val entity = ReminderNotificationEntity(reminderId = reminder.id, triggerAt = adjustedMillis)
            val id = notificationDao.insertNotification(entity)
            ReminderScheduler.scheduleNotification(context, entity.copy(id = id))
        }
    }
}
