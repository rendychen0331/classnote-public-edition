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
private const val TAG = "MsTodoSyncDelegate"

object MsTodoSyncDelegate {

    suspend fun sync(bridge: SyncBridge, token: String): SyncOutcome = withContext(Dispatchers.IO) {
        try {
            val lists = graphGet(token, "$GRAPH_BASE/me/todo/lists?\$top=50").optJSONArray("value")
            var imported = 0; var skipped = 0

            for (i in 0 until (lists?.length() ?: 0)) {
                val list = lists!!.getJSONObject(i)
                val listId = list.getString("id")
                val listName = list.optString("displayName", "MS To Do")
                val tasks = graphGet(token, "$GRAPH_BASE/me/todo/lists/$listId/tasks?\$top=100&\$filter=status ne 'completed'")
                    .optJSONArray("value")

                for (j in 0 until (tasks?.length() ?: 0)) {
                    val task = tasks!!.getJSONObject(j)
                    val taskId = task.getString("id")
                    val externalId = "mstodo:$taskId"
                    if (bridge.findByExternalId(externalId)) { skipped++; continue }

                    val title = task.optString("title", "").trim()
                    if (title.isEmpty()) { skipped++; continue }
                    if (task.optString("status", "") == "completed") { skipped++; continue }

                    val (dueDate, dueTime) = parseDueDateTime(task)
                    val note = try {
                        task.optJSONObject("body")?.optString("content", "")?.trim() ?: ""
                    } catch (_: Exception) { "" }

                    bridge.insertReminderAndSchedule(ReminderInsert(
                        title = title, note = note,
                        dueDate = dueDate, dueTime = dueTime,
                        externalId = externalId,
                        syncSource = "mstodo", sourceName = listName,
                        category = "REMINDER"
                    ))
                    imported++
                }
            }

            if (imported > 0) bridge.refreshWidget()
            bridge.logSync("MsTodo", "sync", "匯入$imported 略過$skipped", true)
            SyncOutcome.Success(imported, skipped)
        } catch (e: Exception) {
            Log.e(TAG, "sync error", e)
            bridge.logSync("MsTodo", "sync", e.message ?: "error", false)
            SyncOutcome.Error(e.message ?: "同步失敗")
        }
    }

    private fun parseDueDateTime(task: JSONObject): Pair<String?, String?> {
        val dueDateStr = task.optString("dueDateTime", "").ifBlank { return Pair(null, null) }
        return try {
            val dt = LocalDateTime.parse(dueDateStr.take(19), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val date = dt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val time = if (dt.hour == 0 && dt.minute == 0) null else dt.format(DateTimeFormatter.ofPattern("HH:mm"))
            Pair(date, time)
        } catch (_: Exception) {
            try { Pair(LocalDate.parse(dueDateStr.take(10)).format(DateTimeFormatter.ISO_LOCAL_DATE), null) }
            catch (_: Exception) { Pair(null, null) }
        }
    }
}

internal fun graphGet(token: String, url: String): JSONObject {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.setRequestProperty("Authorization", "Bearer $token")
    conn.setRequestProperty("Accept", "application/json")
    val body = conn.inputStream.bufferedReader().readText()
    conn.disconnect()
    return JSONObject(body)
}
