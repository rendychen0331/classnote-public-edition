package com.rendy.classnote.notification

import android.content.Intent
import android.os.Build
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
        collapseStatusBar()
        val intent = Intent(this, FloatingQuickAddService::class.java).apply {
            action = FloatingQuickAddService.ACTION_SHOW
        }
        startForegroundService(intent)
    }
}
