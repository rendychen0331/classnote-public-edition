package com.rendy.classnote.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rendy.classnote.feature.SyncOutcome

class TeamsAssignmentSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val bridge = SyncBridgeImpl(applicationContext)
        val sync = FeatureManager.getSync(applicationContext, "microsoft") ?: return Result.failure()
        return when (val r = sync.sync("teams", bridge)) {
            is SyncOutcome.Success -> {
                AppPreferences(applicationContext).lastTeamsAssignmentSyncSummary =
                    "已自動匯入 ${r.imported} 筆，略過 ${r.skipped} 筆"
                Result.success()
            }
            is SyncOutcome.AuthRequired -> Result.retry()
            is SyncOutcome.Error       -> Result.retry()
            is SyncOutcome.NoPermission -> Result.failure()
        }
    }
}
