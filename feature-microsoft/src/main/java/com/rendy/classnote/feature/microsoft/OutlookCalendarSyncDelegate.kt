package com.rendy.classnote.feature.microsoft

import android.util.Log
import com.rendy.classnote.feature.ReminderInsert
import com.rendy.classnote.feature.SyncBridge
import com.rendy.classnote.feature.SyncOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
private const val TAG = "OutlookCalSyncDelegate"

object OutlookCalendarSyncDelegate {

    suspend fun sync(bridge: SyncBridge, token: String): SyncOutcome = withContext(Dispatchers.IO) {
        try {
            val today = LocalDate.now()
            val start = today.format(DateTimeFormatter.ISO_LOCAL_DATE) + "T00:00:00"
            val end = today.plusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE) + "T23:59:59"
            val encodedStart = start.replace(":", "%3A")
            val encodedEnd = end.replace(":", "%3A")

            val url = URL("$GRAPH_BASE/me/calendarView?startDateTime=$encodedStart&endDateTime=$encodedEnd&\$top=100&\$select=id,subject,body,start,end,calendar")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Prefer", "outlook.timezone=\"Asia/Taipei\"")
            val events = JSONObject(conn.inputStream.bufferedReader().readText())
                .also { conn.disconnect() }.optJSONArray("value")

            var imported = 0; var skipped = 0

            for (i in 0 until (events?.length() ?: 0)) {
                val event = events!!.getJSONObject(i)
                val eventId = event.getString("id")
                val externalId = "outlook:$eventId"
                if (bridge.findByExternalId(externalId)) { skipped++; continue }

                val title = event.optString("subject", "").trim()
                if (title.isEmpty()) { skipped++; continue }

                val (dueDate, dueTime) = parseEventDateTime(event)
                if (dueDate == null) { skipped++; continue }

                val note = event.optJSONObject("body")?.optString("content", "")
                    ?.replace(Regex("<[^>]+>"), "")?.trim()
                    ?.let { if (it.length > 500) it.take(500) + "…" else it } ?: ""
                val calendarName = event.optJSONObject("calendar")?.optString("name", "Outlook") ?: "Outlook"

                bridge.insertReminderAndSchedule(ReminderInsert(
                    title = title, note = note,
                    dueDate = dueDate, dueTime = dueTime,
                    externalId = externalId,
                    syncSource = "outlook", sourceName = calendarName,
                    category = "REMINDER"
                ))
                imported++
            }

            if (imported > 0) bridge.refreshWidget()
            bridge.logSync("Outlook", "sync", "匯入$imported 略過$skipped", true)
            SyncOutcome.Success(imported, skipped)
        } catch (e: Exception) {
            Log.e(TAG, "sync error", e)
            bridge.logSync("Outlook", "sync", e.message ?: "error", false)
            SyncOutcome.Error(e.message ?: "同步失敗")
        }
    }

    private fun parseEventDateTime(event: JSONObject): Pair<String?, String?> {
        val dtStr = event.optJSONObject("start")?.optString("dateTime", "").orEmpty().ifBlank { return Pair(null, null) }
        return try {
            val dt = LocalDateTime.parse(dtStr.take(19), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val date = dt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val time = if (dt.hour == 0 && dt.minute == 0) null else dt.format(DateTimeFormatter.ofPattern("HH:mm"))
            Pair(date, time)
        } catch (_: Exception) { Pair(null, null) }
    }
}
