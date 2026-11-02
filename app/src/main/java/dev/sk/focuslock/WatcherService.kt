package dev.sk.focuslock

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.service.quicksettings.TileService
import android.content.ComponentName
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that watches the foreground app and keeps
 * user_setup_complete at 0 only while an allowlisted app is in front,
 * the screen is on and the device is unlocked. Everything else means 1.
 */
class WatcherService : Service() {

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var prefs: Prefs
    private lateinit var detector: ForegroundDetector
    private lateinit var power: PowerManager
    private lateinit var keyguard: KeyguardManager

    private var locked = false
    private var lockedPkg: String? = null
    private var lastText: String? = null
    private var failures = 0

    private val tick = object : Runnable {
        override fun run() {
            try {
                step()
            } catch (t: Throwable) {
                Log.e(TAG, "tick failed", t)
            }
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _state.value = State(running = true)
        prefs = Prefs(this)
        detector = ForegroundDetector(this)
        power = getSystemService(PowerManager::class.java)
        keyguard = getSystemService(KeyguardManager::class.java)
        installCrashGuard(applicationContext)

        createChannel()
        startForeground(NOTIF_ID, buildNotification("Watching ${prefs.allowlist.size} app(s)"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        ShizukuShell.bind(this) // optional fallback writer

        thread = HandlerThread("focuslock-watcher").apply { start() }
        handler = Handler(thread.looper)
        handler.post(tick)
        requestTileUpdate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.watcherWanted = false
            stopSelf()
            return START_NOT_STICKY
        }
        prefs.watcherWanted = true
        return START_STICKY
    }

    private fun step() {
        val allow = prefs.allowlist
        val pass = prefs.passthrough
        val fg = detector.poll()
        val usable = power.isInteractive && !keyguard.isKeyguardLocked

        val wantLock = when {
            !usable -> false
            fg != null && fg in allow -> true
            fg != null && fg in pass -> locked // overlay on top of the game: keep state
            else -> false
        }
        val want = if (wantLock) 0 else 1

        if (SecureSetting.get(this) != want) {
            if (SecureSetting.set(this, want)) {
                failures = 0
                prefs.weLocked = wantLock
            } else {
                failures++
                if (failures == 1 || failures % 20 == 0) {
                    Log.w(TAG, "could not write $want (attempt $failures)")
                    notify("Can't change the setting. Grant permissions in the app.")
                }
                ShizukuShell.bind(this)
                return
            }
        }
        locked = wantLock
        if (wantLock) lockedPkg = fg
        _state.value = State(running = true, lockedPkg = if (locked) lockedPkg else null)
        notify(if (locked) "Locked: $lockedPkg" else "Watching ${allow.size} app(s)")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
        SecureSetting.restore(this)
        prefs.weLocked = false
        _state.value = State()
        requestTileUpdate()
        super.onDestroy()
    }

    // ---- notification -------------------------------------------------------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Watcher", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notify(text: String) {
        if (text == lastText) return
        lastText = text
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, WatcherService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_lock), "Stop & restore", stop).build())
            .build()
    }

    private fun requestTileUpdate() {
        try {
            TileService.requestListeningState(this, ComponentName(this, LockTileService::class.java))
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val TAG = "FocusLock"
        private const val CHANNEL = "watcher"
        private const val NOTIF_ID = 1
        private const val INTERVAL_MS = 700L
        const val ACTION_STOP = "dev.sk.focuslock.STOP"

        data class State(val running: Boolean = false, val lockedPkg: String? = null)

        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state.asStateFlow()

        val running: Boolean get() = _state.value.running

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WatcherService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WatcherService::class.java).setAction(ACTION_STOP))
        }

        private var guardInstalled = false

        /** If the process crashes, put the setting back before dying. */
        fun installCrashGuard(context: Context) {
            if (guardInstalled) return
            guardInstalled = true
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { t, e ->
                try {
                    SecureSetting.setDirect(context, 1)
                } catch (_: Throwable) {
                }
                previous?.uncaughtException(t, e)
            }
        }
    }
}
