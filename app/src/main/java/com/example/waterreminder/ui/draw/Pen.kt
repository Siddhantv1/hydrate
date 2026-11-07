package com.example.waterreminder.ui.draw

import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** How a line through control points is drawn: an open stroke, a closed fill outline, or a closed stroke as a pen loop. */
internal enum class Pen { OPEN, FILL, LOOP }

private const val CORNER_COS = 0.34f   // a turn sharper than ~70 degrees stays a corner
private const val LOOP_OVERRUN = 0.10f // pen loops run on past their start by this share of the perimeter
private const val TAU = 6.2831855f

/**
 * One confident pen stroke through [points], as cubic Béziers packed `[x0, y0, (c1x, c1y, c2x, c2y, x, y)...]`.
 *
 * Smooth runs are a centripetal Catmull-Rom spline through the points; a turn sharper than ~70 degrees stays a
 * corner. The only irregularity is a seeded, slow wave (at most 0.6dp, wavelength 70-95dp) so it reads as a steady
 * hand; same seed gives the same shape. [Pen.LOOP] runs ~10% of the perimeter past its start, drifting inward.
 * [dp] is pixels per dp.
 */
internal fun penCurve(points: List<Offset>, seed: Int, pen: Pen, dp: Float): FloatArray {
    val n = points.size
    if (n < 2) return points.firstOrNull()?.let { floatArrayOf(it.x, it.y) } ?: FloatArray(0)
    val closed = pen != Pen.OPEN
    val corner = BooleanArray(n) { i ->
        if (!closed && (i == 0 || i == n - 1)) true else {
            val u = points[i] - points[(i + n - 1) % n]
            val w = points[(i + 1) % n] - points[i]
            val d = u.getDistance() * w.getDistance()
            d > 0f && (u.x * w.x + u.y * w.y) / d < CORNER_COS
        }
    }
    // Straight corner-to-corner strokes get in-between points so the slow wave can bow them a touch.
    val v = ArrayList<Offset>(n * 2)
    val c = ArrayList<Boolean>(n * 2)
    val step = 18f * dp
    for (i in 0 until if (closed) n else n - 1) {
        val j = (i + 1) % n
        val a = points[i]
        val b = points[j]
        v += a; c += corner[i]
        if (corner[i] && corner[j]) {
            val k = ((b - a).getDistance() / step).toInt()
            for (m in 1..k) { v += a + (b - a) * (m / (k + 1f)); c += false }
        }
    }
    if (!closed) { v += points[n - 1]; c += true }
    val m = v.size
    val s = FloatArray(m)
    for (i in 1 until m) s[i] = s[i - 1] + (v[i] - v[i - 1]).getDistance()
    val total = if (closed) s[m - 1] + (v[0] - v[m - 1]).getDistance() else s[m - 1]
    if (total < 1e-3f) return floatArrayOf(v[0].x, v[0].y)

    val rnd = Random(seed)
    val amp = 0.42f * dp * (total / (40f * dp)).coerceIn(0.25f, 1f) // per axis, so at most ~0.6dp
    // Closed shapes get a wave that repeats exactly once round, so a loop meets itself.
    fun k(wavelength: Float) = TAU / if (closed) total / max(1f, round(total / wavelength)) else wavelength
    val kx = k(70f * dp)
    val ky = k(95f * dp)
    val fx = rnd.nextFloat() * TAU
    val fy = rnd.nextFloat() * TAU
    fun wob(p: Offset, at: Float) = Offset(p.x + amp * sin(at * kx + fx), p.y + amp * sin(at * ky + fy))

    if (pen != Pen.LOOP) return spline(List(m) { wob(v[it], s[it]) }, c, wrap = closed, headPrev = null)

    // Pen loop: go round once, then carry on over the start, easing inward by up to 2dp.
    val over = total * LOOP_OVERRUN
    var centre = Offset.Zero
    points.forEach { centre += it }
    centre /= n.toFloat()
    var radii = 0f
    points.forEach { radii += (it - centre).getDistance() }
    val gap = min(2f * dp, 0.15f * radii / n) // 15% of the mean radius, so small loops drift less
    val q = ArrayList<Offset>(m + 8)
    val qc = ArrayList<Boolean>(m + 8)
    val qs = ArrayList<Float>(m + 8)
    for (i in 0 until m) { q += wob(v[i], s[i]); qc += c[i]; qs += s[i] }
    var i = 0
    var at = total
    while (true) {
        val p = v[i % m]
        val u = ((at - total) / over).coerceIn(0f, 1f)
        val toC = centre - p
        val d = toC.getDistance()
        q += wob(p, at) + if (d > 0f) toC * (gap * u * u * (3f - 2f * u) / d) else Offset.Zero
        qc += c[i % m]; qs += at
        if (at - total >= over) break
        at += (v[(i + 1) % m] - p).getDistance()
        i++
    }
    val out = spline(q, qc, wrap = false, headPrev = if (c[0]) null else q[m - 1])
    // Cut the last segment where the overrun ends (chord-length parameter is close enough on a short span).
    val last = qs.size - 1
    val t = ((total + over - qs[last - 1]) / (qs[last] - qs[last - 1])).coerceIn(0f, 1f)
    splitLast(out, t)
    return out
}

/** Catmull-Rom (centripetal) through [q] as cubic Béziers; corners and open ends take the chord as their tangent. */
private fun spline(q: List<Offset>, corner: List<Boolean>, wrap: Boolean, headPrev: Offset?): FloatArray {
    val n = q.size
    val segs = if (wrap) n else n - 1
    val out = FloatArray(2 + segs * 6)
    out[0] = q[0].x; out[1] = q[0].y
    for (i in 0 until segs) {
        val j = (i + 1) % n
        val p1 = q[i]
        val p2 = q[j]
        val p0 = when {
            corner[i] -> p1 * 2f - p2
            i == 0 && !wrap -> headPrev ?: (p1 * 2f - p2)
            else -> q[(i + n - 1) % n]
        }
        val p3 = if (corner[j] || !wrap && j == n - 1) p2 * 2f - p1 else q[(j + 1) % n]
        val d0 = sqrt((p1 - p0).getDistance()).coerceAtLeast(1e-3f)
        val d1 = sqrt((p2 - p1).getDistance()).coerceAtLeast(1e-3f)
        val d2 = sqrt((p3 - p2).getDistance()).coerceAtLeast(1e-3f)
        val m1 = (p1 - p0) / d0 - (p2 - p0) / (d0 + d1) + (p2 - p1) / d1
        val m2 = (p2 - p1) / d1 - (p3 - p1) / (d1 + d2) + (p3 - p2) / d2
        val c1 = p1 + m1 * (d1 / 3f)
        val c2 = p2 - m2 * (d1 / 3f)
        val o = 2 + i * 6
        out[o] = c1.x; out[o + 1] = c1.y; out[o + 2] = c2.x; out[o + 3] = c2.y; out[o + 4] = p2.x; out[o + 5] = p2.y
    }
    return out
}

/** Keeps the first [t] of the last cubic in [out] (de Casteljau). */
private fun splitLast(out: FloatArray, t: Float) {
    val o = out.size - 6
    for (axis in 0..1) {
        val p0 = out[o - 2 + axis]
        val p1 = out[o + axis]
        val p2 = out[o + 2 + axis]
        val p3 = out[o + 4 + axis]
        val a = p0 + (p1 - p0) * t
        val b = p1 + (p2 - p1) * t
        val cc = p2 + (p3 - p2) * t
        val d = a + (b - a) * t
        val e = b + (cc - b) * t
        out[o + axis] = a
        out[o + 2 + axis] = d
        out[o + 4 + axis] = d + (e - d) * t
    }
}
