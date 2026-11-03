package com.example.waterreminder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.example.waterreminder.ui.draw.SketchBowl
import com.example.waterreminder.ui.draw.SketchFlame
import com.example.waterreminder.ui.theme.Ink
import com.example.waterreminder.ui.theme.display
import kotlinx.coroutines.delay

/** Sketch 3: full bowl; moves on to the streak after 1.5 s (cancelled if the screen is left first). */
@Composable
fun AllDoneScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(1500); onDone() }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        SketchBowl(1f, Modifier.size(130.dp, 90.dp))
        Text("All done!", style = display(30.sp))
    }
}

@Composable
private fun DayEnd(title: String, lit: Boolean, count: Int, onOk: () -> Unit, titleSize: Int = 20) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, Modifier.padding(horizontal = 34.dp), style = display(titleSize.sp), textAlign = TextAlign.Center)
        SketchFlame(lit, Modifier.size(36.dp))
        Text("$count", style = display(40.sp).let { if (lit) it else it.copy(color = Ink.Muted) })
        // 36dp drawing in a 48dp touch target; reported height is 40 dp (the old 4 spacer + 36 button) so the column's fit is unchanged.
        val trim = Modifier.layout { m, c ->
            val p = m.measure(c)
            layout(p.width, p.height - 8.dp.roundToPx()) { p.place(0, -4.dp.roundToPx()) }
        }
        InkButton("Done", 36.dp, 5, onOk, trim, touch = 48.dp) { checkGlyph(it) }
    }
}

/** Sketch 7. */
@Composable
fun StreakUpScreen(streak: Int, onOk: () -> Unit) = DayEnd("Goals met", true, streak, onOk)

/** Sketch 8. */
@Composable
fun StreakLostScreen(onOk: () -> Unit) = DayEnd("You didn't meet your goals", false, 0, onOk, titleSize = 15)
