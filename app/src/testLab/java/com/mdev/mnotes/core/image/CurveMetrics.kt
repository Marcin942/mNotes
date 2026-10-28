package com.mdev.mnotes.core.image

import com.mdev.mnotes.core.shapes.Bezier
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Miary gładkości kresek kredy (2b-B4), te same dla łamanej (dawny wynik) i krzywej Béziera (nowy):
 * - błąd dopasowania: średnia odległość punktów szkieletu od rysowanej linii (px);
 * - węzły na 100 px: wierzchołki łamanej albo kotwice krzywej na 100 px długości kreski;
 * - zęby: załamania kierunku 25°–60° w wierzchołkach rysowanej linii (krzywa próbkowana co 1 px), poza narożnikami;
 *   skręt ≥ 60° to zamierzony narożnik (dla łamanej liczony na jej korzyść jako narożnik, nie ząb);
 * - największy skręt na gładkich odcinkach krzywej (poza ±2 px od narożników).
 */
object CurveMetrics {

    /** Odległość punktu od łamanej (x, y …). */
    fun distToPolyline(x: Float, y: Float, poly: FloatArray): Float {
        var best = Float.MAX_VALUE
        val n = poly.size / 2
        if (n == 1) return hypot(x - poly[0], y - poly[1])
        for (i in 1 until n) {
            val ax = poly[2 * i - 2]; val ay = poly[2 * i - 1]; val bx = poly[2 * i]; val by = poly[2 * i + 1]
            val dx = bx - ax; val dy = by - ay; val l2 = dx * dx + dy * dy
            val t = if (l2 < 1e-9f) 0f else (((x - ax) * dx + (y - ay) * dy) / l2).coerceIn(0f, 1f)
            best = min(best, hypot(x - ax - t * dx, y - ay - t * dy))
        }
        return best
    }

    /** Krzywa [ctrl] jako łamana (x, y …) z próbkami co ok. [step] px. */
    fun curvePolyline(ctrl: FloatArray, step: Float = 1f): FloatArray {
        val s = Bezier.sample(ctrl, step)
        return FloatArray(s.size / 3 * 2) { if (it % 2 == 0) s[it / 2 * 3] else s[it / 2 * 3 + 1] }
    }

    /** Suma odległości punktów [data] od łamanej [poly] i ich liczba. */
    fun errorSum(data: FloatArray, poly: FloatArray): Pair<Double, Int> {
        var s = 0.0
        for (i in 0 until data.size / 2) s += distToPolyline(data[2 * i], data[2 * i + 1], poly)
        return s to data.size / 2
    }

    /** Skręty (stopnie) w wewnętrznych wierzchołkach łamanej; bardzo krótkie odcinki (< 0,05 px) pomijane. */
    fun turns(poly: FloatArray): List<Pair<Int, Double>> {
        val out = ArrayList<Pair<Int, Double>>()
        val n = poly.size / 2
        for (i in 1 until n - 1) {
            val ax = poly[2 * i] - poly[2 * i - 2]; val ay = poly[2 * i + 1] - poly[2 * i - 1]
            val bx = poly[2 * i + 2] - poly[2 * i]; val by = poly[2 * i + 3] - poly[2 * i + 1]
            val la = hypot(ax, ay); val lb = hypot(bx, by)
            if (la < 0.05f || lb < 0.05f) continue
            out += i to Math.toDegrees(acos(((ax * bx + ay * by) / (la * lb)).toDouble().coerceIn(-1.0, 1.0)))
        }
        return out
    }

    /** Kotwice krzywej, w których odcinki stykają się pod kątem ≥ 20° (narożniki; złączenia Schneidera mają wspólną styczną), jako punkty (x, y …). */
    fun cornerAnchors(ctrl: FloatArray): FloatArray {
        val out = ArrayList<Float>()
        val n = ctrl.size / 2
        for (a in 3 until n - 1 step 3) {
            val ix = ctrl[2 * a] - ctrl[2 * a - 2]; val iy = ctrl[2 * a + 1] - ctrl[2 * a - 1]
            val ox = ctrl[2 * a + 2] - ctrl[2 * a]; val oy = ctrl[2 * a + 3] - ctrl[2 * a + 1]
            val li = hypot(ix, iy); val lo = hypot(ox, oy)
            if (li < 1e-4f || lo < 1e-4f) continue
            val deg = Math.toDegrees(acos(((ix * ox + iy * oy) / (li * lo)).toDouble().coerceIn(-1.0, 1.0)))
            if (deg >= 20.0) { out += ctrl[2 * a]; out += ctrl[2 * a + 1] }
        }
        return out.toFloatArray()
    }

    class Sum {
        var length = 0.0; var nodes = 0; var teeth = 0; var err = 0.0; var errN = 0; var maxSmooth = 0.0
        val nodesPer100 get() = if (length > 0) 100.0 * nodes / length else 0.0
        val teethPer100 get() = if (length > 0) 100.0 * teeth / length else 0.0
        val meanErr get() = if (errN > 0) err / errN else 0.0
    }

    /** Dawny wynik: łamana [poly] po uproszczeniu, [data] to punkty szkieletu, z których powstała. */
    fun addPolyline(sum: Sum, data: FloatArray, poly: FloatArray) {
        sum.length += lengthOf(data)
        sum.nodes += poly.size / 2
        val (e, k) = errorSum(data, poly); sum.err += e; sum.errN += k
        for ((_, t) in turns(poly)) if (t in 25.0..60.0) sum.teeth++ else if (t < 25.0) sum.maxSmooth = max(sum.maxSmooth, t)
    }

    /** Nowy wynik: krzywa [ctrl] dopasowana do [data] (punkty szkieletu). */
    fun addCurve(sum: Sum, data: FloatArray, ctrl: FloatArray) {
        sum.length += lengthOf(data)
        sum.nodes += Bezier.anchors(ctrl)
        val poly = curvePolyline(ctrl)
        val (e, k) = errorSum(data, poly); sum.err += e; sum.errN += k
        val corners = cornerAnchors(ctrl)
        fun nearCorner(i: Int): Boolean {
            for (c in 0 until corners.size / 2) if (hypot(poly[2 * i] - corners[2 * c], poly[2 * i + 1] - corners[2 * c + 1]) <= 2f) return true
            return false
        }
        for ((i, t) in turns(poly)) {
            if (nearCorner(i)) continue
            if (t >= 25.0) sum.teeth++
            sum.maxSmooth = max(sum.maxSmooth, t)
        }
    }

    fun lengthOf(p: FloatArray): Double {
        var s = 0.0
        for (i in 1 until p.size / 2) s += hypot((p[2 * i] - p[2 * i - 2]).toDouble(), (p[2 * i + 1] - p[2 * i - 1]).toDouble())
        return s
    }
}
