package com.rendy.classnote.data

import android.content.Context
import androidx.work.WorkerParameters

class ClassroomSyncWorker(ctx: Context, params: WorkerParameters) : BaseSyncWorker(ctx, params) {
    override val provider = "google"
    override val feature = "classroom"
    override fun saveSummary(prefs: AppPreferences, imported: Int, skipped: Int) {
        prefs.lastClassroomSyncSummary = syncSummary(imported, skipped)
    }
}
