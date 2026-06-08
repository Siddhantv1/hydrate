package com.example.waterreminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.waterreminder.data.WaterDefaults
import com.example.waterreminder.data.WaterState
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun WaterReminderApp(viewModel: WaterReminderViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.Home) }

    when (screen) {
        Screen.Home -> HomeScreen(
            state = state,
            onAddDrink = viewModel::addDrink,
            onEditGoal = { screen = Screen.Goal },
            onEditWindow = { screen = Screen.Window },
            onReset = viewModel::resetToday
        )

        Screen.Goal -> GoalScreen(
            state = state,
            onSave = { goal, drink ->
                viewModel.saveGoal(goal, drink)
                screen = Screen.Home
            },
            onBack = { screen = Screen.Home }
        )

        Screen.Window -> ReminderWindowScreen(
            state = state,
            onSave = { start, end ->
                viewModel.saveReminderWindow(start, end)
                screen = Screen.Home
            },
            onBack = { screen = Screen.Home }
        )
    }
}

@Composable
private fun HomeScreen(
    state: WaterState,
    onAddDrink: () -> Unit,
    onEditGoal: () -> Unit,
    onEditWindow: () -> Unit,
    onReset: () -> Unit
) {
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 28.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ProgressHeader(state)
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${state.currentDayIntakeMl} / ${state.dailyGoalMl} ml",
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
            item {
                Text(
                    text = "${state.remainingIntakeMl} ml remaining",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            if (state.isGoalReached) {
                item {
                    Text(
                        text = "Goal reached",
                        color = MaterialTheme.colors.secondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                FullWidthButton("Add ${state.defaultDrinkAmountMl} ml", onAddDrink)
            }
            item {
                FullWidthButton("Goal", onEditGoal)
            }
            item {
                FullWidthButton("Reminder hours", onEditWindow)
            }
            item {
                FullWidthButton("Reset today", onReset)
            }
        }
    }
}

@Composable
private fun GoalScreen(
    state: WaterState,
    onSave: (goalMl: Int, drinkAmountMl: Int) -> Unit,
    onBack: () -> Unit
) {
    var goalDraft by remember(state.dailyGoalMl) {
        mutableIntStateOf(state.dailyGoalMl)
    }
    var drinkDraft by remember(state.defaultDrinkAmountMl) {
        mutableIntStateOf(state.defaultDrinkAmountMl)
    }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 28.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text("Daily goal", fontSize = 18.sp, textAlign = TextAlign.Center)
            }
            item {
                NumberStepper(
                    valueText = "$goalDraft ml",
                    onDecrease = {
                        goalDraft = (goalDraft - 100).coerceAtLeast(WaterDefaults.MIN_GOAL_ML)
                    },
                    onIncrease = {
                        goalDraft = (goalDraft + 100).coerceAtMost(WaterDefaults.MAX_GOAL_ML)
                    }
                )
            }
            item {
                Text("Drink amount", fontSize = 16.sp, textAlign = TextAlign.Center)
            }
            item {
                NumberStepper(
                    valueText = "$drinkDraft ml",
                    onDecrease = {
                        drinkDraft = (drinkDraft - 50)
                            .coerceAtLeast(WaterDefaults.MIN_DRINK_AMOUNT_ML)
                    },
                    onIncrease = {
                        drinkDraft = (drinkDraft + 50)
                            .coerceAtMost(WaterDefaults.MAX_DRINK_AMOUNT_ML)
                    }
                )
            }
            item {
                FullWidthButton("Save") { onSave(goalDraft, drinkDraft) }
            }
            item {
                FullWidthButton("Back", onBack)
            }
        }
    }
}

@Composable
private fun ReminderWindowScreen(
    state: WaterState,
    onSave: (startMinute: Int, endMinute: Int) -> Unit,
    onBack: () -> Unit
) {
    var startDraft by remember(state.reminderStartMinute) {
        mutableIntStateOf(state.reminderStartMinute)
    }
    var endDraft by remember(state.reminderEndMinute) {
        mutableIntStateOf(state.reminderEndMinute)
    }
    val isValid = endDraft > startDraft

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 28.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text("Start", fontSize = 18.sp, textAlign = TextAlign.Center)
            }
            item {
                TimeStepper(
                    valueText = formatMinute(startDraft),
                    onDecrease = { startDraft = adjustMinute(startDraft, -15) },
                    onIncrease = { startDraft = adjustMinute(startDraft, 15) }
                )
            }
            item {
                Text("End", fontSize = 18.sp, textAlign = TextAlign.Center)
            }
            item {
                TimeStepper(
                    valueText = formatMinute(endDraft),
                    onDecrease = { endDraft = adjustMinute(endDraft, -15) },
                    onIncrease = { endDraft = adjustMinute(endDraft, 15) }
                )
            }
            if (!isValid) {
                item {
                    Text(
                        text = "End must be after start",
                        color = MaterialTheme.colors.error,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            }
            item {
                FullWidthButton("Save", enabled = isValid) {
                    onSave(startDraft, endDraft)
                }
            }
            item {
                FullWidthButton("Back", onBack)
            }
        }
    }
}

@Composable
private fun ProgressHeader(state: WaterState) {
    Box(
        modifier = Modifier.size(104.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = state.progressFraction,
            modifier = Modifier.fillMaxSize()
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${state.percentComplete}%",
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "complete",
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NumberStepper(
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton("-", onDecrease)
        Text(
            text = valueText,
            modifier = Modifier.width(96.dp),
            textAlign = TextAlign.Center,
            fontSize = 16.sp
        )
        StepButton("+", onIncrease)
    }
}

@Composable
private fun TimeStepper(
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton("-", onDecrease)
        Text(
            text = valueText,
            modifier = Modifier.width(104.dp),
            textAlign = TextAlign.Center,
            fontSize = 16.sp
        )
        StepButton("+", onIncrease)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(42.dp)
    ) {
        Text(label, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FullWidthButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Chip(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        label = {
            Text(label, textAlign = TextAlign.Center)
        }
    )
}

private fun adjustMinute(value: Int, delta: Int): Int {
    val lastMinute = 23 * 60 + 45
    return (value + delta).coerceIn(0, lastMinute)
}

private fun formatMinute(minute: Int): String {
    val safeMinute = minute.coerceIn(0, 23 * 60 + 59)
    return LocalTime.of(safeMinute / 60, safeMinute % 60).format(TimeFormatter)
}

private enum class Screen {
    Home,
    Goal,
    Window
}

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
