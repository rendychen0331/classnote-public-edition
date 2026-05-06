package com.rendy.classnote.data

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.javanet.NetHttpTransport
import com.rendy.classnote.data.local.ClassNoteDatabase
import com.rendy.classnote.data.local.dao.ReminderDao
import com.rendy.classnote.data.local.dao.ReminderNotificationDao
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import com.rendy.classnote.data.remote.GeminiApi
import com.rendy.classnote.notification.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

object KeepSyncManager {

    sealed class SyncResult {
        data class Success(val imported: Int, val skipped: Int) : SyncResult()
        data class Error(val message: String) : SyncResult()
        object NoPermission : SyncResult()
    }

    private const val TAG = "KeepSyncManager"
    const val KEEP_READONLY_SCOPE = "https://www.googleapis.com/auth/keep.readonly"
    private const val BASE_URL = "https://keep.googleapis.com/v1/notes"

    suspend fun sync(
        context: Context,
        email: String,
        dao: ReminderDao,
        notificationDao: ReminderNotificationDao
    ): SyncResult = withContext(Dispatchers.IO) {
        if (email.isBlank()) return@withContext SyncResult.NoPermission
        val prefs = AppPreferences(context)

        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(KEEP_READONLY_SCOPE)
            ).apply { selectedAccountName = email }

            val requestFactory = NetHttpTransport().createRequestFactory(credential)
            var imported = 0
            var skipped = 0
            var pageToken: String? = null

            do {
                val urlStr = buildString {
                    append(BASE_URL)
                    append("?pageSize=100")
                    if (pageToken != null) append("&pageToken=$pageToken")
                }
                val response = requestFactory.buildGetRequest(GenericUrl(urlStr)).execute()
                if (response.statusCode == 401 || response.statusCode == 403) {
                    return@withContext SyncResult.NoPermission
                }

                val body = JSONObject(response.parseAsString())
                val notes = body.optJSONArray("notes") ?: break

                for (i in 0 until notes.length()) {
                    val note = notes.getJSONObject(i)
                    if (note.optBoolean("trashed")) continue

                    val noteId = note.optString("name").removePrefix("notes/")
                    val title = note.optString("title").trim()
                    val bodyObj = note.optJSONObject("body")

                    if (bodyObj?.has("list") == true) {
                        val listItems = bodyObj.getJSONObject("list").optJSONArray("listItems")
                            ?: continue
                        for (j in 0 until listItems.length()) {
                            val item = listItems.getJSONObject(j)
                            if (item.optBoolean("checked")) continue
                            val itemText = item.optJSONObject("text")
                                ?.optString("text")?.trim() ?: continue
                            if (itemText.isBlank()) continue

                            val externalId = "keep:${noteId}:$j"
                            if (dao.findByExternalId(externalId) != null) { skipped++; continue }

                            val eventInfo = GeminiApi.analyzeKeepNote(
                                prefs.geminiApiKey, title.ifEmpty { itemText }, itemText
                            )
                            val reminder = ReminderEntity(
                                title = eventInfo?.title ?: title.ifEmpty { itemText },
                                note = eventInfo?.note ?: "",
                                dueDate = eventInfo?.dueDate,
                                dueTime = eventInfo?.dueTime,
                                externalId = externalId,
                                syncSource = "keep",
                                sourceName = "Google Keep",
                                category = eventInfo?.category ?: "REMINDER"
                            )
                            val reminderId = dao.insertReminder(reminder)
                            reminder.dueDate?.let {
                                scheduleDefaultNotifications(context, reminderId, it, notificationDao, prefs)
                            }
                            imported++
                        }
                    } else {
                        val externalId = "keep:$noteId"
                        if (dao.findByExternalId(externalId) != null) { skipped++; continue }

                        val text = bodyObj?.optJSONObject("text")
                            ?.optString("text")?.trim() ?: ""
                        val noteTitle = title.ifEmpty { text.take(50) }
                        if (noteTitle.isBlank()) continue

                        val eventInfo = GeminiApi.analyzeKeepNote(
                            prefs.geminiApiKey, noteTitle, text
                        )
                        val reminder = ReminderEntity(
                            title = eventInfo?.title ?: noteTitle,
                            note = eventInfo?.note ?: text.take(200),
                            dueDate = eventInfo?.dueDate,
                            dueTime = eventInfo?.dueTime,
                            externalId = externalId,
                            syncSource = "keep",
                            sourceName = "Google Keep",
                            category = eventInfo?.category ?: "REMINDER"
                        )
                        val reminderId = dao.insertReminder(reminder)
                        reminder.dueDate?.let {
                            scheduleDefaultNotifications(context, reminderId, it, notificationDao, prefs)
                        }
                        imported++
                    }
                }

                pageToken = body.optString("nextPageToken").ifEmpty { null }
            } while (pageToken != null)

            Log.d(TAG, "sync done: imported=$imported, skipped=$skipped")
            SyncResult.Success(imported, skipped)
        } catch (e: Exception) {
            Log.e(TAG, "sync error", e)
            val msg = e.message ?: ""
            if (msg.contains("403") || msg.contains("401") || msg.contains("PERMISSION_DENIED")) {
                SyncResult.NoPermission
            } else {
                SyncResult.Error(msg.ifEmpty { "同步失敗" })
            }
        }
    }

    private suspend fun scheduleDefaultNotifications(
        context: Context,
        reminderId: Long,
        dueDate: String,
        notificationDao: ReminderNotificationDao,
        prefs: AppPreferences
    ) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val base = LocalDate.parse(dueDate).atTime(prefs.defaultRemindHour, prefs.defaultRemindMinute)
        val triggerMillis = base.atZone(zone).toInstant().toEpochMilli()
        val clamped = ReminderScheduler.clampToQuietHours(triggerMillis, prefs, zone)
        if (clamped <= now) return

        val pendingTimes = notificationDao.getAllPendingNotifications().map { it.triggerAt }.toMutableSet()
        var adjusted = clamped
        while (pendingTimes.contains(adjusted)) adjusted += 60_000L

        val entity = ReminderNotificationEntity(reminderId = reminderId, triggerAt = adjusted)
        val id = notificationDao.insertNotification(entity)
        ReminderScheduler.scheduleNotification(context, entity.copy(id = id))
    }
}
