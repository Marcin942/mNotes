// Algorytmy kredy na podstawie programu kreda — autor i licencja do potwierdzenia przed publikacją (app/src/kreda/NOTICE.md).

package com.mdev.mnotes.core.image

import com.mdev.mnotes.core.geom.FloatList
import com.mdev.mnotes.core.shapes.Bezier
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Kreska z kredy przed dopasowaniem krzywych (2b-B4): punkty szkieletu po wygładzeniu (co ok. 1 px wyniku), indeksy
 * narożników (tam krzywa dostaje ostry węzeł), grubość w px i czy ścieżka jest pętlą. Pętla nie powtarza pierwszego
 * punktu na końcu.
 */
class ChalkPath(val pts: FloatArray, val corners: IntArray, val width: Float, val closed: Boolean) {
    val size get() = pts.size / 2
    fun length(): Float {
        var s = 0f
        for (i in 1 until size) s += hypot(pts[2 * i] - pts[2 * i - 2], pts[2 * i + 1] - pts[2 * i - 1])
        if (closed && size > 1) s += hypot(pts[0] - pts[2 * size - 2], pts[1] - pts[2 * size - 1])
        return s
    }
}

/**
 * Kreda jako gładkie krzywe Béziera (2b-B4, „wzory po kredzie są postrzępione”). Łamana po pikselach szkieletu ma zęby:
 * każdy schodek szkieletu i każdy wierzchołek uproszczenia to załamanie kreski, widoczne po powiększeniu. Teraz:
 * 1. szkielet → łamane jak w [ChalkVector] (te same porządki w węzłach i łączenie przerw);
 * 2. narożniki: kierunek skręca o ≥ [CORNER_DEGREES]° i skręt skupia się w jednym miejscu: na pół ramienia zostaje
 *    ≥ [CORNER_RATIO] skrętu, a sąsiedzi o ramię dalej skręcają ≤ [SIDE_RATIO] tyle (na okręgu skręcają tyle samo,
 *    przy narożniku mają proste ramiona). Szkielet zaokrągla wierzchołek, więc zmierzony skręt jest o ok. 20 % mniejszy
 *    od kąta narysowanego: 45° zmierzone to ok. 55–60° na tablicy;
 * 3. wygładzenie filtrem dwumianowym 1‑4‑6‑4‑1 (średnia ważona z 5 px) osobno między narożnikami; końce i narożniki stoją;
 * 4. dopasowanie sześciennych krzywych Béziera ([Bezier.fit], metoda Schneidera) z tolerancją [tolFor]; w narożniku
 *    odcinki stykają się pod kątem (węzeł ostry), wszędzie indziej mają wspólną styczną (węzeł gładki, bez zęba).
 * Progi dobrane na kształtach syntetycznych z chropowatym brzegiem (`ChalkCurvesTest`): ∑, Z, L, V mają narożniki,
 * okręgi o promieniu 5–40 px, sinusoida, S i prosta po schodkach — żadnego.
 */
object ChalkCurves {

    /** Skręt kierunku (stopnie, zmierzony na szkielecie), od którego miejsce na kresce może być narożnikiem. */
    const val CORNER_DEGREES = 45.0

    /** Na łuku skręt na połowie ramion to połowa skrętu; w narożniku zostaje co najmniej tyle. */
    const val CORNER_RATIO = 0.65

    /** Skręt sąsiadów o ramię dalej (część skrętu w punkcie), powyżej którego to łuk, a nie narożnik. */
    const val SIDE_RATIO = 0.55

    /** Najkrótsze ramię (px), na którym mierzymy skręt; grubsza kreska ma ramię równe grubości. */
    private const val CORNER_ARM = 5f

    /**
     * Tolerancja dopasowania (px) przy dłuższym boku wyniku 2400 px. Niżej nie schodzimy poniżej 1 px: szkielet ma szum
     * kwantyzacji ±0,5 px (schodki), a ciaśniejsza tolerancja dzieliła prostą kreskę na kilka odcinków.
     */
    const val TOL_2400 = 1.2f

    /** Najkrótszy i najdłuższy odcinek (px), z którego liczymy styczną na końcu kawałka. */
    private const val TANGENT_ARM = 3f
    private const val TANGENT_MAX = 10f

    fun tolFor(w: Int, h: Int): Float = (TOL_2400 * max(w, h) / 2400f).coerceIn(1f, 1.5f)

    private fun lengthOf(p: FloatArray): Float {
        var s = 0f
        for (i in 1 until p.size / 2) s += hypot(p[2 * i] - p[2 * i - 2], p[2 * i + 1] - p[2 * i - 1])
        return s
    }

    /** Gładkie ścieżki kredy z obrazu [img] (krycie ≥ [alphaMin]); krótsze niż [minLength] px odpadają jak w [ChalkVector.vectorize]. */
    fun paths(img: Raster, alphaMin: Int = 128, minLength: Float = 6f): List<ChalkPath> =
        ChalkVector.skeleton(img, alphaMin).filter { it.length() >= minLength }.map { smooth(it.pts, it.width) }

    /** Ścieżka z łamanej szkieletu [raw] (piksele, x, y …): bez powtórzeń, z narożnikami, wygładzona między nimi. */
    fun smooth(raw: FloatArray, width: Float): ChalkPath {
        val xy = FloatList(raw.size)
        for (i in 0 until raw.size / 2) {
            val x = raw[2 * i]; val y = raw[2 * i + 1]
            // łańcuchy z ChalkVector.join powtarzają piksel węzła
            if (xy.size >= 2 && hypot(x - xy[xy.size - 2], y - xy[xy.size - 1]) < 0.5f) continue
            xy.add(x, y)
        }
        var p = xy.toArray()
        var n = p.size / 2
        val closed = n >= 8 && hypot(p[0] - p[2 * n - 2], p[1] - p[2 * n - 1]) <= 1.5f
        if (closed && p[0] == p[2 * n - 2] && p[1] == p[2 * n - 1]) { p = p.copyOf(2 * n - 2); n-- }
        if (n < 3) return ChalkPath(p, IntArray(0), width, false)
        val corners = corners(binomial(p, closed, IntArray(0)), closed, max(CORNER_ARM, width))
        return ChalkPath(binomial(p, closed, corners), corners, width, closed)
    }

    /**
     * Filtr dwumianowy 1‑4‑6‑4‑1 wzdłuż ścieżki. Punkty [fixed] (narożniki) i końce ścieżki otwartej stoją; obok nich
     * okno maleje symetrycznie (1‑2‑1, potem bez zmian), więc filtr nie przenosi kształtu przez narożnik ani nie skraca kreski.
     */
    fun binomial(p: FloatArray, closed: Boolean, fixed: IntArray): FloatArray {
        val n = p.size / 2
        val out = p.copyOf()
        val stops = fixed.sorted()
        for (i in 0 until n) {
            var r = 2
            if (stops.isNotEmpty()) for (c in stops) {
                val ahead = if (closed) Math.floorMod(c - i, n) else c - i
                val back = if (closed) Math.floorMod(i - c, n) else i - c
                if (ahead >= 0) r = min(r, ahead)
                if (back >= 0) r = min(r, back)
            }
            if (!closed) r = min(r, min(i, n - 1 - i))
            if (r <= 0) continue
            fun x(k: Int) = p[2 * Math.floorMod(i + k, n)]
            fun y(k: Int) = p[2 * Math.floorMod(i + k, n) + 1]
            if (r == 1) {
                out[2 * i] = (x(-1) + 2f * x(0) + x(1)) / 4f
                out[2 * i + 1] = (y(-1) + 2f * y(0) + y(1)) / 4f
            } else {
                out[2 * i] = (x(-2) + 4f * x(-1) + 6f * x(0) + 4f * x(1) + x(2)) / 16f
                out[2 * i + 1] = (y(-2) + 4f * y(-1) + 6f * y(0) + 4f * y(1) + y(2)) / 16f
            }
        }
        return out
    }

    /** Indeks punktu o ≥ [arm] px po łuku od [i] w kierunku [step] (±1); null, gdy ścieżka otwarta kończy się wcześniej. */
    private fun reach(p: FloatArray, closed: Boolean, i: Int, step: Int, arm: Float): Int? {
        val n = p.size / 2
        var s = 0f; var cur = i
        for (k in 0 until n / 2) {
            val next = cur + step
            val nx = if (closed) Math.floorMod(next, n) else next
            if (nx !in 0 until n) return null
            s += hypot(p[2 * nx] - p[2 * cur], p[2 * nx + 1] - p[2 * cur + 1])
            cur = nx
            if (s >= arm) return cur
        }
        return null
    }

    /** Skręt kierunku w punkcie [i] (stopnie) między ramionami długości [arm] wstecz i naprzód; 0 przy końcu ścieżki. */
    fun turn(p: FloatArray, closed: Boolean, i: Int, arm: Float): Double {
        val j = reach(p, closed, i, -1, arm) ?: return 0.0
        val k = reach(p, closed, i, 1, arm) ?: return 0.0
        val ax = p[2 * i] - p[2 * j]; val ay = p[2 * i + 1] - p[2 * j + 1]
        val bx = p[2 * k] - p[2 * i]; val by = p[2 * k + 1] - p[2 * i + 1]
        val la = hypot(ax, ay); val lb = hypot(bx, by)
        if (la < 1e-4f || lb < 1e-4f) return 0.0
        return Math.toDegrees(acos(((ax * bx + ay * by) / (la * lb)).toDouble().coerceIn(-1.0, 1.0)))
    }

    /**
     * Narożniki ścieżki [p] (już lekko wygładzonej): skręt na ramionach [arm] ≥ [CORNER_DEGREES], na połowie ramion
     * ≥ [CORNER_RATIO] tego skrętu, a w punktach o ramię dalej ≤ [SIDE_RATIO] tego skrętu. W skupisku sąsiednich
     * kandydatów (bliżej niż ramię) zostaje ten o największym skręcie.
     */
    fun corners(p: FloatArray, closed: Boolean, arm: Float): IntArray {
        val n = p.size / 2
        val all = DoubleArray(n) { turn(p, closed, it, arm) }
        val t = DoubleArray(n)
        val cand = ArrayList<Int>()
        for (i in 0 until n) {
            val full = all[i]
            if (full < CORNER_DEGREES) continue
            if (turn(p, closed, i, arm / 2f) < CORNER_RATIO * full) continue
            // sąsiedzi o ramię dalej: na łuku skręcają tak samo jak punkt, przy narożniku ich ramiona są proste
            val side = max(reach(p, closed, i, -1, arm)?.let { all[it] } ?: 0.0, reach(p, closed, i, 1, arm)?.let { all[it] } ?: 0.0)
            if (side > SIDE_RATIO * full) continue
            t[i] = full; cand += i
        }
        if (cand.isEmpty()) return IntArray(0)
        val out = ArrayList<Int>()
        val gap = max(2, arm.toInt())
        var best = cand[0]; var last = cand[0]
        for (k in 1 until cand.size) {
            val i = cand[k]
            if (i - last <= gap) { if (t[i] > t[best]) best = i } else { out += best; best = i }
            last = i
        }
        out += best
        // w pętli pierwsze i ostatnie skupisko mogą być jednym (przez szew)
        if (closed && out.size >= 2 && out[0] + n - out.last() <= gap) {
            if (t[out[0]] >= t[out.last()]) out.removeAt(out.size - 1) else out.removeAt(0)
        }
        return out.toIntArray()
    }

    /** Kierunek od punktu [from] do punktu o ≥ [span] px dalej w stronę [to] (bez wychodzenia poza [to]). */
    private fun dir(p: FloatArray, from: Int, to: Int, span: Float): FloatArray {
        val step = if (to > from) 1 else -1
        var s = 0f; var k = from
        while (k != to && s < span) {
            s += hypot(p[2 * (k + step)] - p[2 * k], p[2 * (k + step) + 1] - p[2 * k + 1]); k += step
        }
        return floatArrayOf(p[2 * k] - p[2 * from], p[2 * k + 1] - p[2 * from + 1])
    }

    /** Krzywa ścieżki [path] w zapisie [Bezier] (px wyniku); null dla ścieżki z mniej niż 2 punktami. */
    fun fit(path: ChalkPath, tol: Float): FloatArray? = fitRun(path.pts, path.corners, path.closed, tol)

    /**
     * Dopasowanie krzywych do punktów [pts] (x, y …) z narożnikami [corners] (indeksy punktów). Pętla [closed] bez
     * narożników kończy się dokładnie w początku z tą samą styczną (krzywa zamknięta jak w 2b-A10); pętla z narożnikami
     * zaczyna się i kończy w pierwszym z nich.
     */
    fun fitRun(pts: FloatArray, corners: IntArray, closed: Boolean, tol: Float): FloatArray? {
        val n = pts.size / 2
        if (n < 2) return null
        if (!closed) return fitOpen(pts, corners.filter { it in 1 until n - 1 }.sorted(), tol)
        if (n < 4) return fitOpen(pts, emptyList(), tol)
        if (corners.isEmpty()) {
            val loop = pts.copyOf(2 * n + 2).also { it[2 * n] = pts[0]; it[2 * n + 1] = pts[1] }
            // styczna w punkcie 0 z sąsiadów po obu stronach szwu
            val span = (lengthOf(loop) / 12f).coerceIn(TANGENT_ARM, TANGENT_MAX)
            val a = reach(pts, true, 0, 1, span) ?: 1
            val b = reach(pts, true, 0, -1, span) ?: (n - 1)
            val tx = pts[2 * a] - pts[2 * b]; val ty = pts[2 * a + 1] - pts[2 * b + 1]
            val c = Bezier.fit(loop, tol, floatArrayOf(tx, ty), floatArrayOf(-tx, -ty)) ?: return null
            pin(c, pts[0], pts[1])
            return c
        }
        val s = corners.minOrNull()!!
        val rot = FloatArray(2 * n + 2) { pts[Math.floorMod(2 * s + it, 2 * n)] }
        val cs = corners.map { Math.floorMod(it - s, n) }.filter { it in 1 until n }.sorted()
        return fitOpen(rot, cs, tol)
    }

    /** Kawałki między końcami i narożnikami osobno, z kierunkami z własnych punktów; w narożniku węzeł ostry. */
    private fun fitOpen(p: FloatArray, corners: List<Int>, tol: Float): FloatArray? {
        val n = p.size / 2
        val cuts = listOf(0) + corners + (n - 1)
        val out = FloatList(64)
        for (k in 0 until cuts.size - 1) {
            val a = cuts[k]; val b = cuts[k + 1]
            if (b <= a) continue
            val piece = p.copyOfRange(2 * a, 2 * b + 2)
            // Styczna z odcinka do 1/3 kawałka (najwyżej TANGENT_MAX px): z 3 px schodka szkieletu kierunek bywał pozioma
            // zamiast 8°, a wtedy nawet prosta kreska dzieliła się na trzy odcinki.
            val span = (lengthOf(piece) / 3f).coerceIn(TANGENT_ARM, TANGENT_MAX)
            val c = Bezier.fit(piece, tol, dir(p, a, b, span), dir(p, b, a, span)) ?: continue
            pin(c, p[2 * b], p[2 * b + 1])
            val from = if (out.size == 0) 0 else 2
            for (i in from until c.size) out.add(c[i])
        }
        return if (out.size >= 8) out.toArray() else null
    }

    /**
     * Ostatnia kotwica [c] dokładnie w (x, y) razem ze swoim uchwytem: [Bezier.fit] pomija punkty bliższe niż 0,6 od
     * poprzedniego, więc koniec kawałka mógł wypaść tuż przed narożnikiem, a następny kawałek zaczyna się w nim.
     */
    private fun pin(c: FloatArray, x: Float, y: Float) {
        val m = c.size
        val dx = x - c[m - 2]; val dy = y - c[m - 1]
        c[m - 2] = x; c[m - 1] = y
        if (m >= 4) { c[m - 4] += dx; c[m - 3] += dy }
    }
}
