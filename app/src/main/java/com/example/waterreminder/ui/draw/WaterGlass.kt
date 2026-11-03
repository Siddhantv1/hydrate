package com.example.waterreminder.ui.draw

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.example.waterreminder.ui.theme.display
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Water level plus a one-shot slosh: amplitude (dp) jumps to 6 and springs back to 0 while the phase runs ~1.2 s. */
internal class SloshState(initial: Float) {
    val level = Animatable(initial)
    val amp = Animatable(0f)
    val phase = Animatable(0f)

    suspend fun to(target: Float) = coroutineScope {
        val t = target.coerceIn(0f, 1f)
        if (t == level.targetValue) return@coroutineScope
        launch { level.animateTo(t, Slosh) }
        launch { amp.snapTo(6f); amp.animateTo(0f, Slosh) }
        launch { phase.snapTo(0f); phase.animateTo(3f * Math.PI.toFloat(), tween(1200, easing = LinearOutSlowInEasing)) }
    }
}

/** Tapered glass from the home-screen sketch, filled to glasses/total, with the count written inside. */
@Composable
fun WaterGlass(glasses: Int, total: Int, modifier: Modifier = Modifier, fromGlasses: Int = glasses) {
    fun frac(g: Int) = if (total <= 0) 0f else (g.toFloat() / total).coerceIn(0f, 1f)
    val water = remember { SloshState(frac(fromGlasses)) }
    LaunchedEffect(glasses, total) { water.to(frac(glasses)) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val tl = p(0.16f, 0.06f); val bl = p(0.29f, 0.94f); val br = p(0.71f, 0.94f); val tr = p(0.84f, 0.06f)
            val i = 2.dp.toPx()
            val inside = Path().apply {
                moveTo(tl.x + i, tl.y); lineTo(bl.x + i, bl.y - i); lineTo(br.x - i, br.y - i); lineTo(tr.x - i, tr.y); close()
            }
            val top = size.height * 0.16f
            val bottom = bl.y
            val level = water.level.value
            if (level > 0.001f) clipPath(inside) {
                drawWater(bottom - (bottom - top) * level, water.amp.value.dp.toPx(), water.phase.value, bottom)
            }
            sketchStroke(listOf(tl, bl, br, tr), 7)
            sketchStroke(ring(Offset((tl.x + tr.x) / 2f, tl.y), (tr.x - tl.x) / 2f, size.height * 0.035f, n = 10), 8, closed = true)
        }
        Text("$glasses/$total", style = display(22.sp).copy(shadow = Shadow(Color.Black, blurRadius = 10f)))
    }
}
