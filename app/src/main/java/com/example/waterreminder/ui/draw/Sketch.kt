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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.example.waterreminder.domain.Gender
import com.example.waterreminder.ui.theme.Ink
import com.example.waterreminder.ui.theme.display
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

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

private data class SketchKey(val points: List<Offset>, val seed: Int, val closed: Boolean, val density: Float)

// Reusing the same Path instance every frame lets the renderer cache its geometry; rebuilding it each
// frame made every animation frame re-tessellate every outline on screen.
// ponytail: unbounded-by-design small LRU (128 shapes); main-thread only, which is where drawing happens.
private val sketchCache = object : LinkedHashMap<SketchKey, Path>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SketchKey, Path>?) = size > 128
}

/**
 * Hand-drawn path through [points]: segments are subdivided every ~10dp, every point is nudged by a
 * seeded random of at most 1.5dp (same seed = same wobble on every frame), then smoothed.
 * Cached: callers must not mutate the returned Path.
 */
fun DrawScope.sketchPath(points: List<Offset>, seed: Int, closed: Boolean = false): Path =
    sketchCache.getOrPut(SketchKey(points, seed, closed, density)) { buildSketchPath(points, seed, closed) }

private fun DrawScope.buildSketchPath(points: List<Offset>, seed: Int, closed: Boolean): Path {
    val step = 10.dp.toPx()
    val dense = ArrayList<Offset>()
    val segs = if (closed) points.size else points.size - 1
    for (i in 0 until segs) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        val k = (hypot(b.x - a.x, b.y - a.y) / step).toInt().coerceAtLeast(1)
        for (j in 0 until k) dense += a + (b - a) * (j / k.toFloat())
    }
    if (!closed) dense += points.last()
    val rnd = Random(seed)
    val j = 1.5.dp.toPx()
    val q = dense.map { it + Offset((rnd.nextFloat() * 2 - 1) * j, (rnd.nextFloat() * 2 - 1) * j) }
    fun mid(a: Offset, b: Offset) = (a + b) / 2f
    return Path().apply {
        if (closed) {
            val start = mid(q.last(), q[0])
            moveTo(start.x, start.y)
            for (i in q.indices) {
                val m = mid(q[i], q[(i + 1) % q.size])
                quadraticTo(q[i].x, q[i].y, m.x, m.y)
            }
            close()
        } else {
            moveTo(q[0].x, q[0].y)
            for (i in 1 until q.size - 1) {
                val m = mid(q[i], q[i + 1])
                quadraticTo(q[i].x, q[i].y, m.x, m.y)
            }
            lineTo(q.last().x, q.last().y)
        }
    }
}

fun DrawScope.sketchStroke(points: List<Offset>, seed: Int, color: Color = Ink.Paper, closed: Boolean = false) {
    drawPath(
        sketchPath(points, seed, closed), color,
        style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
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

/** Bell from sketch 4: dome, rim, knob and clapper. Swings once when it first appears. */
@Composable
fun SketchBell(modifier: Modifier = Modifier) {
    val swing = remember { Animatable(0f) }
    LaunchedEffect(Unit) { swing.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 200f, visibilityThreshold = 0.5f), initialVelocity = 260f) }
    Canvas(
        modifier.graphicsLayer {
            rotationZ = swing.value
            transformOrigin = TransformOrigin(0.5f, 0.08f)
        }
    ) {
        sketchStroke(
            listOf(
                p(0.17f, 0.74f), p(0.24f, 0.64f), p(0.27f, 0.46f), p(0.31f, 0.32f), p(0.40f, 0.22f),
                p(0.50f, 0.19f), p(0.60f, 0.22f), p(0.69f, 0.32f), p(0.73f, 0.46f), p(0.76f, 0.64f), p(0.83f, 0.74f),
            ),
            31,
        )
        sketchStroke(listOf(p(0.10f, 0.77f), p(0.50f, 0.79f), p(0.90f, 0.76f)), 32)
        sketchStroke(ring(p(0.5f, 0.12f), size.width * 0.05f, n = 6), 33, closed = true)
        sketchStroke(ring(p(0.5f, 0.88f), size.width * 0.06f, n = 6), 34, closed = true)
    }
}

/** Three-tongued flame from sketch 7: filled ember when lit, a faint outline when not. */
@Composable
fun SketchFlame(lit: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val outline = listOf(
            p(0.50f, 0.93f), p(0.31f, 0.89f), p(0.19f, 0.77f), p(0.16f, 0.60f), p(0.21f, 0.44f),
            p(0.27f, 0.30f), p(0.34f, 0.44f), p(0.41f, 0.32f), p(0.46f, 0.18f), p(0.51f, 0.06f),
            p(0.57f, 0.20f), p(0.62f, 0.33f), p(0.68f, 0.45f), p(0.75f, 0.30f), p(0.81f, 0.45f),
            p(0.85f, 0.61f), p(0.80f, 0.78f), p(0.68f, 0.89f),
        )
        if (lit) {
            drawPath(sketchPath(outline, 41, closed = true), Ink.Ember)
            sketchStroke(outline, 41, Ink.Ember, closed = true)
        } else {
            sketchStroke(outline, 41, Ink.Ember.copy(alpha = 0.4f), closed = true)
        }
    }
}

/** Mars / Venus marks drawn by hand. */
@Composable
fun GenderGlyph(gender: Gender, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension * 0.24f
        if (gender == Gender.MALE) {
            sketchStroke(ring(p(0.40f, 0.60f), r, n = 7), 51, closed = true)
            sketchStroke(listOf(p(0.57f, 0.43f), p(0.84f, 0.16f)), 52)
            sketchStroke(listOf(p(0.62f, 0.16f), p(0.84f, 0.16f), p(0.84f, 0.38f)), 53)
        } else {
            sketchStroke(ring(p(0.50f, 0.36f), r, n = 7), 54, closed = true)
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
