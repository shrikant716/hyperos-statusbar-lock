package dev.sk.focuslock

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * Works out which app is in front from UsageStats events.
 *
 * Every activity that is resumed and not yet paused or stopped is tracked, and the
 * one resumed most recently wins. When the front activity goes away without anything
 * new being resumed (a floating window closed over a game, or a partial swipe home
 * that springs back), the app still underneath takes over again.
 */
class ForegroundDetector(context: Context) {

    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private var since = System.currentTimeMillis() - 60 * 60 * 1000L
    private val event = UsageEvents.Event()

    /** "package/class" of each live resumed activity -> (package, resume time). */
    private val resumed = HashMap<String, Pair<String, Long>>()

    var current: String? = null
        private set

    fun poll(): String? {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(since, now) ?: return current
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val key = pkg + "/" + (event.className ?: "")
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    resumed[key] = pkg to event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val r = resumed[key]
                    // Ignore a pause older than the resume we have (re-read overlap).
                    if (r != null && r.second <= event.timeStamp) resumed.remove(key)
                }
            }
        }
        // Small overlap so events logged a little late are not missed.
        // Re-reading them is harmless: each event leaves the same state behind.
        since = now - 3000
        current = resumed.values.maxByOrNull { it.second }?.first
        return current
    }

    /** True if any activity of one of [packages] is still resumed (e.g. under a floating window). */
    fun anyResumed(packages: Set<String>): Boolean =
        resumed.values.any { it.first in packages }

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
