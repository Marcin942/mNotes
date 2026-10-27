package com.mdev.mnotes.core.shapes

import com.mdev.mnotes.core.geom.GeomMath
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Auto-korekta symboli matematycznych (F28, tryb geometryczny): odręczny √, ∑ i ∫ po przytrzymaniu pióra zamienia się
 * w równy symbol tej samej wielkości i w tym samym miejscu (prostokąt otaczający zostaje). Rozpoznanie opiera się na
 * kolejności i kierunkach odcinków, a nie na modelu — model on-device to wariant lab.
 */
object MathGlyphs {
    enum class Kind { ROOT, SUM, INTEGRAL }

    /** Wynik: rodzaj, łamana (x, y …) dla √ i ∑ albo punkty kontrolne krzywej dla ∫. */
    class Glyph(val kind: Kind, val xy: FloatArray, val curve: Boolean)

    fun recognize(xyIn: FloatArray, n: Int = xyIn.size / 2, root: Boolean, sum: Boolean, integral: Boolean): Glyph? {
        if (n < 6) return null
        val xy = xyIn.copyOf(2 * n)
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in 0 until n) { l = min(l, xy[2 * i]); r = max(r, xy[2 * i]); t = min(t, xy[2 * i + 1]); b = max(b, xy[2 * i + 1]) }
        val w = r - l; val h = b - t
        if (max(w, h) < 10f) return null
        if (integral) integral(xy, n, l, t, r, b)?.let { return it }
        val idx = GeomMath.simplify(xy, n, max(2f, 0.08f * max(w, h)))
        val v = FloatArray(2 * idx.size) { xy[2 * idx[it / 2] + it % 2] }
        if (root) root(v, l, t, r, b)?.let { return it }
        if (sum) sum(v, l, t, r, b)?.let { return it }
        return null
    }

    private fun ang(v: FloatArray, i: Int): Float = atan2(v[2 * i + 3] - v[2 * i + 1], v[2 * i + 2] - v[2 * i])
    private fun len(v: FloatArray, i: Int): Float = hypot(v[2 * i + 2] - v[2 * i], v[2 * i + 3] - v[2 * i + 1])
    private fun deg(a: Float) = Math.toDegrees(a.toDouble()).toFloat()

    /**
     * √: na końcu długa pozioma kreska u góry (w prawo), przed nią stromy wzlot od najniższego punktu; początek po lewej,
     * wyżej niż dno. Wynik: ząbek, dno, szczyt, koniec kreski.
     */
    private fun root(v: FloatArray, l: Float, t: Float, r: Float, b: Float): Glyph? {
        val k = v.size / 2
        if (k !in 3..6) return null
        val w = r - l; val h = b - t
        val last = k - 2
        val barAng = deg(ang(v, last))
        // Kreska co najmniej pół szerokości: krótsza to zwykle „v” z łącznikiem do następnej litery (A2-4).
        if (abs(barAng) > 18f || len(v, last) < 0.5f * w) return null
        if (v[2 * last + 1] - t > 0.25f * h) return null                 // kreska u góry
        val rise = deg(ang(v, last - 1))
        if (rise > -35f || rise < -110f || len(v, last - 1) < 0.45f * h) return null   // stromo w górę (y maleje)
        val bottom = last - 1
        if (b - v[2 * bottom + 1] > 0.2f * h) return null                // wzlot zaczyna się na dole
        if (v[1] > b - 0.15f * h || v[0] - l > 0.25f * w) return null   // początek po lewej, wyżej niż dno
        val y0 = t + 0.62f * h
        val out = floatArrayOf(
            l, y0 + 0.05f * h, l + 0.07f * w, y0,          // krótki ząbek
            l + 0.2f * w, b,                               // dno
            l + 0.34f * w, t,                              // szczyt
            r, t,                                          // pozioma kreska
        )
        return Glyph(Kind.ROOT, out, false)
    }

    /** ∑: góra w lewo, ukos w dół-w prawo do środka, ukos w dół-w lewo, dół w prawo. */
    private fun sum(v: FloatArray, l: Float, t: Float, r: Float, b: Float): Glyph? {
        val k = v.size / 2
        if (k != 5) return null
        val w = r - l; val h = b - t
        if (h < 0.5f * w || h > 3f * w) return null
        val a = FloatArray(4) { deg(ang(v, it)) }
        // Węższe zakresy niż na początku (A2-4): pismo podobne do ∑ (ε, „3” od tyłu) ma ukosy bardziej strome albo płaskie.
        val topOk = abs(abs(a[0]) - 180f) < 22f && len(v, 0) > 0.45f * w
        val d1 = a[1] in 25f..70f
        val d2 = a[2] in 110f..155f
        val botOk = abs(a[3]) < 22f && len(v, 3) > 0.45f * w
        if (!(topOk && d1 && d2 && botOk)) return null
        if (v[1] - t > 0.2f * h || b - v[7] > 0.2f * h) return null
        if (v[4] - l < 0.3f * w) return null                              // środkowy wierzchołek wyraźnie w prawo
        val out = floatArrayOf(r, t, l, t, l + 0.55f * w, (t + b) / 2f, l, b, r, b)
        return Glyph(Kind.SUM, out, false)
    }

    /**
     * ∫: wysoki znak od góry-prawej do dołu-lewej z haczykami na obu końcach (warianty z ✓ w zeszycie). Prosta ukośna
     * kreska bez haczyków (warianty z ✗) zostaje zwykłą linią. Wynik: krzywa Béziera w tym samym prostokącie.
     */
    private fun integral(xy: FloatArray, n: Int, l: Float, t: Float, r: Float, b: Float): Glyph? {
        val w = r - l; val h = b - t
        if (h < 2.2f * w || h < 20f) return null
        val sx = xy[0]; val sy = xy[1]; val ex = xy[2 * n - 2]; val ey = xy[2 * n - 1]
        if (sy - t > 0.25f * h || b - ey > 0.25f * h) return null       // od góry do dołu
        if (sx <= ex) return null                                         // od prawej do lewej
        // Haczyki: przy górze kreska idzie w lewo (od początku do najwyższego punktu x maleje lub idzie w górę-w lewo),
        // przy dole też w lewo — a w środku prawie pionowo.
        var top = 0; var bot = 0
        for (i in 0 until n) { if (xy[2 * i + 1] < xy[2 * top + 1]) top = i; if (xy[2 * i + 1] > xy[2 * bot + 1]) bot = i }
        val chordDev = maxDev(xy, n)
        if (chordDev < 0.05f * h) return null                             // prosta kreska — wariant z ✗
        // ∫ przez całą drogę idzie w lewo (haczyki też); ε albo „3” od tyłu wracają w prawo do środka (A2-4).
        var minX = sx; var rebound = 0f
        for (i in 1 until n) { minX = min(minX, xy[2 * i]); rebound = max(rebound, xy[2 * i] - minX) }
        if (rebound > max(0.2f * w, 2f)) return null
        val topHook = sx - xy[2 * top] > 0.15f * w || (top > 0 && sy - xy[2 * top + 1] > 0.02f * h)
        val botHook = xy[2 * bot] - ex > 0.15f * w || (bot < n - 1 && xy[2 * bot + 1] - ey > 0.02f * h)
        if (!topHook || !botHook) return null
        val cx = (l + r) / 2f
        val c = floatArrayOf(
            r, t + 0.07f * h,
            r - 0.02f * w, t - 0.01f * h, cx + 0.15f * w, t - 0.02f * h,
            cx + 0.08f * w, t + 0.18f * h,
            cx, t + 0.42f * h, cx, t + 0.58f * h,
            cx - 0.08f * w, b - 0.18f * h,
            cx - 0.15f * w, b + 0.02f * h, l + 0.02f * w, b + 0.01f * h,
            l, b - 0.07f * h,
        )
        return Glyph(Kind.INTEGRAL, c, true)
    }

    /** Największa odległość punktów od cięciwy początek–koniec. */
    private fun maxDev(xy: FloatArray, n: Int): Float {
        var m = 0f
        for (i in 1 until n - 1) m = max(m, kotlin.math.sqrt(GeomMath.distSqPointSegment(xy[2 * i], xy[2 * i + 1], xy[0], xy[1], xy[2 * n - 2], xy[2 * n - 1])))
        return m
    }
}
