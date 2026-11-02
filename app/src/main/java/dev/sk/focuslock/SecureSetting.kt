package dev.sk.focuslock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/** Reads and writes Settings.Secure "user_setup_complete". 1 = normal, 0 = top-edge peek blocked. */
object SecureSetting {
    const val KEY = "user_setup_complete"
    private const val TAG = "FocusLock"

    fun get(context: Context): Int =
        Settings.Secure.getInt(context.contentResolver, KEY, 1)

    fun canWriteDirect(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** Direct write with WRITE_SECURE_SETTINGS; works without Shizuku (used at boot). */
    fun setDirect(context: Context, value: Int): Boolean = try {
        Settings.Secure.putInt(context.contentResolver, KEY, value)
    } catch (e: SecurityException) {
        Log.w(TAG, "direct write failed: ${e.message}")
        false
    }

    /** Direct write first, then Shizuku shell as a fallback. */
    fun set(context: Context, value: Int): Boolean {
        if (setDirect(context, value) && get(context) == value) return true
        val code = ShizukuShell.exec("settings put secure $KEY $value")
        return code == 0 && get(context) == value
    }

    /** Never leave the device at 0 unless we mean to. */
    fun restore(context: Context): Boolean =
        if (get(context) == 1) true else set(context, 1)
}
