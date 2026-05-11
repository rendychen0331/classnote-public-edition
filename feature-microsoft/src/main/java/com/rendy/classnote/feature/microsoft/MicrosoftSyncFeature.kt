package com.rendy.classnote.feature.microsoft

import com.rendy.classnote.feature.SyncBridge
import com.rendy.classnote.feature.SyncFeature
import com.rendy.classnote.feature.SyncOutcome

class MicrosoftSyncFeature : SyncFeature {

    override suspend fun sync(service: String, bridge: SyncBridge): SyncOutcome {
        val ctx = bridge.context
        if (service == "teams") {
            val teamsToken = MsAuthHelper.acquireTokenSilentForTeams(ctx)
                ?: return SyncOutcome.AuthRequired
            return TeamsAssignmentSyncDelegate.sync(bridge, teamsToken)
        }
        val token = MsAuthHelper.acquireTokenSilent(ctx) ?: return SyncOutcome.AuthRequired
        return when (service) {
            "mstodo"           -> MsTodoSyncDelegate.sync(bridge, token)
            "outlook_calendar" -> OutlookCalendarSyncDelegate.sync(bridge, token)
            "onenote"          -> OneNoteSyncDelegate.sync(bridge, token)
            else               -> SyncOutcome.Error("Unknown service: $service")
        }
    }

    override suspend fun syncAll(bridge: SyncBridge): Map<String, SyncOutcome> {
        val ctx = bridge.context
        val token = MsAuthHelper.acquireTokenSilent(ctx)
            ?: return mapOf("microsoft" to SyncOutcome.AuthRequired)

        val results = mutableMapOf<String, SyncOutcome>()
        results["mstodo"]           = MsTodoSyncDelegate.sync(bridge, token)
        results["outlook_calendar"] = OutlookCalendarSyncDelegate.sync(bridge, token)
        results["onenote"]          = OneNoteSyncDelegate.sync(bridge, token)

        val teamsToken = MsAuthHelper.acquireTokenSilentForTeams(ctx)
        if (teamsToken != null) results["teams"] = TeamsAssignmentSyncDelegate.sync(bridge, teamsToken)

        return results
    }
}
