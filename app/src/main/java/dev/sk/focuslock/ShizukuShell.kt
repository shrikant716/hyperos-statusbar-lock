package dev.sk.focuslock

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import rikka.shizuku.Shizuku

/** Thin wrapper around the Shizuku UserService that runs shell commands as uid 2000. */
object ShizukuShell {

    @Volatile
    private var service: IShellService? = null
    private val main = Handler(Looper.getMainLooper())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                service = IShellService.Stub.asInterface(binder)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private fun args(context: Context) = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShellService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    fun running(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(): Boolean = running() && !Shizuku.isPreV11() &&
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun isBound(): Boolean = service?.asBinder()?.pingBinder() == true

    fun bind(context: Context) {
        if (!hasPermission() || isBound()) return
        val app = context.applicationContext
        main.post {
            try {
                Shizuku.bindUserService(args(app), connection)
            } catch (_: Throwable) {
            }
        }
    }

    /** Blocks (off the main thread!) until bound or the timeout passes. */
    fun awaitBound(context: Context, timeoutMs: Long = 6000): Boolean {
        bind(context)
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (!isBound() && SystemClock.uptimeMillis() < end) SystemClock.sleep(100)
        return isBound()
    }

    fun exec(cmd: String): Int = try {
        service?.exec(cmd) ?: -1
    } catch (t: Throwable) {
        -1
    }

    fun execOut(cmd: String): String = try {
        service?.execOut(cmd) ?: "shizuku service not bound"
    } catch (t: Throwable) {
        t.toString()
    }
}
