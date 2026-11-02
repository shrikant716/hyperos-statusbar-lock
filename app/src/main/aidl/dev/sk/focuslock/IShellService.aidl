package dev.sk.focuslock;

interface IShellService {
    // Reserved transaction code Shizuku uses to tear the service down.
    void destroy() = 16777114;

    // Runs `sh -c cmd` as the shell user (uid 2000). Returns the exit code.
    int exec(String cmd) = 1;

    // Same, but returns stdout + stderr.
    String execOut(String cmd) = 2;
}
