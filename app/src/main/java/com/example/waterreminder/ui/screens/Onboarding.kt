package com.example.waterreminder.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.material.curvedText
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.PickerState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberPickerState
import com.example.waterreminder.domain.Gender
import com.example.waterreminder.domain.VolumeUnit
import com.example.waterreminder.domain.formatGlasses
import com.example.waterreminder.domain.suggestedGoalGlasses
import com.example.waterreminder.ui.Draft
import com.example.waterreminder.ui.draw.GenderGlyph
import com.example.waterreminder.ui.draw.SketchBowl
import com.example.waterreminder.ui.draw.SketchPill
import com.example.waterreminder.ui.draw.ring
import com.example.waterreminder.ui.draw.sketchStroke
import com.example.waterreminder.ui.theme.Bricolage
import com.example.waterreminder.ui.theme.Ink
import com.example.waterreminder.ui.theme.body
import com.example.waterreminder.ui.theme.display
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MIN_AGE = 8
private const val MIN_GOAL = 4
private const val MAX_GOAL = 20
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

/** One bezel detent = one option on the picker [target] returns; takes focus on entry. No haptics. */
@Composable
private fun Modifier.bezel(target: () -> PickerState): Modifier {
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val aim = remember { intArrayOf(0) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    return onRotaryScrollEvent { e ->
        val s = target()
        if (!s.isScrollInProgress) aim[0] = s.selectedOption
        aim[0] = (aim[0] + if (e.verticalScrollPixels > 0) 1 else -1).coerceIn(0, s.numberOfOptions - 1)
        val to = aim[0]
        scope.launch { s.animateScrollToOption(to) }
        true
    }.focusRequester(focus).focusable()
}

/** Wear picker with the selected row boxed in ink, no fade gradient, no ripple. */
@Composable
private fun InkPicker(
    state: PickerState,
    label: (Int) -> String,
    description: String,
    modifier: Modifier,
    style: TextStyle,
    row: Dp,
    box: Color = Ink.Paper,
    onTap: (selected: Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    Box(modifier.height(row * 3), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(row + 4.dp)) {
            sketchStroke(listOf(Offset.Zero, Offset(size.width, 0f), Offset(size.width, size.height), Offset(0f, size.height)), description.length + 60, box, closed = true)
        }
        Picker(
            state = state,
            contentDescription = "$description ${label(state.selectedOption)}",
            modifier = Modifier.fillMaxSize(),
            gradientRatio = 0f,
            rotaryScrollableBehavior = null,
        ) { i ->
            val selected = i == state.selectedOption
            Box(
                Modifier.fillMaxWidth().height(row).clickable(interactionSource = null, indication = null) {
                    onTap(selected)
                    if (!selected) scope.launch { state.animateScrollToOption(i) }
                },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(i), style = style.copy(color = if (selected) Ink.Paper else Ink.Muted))
            }
        }
    }
}

/** Onboarding page: title curved along the top rim, content centred below it. */
@Composable
private fun Page(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().then(modifier)) {
        CurvedLayout(Modifier.fillMaxSize()) {
            curvedText(title, style = CurvedTextStyle(fontSize = 17.sp, fontFamily = Bricolage, fontWeight = FontWeight(800), color = Ink.Paper))
        }
        Column(
            Modifier.fillMaxSize().padding(top = 28.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

@Composable
fun AgeScreen(draft: Draft, onChange: (Draft) -> Unit, onNext: () -> Unit) {
    val state = rememberPickerState(100 - MIN_AGE + 1, (draft.age - MIN_AGE).coerceIn(0, 100 - MIN_AGE), repeatItems = false)
    Page("Your age", Modifier.bezel { state }) {
        InkPicker(state, { "${it + MIN_AGE}" }, "Age", Modifier.width(110.dp), display(30.sp), 46.dp, onTap = { if (it) { onChange(draft.copy(age = state.selectedOption + MIN_AGE)); onNext() } })
    }
}

@Composable
fun GenderScreen(onPick: (Gender) -> Unit) {
    Page("Your gender") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Gender.entries.forEach { g ->
                val name = if (g == Gender.MALE) "male" else "female"
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(78.dp)
                            .semantics { contentDescription = name }
                            .clickable(role = Role.Button, interactionSource = null, indication = null) { onPick(g) }
                            .drawBehind {
                                sketchStroke(ring(center, size.minDimension / 2f - 3.dp.toPx(), fromDeg = -110f, sweepDeg = 385f, n = 20), 70 + g.ordinal)
                            },
                        contentAlignment = Alignment.Center,
                    ) { GenderGlyph(g, Modifier.size(42.dp)) }
                    Text(name, style = body(13.sp).copy(color = Ink.Muted))
                }
            }
        }
    }
}

@Composable
private fun Tick(checked: Boolean, modifier: Modifier) = Canvas(modifier) {
    sketchStroke(ring(center, size.minDimension / 2f - 2.dp.toPx(), n = 7), 80, if (checked) Ink.Paper else Ink.Muted, closed = true)
    if (checked) sketchStroke(
        listOf(Offset(size.width * 0.26f, size.height * 0.52f), Offset(size.width * 0.44f, size.height * 0.72f), Offset(size.width * 0.80f, size.height * 0.24f)),
        81, Ink.Water,
    )
}

private fun suggested(d: Draft) = suggestedGoalGlasses(d.age, d.gender, d.unit).coerceIn(MIN_GOAL, MAX_GOAL)

@Composable
fun GoalPicker(draft: Draft, onChange: (Draft) -> Unit, onConfirm: () -> Unit) {
    val d by rememberUpdatedState(draft)
    val change by rememberUpdatedState(onChange)
    val index = draft.goalGlasses.coerceIn(MIN_GOAL, MAX_GOAL) - MIN_GOAL
    val state = rememberPickerState(MAX_GOAL - MIN_GOAL + 1, index, repeatItems = false)
    LaunchedEffect(Unit) { if (d.autoGoal && d.goalGlasses != suggested(d)) change(d.copy(goalGlasses = suggested(d))) }
    LaunchedEffect(index) { if (state.selectedOption != index) state.scrollToOption(index) }
    // Read in composition: a snapshotFlow on selectedOption stopped emitting after the first change on device.
    val picked = state.selectedOption + MIN_GOAL
    LaunchedEffect(picked) {
        // A move that didn't come from the draft is the user: auto goes off.
        if (picked != d.goalGlasses) change(d.copy(goalGlasses = picked, autoGoal = false))
    }
    Page("Set goal", Modifier.bezel { state }) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            VolumeUnit.entries.forEach { u ->
                SketchPill(
                    text = if (u == VolumeUnit.ML) "ml" else if (u == VolumeUnit.L) "L" else "oz",
                    onClick = { val nd = d.copy(unit = u); change(if (nd.autoGoal) nd.copy(goalGlasses = suggested(nd)) else nd) },
                    selected = u == draft.unit,
                    style = display(13.sp),
                )
            }
        }
        InkPicker(state, { formatGlasses(it + MIN_GOAL, draft.unit) }, "Goal", Modifier.width(128.dp), display(20.sp), 26.dp)
        Row(
            Modifier.clickable(role = Role.Checkbox, interactionSource = null, indication = null) {
                change(if (d.autoGoal) d.copy(autoGoal = false) else d.copy(autoGoal = true, goalGlasses = suggested(d)))
            }.padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tick(draft.autoGoal, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Set goal automatically", style = body(13.sp))
        }
        Spacer(Modifier.height(4.dp))
        SketchPill("Confirm", onConfirm)
    }
}

private fun clock(minute: Int): String = LocalTime.of(minute / 60, minute % 60).format(timeFormat)

@Composable
fun HoursPicker(draft: Draft, onChange: (Draft) -> Unit, onConfirm: () -> Unit) {
    val d by rememberUpdatedState(draft)
    val change by rememberUpdatedState(onChange)
    val start = rememberPickerState(96, (draft.startMinute / 15).coerceIn(0, 95), repeatItems = false)
    val end = rememberPickerState(96, (draft.endMinute / 15).coerceIn(0, 95), repeatItems = false)
    var active by remember { mutableIntStateOf(0) }
    val from = start.selectedOption * 15
    val to = end.selectedOption * 15
    LaunchedEffect(from, to) { if (from != d.startMinute || to != d.endMinute) change(d.copy(startMinute = from, endMinute = to)) }
    val startMoving = start.isScrollInProgress
    val endMoving = end.isScrollInProgress
    LaunchedEffect(startMoving, endMoving) { if (startMoving) active = 0 else if (endMoving) active = 1 }
    Page("Remind me", Modifier.bezel { if (active == 0) start else end }) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(start to "from", end to "to").forEachIndexed { i, (state, name) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name, style = body(12.sp).copy(color = if (active == i) Ink.Water else Ink.Muted))
                    InkPicker(
                        state, { clock(it * 15) }, "Remind me $name",
                        Modifier.fillMaxWidth(), display(16.sp), 28.dp,
                        box = if (active == i) Ink.Water else Ink.Muted,
                        onTap = { active = i },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        SketchPill("Confirm", onConfirm, enabled = to > from)
    }
}

/** Saves the onboarding exactly once, shows the full bowl, then hands over to Home. */
@Composable
fun AllSetScreen(finish: () -> Job, onDone: () -> Unit) {
    var saved by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!saved) { saved = true; finish().join() }
        delay(1500)
        onDone()
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("All set!", style = display(30.sp))
        Spacer(Modifier.height(6.dp))
        SketchBowl(level = 1f, modifier = Modifier.size(130.dp, 78.dp))
    }
}
