package com.rendy.classnote.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rendy.classnote.data.local.ClassNoteDatabase

class TasksSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val emails = GoogleAuthManager.getTasksAccountEmails(applicationContext)
        if (emails.isEmpty()) return Result.failure()

        val prefs = AppPreferences(applicationContext)
        val db = ClassNoteDatabase.getDatabase(applicationContext)
        var totalImported = 0
        var totalSkipped = 0

        for (email in emails) {
            val result = TasksSyncManager.sync(
                applicationContext, email,
                db.reminderDao(), db.reminderNotificationDao()
            )
            when (result) {
                is TasksSyncManager.SyncResult.Success -> {
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
                else -> {}
            }
        }

        prefs.lastTasksSyncSummary = "已自動匯入 $totalImported 筆，略過 $totalSkipped 筆"
        return Result.success()
    }
}
