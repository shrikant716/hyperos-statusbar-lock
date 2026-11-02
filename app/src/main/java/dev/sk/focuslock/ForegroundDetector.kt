package dev.sk.focuslock

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/** Tracks the most recently resumed activity's package using UsageStats events. */
class ForegroundDetector(context: Context) {

    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private var since = System.currentTimeMillis() - 60 * 60 * 1000L
    private val event = UsageEvents.Event()

    var current: String? = null
        private set

    fun poll(): String? {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(since, now) ?: return current
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                current = event.packageName
            }
        }
        // Small overlap so events logged a little late are not missed.
        // Re-reading them is harmless: the last resumed one still wins.
        since = now - 3000
        return current
    }

    companion object {
        fun hasUsageAccess(context: Context): Boolean {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val mode = ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
