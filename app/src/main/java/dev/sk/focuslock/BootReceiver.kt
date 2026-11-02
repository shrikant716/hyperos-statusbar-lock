package dev.sk.focuslock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Safety net: whatever happened before the reboot (crash, battery died, forced
 * restart), put user_setup_complete back to 1 as early as possible.
 * Then optionally start the watcher again.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        if (SecureSetting.get(context) == 0) {
            val ok = SecureSetting.setDirect(context, 1)
            Log.i("FocusLock", "boot restore user_setup_complete=1 ok=$ok (${intent.action})")
        }
        prefs.weLocked = false

        val restart = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> prefs.startOnBoot && prefs.watcherWanted
            Intent.ACTION_MY_PACKAGE_REPLACED -> prefs.watcherWanted
            else -> false
        }
        if (restart) {
            try {
                WatcherService.start(context)
            } catch (t: Throwable) {
                Log.w("FocusLock", "could not start watcher: $t")
            }
        }
    }
}
