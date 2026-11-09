# Hydrate: UI redesign and reminder flow

Date: 2026-11-03
Status: reviewed, awaiting owner approval

## Intent

Hydrate is a personal water-reminder app for the owner's Galaxy Watch 4 (SM-R870, Wear OS 4, Android 13 / API 33). It is sideloaded over wireless ADB and will never be published.

The current app (in this repo) works but is a plain list of chips and steppers with no personality and no animation. The owner sketched a new flow ([2026-11-03-hydrate-sketch.jpg](2026-11-03-hydrate-sketch.jpg), the primary visual reference; screen numbers below refer to it) (first-launch onboarding, glass-shaped home screen, alarm-style "Drink up!" screen, streaks) and wants it to feel hand-made and specific, **not generic or "AI-sloppy"**.

Underneath, this is an alarm app. It must stay battery-cheap: nothing runs between reminders.

## Goals

- First-launch onboarding: notification permission → age → gender → goal + unit → active hours → "All set!".
- Home screen: a glass that fills toward the daily goal, showing glasses done / glasses total (e.g. `2/8`).
- Reminders that make a sound **and** vibrate, then lead to a full-screen "Drink up!" screen with the alarm-style swipe controls.
- Streaks: +1 when the day's goal is met; reset to 0 when a day ends without meeting it.
- A deliberate, non-generic visual identity (see *Visual direction*).

## Non-goals

- Publishing, Play Store compliance, a phone companion app, sync, accounts, cloud.
- Tiles, complications, watch faces (possible later; not now).
- Health Connect / Samsung Health integration, sensors, heart-rate, activity-based goals.
- Localization beyond English. Multiple users.

## Anti-goals (would be a failure even if it "works")

- **Looks AI-generated / generic**: emoji as icons, stock Material icons everywhere, purple/indigo gradients, glassmorphism cards, default Wear Material chips stacked in a list, centered-everything with no hierarchy, filler copy.
- **Drains battery**: looping/infinite animations, ambient/always-on mode, polling, wake locks, background services, sensors, network.
- **Unreliable reminders**: alarms delayed by up to an hour (the current inexact-alarm behaviour), or reminders silently lost after reboot.
- **Accidental actions**: a stray tap marking a drink done or skipped.

## Constraints

- Device: Galaxy Watch 4 44 mm, 450×450 round AMOLED, touch bezel (emits rotary scroll events), Android 13 / API 33.
- Toolchain already working in this repo: Kotlin 2.3.21, AGP 8.13.0, Gradle 9.4.1 (wrapper committed), Compose BOM 2026.05.00, Wear Compose 1.6.2, DataStore, JDK 17. `services.gradle.org` is unreachable from this network; the Gradle distribution is already cached. Google Maven and Maven Central are reachable.
- Build and install from the CLI only (no Android Studio, no emulator):
  `.\gradlew.bat :app:assembleDebug` then `adb -s <watch-serial> install -r app\build\outputs\apk\debug\app-debug.apk`. A phone is also connected to ADB, so always target the watch serial.
- Samsung's background freezer kills idle apps aggressively; everything that must happen later goes through AlarmManager, never a lingering coroutine.

## The flow (as the owner described it)

### 1. First launch (once)

1. **Notification permission**: Android's system prompt (`POST_NOTIFICATIONS`, required on API 33).
2. **Your age**: a vertical scroll wheel (owner's sketch: 17 / **18** / 19 / 20 with the selected value boxed). Bezel scrolls it.
3. **Your gender**: two large round buttons, ♂ and ♀ (drawn as custom glyphs, not emoji). Tapping one advances.
4. **Set goal**: unit choice (ml, L, or oz, user's choice), a vertical value picker, and a "set goal automatically" tick. When ticked, the goal is suggested from age + gender; the user can still adjust it. **Confirm** advances.
5. **Active hours**: begin time and end time.
6. **All set!**: a short celebratory beat, then the home screen.

Onboarding completion is persisted; it never shows again unless app data is cleared.

### 2. Home screen

Per the owner's sketch: a glass in the centre with the count (`1/8`) and a water line that rises as the day progresses. A **⋯** button on the left, a second small button on the right, and a **+** button below.

- **+** logs one glass (same rise animation as the "Hell yeah!" screen, inline).
- **Right button = undo last glass** *(Claude's call: the sketch shows an unlabelled filled circle; the owner did not object to this reading in the mockup review).*
- **⋯** opens a menu with exactly three items:
  - **Change goal** → the Set goal screen, then Active hours, then back home *(Claude's call: hours have to be editable somewhere and this is the only path)*.
  - **Reset timer** → the next reminder is rescheduled to *now + one interval* *(Claude's call)*.
  - **Ignore for today** → no more reminders until tomorrow's start time. The streak rule still applies: if the goal isn't met by day end, the streak resets.

### 3. Each reminder

1. A notification fires **with sound and vibration**. Tapping it opens the app on the **Drink up!** screen. The owner wants the user to have to open the app; the notification itself carries no action buttons.
2. **Drink up!** (owner's sketch #4, validated in the drag prototype): a bell, "Drink up!", the amount (e.g. "250 ml · glass 3 of 8"), and two buttons at the bottom of the round screen:
   - **−** bottom-left, dragged **up along the left edge** = skip.
   - **✓** bottom-right, dragged **up along the right edge** = drank.
   - Each sits on a faint curved track that follows the screen edge (~90° of arc). The track fills behind the button as it moves. A tick mark sits at **65 %** of the track (owner approved this threshold in the prototype). Release past it triggers the action; release before it springs back. **No haptics here, or anywhere in the app UI (owner).** The watch vibrates only when a reminder notification arrives.
   - Modelled on Samsung's Galaxy Watch alarm-dismiss interaction (drag the X along the edge). Custom-built: Samsung's alarm UI is not reusable by third-party apps.
   - System swipe-to-dismiss (swipe right = back) is **disabled on this screen only**, so dragging the left button can't trigger it.
3. **Drank → "Hell yeah!"** (sketch #6): the glass with the water rising from n/8 to (n+1)/8, the count ticking up, "Hell yeah!". Returns home after ~2 s.
4. **Skipped → "Make it two glasses next time"** (sketch #5) with an **OK** button. **The next reminder asks for two glasses, and ✓ on it counts as two** *(Claude's call, shown in the mockup without objection)*. Skipping again does not stack beyond two *(Claude's call)*.
5. **Unanswered reminder = skip (owner).** If a reminder is still outstanding when the next one fires, the missed one counts as a skip: the "two glasses" rule kicks in. The owner wants this delivered as a notification with sound + vibration, so the new reminder's notification says so (e.g. "Missed one. Make it two glasses."). No separate extra notification *(Claude's reading of the owner's instruction)*.

### 4. End of day

- **Goal reached → "All done!"** (sketch #3, a full bowl), immediately followed by **streak +1** (sketch #7: "Goals met", flame, count, ✓ to dismiss). Reminders stop for the rest of the day.
- **Goal missed**: when a day ends without the goal met, the streak resets to 0. The **"You didn't meet your goals"** screen (sketch #8: dim flame, 0, ✓) is shown **the next time the user opens the app** *(Claude's call: no extra alarm just to deliver bad news)*.

## Behaviour decisions (Claude's calls unless marked)

- **Goal unit and glasses.** Goal is stored internally in ml. Display unit is ml, L, or oz (owner). Glass size is fixed at **250 ml**, or **8 fl oz** when the unit is oz. Glasses total = `ceil(goal / glass)`. Whether a glass-size control is needed is left open; default is no control.
- **Automatic goal.** Suggest from age + gender using EFSA adequate-intake figures for total water as a drinking target: adult male 2.5 L, adult female 2.0 L, ages 9–13 ~1.9–2.1 L, rounded to whole glasses. Not medical advice; it's a starting value the user can change. The builder may refine the table but must keep it a small lookup, not a formula library.
- **Reminder spacing.** Interval formula stays: `interval = max(15 min, activeWindow / glassesTotal)`. **Scheduling becomes relative, not fixed slots:** a persisted `nextReminderAt` is the single source of truth. Any interaction (✓, −, **+** on home, Reset timer) sets `nextReminderAt = now + interval`. A reminder firing sets it to `fireTime + interval`. The result is clamped into the active window (before start → today's start, after end → tomorrow's start). Recalculation on app open must *not* overwrite a valid future `nextReminderAt`. One alarm scheduled at a time.
- **Outstanding reminder.** When a reminder fires it sets an `outstanding` flag. While it's set, opening the app (from the notification *or* the launcher) lands on Drink up!. Leaving Drink up! with the hardware back button keeps it outstanding. ✓, −, or **+** on home clears it (and dismisses the notification).
- **"Two glasses next time".** Applies to the next Drink up! only. ✓ on it logs two glasses and clears it. + on home logs one glass and does not clear it.
- **Exact alarms.** Switch from `setAndAllowWhileIdle` (Android gave it a 1-hour window on the device) to `setExactAndAllowWhileIdle` using the `USE_EXACT_ALARM` permission (auto-granted on API 33+, fine because the app is never published). Declare `SCHEDULE_EXACT_ALARM` with `maxSdkVersion="32"` for older devices. Same number of wake-ups, so no battery difference.
- **Notification channel.** New channel ID (channel settings are immutable once created), `IMPORTANCE_HIGH` so it pops up on the watch, default notification sound, vibration on with a distinctive double-buzz pattern. The watch's own sound mode, DND, Bedtime, and Theater modes still apply; that's expected.
- **Day boundary.** A "day" is the local calendar date. Rollover is detected whenever the app or a reminder runs (as today). On rollover, if yesterday's goal wasn't met, the streak resets and a flag is set to show the missed-goal screen on next app open.
- **Reboot / time change.** Keep the existing `BootReceiver` behaviour (reschedule on boot, time, timezone, package replaced).
- **Remove what the new flow makes dead.** Drop the notification `Drank`/`Skip` actions and `DrinkActionReceiver` (the owner wants the app opened). Drop the pass-through `WaterRepository` and the unused `ReminderScheduler.cancel()`.

## Visual direction (Claude's call, delegated by the owner: "non AI-sloppy")

The reference is **the owner's own sketch**: hand-drawn glass, bowl, bell, and flame with wobbly water lines. The app should feel like that notebook page brought to life, not like a template.

- **Illustrations are drawn, not picked.** Glass, bowl, bell, flame, and the gender glyphs are custom vector shapes (Compose `Canvas` / `Path`, or hand-authored vector drawables), with slightly irregular strokes like the sketch. **No emoji. No stock Material icons** except where a system affordance genuinely requires one.
- **Water is the hero.** The water surface is a real wave path. On a change it sloshes and **settles to still**: a one-shot spring, never a loop. The fill height animates with the same spring.
- **Palette: OLED-black base** (true `#000` background, which also saves battery on AMOLED because black pixels are off), **one water colour** (a clear, slightly cyan blue) used sparingly, **warm ember** only for streak/flame, muted red for skip, and a fresh green for done. No gradients except inside the water body.
- **Type: one bundled, characterful typeface** with a heavy display weight for numbers and the big words ("Drink up!", "Hell yeah!", "All set!"), and a regular weight for small text. Must be OFL/free to bundle. The builder picks it, avoiding the generic defaults (Roboto, Inter, Poppins, Montserrat).
- **Copy keeps the owner's voice**: short, casual, a bit cheeky ("Hell yeah!", "Drink up!", "All done!"). No filler sentences.
- **Layout respects the circle.** Controls hug the round edge (the swipe tracks, curved text where it helps). Nothing is crammed into the square-inscribed area.
- **Motion budget:** every animation is one-shot and short (≤ ~1.2 s), triggered by the user or a reminder. Nothing animates while the user isn't looking. No ambient mode.

## Battery rules (hard)

- One alarm scheduled at a time. No services, no WorkManager polling, no wake locks, no network, no sensors.
- Animations only while the screen is interactive; all one-shot.
- No always-on / ambient support.

## Left to the builder

- Navigation mechanism (the existing enum-based screen state is fine; adopting a Wear navigation library is optional).
- Whether to stay on `wear.compose.material` or move to `wear.compose.material3` (the visuals are custom either way).
- Exact typeface, exact colour values, exact easing and spring constants, within the direction above.
- How to structure persisted state (extend the existing DataStore preferences).
- Picker implementation details (bezel/rotary support is required, implementation is free).

## Verification

- One small runnable check (plain JUnit) covering the pure logic: reminder-time calculation, day rollover + streak update, the skip → "two glasses" rule, and the auto-goal lookup.
- On-device check on the watch for each screen and transition, notification sound + vibration, the swipe thresholds, and an alarm firing at the scheduled minute (confirmed via `adb shell dumpsys alarm` and a short test window).
