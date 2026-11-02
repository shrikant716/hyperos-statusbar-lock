# Status Bar Lock

Stop the status bar from dropping down when your thumb brushes the top edge in a game. Works on HyperOS, no root.

## Why this exists

realme phones have a Game Focus Mode. With it on, a swipe from the top edge does nothing while you play. HyperOS has nothing like it. In landscape games like PUBG Mobile, buttons sit right at the top edge. A fast flick there peeks the status bar, steals the touch and can cost you a fight. Game Turbo blocks the full notification shade, but the top-edge peek still gets through.

Status Bar Lock fills that gap:

- **Per app.** It only kicks in while one of the apps you picked is on screen. Everywhere else, the phone behaves normally.
- **Home gesture still works.** Swiping up from the bottom still takes you home, so you're never stuck.
- **Turns itself off.** It puts things back when you leave the game, lock the screen, stop the app, after a reboot or update, or if the app crashes.
- **Private.** It has no internet permission, no analytics and no ads. The build fails if the internet permission ever gets added.

## How it works

On HyperOS, the top-edge peek in full-screen apps is blocked while Android thinks phone setup isn't finished (`user_setup_complete = 0`). The home gesture belongs to the launcher, so it keeps working.

The app watches which app is in front. While one of your apps is open, the screen is on and the phone is unlocked, it sets the value to `0`. Every other time it's `1`, the normal value.

## Requirements

- Android 10 or newer. Built and tested on HyperOS 3 (Android 16).
- One of the following, needed once for setup:
  - [Shizuku](https://shizuku.rikka.app/), or
  - adb from a computer, or
  - adb on the phone itself (wireless debugging)

## Download

Grab the latest `StatusBarLock-vX.Y.Z.apk` from [Releases](https://github.com/shrikant716/hyperos-statusbar-lock/releases/latest). Each release also has a `.sha256` checksum. Then follow [Set up](#set-up).

Release APKs are signed with the project key, so later releases install over earlier ones. An APK you build yourself is signed with your own debug key, so you can't switch between the two without uninstalling first.

## Build it yourself

You need git, JDK 17 to 21, and the Android SDK. If Android Studio is installed, you already have the SDK.

```
git clone https://github.com/shrikant716/hyperos-statusbar-lock.git
cd hyperos-statusbar-lock
```

If the SDK isn't found, create `local.properties` with its path:

```
# macOS
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
# Linux
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
# Windows (PowerShell)
"sdk.dir=$($env:LOCALAPPDATA -replace '\\','/')/Android/Sdk" | Out-File -Encoding ascii local.properties
```

Then build:

```
./gradlew assembleDebug        # macOS / Linux
gradlew.bat assembleDebug      # Windows
```

The APK is saved as `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or copy it to the phone and open it.

If the build fails with an error showing only a Java version number (such as `25.0.1`), your default JDK is too new. Point `JAVA_HOME` at JDK 17 to 21. Android Studio's bundled JBR works.

On Xiaomi, `INSTALL_FAILED_USER_RESTRICTED` means **Developer options → Install via USB** is off. Turn it on, or copy the APK over and install it from the Files app.

## Set up

The app needs two permissions that Android only gives through adb or Shizuku. Both are granted once and survive reboots.

### Option A: Shizuku (easiest)

1. Start Shizuku. Its app shows you how.
2. Open **Status Bar Lock** and tap **Grant with Shizuku**. Allow the prompt.
3. Under **Locked apps**, pick your games. Then tap **Start**.

Once setup is done, you don't need Shizuku running anymore.

### Option B: adb from a computer (macOS, Windows, Linux)

1. Enable **Developer options**, then turn on **USB debugging**. On Xiaomi, also turn on **USB debugging (Security settings)**.
2. Connect the phone and accept the debugging prompt. `adb devices` should list it.
3. Run:

```
adb shell pm grant dev.sk.focuslock android.permission.WRITE_SECURE_SETTINGS
adb shell appops set dev.sk.focuslock GET_USAGE_STATS allow
```

On Windows, use `adb.exe` from the Android SDK `platform-tools` folder.

### Option C: adb on the phone (no computer)

Use a local adb shell app such as aShell (needs Shizuku) or LADB (uses wireless debugging). From that shell, run the same commands without the `adb shell` prefix:

```
pm grant dev.sk.focuslock android.permission.WRITE_SECURE_SETTINGS
appops set dev.sk.focuslock GET_USAGE_STATS allow
```

### Keep it alive on HyperOS

In **Settings → Apps → Status Bar Lock**:

- set **Battery saver** to **No restrictions**
- turn on **Autostart**

Otherwise HyperOS may kill the watcher in the background.

## Using it

- **Start / Stop**: the big button in the app. Stop always puts the value back to `1`.
- **Quick Settings tile**: tap **Add Quick Settings tile** under Options, or edit your Quick Settings and drag in **Bar lock**. You can then start and stop it from the status bar.
- **Notification**: while running, the notification has a **Stop & restore** action.
- **Overlay apps**: some apps pop up over the game without really leaving it, such as Game Turbo. Apps you add here keep the lock on while they're showing.
- **Start after reboot**: brings the watcher back after a restart.
- **Advanced**: shows the current `user_setup_complete` value. Tap it to refresh. If the value ever gets stuck at `0`, a **Restore to 1** button appears.

## If something goes wrong

To put the phone back to normal from any adb shell:

```
adb shell settings put secure user_setup_complete 1
```

Uninstalling the app does **not** reset the value by itself. Tap **Stop** before you uninstall.

Avoid rebooting while the value is `0`. The app resets it early in boot, but the setup wizard could still flash up first.

## Known limits

- While locked, the value is phone-wide. It only changes while your game is in front, but side swipes and notification/Quick Settings behaviour in that state aren't fully tested.
- After a partial bottom swipe that doesn't leave the game, the top edge can peek again until you reopen the game.
- Built for HyperOS. Other Android skins may handle `user_setup_complete` differently.

## License

[MIT](LICENSE)
