package dev.sk.focuslock

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: tap to start/stop the watcher without opening the app. */
class LockTileService : TileService() {

    override fun onStartListening() {
        render(WatcherService.running)
    }

    override fun onClick() {
        if (WatcherService.running) {
            WatcherService.stop(this)
            render(false)
            return
        }
        // Not set up yet: open the app instead of starting a watcher that can't do anything.
        val ready = Prefs(this).allowlist.isNotEmpty() &&
            checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        if (!ready) {
            openApp()
            return
        }
        try {
            WatcherService.start(this)
            render(true)
        } catch (t: Throwable) {
            openApp()
        }
    }

    private fun render(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (running) "On" else "Off"
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
