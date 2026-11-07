package com.example.waterreminder.ui.draw

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.example.waterreminder.domain.Gender
import com.example.waterreminder.ui.theme.Ink
import com.example.waterreminder.ui.theme.display
import kotlin.math.cos
import kotlin.math.sin

/** Shared spring for water level, slosh decay and the bowl fill. */
internal val Slosh = spring<Float>(dampingRatio = 0.55f, stiffness = 120f)

/** Point at fractions of the draw area. */
internal fun DrawScope.p(x: Float, y: Float) = Offset(x * size.width, y * size.height)

/** Points along an ellipse arc, degrees clockwise from 3 o'clock. */
fun ring(center: Offset, rx: Float, ry: Float = rx, fromDeg: Float = 0f, sweepDeg: Float = 360f, n: Int = 18): List<Offset> {
    val count = if (sweepDeg == 360f) n else n + 1
    return List(count) { i ->
        val a = Math.toRadians((fromDeg + sweepDeg * i / n).toDouble())
        Offset(center.x + rx * cos(a).toFloat(), center.y + ry * sin(a).toFloat())
    }
}

private data class SketchKey(val points: List<Offset>, val seed: Int, val pen: Pen, val density: Float)

// Reusing the same Path instance every frame lets the renderer cache its geometry; rebuilding it each
// frame made every animation frame re-tessellate every outline on screen.
// ponytail: unbounded-by-design small LRU (128 shapes); main-thread only, which is where drawing happens.
private val sketchCache = object : LinkedHashMap<SketchKey, Path>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SketchKey, Path>?) = size > 128
}

private fun DrawScope.penPath(points: List<Offset>, seed: Int, pen: Pen): Path =
    sketchCache.getOrPut(SketchKey(points, seed, pen, density)) {
        val c = penCurve(points, seed, pen, density)
        Path().apply {
            if (c.size >= 2) moveTo(c[0], c[1])
            var i = 2
            while (i + 5 < c.size) { cubicTo(c[i], c[i + 1], c[i + 2], c[i + 3], c[i + 4], c[i + 5]); i += 6 }
            if (pen == Pen.FILL) close()
        }
    }

/**
 * Hand-drawn path through [points] (see [penCurve]): one smooth pen line, same seed = same shape.
 * [closed] gives the properly closed outline, for fills and clips. Cached: callers must not mutate it.
 */
fun DrawScope.sketchPath(points: List<Offset>, seed: Int, closed: Boolean = false): Path =
    penPath(points, seed, if (closed) Pen.FILL else Pen.OPEN)

/** Strokes [points] as one pen line; [closed] shapes are drawn as a pen loop that runs a little past its start. */
fun DrawScope.sketchStroke(points: List<Offset>, seed: Int, color: Color = Ink.Paper, closed: Boolean = false, width: Dp = 2.2.dp) {
    drawPath(
        penPath(points, seed, if (closed) Pen.LOOP else Pen.OPEN), color,
        style = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** Water body from [surface] down to [bottom]: a still ripple plus a slosh wave of [amp] px. */
internal fun DrawScope.drawWater(surface: Float, amp: Float, phase: Float, bottom: Float) {
    val ripple = 1.6.dp.toPx()
    val w = size.width
    fun y(x: Float): Float {
        val t = x / w * 2f * Math.PI.toFloat()
        return surface + ripple * sin(t * 2.5f + 1f) + amp * sin(t * 1.1f + phase)
    }
    val line = Path().apply {
        moveTo(0f, y(0f))
        var x = 0f
        while (x < w) { x = (x + 3f).coerceAtMost(w); lineTo(x, y(x)) }
    }
    val body = Path().apply { addPath(line); lineTo(w, bottom + 1f); lineTo(0f, bottom + 1f); close() }
    drawPath(body, Brush.verticalGradient(listOf(Ink.Water, Ink.WaterDeep), startY = surface - amp, endY = bottom))
    drawPath(line, Ink.Paper.copy(alpha = 0.8f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
}

/** Wide shallow bowl from sketch 3; the water rises and sloshes once on first show. */
@Composable
fun SketchBowl(level: Float, modifier: Modifier = Modifier) {
    val water = remember { SloshState(0f) }
    LaunchedEffect(level) { water.to(level) }
    Canvas(modifier) {
        val rim = 0.26f
        val base = 0.76f
        val walls = List(15) { i ->
            val t = i / 14f * 2f - 1f
            val tt = t * t
            p(0.5f + t * 0.45f, rim + (base - rim) * (1f - tt * tt))
        }
        val inside = sketchPath(walls, 21, closed = true)
        val top = rim * size.height + 6.dp.toPx()
        val bottom = base * size.height
        clipPath(inside) {
            drawWater(bottom - (bottom - top) * water.level.value, water.amp.value.dp.toPx(), water.phase.value, bottom)
        }
        sketchStroke(walls, 21)
        sketchStroke(listOf(p(0.02f, rim), p(0.98f, rim)), 22)
        sketchStroke(listOf(p(0.34f, base - 0.02f), p(0.37f, 0.87f), p(0.63f, 0.87f), p(0.66f, base - 0.02f)), 23)
    }
}

/** Bell from sketch 4: smooth dome, flared lip, ring on top, clapper; faint ringing marks either side. Swings once when it first appears. */
@Composable
fun SketchBell(modifier: Modifier = Modifier) {
    val swing = remember { Animatable(0f) }
    LaunchedEffect(Unit) { swing.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 200f, visibilityThreshold = 0.5f), initialVelocity = 260f) }
    // Shapes are traced from the 450px mockup (bell centred on x=225, ring top at y=86) and scaled to the canvas.
    fun DrawScope.b(x: Float, y: Float) = p(0.5f + (x - 225f) * BELL, 0.07f + (y - 86f) * BELL)
    Canvas(
        modifier
            .drawBehind { // outside the rotating layer, so the ringing marks stay put while the bell swings
                val faint = Ink.Paper.copy(alpha = 0.45f)
                sketchStroke(listOf(b(168f, 124f), b(164.3f, 132.3f), b(163f, 141f), b(164f, 150f)), 35, faint, width = 2.4.dp)
                sketchStroke(listOf(b(282f, 124f), b(285.7f, 132.3f), b(287f, 141f), b(286f, 150f)), 36, faint, width = 2.4.dp)
            }
            .graphicsLayer {
                rotationZ = swing.value
                transformOrigin = TransformOrigin(0.5f, 0.08f)
            }
    ) {
        sketchStroke(
            listOf(
                b(190f, 160f), b(191.4f, 143.9f), b(194f, 127.4f), b(199.6f, 112.9f), b(210f, 103f), b(225f, 99.3f),
                b(240f, 103f), b(250.4f, 112.9f), b(256f, 127.4f), b(258.6f, 143.9f), b(260f, 160f),
            ),
            31, width = 2.4.dp,
        )
        // Lip: a shallow loop, out along the top, sharp turn at the right, back underneath and a touch past the start.
        sketchStroke(
            listOf(
                b(178f, 164f), b(201f, 160.5f), b(225f, 159.5f), b(249f, 160.5f), b(272f, 164f),
                b(249f, 167.5f), b(225f, 168.4f), b(201f, 167.5f), b(175f, 163.2f),
            ),
            32,
        )
        sketchStroke(ring(b(224.5f, 92f), size.width * 6f * BELL, fromDeg = 150f, n = 8), 33, closed = true, width = 2.4.dp)
        sketchStroke(listOf(b(218f, 172f), b(220.5f, 176.6f), b(225f, 178.4f), b(229.5f, 176.6f), b(232f, 172f)), 34)
    }
}

private const val BELL = 0.0094f // canvas fraction per mockup px

/** Three-tongued flame from sketch 7: a smooth teardrop with pointed tips; filled ember when lit, a faint outline when not. */
@Composable
fun SketchFlame(lit: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val outline = listOf(
            p(0.50f, 0.94f), p(0.32f, 0.90f), p(0.20f, 0.79f), p(0.15f, 0.64f), p(0.18f, 0.50f), p(0.22f, 0.38f),
            p(0.27f, 0.27f), p(0.32f, 0.40f), p(0.37f, 0.44f), p(0.41f, 0.27f), p(0.46f, 0.15f), p(0.52f, 0.05f),
            p(0.57f, 0.20f), p(0.60f, 0.31f), p(0.63f, 0.42f), p(0.70f, 0.36f), p(0.77f, 0.28f), p(0.82f, 0.42f),
            p(0.85f, 0.58f), p(0.82f, 0.75f), p(0.70f, 0.88f),
        )
        val w = if (size.minDimension < 24.dp.toPx()) 1.8.dp else 2.2.dp
        if (lit) {
            drawPath(sketchPath(outline, 41, closed = true), Ink.Ember)
            sketchStroke(outline, 41, Ink.Ember, closed = true, width = w)
        } else {
            sketchStroke(outline, 41, Ink.Ember.copy(alpha = 0.4f), closed = true, width = w)
        }
    }
}

/** Mars / Venus marks drawn by hand. */
@Composable
fun GenderGlyph(gender: Gender, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension * 0.24f
        if (gender == Gender.MALE) {
            sketchStroke(ring(p(0.40f, 0.60f), r, fromDeg = 200f, n = 10), 51, closed = true)
            sketchStroke(listOf(p(0.57f, 0.43f), p(0.84f, 0.16f)), 52)
            sketchStroke(listOf(p(0.62f, 0.16f), p(0.84f, 0.16f), p(0.84f, 0.38f)), 53)
        } else {
            sketchStroke(ring(p(0.50f, 0.36f), r, fromDeg = 160f, n = 10), 54, closed = true)
            sketchStroke(listOf(p(0.50f, 0.60f), p(0.50f, 0.94f)), 55)
            sketchStroke(listOf(p(0.32f, 0.78f), p(0.68f, 0.78f)), 56)
        }
    }
}

/** A hand-drawn pill button. [selected] fills it with ink; disabled turns it muted and inert. */
@Composable
fun SketchPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    style: TextStyle = display(15.sp),
) {
    val ink = if (enabled) Ink.Paper else Ink.Muted
    Box(
        modifier
            .clickable(enabled = enabled, role = Role.Button, interactionSource = null, indication = null, onClick = onClick)
            .drawBehind {
                val h = size.height
                val r = h / 2f
                val pts = ring(Offset(size.width - r, r), r, fromDeg = -90f, sweepDeg = 180f, n = 5) +
                    ring(Offset(r, r), r, fromDeg = 90f, sweepDeg = 180f, n = 5)
                val seed = text.hashCode()
                if (selected) drawPath(sketchPath(pts, seed, closed = true), ink)
                sketchStroke(pts, seed, ink, closed = true)
            }
            .padding(horizontal = 14.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style.copy(color = if (selected) Ink.Bg else ink))
    }
}
