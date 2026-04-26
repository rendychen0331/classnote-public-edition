package com.rendy.classnote.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.rendy.classnote.data.local.ClassNoteDatabase

class GmailSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val account = GoogleSignIn.getLastSignedInAccount(applicationContext)
            ?: return Result.failure()
        if (!GmailSyncManager.hasGmailPermission(applicationContext, account))
            return Result.failure()

        val prefs = AppPreferences(applicationContext)
        val db = ClassNoteDatabase.getDatabase(applicationContext)
        val result = GmailSyncManager.sync(
            applicationContext,
            account,
            db.reminderDao(),
            db.reminderNotificationDao(),
            prefs.gmailClassroomForwardEnabled
        )

        return when (result) {
            is GmailSyncManager.SyncResult.Success -> {
                prefs.lastGmailSyncSummary = "已自動匯入 ${result.imported} 筆，略過 ${result.skipped} 筆"
                Result.success()
            }
            is GmailSyncManager.SyncResult.Error -> Result.retry()
            GmailSyncManager.SyncResult.NoPermission -> Result.failure()
        }
    }
}
