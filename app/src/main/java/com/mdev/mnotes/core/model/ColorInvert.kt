package com.mdev.mnotes.core.model

/**
 * Odwrócenie kolorów strony (F11, „ciemny tryb dokumentu”): jasność odwrócona, odcień zachowany. Samo odwrócenie RGB
 * zrobiłoby z czerwonego atramentu turkus, a z niebieskiego żółty; tu po odwróceniu idzie obrót barwy o 180°
 * (macierz zachowująca luminancję, jak `hueRotate` w SVG), więc czerwony zostaje czerwony, tylko jasny.
 *
 * Macierz 4×5 w układzie `ColorMatrix` Androida (wartości 0–255, przesunięcie w piątej kolumnie).
 */
object ColorInvert {
    private val H = floatArrayOf(
        -0.574f, 1.430f, 0.144f,
        0.426f, 0.430f, 0.144f,
        0.426f, 1.430f, -0.856f,
    )

    /** out = 255 − H·in (wiersze H sumują się do 1, więc biały → czarny, czarny → biały). */
    val MATRIX: FloatArray = floatArrayOf(
        -H[0], -H[1], -H[2], 0f, 255f,
        -H[3], -H[4], -H[5], 0f, 255f,
        -H[6], -H[7], -H[8], 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    /**
     * Kolor kreski rysowanej w trakcie na stronie [inverted] (A2-6): kryjący atrament od razu odwrócony — tam, gdzie
     * zakrywa tło, wynik jest ten sam co odwrócenie całości, a warstwa na każdą klatkę odpada. Kreska, która miesza się z
     * tłem ([mixes]: zakreślacz, pióro półprzezroczyste) wymaga odwrócenia sumy, bo macierz obcina nasycone kanały i nie
     * przenosi się przez mieszanie: wtedy null = rysuj w warstwie z filtrem.
     */
    fun liveInk(argb: Int, inverted: Boolean, mixes: Boolean): Int? = when {
        !inverted -> argb
        mixes -> null
        else -> apply(argb)
    }

    /** Kolor ARGB po odwróceniu (do testów i miniatur kolorów w UI). */
    fun apply(argb: Int): Int {
        val r = (argb shr 16) and 255; val g = (argb shr 8) and 255; val b = argb and 255
        fun ch(row: Int): Int = Math.round(MATRIX[5 * row] * r + MATRIX[5 * row + 1] * g + MATRIX[5 * row + 2] * b + MATRIX[5 * row + 4]).coerceIn(0, 255)
        return (argb and 0xFF000000.toInt()) or (ch(0) shl 16) or (ch(1) shl 8) or ch(2)
    }
}
