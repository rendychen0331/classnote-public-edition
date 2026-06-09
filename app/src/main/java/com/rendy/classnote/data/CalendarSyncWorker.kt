package com.rendy.classnote.data

import android.content.Context
import androidx.work.WorkerParameters

class CalendarSyncWorker(ctx: Context, params: WorkerParameters) : BaseSyncWorker(ctx, params) {
    override val provider = "google"
    override val feature = "calendar"
    override fun saveSummary(prefs: AppPreferences, imported: Int, skipped: Int) {
        prefs.lastCalendarSyncSummary = syncSummary(imported, skipped)
    }
}
