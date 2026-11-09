package com.example.waterreminder.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.waterreminder.domain.Gender
import com.example.waterreminder.domain.HydrateState
import com.example.waterreminder.domain.VolumeUnit
import com.example.waterreminder.domain.rollover
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.hydrateDataStore by preferencesDataStore(name = "hydrate")

class HydrateStore(
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val age = intPreferencesKey("age")
        val gender = stringPreferencesKey("gender")
        val unit = stringPreferencesKey("unit")
        val autoGoal = booleanPreferencesKey("autoGoal")
        val goalGlasses = intPreferencesKey("goalGlasses")
        val startMinute = intPreferencesKey("startMinute")
        val endMinute = intPreferencesKey("endMinute")
        val glassesToday = intPreferencesKey("glassesToday")
        val date = stringPreferencesKey("date")
        val streak = intPreferencesKey("streak")
        val goalMetDate = stringPreferencesKey("goalMetDate")
        val ignoredDate = stringPreferencesKey("ignoredDate")
        val nextReminderAt = longPreferencesKey("nextReminderAt")
        val outstanding = booleanPreferencesKey("outstanding")
        val doubleNext = booleanPreferencesKey("doubleNext")
        val showMissedGoal = booleanPreferencesKey("showMissedGoal")
        val firstDate = stringPreferencesKey("firstDate")
        val lastReminderAt = longPreferencesKey("lastReminderAt")
    }

    private fun read(p: Preferences): HydrateState {
        val d = HydrateState()
        return HydrateState(
            onboarded = p[K.onboarded] ?: d.onboarded,
            age = p[K.age] ?: d.age,
            gender = p[K.gender]?.let { runCatching { Gender.valueOf(it) }.getOrNull() } ?: d.gender,
            unit = p[K.unit]?.let { runCatching { VolumeUnit.valueOf(it) }.getOrNull() } ?: d.unit,
            autoGoal = p[K.autoGoal] ?: d.autoGoal,
            goalGlasses = p[K.goalGlasses] ?: d.goalGlasses,
            startMinute = p[K.startMinute] ?: d.startMinute,
            endMinute = p[K.endMinute] ?: d.endMinute,
            glassesToday = p[K.glassesToday] ?: d.glassesToday,
            date = p[K.date] ?: d.date,
            streak = p[K.streak] ?: d.streak,
            goalMetDate = p[K.goalMetDate] ?: d.goalMetDate,
            ignoredDate = p[K.ignoredDate] ?: d.ignoredDate,
            nextReminderAt = p[K.nextReminderAt] ?: d.nextReminderAt,
            outstanding = p[K.outstanding] ?: d.outstanding,
            doubleNext = p[K.doubleNext] ?: d.doubleNext,
            showMissedGoal = p[K.showMissedGoal] ?: d.showMissedGoal,
            firstDate = p[K.firstDate] ?: d.firstDate,
            lastReminderAt = p[K.lastReminderAt] ?: d.lastReminderAt,
        )
    }

    private fun write(p: androidx.datastore.preferences.core.MutablePreferences, s: HydrateState) {
        p[K.onboarded] = s.onboarded
        p[K.age] = s.age
        p[K.gender] = s.gender.name
        p[K.unit] = s.unit.name
        p[K.autoGoal] = s.autoGoal
        p[K.goalGlasses] = s.goalGlasses
        p[K.startMinute] = s.startMinute
        p[K.endMinute] = s.endMinute
        p[K.glassesToday] = s.glassesToday
        p[K.date] = s.date
        p[K.streak] = s.streak
        p[K.goalMetDate] = s.goalMetDate
        p[K.ignoredDate] = s.ignoredDate
        p[K.nextReminderAt] = s.nextReminderAt
        p[K.outstanding] = s.outstanding
        p[K.doubleNext] = s.doubleNext
        p[K.showMissedGoal] = s.showMissedGoal
        p[K.firstDate] = s.firstDate
        p[K.lastReminderAt] = s.lastReminderAt
    }

    /** Persisted state passed through rollover(today); does not write. */
    val state: Flow<HydrateState> =
        dataStore.data.map { rollover(read(it), LocalDate.now(clock())) }

    /** Returns (rolledBefore, after); persists `after` atomically. */
    suspend fun update(transform: (HydrateState) -> HydrateState): Pair<HydrateState, HydrateState> {
        var result: Pair<HydrateState, HydrateState>? = null
        dataStore.edit { p ->
            val before = rollover(read(p), LocalDate.now(clock()))
            val after = transform(before)
            write(p, after)
            result = before to after
        }
        return result!!
    }

    companion object {
        @Volatile private var instance: HydrateStore? = null

        fun get(context: Context): HydrateStore =
            instance ?: synchronized(this) {
                instance ?: HydrateStore(context.applicationContext.hydrateDataStore).also { instance = it }
            }
    }
}
