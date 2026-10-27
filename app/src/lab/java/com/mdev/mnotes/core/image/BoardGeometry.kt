package com.mdev.mnotes.core.image

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Cztery narożniki tablicy w kolejności: lewy górny, prawy górny, prawy dolny, lewy dolny (x, y w pikselach). */
class Quad(val pts: FloatArray) {
    init { require(pts.size == 8) { "czworokąt ma 4 punkty" } }

    fun x(i: Int) = pts[2 * i]
    fun y(i: Int) = pts[2 * i + 1]
    fun scaled(k: Float) = Quad(FloatArray(8) { pts[it] * k })

    /** Każdy narożnik przesunięty o [d] pikseli w stronę środka czworokąta. */
    fun inset(d: Float): Quad {
        val cx = (x(0) + x(1) + x(2) + x(3)) / 4f; val cy = (y(0) + y(1) + y(2) + y(3)) / 4f
        return Quad(FloatArray(8) { i ->
            val p = i / 2
            val dx = cx - x(p); val dy = cy - y(p)
            val len = hypot(dx, dy).coerceAtLeast(1e-3f)
            if (i % 2 == 0) x(p) + dx / len * d else y(p) + dy / len * d
        })
    }
    private fun side(i: Int, j: Int) = hypot(x(j) - x(i), y(j) - y(i))

    /** Rozmiar prostokąta po wyprostowaniu: dłuższe z przeciwległych boków, bez powiększania. */
    fun rectSize(): Pair<Int, Int> =
        max(8, max(side(0, 1), side(3, 2)).roundToInt()) to max(8, max(side(0, 3), side(1, 2)).roundToInt())

    /** Czy to [full] dla obrazu [w]×[h], czyli „tablicy nie znaleziono” w [BoardGeometry.guessQuad]. */
    fun isFull(w: Int, h: Int): Boolean {
        val f = full(w, h).pts
        return pts.indices.all { abs(pts[it] - f[it]) < 0.5f }
    }

    /** Czy czworokąt jest wypukły (wszystkie iloczyny wektorowe kolejnych boków mają ten sam znak). */
    fun isConvex(): Boolean {
        var pos = 0; var neg = 0
        for (i in 0 until 4) {
            val a = i; val b = (i + 1) % 4; val c = (i + 2) % 4
            val cross = (x(b) - x(a)) * (y(c) - y(b)) - (y(b) - y(a)) * (x(c) - x(b))
            if (cross > 0) pos++ else if (cross < 0) neg++
        }
        return pos == 4 || neg == 4
    }

    companion object {
        fun of(vararg xy: Float) = Quad(xy)
        fun full(w: Int, h: Int) = Quad(floatArrayOf(0f, 0f, w - 1f, 0f, w - 1f, h - 1f, 0f, h - 1f))

        /**
         * Narożniki chmury punktów (x, y) jak `_order_quad` w kreda.py: lewy górny ma najmniejsze x + y, prawy dolny
         * największe, prawy górny największe x − y, lewy dolny najmniejsze.
         */
        fun ofExtremes(xs: IntArray, ys: IntArray, n: Int): Quad {
            var tl = 0; var br = 0; var tr = 0; var bl = 0
            for (i in 1 until n) {
                val s = xs[i] + ys[i]; val d = xs[i] - ys[i]
                if (s < xs[tl] + ys[tl]) tl = i
                if (s > xs[br] + ys[br]) br = i
                if (d > xs[tr] - ys[tr]) tr = i
                if (d < xs[bl] - ys[bl]) bl = i
            }
            return of(xs[tl].toFloat(), ys[tl].toFloat(), xs[tr].toFloat(), ys[tr].toFloat(), xs[br].toFloat(), ys[br].toFloat(), xs[bl].toFloat(), ys[bl].toFloat())
        }
    }
}

/** Przekształcenie rzutowe 3×3 (h[8] = 1): (x, y) → ((h0x + h1y + h2)/d, (h3x + h4y + h5)/d), d = h6x + h7y + 1. */
class Homography(val h: DoubleArray) {
    fun map(x: Double, y: Double): DoubleArray {
        val d = h[6] * x + h[7] * y + 1.0
        return doubleArrayOf((h[0] * x + h[1] * y + h[2]) / d, (h[3] * x + h[4] * y + h[5]) / d)
    }

    companion object {
        /** Z czterech par punktów: src[i] → dst[i]; null, gdy punkty są współliniowe. */
        fun of(src: Quad, dst: Quad): Homography? {
            val a = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val x = src.x(i).toDouble(); val y = src.y(i).toDouble()
                val u = dst.x(i).toDouble(); val v = dst.y(i).toDouble()
                a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
                a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
            }
            val sol = solve(a) ?: return null
            return Homography(DoubleArray(9) { if (it < 8) sol[it] else 1.0 })
        }

        /** Eliminacja Gaussa z wyborem elementu głównego; macierz rozszerzona n×(n+1). */
        private fun solve(a: Array<DoubleArray>): DoubleArray? {
            val n = a.size
            for (c in 0 until n) {
                var p = c
                for (r in c + 1 until n) if (abs(a[r][c]) > abs(a[p][c])) p = r
                if (abs(a[p][c]) < 1e-12) return null
                val t = a[p]; a[p] = a[c]; a[c] = t
                for (r in 0 until n) if (r != c) {
                    val f = a[r][c] / a[c][c]
                    if (f != 0.0) for (k in c..n) a[r][k] -= f * a[c][k]
                }
            }
            return DoubleArray(n) { a[it][n] / a[it][it] }
        }
    }
}

object BoardGeometry {

    /** Prostuje czworokąt [quad] do prostokąta o rozmiarze z jego boków; próbkowanie dwuliniowe. */
    fun warp(src: Raster, quad: Quad, size: Pair<Int, Int> = quad.rectSize()): Raster {
        val (w, h) = size
        val dst = Quad.of(0f, 0f, w - 1f, 0f, w - 1f, h - 1f, 0f, h - 1f)
        val back = Homography.of(dst, quad) ?: return src.copy()
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val p = back.map(x.toDouble(), y.toDouble())
            out[y * w + x] = sample(src, p[0].toFloat(), p[1].toFloat())
        }
        return Raster(w, h, out)
    }

    /** Próbka dwuliniowa w (x, y); poza obrazem przezroczysty piksel. */
    fun sample(r: Raster, x: Float, y: Float): Int {
        if (x < -0.5f || y < -0.5f || x > r.w - 0.5f || y > r.h - 0.5f) return 0
        val fx = x.coerceIn(0f, r.w - 1f); val fy = y.coerceIn(0f, r.h - 1f)
        val x0 = fx.toInt(); val y0 = fy.toInt()
        val x1 = min(r.w - 1, x0 + 1); val y1 = min(r.h - 1, y0 + 1)
        val tx = fx - x0; val ty = fy - y0
        val c00 = r[x0, y0]; val c10 = r[x1, y0]; val c01 = r[x0, y1]; val c11 = r[x1, y1]
        fun ch(shift: Int): Int {
            val a = ((c00 shr shift) and 255) * (1 - tx) + ((c10 shr shift) and 255) * tx
            val b = ((c01 shr shift) and 255) * (1 - tx) + ((c11 shr shift) and 255) * tx
            return (a * (1 - ty) + b * ty + 0.5f).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Szerokość roboczego podglądu przy szukaniu tablicy (jak 1000 px w kreda.py, mniej, bo telefon). */
    const val WORK = 400

    /** Najmniejsza różnica jasności tła tablicy i otoczenia, przy której maska ma sens. */
    const val MIN_CONTRAST = 0.12

    /**
     * Maska „to jest tablica” w obrazie [r] (już pomniejszonym): tło (otwarcie usuwa kredę) ciemniejsze od progu Otsu
     * i mało nasycone; zostaje największa plama z wypełnionymi dziurami. Dla białej tablicy odwrotnie.
     */
    fun boardMask(r: Raster, black: Boolean): BooleanArray? {
        val w = r.w; val h = r.h
        val v = r.plane { Raster.maxRgb(it) }
        val rad = max(2, w / 70)
        val bg = if (black) BoardFilters.open(v, w, h, rad) else BoardFilters.close(v, w, h, rad)
        val sat = r.plane { Raster.maxRgb(it) - Raster.minRgb(it) }
        val satBg = BoardFilters.minMax(sat, w, h, rad, false)
        val thr = BoardFilters.otsu(bg)
        val mask = BooleanArray(w * h) { (if (black) bg[it] < thr else bg[it] >= thr) && satBg[it] < 0.35f }
        // Otsu dzieli na dwie części nawet sam szum (zdjęcie, na którym tablica wypełnia kadr): bez wyraźnej różnicy
        // jasności między „tablicą” a resztą nie ma czego wycinać.
        var inS = 0.0; var inN = 0; var outS = 0.0; var outN = 0
        for (i in mask.indices) if (mask[i]) { inS += bg[i]; inN++ } else { outS += bg[i]; outN++ }
        if (inN == 0 || outN == 0 || abs(outS / outN - inS / inN) < MIN_CONTRAST) return null
        val blob = BoardFilters.largestFilled(mask, w, h) ?: return null
        return if (blob.count { it } < 0.03 * w * h) null else blob
    }

    /**
     * Narożniki tablicy na zdjęciu: maska na podglądzie [WORK] px, narożniki ze skrajnych punktów plamy, przeliczone
     * na pełny rozmiar. Gdy nic sensownego nie wyszło, cały obraz (użytkownik i tak może przesunąć narożniki).
     */
    fun guessQuad(src: Raster, black: Boolean = true): Quad {
        val small = src.fit(WORK)
        val k = src.w.toFloat() / small.w
        val mask = boardMask(small, black) ?: return Quad.full(src.w, src.h)
        val n = mask.count { it }
        val xs = IntArray(n); val ys = IntArray(n)
        var i = 0
        for (p in mask.indices) if (mask[p]) { xs[i] = p % small.w; ys[i] = p / small.w; i++ }
        // Skrajne piksele plamy leżą na samej krawędzi tablicy; wcięcie o 1.5 piksela podglądu odcina pasek ściany,
        // który po prostowaniu dawałby jasną ramkę dookoła kredy.
        val q = Quad.ofExtremes(xs, ys, n).inset(1.5f).scaled(k)
        return if (q.isConvex()) q else Quad.full(src.w, src.h)
    }

    /**
     * Czy to zdjęcie ciemnej tablicy (propozycja „Oczyść tablicę”): w środkowej części obrazu przeważa ciemne, mało
     * nasycone tło. Zrzuty ekranu i kartki dają false.
     */
    fun looksLikeDarkBoard(src: Raster): Boolean {
        val s = src.fit(160)
        val bg = BoardFilters.open(s.plane { Raster.maxRgb(it) }, s.w, s.h, max(1, s.w / 60))
        var dark = 0; var total = 0
        for (y in s.h / 5 until s.h * 4 / 5) for (x in s.w / 5 until s.w * 4 / 5) {
            val c = s[x, y]
            total++
            if (bg[y * s.w + x] < 0.42f && Raster.maxRgb(c) - Raster.minRgb(c) < 0.3f) dark++
        }
        return total > 0 && dark >= 0.55 * total
    }
}
