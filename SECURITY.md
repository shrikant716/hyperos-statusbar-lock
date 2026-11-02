# Security Policy

## Supported versions

Only the [latest release](https://github.com/shrikant716/hyperos-statusbar-lock/releases/latest) gets security fixes.

## Reporting a vulnerability

Please **don't open a public issue** for security problems.

Report privately through GitHub instead: [Report a vulnerability](https://github.com/shrikant716/hyperos-statusbar-lock/security/advisories/new). Include:

- what the problem is and what an attacker could do with it
- steps to reproduce, with device model, Android/HyperOS version and app version
- a proof of concept, if you have one

Once a fix is released, the advisory is published with credit to you, unless you'd rather stay anonymous.

## Scope

In scope:

- the app's code, its exported components and its Shizuku shell service
- the release workflow and how the published APKs are signed

Out of scope:

- Shizuku, adb or HyperOS themselves
- the known, documented effect that `user_setup_complete` is system-wide while the lock is on (see the README's Known limits)

## Verifying a download

Each release has a `.sha256` file. Check the APK against it before you install:

```
shasum -a 256 StatusBarLock-vX.Y.Z.apk      # macOS / Linux
certutil -hashfile StatusBarLock-vX.Y.Z.apk SHA256   # Windows
```

Every release is signed with the same key (certificate `CN=shrikant716`). Android refuses to install an update signed with any other key.
