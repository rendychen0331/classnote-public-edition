package com.rendy.classnote.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rendy.classnote.data.local.ClassNoteDatabase

class MsTodoSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val token = OneDriveAuthManager.acquireTokenSilent(applicationContext) ?: return Result.failure()
        val prefs = AppPreferences(applicationContext)
        val db = ClassNoteDatabase.getDatabase(applicationContext)
        val result = MsTodoSyncManager.sync(applicationContext, token, db.reminderDao(), db.reminderNotificationDao())
        return when (result) {
            is MsTodoSyncManager.SyncResult.Success -> {
                prefs.lastMsTodoSyncSummary = "已自動匯入 ${result.imported} 筆，略過 ${result.skipped} 筆"
                Result.success()
            }
            is MsTodoSyncManager.SyncResult.Error -> Result.retry()
            MsTodoSyncManager.SyncResult.NoPermission -> Result.failure()
        }
    }
}
