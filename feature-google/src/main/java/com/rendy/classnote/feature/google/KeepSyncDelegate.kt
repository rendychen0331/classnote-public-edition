package com.rendy.classnote.feature.google

import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.javanet.NetHttpTransport
import com.rendy.classnote.feature.ReminderInsert
import com.rendy.classnote.feature.SyncBridge
import com.rendy.classnote.feature.SyncOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val KEEP_READONLY = "https://www.googleapis.com/auth/keep.readonly"
private const val BASE_URL = "https://keep.googleapis.com/v1/notes"
private const val TAG = "KeepSyncDelegate"

object KeepSyncDelegate {

    suspend fun sync(bridge: SyncBridge, email: String): SyncOutcome = withContext(Dispatchers.IO) {
        if (email.isBlank()) return@withContext SyncOutcome.NoPermission
        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                bridge.context, listOf(KEEP_READONLY)
            ).apply { selectedAccountName = email }
            val requestFactory = NetHttpTransport().createRequestFactory(credential)

            var imported = 0; var skipped = 0
            var pageToken: String? = null

            do {
                val urlStr = buildString {
                    append(BASE_URL); append("?pageSize=100")
                    if (pageToken != null) append("&pageToken=$pageToken")
                }
                val response = requestFactory.buildGetRequest(GenericUrl(urlStr)).execute()
                if (response.statusCode == 401 || response.statusCode == 403)
                    return@withContext SyncOutcome.NoPermission

                val body = JSONObject(response.parseAsString())
                val notes = body.optJSONArray("notes") ?: break

                for (i in 0 until notes.length()) {
                    val note = notes.getJSONObject(i)
                    if (note.optBoolean("trashed")) continue

                    val noteId = note.optString("name").removePrefix("notes/")
                    val title = note.optString("title").trim()
                    val bodyObj = note.optJSONObject("body")

                    if (bodyObj?.has("list") == true) {
                        val listItems = bodyObj.getJSONObject("list").optJSONArray("listItems") ?: continue
                        for (j in 0 until listItems.length()) {
                            val item = listItems.getJSONObject(j)
                            if (item.optBoolean("checked")) continue
                            val itemText = item.optJSONObject("text")?.optString("text")?.trim() ?: continue
                            if (itemText.isBlank()) continue
                            val externalId = "keep:${noteId}:$j"
                            if (bridge.findByExternalId(externalId)) { skipped++; continue }

                            val info = bridge.analyzeKeepNote(title.ifEmpty { itemText }, itemText)
                            bridge.insertReminderAndSchedule(ReminderInsert(
                                title = info?.title ?: title.ifEmpty { itemText },
                                note = info?.note ?: "",
                                dueDate = info?.dueDate, dueTime = info?.dueTime,
                                externalId = externalId,
                                syncSource = "keep", sourceName = "Google Keep",
                                category = info?.category ?: "REMINDER"
                            ))
                            imported++
                        }
                    } else {
                        val externalId = "keep:$noteId"
                        if (bridge.findByExternalId(externalId)) { skipped++; continue }
                        val text = bodyObj?.optJSONObject("text")?.optString("text")?.trim() ?: ""
                        val noteTitle = title.ifEmpty { text.take(50) }
                        if (noteTitle.isBlank()) continue

                        val info = bridge.analyzeKeepNote(noteTitle, text)
                        bridge.insertReminderAndSchedule(ReminderInsert(
                            title = info?.title ?: noteTitle,
                            note = info?.note ?: text.take(200),
                            dueDate = info?.dueDate, dueTime = info?.dueTime,
                            externalId = externalId,
                            syncSource = "keep", sourceName = "Google Keep",
                            category = info?.category ?: "REMINDER"
                        ))
                        imported++
                    }
                }

                pageToken = body.optString("nextPageToken").ifEmpty { null }
            } while (pageToken != null)

            Log.d(TAG, "sync done: imported=$imported, skipped=$skipped")
            if (imported > 0) bridge.refreshWidget()
            SyncOutcome.Success(imported, skipped)
        } catch (e: UserRecoverableAuthIOException) {
            SyncOutcome.AuthRequired
        } catch (e: Exception) {
            Log.e(TAG, "sync error", e)
            val msg = e.message ?: ""
            if (msg.contains("403") || msg.contains("401") || msg.contains("PERMISSION_DENIED"))
                SyncOutcome.NoPermission
            else SyncOutcome.Error(msg.ifEmpty { "同步失敗" })
        }
    }
}
