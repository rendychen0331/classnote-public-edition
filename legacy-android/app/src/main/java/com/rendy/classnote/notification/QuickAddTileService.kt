package com.rendy.classnote.notification

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.rendy.classnote.data.FeatureManager

class QuickAddTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.run {
            state = if (FeatureManager.isDownloaded(this@QuickAddTileService, "assistant"))
                Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, DismissShadeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        startActivityAndCollapse(pi)
    }
}
