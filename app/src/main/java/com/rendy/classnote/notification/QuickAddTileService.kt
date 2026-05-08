package com.rendy.classnote.notification

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class QuickAddTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.run {
            state = Tile.STATE_ACTIVE
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
