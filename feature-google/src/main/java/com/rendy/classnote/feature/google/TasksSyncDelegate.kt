package com.rendy.classnote.feature.google

import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.tasks.Tasks
import com.rendy.classnote.feature.ReminderInsert
import com.rendy.classnote.feature.SyncBridge
import com.rendy.classnote.feature.SyncOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TASKS_READONLY = "https://www.googleapis.com/auth/tasks.readonly"
private const val TAG = "TasksSyncDelegate"

object TasksSyncDelegate {

    suspend fun sync(bridge: SyncBridge, email: String): SyncOutcome = withContext(Dispatchers.IO) {
        if (email.isBlank()) return@withContext SyncOutcome.NoPermission
        try {
            val service = buildService(bridge, email)
            val taskLists = service.tasklists().list().setMaxResults(20).execute()
            var imported = 0; var skipped = 0

            for (taskList in taskLists.items ?: emptyList()) {
                val listId = taskList.id ?: continue
                val tasks = service.tasks().list(listId).setShowCompleted(false).setMaxResults(100).execute()
                for (task in tasks.items ?: emptyList()) {
                    val taskId = task.id ?: continue
                    val externalId = "gtask:$taskId"
                    if (bridge.findByExternalId(externalId)) { skipped++; continue }
                    val title = task.title?.trim()?.takeIf { it.isNotBlank() } ?: continue
                    val dueDate = task.due?.let { runCatching { it.substring(0, 10) }.getOrNull() }
                    bridge.insertReminderAndSchedule(ReminderInsert(
                        title = title,
                        note = task.notes?.trim() ?: "",
                        dueDate = dueDate,
                        dueTime = null,
                        externalId = externalId,
                        syncSource = "gtask",
                        sourceName = taskList.title ?: "Google Tasks",
                        category = "REMINDER"
                    ))
                    imported++
                }
            }

            Log.d(TAG, "sync done: imported=$imported, skipped=$skipped")
            if (imported > 0) bridge.refreshWidget()
            SyncOutcome.Success(imported, skipped)
        } catch (e: UserRecoverableAuthIOException) {
            SyncOutcome.AuthRequired
        } catch (e: Exception) {
            Log.e(TAG, "sync error", e)
            SyncOutcome.Error(e.message ?: "同步失敗")
        }
    }

    private fun buildService(bridge: SyncBridge, email: String): Tasks {
        val credential = GoogleAccountCredential.usingOAuth2(
            bridge.context, listOf(TASKS_READONLY)
        ).apply { selectedAccountName = email }
        return Tasks.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("ClassNote").build()
    }
}
