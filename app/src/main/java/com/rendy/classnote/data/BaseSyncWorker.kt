package com.rendy.classnote.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rendy.classnote.feature.SyncOutcome

internal fun syncSummary(imported: Int, skipped: Int) = "已自動匯入 $imported 筆，略過 $skipped 筆"

abstract class BaseSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    abstract val provider: String
    abstract val feature: String
    abstract fun saveSummary(prefs: AppPreferences, imported: Int, skipped: Int)

    override suspend fun doWork(): Result {
        val bridge = SyncBridgeImpl(applicationContext)
        val sync = FeatureManager.getSync(applicationContext, provider) ?: return Result.failure()
        return when (val r = sync.sync(feature, bridge)) {
            is SyncOutcome.Success -> {
                saveSummary(AppPreferences(applicationContext), r.imported, r.skipped)
                Result.success()
            }
            is SyncOutcome.AuthRequired -> Result.retry()
            is SyncOutcome.Error        -> Result.retry()
            is SyncOutcome.NoPermission -> Result.failure()
        }
    }
}
