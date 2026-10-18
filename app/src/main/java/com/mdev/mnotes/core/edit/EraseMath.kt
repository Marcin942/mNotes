package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.geom.FloatList
import com.mdev.mnotes.core.geom.GeomMath
import com.mdev.mnotes.core.model.BoxElement
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.pen.LineStyle
import com.mdev.mnotes.core.pen.StrokeOutline
import kotlin.math.abs
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

    /** Wynik gumki pikselowej: pozostałe kawałki kreski i obszar strony, którego wygląd się zmienił. */
    class Erased(val pieces: List<FloatArray>, val dirty: Bounds)

    /**
     * Gumka pikselowa: usuwa z kreski wszystko, co leży w okręgu (cx, cy, r), i zwraca pozostałe kawałki
     * (jako tablice trójek x, y, p). Null = kreska nietknięta; pusta lista = skasowana w całości.
     */
    fun erasePartial(s: Stroke, cx: Float, cy: Float, r: Float): List<FloatArray>? = eraseLocal(s, cx, cy, r)?.pieces

    /**
     * Jak [erasePartial], ale dogęszcza tylko odcinki w zasięgu gumki, a resztę punktów zostawia bez zmian. Dzięki
     * temu kawałki poza okolicą gumki rysują się co do piksela tak samo jak kreska przed starciem i wystarczy
     * przemalować mały [Erased.dirty] zamiast całej kreski (na długim zakreślaczu to setki razy mniej pracy).
     * Gdzie wygląd kawałków mógłby się zmienić także daleko od gumki (wzór linii, łamana, inna średnia grubość,
     * kawałki nachodzące na siebie), [Erased.dirty] obejmuje całą kreskę.
     */
    fun eraseLocal(s: Stroke, cx: Float, cy: Float, r: Float): Erased? {
        if (!hitsStroke(s, cx, cy, r)) return null
        val p = s.pts
        val n = s.count
        val half = s.width * 0.5f
        if (n == 1) return Erased(emptyList(), s.bounds)
        var pMax = 0f
        for (i in 0 until n) pMax = max(pMax, p[3 * i + 2])
        val step = max(0.6f, r * 0.5f)
        val reach = r + 0.35f * half * pMax + step
        // Łamana z rozpoznanego kształtu rysuje się odcinkami, a kawałki krzywą: gęste punkty na całej długości
        // zachowują jej wygląd (kawałki tracą znacznik łamanej).
        val all = s.poly
        val src = FloatList(p.size + 64)
        val near = BooleanArray(n)
        src.add(p[0], p[1], p[2])
        for (i in 1 until n) {
            val x0 = p[3 * i - 3]; val y0 = p[3 * i - 2]; val p0 = p[3 * i - 1]
            val x1 = p[3 * i]; val y1 = p[3 * i + 1]; val p1 = p[3 * i + 2]
            if (all || GeomMath.distSqPointSegment(cx, cy, x0, y0, x1, y1) <= reach * reach) {
                near[i - 1] = true
                val k = (hypot(x1 - x0, y1 - y0) / step).toInt().coerceAtMost(400)
                for (j in 1..k) {
                    val t = j.toFloat() / (k + 1)
                    src.add(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, p0 + (p1 - p0) * t)
                }
            }
            src.add(x1, y1, p1)
        }
        val a = src.a
        val m = src.size / 3
        val out = ArrayList<FloatArray>()
        var removed = false
        var runStart = -1
        fun flush(endExcl: Int) {
            if (runStart >= 0 && endExcl - runStart >= 2) out.add(a.copyOfRange(3 * runStart, 3 * endExcl))
            runStart = -1
        }
        for (i in 0 until m) {
            val rad = r + 0.35f * half * a[3 * i + 2]
            val inside = hypot(a[3 * i] - cx, a[3 * i + 1] - cy) <= rad
            if (inside) { removed = true; flush(i) } else if (runStart < 0) runStart = i
        }
        flush(m)
        if (!removed) return null
        if (all || out.isEmpty()) return Erased(out, s.bounds)
        val dirty = localDirty(s, near, cx, cy, r, half * pMax)
        return Erased(out, if (needsFullRepaint(s, out, dirty)) s.bounds else dirty)
    }

    /**
     * Obszar zmieniony przez starcie: odcinki w zasięgu gumki z dwoma sąsiadami po obu stronach (od nich zależą
     * wygładzenie krzywej i normalne obrysu), poszerzone o pół grubości.
     */
    private fun localDirty(s: Stroke, near: BooleanArray, cx: Float, cy: Float, r: Float, halfMax: Float): Bounds {
        val p = s.pts
        val n = s.count
        var l = cx - r; var t = cy - r; var rr = cx + r; var b = cy + r
        for (i in 0 until n) {
            var hit = false
            for (k in i - 2..i + 1) if (k in 0 until n && near[k]) { hit = true; break }
            if (!hit) continue
            val x = p[3 * i]; val y = p[3 * i + 1]
            if (x < l) l = x
            if (x > rr) rr = x
            if (y < t) t = y
            if (y > b) b = y
        }
        val d = Bounds(l, t, rr, b).inflate(halfMax + 1f)
        return d.intersection(s.bounds.inflate(1f)) ?: d
    }

    private fun needsFullRepaint(s: Stroke, pieces: List<FloatArray>, dirty: Bounds): Boolean {
        if (s.line != LineStyle.SOLID) return true
        // Kreska o stałej grubości rysuje się średnią z nacisków: kawałek z inną średnią zmieniłby grubość na całej długości.
        val constant = StrokeOutline.isConstant(s.pts, s.count)
        val avg = avgPressure(s.pts)
        for (q in pieces) {
            if (StrokeOutline.isConstant(q) != constant) return true
            if (constant && abs(avgPressure(q) - avg) > 1e-4f) return true
        }
        // Półprzezroczyste pióro idzie jedną ścieżką, więc miejsce, gdzie kreska przecina samą siebie, nie ciemnieje;
        // po rozcięciu takie skrzyżowanie mogłoby leżeć daleko od gumki. Liczą się tylko punkty poza przemalowanym
        // obszarem: końce kawałków przy samym cięciu mogą na siebie zachodzić, ale ten obszar i tak jest malowany od nowa.
        if (pieces.size >= 2) {
            val half = s.width * 0.5f
            val bs = pieces.mapNotNull { outsideBounds(it, dirty)?.inflate(half) }
            for (i in bs.indices) for (j in i + 1 until bs.size) if (bs[i].intersects(bs[j])) return true
        }
        return false
    }

    /** Prostokąt punktów kawałka leżących poza [skip]; null, gdy cały kawałek jest w środku. */
    private fun outsideBounds(pts: FloatArray, skip: Bounds): Bounds? {
        var l = Float.POSITIVE_INFINITY; var t = l; var r = Float.NEGATIVE_INFINITY; var b = r
        for (i in 0 until pts.size / 3) {
            val x = pts[3 * i]; val y = pts[3 * i + 1]
            if (skip.contains(x, y)) continue
            if (x < l) l = x
            if (x > r) r = x
            if (y < t) t = y
            if (y > b) b = y
        }
        return if (l > r) null else Bounds(l, t, r, b)
    }

    private fun avgPressure(pts: FloatArray): Float {
        val n = pts.size / 3
        var sum = 0f
        for (i in 0 until n) sum += pts[3 * i + 2]
        return if (n == 0) 0f else sum / n
    }

    /** Dogęszcza trójki tak, by odstęp między sąsiednimi punktami nie przekraczał [maxStep]. */
    fun densify(pts: FloatArray, maxStep: Float): FloatArray {
        val n = pts.size / 3
        if (n < 2) return pts
        val out = FloatList(pts.size * 2)
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
