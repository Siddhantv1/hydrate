package com.example.waterreminder.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.foundation.BasicSwipeToDismissBox
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import com.example.waterreminder.ui.screens.AgeScreen
import com.example.waterreminder.ui.screens.AllDoneScreen
import com.example.waterreminder.ui.screens.AllSetScreen
import com.example.waterreminder.ui.screens.CalendarScreen
import com.example.waterreminder.ui.screens.DrinkUpScreen
import com.example.waterreminder.ui.screens.GenderScreen
import com.example.waterreminder.ui.screens.GoalPicker
import com.example.waterreminder.ui.screens.HellYeahScreen
import com.example.waterreminder.ui.screens.HomeScreen
import com.example.waterreminder.ui.screens.HoursPicker
import com.example.waterreminder.ui.screens.MenuScreen
import com.example.waterreminder.ui.screens.NoticeScreen
import com.example.waterreminder.ui.screens.SkipNoteScreen
import com.example.waterreminder.ui.screens.StreakLostScreen
import com.example.waterreminder.ui.screens.StreakUpScreen

@Composable
fun HydrateApp(vm: HydrateViewModel) {
    val screen by vm.screen.collectAsState()
    val activity = LocalActivity.current
    val current = screen ?: return Box(Modifier.fillMaxSize().background(Color.Black))
    BackHandler { if (!vm.back()) activity?.finish() }
    BasicSwipeToDismissBox(
        onDismissed = { if (!vm.back()) activity?.finish() },
        state = rememberSwipeToDismissBoxState(),
        userSwipeEnabled = current != Screen.DrinkUp,
    ) { isBackground ->
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (!isBackground) Body(vm, current)
        }
    }
}

@Composable
private fun Body(vm: HydrateViewModel, screen: Screen) {
    val home = { vm.go(Screen.Home) }
    when (screen) {
        Screen.Age -> AgeScreen(vm.draft, { vm.draft = it }) { vm.go(Screen.Gender) }
        Screen.Gender -> GenderScreen { vm.draft = vm.draft.copy(gender = it); vm.go(Screen.Goal) }
        Screen.Goal -> GoalPicker(vm.draft, { vm.draft = it }) { vm.go(Screen.Hours) }
        Screen.Hours -> HoursPicker(vm.draft, { vm.draft = it }) { vm.go(Screen.AllSet) }
        Screen.AllSet -> AllSetScreen(vm::finishOnboarding, home)
        Screen.Home -> {
            val state by vm.state.collectAsState()
            state?.let { HomeScreen(it, vm::addGlass, vm::undoGlass, onStreak = { vm.go(Screen.Calendar) }) { vm.go(Screen.Menu) } }
        }
        Screen.Calendar -> {
            val state by vm.state.collectAsState()
            state?.let { CalendarScreen(it) }
        }
        Screen.Menu -> {
            val state by vm.state.collectAsState()
            MenuScreen(state, { vm.go(Screen.EditGoal) }, { vm.go(Screen.ResetHours) }, vm::ignoreToday, vm::resumeToday)
        }
        Screen.EditGoal -> GoalPicker(vm.draft, { vm.draft = it }, vm::saveGoal)
        Screen.EditHours, Screen.ResetHours -> HoursPicker(vm.draft, { vm.draft = it }, vm::saveHours)
        Screen.Notice -> {
            val text by vm.notice.collectAsState()
            NoticeScreen(text, home)
        }
        Screen.DrinkUp -> {
            val state by vm.state.collectAsState()
            state?.let { DrinkUpScreen(it, vm::skip, vm::drank) }
        }
        Screen.HellYeah -> {
            val log by vm.lastLog.collectAsState()
            log?.let { HellYeahScreen(it, home) }
        }
        Screen.SkipNote -> SkipNoteScreen(home)
        Screen.AllDone -> AllDoneScreen { vm.go(Screen.StreakUp) }
        Screen.StreakUp -> run {
            val state by vm.state.collectAsState()
            StreakUpScreen(state?.streak ?: 0, home)
        }
        Screen.StreakLost -> StreakLostScreen { vm.dismissMissedGoal() }
    }
}
