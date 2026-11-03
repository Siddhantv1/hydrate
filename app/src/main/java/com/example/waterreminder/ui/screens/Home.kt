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
    glyph: DrawScope.(Color) -> Unit,
) {
    val ink = if (enabled) Ink.Paper else Ink.Muted
    Box(
        modifier.size(size)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, interactionSource = null, indication = null, onClick = onClick)
            .drawBehind {
                val pts = ring(center, this.size.minDimension / 2f - 2.dp.toPx(), fromDeg = -100f + seed * 7, n = 9)
                if (filled && enabled) drawPath(sketchPath(pts, seed, closed = true), ink)
                sketchStroke(pts, seed, ink, closed = true)
                glyph(if (filled && enabled) Ink.Bg else ink)
            },
    )
}

/** Undo arrow (the usual undo icon): a chevron pointing left on top, its shaft hooking round on the right and back. */
private fun DrawScope.undoArrow(color: Color) {
    val r = size.minDimension * 0.3f
    fun q(x: Float, y: Float) = center + Offset((x + 0.12f) * r, (y + 0.08f) * r)
    val tip = q(-0.85f, -0.35f)
    sketchStroke(listOf(q(-0.4f, -0.8f), tip, q(-0.4f, 0.1f)), 61, color)
    val hookC = q(0.15f, 0.1f)
    sketchStroke(listOf(tip) + ring(hookC, 0.45f * r, fromDeg = -90f, sweepDeg = 180f, n = 5) + q(-0.3f, 0.55f), 62, color)
}

@Composable
fun HomeScreen(state: HydrateState, onAdd: () -> Unit, onUndo: () -> Unit, onMenu: () -> Unit) {
    val glasses = state.glassesToday
    // Entering Home never replays a rise (Hell yeah! shows it); while Home is up, + and undo animate the glass.
    Column(Modifier.fillMaxSize().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // The drawn glass is 68% of the box: ~87dp, ~41% of the 212dp screen. A taller box pushes the streak row off the bottom.
            WaterGlass(glasses, state.goalGlasses, Modifier.size(128.dp, 132.dp))
            InkButton("Menu", 36.dp, 1, onMenu, Modifier.offset(x = (-68).dp)) { ink ->
                listOf(-1, 0, 1).forEach { k ->
                    drawCircle(ink, 2.2.dp.toPx(), center + Offset(k * 7.dp.toPx(), (k * k - 0.5f) * 0.6.dp.toPx()))
                }
            }
            InkButton("Undo glass", 36.dp, 2, onUndo, Modifier.offset(x = 68.dp), enabled = glasses > 0, filled = true) { undoArrow(it) }
        }
        InkButton("Add a glass", 48.dp, 3, onAdd) { _ ->
            val r = size.minDimension * 0.22f
            sketchStroke(listOf(center + Offset(-r, 0f), center + Offset(r, r * 0.06f)), 63, Ink.Water)
            sketchStroke(listOf(center + Offset(0f, -r), center + Offset(r * 0.05f, r)), 64, Ink.Water)
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SketchFlame(lit = state.streak > 0, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(3.dp))
            Text("${state.streak}", style = body(13.sp).copy(color = if (state.streak > 0) Ink.Ember else Ink.Muted))
        }
    }
}

@Composable
fun MenuScreen(onChangeGoal: () -> Unit, onResetTimer: () -> Unit, onIgnoreToday: () -> Unit) {
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
        Column(
            Modifier.verticalScroll(scroll).padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            listOf("Change goal" to onChangeGoal, "Reset timer" to onResetTimer, "Ignore for today" to onIgnoreToday)
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
