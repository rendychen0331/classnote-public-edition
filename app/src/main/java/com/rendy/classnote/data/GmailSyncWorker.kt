package com.rendy.classnote.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rendy.classnote.data.local.ClassNoteDatabase

class GmailSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val emails = GoogleAuthManager.getGmailAccountEmails(applicationContext)
        if (emails.isEmpty()) return Result.failure()

        val prefs = AppPreferences(applicationContext)
        val db = ClassNoteDatabase.getDatabase(applicationContext)
        var totalImported = 0
        var totalSkipped = 0
        var hasError = false

        for (email in emails) {
            val result = GmailSyncManager.sync(
                applicationContext, email,
                db.reminderDao(), db.reminderNotificationDao(),
                prefs.gmailClassroomForwardEnabled
            )
            when (result) {
                is GmailSyncManager.SyncResult.Success -> {
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
                is GmailSyncManager.SyncResult.Error -> hasError = true
                GmailSyncManager.SyncResult.NoPermission -> {}
                is GmailSyncManager.SyncResult.AuthRequired -> hasError = true
            }
        }

        prefs.lastGmailSyncSummary = "已自動匯入 $totalImported 筆，略過 $totalSkipped 筆"
        return if (hasError && totalImported == 0) Result.retry() else Result.success()
    }
}
