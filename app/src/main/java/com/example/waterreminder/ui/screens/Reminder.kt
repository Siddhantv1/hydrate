package com.example.waterreminder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.example.waterreminder.domain.HydrateState
import com.example.waterreminder.domain.formatGlasses
import com.example.waterreminder.ui.Logged
import com.example.waterreminder.ui.draw.ArcSide
import com.example.waterreminder.ui.draw.ArcSwipeButton
import com.example.waterreminder.ui.draw.SketchBell
import com.example.waterreminder.ui.draw.SketchPill
import com.example.waterreminder.ui.draw.WaterGlass
import com.example.waterreminder.ui.draw.sketchStroke
import com.example.waterreminder.ui.theme.Ink
import com.example.waterreminder.ui.theme.body
import com.example.waterreminder.ui.theme.display
import kotlinx.coroutines.delay

/** Sketch 4: bell, "Drink up!", and the two edge-drag buttons (− skip on the left, ✓ drank on the right). */
@Composable
fun DrinkUpScreen(state: HydrateState, onSkip: () -> Unit, onDrank: () -> Unit) {
    val line = if (state.doubleNext) "Two glasses this time"
    else "${formatGlasses(1, state.unit)} · glass ${state.glassesToday + 1} of ${state.goalGlasses}"
    // Once one button fires, the other is dead and the subline stops following the store (it changes before the screen does).
    var frozen by remember { mutableStateOf<String?>(null) }
    fun once(action: () -> Unit): () -> Unit = { if (frozen == null) { frozen = line; action() } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.Center).offset(y = (-18).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SketchBell(Modifier.size(48.dp))
            Text("Drink up!", style = display(30.sp))
            Text(frozen ?: line, style = body(15.sp).copy(color = Ink.Paper.copy(alpha = 0.72f)))
        }
        ArcSwipeButton(ArcSide.LEFT, Ink.Skip, "Skip", once(onSkip)) {
            val r = size.minDimension * 0.2f
            sketchStroke(listOf(center + Offset(-r, 0f), center + Offset(r, r * 0.08f)), 91, Ink.Skip, width = 3.6.dp)
        }
        ArcSwipeButton(ArcSide.RIGHT, Ink.Done, "Drank", once(onDrank)) {
            checkGlyph(Ink.Done, 3.6.dp)
        }
    }
}

/** Sketch 6: the glass fills from [log].from to [log].to, the count ticks over, then home after 2 s. */
@Composable
fun HellYeahScreen(log: Logged, onDone: () -> Unit) {
    var ticked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(450); ticked = true
        delay(1550); onDone()
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        WaterGlass(
            log.to, log.total, Modifier.size(104.dp, 124.dp), fromGlasses = log.from,
            label = "${if (ticked) log.to else log.from}/${log.total}",
        )
        Text("Hell yeah!", style = display(28.sp))
    }
}

/** Sketch 5: the skip note. */
@Composable
fun SkipNoteScreen(onOk: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Make it two glasses next time", Modifier.padding(horizontal = 34.dp), style = display(20.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        SketchPill("OK", onOk)
    }
}

/** The hand-drawn ✓ shared by the drank button and the end-of-day buttons. */
internal fun DrawScope.checkGlyph(color: androidx.compose.ui.graphics.Color, width: Dp = 3.dp) {
    val r = size.minDimension * 0.2f
    sketchStroke(listOf(center + Offset(-r, 0f), center + Offset(-r * 0.25f, r * 0.7f), center + Offset(r, -r * 0.65f)), 92, color, width = width)
}
