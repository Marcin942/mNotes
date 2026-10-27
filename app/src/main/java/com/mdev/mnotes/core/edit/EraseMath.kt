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
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Matematyka gumki i zaznaczania (lasso): czysta, bez Androida. */
object EraseMath {

    /** Czy okrąg (cx, cy, r) dotyka kreski (z uwzględnieniem jej grubości). */
    fun hitsStroke(s: Stroke, cx: Float, cy: Float, r: Float): Boolean = hitsStrokeSwept(s, cx, cy, cx, cy, r)

    /**
     * Czy „kapsuła” — okrąg o promieniu [r] przesunięty od (ax, ay) do (bx, by) — dotyka kreski. Gumka liczy tak cały
     * ruch między dwoma zdarzeniami naraz, zamiast dziesiątek kół co ułamek promienia (B1: zacinanie przy mazaniu).
     */
    fun hitsStrokeSwept(s: Stroke, ax: Float, ay: Float, bx: Float, by: Float, r: Float): Boolean {
        val qx0 = min(ax, bx); val qy0 = min(ay, by); val qx1 = max(ax, bx); val qy1 = max(ay, by)
        if (!s.bounds.inflate(r).intersects(qx0, qy0, qx1, qy1)) return false
        val p = s.pts
        val n = s.count
        val half = s.width * 0.5f
        if (n == 1) {
            val rad = r + half * p[2]
            return GeomMath.distSqPointSegment(p[0], p[1], ax, ay, bx, by) <= rad * rad
        }
        for (i in 0 until n - 1) {
            val x0 = p[3 * i]; val y0 = p[3 * i + 1]; val x1 = p[3 * i + 3]; val y1 = p[3 * i + 4]
            val rad = r + half * max(p[3 * i + 2], p[3 * i + 5])
            if (min(x0, x1) > qx1 + rad || max(x0, x1) < qx0 - rad || min(y0, y1) > qy1 + rad || max(y0, y1) < qy0 - rad) continue
            if (GeomMath.distSqSegmentSegment(x0, y0, x1, y1, ax, ay, bx, by) <= rad * rad) return true
        }
        return false
    }

    fun hitsElement(e: Element, cx: Float, cy: Float, r: Float): Boolean = when (e) {
        is Stroke -> hitsStroke(e, cx, cy, r)
        is BoxElement -> e.containsPoint(cx, cy, r)
        is FillEl -> e.containsPoint(cx, cy)
    }

    /** [hitsElement] dla całego ruchu od (ax, ay) do (bx, by); ramki i wypełnienia sprawdzane są kołami co ułamek promienia. */
    fun hitsElementSwept(e: Element, ax: Float, ay: Float, bx: Float, by: Float, r: Float): Boolean {
        if (e is Stroke) return hitsStrokeSwept(e, ax, ay, bx, by, r)
        val steps = ceil(hypot(bx - ax, by - ay) / max(0.5f, r * 0.6f)).toInt().coerceIn(0, 4000)
        for (k in 0..steps) {
            val f = if (steps == 0) 0f else k.toFloat() / steps
            if (hitsElement(e, ax + (bx - ax) * f, ay + (by - ay) * f, r)) return true
        }
        return false
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
    fun eraseLocal(s: Stroke, cx: Float, cy: Float, r: Float): Erased? = eraseSwept(s, cx, cy, cx, cy, r)

    /** [eraseLocal] dla całego ruchu gumki od (ax, ay) do (bx, by) naraz (kapsuła, jak w [hitsStrokeSwept]). */
    fun eraseSwept(s: Stroke, ax: Float, ay: Float, bx: Float, by: Float, r: Float): Erased? {
        if (!hitsStrokeSwept(s, ax, ay, bx, by, r)) return null
        val p = s.pts
        val n = s.count
        val half = s.width * 0.5f
        if (n == 1) return Erased(emptyList(), s.bounds)
        var pMax = 0f
        for (i in 0 until n) pMax = max(pMax, p[3 * i + 2])
        val step = max(0.6f, r * 0.5f)
        val reach = r + 0.35f * half * pMax + step
        val qx0 = min(ax, bx) - reach; val qy0 = min(ay, by) - reach; val qx1 = max(ax, bx) + reach; val qy1 = max(ay, by) + reach
        // Łamana z rozpoznanego kształtu rysuje się odcinkami, a kawałki krzywą: gęste punkty na całej długości
        // zachowują jej wygląd (kawałki tracą znacznik łamanej).
        val all = s.poly
        val src = FloatList(p.size + 64)
        val near = BooleanArray(n)
        src.add(p[0], p[1], p[2])
        for (i in 1 until n) {
            val x0 = p[3 * i - 3]; val y0 = p[3 * i - 2]; val p0 = p[3 * i - 1]
            val x1 = p[3 * i]; val y1 = p[3 * i + 1]; val p1 = p[3 * i + 2]
            val close = !(min(x0, x1) > qx1 || max(x0, x1) < qx0 || min(y0, y1) > qy1 || max(y0, y1) < qy0) &&
                GeomMath.distSqSegmentSegment(x0, y0, x1, y1, ax, ay, bx, by) <= reach * reach
            if (all || close) {
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
            val x = a[3 * i]; val y = a[3 * i + 1]
            val rad = r + 0.35f * half * a[3 * i + 2]
            val inside = x >= qx0 && x <= qx1 && y >= qy0 && y <= qy1 && GeomMath.distSqPointSegment(x, y, ax, ay, bx, by) <= rad * rad
            if (inside) { removed = true; flush(i) } else if (runStart < 0) runStart = i
        }
        flush(m)
        if (!removed) return null
        if (all || out.isEmpty()) return Erased(out, s.bounds)
        val dirty = localDirty(s, near, min(ax, bx) - r, min(ay, by) - r, max(ax, bx) + r, max(ay, by) + r, half * pMax)
        return Erased(out, if (needsFullRepaint(s, out, dirty)) s.bounds else dirty)
    }

    /**
     * Obszar zmieniony przez starcie: odcinki w zasięgu gumki z dwoma sąsiadami po obu stronach (od nich zależą
     * wygładzenie krzywej i normalne obrysu), poszerzone o pół grubości.
     */
    private fun localDirty(s: Stroke, near: BooleanArray, l0: Float, t0: Float, r0: Float, b0: Float, halfMax: Float): Bounds {
        val p = s.pts
        val n = s.count
        var l = l0; var t = t0; var rr = r0; var b = b0
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

    /**
     * Wielokąt zaznaczenia lasso, reguła niezerowego nawinięcia. Pętla, która przecina samą siebie (dwa nachodzące
     * okrążenia), obejmuje też część wspólną; reguła parzysto-nieparzysta ją wycinała (B7).
     */
    fun polygonContains(poly: FloatArray, x: Float, y: Float): Boolean {
        val n = poly.size / 2
        var wn = 0
        var j = n - 1
        for (i in 0 until n) {
            val xj = poly[2 * j]; val yj = poly[2 * j + 1]
            val xi = poly[2 * i]; val yi = poly[2 * i + 1]
            val side = (xi - xj) * (y - yj) - (x - xj) * (yi - yj)
            if (yj <= y) { if (yi > y && side > 0f) wn++ } else if (yi <= y && side < 0f) wn--
            j = i
        }
        return wn != 0
    }

    /**
     * Punkty kreski rozłożone równo po długości (bez samych końców): prosta z dwoma punktami ma tyle samo próbek co
     * pismo, więc linia od rogu do rogu ramki prostokąta trafia do zaznaczenia (B3, B10).
     */
    fun samplesAlong(e: Stroke, count: Int = 48): FloatArray {
        val p = e.pts
        val n = e.count
        if (n <= 1) return if (n == 1) floatArrayOf(p[0], p[1]) else FloatArray(0)
        var total = 0f
        for (i in 1 until n) total += hypot(p[3 * i] - p[3 * i - 3], p[3 * i + 1] - p[3 * i - 2])
        if (total <= 1e-6f) return floatArrayOf(p[0], p[1])
        val out = FloatArray(2 * count)
        var seg = 1
        var acc = 0f
        var segLen = hypot(p[3] - p[0], p[4] - p[1])
        for (k in 0 until count) {
            val target = total * (k + 0.5f) / count
            while (acc + segLen < target && seg < n - 1) {
                acc += segLen; seg++
                segLen = hypot(p[3 * seg] - p[3 * seg - 3], p[3 * seg + 1] - p[3 * seg - 2])
            }
            val t = if (segLen <= 1e-6f) 0f else ((target - acc) / segLen).coerceIn(0f, 1f)
            out[2 * k] = p[3 * seg - 3] + (p[3 * seg] - p[3 * seg - 3]) * t
            out[2 * k + 1] = p[3 * seg - 2] + (p[3 * seg + 1] - p[3 * seg - 2]) * t
        }
        return out
    }

    /**
     * Lasso „tylko zahacz” (F1): obiekt trafia do zaznaczenia, gdy choć kawałek leży w pętli albo pętla go musnęła
     * (przeszła w odległości [slop] od kreski, przez ramkę, przez wypełnienie). Zbalansowane to [selectedByLasso].
     */
    fun touchedByLasso(e: Element, poly: FloatArray, slop: Float): Boolean {
        val n = poly.size / 2
        if (n == 0) return false
        val lb = Bounds.ofPoints(poly).inflate(slop)
        if (!lb.intersects(e.bounds)) return false
        when (e) {
            is Stroke -> {
                val s = samplesAlong(e)
                for (k in 0 until s.size / 2) if (polygonContains(poly, s[2 * k], s[2 * k + 1])) return true
                for (i in 0 until n) {
                    val j = (i + 1) % n
                    if (hitsStrokeSwept(e, poly[2 * i], poly[2 * i + 1], poly[2 * j], poly[2 * j + 1], slop)) return true
                }
                return false
            }
            is BoxElement -> {
                if (polygonContains(poly, e.cx, e.cy)) return true
                val b = e.bounds
                if (polygonContains(poly, b.l, b.t) || polygonContains(poly, b.r, b.t) || polygonContains(poly, b.r, b.b) || polygonContains(poly, b.l, b.b)) return true
                for (i in 0 until n) {
                    val j = (i + 1) % n
                    if (hitsElementSwept(e, poly[2 * i], poly[2 * i + 1], poly[2 * j], poly[2 * j + 1], slop)) return true
                }
                return false
            }
            is FillEl -> {
                for (ring in e.rings) for (k in 0 until ring.size / 2) if (polygonContains(poly, ring[2 * k], ring[2 * k + 1])) return true
                for (i in 0 until n) if (e.containsPoint(poly[2 * i], poly[2 * i + 1])) return true
                return false
            }
        }
    }

    /** Zaznaczenie lassem wg trybu: [touch] = „tylko zahacz”, inaczej zbalansowane. */
    fun lassoSelects(e: Element, poly: FloatArray, touch: Boolean, slop: Float): Boolean =
        if (touch) touchedByLasso(e, poly, slop) else selectedByLasso(e, poly)

    /** Kreska i wypełnienie wpadają do zaznaczenia, gdy co najmniej połowa ich długości leży w wielokącie; ramki — gdy środek. */
    fun selectedByLasso(e: Element, poly: FloatArray): Boolean = when (e) {
        is Stroke -> {
            val s = samplesAlong(e)
            var inside = 0
            val cnt = s.size / 2
            for (k in 0 until cnt) if (polygonContains(poly, s[2 * k], s[2 * k + 1])) inside++
            cnt > 0 && inside * 2 >= cnt
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
