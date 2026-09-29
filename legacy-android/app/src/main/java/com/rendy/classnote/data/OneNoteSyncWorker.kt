package com.rendy.classnote.data

import android.content.Context
import androidx.work.WorkerParameters

class OneNoteSyncWorker(ctx: Context, params: WorkerParameters) : BaseSyncWorker(ctx, params) {
    override val provider = "microsoft"
    override val feature = "onenote"
    override fun saveSummary(prefs: AppPreferences, imported: Int, skipped: Int) {
        prefs.lastOneNoteSyncSummary = syncSummary(imported, skipped)
    }
}
