package com.mdev.mnotes.core.samsung

import kotlin.math.max
import kotlin.math.min

/**
 * Kolory notatek Samsung Notes. Pliki niosą notatkę jasną; tryb ciemny to wygląd aplikacji Samsunga, zmierzony na
 * zrzucie ekranu: papier FCFCFC → 030303, pismo 030303 → FCFCFC, 123644 → BBDFED, czyli odwrócenie jasności HLS
 * z zachowaniem barwy i nasycenia. Zakreślacz Samsunga to zwykła alfa nałożona na papier, a zakreślacz mNotes
 * działa jak „multiply” (jasny papier) albo „screen” (ciemny), więc kolor trzeba przeliczyć wstecz.
 */
internal object SamsungColor {
    /** Alfa zakreślacza mNotes (PenSpec.HIGHLIGHTER) i współczynnik „screen” na ciemnym papierze (PageRenderer). */
    private const val HIGHLIGHT_ALPHA = 0.5
    private const val DARK_SCREEN_FACTOR = 0.84

    fun a(c: Int) = c ushr 24
    fun r(c: Int) = (c shr 16) and 0xFF
    fun g(c: Int) = (c shr 8) and 0xFF
    fun b(c: Int) = c and 0xFF
    fun pack(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun round(v: Double): Int = Math.rint(v).toInt()

    /** Odwraca jasność (HLS) z zachowaniem barwy i nasycenia; alfa bez zmian. */
    fun invertArgb(c: Int): Int {
        val rf = r(c) / 255.0
        val gf = g(c) / 255.0
        val bf = b(c) / 255.0
        val mx = max(rf, max(gf, bf))
        val mn = min(rf, min(gf, bf))
        val sum = mx + mn
        val range = mx - mn
        val l = sum / 2.0
        var h = 0.0
        var s = 0.0
        if (range != 0.0) {
            s = if (l <= 0.5) range / sum else range / (2.0 - mx - mn)
            val rc = (mx - rf) / range
            val gc = (mx - gf) / range
            val bc = (mx - bf) / range
            h = when {
                rf == mx -> bc - gc
                gf == mx -> 2.0 + rc - bc
                else -> 4.0 + gc - rc
            }
            h = (h / 6.0).mod(1.0)
        }
        val l2 = 1.0 - l
        if (s == 0.0) {
            val v = round(l2 * 255)
            return pack(a(c), v, v, v)
        }
        val m2 = if (l2 <= 0.5) l2 * (1.0 + s) else l2 + s - l2 * s
        val m1 = 2.0 * l2 - m2
        return pack(a(c), round(hue(m1, m2, h + 1.0 / 3) * 255), round(hue(m1, m2, h) * 255), round(hue(m1, m2, h - 1.0 / 3) * 255))
    }

    private fun hue(m1: Double, m2: Double, h0: Double): Double {
        val h = h0.mod(1.0)
        return when {
            h < 1.0 / 6 -> m1 + (m2 - m1) * h * 6.0
            h < 0.5 -> m2
            h < 2.0 / 3 -> m1 + (m2 - m1) * (2.0 / 3 - h) * 6.0
            else -> m1
        }
    }

    /**
     * Kolor zakreślacza mNotes (multiply z alfą 0,5), by wynik na jasnym [paper] był taki jak w Samsung Notes
     * (tam: P + (C − P)·alfa, ale nigdy jaśniej niż papier).
     */
    fun highlighter(c0: Int, paper: Int): Int {
        val alpha = a(c0) / 255.0
        fun ch(c: Int, p: Int): Int {
            val t = min(p.toDouble(), p + (c - p) * alpha)
            val v = if (p > 0) 255.0 * (t / p - 0.5) / 0.5 else 255.0
            return round(v.coerceIn(0.0, 255.0))
        }
        return pack(0xFF, ch(r(c0), r(paper)), ch(g(c0), g(paper)), ch(b(c0), b(paper)))
    }

    /**
     * Kolor zakreślacza mNotes na ciemnym [paper]: mNotes rozświetla „screen” z kryciem 0,5·0,84, więc odwracamy
     * wzór screen. Kolor jaśniejszy, niż to krycie da, jest przycinany (wynik nieco ciemniejszy niż w oryginale).
     */
    fun highlighterDark(c0: Int, paper: Int): Int {
        val alpha = a(c0) / 255.0
        val am = HIGHLIGHT_ALPHA * DARK_SCREEN_FACTOR
        fun ch(c: Int, p: Int): Int {
            val t = (p + (c - p) * alpha) / 255.0
            val pp = p / 255.0
            val v = if (pp < 1.0) (1.0 - (1.0 - t) / (1.0 - pp)) / am else 1.0
            return round(255.0 * v.coerceIn(0.0, 1.0))
        }
        return pack(0xFF, ch(r(c0), r(paper)), ch(g(c0), g(paper)), ch(b(c0), b(paper)))
    }

    /** Odległość kolorów (suma kwadratów różnic kanałów) — do wyboru najbliższego papieru z palety mNotes. */
    fun distance(x: Int, y: Int): Int {
        val dr = r(x) - r(y)
        val dg = g(x) - g(y)
        val db = b(x) - b(y)
        return dr * dr + dg * dg + db * db
    }
}
