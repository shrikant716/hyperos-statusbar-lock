package dev.sk.focuslock

import android.content.Context
import android.content.SharedPreferences

/**
 * Stored in device-protected storage so BootReceiver can read it before the
 * first unlock (LOCKED_BOOT_COMPLETED).
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences("focuslock", Context.MODE_PRIVATE)

    var allowlist: Set<String>
        get() = parse(sp.getString(KEY_ALLOW, DEFAULT_ALLOW)!!)
        set(v) = sp.edit().putString(KEY_ALLOW, v.joinToString("\n")).apply()

    var passthrough: Set<String>
        get() = parse(sp.getString(KEY_PASS, DEFAULT_PASS)!!)
        set(v) = sp.edit().putString(KEY_PASS, v.joinToString("\n")).apply()

    var startOnBoot: Boolean
        get() = sp.getBoolean(KEY_BOOT, false)
        set(v) = sp.edit().putBoolean(KEY_BOOT, v).apply()

    /** True while the user wants the watcher running (survives app updates). */
    var watcherWanted: Boolean
        get() = sp.getBoolean(KEY_WANTED, false)
        set(v) = sp.edit().putBoolean(KEY_WANTED, v).apply()

    /** True while this app has user_setup_complete set to 0. */
    var weLocked: Boolean
        get() = sp.getBoolean(KEY_LOCKED, false)
        set(v) {
            sp.edit().putBoolean(KEY_LOCKED, v).commit()
        }

    companion object {
        private const val KEY_ALLOW = "allowlist"
        private const val KEY_PASS = "passthrough"
        private const val KEY_BOOT = "start_on_boot"
        private const val KEY_WANTED = "watcher_wanted"
        private const val KEY_LOCKED = "we_locked"

        const val DEFAULT_ALLOW = "com.pubg.imobile"
        // Game Turbo / security center overlays on HyperOS.
        const val DEFAULT_PASS = "com.miui.securitycenter\ncom.android.systemui"

        fun parse(text: String): Set<String> =
            text.split('\n', ',', ' ', '\t')
                .map { it.trim() }
                .filter { it.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) }
                .toCollection(LinkedHashSet())
    }
}
