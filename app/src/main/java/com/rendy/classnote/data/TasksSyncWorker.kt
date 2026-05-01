package com.rendy.classnote.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rendy.classnote.data.local.ClassNoteDatabase

class TasksSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val email = GoogleAuthManager.getTasksAccountEmail(applicationContext)
            ?: return Result.failure()

        val prefs = AppPreferences(applicationContext)
        val db = ClassNoteDatabase.getDatabase(applicationContext)
        val result = TasksSyncManager.sync(
            applicationContext,
            email,
            db.reminderDao(),
            db.reminderNotificationDao()
        )

        return when (result) {
            is TasksSyncManager.SyncResult.Success -> {
                prefs.lastTasksSyncSummary = "已自動匯入 ${result.imported} 筆，略過 ${result.skipped} 筆"
                Result.success()
            }
            is TasksSyncManager.SyncResult.Error -> Result.retry()
            TasksSyncManager.SyncResult.NoPermission -> Result.failure()
        }
    }
}
