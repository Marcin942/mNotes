package com.mdev.mnotes.core.pen

/**
 * Kolory lasera (F7: listwa lasera). Ślad rysuje się czterema warstwami: dwie poświaty w kolorze lasera, ciemniejszy
 * rdzeń i biały środek; dla czerwieni wychodzą dokładnie dawne stałe, więc domyślny wygląd się nie zmienia.
 */
object LaserColors {
    /** Czerwony (domyślny), zielony, niebieski, fioletowy — RGB bez kanału alfa. */
    val PALETTE = intArrayOf(0xFF3B30, 0x34C759, 0x0A84FF, 0xBF5AF2)
    const val DEFAULT = 0xFF3B30

    /** Kolory czterech warstw śladu dla lasera [rgb]. */
    fun layers(rgb: Int): IntArray {
        val c = rgb and 0xFFFFFF
        return intArrayOf(c, c, core(c), 0xFFFFFF)
    }

    /** Rdzeń: najjaśniejszy kanał zostaje, pozostałe ciemnieją o 30 %; czerwień ma dawny, ręcznie dobrany rdzeń. */
    fun core(rgb: Int): Int {
        if (rgb == DEFAULT) return 0xFF2A1F
        val r = (rgb shr 16) and 0xFF; val g = (rgb shr 8) and 0xFF; val b = rgb and 0xFF
        fun dim(v: Int) = if (v == maxOf(r, g, b)) v else Math.round(v * 0.7f).coerceIn(0, 255)
        return (dim(r) shl 16) or (dim(g) shl 8) or dim(b)
    }
}
