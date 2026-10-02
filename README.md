# Pickleball for Xiaomi Smart Band 10

A standalone Vela band app and Android announcer. Tasker is not required.

Download the matching **[Android APK and band RPK from GitHub Releases](https://github.com/mahmoodw/pickleball-band/releases)**. The release bundle also includes setup instructions and source. Install both apps from the same release.

**Prototype status:** the APK and RPK compile, the APK signature verifies, and the scoring/transport tests pass. Installation, Vela rendering, Bluetooth delivery and speech with the phone locked have **not** been tested on a physical Band 10.

The band keeps the game and undo history locally. The phone receives score snapshots and speaks using an offline English Android voice. This prototype uses Xiaomi's official Interconnect SDK, which **requires Mi Fitness to have a working connection to the band**. Having Mi Fitness installed is not sufficient. Notify installs the band app; it is not the message transport for this build. Whether Mi Fitness can provide that connection alongside a Notify setup is the first device test.

## Install and connect

1. Copy `pickleball-phone-0.1.0.apk` to your Android phone and open it to install. Android 8 or newer is required. Allow installation from the file manager/browser you use if Android prompts.
2. Install `pickleball-band-0.1.0.rpk` through Notify's custom-app installation flow, as with your other band apps. The package is an app, not a watchface. Keep both supplied packages together: their package names and signing certificates match.
3. Open **Mi Fitness** and check whether it currently shows your Band 10 as connected. If it does not, resolve that connection before expecting announcements. This prototype does not manage pairing.
4. Open **Pickleball** on the phone. Tap **Grant band access / select band**, select the Band 10 and complete any Mi Fitness authorization prompt.
5. Tap **Start announcer**, allow its notification, wait for the voice status, then tap **Test phone voice**. You should hear “zero, zero, two.” If needed, use **Voice settings** to install an offline English voice and then stop/start the announcer.
6. Open **Pickleball** on the band. Start a new game, choosing singles/doubles, who serves first and 11/15/21 points. Wait for **Phone connected**, then tap **Speak**. The phone should read the displayed call; the band should eventually say **Score spoken**.

The announcer has an ongoing notification while active. Leave it running during play, then use **Stop announcer** or the notification's **Stop** action. Speech uses media volume and the phone's selected audio output, including connected headphones or speakers.

## During a game

- **We won rally / They won rally:** record the rally result. Under traditional side-out scoring, winning while receiving changes the server or serving side without adding a point.
- **Undo:** restore the entire previous state, including serve and server number. Up to 100 changes are retained.
- **Correct:** edit either score, serving side and (in doubles) server number; **Save & speak** applies the correction once. **Cancel** discards edits.
- **Speak:** repeat the current call without changing the game.
- **New:** choose new-game settings. **Start game** replaces the current game and its undo history; **Cancel** preserves it.

Doubles calls are serving score, receiving score, server number. The opening serve is `0 - 0 - 2`. Singles uses two numbers. Corrections and undo begin with “Correction.” At a winning score, the announcement gives the final score and winner. Games use win-by-two; further rallies are blocked after a win until Undo, Correct or New. Scores are limited to 99. Rally scoring and player-position tracking are outside this prototype.

Each update is saved on the band before transmission. Updates made offline stay on the band. Reconnecting synchronizes silently, and **Speak** reads the latest score. Old changes are not replayed as a speech queue. A save failure leaves the previous game unchanged. Rapid new announcements replace speech already in progress.

## First device check

1. Confirm installation of both packages and that every band button is visible and tappable.
2. With the phone unlocked, test the voice, then score one rally from the band.
3. Lock the phone, wait a minute, and score another rally. Repeat after several minutes.
4. Test Undo and a correction affecting both points and server.
5. Disconnect the band, record a rally, reconnect and verify the current score syncs silently. Tap Speak to announce it.

If it fails, record the exact phone **connection** and **voice** status and the band's status. These distinguish missing Mi Fitness service/authorization, missing band communication and unavailable TTS. **Score spoken** means Android's speech engine reported completion; it cannot establish that the phone's volume was audible on court. Android battery management may need adjustment if the announcer stops while the phone is locked.

If Mi Fitness cannot expose the band in your preferred Notify setup, this build can still keep score on the band, but announcements will not work. A Notify/Tasker adapter would be a separate fallback; none is secretly required by these packages.

## Build and test

Sources are in `band/src` and `android/src`. Both applications use `com.wmahmood.pickleball`. Build tools used here: Node 24.21.0, Xiaomi `aiot-toolkit` 2.0.5 (dependencies locked), JDK 17, Android platform 35 and build-tools 35.0.0. Android uses platform APIs and Xiaomi's SDK without Gradle or AndroidX.

Fetch the official Xiaomi SDK with `python3 scripts/fetch_xiaomi_sdk.py` and install band dependencies with `npm ci` from `band/`. Then set these environment variables to your installed tools:

```bash
export PICKLEBALL_JDK=/path/to/jdk-17
export PICKLEBALL_ANDROID_PLATFORM=/path/to/android-sdk/platforms/android-35
export PICKLEBALL_ANDROID_TOOLS=/path/to/android-sdk/build-tools/35.0.0
python3 scripts/build_android.py
```

The Android build creates a local prototype signing key and copies the matching PEM files to the band's signing directories. Next run `npm run release` from `band/`. Android output is in `dist/`; band output is in `band/dist/`.

Keep `sign/` and `band/sign/` private and preserve them for updates to an installed pair. They are deliberately excluded from the distributable source archive. Rebuilding without those keys creates a new signing identity; Android will require removal of an older differently signed build before installing it. Do not uninstall the band app to update it without first noting the current score, because uninstallation may erase the saved game.

Run `npm test` at the project root for the 11 scoring and band-controller tests. For the Android score/protocol tests, download `org.json:json:20240303` from Maven Central as a JVM-only test dependency, then run:

```bash
"$PICKLEBALL_JDK/bin/javac" -cp /path/to/json-20240303.jar -d build/java-tests android/src/com/wmahmood/pickleball/Score.java android/src/com/wmahmood/pickleball/MessageOrder.java tests/ScoreTest.java
"$PICKLEBALL_JDK/bin/java" -cp build/java-tests:/path/to/json-20240303.jar com.wmahmood.pickleball.ScoreTest
```

Those 25 checks exercise score syntax, corrections, game-over calls, input validation, duplicate/out-of-order delivery and silent synchronization before speech. They do not emulate Mi Fitness or a physical wearable.

## References

- [Xiaomi Interconnect documentation and SDK/demo download](https://iot.mi.com/vela/quickapp/en/features/network/interconnect.html)
- [Xiaomi Vela app documentation](https://iot.mi.com/vela/quickapp/en/)
- [Notify wearable apps](https://www.mibandnotify.com/xiaomi-mi-band/notify-app.php)

See `THIRD_PARTY.md` for external components.
