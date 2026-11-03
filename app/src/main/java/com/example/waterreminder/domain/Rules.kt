package com.example.waterreminder.domain

import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.ceil

fun glassMl(unit: VolumeUnit): Int = if (unit == VolumeUnit.OZ) 237 else 250

fun formatGlasses(glasses: Int, unit: VolumeUnit): String = when (unit) {
    VolumeUnit.ML -> "${glasses * 250} ml"
    VolumeUnit.L -> {
        val litres = String.format(Locale.US, "%.2f", glasses * 250 / 1000.0).trimEnd('0')
        "${if (litres.endsWith(".")) litres + "0" else litres} L"
    }
    VolumeUnit.OZ -> "${glasses * 8} oz"
}

// EFSA adequate-intake starting value, not medical advice.
fun suggestedGoalGlasses(age: Int, gender: Gender, unit: VolumeUnit): Int {
    val ml = when {
        age <= 8 -> 1600
        age <= 13 -> if (gender == Gender.MALE) 2100 else 1900
        else -> if (gender == Gender.MALE) 2500 else 2000
    }
    return ceil(ml.toDouble() / glassMl(unit)).toInt()
}

fun intervalMinutes(s: HydrateState): Long =
    ((s.endMinute - s.startMinute) / s.goalGlasses.coerceAtLeast(1).toLong()).coerceAtLeast(15L)

fun goalMet(s: HydrateState): Boolean = s.glassesToday >= s.goalGlasses

fun canRemind(s: HydrateState, now: ZonedDateTime): Boolean {
    val minute = now.hour * 60 + now.minute
    return s.onboarded && s.endMinute > s.startMinute && minute >= s.startMinute && minute < s.endMinute &&
        !goalMet(s) && s.ignoredDate != now.toLocalDate().toString()
}

fun rollover(s: HydrateState, today: LocalDate): HydrateState {
    if (s.date == today.toString()) return s
    val missed = s.onboarded && s.date != "" &&
        ((s.goalMetDate != s.date && s.date != s.firstDate) || today > LocalDate.parse(s.date).plusDays(1))
    return s.copy(
        glassesToday = 0,
        outstanding = false,
        doubleNext = false,
        date = today.toString(),
        streak = if (missed) 0 else s.streak,
        showMissedGoal = s.showMissedGoal || missed,
    )
}

fun settleGoal(s: HydrateState, today: LocalDate): HydrateState {
    val d = today.toString()
    return when {
        goalMet(s) && s.goalMetDate != d -> s.copy(streak = s.streak + 1, goalMetDate = d)
        !goalMet(s) && s.goalMetDate == d -> s.copy(streak = (s.streak - 1).coerceAtLeast(0), goalMetDate = "")
        else -> s
    }
}

fun planNext(s: HydrateState, now: ZonedDateTime, from: ZonedDateTime?): HydrateState {
    val today = now.toLocalDate()
    fun startOf(day: LocalDate) = day.atStartOfDay(now.zone).plusMinutes(s.startMinute.toLong())
    fun endOf(day: LocalDate) = day.atStartOfDay(now.zone).plusMinutes(s.endMinute.toLong())
    fun clamp(t: ZonedDateTime) = when {
        t < startOf(today) -> startOf(today)
        t >= endOf(today) -> startOf(today.plusDays(1))
        else -> t
    }
    val next = when {
        !s.onboarded || s.endMinute <= s.startMinute -> 0L
        goalMet(s) || s.ignoredDate == today.toString() -> startOf(today.plusDays(1)).toInstant().toEpochMilli()
        from != null -> clamp(from.plusMinutes(intervalMinutes(s))).toInstant().toEpochMilli()
        s.nextReminderAt > now.toInstant().toEpochMilli() -> s.nextReminderAt
        else -> clamp(now.plusMinutes(intervalMinutes(s))).toInstant().toEpochMilli()
    }
    return s.copy(nextReminderAt = next)
}

fun addGlasses(s: HydrateState, n: Int, now: ZonedDateTime): HydrateState =
    planNext(settleGoal(s.copy(glassesToday = s.glassesToday + n, outstanding = false), now.toLocalDate()), now, now)

fun undoGlass(s: HydrateState, now: ZonedDateTime): HydrateState =
    planNext(settleGoal(s.copy(glassesToday = (s.glassesToday - 1).coerceAtLeast(0)), now.toLocalDate()), now, now)

fun drank(s: HydrateState, now: ZonedDateTime): HydrateState =
    addGlasses(s.copy(doubleNext = false), if (s.doubleNext) 2 else 1, now)

fun skip(s: HydrateState, now: ZonedDateTime): HydrateState =
    planNext(s.copy(doubleNext = true, outstanding = false), now, now)

fun reminderFired(s: HydrateState, now: ZonedDateTime): HydrateState =
    if (canRemind(s, now)) planNext(s.copy(doubleNext = s.doubleNext || s.outstanding, outstanding = true), now, now)
    else planNext(s, now, null)

fun resetTimer(s: HydrateState, now: ZonedDateTime): HydrateState = planNext(s, now, now)

fun ignoreToday(s: HydrateState, now: ZonedDateTime): HydrateState =
    planNext(s.copy(ignoredDate = now.toLocalDate().toString(), outstanding = false), now, null)

fun completeOnboarding(
    s: HydrateState, age: Int, gender: Gender, unit: VolumeUnit, autoGoal: Boolean,
    goalGlasses: Int, startMinute: Int, endMinute: Int, now: ZonedDateTime,
): HydrateState = planNext(
    s.copy(
        onboarded = true, age = age, gender = gender, unit = unit, autoGoal = autoGoal,
        goalGlasses = goalGlasses, startMinute = startMinute, endMinute = endMinute,
        date = now.toLocalDate().toString(), firstDate = now.toLocalDate().toString(), glassesToday = 0,
    ),
    now, null,
)

fun saveGoal(s: HydrateState, unit: VolumeUnit, autoGoal: Boolean, goalGlasses: Int, now: ZonedDateTime): HydrateState =
    planNext(
        settleGoal(s.copy(unit = unit, autoGoal = autoGoal, goalGlasses = goalGlasses), now.toLocalDate()),
        now, now,
    )

fun saveHours(s: HydrateState, startMinute: Int, endMinute: Int, now: ZonedDateTime): HydrateState =
    planNext(s.copy(startMinute = startMinute, endMinute = endMinute), now, now)

fun dismissMissedGoal(s: HydrateState): HydrateState = s.copy(showMissedGoal = false)

fun reminderBody(before: HydrateState, after: HydrateState): String = when {
    before.outstanding -> "Missed one. Make it two glasses."
    after.doubleNext -> "Make it two glasses."
    else -> "Glass ${after.glassesToday + 1} of ${after.goalGlasses}"
}
