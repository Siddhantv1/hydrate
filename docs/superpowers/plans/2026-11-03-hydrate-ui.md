# Hydrate UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the plain chip-list app with the owner's sketched flow (onboarding, glass home screen, alarm-style Drink up!, streaks), give it a hand-drawn non-generic look, and make reminders exact, loud and buzzy.

**Architecture:** All behaviour lives in pure Kotlin functions over one immutable `HydrateState` (`domain/`), unit-tested on the JVM. One DataStore-backed `HydrateStore` persists it and applies day rollover inside every atomic `update`. A ViewModel owns navigation (`Screen` enum) and calls `store.update { rule(it, now) }` and then `ReminderScheduler.arm(after)`. Screens are stateless composables drawn with a small custom kit (theme, sketch shapes, water glass, arc swipe).

**Tech Stack:** Kotlin 2.3.21, AGP 8.13.0, Gradle 9.4.1 wrapper, Compose BOM 2026.05.00, Wear Compose 1.6.2 (foundation + material), DataStore Preferences 1.2.1, JUnit 4.13.2, AlarmManager exact alarms.

**Spec:** `docs/superpowers/specs/2026-11-03-hydrate-ui-design.md` (visual reference: `docs/superpowers/specs/2026-11-03-hydrate-sketch.jpg`)

## Global Constraints

- Device: Galaxy Watch 4 SM-R870, Android 13 / API 33, 450×450 round AMOLED, touch bezel emits rotary events. `minSdk` stays 26, `compileSdk`/`targetSdk` stay 36.
- Build: `.\gradlew.bat :app:assembleDebug`. Install: `adb -s $w install -r app\build\outputs\apk\debug\app-debug.apk`, where `$w` is the watch serial from `adb devices -l` (model `SM_R870`; currently `adb-RFAT62AKBJE-dKrvj7._adb-tls-connect._tcp`). A phone is also attached; never install without `-s`.
- Tests: `.\gradlew.bat :app:testDebugUnitTest`.
- No haptics anywhere in the app UI. The watch vibrates only when the reminder notification arrives (owner).
- Battery: one alarm at a time; no services, WorkManager, wake locks, network or sensors; every animation is one-shot and ≤ ~1.2 s; no ambient mode.
- Visual: true black `#000000` background. No emoji. No stock Material icons. No gradients except inside the water. One bundled typeface (Bricolage Grotesque, OFL). Illustrations are custom `Path`s with seeded hand-drawn wobble, matching the sketch.
- Copy (exact): "Drink up!", "Hell yeah!", "All set!", "All done!", "Make it two glasses next time", "Goals met", "You didn't meet your goals", menu items "Change goal", "Reset timer", "Ignore for today". Notification title "Drink up!".
- Swipe threshold: 65 % of a 90° edge track. System swipe-to-dismiss is disabled on Drink up! only.
- Glass = 250 ml (ML and L units) or 237 ml / 8 fl oz (OZ). The goal is stored as a whole number of glasses. *(Plan's call: the spec says goal stored in ml. This is equivalent because the goal picker steps in whole glasses.)*
- Package/namespace stays `com.example.waterreminder`. The DataStore file is the new `hydrate` (old `water_preferences` data is abandoned; first launch shows onboarding).
- Work on branch `hydrate-ui`, not `main`.

## Review Focus

1. **Undo after the goal was met:** the streak +1 must be reverted and reminders resume. → test `undoAfterGoalMetRevertsStreak` (Task 1).
2. **Watch off/rebooted past a scheduled reminder:** a stored `nextReminderAt` in the past must be replaced with a future time, not lost. → test `planNextReplacesPastReminder` (Task 1).
3. **A whole day with no app/alarm activity** (watch dead): the streak must still reset; onboarding day must not count as missed. → tests `rolloverAfterGapDayResetsStreak`, `rolloverNeverMissesBeforeFirstDay` (Task 1).
4. **Goal lowered mid-day below today's count:** counts as met, streak +1 exactly once. → test `saveGoalBelowTodayCountMeetsGoal` (Task 1).
5. **Stale alarm** firing after the goal is met, after the end time, or on an ignored day: no notification, re-armed for tomorrow's start. → test `staleReminderDoesNotNotify` (Task 1).

---

### Task 1: Domain state and rules

**Files:**
- Create: `app/src/main/java/com/example/waterreminder/domain/HydrateState.kt`
- Create: `app/src/main/java/com/example/waterreminder/domain/Rules.kt`
- Modify: `app/build.gradle.kts` (add `testImplementation("junit:junit:4.13.2")`)
- Test: `app/src/test/java/com/example/waterreminder/domain/RulesTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class Gender { MALE, FEMALE }
  enum class VolumeUnit { ML, L, OZ }
  data class HydrateState(
      val onboarded: Boolean = false,
      val age: Int = 25,
      val gender: Gender = Gender.MALE,
      val unit: VolumeUnit = VolumeUnit.ML,
      val autoGoal: Boolean = true,
      val goalGlasses: Int = 8,
      val startMinute: Int = 8 * 60,
      val endMinute: Int = 22 * 60,
      val glassesToday: Int = 0,
      val date: String = "",          // ISO LocalDate that glassesToday belongs to
      val streak: Int = 0,
      val goalMetDate: String = "",   // date the streak was last credited
      val ignoredDate: String = "",
      val nextReminderAt: Long = 0L,  // epoch millis, 0 = none
      val outstanding: Boolean = false,
      val doubleNext: Boolean = false,
      val showMissedGoal: Boolean = false,
  )
  ```
  Top-level functions in `Rules.kt` (all pure; all action functions assume `rollover` was already applied, which `HydrateStore.update` guarantees):
  `glassMl(unit): Int` · `formatGlasses(glasses: Int, unit): String` · `suggestedGoalGlasses(age: Int, gender: Gender, unit: VolumeUnit): Int` · `intervalMinutes(s): Long` · `goalMet(s): Boolean` · `canRemind(s, now: ZonedDateTime): Boolean` · `rollover(s, today: LocalDate): HydrateState` · `settleGoal(s, today: LocalDate): HydrateState` · `planNext(s, now: ZonedDateTime, from: ZonedDateTime?): HydrateState` · `addGlasses(s, n: Int, now)` · `undoGlass(s, now)` · `drank(s, now)` · `skip(s, now)` · `reminderFired(s, now)` · `resetTimer(s, now)` · `ignoreToday(s, now)` · `completeOnboarding(s, age, gender, unit, autoGoal, goalGlasses, startMinute, endMinute, now)` · `saveGoal(s, unit, autoGoal, goalGlasses, now)` · `saveHours(s, startMinute, endMinute, now)` · `dismissMissedGoal(s)` · `reminderBody(before: HydrateState, after: HydrateState): String`. Every action returns `HydrateState`.

- [ ] **Step 1: Create branch**

Run: `git checkout -b hydrate-ui`. Expected: `Switched to a new branch 'hydrate-ui'`.

- [ ] **Step 2: Write the failing tests**

```kotlin
package com.example.waterreminder.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(h: Int, m: Int, day: Int = 3) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone)
    private fun ms(h: Int, m: Int, day: Int = 3) = at(h, m, day).toInstant().toEpochMilli()
    private val d3 = LocalDate.of(2026, 11, 3)
    // 8:00–22:00 = 840 min / 8 glasses = 105 min interval
    private val base = HydrateState(onboarded = true, goalGlasses = 8, startMinute = 480, endMinute = 1320, date = "2026-11-03")

    @Test fun suggestedGoal() {
        assertEquals(10, suggestedGoalGlasses(25, Gender.MALE, VolumeUnit.ML))   // 2500 ml
        assertEquals(8, suggestedGoalGlasses(25, Gender.FEMALE, VolumeUnit.ML))  // 2000 ml
        assertEquals(9, suggestedGoalGlasses(11, Gender.MALE, VolumeUnit.ML))    // 2100 ml
        assertEquals(8, suggestedGoalGlasses(11, Gender.FEMALE, VolumeUnit.ML))  // 1900 ml
        assertEquals(7, suggestedGoalGlasses(6, Gender.FEMALE, VolumeUnit.ML))   // 1600 ml
        assertEquals(11, suggestedGoalGlasses(25, Gender.MALE, VolumeUnit.OZ))   // 2500 / 237
    }

    @Test fun formatsGoal() {
        assertEquals("2000 ml", formatGlasses(8, VolumeUnit.ML))
        assertEquals("2.0 L", formatGlasses(8, VolumeUnit.L))
        assertEquals("2.25 L", formatGlasses(9, VolumeUnit.L))
        assertEquals("64 oz", formatGlasses(8, VolumeUnit.OZ))
    }

    @Test fun interval() {
        assertEquals(105L, intervalMinutes(base))
        assertEquals(15L, intervalMinutes(base.copy(goalGlasses = 100)))
    }

    @Test fun planNextFromInteraction() =
        assertEquals(ms(10, 45), planNext(base, at(9, 0), at(9, 0)).nextReminderAt)

    @Test fun planNextKeepsValidFuture() {
        val s = base.copy(nextReminderAt = ms(11, 0))
        assertEquals(ms(11, 0), planNext(s, at(9, 0), null).nextReminderAt)
    }

    @Test fun planNextReplacesPastReminder() {
        val s = base.copy(nextReminderAt = ms(7, 0))
        assertEquals(ms(10, 45), planNext(s, at(9, 0), null).nextReminderAt)
    }

    @Test fun planNextClampsIntoWindow() {
        assertEquals(ms(8, 0, day = 4), planNext(base, at(21, 30), at(21, 30)).nextReminderAt)
        assertEquals(ms(8, 0), planNext(base, at(6, 0), null).nextReminderAt)
    }

    @Test fun goalMetMeansTomorrow() =
        assertEquals(ms(8, 0, day = 4), planNext(base.copy(glassesToday = 8), at(12, 0), at(12, 0)).nextReminderAt)

    @Test fun ignoreToday() {
        val s = ignoreToday(base.copy(outstanding = true), at(12, 0))
        assertEquals("2026-11-03", s.ignoredDate)
        assertFalse(s.outstanding)
        assertEquals(ms(8, 0, day = 4), s.nextReminderAt)
        assertFalse(canRemind(s, at(13, 0)))
    }

    @Test fun drankLogsOneAndPushesReminder() {
        val s = drank(base.copy(glassesToday = 2, outstanding = true), at(10, 0))
        assertEquals(3, s.glassesToday)
        assertFalse(s.outstanding)
        assertEquals(ms(11, 45), s.nextReminderAt)
    }

    @Test fun skipThenDrankCountsTwo() {
        val skipped = skip(base.copy(glassesToday = 2, outstanding = true), at(10, 0))
        assertTrue(skipped.doubleNext)
        assertFalse(skipped.outstanding)
        val s = drank(skipped, at(11, 0))
        assertEquals(4, s.glassesToday)
        assertFalse(s.doubleNext)
    }

    @Test fun skipTwiceStillTwo() {
        val s = drank(skip(skip(base, at(10, 0)), at(11, 0)), at(12, 0))
        assertEquals(2, s.glassesToday)
    }

    @Test fun missedReminderCountsAsSkip() {
        val before = base.copy(glassesToday = 2, outstanding = true)
        val after = reminderFired(before, at(12, 0))
        assertTrue(after.outstanding)
        assertTrue(after.doubleNext)
        assertEquals("Missed one. Make it two glasses.", reminderBody(before, after))
    }

    @Test fun reminderBodies() {
        val fresh = base.copy(glassesToday = 2)
        assertEquals("Glass 3 of 8", reminderBody(fresh, reminderFired(fresh, at(12, 0))))
        val skipped = base.copy(doubleNext = true)
        assertEquals("Make it two glasses.", reminderBody(skipped, reminderFired(skipped, at(12, 0))))
    }

    @Test fun staleReminderDoesNotNotify() {
        val met = reminderFired(base.copy(glassesToday = 8), at(12, 0))
        assertFalse(met.outstanding)
        assertEquals(ms(8, 0, day = 4), met.nextReminderAt)
        assertFalse(reminderFired(base, at(22, 30)).outstanding)
        assertFalse(reminderFired(base.copy(ignoredDate = "2026-11-03"), at(12, 0)).outstanding)
    }

    @Test fun reachingGoalCreditsStreakOnce() {
        val met = addGlasses(base.copy(glassesToday = 7, streak = 4), 1, at(15, 0))
        assertEquals(5, met.streak)
        assertEquals("2026-11-03", met.goalMetDate)
        assertEquals(5, addGlasses(met, 1, at(16, 0)).streak)
    }

    @Test fun undoAfterGoalMetRevertsStreak() {
        val s = undoGlass(base.copy(glassesToday = 8, streak = 5, goalMetDate = "2026-11-03"), at(15, 0))
        assertEquals(7, s.glassesToday)
        assertEquals(4, s.streak)
        assertEquals("", s.goalMetDate)
        assertEquals(ms(16, 45), s.nextReminderAt)
    }

    @Test fun saveGoalBelowTodayCountMeetsGoal() {
        val s = saveGoal(base.copy(glassesToday = 6, streak = 2), VolumeUnit.ML, false, 6, at(15, 0))
        assertEquals(3, s.streak)
        assertEquals(3, saveGoal(s, VolumeUnit.ML, false, 6, at(15, 5)).streak)
    }

    @Test fun rolloverAfterMetDayKeepsStreak() {
        val s = rollover(base.copy(glassesToday = 8, streak = 5, goalMetDate = "2026-11-03"), d3.plusDays(1))
        assertEquals(5, s.streak); assertEquals(0, s.glassesToday)
        assertEquals("2026-11-04", s.date); assertFalse(s.showMissedGoal)
    }

    @Test fun rolloverAfterMissedDayResetsStreak() {
        val s = rollover(base.copy(glassesToday = 3, streak = 5, goalMetDate = "2026-11-02", outstanding = true, doubleNext = true), d3.plusDays(1))
        assertEquals(0, s.streak); assertTrue(s.showMissedGoal)
        assertFalse(s.outstanding); assertFalse(s.doubleNext)
    }

    @Test fun rolloverAfterGapDayResetsStreak() {
        val s = rollover(base.copy(glassesToday = 8, streak = 5, goalMetDate = "2026-11-03"), d3.plusDays(2))
        assertEquals(0, s.streak); assertTrue(s.showMissedGoal)
    }

    @Test fun rolloverNeverMissesBeforeFirstDay() {
        assertFalse(rollover(HydrateState(), d3).showMissedGoal)
        assertEquals(base, rollover(base, d3))
    }

    @Test fun onboardingStartsToday() {
        val s = completeOnboarding(HydrateState(), 25, Gender.FEMALE, VolumeUnit.ML, true, 8, 480, 1320, at(9, 0))
        assertTrue(s.onboarded); assertEquals("2026-11-03", s.date)
        assertEquals(ms(10, 45), s.nextReminderAt)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.waterreminder.domain.RulesTest"`
Expected: compilation FAIL (unresolved `HydrateState`, `planNext`, …).

- [ ] **Step 4: Implement `HydrateState.kt` and `Rules.kt`**

Values and rules the tests don't fully pin:
- `suggestedGoalGlasses`: ml table (age ≤ 8 → 1600; 9–13 → M 2100 / F 1900; ≥ 14 → M 2500 / F 2000), then `ceil(ml / glassMl(unit))`. Add a comment that this is an EFSA adequate-intake starting value, not medical advice.
- `formatGlasses`: ML → `"${g*250} ml"`. L → litres with 2 decimals, trailing zeros trimmed but at least one decimal. OZ → `"${g*8} oz"`.
- `canRemind` = onboarded && end > start && start ≤ minuteOfDay < end && !goalMet && ignoredDate != today.
- `rollover(s, today)`: no-op if `s.date == today`. Missed = `s.onboarded && s.date != "" && (s.goalMetDate != s.date || today > LocalDate.parse(s.date).plusDays(1))`. Then reset glassesToday/outstanding/doubleNext, set date, `streak = 0` if missed, and `showMissedGoal ||= missed`.
- `settleGoal`: met && goalMetDate != today → streak+1, goalMetDate = today. !met && goalMetDate == today → streak−1 (≥ 0), goalMetDate = "".
- `planNext(s, now, from)`:
  ```
  !onboarded || end <= start           -> nextReminderAt = 0
  goalMet || ignoredDate == today      -> tomorrow at startMinute
  from != null                         -> clamp(from + interval)
  s.nextReminderAt > now               -> unchanged
  else                                 -> clamp(now + interval)
  clamp(t): t before today's start -> today's start; t at/after today's end -> tomorrow's start
  ```
- `addGlasses` = settleGoal(glassesToday += n, outstanding = false), then planNext(from = now). `undoGlass` = settleGoal(glassesToday − 1, ≥ 0), then planNext(from = now). `drank` = addGlasses(doubleNext = false, n = if doubleNext 2 else 1). `skip` = doubleNext = true, outstanding = false, planNext(from = now). `reminderFired`: if canRemind → doubleNext ||= outstanding, outstanding = true, planNext(from = now); else planNext(from = null). `resetTimer` = planNext(from = now). `ignoreToday` = ignoredDate = today, outstanding = false, planNext(from = null). `completeOnboarding` sets the fields, onboarded = true, date = today, glassesToday = 0, then planNext(from = null). `saveGoal` → settleGoal then planNext(from = now). `saveHours` → planNext(from = now).
- `reminderBody(before, after)`: before.outstanding → "Missed one. Make it two glasses."; after.doubleNext → "Make it two glasses."; else `"Glass ${after.glassesToday + 1} of ${after.goalGlasses}"`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.waterreminder.domain.RulesTest"`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/example/waterreminder/domain app/src/test
git commit -m "feat: pure hydration rules with streaks, skips and relative scheduling"
```

---

### Task 2: Persistent store

**Files:**
- Create: `app/src/main/java/com/example/waterreminder/data/HydrateStore.kt`
- Test: `app/src/test/java/com/example/waterreminder/data/HydrateStoreTest.kt`

**Interfaces:**
- Consumes: `HydrateState`, `rollover` (Task 1).
- Produces:
  ```kotlin
  class HydrateStore(private val dataStore: DataStore<Preferences>, private val clock: java.time.Clock = Clock.systemDefaultZone()) {
      val state: Flow<HydrateState>   // persisted state passed through rollover(today); does not write
      suspend fun update(transform: (HydrateState) -> HydrateState): Pair<HydrateState, HydrateState> // (rolledBefore, after); persists after atomically
      companion object { fun get(context: Context): HydrateStore }  // process singleton over preferencesDataStore("hydrate")
  }
  ```

- [ ] **Step 1: Write the failing tests**

```kotlin
class HydrateStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun clockOn(day: Int) = Clock.fixed(ZonedDateTime.of(2026, 11, day, 9, 0, 0, 0, zone).toInstant(), zone)
    // One DataStore per file per test: DataStore forbids two live instances on the same file.
    private fun dataStore(scope: CoroutineScope) =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "t.preferences_pb") })

    @Test fun roundTripsEveryField() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val ds = dataStore(scope)
        val full = HydrateState(true, 31, Gender.FEMALE, VolumeUnit.OZ, false, 11, 420, 1260, 4, "2026-11-03",
            6, "2026-11-02", "2026-11-03", 1793700000000L, true, true, true)
        HydrateStore(ds, clockOn(3)).update { full }
        assertEquals(full, HydrateStore(ds, clockOn(3)).state.first())
        scope.cancel()
    }

    @Test fun updateAppliesRolloverFirst() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val s = HydrateStore(dataStore(scope), clockOn(4))
        s.update { HydrateState(onboarded = true, glassesToday = 5, date = "2026-11-03", goalMetDate = "2026-11-03", streak = 2) }
        val (before, after) = s.update { it }
        assertEquals("2026-11-04", before.date); assertEquals(0, after.glassesToday); assertEquals(2, after.streak)
        scope.cancel()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.waterreminder.data.HydrateStoreTest"`. Expected: compilation FAIL.

- [ ] **Step 3: Implement `HydrateStore`**

One preferences key per field (enums stored by `name`). Inside `dataStore.edit { }`: read the state, apply `rollover(LocalDate.now(clock))`, apply `transform`, write every field. `get(context)` uses `Context.preferencesDataStore(name = "hydrate")` on the application context.

- [ ] **Step 4: Run tests to verify they pass**

Run the same command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/waterreminder/data/HydrateStore.kt app/src/test/java/com/example/waterreminder/data
git commit -m "feat: DataStore-backed HydrateStore with rollover on every update"
```

---

### Task 3: Cut over: navigation, ViewModel, exact reminders (placeholder screens)

**Files:**
- Create: `app/src/main/java/com/example/waterreminder/ui/Screen.kt`
- Create: `app/src/main/java/com/example/waterreminder/ui/HydrateViewModel.kt`
- Create: `app/src/main/java/com/example/waterreminder/ui/HydrateApp.kt`
- Create: `app/src/main/java/com/example/waterreminder/reminder/ReminderReceiver.kt`
- Rewrite: `reminder/ReminderScheduler.kt`, `reminder/BootReceiver.kt`, `MainActivity.kt`
- Rename and rewrite: `reminder/WaterNotification.kt` → `reminder/ReminderNotification.kt`
- Delete: `data/WaterDefaults.kt`, `data/WaterPreferences.kt`, `data/WaterRepository.kt`, `data/WaterState.kt`, `reminder/DrinkActionReceiver.kt`, `reminder/ReminderNotificationReceiver.kt`, `ui/WaterReminderApp.kt`, `ui/WaterReminderViewModel.kt`
- Modify: `AndroidManifest.xml`, `res/values/themes.xml`, `res/values/strings.xml`, `ui/theme/Theme.kt` (rename `WaterReminderTheme` → `HydrateTheme`; Task 4 rewrites its body)
- Test: `app/src/test/java/com/example/waterreminder/ui/ScreenTest.kt`

**Interfaces:**
- Consumes: Task 1 rules, Task 2 `HydrateStore`.
- Produces:
  ```kotlin
  enum class Screen { Age, Gender, Goal, Hours, AllSet, Home, Menu, EditGoal, EditHours, DrinkUp, HellYeah, SkipNote, AllDone, StreakUp, StreakLost }
  fun startScreen(s: HydrateState): Screen
  fun backOf(screen: Screen): Screen?   // null = leave the app
  data class Draft(val age: Int, val gender: Gender, val unit: VolumeUnit, val autoGoal: Boolean, val goalGlasses: Int, val startMinute: Int, val endMinute: Int)
  data class Logged(val from: Int, val to: Int, val total: Int)

  class HydrateViewModel(app: Application) : AndroidViewModel(app) {
      val state: StateFlow<HydrateState?>   // null while loading
      val screen: StateFlow<Screen?>        // null while loading
      val lastLog: StateFlow<Logged?>       // drives the glass-rise animation
      var draft: Draft                      // Compose mutableState; seeded from state for EditGoal/EditHours
      fun go(to: Screen); fun back(): Boolean
      fun onForeground(force: Boolean)      // planNext(from=null) + arm; if force or screen in {null, Home} → startScreen(state)
      fun finishOnboarding()                // → AllSet
      fun addGlass()                        // stays Home, or → AllDone if the goal was just met
      fun undoGlass(); fun drank()          // drank → HellYeah, or AllDone if the goal was just met
      fun skip()                            // → SkipNote
      fun resetTimer(); fun ignoreToday()   // → Home
      fun saveGoal()                        // → EditHours
      fun saveHours()                       // → Home
      fun dismissMissedGoal()               // → startScreen(state)
  }
  object ReminderScheduler { fun arm(context: Context, s: HydrateState) }
  object ReminderNotification { const val CHANNEL_ID = "reminders_v2"; fun ensureChannel(context: Context); fun show(context: Context, body: String); fun cancel(context: Context) }
  ```
  "Goal just met" = `after.goalMetDate == today && before.goalMetDate != today`.

- [ ] **Step 1: Write the failing test**

```kotlin
class ScreenTest {
    private val on = HydrateState(onboarded = true)
    @Test fun start() {
        assertEquals(Screen.Age, startScreen(HydrateState()))
        assertEquals(Screen.StreakLost, startScreen(on.copy(showMissedGoal = true, outstanding = true)))
        assertEquals(Screen.DrinkUp, startScreen(on.copy(outstanding = true)))
        assertEquals(Screen.Home, startScreen(on))
    }
    @Test fun back() {
        assertEquals(Screen.Home, backOf(Screen.DrinkUp))
        assertEquals(Screen.EditGoal, backOf(Screen.EditHours))
        assertEquals(Screen.Age, backOf(Screen.Gender))
        assertEquals(null, backOf(Screen.Home))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.waterreminder.ui.ScreenTest"`. Expected: compilation FAIL.

- [ ] **Step 3: Implement `Screen.kt`**

`backOf`: Gender→Age, Goal→Gender, Hours→Goal, Menu→Home, EditGoal→Menu, EditHours→EditGoal, DrinkUp→Home. Everything else → null.

- [ ] **Step 4: Implement the reminder pipeline**

- `ReminderScheduler.arm`: cancel the PendingIntent (action `com.example.waterreminder.action.REMIND`, request code 4100, `FLAG_IMMUTABLE`). If `nextReminderAt > 0`, call `setExactAndAllowWhileIdle(RTC_WAKEUP, …)`. Fall back to `setAndAllowWhileIdle` only when API ≥ 31 and `!canScheduleExactAlarms()`.
- `ReminderNotification`: `ensureChannel` deletes the old `water_reminders` channel and creates `reminders_v2` with `IMPORTANCE_HIGH`, the default notification sound, vibration on, pattern `longArrayOf(0, 250, 120, 250)`. `show`: small icon `ic_stat_water`, title "Drink up!", the body text, `CATEGORY_REMINDER`, `PRIORITY_HIGH`, auto-cancel, **no actions**. Tapping opens `MainActivity` with `FLAG_ACTIVITY_SINGLE_TOP or FLAG_ACTIVITY_CLEAR_TOP`. Return silently if `POST_NOTIFICATIONS` is not granted.
- `ReminderReceiver` (action REMIND, `goAsync` + IO coroutine, same pattern as the old receivers): `(before, after) = store.update { reminderFired(it, now) }`. If `after.outstanding && canRemind(before, now)`, call `show(reminderBody(before, after))`. Then `arm(after)`.
- `BootReceiver`: keep the same four actions; `store.update { planNext(it, now, null) }` then `arm`.

- [ ] **Step 5: Implement the ViewModel, `HydrateApp` and `MainActivity`**

- Each VM action: `store.update { rule(it, ZonedDateTime.now()) }` → `ReminderScheduler.arm(app, after)`. Call `ReminderNotification.cancel(app)` for drank / skip / addGlass / ignoreToday. Then navigate as the Interfaces block says.
- `HydrateApp(vm)`: wrap content in `androidx.wear.compose.foundation.BasicSwipeToDismissBox` with `userSwipeEnabled = screen != Screen.DrinkUp && backOf(screen) != null`. Dismissing calls `vm.back()`. Add `BackHandler` for the hardware back button, which calls `vm.back()` and finishes the activity when `back()` returns false. The body is a `when (screen)` over **every** `Screen` value. In this task each branch is a temporary `PlaceholderScreen(name: String, actions: List<Pair<String, () -> Unit>>)`: the screen name plus tappable text rows that call the matching VM functions with `draft` defaults. Tasks 4–7 replace each branch. Task 7 deletes `PlaceholderScreen`.
- `MainActivity`: keep the notification permission request; `setContent { HydrateTheme { HydrateApp(vm) } }`; `onResume` → `vm.onForeground(false)`; `onNewIntent` → `vm.onForeground(true)`.
- Manifest: add `USE_EXACT_ALARM`, plus `SCHEDULE_EXACT_ALARM` with `android:maxSdkVersion="32"`. Register `ReminderReceiver` (not exported). Remove the two deleted receivers. Set `android:launchMode="singleTop"` on `MainActivity`. Change `app_name` to "Hydrate". Theme: add `<item name="android:windowSwipeToDismiss">false</item>`.

- [ ] **Step 6: Run all unit tests and build**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`. Expected: `BUILD SUCCESSFUL`, no `e:` lines.

- [ ] **Step 7: Verify on the watch**

Install. Tap through the placeholder onboarding to Home. Run `adb -s $w shell "dumpsys alarm | grep -A3 waterreminder"`. Expected: one `RTC_WAKEUP` alarm, `tag=*walarm*:com.example.waterreminder.action.REMIND`, `window=0`, `origWhen` = now + `intervalMinutes` for the draft defaults. Check `adb -s $w logcat -d -b crash` is empty.

- [ ] **Step 8: Commit**

```bash
git add -A app/src
git commit -m "feat: cut over to HydrateStore, exact alarms and screen navigation"
```

---

### Task 4: Look-and-feel kit and onboarding screens

**Files:**
- Create: `app/src/main/res/font/bricolage.ttf`
- Rewrite: `app/src/main/java/com/example/waterreminder/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/example/waterreminder/ui/draw/Sketch.kt`
- Create: `app/src/main/java/com/example/waterreminder/ui/draw/WaterGlass.kt`
- Create: `app/src/main/java/com/example/waterreminder/ui/screens/Onboarding.kt`
- Modify: `ui/HydrateApp.kt` (replace the Age, Gender, Goal, Hours and AllSet placeholders)

**Interfaces:**
- Consumes: `HydrateViewModel.draft`, `finishOnboarding()`, `go()`; `suggestedGoalGlasses`, `formatGlasses`.
- Produces (used by Tasks 5–7):
  ```kotlin
  object Ink { val Bg = Color(0xFF000000); val Paper = Color(0xFFF2EEE6); val Muted = Color(0xFF8F8A80); val Water = Color(0xFF49C6F2); val WaterDeep = Color(0xFF1580C4); val Ember = Color(0xFFFF8A3D); val Skip = Color(0xFFE8696B); val Done = Color(0xFF7BE495); val Track = Color(0xFF262626) }
  val Bricolage: FontFamily
  fun display(size: TextUnit): TextStyle   // Bricolage ExtraBold (wght 800), Paper
  fun body(size: TextUnit): TextStyle      // Bricolage Regular (wght 400), Paper
  @Composable fun HydrateTheme(content: @Composable () -> Unit)
  fun DrawScope.sketchStroke(points: List<Offset>, seed: Int, color: Color = Ink.Paper, closed: Boolean = false)  // seeded wobble ≤ 1.5dp, 2.5dp round stroke
  @Composable fun SketchBowl(level: Float, modifier: Modifier = Modifier)
  @Composable fun SketchBell(modifier: Modifier = Modifier)               // swings once on first composition
  @Composable fun SketchFlame(lit: Boolean, modifier: Modifier = Modifier)
  @Composable fun GenderGlyph(gender: Gender, modifier: Modifier = Modifier)
  @Composable fun WaterGlass(glasses: Int, total: Int, modifier: Modifier = Modifier, fromGlasses: Int = glasses)  // tapered glass, count label inside
  @Composable fun GoalPicker(draft: Draft, onChange: (Draft) -> Unit, onConfirm: () -> Unit)  // reused by EditGoal
  @Composable fun HoursPicker(draft: Draft, onChange: (Draft) -> Unit, onConfirm: () -> Unit) // reused by EditHours
  ```

- [ ] **Step 1: Fetch the font**

Run: `Invoke-WebRequest "https://github.com/google/fonts/raw/main/ofl/bricolagegrotesque/BricolageGrotesque%5Bopsz,wdth,wght%5D.ttf" -OutFile app\src\main\res\font\bricolage.ttf`. Expected: a file over 100 KB. If GitHub is unreachable, use the same family from `fonts.google.com`. Do not substitute Roboto, Inter, Poppins or Montserrat.

- [ ] **Step 2: Build the kit**

- `Theme.kt`: the `Ink`, `Bricolage` (variable-font `Font(R.font.bricolage, weight, variationSettings = FontVariation.Settings(FontVariation.weight(n)))` at 400 and 800), `display`, `body` and `HydrateTheme` values above. Delete the old `WaterReminderTheme`.
- `Sketch.kt`: `sketchStroke` jitters each point by a seeded `Random(seed)` so a shape looks identical on every frame. Shapes follow the sketch: bowl = wide shallow arc with a wavy water line; bell = dome plus clapper; flame = three-lobed teardrop (filled `Ember` when lit, outline at 40 % alpha when not); gender glyphs = circle plus arrow / circle plus cross.
- `WaterGlass.kt`: tapered glass outline drawn with `sketchStroke`. Water body uses a vertical `Water`→`WaterDeep` gradient (the only gradient allowed). The surface is a sine path. When `glasses` changes, the level animates `fromGlasses/total → glasses/total` with `spring(dampingRatio = 0.55f, stiffness = 120f)`. Slosh amplitude jumps to about 6dp and decays to 0 with the same spring while the phase advances one-shot over ~1.2 s, then everything is still. The label `"$glasses/$total"` uses `display(22.sp)`.

- [ ] **Step 3: Build the onboarding screens**

Layouts follow sketches #1–#2 in `2026-11-03-hydrate-sketch.jpg`:
- **Age**: "Your age" title, a vertical wheel 8–100 with the selected value boxed (wear `Picker` or equivalent), and a bezel/rotary scroll that takes focus on entry. Tapping the boxed value goes to Gender.
- **Gender**: "Your gender" title and two large circular `GenderGlyph` buttons. Tapping one sets the draft and goes to Goal.
- **Goal** (`GoalPicker`): three unit pills (ml / L / oz), a vertical picker of 4–20 glasses showing `formatGlasses`, a "Set goal automatically" tick (on = `suggestedGoalGlasses(draft.age, draft.gender, draft.unit)`; moving the picker turns it off), and a "Confirm" pill.
- **Hours** (`HoursPicker`): "Remind me" with start and end pickers in 15-minute steps (`h:mm a`). Confirm is disabled unless end > start.
- **AllSet**: "All set!" in `display(30.sp)`, `SketchBowl(level = 1f)`. Calls `finishOnboarding()` on entry and goes to Home after 1.5 s.

- [ ] **Step 4: Build, install, verify on watch**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`, then clear the app data and install: `adb -s $w shell pm clear com.example.waterreminder`, then `install -r`. On the watch: walk the onboarding. The bezel scrolls the age wheel. Choosing female at age 25 suggests "2000 ml"; switching to oz shows "72 oz" (9 glasses × 8 oz). End ≤ start disables Confirm.
**Reminder fire check:** onboard with goal 20 glasses and hours starting at the current quarter-hour. That gives a 15-minute interval: (end − start) / 20 ≤ 15. Wait for the alarm. Expected: notification "Drink up!" with sound and vibration, arriving at the minute shown by `dumpsys alarm`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/font app/src/main/java/com/example/waterreminder/ui
git commit -m "feat: hand-drawn visual kit and onboarding screens"
```

---

### Task 5: Home screen, menu, edit goal/hours

**Files:**
- Create: `app/src/main/java/com/example/waterreminder/ui/screens/Home.kt`
- Modify: `ui/HydrateApp.kt` (replace the Home, Menu, EditGoal and EditHours placeholders)

**Interfaces:**
- Consumes: `WaterGlass`, `Ink`, `display/body`, `GoalPicker`, `HoursPicker` (Task 4); VM `addGlass`, `undoGlass`, `resetTimer`, `ignoreToday`, `saveGoal`, `saveHours`, `lastLog`, `draft`.

- [ ] **Step 1: Build `HomeScreen(state, lastLog, onAdd, onUndo, onMenu)`**

Layout per the sketch's home screen: `WaterGlass` centred (≈ 45 % of the screen width), a small round ⋯ button left of it, a small round undo button right of it (a hand-drawn curved arrow via `sketchStroke`), and a larger round + button below. Draw the button glyphs with `sketchStroke`, not icons. The streak count with a small `SketchFlame(lit = streak > 0)` sits under + in `body(13.sp)`. Undo is disabled at 0 glasses. + logs and the glass animates from `lastLog.from`.

- [ ] **Step 2: Build `MenuScreen(onChangeGoal, onResetTimer, onIgnoreToday)`**

Three text rows in `display(18.sp)` separated by hand-drawn lines, vertically centred, with a scrolling list and rotary support. "Change goal" seeds `draft` from state and goes to EditGoal. EditGoal (`GoalPicker`) confirm → `saveGoal()` → EditHours. EditHours (`HoursPicker`) confirm → `saveHours()` → Home.

- [ ] **Step 3: Build, install, verify on watch**

Build and install as before. Check: + raises the water with one settle and no loop; undo lowers it; Reset timer moves the `dumpsys alarm` `origWhen` to now + interval; Ignore for today moves it to tomorrow's start; Change goal → hours → Home keeps today's count.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/waterreminder/ui
git commit -m "feat: glass home screen and menu"
```

---

### Task 6: Drink up! swipe, Hell yeah!, skip note

**Files:**
- Create: `app/src/main/java/com/example/waterreminder/ui/draw/ArcSwipe.kt`
- Create: `app/src/main/java/com/example/waterreminder/ui/screens/Reminder.kt`
- Modify: `ui/HydrateApp.kt` (replace the DrinkUp, HellYeah and SkipNote placeholders)
- Test: `app/src/test/java/com/example/waterreminder/ui/draw/ArcSwipeTest.kt`

**Interfaces:**
- Consumes: kit (Task 4); VM `drank`, `skip`, `lastLog`, `state.doubleNext`.
- Produces:
  ```kotlin
  enum class ArcSide { LEFT, RIGHT }   // LEFT: start 225°, sweeps clockwise; RIGHT: start 135°, sweeps counter-clockwise; angles clockwise from 12 o'clock
  const val SWIPE_SPAN_DEG = 90f
  const val SWIPE_THRESHOLD = 0.65f
  fun arcProgress(angleDeg: Float, side: ArcSide): Float   // 0..1
  @Composable fun ArcSwipeButton(side: ArcSide, color: Color, onTrigger: () -> Unit, glyph: DrawScope.() -> Unit)
  ```

- [ ] **Step 1: Write the failing test**

```kotlin
class ArcSwipeTest {
    private fun p(a: Float, s: ArcSide) = arcProgress(a, s)
    @Test fun left() {
        assertEquals(0f, p(225f, ArcSide.LEFT), 1e-4f); assertEquals(0.5f, p(270f, ArcSide.LEFT), 1e-4f)
        assertEquals(1f, p(315f, ArcSide.LEFT), 1e-4f); assertEquals(0f, p(200f, ArcSide.LEFT), 1e-4f)
        assertEquals(1f, p(10f, ArcSide.LEFT), 1e-4f)   // overshoot past 12 o'clock
    }
    @Test fun right() {
        assertEquals(0f, p(135f, ArcSide.RIGHT), 1e-4f); assertEquals(0.5f, p(90f, ArcSide.RIGHT), 1e-4f)
        assertEquals(1f, p(45f, ArcSide.RIGHT), 1e-4f); assertEquals(0f, p(160f, ArcSide.RIGHT), 1e-4f)
        assertEquals(1f, p(350f, ArcSide.RIGHT), 1e-4f)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.waterreminder.ui.draw.ArcSwipeTest"`. Expected: compilation FAIL.

- [ ] **Step 3: Implement `arcProgress` and `ArcSwipeButton`**

`arcProgress`: `p = (angle − start) × dir / SPAN`, clamped to 0..1. Overshoot past the top wraps: LEFT with angle < 90 → 1, RIGHT with angle > 270 → 1 (this is the validated prototype's math in `.superpowers/brainstorm/*/content/drinkup-drag.html`). `ArcSwipeButton` draws a 10dp `Ink.Track` arc at radius ≈ 0.81 × half-width, with a progress fill in `color`, a tick at `SWIPE_THRESHOLD`, and a 58dp knob following the finger via `pointerInput`/`detectDragGestures`, which consumes the events. On release ≥ threshold it snaps to the end and calls `onTrigger`. Otherwise it springs back to 0 (`spring(dampingRatio = 0.5f)`). **No haptics.**

- [ ] **Step 4: Build the reminder screens**

- **DrinkUp**: `SketchBell` (one swing), "Drink up!" in `display(30.sp)`, and a subline `body(15.sp)` that reads "${volume} · glass N of T", or "Two glasses this time" when `doubleNext`. LEFT `ArcSwipeButton` in `Ink.Skip` with a hand-drawn − calls `vm.skip()`. RIGHT in `Ink.Done` with a hand-drawn ✓ calls `vm.drank()`.
- **HellYeah**: `WaterGlass(to, total, fromGlasses = from)` from `lastLog`, then "Hell yeah!" in `display(28.sp)`. After 2 s → Home.
- **SkipNote**: "Make it two glasses next time" in `display(20.sp)`, and an "OK" pill → Home.

- [ ] **Step 5: Run tests, build, install, verify on watch**

Run all unit tests and assemble, then install. On the watch, trigger a reminder (the 15-minute setup from Task 4). The notification arrives with sound and vibration; tapping it opens Drink up!. A partial drag springs back. Dragging − past the tick shows the skip note, and the next Drink up! says "Two glasses this time". Dragging ✓ shows Hell yeah! with +2. Swiping right on Drink up! does **not** leave the screen. The hardware back button goes to Home, and reopening the app returns to Drink up!. No vibration on any swipe.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/waterreminder/ui app/src/test/java/com/example/waterreminder/ui
git commit -m "feat: alarm-style Drink up! swipe with Hell yeah and skip screens"
```

---

### Task 7: End-of-day screens

**Files:**
- Create: `app/src/main/java/com/example/waterreminder/ui/screens/DayEnd.kt`
- Modify: `ui/HydrateApp.kt` (replace the AllDone, StreakUp and StreakLost placeholders; delete `PlaceholderScreen`)

**Interfaces:**
- Consumes: kit (Task 4); VM `dismissMissedGoal`, `go`, `state.streak`.

- [ ] **Step 1: Build the three screens**

- **AllDone** (sketch #3): "All done!" in `display(30.sp)` with `SketchBowl(level = 1f)`. Its water settles once. After 1.5 s → StreakUp.
- **StreakUp** (sketch #7): "Goals met", a lit `SketchFlame`, the streak number in `display(40.sp)`, and a round ✓ button → Home.
- **StreakLost** (sketch #8): "You didn't meet your goals", an unlit `SketchFlame`, "0", and a round ✓ button → `dismissMissedGoal()`.

- [ ] **Step 2: Build, install, verify on watch**

Confirm `grep -r "PlaceholderScreen" app/src` finds nothing. Build and install. Log glasses up to the goal: AllDone → StreakUp shows streak 1. `dumpsys alarm` shows the next alarm at tomorrow's start time. StreakLost can't be forced on the device without a date change. It's covered by `rolloverAfterMissedDayResetsStreak` and the `startScreen` test.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/waterreminder/ui
git commit -m "feat: All done, streak and missed-goal screens"
```

---

### Task 8: Final pass

**Files:**
- Modify: `README.md`
- Modify: `res/drawable/ic_water.xml` (launcher icon redrawn in the sketch style: a tapered glass outline in Paper with Water fill)

- [ ] **Step 1: Battery and lint checks**

Run: `grep -rnE "infiniteRepeatable|rememberInfiniteTransition|WakeLock|WorkManager|Vibrator|HapticFeedback|performHapticFeedback" app/src/main`. Expected: no matches. Run: `adb -s $w shell "dumpsys alarm | grep -c com.example.waterreminder.action.REMIND"`. Expected: `1`.

- [ ] **Step 2: Rewrite README**

Short sections: what it does (the flow in five lines), build/install commands (CLI only, with `-s <watch>`), Samsung battery tip (set the app's battery use to Unrestricted, turn off "Pause app activity if unused"), and where the spec and plan live. Remove the obsolete Android Studio/emulator instructions.

- [ ] **Step 3: Full build, tests, install, smoke test**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`, then install. Walk the whole flow once on the watch: onboarding → home → reminder → swipe ✓ → … → goal → streak.

- [ ] **Step 4: Commit**

```bash
git add README.md app/src/main/res/drawable/ic_water.xml
git commit -m "docs: README for the redesigned app; sketch-style launcher icon"
```
