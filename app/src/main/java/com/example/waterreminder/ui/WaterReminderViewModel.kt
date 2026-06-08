package com.example.waterreminder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.waterreminder.data.WaterRepository
import com.example.waterreminder.data.WaterState
import com.example.waterreminder.reminder.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WaterReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WaterRepository(application)

    val uiState: StateFlow<WaterState> = repository.waterState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WaterState()
    )

    init {
        viewModelScope.launch {
            repository.ensureToday()
            ReminderScheduler.scheduleNextReminder(getApplication())
        }
    }

    fun addDrink() {
        viewModelScope.launch {
            repository.addDrink()
            ReminderScheduler.scheduleNextReminder(getApplication())
        }
    }

    fun saveGoal(goalMl: Int, drinkAmountMl: Int) {
        viewModelScope.launch {
            repository.setGoalAndDrinkAmount(goalMl, drinkAmountMl)
            ReminderScheduler.scheduleNextReminder(getApplication())
        }
    }

    fun saveReminderWindow(startMinute: Int, endMinute: Int) {
        viewModelScope.launch {
            repository.setReminderWindow(startMinute, endMinute)
            ReminderScheduler.scheduleNextReminder(getApplication())
        }
    }

    fun resetToday() {
        viewModelScope.launch {
            repository.resetToday()
            ReminderScheduler.scheduleNextReminder(getApplication())
        }
    }
}
