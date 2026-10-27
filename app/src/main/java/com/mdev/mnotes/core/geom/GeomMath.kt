package com.mdev.mnotes.core.geom

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Czysta geometria 2D używana przez gumkę, lasso i rozpoznawanie kształtów. */
object GeomMath {

    fun distSq(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by
        return dx * dx + dy * dy
    }

    fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float = sqrt(distSq(ax, ay, bx, by))

    fun distSqPointSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 <= 1e-12f) return distSq(px, py, ax, ay)
        var t = ((px - ax) * dx + (py - ay) * dy) / len2
        if (t < 0f) t = 0f else if (t > 1f) t = 1f
        return distSq(px, py, ax + t * dx, ay + t * dy)
    }

    /** Kwadrat najmniejszej odległości dwóch odcinków (0, gdy się przecinają). */
    fun distSqSegmentSegment(
        ax: Float, ay: Float, bx: Float, by: Float,
        cx: Float, cy: Float, dx: Float, dy: Float,
    ): Float {
        if (segmentsIntersect(ax, ay, bx, by, cx, cy, dx, dy)) return 0f
        return min(
            min(distSqPointSegment(ax, ay, cx, cy, dx, dy), distSqPointSegment(bx, by, cx, cy, dx, dy)),
            min(distSqPointSegment(cx, cy, ax, ay, bx, by), distSqPointSegment(dx, dy, ax, ay, bx, by)),
        )
    }

    /** Reguła parzystości; [poly] to pary x,y, [n] liczba wierzchołków. */
    fun pointInPolygon(x: Float, y: Float, poly: FloatArray, n: Int): Boolean {
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val xi = poly[2 * i]; val yi = poly[2 * i + 1]
            val xj = poly[2 * j]; val yj = poly[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    fun segmentsIntersect(
        ax: Float, ay: Float, bx: Float, by: Float,
        cx: Float, cy: Float, dx: Float, dy: Float,
    ): Boolean {
        val d1 = cross(cx, cy, dx, dy, ax, ay)
        val d2 = cross(cx, cy, dx, dy, bx, by)
        val d3 = cross(ax, ay, bx, by, cx, cy)
        val d4 = cross(ax, ay, bx, by, dx, dy)
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true
        return (d1 == 0f && onSeg(cx, cy, dx, dy, ax, ay)) || (d2 == 0f && onSeg(cx, cy, dx, dy, bx, by)) ||
            (d3 == 0f && onSeg(ax, ay, bx, by, cx, cy)) || (d4 == 0f && onSeg(ax, ay, bx, by, dx, dy))
    }

    private fun cross(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float): Float =
        (bx - ax) * (py - ay) - (by - ay) * (px - ax)

    private fun onSeg(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float): Boolean =
        px >= min(ax, bx) && px <= max(ax, bx) && py >= min(ay, by) && py <= max(ay, by)

    fun polylineLength(xy: FloatArray, n: Int): Float {
        var s = 0f
        for (i in 1 until n) s += dist(xy[2 * i - 2], xy[2 * i - 1], xy[2 * i], xy[2 * i + 1])
        return s
    }

    /**
     * Ramer–Douglas–Peucker. Zwraca indeksy zachowanych wierzchołków (rosnąco, z pierwszym i ostatnim).
     */
    fun simplify(xy: FloatArray, n: Int, epsilon: Float): IntArray {
        if (n <= 2) return IntArray(n) { it }
        val keep = BooleanArray(n)
        keep[0] = true; keep[n - 1] = true
        val eps2 = epsilon * epsilon
        val stack = IntArray(2 * n)
        var sp = 0
        stack[sp++] = 0; stack[sp++] = n - 1
        while (sp > 0) {
            val hi = stack[--sp]; val lo = stack[--sp]
            var worst = -1f; var idx = -1
            for (i in lo + 1 until hi) {
                val d = distSqPointSegment(xy[2 * i], xy[2 * i + 1], xy[2 * lo], xy[2 * lo + 1], xy[2 * hi], xy[2 * hi + 1])
                if (d > worst) { worst = d; idx = i }
            }
            if (idx >= 0 && worst > eps2) {
                keep[idx] = true
                stack[sp++] = lo; stack[sp++] = idx
                stack[sp++] = idx; stack[sp++] = hi
            }
        }
        var c = 0
        for (k in keep) if (k) c++
        val out = IntArray(c)
        var o = 0
        for (i in 0 until n) if (keep[i]) out[o++] = i
        return out
    }

    fun angleBetween(ux: Float, uy: Float, vx: Float, vy: Float): Float {
        val lu = sqrt(ux * ux + uy * uy); val lv = sqrt(vx * vx + vy * vy)
        if (lu < 1e-6f || lv < 1e-6f) return 0f
        val cs = ((ux * vx + uy * vy) / (lu * lv)).coerceIn(-1f, 1f)
        return kotlin.math.acos(cs)
    }

    fun approxEq(a: Float, b: Float, tol: Float): Boolean = abs(a - b) <= tol
}
