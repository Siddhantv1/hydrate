package com.example.waterreminder.ui.draw

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.example.waterreminder.ui.theme.Ink
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

private val Streak = Ink.Paper.copy(alpha = 0.3f)

/** The glass's geometry in a [w]×[h] box ([dp] px per dp). Walls belly out a touch and the base sags a touch. */
private class GlassShape(w: Float, h: Float, dp: Float) {
    val tl = Offset(0.16f * w, 0.06f * h); val bl = Offset(0.29f * w, 0.94f * h)
    val br = Offset(0.71f * w, 0.94f * h); val tr = Offset(0.84f * w, 0.06f * h)
    private val bow = 1.4f * dp
    val sag = 1.8f * dp
    private val run = 2.6f * dp // how far strokes run past where they meet
    // Mid-wall points pushed out along the wall normal: a 3-point pen line through them is a gentle arc.
    val lm = (tl + bl) / 2f + Offset(-(bl.y - tl.y), bl.x - tl.x) * (bow / (bl - tl).getDistance())
    val rm = (tr + br) / 2f + Offset(br.y - tr.y, -(br.x - tr.x)) * (bow / (br - tr).getDistance())
    val bm = Offset((bl.x + br.x) / 2f, bl.y + sag)
    val leftWall = listOf(tl, lm, bl + Offset(0f, run * 0.6f))
    val rightWall = listOf(tr, rm, br + Offset(0f, run * 0.6f))
    val base = listOf(bl + Offset(-run, -0.3f * sag), bm, br + Offset(run, -0.4f * sag))
    // Rim: a pen loop from the left end, over the top, back underneath and on past the start.
    val rim = ring(Offset((tl.x + tr.x) / 2f, tl.y), (tr.x - tl.x) / 2f + run * 0.7f, h * 0.035f, fromDeg = 180f, n = 12)
}

/** Tapered glass from the home-screen sketch, filled to glasses/total, with the count written inside. */
@Composable
fun WaterGlass(glasses: Int, total: Int, modifier: Modifier = Modifier, fromGlasses: Int = glasses, label: String = "$glasses/$total") {
    fun frac(g: Int) = if (total <= 0) 0f else (g.toFloat() / total).coerceIn(0f, 1f)
    val water = remember { SloshState(frac(fromGlasses)) }
    LaunchedEffect(glasses, total) { water.to(frac(glasses)) }
    Box(modifier, contentAlignment = Alignment.Center) {
        // Own layer: the slosh redraws ~70 frames per glass; keep that from re-recording the rest of the screen.
        // Geometry is built once per size; only the water changes per frame.
        Spacer(Modifier.matchParentSize().graphicsLayer().drawWithCache {
            val g = GlassShape(size.width, size.height, density)
            val i = 2.dp.toPx()
            // Water clip: a 7-edge convex polygon through the bow points, inset by i. Keep it a convex polygon of at
            // most 8 edges: the renderer clips those analytically, while a curved clip can cost a mask every frame.
            val inside = Path().apply {
                moveTo(g.tl.x + i, g.tl.y)
                lineTo(g.lm.x + i, g.lm.y); lineTo(g.bl.x + i, g.bl.y - i)
                lineTo(g.bm.x, g.bm.y - i); lineTo(g.br.x - i, g.br.y - i)
                lineTo(g.rm.x - i, g.rm.y); lineTo(g.tr.x - i, g.tr.y)
                close()
            }
            val top = size.height * 0.16f
            val bottom = g.bl.y + g.sag
            val streakW = 2.4.dp.toPx()
            val streakMin = 4.dp.toPx()
            val streakTop = 7.dp.toPx()
            val streakEnd = g.bl.y - 5.dp.toPx()
            val streakIn = 5.dp.toPx()
            fun wallX(y: Float) = g.tl.x + (g.bl.x - g.tl.x) * (y - g.tl.y) / (g.bl.y - g.tl.y) + streakIn
            onDrawBehind {
                val level = water.level.value
                if (level > 0.001f) clipPath(inside) {
                    val surface = bottom - (bottom - top) * level
                    drawWater(surface, water.amp.value.dp.toPx(), water.phase.value, bottom)
                    // Soft light streak down the water near the left wall.
                    val y0 = surface + streakTop
                    if (streakEnd - y0 > streakMin) drawLine(Streak, Offset(wallX(y0), y0), Offset(wallX(streakEnd), streakEnd), streakW, StrokeCap.Round)
                }
            }
        })
        // The outline never changes: its own offscreen layer is rendered once and reused as a texture while the water moves.
        Spacer(Modifier.matchParentSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithCache {
            val g = GlassShape(size.width, size.height, density)
            onDrawBehind {
                sketchStroke(g.leftWall, 7, width = 3.2.dp)
                sketchStroke(g.rightWall, 9, width = 3.2.dp)
                sketchStroke(g.base, 10, width = 3.2.dp)
                sketchStroke(g.rim, 8, closed = true)
            }
        })
        Text(label, style = display(22.sp).copy(shadow = Shadow(Color.Black, blurRadius = 10f)))
    }
}
