package com.rendy.classnote.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rendy.classnote.data.local.ClassNoteDatabase

class ClassroomSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val emails = GoogleAuthManager.getClassroomAccountEmails(applicationContext)
        if (emails.isEmpty()) return Result.failure()

        val prefs = AppPreferences(applicationContext)
        val db = ClassNoteDatabase.getDatabase(applicationContext)
        var totalImported = 0
        var totalSkipped = 0

        for (email in emails) {
            val result = ClassroomSyncManager.sync(
                applicationContext, email,
                db.reminderDao(), db.reminderNotificationDao()
            )
            when (result) {
                is ClassroomSyncManager.SyncResult.Success -> {
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
                else -> {}
            }
        }

        prefs.lastClassroomSyncSummary = "已自動匯入 $totalImported 筆，略過 $totalSkipped 筆"
        return Result.success()
    }
}
