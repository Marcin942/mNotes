package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.geom.GeomMath
import com.mdev.mnotes.core.model.BoxElement
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.Stroke
import kotlin.math.hypot
import kotlin.math.max

/** Matematyka gumki i zaznaczania (lasso): czysta, bez Androida. */
object EraseMath {

    /** Czy okrąg (cx, cy, r) dotyka kreski (z uwzględnieniem jej grubości). */
    fun hitsStroke(s: Stroke, cx: Float, cy: Float, r: Float): Boolean {
        if (!s.bounds.inflate(r).contains(cx, cy)) return false
        val p = s.pts
        val n = s.count
        val half = s.width * 0.5f
        if (n == 1) return hypot(p[0] - cx, p[1] - cy) <= r + half * p[2]
        for (i in 0 until n - 1) {
            val rad = r + half * max(p[3 * i + 2], p[3 * i + 5])
            val d2 = GeomMath.distSqPointSegment(cx, cy, p[3 * i], p[3 * i + 1], p[3 * i + 3], p[3 * i + 4])
            if (d2 <= rad * rad) return true
        }
        return false
    }

    fun hitsElement(e: Element, cx: Float, cy: Float, r: Float): Boolean = when (e) {
        is Stroke -> hitsStroke(e, cx, cy, r)
        is BoxElement -> e.containsPoint(cx, cy, r)
        is FillEl -> e.containsPoint(cx, cy)
    }

    /**
     * Gumka pikselowa: usuwa z kreski wszystko, co leży w okręgu (cx, cy, r), i zwraca pozostałe kawałki
     * (jako tablice trójek x, y, p). Null = kreska nietknięta; pusta lista = skasowana w całości.
     */
    fun erasePartial(s: Stroke, cx: Float, cy: Float, r: Float): List<FloatArray>? {
        if (!hitsStroke(s, cx, cy, r)) return null
        val half = s.width * 0.5f
        val src = densify(s.pts, max(0.6f, r * 0.5f))
        val n = src.size / 3
        val out = ArrayList<FloatArray>()
        var runStart = -1
        fun flush(endExcl: Int) {
            if (runStart >= 0 && endExcl - runStart >= 2) out.add(src.copyOfRange(3 * runStart, 3 * endExcl))
            runStart = -1
        }
        for (i in 0 until n) {
            val rad = r + 0.35f * half * src[3 * i + 2]
            val inside = hypot(src[3 * i] - cx, src[3 * i + 1] - cy) <= rad
            if (inside) flush(i) else if (runStart < 0) runStart = i
        }
        flush(n)
        return out
    }

    /** Dogęszcza trójki tak, by odstęp między sąsiednimi punktami nie przekraczał [maxStep]. */
    fun densify(pts: FloatArray, maxStep: Float): FloatArray {
        val n = pts.size / 3
        if (n < 2) return pts
        val out = com.mdev.mnotes.core.geom.FloatList(pts.size * 2)
        out.add(pts[0], pts[1], pts[2])
        for (i in 1 until n) {
            val x0 = pts[3 * i - 3]; val y0 = pts[3 * i - 2]; val p0 = pts[3 * i - 1]
            val x1 = pts[3 * i]; val y1 = pts[3 * i + 1]; val p1 = pts[3 * i + 2]
            val d = hypot(x1 - x0, y1 - y0)
            val k = (d / maxStep).toInt().coerceAtMost(400)
            for (j in 1..k) {
                val t = j.toFloat() / (k + 1)
                out.add(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, p0 + (p1 - p0) * t)
            }
            out.add(x1, y1, p1)
        }
        return out.toArray()
    }

    /** Wielokąt zaznaczenia lasso: parzysto-nieparzysta reguła przecięć promienia. */
    fun polygonContains(poly: FloatArray, x: Float, y: Float): Boolean {
        val n = poly.size / 2
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

    /** Kreska i wypełnienie wpadają do zaznaczenia, gdy co najmniej połowa ich punktów leży w wielokącie; ramki — gdy środek. */
    fun selectedByLasso(e: Element, poly: FloatArray): Boolean = when (e) {
        is Stroke -> {
            var inside = 0
            val n = e.count
            val step = max(1, n / 48)
            var cnt = 0
            var i = 0
            while (i < n) {
                if (polygonContains(poly, e.pts[3 * i], e.pts[3 * i + 1])) inside++
                cnt++
                i += step
            }
            inside * 2 >= cnt
        }
        is BoxElement -> polygonContains(poly, e.cx, e.cy)
        is FillEl -> {
            var inside = 0; var cnt = 0
            for (ring in e.rings) {
                val n = ring.size / 2
                val step = max(1, n / 48)
                var i = 0
                while (i < n) { if (polygonContains(poly, ring[2 * i], ring[2 * i + 1])) inside++; cnt++; i += step }
            }
            cnt > 0 && inside * 2 >= cnt
        }
    }
}
