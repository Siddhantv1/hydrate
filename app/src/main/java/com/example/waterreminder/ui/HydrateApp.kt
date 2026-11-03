package com.example.waterreminder.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.BasicSwipeToDismissBox
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.Text
import com.example.waterreminder.ui.screens.AgeScreen
import com.example.waterreminder.ui.screens.AllSetScreen
import com.example.waterreminder.ui.screens.DrinkUpScreen
import com.example.waterreminder.ui.screens.GenderScreen
import com.example.waterreminder.ui.screens.GoalPicker
import com.example.waterreminder.ui.screens.HellYeahScreen
import com.example.waterreminder.ui.screens.HomeScreen
import com.example.waterreminder.ui.screens.HoursPicker
import com.example.waterreminder.ui.screens.MenuScreen
import com.example.waterreminder.ui.screens.SkipNoteScreen

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
            state?.let { HomeScreen(it, vm::addGlass, vm::undoGlass) { vm.go(Screen.Menu) } }
        }
        Screen.Menu -> MenuScreen({ vm.go(Screen.EditGoal) }, vm::resetTimer, vm::ignoreToday)
        Screen.EditGoal -> GoalPicker(vm.draft, { vm.draft = it }, vm::saveGoal)
        Screen.EditHours -> HoursPicker(vm.draft, { vm.draft = it }, vm::saveHours)
        Screen.DrinkUp -> {
            val state by vm.state.collectAsState()
            state?.let { DrinkUpScreen(it, vm::skip, vm::drank) }
        }
        Screen.HellYeah -> {
            val log by vm.lastLog.collectAsState()
            log?.let { HellYeahScreen(it, home) }
        }
        Screen.SkipNote -> SkipNoteScreen(home)
        Screen.AllDone -> PlaceholderScreen("AllDone", listOf("Home" to home))
        Screen.StreakUp -> PlaceholderScreen("StreakUp", listOf("Home" to home))
        Screen.StreakLost -> PlaceholderScreen("StreakLost", listOf("dismiss" to { vm.dismissMissedGoal() }))
    }
}

// Temporary scaffolding; Task 7 deletes it.
@Composable
fun PlaceholderScreen(name: String, actions: List<Pair<String, () -> Unit>>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(name)
        actions.forEach { (label, f) -> Text("> $label", Modifier.clickable { f() }.padding(vertical = 8.dp)) }
    }
}