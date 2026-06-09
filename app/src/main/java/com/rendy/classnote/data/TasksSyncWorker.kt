package com.rendy.classnote.data

import android.content.Context
import androidx.work.WorkerParameters

class TasksSyncWorker(ctx: Context, params: WorkerParameters) : BaseSyncWorker(ctx, params) {
    override val provider = "google"
    override val feature = "tasks"
    override fun saveSummary(prefs: AppPreferences, imported: Int, skipped: Int) {
        prefs.lastTasksSyncSummary = syncSummary(imported, skipped)
    }
}
