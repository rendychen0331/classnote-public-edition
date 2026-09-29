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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
private const val TAG = "TeamsAssignmentSync"

object TeamsAssignmentSyncDelegate {

    suspend fun sync(bridge: SyncBridge, token: String): SyncOutcome = withContext(Dispatchers.IO) {
        try {
            val assignments = fetchAssignments(token)
            var imported = 0; var skipped = 0

            for (assignment in assignments) {
                val assignmentId = assignment.getString("id")
                val externalId = "teams:$assignmentId"
                if (bridge.findByExternalId(externalId)) { skipped++; continue }

                val title = assignment.optString("displayName", "").trim()
                if (title.isEmpty()) { skipped++; continue }
                val status = assignment.optString("status", "")
                if (status == "returned" || status == "submitted") { skipped++; continue }

                val (dueDate, dueTime) = parseDueDateTime(assignment)
                val note = assignment.optJSONObject("instructions")
                    ?.optString("content", "")
                    ?.replace(Regex("<[^>]+>"), "")?.trim()
                    ?.let { if (it.length > 500) it.take(500) + "…" else it } ?: ""
                val className = assignment.optString("classId", "Teams")

                bridge.insertReminderAndSchedule(ReminderInsert(
                    title = title, note = note,
                    dueDate = dueDate, dueTime = dueTime,
                    externalId = externalId,
                    syncSource = "teams", sourceName = className,
                    category = "REMINDER"
                ))
                imported++
            }

            if (imported > 0) bridge.refreshWidget()
            SyncOutcome.Success(imported, skipped)
        } catch (e: Exception) {
            Log.e(TAG, "sync error", e)
            SyncOutcome.Error(e.message ?: "同步失敗")
        }
    }

    private fun fetchAssignments(token: String): List<JSONObject> {
        val url = URL("$GRAPH_BASE/education/me/assignments?\$top=100&\$filter=status ne 'returned'")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        val body = conn.inputStream.bufferedReader().readText(); conn.disconnect()
        val arr = JSONObject(body).optJSONArray("value") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun parseDueDateTime(assignment: JSONObject): Pair<String?, String?> {
        val dueDt = assignment.optJSONObject("dueDateTime") ?: return Pair(null, null)
        val dtStr = dueDt.optString("dateTime", "").ifBlank { return Pair(null, null) }
        return try {
            val dt = LocalDateTime.parse(dtStr.take(19), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val date = dt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val time = if (dt.hour == 0 && dt.minute == 0) null else dt.format(DateTimeFormatter.ofPattern("HH:mm"))
            Pair(date, time)
        } catch (_: Exception) { Pair(null, null) }
    }
}
