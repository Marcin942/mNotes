package com.mdev.mnotes.core.geom

import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.Template
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Układ współrzędnych na kratce (2b-A6; od 2c-A3 domyślnie wyłączone do sprawdzenia na tablecie): (1) przy wstawianiu początek układu ląduje na węźle kratki
 * strony, a podziałka na wielokrotności oczka; (2) przesuwany lassem układ (pozioma i pionowa prosta, które się
 * przecinają) dociska początek do węzła; (3) odręczna kreska w obszarze układu — fragmenty, które biegną wzdłuż linii
 * siatki (w progu), stają się dokładnie na niej („wykres po kratkach”), a przecięcia linii zostają, jak były.
 */
object AxesSnap {
    /** Kratka strony: pierwsza linia w ([x0], [y0]), oczko [sp] pt. */
    class Grid(val x0: Float, val y0: Float, val sp: Float) {
        fun nodeX(x: Float) = x0 + ((x - x0) / sp).roundToInt() * sp
        fun nodeY(y: Float) = y0 + ((y - y0) / sp).roundToInt() * sp
    }

    /** Kratka jak w `TemplateGeometry` (kratka, kropki, milimetry — cienkie linie); null, gdy strona jej nie ma. */
    fun pageGrid(spec: PageSpec): Grid? {
        if (spec.pdf != null) return null
        return when (spec.template) {
            Template.GRID, Template.DOTS -> { val sp = max(spec.spacing, 3f); Grid((spec.w % sp) / 2f, (spec.h % sp) / 2f, sp) }
            Template.MILLIMETER -> { val major = max(spec.spacing, 10f); Grid((spec.w % major) / 2f, (spec.h % major) / 2f, major / 10f) }
            else -> null
        }
    }

    /**
     * Wstawianie: jednostka [unit] (pt na 1) taka, by krok podziałki [step] był wielokrotnością oczka, i przesunięcie
     * (dx, dy), które kładzie początek ([ox], [oy]) na węźle. Zwraca [jednostka, dx, dy].
     */
    fun alignInsert(unit: Float, step: Float, ox: Float, oy: Float, g: Grid): FloatArray {
        val k = max(1, (unit * step / g.sp).roundToInt())
        val u = k * g.sp / step
        return floatArrayOf(u, g.nodeX(ox) - ox, g.nodeY(oy) - oy)
    }

    /** Układ współrzędnych znaleziony w elementach: początek, obszar (z zapasem) i oczka siatki (null — nie wiadomo). */
    class Frame(val ox: Float, val oy: Float, val area: Bounds, val gx: Float?, val gy: Float?)

    private fun seg(e: Element): FloatArray? = (e as? Stroke)?.takeIf { it.poly && it.count == 2 }?.pts?.let { floatArrayOf(it[0], it[1], it[3], it[4]) }

    /**
     * Układy w [els]: każda para pozioma + pionowa prosta (łamana z dwóch punktów, ≥ 40 pt), które się przecinają
     * i mają podziałkę — co najmniej [MIN_TICKS] krótkich (< 40 pt) poprzecznych kresek przechodzących przez którąś oś
     * (tak wstawia układ narzędzie). Dwie przecięte proste bez podziałki (tabela, ramka) to nie układ (2c-A3).
     * Oczka siatki to najmniejszy odstęp między równoległymi krótszymi odcinkami (znaczniki, linie siatki) w obszarze.
     */
    fun frames(els: List<Element>): List<Frame> {
        val hs = ArrayList<FloatArray>(); val vs = ArrayList<FloatArray>()
        for (e in els) {
            val s = seg(e) ?: continue
            if (abs(s[1] - s[3]) < 0.5f && abs(s[0] - s[2]) >= 1f) hs += floatArrayOf(min(s[0], s[2]), s[1], max(s[0], s[2]))
            else if (abs(s[0] - s[2]) < 0.5f && abs(s[1] - s[3]) >= 1f) vs += floatArrayOf(s[0], min(s[1], s[3]), max(s[1], s[3]))
        }
        val out = ArrayList<Frame>()
        val usedV = HashSet<Int>()
        for (h in hs.sortedByDescending { it[2] - it[0] }) {
            if (h[2] - h[0] < 40f) break
            var best = -1
            for ((i, v) in vs.withIndex()) {
                if (i in usedV || v[2] - v[1] < 40f) continue
                if (v[0] in h[0] - 1f..h[2] + 1f && h[1] in v[1] - 1f..v[2] + 1f && (best < 0 || v[2] - v[1] > vs[best][2] - vs[best][1])) best = i
            }
            if (best < 0) continue
            val v = vs[best]
            val ticks = vs.count { it[2] - it[1] < 40f && it[0] in h[0]..h[2] && it[1] < h[1] && h[1] < it[2] } +
                hs.count { it[2] - it[0] < 40f && it[1] in v[1]..v[2] && it[0] < v[0] && v[0] < it[2] }
            if (ticks < MIN_TICKS) continue
            usedV += best
            val area = Bounds(h[0], v[1], h[2], v[2])
            if (out.any { it.area.contains(v[0], h[1]) }) continue
            // Najmniejszy odstęp (> 2 pt) między położeniami równoległych odcinków w zakresie [lo, hi].
            fun spacing(cs: List<Float>, lo: Float, hi: Float): Float? =
                cs.filter { it in lo - 1f..hi + 1f }.sorted().zipWithNext { a, b -> b - a }.filter { it > 2f }.minOrNull()
            val gx = spacing(vs.filter { it !== v && it[1] >= area.t - 1f && it[2] <= area.b + 1f }.map { it[0] } + v[0], area.l, area.r)
            val gy = spacing(hs.filter { it !== h && it[0] >= area.l - 1f && it[2] <= area.r + 1f }.map { it[1] } + h[1], area.t, area.b)
            out += Frame(v[0], h[1], area, gx, gy)
        }
        return out
    }

    /** Przesunięcie (dx, dy) zaznaczenia poprawione tak, by początek układu ([ox], [oy]) wylądował na węźle kratki [g]. */
    fun snapMove(ox: Float, oy: Float, dx: Float, dy: Float, g: Grid): FloatArray =
        floatArrayOf(g.nodeX(ox + dx) - ox, g.nodeY(oy + dy) - oy)

    /** Ile poprzecznych znaczników musi przecinać osie, żeby krzyż był układem (wstawiony ma ich zwykle kilkanaście). */
    const val MIN_TICKS = 2

    /**
     * Największe nachylenie przebiegu, który się jeszcze spłaszcza (2c-A3). Przegląd: poniżej 3°; próg 2°, bo dokładnie
     * 3° z drżeniem ręki daje nachylenie regresji raz pod, raz nad progiem, a taka prosta ma zostać prostą.
     */
    val MAX_SLOPE: Float = kotlin.math.tan(Math.toRadians(2.0)).toFloat()

    /**
     * Odręczna kreska [pts] (trójki x, y, p) dociśnięta do siatki układu [f]: biegi kolejnych punktów w odległości ≤ [thr]
     * od tej samej linii siatki, które ciągną się wzdłuż niej co najmniej [minRun] pt i są do niej równoległe
     * (nachylenie regresji < [MAX_SLOPE]), kładą się dokładnie na linii. Kreska tuż przed i tuż za biegiem (na długości
     * 2·[thr]) dochodzi do linii płynnie, bez uskoku. Null, gdy nic się nie zmieniło (kreska poza układem, ukośna,
     * przecina linie, układ bez znanej siatki).
     */
    fun followGrid(pts: FloatArray, f: Frame, thr: Float, minRun: Float = max(3f * thr, 0.5f * min(f.gx ?: 1e9f, f.gy ?: 1e9f))): FloatArray? {
        val n = pts.size / 3
        if (n < 2 || thr <= 0f) return null
        val box = Bounds(f.area.l - thr, f.area.t - thr, f.area.r + thr, f.area.b + thr)
        val out = pts.copyOf()
        var changed = false
        // Długość łuku do każdego punktu: przejście mierzone wzdłuż kreski, nie wzdłuż osi.
        val arc = FloatArray(n)
        for (m in 1 until n) arc[m] = arc[m - 1] + kotlin.math.hypot(pts[3 * m] - pts[3 * m - 3], pts[3 * m + 1] - pts[3 * m - 2])
        val ramp = 2f * thr
        // axis 0: linie pionowe (x = ox + k·gx, bieg wzdłuż y), 1: poziome.
        for (axis in 0..1) {
            val g = (if (axis == 0) f.gx else f.gy) ?: continue
            val o = if (axis == 0) f.ox else f.oy
            val fixed = BooleanArray(n)
            var i = 0
            while (i < n) {
                val c = pts[3 * i + axis]
                val k = ((c - o) / g).roundToInt()
                if (!box.contains(pts[3 * i], pts[3 * i + 1]) || abs(c - (o + k * g)) > thr) { i++; continue }
                var j = i
                while (j + 1 < n && box.contains(pts[3 * (j + 1)], pts[3 * (j + 1) + 1]) &&
                    ((pts[3 * (j + 1) + axis] - o) / g).roundToInt() == k && abs(pts[3 * (j + 1) + axis] - (o + k * g)) <= thr) j++
                val along = abs(pts[3 * j + 1 - axis] - pts[3 * i + 1 - axis])
                if (along >= minRun && abs(slope(pts, i, j, axis)) < MAX_SLOPE) {
                    val line = o + k * g
                    for (m in i..j) { out[3 * m + axis] = line; fixed[m] = true }
                    // Uskok na brzegu biegu rozkłada się liniowo na sąsiednie punkty.
                    val dIn = line - pts[3 * i + axis]; val dOut = line - pts[3 * j + axis]
                    var m = i - 1
                    while (m >= 0 && !fixed[m] && arc[i] - arc[m] < ramp) { out[3 * m + axis] = pts[3 * m + axis] + dIn * (1f - (arc[i] - arc[m]) / ramp); m-- }
                    m = j + 1
                    while (m < n && arc[m] - arc[j] < ramp) { out[3 * m + axis] = pts[3 * m + axis] + dOut * (1f - (arc[m] - arc[j]) / ramp); m++ }
                    changed = true
                }
                i = j + 1
            }
        }
        return if (changed) out else null
    }

    /** Nachylenie regresji współrzędnej [axis] względem drugiej na punktach [i]..[j] (0, gdy bieg nie ma długości). */
    private fun slope(pts: FloatArray, i: Int, j: Int, axis: Int): Float {
        val cnt = j - i + 1
        var sa = 0.0; var sp = 0.0
        for (m in i..j) { sa += pts[3 * m + 1 - axis]; sp += pts[3 * m + axis] }
        val ma = sa / cnt; val mp = sp / cnt
        var cov = 0.0; var va = 0.0
        for (m in i..j) { val da = pts[3 * m + 1 - axis] - ma; cov += da * (pts[3 * m + axis] - mp); va += da * da }
        return if (va < 1e-9) 0f else (cov / va).toFloat()
    }

    /** Czy dopasowanie jednostki do kratki zmieniło ją wyraźnie (o > 2 %) — wtedy edytor mówi o tym dymkiem. */
    fun unitChanged(before: Float, after: Float): Boolean = abs(after - before) > 0.02f * before
}
