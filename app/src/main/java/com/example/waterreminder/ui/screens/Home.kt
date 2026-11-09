package com.example.waterreminder.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.example.waterreminder.domain.HydrateState
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.material.curvedText
import com.example.waterreminder.ui.theme.Bricolage
import com.example.waterreminder.domain.isPausedToday
import com.example.waterreminder.domain.lastReminderLabel
import com.example.waterreminder.domain.nextReminderLabel
import com.example.waterreminder.domain.nextReminderShort
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextAlign
import com.example.waterreminder.ui.draw.SketchFlame
import com.example.waterreminder.ui.draw.WaterGlass
import com.example.waterreminder.ui.draw.ring
import com.example.waterreminder.ui.draw.sketchPath
import com.example.waterreminder.ui.draw.sketchStroke
import com.example.waterreminder.ui.theme.Ink
import com.example.waterreminder.ui.theme.body
import com.example.waterreminder.ui.theme.display
import kotlinx.coroutines.launch

/** Round hand-drawn button: [filled] paints the disc in ink (the sketch's solid right button). */
@Composable
internal fun InkButton(
    description: String,
    size: Dp,
    seed: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    touch: Dp = size,
    glyph: DrawScope.(Color) -> Unit,
) {
    val ink = if (enabled) Ink.Paper else Ink.Muted
    Box(
        modifier.size(touch)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).drawBehind {
                // Pen loop starting just below 9 o'clock, like the mockup's buttons.
                val pts = ring(center, this.size.minDimension / 2f - 2.dp.toPx(), fromDeg = 160f + seed * 4, n = 10)
                if (filled && enabled) drawPath(sketchPath(pts, seed, closed = true), ink)
                sketchStroke(pts, seed, ink, closed = true)
                glyph(if (filled && enabled) Ink.Bg else ink)
            })
    }
}

/**
 * Undo arrow (the usual undo icon): a chevron pointing left on top, its shaft hooking round on the right and back.
 * Traced from the mockup, where the button is centred on (360, 152) with a 33px loop radius.
 */
private fun DrawScope.undoArrow(color: Color) {
    val u = (size.minDimension / 2f - 2.dp.toPx()) / 33f
    fun q(x: Float, y: Float) = center + Offset((x - 360f) * u, (y - 152f) * u)
    sketchStroke(listOf(q(371f, 168f), q(378.3f, 160.2f), q(380.4f, 150.5f), q(377f, 141.6f), q(368f, 136f), q(355.9f, 135f), q(343f, 137f)), 62, color, width = 3.4.dp)
    sketchStroke(listOf(q(352f, 127f), q(342f, 137f), q(353f, 147f)), 61, color, width = 3.4.dp)
}

@Composable
fun HomeScreen(state: HydrateState, onAdd: () -> Unit, onUndo: () -> Unit, onStreak: () -> Unit, onMenu: () -> Unit) {
    val glasses = state.glassesToday
    // Entering Home never replays a rise (Hell yeah! shows it); while Home is up, + and undo animate the glass.
    Column(Modifier.fillMaxSize().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // The drawn glass is 68% of the box. A taller box pushes the next-reminder and streak rows off the bottom.
            WaterGlass(glasses, state.goalGlasses, Modifier.size(120.dp, 122.dp))
            InkButton("Menu", 36.dp, 1, onMenu, Modifier.offset(x = (-68).dp)) { ink ->
                listOf(-1, 0, 1).forEach { k ->
                    drawCircle(ink, 2.2.dp.toPx(), center + Offset(k * 7.dp.toPx(), (k * k - 0.5f) * 0.6.dp.toPx()))
                }
            }
            InkButton("Undo glass", 36.dp, 2, onUndo, Modifier.offset(x = 68.dp), enabled = glasses > 0, filled = true) { undoArrow(it) }
        }
        InkButton("Add a glass", 48.dp, 3, onAdd) { _ ->
            val r = size.minDimension * 0.22f
            sketchStroke(listOf(center + Offset(-r, 0f), center + Offset(r, r * 0.06f)), 63, Ink.Water, width = 3.6.dp)
            sketchStroke(listOf(center + Offset(0f, -r), center + Offset(r * 0.05f, r)), 64, Ink.Water, width = 3.6.dp)
        }
        Text(nextReminderShort(state, ZonedDateTime.now()), style = body(12.sp).copy(color = Ink.Muted))
        Row(
            Modifier.semantics { contentDescription = "Streak ${state.streak} days. Open calendar" }
                .clickable(role = Role.Button, interactionSource = null, indication = null, onClick = onStreak)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchFlame(lit = state.streak > 0, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(3.dp))
            Text("${state.streak}", style = body(13.sp).copy(color = if (state.streak > 0) Ink.Ember else Ink.Muted))
        }
    }
}

@Composable
fun MenuScreen(state: HydrateState?, onChangeGoal: () -> Unit, onResetTimer: () -> Unit, onIgnoreToday: () -> Unit, onResume: () -> Unit) {
    val paused = state != null && isPausedToday(state, ZonedDateTime.now())
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        Modifier.fillMaxSize()
            .onRotaryScrollEvent { e -> scope.launch { scroll.scrollBy(e.verticalScrollPixels) }; true }
            .focusRequester(focus).focusable(),
        contentAlignment = Alignment.Center,
    ) {
        // The schedule, curved along the rim: next reminder on top, and (once one has fired) the last one at the bottom.
        state?.let {
            val now = ZonedDateTime.now()
            CurvedLayout(Modifier.fillMaxSize()) {
                curvedText(nextReminderLabel(it, now), style = CurvedTextStyle(fontSize = 12.sp, fontFamily = Bricolage, color = Ink.Water))
            }
            if (it.lastReminderAt != 0L) CurvedLayout(Modifier.fillMaxSize(), anchor = 90f, angularDirection = CurvedDirection.Angular.Reversed) {
                curvedText(lastReminderLabel(it, now), style = CurvedTextStyle(fontSize = 12.sp, fontFamily = Bricolage, color = Ink.Muted))
            }
        }
        Column(
            Modifier.verticalScroll(scroll).padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            listOf("Change goal" to onChangeGoal, "Reset timer" to onResetTimer, if (paused) "Resume reminders" to onResume else "Ignore for today" to onIgnoreToday)
                .forEachIndexed { i, (label, action) ->
                    if (i > 0) Box(
                        Modifier.size(118.dp, 6.dp).drawBehind {
                            sketchStroke(listOf(Offset(0f, size.height / 2f), Offset(size.width * 0.5f, size.height * (0.3f + 0.4f * i)), Offset(size.width, size.height / 2f)), 70 + i, Ink.Muted)
                        }
                    )
                    Text(
                        label,
                        Modifier.clickable(role = Role.Button, interactionSource = null, indication = null, onClick = action)
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        style = display(18.sp),
                    )
                }
        }
    }
}

/** Confirms a menu action with the schedule it produced, then returns home. Tap to dismiss early. */
@Composable
fun NoticeScreen(text: String, onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(2500); onDone() }
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = onDone).padding(horizontal = 30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = display(20.sp), textAlign = TextAlign.Center)
    }
}
