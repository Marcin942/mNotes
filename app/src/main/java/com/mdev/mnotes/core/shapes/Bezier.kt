package com.mdev.mnotes.core.shapes

import com.mdev.mnotes.core.geom.FloatList
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Krzywe Béziera (F17, F18): gładki splajn z odcinków sześciennych dopasowany do odręcznej kreski i jego edycja.
 *
 * Zapis punktów kontrolnych [ctrl] (pary x, y): kotwica, uchwyt wyjściowy, uchwyt wejściowy następnej kotwicy,
 * kotwica, … — czyli 3·k + 1 punktów dla k odcinków. Kreska trzyma zawsze także gęstą łamaną z [sample], więc
 * renderer, gumka, lasso, eksport i starsze wersje aplikacji widzą zwykłą kreskę.
 */
object Bezier {

    /** Kreska [s] z nowymi punktami kontrolnymi [c] (łamana liczona od nowa); styl i id bez zmian. */
    fun withCtrl(s: com.mdev.mnotes.core.model.Stroke, c: FloatArray) =
        com.mdev.mnotes.core.model.Stroke(s.id, s.pen, s.color, s.width, sample(c), s.locked, false, false, s.line, c)

    /** Liczba kotwic w zapisie [ctrl]. */
    fun anchors(ctrl: FloatArray): Int = (ctrl.size / 2 + 2) / 3

    /** Czy indeks punktu (0…n−1) to kotwica (inaczej uchwyt). */
    fun isAnchor(i: Int): Boolean = i % 3 == 0

    /** Gęsta łamana krzywej: punkty co ok. [step] pt (z kotwicami dokładnie), jako trójki x, y, p z naciskiem 1. */
    fun sample(ctrl: FloatArray, step: Float = 1.5f): FloatArray {
        val segs = (ctrl.size / 2 - 1) / 3
        val out = FloatList(64)
        if (segs <= 0) { if (ctrl.size >= 2) out.add(ctrl[0], ctrl[1], 1f); return out.toArray() }
        out.add(ctrl[0], ctrl[1], 1f)
        for (s in 0 until segs) {
            val o = 6 * s
            val x0 = ctrl[o]; val y0 = ctrl[o + 1]; val x1 = ctrl[o + 2]; val y1 = ctrl[o + 3]
            val x2 = ctrl[o + 4]; val y2 = ctrl[o + 5]; val x3 = ctrl[o + 6]; val y3 = ctrl[o + 7]
            // Długość wielokąta kontrolnego ogranicza długość odcinka z góry, więc gęstość jest wystarczająca.
            val len = hypot(x1 - x0, y1 - y0) + hypot(x2 - x1, y2 - y1) + hypot(x3 - x2, y3 - y2)
            val n = (len / step).toInt().coerceIn(2, 400)
            for (k in 1..n) {
                val t = k.toFloat() / n; val u = 1f - t
                val a = u * u * u; val b = 3f * u * u * t; val c = 3f * u * t * t; val d = t * t * t
                out.add(a * x0 + b * x1 + c * x2 + d * x3, a * y0 + b * y1 + c * y2 + d * y3, 1f)
            }
        }
        return out.toArray()
    }

    /**
     * Dopasowanie splajnu do punktów (x, y …) z błędem najwyżej [tol] pt (Schneider, „An Algorithm for Automatically
     * Fitting Digitized Curves”, przepisane od zera). Zwraca [ctrl]; dla mniej niż 2 punktów null.
     */
    fun fit(xyIn: FloatArray, tol: Float = 1.2f): FloatArray? {
        // Punkty bliższe niż 0,6 pt łączymy: drżenie rysika dawałoby złe styczne.
        val xy = FloatList(xyIn.size)
        for (i in 0 until xyIn.size / 2) {
            val x = xyIn[2 * i]; val y = xyIn[2 * i + 1]
            if (xy.size >= 2 && hypot(x - xy[xy.size - 2], y - xy[xy.size - 1]) < 0.6f) continue
            xy.add(x, y)
        }
        val p = xy.toArray()
        val n = p.size / 2
        if (n < 2) return null
        if (n == 2) return line(p[0], p[1], p[2], p[3])
        val out = FloatList(64)
        out.add(p[0], p[1])
        val tl = unit(p[2] - p[0], p[3] - p[1])
        val tr = unit(p[2 * n - 4] - p[2 * n - 2], p[2 * n - 3] - p[2 * n - 1])
        fitRange(p, 0, n - 1, tl, tr, tol * tol, out, 0)
        return out.toArray()
    }

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float) = floatArrayOf(
        x0, y0, x0 + (x1 - x0) / 3f, y0 + (y1 - y0) / 3f, x0 + 2f * (x1 - x0) / 3f, y0 + 2f * (y1 - y0) / 3f, x1, y1,
    )

    private fun unit(x: Float, y: Float): FloatArray { val l = hypot(x, y); return if (l < 1e-6f) floatArrayOf(1f, 0f) else floatArrayOf(x / l, y / l) }

    /** Dopasowuje punkty [first]..[last]; do [out] dopisuje uchwyty i kotwicę końcową (początkowa już tam jest). */
    private fun fitRange(p: FloatArray, first: Int, last: Int, tl: FloatArray, tr: FloatArray, tol2: Float, out: FloatList, depth: Int) {
        val count = last - first + 1
        if (count == 2) {
            val d = hypot(p[2 * last] - p[2 * first], p[2 * last + 1] - p[2 * first + 1]) / 3f
            out.add(p[2 * first] + tl[0] * d, p[2 * first + 1] + tl[1] * d)
            out.add(p[2 * last] + tr[0] * d, p[2 * last + 1] + tr[1] * d)
            out.add(p[2 * last], p[2 * last + 1])
            return
        }
        var u = chordParams(p, first, last)
        var bez = generate(p, first, last, u, tl, tr)
        var (err, split) = maxError(p, first, last, bez, u)
        if (err < tol2 || depth > 12) { emit(bez, out); return }
        // Kilka kroków Newtona–Raphsona na parametrach, zanim podzielimy.
        if (err < tol2 * 16f) {
            repeat(4) {
                u = reparam(p, first, last, bez, u)
                bez = generate(p, first, last, u, tl, tr)
                val r = maxError(p, first, last, bez, u)
                err = r.first; split = r.second
                if (err < tol2) { emit(bez, out); return }
            }
        }
        split = split.coerceIn(first + 1, last - 1)
        val tc = unit(p[2 * (split - 1)] - p[2 * (split + 1)], p[2 * (split - 1) + 1] - p[2 * (split + 1) + 1])
        fitRange(p, first, split, tl, tc, tol2, out, depth + 1)
        fitRange(p, split, last, floatArrayOf(-tc[0], -tc[1]), tr, tol2, out, depth + 1)
    }

    private fun emit(b: FloatArray, out: FloatList) { out.add(b[2], b[3]); out.add(b[4], b[5]); out.add(b[6], b[7]) }

    private fun chordParams(p: FloatArray, first: Int, last: Int): FloatArray {
        val u = FloatArray(last - first + 1)
        for (i in first + 1..last) u[i - first] = u[i - first - 1] + hypot(p[2 * i] - p[2 * i - 2], p[2 * i + 1] - p[2 * i - 1])
        val total = u.last()
        if (total > 0f) for (i in u.indices) u[i] /= total
        return u
    }

    /** Odcinek z kotwicami w punktach skrajnych i uchwytami wzdłuż stycznych, długości z najmniejszych kwadratów. */
    private fun generate(p: FloatArray, first: Int, last: Int, u: FloatArray, tl: FloatArray, tr: FloatArray): FloatArray {
        val x0 = p[2 * first]; val y0 = p[2 * first + 1]; val x3 = p[2 * last]; val y3 = p[2 * last + 1]
        var c00 = 0.0; var c01 = 0.0; var c11 = 0.0; var x0r = 0.0; var x1r = 0.0
        for (i in u.indices) {
            val t = u[i]; val s = 1f - t
            val b0 = s * s * s; val b1 = 3 * s * s * t; val b2 = 3 * s * t * t; val b3 = t * t * t
            val a1x = tl[0] * b1; val a1y = tl[1] * b1; val a2x = tr[0] * b2; val a2y = tr[1] * b2
            c00 += (a1x * a1x + a1y * a1y).toDouble(); c01 += (a1x * a2x + a1y * a2y).toDouble(); c11 += (a2x * a2x + a2y * a2y).toDouble()
            val px = p[2 * (first + i)] - (x0 * (b0 + b1) + x3 * (b2 + b3))
            val py = p[2 * (first + i) + 1] - (y0 * (b0 + b1) + y3 * (b2 + b3))
            x0r += (a1x * px + a1y * py).toDouble(); x1r += (a2x * px + a2y * py).toDouble()
        }
        val det = c00 * c11 - c01 * c01
        val seg = hypot(x3 - x0, y3 - y0)
        var al = if (abs(det) > 1e-12) ((x0r * c11 - x1r * c01) / det).toFloat() else 0f
        var ar = if (abs(det) > 1e-12) ((c00 * x1r - c01 * x0r) / det).toFloat() else 0f
        // Ujemne albo zbyt małe długości dają pętle — wtedy heurystyka: 1/3 cięciwy.
        if (al < 1e-3f * seg || ar < 1e-3f * seg || al > 2f * seg || ar > 2f * seg) { al = seg / 3f; ar = seg / 3f }
        return floatArrayOf(x0, y0, x0 + tl[0] * al, y0 + tl[1] * al, x3 + tr[0] * ar, y3 + tr[1] * ar, x3, y3)
    }

    private fun point(b: FloatArray, t: Float): FloatArray {
        val s = 1f - t
        val a = s * s * s; val c1 = 3 * s * s * t; val c2 = 3 * s * t * t; val d = t * t * t
        return floatArrayOf(a * b[0] + c1 * b[2] + c2 * b[4] + d * b[6], a * b[1] + c1 * b[3] + c2 * b[5] + d * b[7])
    }

    private fun maxError(p: FloatArray, first: Int, last: Int, b: FloatArray, u: FloatArray): Pair<Float, Int> {
        var worst = 0f; var at = (first + last) / 2
        for (i in first + 1 until last) {
            val q = point(b, u[i - first])
            val dx = q[0] - p[2 * i]; val dy = q[1] - p[2 * i + 1]
            val d = dx * dx + dy * dy
            if (d >= worst) { worst = d; at = i }
        }
        return worst to at
    }

    private fun reparam(p: FloatArray, first: Int, last: Int, b: FloatArray, u: FloatArray): FloatArray = FloatArray(u.size) { k ->
        val t = u[k]; val s = 1f - t
        val q = point(b, t)
        // Pierwsza i druga pochodna w t.
        val d1x = 3 * (s * s * (b[2] - b[0]) + 2 * s * t * (b[4] - b[2]) + t * t * (b[6] - b[4]))
        val d1y = 3 * (s * s * (b[3] - b[1]) + 2 * s * t * (b[5] - b[3]) + t * t * (b[7] - b[5]))
        val d2x = 6 * (s * (b[4] - 2 * b[2] + b[0]) + t * (b[6] - 2 * b[4] + b[2]))
        val d2y = 6 * (s * (b[5] - 2 * b[3] + b[1]) + t * (b[7] - 2 * b[5] + b[3]))
        val ex = q[0] - p[2 * (first + k)]; val ey = q[1] - p[2 * (first + k) + 1]
        val num = ex * d1x + ey * d1y
        val den = d1x * d1x + d1y * d1y + ex * d2x + ey * d2y
        if (abs(den) < 1e-9f) t else (t - num / den).coerceIn(0f, 1f)
    }

    /**
     * Przesunięcie punktu [i] zapisu: kotwica ciągnie za sobą oba swoje uchwyty, uchwyt przy kotwicy gładkiej (uchwyty
     * współliniowe) obraca też przeciwny uchwyt, żeby krzywa nie dostała załamania; zwraca nową tablicę.
     */
    fun moved(ctrl: FloatArray, i: Int, x: Float, y: Float): FloatArray {
        val out = ctrl.copyOf()
        val n = ctrl.size / 2
        if (i !in 0 until n) return out
        val dx = x - ctrl[2 * i]; val dy = y - ctrl[2 * i + 1]
        out[2 * i] = x; out[2 * i + 1] = y
        if (isAnchor(i)) {
            for (h in intArrayOf(i - 1, i + 1)) if (h in 0 until n) { out[2 * h] += dx; out[2 * h + 1] += dy }
            return out
        }
        val a = if (i % 3 == 1) i - 1 else i + 1          // kotwica tego uchwytu
        val o = if (i % 3 == 1) i - 2 else i + 2          // uchwyt po drugiej stronie kotwicy
        if (o !in 0 until n) return out
        val ax = ctrl[2 * a]; val ay = ctrl[2 * a + 1]
        if (smooth(ctrl, a)) {
            val ol = hypot(ctrl[2 * o] - ax, ctrl[2 * o + 1] - ay)
            val v = unit(ax - x, ay - y)
            out[2 * o] = ax + v[0] * ol; out[2 * o + 1] = ay + v[1] * ol
        }
        return out
    }

    /** Czy kotwica [a] jest gładka: jej dwa uchwyty leżą (prawie) na jednej prostej po przeciwnych stronach. */
    fun smooth(ctrl: FloatArray, a: Int): Boolean {
        val n = ctrl.size / 2
        if (a - 1 < 0 || a + 1 >= n) return false
        val ax = ctrl[2 * a]; val ay = ctrl[2 * a + 1]
        val u = unit(ctrl[2 * (a - 1)] - ax, ctrl[2 * (a - 1) + 1] - ay)
        val v = unit(ctrl[2 * (a + 1)] - ax, ctrl[2 * (a + 1) + 1] - ay)
        return u[0] * v[0] + u[1] * v[1] < -0.985f
    }

    /**
     * Czy kreska (pierwsze [n] par x, y w pt) ma ostry narożnik: na odcinku ±1/16 długości kierunek skręca o więcej niż
     * [maxTurnDeg]. Łuk, okrąg i fala skręcają łagodnie (półokrąg ok. 22°), a litery i kąty łamanej ostro — F18 wygładza
     * tylko te pierwsze, żeby trzymane dłużej pismo nie zamieniało się w krzywą (A2-3).
     */
    fun sharpCorner(xy: FloatArray, n: Int = xy.size / 2, maxTurnDeg: Float = 60f): Boolean {
        val m = n.coerceAtMost(xy.size / 2)
        if (m < 3) return false
        var len = 0f
        for (i in 1 until m) len += hypot(xy[2 * i] - xy[2 * i - 2], xy[2 * i + 1] - xy[2 * i - 1])
        // Próbki co 1/32 długości (nie gęściej niż co 1,5 pt): drżenie rysika nie daje wtedy fałszywych kątów.
        val step = max(len / 32f, 1.5f)
        val r = FloatList(80)
        r.add(xy[0], xy[1])
        var carry = 0f
        for (i in 1 until m) {
            val x0 = xy[2 * i - 2]; val y0 = xy[2 * i - 1]; val x1 = xy[2 * i]; val y1 = xy[2 * i + 1]
            val seg = hypot(x1 - x0, y1 - y0)
            var d = step - carry
            while (d <= seg) { val t = d / seg; r.add(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t); d += step }
            carry = seg - (d - step)
        }
        val p = r.toArray()
        val k = 2
        val cosMax = kotlin.math.cos(Math.toRadians(maxTurnDeg.toDouble())).toFloat()
        for (i in k until p.size / 2 - k) {
            val a = unit(p[2 * i] - p[2 * (i - k)], p[2 * i + 1] - p[2 * (i - k) + 1])
            val b = unit(p[2 * (i + k)] - p[2 * i], p[2 * (i + k) + 1] - p[2 * i + 1])
            if (a[0] * b[0] + a[1] * b[1] < cosMax) return true
        }
        return false
    }

    /** Największa odległość punktów (x, y …) od krzywej [ctrl] (pomiar dopasowania, do testów). */
    fun maxDistance(xy: FloatArray, ctrl: FloatArray): Float {
        val s = sample(ctrl, 0.25f)
        var worst = 0f
        for (i in 0 until xy.size / 2) {
            var best = Float.MAX_VALUE
            for (k in 0 until s.size / 3) { val dx = s[3 * k] - xy[2 * i]; val dy = s[3 * k + 1] - xy[2 * i + 1]; best = minOf(best, dx * dx + dy * dy) }
            worst = max(worst, best)
        }
        return sqrt(worst)
    }
}
