package com.rendy.classnote.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rendy.classnote.feature.BackupOutcome

class OneDriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val bridge = SyncBridgeImpl(applicationContext)
        val backup = FeatureManager.getBackup(applicationContext, "microsoft") ?: return Result.failure()
        return when (backup.backup(bridge)) {
            is BackupOutcome.Success -> {
                AppPreferences(applicationContext).lastOneDriveSyncSummary = "自動備份成功"
                Result.success()
            }
            is BackupOutcome.AuthRequired -> Result.failure()
            is BackupOutcome.Error       -> Result.retry()
        }
    }
}
