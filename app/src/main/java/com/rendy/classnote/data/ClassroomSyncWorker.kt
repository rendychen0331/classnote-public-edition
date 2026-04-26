package com.rendy.classnote.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rendy.classnote.data.local.ClassNoteDatabase

class ClassroomSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val email = GoogleAuthManager.getClassroomAccountEmail(applicationContext)
            ?: return Result.failure()

        val prefs = AppPreferences(applicationContext)
        val db = ClassNoteDatabase.getDatabase(applicationContext)
        val result = ClassroomSyncManager.sync(
            applicationContext,
            email,
            db.reminderDao(),
            db.reminderNotificationDao()
        )

        return when (result) {
            is ClassroomSyncManager.SyncResult.Success -> {
                prefs.lastClassroomSyncSummary = "已自動匯入 ${result.imported} 筆，略過 ${result.skipped} 筆"
                Result.success()
            }
            is ClassroomSyncManager.SyncResult.Error -> Result.retry()
            ClassroomSyncManager.SyncResult.NoPermission -> Result.failure()
        }
    }
}
