package com.rendy.classnote.data

import android.content.Context
import androidx.work.WorkerParameters

class MsTodoSyncWorker(ctx: Context, params: WorkerParameters) : BaseSyncWorker(ctx, params) {
    override val provider = "microsoft"
    override val feature = "mstodo"
    override fun saveSummary(prefs: AppPreferences, imported: Int, skipped: Int) {
        prefs.lastMsTodoSyncSummary = syncSummary(imported, skipped)
    }
}
