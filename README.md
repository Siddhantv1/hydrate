# Hydrate

Hydrate is a small water reminder for Wear OS. I wrote it for my own Galaxy Watch 4 because the reminders I tried were either too fiddly or too easy to ignore. It nudges you through the day, lets you log a glass with one tap, and keeps a streak for days you hit your goal.

The look comes from a page of pencil sketches: the glass, the bell and the buttons are drawn as pen strokes rather than taken from an icon set. It runs entirely on the watch. There's no phone app, no account, and nothing leaves the device.

It isn't on the Play Store, and isn't meant to be. You build it yourself and sideload it.

## What it does

The first time you open it, it asks for permission to show notifications. Then it walks you through a short setup: your age and gender (used only to suggest a sensible daily amount), your daily goal in ml, L or oz, and the hours you want to be reminded.

After that, the home screen is a glass that fills as you drink. Tap **+** to log a glass and the button on the right to take one back. The small line under **+** tells you when the next reminder is due, and the flame at the bottom is your current streak.

The **⋯** menu has three options:

- **Change goal**: set a new daily goal, then your reminder hours.
- **Reset timer**: pick your reminder hours again. The schedule starts over from the moment you save.
- **Ignore for today**: no more reminders until tomorrow. While today is paused, this option changes to **Resume reminders**.

The top of the menu shows when the next reminder is due. Once at least one has fired, the bottom shows when the last one went off.

When a reminder arrives, the watch plays a sound, buzzes twice and shows "Drink up!". Opening the app takes you to a screen with two buttons in the bottom corners, much like dismissing an alarm on a Galaxy watch. Drag the tick up the right edge if you drank, or the minus up the left edge to skip. A light tap does nothing, so you can't log a drink by accident.

## How reminders are timed

The gap between reminders is your reminder window divided by the number of glasses in your daily goal, and never shorter than 15 minutes. A glass is 250 ml, or 8 fl oz if you use ounces.

For example, with reminders from 1:30 PM to 10:00 PM and a goal of 2 litres (8 glasses), the window is 510 minutes, so you get a reminder roughly every 64 minutes.

The schedule counts from your last action, not from fixed clock times. Logging a glass, answering a reminder, saving new hours or resuming all set the next reminder one gap from now. That way it never pings you a minute after you've just had a drink.

A few rules sit on top of that:

- **Goal met:** reminders stop for the rest of the day and start again at the beginning of tomorrow's window.
- **Skipping:** if you skip a reminder, or let one pass without answering, the next one asks for two glasses, and confirming it logs two.
- **Exact timing:** reminders use exact alarms, so they arrive at the minute shown rather than whenever Android gets round to it. Only one alarm is scheduled at a time, and nothing runs in the background between reminders.
- **Restarts:** after a restart or a time-zone change, the next reminder is set again automatically.

## Streaks

Your streak goes up by one on each day you reach your goal. If a day ends short of the goal, it drops back to zero, and the app tells you the next time you open it. The day you first set the app up never counts as a missed day.

## What you need

- A Wear OS watch on Android 8 (API 26) or newer. It's tested on a Galaxy Watch 4 (SM-R870) running Wear OS 4 (API 33).
- A Windows, macOS or Linux computer on the same Wi-Fi network as the watch.
- JDK 17.
- The Android SDK command-line tools with `platform-tools` (for `adb`). Android Studio isn't needed.
- Git.

The project brings its own Gradle wrapper. On the first build, Gradle downloads the Android platform and libraries it needs. That works once you've accepted the SDK licences (`sdkmanager --licenses`).

## Installing it

### 1. Get the watch ready

1. On the watch, open **Settings → About watch → Software information** and tap **Software version** five times to unlock developer options.
2. Go to **Settings → Developer options** and turn on **ADB debugging** and **Wireless debugging**.
3. Make sure the watch and your computer are on the same Wi-Fi network.
4. Under **Wireless debugging**, tap **Pair new device**. The watch shows an IP address, a port and a six-digit code. On the computer, run:

   ```
   adb pair <ip>:<pairing-port> <code>
   adb connect <ip>:<connect-port>
   ```

5. Check that it shows up:

   ```
   adb devices -l
   ```

   The watch is the line with its model name (`SM_R870` for a Galaxy Watch 4). If a phone is connected as well, you'll see two devices, so use the watch's serial with `-s` in the commands below.

### 2. Build and install

```
git clone https://github.com/Siddhantv1/hydrate.git
cd hydrate
./gradlew :app:testDebugUnitTest :app:assembleRelease
adb -s <watch-serial> install -r app/build/outputs/apk/release/app-release.apk
adb -s <watch-serial> shell cmd package compile -m speed-profile -f com.example.waterreminder
```

On Windows, use `.\gradlew.bat` and backslashes in the APK path.

Install the release build, not the debug build. The debug build runs noticeably slower on the watch: it took about 4 seconds to open, against under a second for release. The release build is signed with the standard Android debug key, so you can install it over a debug build without losing your data.

The last command is optional. It compiles the app ahead of time straight away, which makes the first few screens smoother. The watch does the same thing by itself the next time it's idle and charging.

### 3. First run

Open Hydrate from the watch's app list, allow notifications, and go through the setup. That's it.

To update later, pull the latest code and run the same build and install commands. Your goal, history and streak are kept.

## Settings worth changing on the watch

Samsung's power management is aggressive, and it can hold back reminders from apps it thinks you don't use. In **Settings → Apps → Hydrate**:

- Set **Battery** to **Unrestricted**.
- Turn off **Pause app activity if unused**.

When you've finished installing, turn **Wireless debugging** off again. It keeps the Wi-Fi radio awake and drains the battery quickly.

For the reminder sound, the watch needs to be in **Sound** mode, not Vibrate. **Do Not Disturb**, **Bedtime** and **Theater** mode silence reminders, as they do every other notification.

## Troubleshooting

**The watch isn't listed in `adb devices`.** Wireless debugging switches itself off after a while, and after a restart. Turn it back on in Developer options and pair again if needed. The watch also drops Wi-Fi while it's connected to a phone over Bluetooth; set its Wi-Fi to "Always on", or turn off Bluetooth while you install.

**Pairing hangs on an office or university network.** Many managed networks stop devices from seeing each other. Turn on your phone's hotspot and connect both the computer and the watch to it.

**No reminders today.** Check the top of the **⋯** menu. It says if you've already met today's goal, or if today is paused, and when the next reminder is due.

**Reminders are late or missing.** Set battery use to Unrestricted, as described above.

**The build is very slow or gets killed.** Gradle needs a couple of gigabytes of free memory. Close other heavy programs, or run `./gradlew --stop` to shut down build daemons left over from earlier runs.

## Privacy and permissions

Everything Hydrate knows is stored in a small preferences file on the watch: your settings, today's count, your streak and the reminder schedule. It has no internet permission, collects nothing and sends nothing anywhere.

It asks for:

- **Notifications**, to show reminders.
- **Exact alarms**, so reminders arrive on time. On Android 13 and newer this permission is granted at install without asking. The Play Store only lets a few kinds of app use it, which is one reason Hydrate stays a sideloaded app.
- **Start at boot**, to schedule the next reminder again after a restart.
- **Vibrate**, used by the reminder notification only. The app itself never vibrates while you're using it.

## How the code is organised

| Folder | What's in it |
|---|---|
| `domain/` | The rules: scheduling, streaks, skips and day changes. Plain Kotlin, no Android code. |
| `data/` | `HydrateStore`, which saves the app's state with DataStore. |
| `reminder/` | Scheduling the alarm, showing the notification, and rescheduling after a reboot. |
| `ui/` | The screens, navigation, and the hand-drawn pieces (pen strokes, the water glass, the swipe buttons). |

Everything lives under `app/src/main/java/com/example/waterreminder/`. It's written in Kotlin with Jetpack Compose for Wear OS. The app's package name is `com.example.waterreminder`.

The rules have unit tests that run on your computer, with no watch needed:

```
./gradlew :app:testDebugUnitTest
```

The original design notes and the build plan are in `docs/superpowers/`. The pencil sketch the app is based on is saved next to the spec.
