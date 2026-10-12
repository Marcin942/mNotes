package com.mdev.mnotes.core.shapes

import com.mdev.mnotes.core.model.Stroke
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Zorientowana ramka kształtu: środek ([cx], [cy]), jednostkowe osie [ux],[uy] (szerokość) i [vx],[vy] (wysokość)
 * oraz połowy rozmiarów [hw], [hh]. Prostokąt może być obrócony, elipsa też (po obrocie zaznaczenia).
 */
class ShapeFrame(
    val cx: Float, val cy: Float,
    val ux: Float, val uy: Float,
    val vx: Float, val vy: Float,
    val hw: Float, val hh: Float,
) {
    /** Punkt ramki w punktach strony dla współrzędnych lokalnych (a, b) wyrażonych w połowach rozmiarów (−1..1). */
    fun x(a: Float, b: Float) = cx + ux * a * hw + vx * b * hh
    fun y(a: Float, b: Float) = cy + uy * a * hw + vy * b * hh

    /** Współrzędna wzdłuż osi szerokości dla punktu strony, względem środka (w pt). */
    fun localU(x: Float, y: Float) = (x - cx) * ux + (y - cy) * uy
    fun localV(x: Float, y: Float) = (x - cx) * vx + (y - cy) * vy
}

/**
 * Zmiana szerokości i wysokości rozpoznanych kształtów: koła → elipsy, kwadratu → prostokąta.
 * Ma 8 uchwytów (rogi i środki boków); każdy przeciąga swoją krawędź, a przeciwległa zostaje na miejscu.
 */
object ShapeBox {

    /** Uchwyty w kolejności: LG, G, PG, P, PD, D, LD, L (kierunki −1/0/1 po osiach szerokości i wysokości). */
    val HX = intArrayOf(-1, 0, 1, 1, 1, 0, -1, -1)
    val HY = intArrayOf(-1, -1, -1, 0, 1, 1, 1, 0)
    const val HANDLES = 8
    const val MIN_SIZE = 4f

    fun isCorner(h: Int) = HX[h] != 0 && HY[h] != 0

    /** Ramka kształtu albo null, gdy kreska nie jest (już) idealnym prostokątem ani elipsą z autokorekty. */
    fun frameOf(s: Stroke): ShapeFrame? {
        if (s.locked) return null
        return if (s.ellipse) ellipseFrame(s) else if (s.poly) rectFrame(s) else null
    }

    private fun ellipseFrame(s: Stroke): ShapeFrame? {
        val n = s.count
        if (n < 9 || (n - 1) % 4 != 0) return null
        val p = s.pts
        val m = n - 1
        var sx = 0f; var sy = 0f
        for (i in 0 until m) { sx += p[3 * i]; sy += p[3 * i + 1] }
        val cx = sx / m; val cy = sy / m
        val q = m / 4
        val ax = p[0] - cx; val ay = p[1] - cy
        val bx = p[3 * q] - cx; val by = p[3 * q + 1] - cy
        val hw = hypot(ax, ay); val hh = hypot(bx, by)
        if (hw < 0.5f || hh < 0.5f) return null
        val ux = ax / hw; val uy = ay / hw
        val vx = bx / hh; val vy = by / hh
        if (abs(ux * vx + uy * vy) > 0.02f) return null
        return ShapeFrame(cx, cy, ux, uy, vx, vy, hw, hh)
    }

    private fun rectFrame(s: Stroke): ShapeFrame? {
        if (s.count != 5 || !s.closed) return null
        val p = s.pts
        val e1x = p[3] - p[0]; val e1y = p[4] - p[1]
        val e2x = p[6] - p[3]; val e2y = p[7] - p[4]
        val w = hypot(e1x, e1y); val h = hypot(e2x, e2y)
        if (w < 0.5f || h < 0.5f) return null
        val ux = e1x / w; val uy = e1y / w
        val vx = e2x / h; val vy = e2y / h
        if (abs(ux * vx + uy * vy) > 0.01f) return null
        // Równoległobok: bok 2→3 równy bokowi 0→1 odwróconemu, bok 3→0 równy bokowi 1→2 odwróconemu.
        val tol = 0.15f + 0.005f * max(w, h)
        if (hypot(p[9] - p[6] + e1x, p[10] - p[7] + e1y) > tol) return null
        if (hypot(p[0] - p[9] + e2x, p[1] - p[10] + e2y) > tol) return null
        val cx = (p[0] + p[3] + p[6] + p[9]) / 4f; val cy = (p[1] + p[4] + p[7] + p[10]) / 4f
        return ShapeFrame(cx, cy, ux, uy, vx, vy, w / 2f, h / 2f)
    }

    /** Pozycja uchwytu [h] na stronie. */
    fun handleX(f: ShapeFrame, h: Int) = f.x(HX[h].toFloat(), HY[h].toFloat())
    fun handleY(f: ShapeFrame, h: Int) = f.y(HX[h].toFloat(), HY[h].toFloat())

    /**
     * Nowy rozmiar po przeciągnięciu uchwytu [h] w punkt (px, py) (punkt strony, już z korektą miejsca chwytu).
     * Przeciwległa krawędź (lub róg) stoi w miejscu, kształt nie odwraca się i nie schodzi poniżej [MIN_SIZE].
     * Gdy oba wymiary różnią się mniej niż [snapTol] pt, wyrównujemy je (kwadrat / koło). Zwraca ramkę wynikową;
     * [applyTo] przelicza na nią punkty kreski.
     */
    fun resized(f: ShapeFrame, h: Int, px: Float, py: Float, snapTol: Float): ShapeFrame {
        val lx = f.localU(px, py); val ly = f.localV(px, py)
        val hx = HX[h]; val hy = HY[h]
        var nhw = f.hw; var nhh = f.hh
        var ca = 0f; var cb = 0f // przesunięcie środka względem starego, w lokalnych pt
        if (hx != 0) { nhw = max(MIN_SIZE / 2f, hx * (lx + hx * f.hw) / 2f); ca = -hx * f.hw + hx * nhw }
        if (hy != 0) { nhh = max(MIN_SIZE / 2f, hy * (ly + hy * f.hh) / 2f); cb = -hy * f.hh + hy * nhh }
        if (abs(2f * nhw - 2f * nhh) < snapTol) {
            // Wyrównanie: przy rogu średnia, przy boku wymiar ruszany dopasowuje się do nieruszanego.
            val t = when {
                hx != 0 && hy != 0 -> (nhw + nhh) / 2f
                hx != 0 -> nhh
                else -> nhw
            }
            if (hx != 0) { nhw = t; ca = -hx * f.hw + hx * nhw }
            if (hy != 0) { nhh = t; cb = -hy * f.hh + hy * nhh }
        }
        val ncx = f.cx + f.ux * ca + f.vx * cb
        val ncy = f.cy + f.uy * ca + f.vy * cb
        return ShapeFrame(ncx, ncy, f.ux, f.uy, f.vx, f.vy, nhw, nhh)
    }

    /** Ramka prostokąta z jego pięciu wierzchołków (x, y …; wynik rozpoznawania), albo null, gdy to nie prostokąt. */
    fun frameOfCorners(xy: FloatArray): ShapeFrame? {
        if (xy.size < 10) return null
        val e1x = xy[2] - xy[0]; val e1y = xy[3] - xy[1]
        val e2x = xy[4] - xy[2]; val e2y = xy[5] - xy[3]
        val w = hypot(e1x, e1y); val h = hypot(e2x, e2y)
        if (w < 0.5f || h < 0.5f) return null
        val cx = (xy[0] + xy[2] + xy[4] + xy[6]) / 4f; val cy = (xy[1] + xy[3] + xy[5] + xy[7]) / 4f
        return ShapeFrame(cx, cy, e1x / w, e1y / w, e2x / h, e2y / h, w / 2f, h / 2f)
    }

    /**
     * Regulacja prostokąta piórem trzymanym po rozpoznaniu: środek stoi, a przesunięcie pióra z punktu osiadnięcia
     * ([downX], [downY]) do ([px], [py]) rozkłada się na osie ramki — ruch wzdłuż szerokości zmienia tylko szerokość,
     * wzdłuż wysokości tylko wysokość (narożnik z pierwotnej strony pióra podąża za nim). Gdy wymiary różnią się mniej
     * niż [snapTol] pt, wyrównujemy je (kwadrat).
     */
    fun dragged(f: ShapeFrame, downX: Float, downY: Float, px: Float, py: Float, snapTol: Float): ShapeFrame {
        val u0 = f.localU(downX, downY); val v0 = f.localV(downX, downY)
        val sx = if (u0 < 0f) -1f else 1f; val sy = if (v0 < 0f) -1f else 1f
        var nhw = max(MIN_SIZE / 2f, f.hw + sx * (f.localU(px, py) - u0))
        var nhh = max(MIN_SIZE / 2f, f.hh + sy * (f.localV(px, py) - v0))
        if (abs(2f * nhw - 2f * nhh) < snapTol) { val t = (nhw + nhh) / 2f; nhw = t; nhh = t }
        return ShapeFrame(f.cx, f.cy, f.ux, f.uy, f.vx, f.vy, nhw, nhh)
    }

    /** Kopia kreski przeniesiona z ramki [from] do ramki [to]: każdy punkt zachowuje swoje miejsce względem ramki. */
    fun applyTo(s: Stroke, from: ShapeFrame, to: ShapeFrame): Stroke {
        val out = s.pts.copyOf()
        val n = s.count
        for (i in 0 until n) {
            val a = from.localU(s.pts[3 * i], s.pts[3 * i + 1]) / from.hw
            val b = from.localV(s.pts[3 * i], s.pts[3 * i + 1]) / from.hh
            out[3 * i] = to.x(a, b); out[3 * i + 1] = to.y(a, b)
        }
        // Zamknięta figura zostaje zamknięta co do bitu (bez zaokrągleń w jednym punkcie).
        if (n > 1 && s.pts[0] == s.pts[3 * n - 3] && s.pts[1] == s.pts[3 * n - 2]) { out[3 * n - 3] = out[0]; out[3 * n - 2] = out[1] }
        return Stroke(s.id, s.pen, s.color, s.width, out, s.locked, s.poly, s.ellipse, s.line)
    }
}
