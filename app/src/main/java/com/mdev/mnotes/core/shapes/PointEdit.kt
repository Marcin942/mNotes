package com.mdev.mnotes.core.shapes

import com.mdev.mnotes.core.model.Stroke
import kotlin.math.hypot

/**
 * Edycja punktów kształtu (F21): podwójne dotknięcie zamienia kształt w punkty do przeciągania, a przytrzymanie
 * uchwytu otwiera menu punktu (usuń, dodaj, gładki/ostry).
 */
object PointEdit {
    /** Stała łuku ćwiartki koła sześcienną krzywą Béziera (błąd < 0,03 %). */
    private const val KAPPA = 0.5522848f

    /** Elipsa z ramki [f] jako zamknięta krzywa z 4 kotwicami na końcach osi (ten sam kierunek i obrót). */
    fun ellipseCtrl(f: ShapeFrame): FloatArray {
        val k = KAPPA
        // Punkty w układzie ramki (a, b) ∈ [−1, 1]: kotwice (1,0) → (0,1) → (−1,0) → (0,−1) → (1,0).
        val ab = floatArrayOf(
            1f, 0f, 1f, k, k, 1f, 0f, 1f,
            -k, 1f, -1f, k, -1f, 0f,
            -1f, -k, -k, -1f, 0f, -1f,
            k, -1f, 1f, -k, 1f, 0f,
        )
        return FloatArray(ab.size) { i -> if (i % 2 == 0) f.x(ab[i], ab[i + 1]) else f.y(ab[i - 1], ab[i]) }
    }

    /**
     * Kreska jako krzywa do edycji punktów: elipsa → 4 łuki, zwykła kreska → dopasowany splajn; null, gdy już jest
     * krzywą albo łamaną (te mają swoje uchwyty).
     */
    fun toCurve(s: Stroke): Stroke? {
        if (s.ctrl != null) return null
        if (s.ellipse) { val f = ShapeBox.frameOf(s) ?: return null; return Bezier.withCtrl(s, ellipseCtrl(f)) }
        if (s.poly) return null
        val xy = FloatArray(2 * s.count) { i -> s.pts[3 * (i / 2) + i % 2] }
        val c = Bezier.fit(xy, (s.width * 0.6f).coerceIn(0.8f, 2.5f)) ?: return null
        return Bezier.withCtrl(s, c)
    }

    /** Łamana bez wierzchołka [i] (zamknięta zostaje zamknięta); null, gdy zostałyby mniej niż 2 wierzchołki. */
    fun removeVertex(s: Stroke, i: Int): Stroke? {
        val n = s.vertexCount
        if (n <= 2 || i !in 0 until n) return null
        val v = ArrayList<Float>()
        for (k in 0 until n) if (k != i) { v.add(s.pts[3 * k]); v.add(s.pts[3 * k + 1]); v.add(s.pts[3 * k + 2]) }
        if (s.closed) { v.add(v[0]); v.add(v[1]); v.add(v[2]) }
        return Stroke(s.id, s.pen, s.color, s.width, v.toFloatArray(), s.locked, true, false, s.line)
    }

    /** Łamana z nowym wierzchołkiem w połowie odcinka za wierzchołkiem [i] (dla ostatniego otwartej — przed nim). */
    fun insertAfter(s: Stroke, i: Int): Stroke? {
        val n = s.count
        if (i !in 0 until n) return null
        val a = if (i < n - 1) i else i - 1
        if (a < 0) return null
        val p = s.pts
        val mid = floatArrayOf((p[3 * a] + p[3 * a + 3]) / 2f, (p[3 * a + 1] + p[3 * a + 4]) / 2f, (p[3 * a + 2] + p[3 * a + 5]) / 2f)
        val out = FloatArray(p.size + 3)
        System.arraycopy(p, 0, out, 0, 3 * (a + 1))
        System.arraycopy(mid, 0, out, 3 * (a + 1), 3)
        System.arraycopy(p, 3 * (a + 1), out, 3 * (a + 2), p.size - 3 * (a + 1))
        return Stroke(s.id, s.pen, s.color, s.width, out, s.locked, true, false, s.line)
    }

    /** Krzywa bez kotwicy o indeksie punktu [i] (sąsiednie odcinki łączą się w jeden); null, gdy zostałaby jedna. */
    fun removeAnchor(ctrl: FloatArray, i: Int): FloatArray? {
        if (!Bezier.isAnchor(i) || Bezier.anchors(ctrl) <= 2) return null
        val n = ctrl.size / 2
        val keep = when (i) {
            0 -> 3 until n
            n - 1 -> 0 until n - 3
            else -> (0 until n).filter { it !in (i - 1)..(i + 1) }
        }
        val out = FloatArray(keep.count() * 2)
        var k = 0
        for (j in keep) { out[2 * k] = ctrl[2 * j]; out[2 * k + 1] = ctrl[2 * j + 1]; k++ }
        return out
    }

    /**
     * Przełącza kotwicę [i] między gładką (uchwyty na jednej prostej, o średniej długości) a ostrą (uchwyty skierowane
     * do sąsiednich kotwic, jak w łamanej).
     */
    fun toggleSmooth(ctrl: FloatArray, i: Int): FloatArray {
        val n = ctrl.size / 2
        if (!Bezier.isAnchor(i) || i == 0 || i == n - 1) return ctrl
        val out = ctrl.copyOf()
        val ax = ctrl[2 * i]; val ay = ctrl[2 * i + 1]
        if (Bezier.smooth(ctrl, i)) {
            // Ostra: każdy uchwyt na 1/3 drogi do sąsiedniej kotwicy.
            for ((h, nb) in listOf(i - 1 to i - 3, i + 1 to i + 3)) {
                out[2 * h] = ax + (ctrl[2 * nb] - ax) / 3f; out[2 * h + 1] = ay + (ctrl[2 * nb + 1] - ay) / 3f
            }
        } else {
            // Gładka: kierunek od poprzedniej do następnej kotwicy, długości uchwytów bez zmian.
            var dx = ctrl[2 * (i + 3)] - ctrl[2 * (i - 3)]; var dy = ctrl[2 * (i + 3) + 1] - ctrl[2 * (i - 3) + 1]
            val l = hypot(dx, dy).coerceAtLeast(1e-6f); dx /= l; dy /= l
            val l1 = hypot(ctrl[2 * (i - 1)] - ax, ctrl[2 * (i - 1) + 1] - ay)
            val l2 = hypot(ctrl[2 * (i + 1)] - ax, ctrl[2 * (i + 1) + 1] - ay)
            out[2 * (i - 1)] = ax - dx * l1; out[2 * (i - 1) + 1] = ay - dy * l1
            out[2 * (i + 1)] = ax + dx * l2; out[2 * (i + 1) + 1] = ay + dy * l2
        }
        return out
    }
}
