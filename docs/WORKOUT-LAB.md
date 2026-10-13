# Workout Lab test

Install the two `0.1.11-workout.1` assets. They are additional downloads alongside the regular release, use the label **Pickleball Lab**, and have separate settings and game data. Select the band again inside the Lab phone app and start its announcer. Keep Notify connected throughout. Stop the regular announcer for the test.

## The three questions

1. Does the band expose its standard Bluetooth Heart Rate Service without a native workout?
2. Do fresh HR packets continue while Pickleball Lab is visible and while its screen sleeps?
3. Do Notify score messages and speech keep working at the same time?

A "connected" or "subscribed" status answers none of these by itself. Watch **Saved HR samples**, **Last HR sample age**, and **Score messages received**. Readings older than 15 seconds are not shown as live. A constant BPM is fine if new packets keep arriving; samples are throttled to one saved value per second.

**Find heart-rate source** scans for advertisements containing the standard HR service for 15 seconds. A band already connected through Notify might not advertise again. **Check already connected devices** lists Android's connected BLE devices, allowing an explicit test of the existing band connection. The app connects to the selected device, discovers the standard service and subscribes to its measurement characteristic. It never unpairs devices, extracts keys, sends proprietary Xiaomi commands or starts workouts. Only its own GATT client is closed when you finish or retry.

If no service exists, enable broadcasting if the firmware offers it and retry. If readings require the native workout, this route does not solve the app lockout. If Notify stops delivering score messages when HR connects, finish the Lab session and check the score connection again. Do not unpair the band as a troubleshooting step for this experiment.

## Screen-off check

With a test session recording and samples arriving, use the Lab band app for a few rallies. Let the band display turn off for at least a minute, then reopen it and record another rally. Repeat with the phone locked. The session timer is owned by Android and does not depend on the band running timers in its sleep state.

The CSV contains `bandVisible` reports from Vela onHide/onShow handshakes and `bandPing` probes while the app is visible. **Samples while band last reported hidden** counts HR packets received after a hidden report and before the next visible report. This is supporting evidence, not hardware screen-state detection: a missing visibility message or terminated band app can leave the last reported state unchanged. Before the first report, samples are not counted as hidden.

The band Workout Lab screen displays the latest phone snapshot. Automatic refresh occurs every eight seconds while visible, and **Refresh** requests another snapshot. If no fresh reply arrives, the HR display is cleared and marked stale after 16 seconds. Reopening the band app clears the cached live reading and re-handshakes. The timer display is a snapshot, not a second-by-second local animation.

## Save and export

Use **Finish session** before exporting the current session. Older sessions remain exportable during a new recording. Each recording gets a unique CSV file stored in the Lab app's private storage, with the start time, monotonic elapsed milliseconds, received HR, score snapshots and visibility/probe events. Files are only shared when you explicitly export them with Android's document picker. Uninstalling the Lab phone app erases its private recordings, so export anything you want to keep first.

TCX export uses the recorded start plus elapsed offsets for timestamps and does not fill missing samples. Distance/calories are unmeasured; the TCX schema requires these fields, so zero placeholders and an explanation are included. A timer-only session has no HR track. Import into Notify manually; exact import behavior and activity labeling still need testing there.

The recorder and announcer are separate foreground services with separate stop controls. Both show notifications if notification permission is granted. HR disconnects leave the session timer running so gaps remain inspectable; use **Retry heart rate** after checking the source. There is no automatic connection retry loop or automatic service restart after process death. If Android kills the process, the saved file remains, ending at the last saved event, and the UI marks the earlier recording as interrupted. A storage error is displayed and new samples are not counted as saved.

Use **Copy workout diagnostics** after the test. It excludes device addresses, device names and pairing credentials. Its counts plus the CSV distinguish absent HR, stale HR, Notify interference and app-lifecycle issues. Real hardware testing is still required before relying on this build for a full match.
