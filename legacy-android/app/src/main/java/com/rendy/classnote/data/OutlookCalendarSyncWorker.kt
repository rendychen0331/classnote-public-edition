package com.rendy.classnote.data

import android.content.Context
import androidx.work.WorkerParameters

class OutlookCalendarSyncWorker(ctx: Context, params: WorkerParameters) : BaseSyncWorker(ctx, params) {
    override val provider = "microsoft"
    override val feature = "outlook_calendar"
    override fun saveSummary(prefs: AppPreferences, imported: Int, skipped: Int) {
        prefs.lastOutlookCalendarSyncSummary = syncSummary(imported, skipped)
    }
}
