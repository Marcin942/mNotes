// Algorytmy kredy na podstawie programu kreda — autor i licencja do potwierdzenia przed publikacją (app/src/kreda/NOTICE.md).

package com.mdev.mnotes.core.image

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Kolor kredy dobrany do papieru strony docelowej (2c-B4). Biała kreda (kolory z oryginału albo „Biały”) na białej
 * kartce znikała, a grafit na ciemnej stronie też. Kontrast jak w WCAG 2 (luminancja względna sRGB, (L1 + 0,05) /
 * (L2 + 0,05)); kolor, któremu brakuje [MIN_CONTRAST], ciemnieje mnożeniem kanałów (odcień zostaje: żółta kreda wychodzi
 * oliwkowa, nie szara), a na ciemnym papierze jaśnieje w stronę bieli. Kolor z dość dużym kontrastem zostaje bez zmian.
 */
object ChalkInk {
    /**
     * Kontrast kreski do papieru: 4,5:1 (WCAG dla zwykłego tekstu). Plan wymaga ≥ 3:1 (grafika), ale szary 3:1 na bieli
     * na okładce PDF był blady, a brzegi kredy są półprzezroczyste, więc widać mniej niż w rdzeniu kreski.
     */
    const val MIN_CONTRAST = 4.5f

    private val LIN = FloatArray(256) { (it / 255.0).let { v -> if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }.toFloat() }

    fun luminance(c: Int): Float = 0.2126f * LIN[c shr 16 and 255] + 0.7152f * LIN[c shr 8 and 255] + 0.0722f * LIN[c and 255]

    fun contrast(a: Int, b: Int): Float {
        val x = luminance(a); val y = luminance(b)
        return (max(x, y) + 0.05f) / (min(x, y) + 0.05f)
    }

    /** [color] z kontrastem ≥ [min] do [paper]; alfa bez zmian. Czerń albo biel zawsze wystarcza (≥ 4,58:1 do dowolnego papieru). */
    fun fit(color: Int, paper: Int, min: Float = MIN_CONTRAST): Int {
        if (contrast(color, paper) >= min) return color
        val lp = luminance(paper)
        val darker = (lp + 0.05f) / 0.05f >= 1.05f / (lp + 0.05f)
        // odrobina zapasu: zaokrąglenie kanałów do 0–255 nie zbije kontrastu poniżej progu
        val goal = min + 0.05f
        var lo = 0f; var hi = 1f
        repeat(16) { val t = (lo + hi) / 2; if (contrast(shift(color, t, darker), paper) >= goal) hi = t else lo = t }
        return shift(color, hi, darker)
    }

    /** t = 0: [c]; t = 1: czerń ([darker]) albo biel. */
    private fun shift(c: Int, t: Float, darker: Boolean): Int {
        fun ch(v: Int) = if (darker) (v * (1f - t) + 0.5f).toInt() else (v + (255 - v) * t + 0.5f).toInt()
        return (c and 0xFF000000.toInt()) or (ch(c shr 16 and 255) shl 16) or (ch(c shr 8 and 255) shl 8) or ch(c and 255)
    }

    /** [fit] dla każdego widocznego piksela; kolory powtarzają się, więc wynik dla danego RGB liczy się raz. */
    fun fitImage(img: Raster, paper: Int, min: Float = MIN_CONTRAST): Raster {
        val memo = HashMap<Int, Int>()
        val out = IntArray(img.px.size)
        for (i in out.indices) {
            val c = img.px[i]
            if (c ushr 24 == 0) { out[i] = c; continue }
            val rgb = c and 0xFFFFFF
            out[i] = (c and 0xFF000000.toInt()) or memo.getOrPut(rgb) { fit(0xFF000000.toInt() or rgb, paper, min) and 0xFFFFFF }
        }
        return Raster(img.w, img.h, out)
    }
}
