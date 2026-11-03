package com.example.waterreminder.ui.draw

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.waterreminder.ui.theme.Ink
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Which bottom corner the button starts in. Angles are degrees clockwise from 12 o'clock. */
enum class ArcSide(val start: Float, val dir: Int) { LEFT(225f, 1), RIGHT(135f, -1) }

const val SWIPE_SPAN_DEG = 90f
const val SWIPE_THRESHOLD = 0.65f

/** How far along its track (0..1) a finger at [angleDeg] puts the knob. Overshooting past 12 o'clock counts as all the way. */
fun arcProgress(angleDeg: Float, side: ArcSide): Float {
    if (side == ArcSide.LEFT && angleDeg < 90f || side == ArcSide.RIGHT && angleDeg > 270f) return 1f
    return ((angleDeg - side.start) * side.dir / SWIPE_SPAN_DEG).coerceIn(0f, 1f)
}

/**
 * Alarm-style button: a knob in a bottom corner that is dragged up a curved track along the screen edge.
 * Let go past the tick ([SWIPE_THRESHOLD]) and it runs to the end and fires [onTrigger]; before it, it springs back.
 * Fills the parent (the track needs the whole circle); only the knob takes touches. No haptics.
 * For TalkBack the knob is a button named [label] whose click fires it straight away.
 */
@Composable
fun ArcSwipeButton(side: ArcSide, color: Color, label: String, onTrigger: () -> Unit, glyph: DrawScope.() -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val trigger by rememberUpdatedState(onTrigger)
    suspend fun fire() { progress.animateTo(1f, tween(120)); trigger() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val knobPx = with(LocalDensity.current) { KNOB.toPx() }
        val c = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)
        val r = minOf(c.x, c.y) * RADIUS
        fun at(p: Float): Offset {
            val a = Math.toRadians((side.start + side.dir * SWIPE_SPAN_DEG * p).toDouble())
            return Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat())
        }

        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
            // ring() counts from 3 o'clock, so 12-o'clock angles shift by -90.
            val track = sketchPath(ring(c, r, fromDeg = side.start - 90f, sweepDeg = side.dir * SWIPE_SPAN_DEG, n = 9), 80 + side.ordinal)
            drawPath(track, Ink.Track, style = stroke)
            val p = progress.value.coerceIn(0f, 1f)
            if (p > 0.005f) {
                val m = PathMeasure().apply { setPath(track, false) }
                val fill = Path().also { m.getSegment(0f, m.length * p, it, true) }
                drawPath(fill, color.copy(alpha = 0.85f), style = stroke)
            }
            val a = Math.toRadians((side.start + side.dir * SWIPE_SPAN_DEG * SWIPE_THRESHOLD).toDouble())
            val dir = Offset(sin(a).toFloat(), -cos(a).toFloat())
            val t = 11.dp.toPx()
            sketchStroke(listOf(c + dir * (r - t), c + dir * (r + t)), 82 + side.ordinal, Ink.Muted)
        }

        Box(
            Modifier
                .offset { at(progress.value).let { IntOffset((it.x - knobPx / 2).roundToInt(), (it.y - knobPx / 2).roundToInt()) } }
                .size(KNOB)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                    onClick(label) { scope.launch { fire() }; true }
                }
                .pointerInput(side, r) {
                    var finger = Offset.Zero
                    fun release() = scope.launch {
                        if (progress.value >= SWIPE_THRESHOLD) {
                            fire()
                        } else {
                            progress.animateTo(0f, spring(dampingRatio = 0.5f, visibilityThreshold = 0.002f))
                        }
                    }
                    detectDragGestures(
                        onDragStart = { finger = at(progress.value) - Offset(knobPx / 2, knobPx / 2) + it },
                        onDragEnd = { release() },
                        onDragCancel = { release() },
                    ) { change, drag ->
                        change.consume()
                        finger += drag
                        val deg = (Math.toDegrees(atan2((finger.x - c.x).toDouble(), (c.y - finger.y).toDouble())) + 360.0) % 360.0
                        scope.launch { progress.snapTo(arcProgress(deg.toFloat(), side)) }
                    }
                }
                .drawBehind {
                    val disc = DISC.toPx() / 2f
                    drawCircle(Ink.Bg, disc)
                    drawCircle(color.copy(alpha = 0.16f), disc)
                    sketchStroke(ring(center, disc - 2.dp.toPx(), fromDeg = side.start, n = 9), 84 + side.ordinal, color, closed = true)
                    glyph()
                },
        )
    }
}

private val KNOB = 58.dp // touch target
// Visible disc: a 58dp disc on the 0.81 radius pokes ~9dp past a 450px round screen, so it's drawn smaller.
private val DISC = 44.dp
private const val RADIUS = 0.81f
