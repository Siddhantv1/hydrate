package com.example.waterreminder.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.waterreminder.data.HydrateStore
import com.example.waterreminder.domain.*
import com.example.waterreminder.reminder.ReminderNotification
import com.example.waterreminder.reminder.ReminderScheduler
import java.time.ZonedDateTime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HydrateViewModel(app: Application) : AndroidViewModel(app) {
    private val store = HydrateStore.get(app)
    private val _state = MutableStateFlow<HydrateState?>(null)
    private val _screen = MutableStateFlow<Screen?>(null)
    private val _lastLog = MutableStateFlow<Logged?>(null)
    val state: StateFlow<HydrateState?> = _state.asStateFlow()
    val screen: StateFlow<Screen?> = _screen.asStateFlow()
    val lastLog: StateFlow<Logged?> = _lastLog.asStateFlow()

    var draft by mutableStateOf(
        Draft(25, Gender.MALE, VolumeUnit.ML, true, suggestedGoalGlasses(25, Gender.MALE, VolumeUnit.ML), 480, 1320)
    )

    init {
        viewModelScope.launch { store.state.collect { _state.value = it } }
    }

    private fun seedDraft(s: HydrateState) {
        draft = Draft(s.age, s.gender, s.unit, s.autoGoal, s.goalGlasses, s.startMinute, s.endMinute)
    }

    fun go(to: Screen) {
        if (to == Screen.EditGoal || to == Screen.EditHours) _state.value?.let(::seedDraft)
        _screen.value = to
    }

    fun back(): Boolean {
        val to = backOf(_screen.value ?: return false) ?: return false
        _screen.value = to
        return true
    }

    private suspend fun apply(cancelNotification: Boolean = false, rule: (HydrateState, ZonedDateTime) -> HydrateState):
        Pair<HydrateState, HydrateState> {
        // NonCancellable: finish() must not land between the store write and the alarm re-arm.
        return withContext(NonCancellable) {
            val now = ZonedDateTime.now()
            val r = store.update { rule(it, now) }
            ReminderScheduler.arm(getApplication(), r.second)
            if (cancelNotification) ReminderNotification.cancel(getApplication())
            r
        }
    }

    private fun goalJustMet(before: HydrateState, after: HydrateState): Boolean {
        val today = ZonedDateTime.now().toLocalDate().toString()
        return after.goalMetDate == today && before.goalMetDate != today
    }

    fun onForeground(force: Boolean) {
        viewModelScope.launch {
            val (_, after) = apply { s, now -> planNext(s, now, null) }
            _state.value = after
            if (force || _screen.value.let { it == null || !keepsPlaceOnResume(it) }) _screen.value = startScreen(after)
        }
    }

    fun finishOnboarding() = viewModelScope.launch {
        val d = draft
        apply { s, now ->
            completeOnboarding(s, d.age, d.gender, d.unit, d.autoGoal, d.goalGlasses, d.startMinute, d.endMinute, now)
        }
        _screen.value = Screen.AllSet
    }

    private fun log(n: Int, before: HydrateState, after: HydrateState) {
        _lastLog.value = Logged(before.glassesToday, after.glassesToday, after.goalGlasses)
    }

    fun addGlass() {
        viewModelScope.launch {
            val (b, a) = apply(true) { s, now -> addGlasses(s, 1, now) }
            log(1, b, a)
            if (goalJustMet(b, a)) _screen.value = Screen.AllDone // else stay on Home; AllDone from an earlier tap wins
        }
    }

    fun undoGlass() {
        viewModelScope.launch { apply { s, now -> undoGlass(s, now) } }
    }

    fun drank() {
        viewModelScope.launch {
            val (b, a) = apply(true) { s, now -> drank(s, now) }
            log(1, b, a)
            _screen.value = if (goalJustMet(b, a)) Screen.AllDone else Screen.HellYeah
        }
    }

    fun skip() {
        viewModelScope.launch {
            apply(true) { s, now -> skip(s, now) }
            _screen.value = Screen.SkipNote
        }
    }

    fun resetTimer() {
        viewModelScope.launch {
            apply { s, now -> resetTimer(s, now) }
            _screen.value = Screen.Home
        }
    }

    fun ignoreToday() {
        viewModelScope.launch {
            apply(true) { s, now -> ignoreToday(s, now) }
            _screen.value = Screen.Home
        }
    }

    fun saveGoal() {
        viewModelScope.launch {
            val d = draft
            apply { s, now -> saveGoal(s, d.unit, d.autoGoal, d.goalGlasses, now) }
            _screen.value = Screen.EditHours
        }
    }

    fun saveHours() {
        viewModelScope.launch {
            val d = draft
            apply { s, now -> saveHours(s, d.startMinute, d.endMinute, now) }
            _screen.value = Screen.Home
        }
    }

    fun dismissMissedGoal() {
        viewModelScope.launch {
            val (_, a) = store.update { dismissMissedGoal(it) }
            _screen.value = startScreen(a)
        }
    }
}