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

private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
private const val TAG = "OneNoteSyncDelegate"

object OneNoteSyncDelegate {

    suspend fun sync(bridge: SyncBridge, token: String): SyncOutcome = withContext(Dispatchers.IO) {
        try {
            val pages = fetchPages(token)
            var imported = 0; var skipped = 0

            for (page in pages) {
                val pageId = page.getString("id")
                val externalId = "onenote:$pageId"
                if (bridge.findByExternalId(externalId)) { skipped++; continue }

                val title = page.optString("title", "").trim()
                val contentUrl = page.optString("contentUrl", "")
                val bodyText = if (contentUrl.isNotBlank()) fetchPageContent(token, contentUrl) else ""
                val noteBody = "$title\n$bodyText".trim()
                if (noteBody.isBlank()) { skipped++; continue }

                val info = bridge.analyzeKeepNote(title, bodyText)

                bridge.insertReminderAndSchedule(ReminderInsert(
                    title = info?.title?.takeIf { it.isNotBlank() } ?: title,
                    note = info?.note?.takeIf { it.isNotBlank() } ?: bodyText.take(500),
                    dueDate = info?.dueDate, dueTime = info?.dueTime,
                    externalId = externalId,
                    syncSource = "onenote", sourceName = "OneNote",
                    category = info?.category ?: "REMINDER"
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

    private fun fetchPages(token: String): List<JSONObject> {
        val url = URL("$GRAPH_BASE/me/onenote/pages?\$top=100&\$orderby=lastModifiedDateTime desc")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        val body = conn.inputStream.bufferedReader().readText(); conn.disconnect()
        val arr = JSONObject(body).optJSONArray("value") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun fetchPageContent(token: String, contentUrl: String): String {
        return try {
            val conn = URL(contentUrl).openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")
            val text = conn.inputStream.bufferedReader().readText(); conn.disconnect()
            text.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s{2,}"), " ").trim().take(2000)
        } catch (_: Exception) { "" }
    }
}
