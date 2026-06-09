package com.rendy.classnote.data

import android.content.Context
import androidx.work.WorkerParameters

class GmailSyncWorker(ctx: Context, params: WorkerParameters) : BaseSyncWorker(ctx, params) {
    override val provider = "google"
    override val feature = "gmail"
    override fun saveSummary(prefs: AppPreferences, imported: Int, skipped: Int) {
        prefs.lastGmailSyncSummary = syncSummary(imported, skipped)
    }
}
