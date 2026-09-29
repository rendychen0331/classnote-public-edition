package com.rendy.classnote.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rendy.classnote.feature.BackupOutcome

class DriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val bridge = SyncBridgeImpl(applicationContext)
        val backup = FeatureManager.getBackup(applicationContext, "google") ?: return Result.failure()
        return when (backup.backup(bridge)) {
            is BackupOutcome.Success     -> Result.success()
            is BackupOutcome.AuthRequired -> Result.failure()
            is BackupOutcome.Error       -> Result.retry()
        }
    }
}
