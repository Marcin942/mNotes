package com.mdev.mnotes.core.shapes

import com.mdev.mnotes.core.geom.FloatList
import com.mdev.mnotes.core.model.Stroke
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Pamięć krzywej w zwykłej kresce (LP-3): krzywa Béziera zamieniona w kreskę zachowuje swoje punkty kontrolne
 * ([Stroke.memo]), więc powrót do krzywej nie dopasowuje splajnu od nowa (z wygładzeniem), tylko przywraca te same
 * punkty — bezstratnie w dowolnej liczbie cykli. Przekształcenia afiniczne przenoszą pamięć tą samą macierzą
 * ([Stroke.transformed]; krzywa Béziera jest afinicznie niezmiennicza), a gumka daje kawałkom ich dokładne odcinki
 * krzywej ([sub], podział de Casteljau).
 *
 * Parametr globalny `u` ∈ [0, liczba odcinków]: część całkowita to numer odcinka sześciennego, ułamkowa to jego `t`.
 */
object CurveMemo {

    /** Krzywa → zwykła kreska, która wygląda jak ona (gęsta łamana krzywej) i pamięta jej punkty; null, gdy to nie krzywa. */
    fun toPlain(s: Stroke): Stroke? {
        val c = s.ctrl ?: return null
        return Stroke(s.id, s.pen, s.color, s.width, Bezier.sample(c), s.locked, false, false, s.line, null, s.fill, c.copyOf())
    }

    /** Zwykła kreska z pamięcią → ta sama krzywa (bez wygładzania); null, gdy kreska nie pamięta krzywej. */
    fun restore(s: Stroke): Stroke? {
        val m = s.memo ?: return null
        if (s.ctrl != null || s.poly || segments(m) <= 0) return null
        return Bezier.withCtrl(s, m.copyOf())
    }

    /** Liczba odcinków sześciennych zapisu [ctrl] (3·k + 1 punktów). */
    fun segments(ctrl: FloatArray): Int = (ctrl.size / 2 - 1) / 3

    /** Punkt krzywej w parametrze globalnym [u] (x, y). */
    fun point(ctrl: FloatArray, u: Float): FloatArray {
        val segs = segments(ctrl)
        if (segs <= 0) return floatArrayOf(ctrl[0], ctrl[1])
        val s = floor(u).toInt().coerceIn(0, segs - 1)
        val t = (u - s).coerceIn(0f, 1f)
        val o = 6 * s
        val v = 1f - t
        val a = v * v * v; val b = 3f * v * v * t; val c = 3f * v * t * t; val d = t * t * t
        return floatArrayOf(
            a * ctrl[o] + b * ctrl[o + 2] + c * ctrl[o + 4] + d * ctrl[o + 6],
            a * ctrl[o + 1] + b * ctrl[o + 3] + c * ctrl[o + 5] + d * ctrl[o + 7],
        )
    }

    /**
     * Podział de Casteljau jednego odcinka (8 liczb: P0..P3) w [t]: lewa i prawa część, po 8 liczb, wspólny punkt
     * podziału. Interpolacja `a·(1−t) + b·t` daje dokładnie końce dla t = 0 i t = 1, więc podział na brzegu nic nie psuje.
     */
    fun splitSegment(p: FloatArray, t: Float): Pair<FloatArray, FloatArray> {
        fun lerp(a: Float, b: Float) = a * (1f - t) + b * t
        val x01 = lerp(p[0], p[2]); val y01 = lerp(p[1], p[3])
        val x12 = lerp(p[2], p[4]); val y12 = lerp(p[3], p[5])
        val x23 = lerp(p[4], p[6]); val y23 = lerp(p[5], p[7])
        val x012 = lerp(x01, x12); val y012 = lerp(y01, y12)
        val x123 = lerp(x12, x23); val y123 = lerp(y12, y23)
        val x = lerp(x012, x123); val y = lerp(y012, y123)
        return floatArrayOf(p[0], p[1], x01, y01, x012, y012, x, y) to floatArrayOf(x, y, x123, y123, x23, y23, p[6], p[7])
    }

    private fun segment(ctrl: FloatArray, s: Int): FloatArray = ctrl.copyOfRange(6 * s, 6 * s + 8)

    /**
     * Odcinek krzywej od parametru [u0] do [u1] (globalnych, u0 < u1) jako nowy zapis punktów kontrolnych; kształt
     * pokrywa się z tym kawałkiem krzywej, a pełny zakres zwraca te same punkty. Null, gdy zakres jest pusty.
     */
    fun sub(ctrl: FloatArray, u0: Float, u1: Float): FloatArray? {
        val segs = segments(ctrl)
        if (segs <= 0) return null
        val a = u0.coerceIn(0f, segs.toFloat()); val b = u1.coerceIn(0f, segs.toFloat())
        if (b - a < 1e-5f) return null
        // Początek w odcinku floor(a), koniec w ceil(b) − 1: koniec na granicy odcinków bierze cały poprzedni.
        val sa = floor(a).toInt().coerceAtMost(segs - 1); val ta = a - sa
        val sb = (ceil(b).toInt() - 1).coerceIn(sa, segs - 1); val tb = b - sb
        val out = FloatList(6 * (sb - sa + 1) + 2)
        fun put(seg: FloatArray, first: Boolean) { for (k in (if (first) 0 else 2) until 8) out.add(seg[k]) }
        if (sa == sb) {
            val right = if (ta > 0f) splitSegment(segment(ctrl, sa), ta).second else segment(ctrl, sa)
            val rest = if (ta < 1f) (tb - ta) / (1f - ta) else 1f
            put(if (rest < 1f) splitSegment(right, rest).first else right, true)
            return out.toArray()
        }
        put(if (ta > 0f) splitSegment(segment(ctrl, sa), ta).second else segment(ctrl, sa), true)
        for (s in sa + 1 until sb) put(segment(ctrl, s), false)
        put(if (tb < 1f) splitSegment(segment(ctrl, sb), tb).first else segment(ctrl, sb), false)
        return out.toArray()
    }

    /** Gęstość tablicy krzywej dla [params]: punkt co ok. tyle pt łuku. */
    private const val TABLE_STEP = 0.2f

    /**
     * Parametr globalny każdego punktu kreski [pts] (trójki x, y, p) leżącej wzdłuż krzywej [ctrl] — mapowanie indeksu
     * próbki na `u`. Pierwszy punkt to początek krzywej, ostatni koniec; pozostałe idą monotonicznie, a każdy szuka
     * najbliższego miejsca krzywej tylko nieco dalej niż poprzedni, więc pętle i skrzyżowania nie mylą kolejności.
     * Działa też po przekształceniu kreski i pamięci tą samą macierzą (próbki nie muszą wyjść z [Bezier.sample]).
     */
    fun params(ctrl: FloatArray, pts: FloatArray): FloatArray {
        val n = pts.size / 3
        val out = FloatArray(n)
        val segs = segments(ctrl)
        if (segs <= 0 || n == 0) return out
        // Tablica krzywej: x, y, u i łuk od początku.
        val tx = FloatList(256); val ty = FloatList(256); val tu = FloatList(256); val ts = FloatList(256)
        var arc = 0f
        for (s in 0 until segs) {
            val o = 6 * s
            val len = hypot(ctrl[o + 2] - ctrl[o], ctrl[o + 3] - ctrl[o + 1]) + hypot(ctrl[o + 4] - ctrl[o + 2], ctrl[o + 5] - ctrl[o + 3]) +
                hypot(ctrl[o + 6] - ctrl[o + 4], ctrl[o + 7] - ctrl[o + 5])
            val m = ceil(len / TABLE_STEP).toInt().coerceIn(4, 4000)
            for (k in (if (s == 0) 0 else 1)..m) {
                val u = s + k.toFloat() / m
                val q = point(ctrl, u)
                if (tx.size > 0) arc += hypot(q[0] - tx[tx.size - 1], q[1] - ty[ty.size - 1])
                tx.add(q[0]); ty.add(q[1]); tu.add(u); ts.add(arc)
            }
        }
        val size = tx.size
        var j = 0
        for (i in 1 until n) {
            if (i == n - 1) { out[i] = segs.toFloat(); break }
            val qx = pts[3 * i]; val qy = pts[3 * i + 1]
            val reach = ts[j] + 2f * hypot(qx - pts[3 * i - 3], qy - pts[3 * i - 2]) + 1f
            var best = j
            var bestD = d2(tx[j] - qx, ty[j] - qy)
            var k = j + 1
            while (k < size && ts[k] <= reach) {
                val d = d2(tx[k] - qx, ty[k] - qy)
                if (d < bestD) { bestD = d; best = k }
                k++
            }
            j = best
            out[i] = maxOf(refine(tx, ty, tu, size, best, qx, qy), out[i - 1])
        }
        return out
    }

    private fun d2(dx: Float, dy: Float) = dx * dx + dy * dy

    /** Rzut punktu na dwa odcinki tablicy przy [k]; parametr liniowo między węzłami tablicy. */
    private fun refine(tx: FloatList, ty: FloatList, tu: FloatList, size: Int, k: Int, qx: Float, qy: Float): Float {
        var bestU = tu[k]; var bestD = d2(tx[k] - qx, ty[k] - qy)
        for (a in intArrayOf(k - 1, k)) {
            val b = a + 1
            if (a < 0 || b >= size) continue
            val dx = tx[b] - tx[a]; val dy = ty[b] - ty[a]
            val l2 = dx * dx + dy * dy
            if (l2 <= 0f) continue
            val f = (((qx - tx[a]) * dx + (qy - ty[a]) * dy) / l2).coerceIn(0f, 1f)
            val d = d2(tx[a] + dx * f - qx, ty[a] + dy * f - qy)
            if (d < bestD) { bestD = d; bestU = tu[a] + (tu[b] - tu[a]) * f }
        }
        return bestU
    }

    /** Parametr w ułamkowym indeksie punktu [f] (np. punkt cięcia między próbkami i oraz i+1). */
    fun paramAt(us: FloatArray, f: Float): Float {
        if (us.isEmpty()) return 0f
        val i = floor(f).toInt().coerceIn(0, us.size - 1)
        val j = (i + 1).coerceAtMost(us.size - 1)
        val w = (f - i).coerceIn(0f, 1f)
        return us[i] + (us[j] - us[i]) * w
    }

    /**
     * Pamięć kawałków kreski po gumce: [spans] to pary ułamkowych indeksów (początek, koniec) każdego kawałka w punktach
     * kreski [s]. Kawałek dostaje dokładny odcinek krzywej, którą kreska pamięta albo którą jest; null w liście — bez pamięci.
     */
    fun pieces(s: Stroke, spans: FloatArray): List<FloatArray?> {
        val c = s.ctrl ?: s.memo
        val count = spans.size / 2
        if (c == null || s.poly || segments(c) <= 0) return List(count) { null }
        val us = params(c, s.pts)
        return List(count) { k -> sub(c, paramAt(us, spans[2 * k]), paramAt(us, spans[2 * k + 1])) }
    }
}
