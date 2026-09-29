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
            // PERF-H6：授權失效不會靠重試自癒，重試只會無限背景喚醒耗電。改回報 failure，
            // 等使用者重新授權後由 UI 重新排程。只有暫時性 Error 才 retry。
            is SyncOutcome.AuthRequired -> Result.failure()
            is SyncOutcome.Error        -> Result.retry()
            is SyncOutcome.NoPermission -> Result.failure()
        }
    }
}
