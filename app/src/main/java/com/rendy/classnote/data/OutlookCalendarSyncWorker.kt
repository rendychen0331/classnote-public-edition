package com.rendy.classnote.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rendy.classnote.data.local.ClassNoteDatabase

class OutlookCalendarSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val token = OneDriveAuthManager.acquireTokenSilent(applicationContext) ?: return Result.failure()
        val prefs = AppPreferences(applicationContext)
        val db = ClassNoteDatabase.getDatabase(applicationContext)
        val result = OutlookCalendarSyncManager.sync(applicationContext, token, db.reminderDao(), db.reminderNotificationDao())
        return when (result) {
            is OutlookCalendarSyncManager.SyncResult.Success -> {
                prefs.lastOutlookCalendarSyncSummary = "已自動匯入 ${result.imported} 筆，略過 ${result.skipped} 筆"
                Result.success()
            }
            is OutlookCalendarSyncManager.SyncResult.Error -> Result.retry()
            OutlookCalendarSyncManager.SyncResult.NoPermission -> Result.failure()
        }
    }
}
