package com.mdev.mnotes.core.shapes

import com.mdev.mnotes.core.geom.GeomMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class ShapeKind { LINE, ELLIPSE, RECT, TRIANGLE, ARROW, POLYLINE }

/**
 * Wynik rozpoznania: rodzaj i gotowa łamana (x, y …) do zapisania jako kreska.
 * [poly] = wszystkie punkty to wierzchołki prostych odcinków (da się je potem przesuwać); elipsa jest gęstą krzywą.
 */
class Shape(val kind: ShapeKind, val xy: FloatArray) {
    val poly: Boolean get() = kind != ShapeKind.ELLIPSE
}

/**
 * Autokorekta „rysuj i przytrzymaj”: z odręcznej łamanej (x, y …, w pt) wykrywa prostą linię, elipsę, prostokąt,
 * trójkąt i strzałkę. Zwraca null, gdy kształt jest niejednoznaczny — wtedy zostaje odręczna kreska.
 */
object ShapeRecognizer {

    fun recognize(xyIn: FloatArray, nIn: Int = xyIn.size / 2): Shape? {
        // Gęste próbki rysika z drżeniem: wyrzucamy punkty bliższe niż 1 pt od poprzedniego.
        val xy = FloatArray(2 * nIn)
        var n = 0
        for (i in 0 until nIn) {
            if (n > 0 && GeomMath.dist(xy[2 * n - 2], xy[2 * n - 1], xyIn[2 * i], xyIn[2 * i + 1]) < 1f) continue
            xy[2 * n] = xyIn[2 * i]; xy[2 * n + 1] = xyIn[2 * i + 1]; n++
        }
        if (n < 4) return null
        val len = GeomMath.polylineLength(xy, n)
        if (len < 18f) return null
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in 0 until n) {
            l = min(l, xy[2 * i]); r = max(r, xy[2 * i]); t = min(t, xy[2 * i + 1]); b = max(b, xy[2 * i + 1])
        }
        val diag = hypot(r - l, b - t)
        if (diag < 14f) return null
        val gap = GeomMath.dist(xy[0], xy[1], xy[2 * n - 2], xy[2 * n - 1])
        val closed = gap < 0.22f * len && len > 2.2f * gap

        if (!closed) {
            line(xy, n, len, diag)?.let { return it }
            arrow(xy, n, len)?.let { return it }
            return polyline(xy, n, diag, false)
        }
        // Kanciaste kształty mają pierwszeństwo przed elipsą (pięciokąt, gwiazda przechodzą test elipsy);
        // gładkie krzywe odpadają w teście ostrości rogów.
        polyline(xy, n, diag, true)?.let { return it }
        return ellipse(xy, n, l, t, r, b)
    }

    private fun line(xy: FloatArray, n: Int, len: Float, diag: Float): Shape? {
        if (len > 1.35f * diag) return null
        val idx = GeomMath.simplify(xy, n, max(2.5f, 0.045f * len))
        if (idx.size != 2) return null
        var x1 = xy[0]; var y1 = xy[1]; var x2 = xy[2 * n - 2]; var y2 = xy[2 * n - 1]
        // Przyciągnięcie do 0°, 45°, 90° w granicach 4°.
        val ang = atan2(y2 - y1, x2 - x1)
        val step = (PI / 4).toFloat()
        val snapped = (ang / step).roundToInt() * step
        if (abs(ang - snapped) < Math.toRadians(4.0)) {
            val l = hypot(x2 - x1, y2 - y1)
            val mx = (x1 + x2) / 2f; val my = (y1 + y2) / 2f
            x1 = mx - cos(snapped) * l / 2f; y1 = my - sin(snapped) * l / 2f
            x2 = mx + cos(snapped) * l / 2f; y2 = my + sin(snapped) * l / 2f
        }
        return Shape(ShapeKind.LINE, floatArrayOf(x1, y1, x2, y2))
    }

    /** Strzałka: trzon (najdłuższy odcinek), po nim ruch z powrotem do grotu i skrzydełka przy czubku. */
    private fun arrow(xy: FloatArray, n: Int, len: Float): Shape? {
        val idx = GeomMath.simplify(xy, n, max(2.5f, 0.05f * len))
        if (idx.size !in 4..6) return null
        val px = FloatArray(idx.size) { xy[2 * idx[it]] }
        val py = FloatArray(idx.size) { xy[2 * idx[it] + 1] }
        val shaft = GeomMath.dist(px[0], py[0], px[1], py[1])
        if (shaft < 0.55f * len) return null
        for (i in 2 until idx.size) {
            if (GeomMath.dist(px[i], py[i], px[1], py[1]) > 0.4f * shaft) return null
        }
        val ux = (px[1] - px[0]) / shaft; val uy = (py[1] - py[0]) / shaft
        val wing = (0.22f * shaft).coerceIn(8f, 26f)
        val a = (28.0 * PI / 180.0).toFloat()
        fun w(sign: Float): Pair<Float, Float> {
            val c = cos(a * sign); val s = sin(a * sign)
            // Wektor wstecz (−u) obrócony o ±28°.
            val bx = -ux * c + uy * s
            val by = -ux * s - uy * c
            return (px[1] + bx * wing) to (py[1] + by * wing)
        }
        val (w1x, w1y) = w(1f); val (w2x, w2y) = w(-1f)
        return Shape(
            ShapeKind.ARROW,
            floatArrayOf(px[0], py[0], px[1], py[1], w1x, w1y, px[1], py[1], w2x, w2y),
        )
    }

    /** Czworokąt ≈ prostokąt (kąty ≈ 90°, przeciwległe boki podobne) → prostokąt/kwadrat po regularyzacji; inaczej null. */
    private fun regularRect(vx: FloatArray, vy: FloatArray): Shape? {
        for (i in 0 until 4) {
            val a = (i + 3) % 4; val c = (i + 1) % 4
            val ang = GeomMath.angleBetween(vx[a] - vx[i], vy[a] - vy[i], vx[c] - vx[i], vy[c] - vy[i])
            if (abs(ang - PI.toFloat() / 2f) > Math.toRadians(30.0)) return null
        }
        val s0 = GeomMath.dist(vx[0], vy[0], vx[1], vy[1]); val s2 = GeomMath.dist(vx[2], vy[2], vx[3], vy[3])
        val s1 = GeomMath.dist(vx[1], vy[1], vx[2], vy[2]); val s3 = GeomMath.dist(vx[3], vy[3], vx[0], vy[0])
        if (abs(s0 - s2) > 0.4f * max(s0, s2) || abs(s1 - s3) > 0.4f * max(s1, s3)) return null
        val mcx = vx.average().toFloat(); val mcy = vy.average().toFloat()
        val w = (s0 + s2) / 2f; val h = (s1 + s3) / 2f
        // Kąt boku 0→1 sprowadzony do [−45°, 45°]; nieparzysta liczba ćwiartek = ten bok leży „pionowo”.
        val q = (PI / 2).toFloat()
        val raw = atan2(vy[1] - vy[0], vx[1] - vx[0])
        val turns = (raw / q).roundToInt()
        var th = raw - turns * q
        var ww = if (turns % 2 != 0) h else w
        var hh = if (turns % 2 != 0) w else h
        if (abs(ww - hh) < 0.12f * max(ww, hh)) { val m = (ww + hh) / 2f; ww = m; hh = m } // kwadrat
        if (abs(th) < Math.toRadians(7.0)) th = 0f
        val c = cos(th); val s = sin(th)
        fun pt(sx: Float, sy: Float) = floatArrayOf(mcx + sx * ww / 2f * c - sy * hh / 2f * s, mcy + sx * ww / 2f * s + sy * hh / 2f * c)
        val a = pt(-1f, -1f); val b = pt(1f, -1f); val d = pt(1f, 1f); val e = pt(-1f, 1f)
        return Shape(ShapeKind.RECT, floatArrayOf(a[0], a[1], b[0], b[1], d[0], d[1], e[0], e[1], a[0], a[1]))
    }

    /** Elipsa dopasowana do prostokąta otaczającego; akceptowana, gdy punkty leżą blisko niej. */
    private fun ellipse(xy: FloatArray, n: Int, l: Float, t: Float, r: Float, b: Float): Shape? {
        var rx = (r - l) / 2f; var ry = (b - t) / 2f
        if (rx < 6f || ry < 6f) return null
        val cx = (l + r) / 2f; val cy = (t + b) / 2f
        var sum = 0f; var sum2 = 0f
        for (i in 0 until n) {
            val dx = (xy[2 * i] - cx) / rx; val dy = (xy[2 * i + 1] - cy) / ry
            val d = sqrt(dx * dx + dy * dy)
            sum += d; sum2 += d * d
        }
        val mean = sum / n
        val sd = sqrt(max(0f, sum2 / n - mean * mean))
        if (abs(mean - 1f) > 0.2f || sd > 0.16f) return null
        if (abs(rx - ry) < 0.1f * max(rx, ry)) { val m = (rx + ry) / 2f; rx = m; ry = m } // koło
        val seg = 48
        val out = FloatArray(2 * (seg + 1))
        for (k in 0..seg) {
            val a = 2f * PI.toFloat() * k / seg
            out[2 * k] = cx + rx * cos(a); out[2 * k + 1] = cy + ry * sin(a)
        }
        return Shape(ShapeKind.ELLIPSE, out)
    }

    /**
     * Łamana z ostrymi rogami: trójkąt, czworokąt, litera L/Z/V, znak pierwiastka, pięciokąt, gwiazda…
     * Próbuje kilku tolerancji uproszczenia (ręka raz drży bardziej, raz mniej) i bierze pierwszą, która daje spójny kształt.
     */
    private fun polyline(xy: FloatArray, n: Int, diag: Float, closed: Boolean): Shape? {
        for (e in EPS) {
            val v = fit(xy, n, diag, closed, max(3f, e * diag)) ?: continue
            val k = if (closed) v.size / 2 - 1 else v.size / 2
            if (closed && k == 4) {
                val vx = FloatArray(4) { v[2 * it] }; val vy = FloatArray(4) { v[2 * it + 1] }
                regularRect(vx, vy)?.let { return it }
            }
            return Shape(if (closed && k == 3) ShapeKind.TRIANGLE else ShapeKind.POLYLINE, v)
        }
        return null
    }

    private val EPS = floatArrayOf(0.035f, 0.055f, 0.08f)

    /**
     * Dopasowuje łamaną o wierzchołkach w rogach; zwraca wspólrzędne (x, y …; przy zamkniętej z powtórzonym pierwszym punktem)
     * albo null, gdy to nie jest łamana (gładki łuk, esy-floresy, zbyt wiele rogów).
     */
    private fun fit(xy: FloatArray, n: Int, diag: Float, closed: Boolean, eps: Float): FloatArray? {
        val m = if (closed) n + 1 else n
        val src = if (closed) FloatArray(2 * m).also { System.arraycopy(xy, 0, it, 0, 2 * n); it[2 * n] = xy[0]; it[2 * n + 1] = xy[1] } else xy
        val idx = GeomMath.simplify(src, m, eps)
        var k = if (closed) idx.size - 1 else idx.size
        if (k > (if (closed) 16 else 18)) return null
        var vi = IntArray(k) { idx[it] }
        // Łuk kreski wzdłuż surowych punktów — do okien lokalnych.
        val cum = FloatArray(m)
        for (i in 1 until m) cum[i] = cum[i - 1] + GeomMath.dist(src[2 * i - 2], src[2 * i - 1], src[2 * i], src[2 * i + 1])
        val total = cum[m - 1]
        if (total <= 0f) return null
        fun at(s: Float, axis: Int): Float {
            val t = if (closed) ((s % total) + total) % total else s.coerceIn(0f, total)
            return interp(src, cum, t, axis)
        }
        fun vxOf(a: Int) = src[2 * vi[a]]
        fun vyOf(a: Int) = src[2 * vi[a] + 1]
        fun prev(a: Int) = if (a == 0) k - 1 else a - 1
        fun next(a: Int) = if (a == k - 1) 0 else a + 1
        fun isCorner(a: Int) = closed || (a in 1 until k - 1)
        fun turn(a: Int): Float {
            val p = prev(a); val q = next(a)
            return GeomMath.angleBetween(vxOf(a) - vxOf(p), vyOf(a) - vyOf(p), vxOf(q) - vxOf(a), vyOf(q) - vyOf(a))
        }
        fun segLen(a: Int) = GeomMath.dist(vxOf(a), vyOf(a), vxOf(next(a)), vyOf(next(a)))
        fun drop(v: Int) { vi = IntArray(k - 1) { if (it < v) vi[it] else vi[it + 1] }; k-- }
        val minTurn = Math.toRadians(28.0).toFloat()
        val minSeg = max(6f, (if (closed) 0.05f else 0.04f) * total)
        val minK = if (closed) 3 else 3
        // Sprzątanie: najsłabsze „rogi” (drżenie ręki na prostym odcinku) i zbyt krótkie odcinki (nawrót, zawijas) wypadają.
        while (true) {
            if (k < minK) return null
            var worst = -1; var wt = Float.MAX_VALUE
            for (a in 0 until k) if (isCorner(a)) { val t = turn(a); if (t < wt) { wt = t; worst = a } }
            if (worst >= 0 && wt < minTurn) { drop(worst); continue }
            val segs = if (closed) k else k - 1
            var short = -1; var sl = minSeg
            for (a in 0 until segs) { val d = segLen(a); if (d < sl) { sl = d; short = a } }
            if (short < 0) break
            val b = next(short)
            val victim = when {
                !closed && short == 0 -> 0
                !closed && b == k - 1 -> b
                turn(short) <= turn(b) -> short
                else -> b
            }
            drop(victim)
        }
        val segs = if (closed) k else k - 1
        // Wszystkie surowe punkty muszą leżeć blisko wynikowej łamanej.
        val tol = 0.075f * diag
        var sum = 0f
        for (i in 0 until n) {
            var best = Float.MAX_VALUE
            for (a in 0 until segs) {
                val b = next(a)
                best = min(best, GeomMath.distSqPointSegment(xy[2 * i], xy[2 * i + 1], vxOf(a), vyOf(a), vxOf(b), vyOf(b)))
            }
            if (best > tol * tol) return null
            sum += sqrt(best)
        }
        // Gładka krzywa obcięta do wielokąta ma stale spore odchylenie; prosta kreska drży tylko lekko.
        if (sum / n > 0.032f * diag) return null
        // Ostrość rogów także lokalnie: łuk kręci się na krótkim odcinku wokół wierzchołka wyraźnie słabiej niż róg.
        for (a in 0 until k) {
            if (!isCorner(a)) continue
            val d = (0.25f * min(segLen(prev(a)), segLen(a))).coerceAtLeast(4f)
            val s = cum[vi[a]]
            val ax = at(s - d, 0); val ay = at(s - d, 1); val bx = at(s + d, 0); val by = at(s + d, 1)
            val local = GeomMath.angleBetween(vxOf(a) - ax, vyOf(a) - ay, bx - vxOf(a), by - vyOf(a))
            if (local < (if (turn(a) >= RIGHT_TURN) LOCAL_RATIO_SHARP else LOCAL_RATIO) * turn(a)) return null
        }
        val cnt = if (closed) k + 1 else k
        val out = FloatArray(2 * cnt)
        for (a in 0 until cnt) { val v = if (a == k) 0 else a; out[2 * a] = vxOf(v); out[2 * a + 1] = vyOf(v) }
        return out
    }

    private const val LOCAL_RATIO = 0.6f
    // Prawie prosty kąt (ząbek pierwiastka, róg kwadratu) ręka zaokrągla mocniej niż łagodny zakręt, więc wystarczy słabszy dowód ostrości.
    private const val LOCAL_RATIO_SHARP = 0.4f
    private val RIGHT_TURN = Math.toRadians(80.0).toFloat()

    /** Punkt (x lub y) na łamanej o skumulowanej długości [cum] w odległości [s] od początku. */
    private fun interp(src: FloatArray, cum: FloatArray, s: Float, axis: Int): Float {
        val m = cum.size
        var lo = 0; var hi = m - 1
        while (hi - lo > 1) { val mid = (lo + hi) ushr 1; if (cum[mid] <= s) lo = mid else hi = mid }
        val span = cum[hi] - cum[lo]
        val t = if (span <= 1e-6f) 0f else ((s - cum[lo]) / span).coerceIn(0f, 1f)
        return src[2 * lo + axis] + (src[2 * hi + axis] - src[2 * lo + axis]) * t
    }
}
