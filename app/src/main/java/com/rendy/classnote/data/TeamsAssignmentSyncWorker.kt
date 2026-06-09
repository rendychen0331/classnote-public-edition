package com.rendy.classnote.data

import android.content.Context
import androidx.work.WorkerParameters

class TeamsAssignmentSyncWorker(ctx: Context, params: WorkerParameters) : BaseSyncWorker(ctx, params) {
    override val provider = "microsoft"
    override val feature = "teams"
    override fun saveSummary(prefs: AppPreferences, imported: Int, skipped: Int) {
        prefs.lastTeamsAssignmentSyncSummary = syncSummary(imported, skipped)
    }
}
