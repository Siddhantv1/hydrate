# Hydrate

A personal Wear OS water reminder (Kotlin, Jetpack Compose for Wear OS), drawn to look like the owner's notebook sketch. Built for a Galaxy Watch 4 (Wear OS, API 33).

## What it does

1. First launch: notification permission, age, gender, daily goal (suggested from age and gender, or set by hand), active hours.
2. Home: a hand-drawn glass that fills toward the goal (`3/8`); **+** logs a glass, the right button undoes one, **⋯** opens Change goal / Reset timer / Ignore for today.
3. Through the active hours one exact alarm at a time fires "Drink up!": drag ✓ up the edge for "Hell yeah!", or − to skip, which makes the next reminder two glasses.
4. An unanswered reminder counts as a skip; any interaction restarts the interval.
5. Meeting the goal shows "All done!" and grows the streak; a missed day resets it, which the app shows the next time it opens. Reminders resume at tomorrow's start.

## Build and install (CLI)

A phone may also be connected to ADB, so always target the watch by serial (`adb devices -l` lists it).

Install the **release** build on the watch: the debug build is interpreted and noticeably choppy (4 s cold start vs under 1 s). The release build is signed with the debug key, so it installs over a debug build and keeps your data.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
adb -s <watch-serial> install -r app\build\outputs\apk\release\app-release.apk
# Optional: compile now instead of waiting for the watch's idle-time compile (uses the bundled startup profile)
adb -s <watch-serial> shell cmd package compile -m speed-profile -f com.example.waterreminder
```

## Battery (Samsung)

- Settings > Apps > Hydrate > Battery: set battery use to **Unrestricted**, otherwise Samsung's freezer can hold back reminders.
- In the same app settings, turn off **Pause app activity if unused**.
- Turn off **Wireless debugging** on the watch when you're not developing; it keeps the radio up and drains the battery.

## Docs

- Design spec: `docs/superpowers/specs/2026-11-03-hydrate-ui-design.md` (the owner's sketch is next to it: `2026-11-03-hydrate-sketch.jpg`).
- Implementation plan: `docs/superpowers/plans/2026-11-03-hydrate-ui.md`.
