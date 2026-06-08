# Water Reminder Wear OS

Native Wear OS water reminder app written in Kotlin with Jetpack Compose for Wear OS.

## What it does

- Stores a daily goal, current-day intake, default drink amount, reminder window, and last tracked date in DataStore.
- Resets intake when the app or reminder pipeline observes a new local date.
- Schedules one upcoming reminder at a time with AlarmManager.
- Shows wearable notifications with `Drank` and `Skip` actions.
- Adds the default drink amount only once per notification action ID, which prevents duplicate action handling.
- Stops reminders for the rest of the day when the goal is reached, then schedules the next reminder window for the following day.

## Scheduling approach

This app uses `AlarmManager.setAndAllowWhileIdle()` instead of WorkManager. WorkManager periodic work is deferrable and has a minimum repeat interval, while water reminders are user-visible time-window notifications. The scheduler still avoids exact alarms, uses one-shot inexact alarms, and enforces a minimum 15-minute interval to be friendlier to Wear OS battery life.

Reminder interval:

```text
planned reminders = ceil(dailyGoalMl / defaultDrinkAmountMl)
interval = max(15 minutes, activeWindowMinutes / planned reminders)
```

For a 2000 ml goal, 250 ml drink amount, and 8:00 AM to 10:00 PM window, the app plans 8 reminders across the allowed window.

## Build and run

1. Open this folder in Android Studio.
2. Install a Wear OS emulator or connect a Wear OS device.
3. Make sure Android SDK 36 is installed.
4. Sync Gradle.
5. Run the `app` configuration on the watch target.

From a machine with Gradle installed, you can also run:

```bash
gradle :app:assembleDebug
```

The current environment did not have Gradle or an Android SDK installed, so a Gradle wrapper was not generated here.

## Important files

- `MainActivity` requests notification permission and hosts Compose UI.
- `WaterReminderViewModel` coordinates UI state, persistence, and rescheduling.
- `WaterPreferences` owns DataStore keys and daily reset/action de-duplication.
- `ReminderScheduler` calculates the next allowed reminder time.
- `ReminderNotificationReceiver` posts reminders only inside the configured time window.
- `DrinkActionReceiver` handles `Drank` and `Skip`.
- `BootReceiver` reschedules after boot, time, timezone, or package replacement events.
