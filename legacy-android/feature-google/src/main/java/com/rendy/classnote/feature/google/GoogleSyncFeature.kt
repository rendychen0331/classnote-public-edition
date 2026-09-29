package com.rendy.classnote.feature.google

import com.rendy.classnote.feature.SyncBridge
import com.rendy.classnote.feature.SyncFeature
import com.rendy.classnote.feature.SyncOutcome

class GoogleSyncFeature : SyncFeature {

    override suspend fun sync(service: String, bridge: SyncBridge): SyncOutcome = when (service) {
        "gmail" -> syncGmail(bridge)
        "classroom" -> syncClassroom(bridge)
        "calendar" -> syncCalendar(bridge)
        "tasks" -> syncTasks(bridge)
        "keep" -> syncKeep(bridge)
        else -> SyncOutcome.Error("Unknown service: $service")
    }

    override suspend fun syncAll(bridge: SyncBridge): Map<String, SyncOutcome> {
        val results = mutableMapOf<String, SyncOutcome>()
        if (bridge.googleAccountEmails("gmail").isNotEmpty())      results["gmail"]      = syncGmail(bridge)
        if (bridge.googleAccountEmails("classroom").isNotEmpty())  results["classroom"]  = syncClassroom(bridge)
        if (bridge.googleAccountEmails("calendar").isNotEmpty())   results["calendar"]   = syncCalendar(bridge)
        if (bridge.googleAccountEmails("tasks").isNotEmpty())      results["tasks"]      = syncTasks(bridge)
        if (bridge.googleAccountEmails("keep").isNotEmpty())       results["keep"]       = syncKeep(bridge)
        return results
    }

    private suspend fun syncGmail(bridge: SyncBridge): SyncOutcome {
        var imported = 0; var skipped = 0; var error = false
        for (email in bridge.googleAccountEmails("gmail")) {
            when (val r = GmailSyncDelegate.sync(bridge, email)) {
                is SyncOutcome.Success -> { imported += r.imported; skipped += r.skipped }
                is SyncOutcome.Error   -> error = true
                else -> {}
            }
        }
        return if (error && imported == 0) SyncOutcome.Error("sync failed") else SyncOutcome.Success(imported, skipped)
    }

    private suspend fun syncClassroom(bridge: SyncBridge): SyncOutcome {
        var imported = 0; var skipped = 0
        for (email in bridge.googleAccountEmails("classroom")) {
            when (val r = ClassroomSyncDelegate.sync(bridge, email)) {
                is SyncOutcome.Success -> { imported += r.imported; skipped += r.skipped }
                else -> {}
            }
        }
        return SyncOutcome.Success(imported, skipped)
    }

    private suspend fun syncCalendar(bridge: SyncBridge): SyncOutcome {
        var imported = 0; var skipped = 0
        for (email in bridge.googleAccountEmails("calendar")) {
            when (val r = CalendarSyncDelegate.sync(bridge, email)) {
                is SyncOutcome.Success -> { imported += r.imported; skipped += r.skipped }
                else -> {}
            }
        }
        return SyncOutcome.Success(imported, skipped)
    }

    private suspend fun syncTasks(bridge: SyncBridge): SyncOutcome {
        var imported = 0; var skipped = 0
        for (email in bridge.googleAccountEmails("tasks")) {
            when (val r = TasksSyncDelegate.sync(bridge, email)) {
                is SyncOutcome.Success -> { imported += r.imported; skipped += r.skipped }
                else -> {}
            }
        }
        return SyncOutcome.Success(imported, skipped)
    }

    private suspend fun syncKeep(bridge: SyncBridge): SyncOutcome {
        var imported = 0; var skipped = 0
        for (email in bridge.googleAccountEmails("keep")) {
            when (val r = KeepSyncDelegate.sync(bridge, email)) {
                is SyncOutcome.Success -> { imported += r.imported; skipped += r.skipped }
                else -> {}
            }
        }
        return SyncOutcome.Success(imported, skipped)
    }
}
