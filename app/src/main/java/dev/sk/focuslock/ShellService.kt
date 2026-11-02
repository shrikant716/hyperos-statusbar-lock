package dev.sk.focuslock

import kotlin.system.exitProcess

/**
 * Shizuku UserService. Shizuku starts this class in a separate process running
 * as the shell user (uid 2000), the same identity as `adb shell` or aShell.
 */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(cmd: String): Int = run(cmd).first

    override fun execOut(cmd: String): String = run(cmd).second

    private fun run(cmd: String): Pair<Int, String> = try {
        val p = ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor() to out
    } catch (t: Throwable) {
        -1 to (t.message ?: t.toString())
    }
}
