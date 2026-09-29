package com.rendy.classnote.data

import android.content.Context
import androidx.work.WorkerParameters

class KeepSyncWorker(ctx: Context, params: WorkerParameters) : BaseSyncWorker(ctx, params) {
    override val provider = "google"
    override val feature = "keep"
    override fun saveSummary(prefs: AppPreferences, imported: Int, skipped: Int) {
        prefs.lastKeepSyncSummary = syncSummary(imported, skipped)
    }
}
