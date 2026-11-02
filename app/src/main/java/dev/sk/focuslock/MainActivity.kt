package dev.sk.focuslock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.sk.focuslock.ui.AppPickerScreen
import dev.sk.focuslock.ui.AppTheme
import dev.sk.focuslock.ui.MainScreen
import dev.sk.focuslock.ui.SetupStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private enum class Screen { Main, PickLocked, PickOverlay }

    private lateinit var prefs: Prefs
    private var status by mutableStateOf(SetupStatus())
    private var pendingGrant = false

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, result ->
            runOnUiThread {
                refresh()
                if (result == PackageManager.PERMISSION_GRANTED && pendingGrant) grantRequested = true
                pendingGrant = false
            }
        }
    private val binderListener = Shizuku.OnBinderReceivedListener { runOnUiThread { refresh() } }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { runOnUiThread { refresh() } }

    /** Flipped when Shizuku permission arrives so the UI runs the grants. */
    private var grantRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        setContent {
            AppTheme {
                val scope = rememberCoroutineScope()
                val snackbar = remember { SnackbarHostState() }
                val watcher by WatcherService.state.collectAsStateWithLifecycle()
                var screen by remember { mutableStateOf(Screen.Main) }
                var locked by remember { mutableStateOf(prefs.allowlist) }
                var overlays by remember { mutableStateOf(prefs.passthrough) }
                var onBoot by remember { mutableStateOf(prefs.startOnBoot) }
                var granting by remember { mutableStateOf(false) }

                fun say(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

                val notifLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { refresh() }

                fun runGrants() {
                    granting = true
                    scope.launch {
                        val failed = withContext(Dispatchers.IO) { grantAll() }
                        granting = false
                        refresh()
                        say(if (failed.isEmpty()) "All set" else "Couldn't grant: ${failed.joinToString()}")
                    }
                }

                LaunchedEffect(grantRequested) {
                    if (grantRequested) {
                        grantRequested = false
                        runGrants()
                    }
                }

                // Keep the status fresh while the screen is visible.
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                LaunchedEffect(lifecycle) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        while (true) {
                            refresh()
                            delay(1500)
                        }
                    }
                }

                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screen",
                ) { current ->
                    when (current) {
                        Screen.Main -> MainScreen(
                            status = status,
                            running = watcher.running,
                            lockedPkg = watcher.lockedPkg,
                            lockedApps = locked,
                            overlayApps = overlays,
                            startOnBoot = onBoot,
                            granting = granting,
                            versionName = BuildConfig.VERSION_NAME,
                            snackbar = snackbar,
                            onToggle = {
                                if (watcher.running) {
                                    WatcherService.stop(this@MainActivity)
                                } else {
                                    when {
                                        locked.isEmpty() -> say("Pick at least one app first")
                                        !status.usageAccess -> say("Usage access is missing. Finish setup first")
                                        !status.writeSecure && !status.shizukuPermission ->
                                            say("Can't change the setting yet. Finish setup first")
                                        else -> {
                                            if (!status.notifications && Build.VERSION.SDK_INT >= 33) {
                                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            WatcherService.start(this@MainActivity)
                                        }
                                    }
                                }
                            },
                            onGrant = {
                                when {
                                    !ShizukuShell.running() -> say("Start Shizuku first")
                                    Shizuku.isPreV11() -> say("Shizuku is too old. Update it")
                                    !ShizukuShell.hasPermission() -> {
                                        pendingGrant = true
                                        Shizuku.requestPermission(REQ_SHIZUKU)
                                    }
                                    else -> runGrants()
                                }
                            },
                            onOpenUsageAccess = {
                                startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        .setData(Uri.parse("package:$packageName"))
                                )
                            },
                            onAskNotifications = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onPickLocked = { screen = Screen.PickLocked },
                            onRemoveLocked = { pkg ->
                                locked = locked - pkg
                                prefs.allowlist = locked
                            },
                            onPickOverlay = { screen = Screen.PickOverlay },
                            onStartOnBoot = {
                                onBoot = it
                                prefs.startOnBoot = it
                            },
                            onRefreshValue = {
                                refresh()
                                say("user_setup_complete = ${SecureSetting.get(this@MainActivity)}")
                            },
                            canAddTile = Build.VERSION.SDK_INT >= 33,
                            onAddTile = { requestAddTile { say(it) } },
                            onRestore = {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        SecureSetting.set(this@MainActivity, 1)
                                    }
                                    refresh()
                                    say(if (ok) "Restored to 1" else "Restore failed. Run: settings put secure user_setup_complete 1")
                                }
                            },
                        )

                        Screen.PickLocked -> AppPickerScreen(
                            title = "Locked apps",
                            initial = locked,
                            allowManual = false,
                            onSave = {
                                locked = it
                                prefs.allowlist = it
                                screen = Screen.Main
                            },
                            onClose = { screen = Screen.Main },
                        )

                        Screen.PickOverlay -> AppPickerScreen(
                            title = "Overlay apps",
                            initial = overlays,
                            allowManual = true,
                            onSave = {
                                overlays = it
                                prefs.passthrough = it
                                screen = Screen.Main
                            },
                            onClose = { screen = Screen.Main },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }

    private fun requestAddTile(say: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) {
            say("Edit Quick Settings and drag in \"${getString(R.string.tile_label)}\"")
            return
        }
        val sbm = getSystemService(android.app.StatusBarManager::class.java)
        try {
            sbm.requestAddTileService(
                android.content.ComponentName(this, LockTileService::class.java),
                getString(R.string.tile_label),
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_lock),
                mainExecutor,
            ) { result ->
                say(
                    when (result) {
                        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Tile added"
                        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Tile is already in Quick Settings"
                        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "Tile not added"
                        else -> "Couldn't ask. Edit Quick Settings and drag in \"${getString(R.string.tile_label)}\""
                    }
                )
            }
        } catch (t: Throwable) {
            say("Edit Quick Settings and drag in \"${getString(R.string.tile_label)}\"")
        }
    }

    private fun refresh() {
        val notif = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        status = SetupStatus(
            shizukuRunning = ShizukuShell.running(),
            shizukuPermission = ShizukuShell.hasPermission(),
            writeSecure = SecureSetting.canWriteDirect(this),
            usageAccess = ForegroundDetector.hasUsageAccess(this),
            notifications = notif,
            settingValue = SecureSetting.get(this),
        )
    }

    /** One-time grants as the shell user. Returns what failed. Runs off the main thread. */
    private fun grantAll(): List<String> {
        if (!ShizukuShell.awaitBound(this)) return listOf("Shizuku service")
        val pkg = packageName
        val cmds = buildList {
            add("WRITE_SECURE_SETTINGS" to "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")
            add("Usage access" to "appops set $pkg GET_USAGE_STATS allow")
            add("Battery whitelist" to "dumpsys deviceidle whitelist +$pkg")
            if (Build.VERSION.SDK_INT >= 33) {
                add("Notifications" to "pm grant $pkg android.permission.POST_NOTIFICATIONS")
            }
        }
        return cmds.filter { (_, cmd) -> ShizukuShell.exec(cmd) != 0 }.map { it.first }
    }

    companion object {
        private const val REQ_SHIZUKU = 42
    }
}
