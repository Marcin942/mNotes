package com.mdev.mnotes.core.layout

/**
 * Dwa zeszyty w jednym obszarze roboczym (F4), jak na rysunku w zeszycie Marcina: górny rząd stron to zeszyt 1,
 * dolny — zeszyt 2; każdy rząd przesuwa się osobno (strony leżą poziomo, w rzędzie), a między nimi jest przeciągany
 * rozdzielacz. Powiększenie może być wspólne: ta sama skala (px na pt) w obu rzędach, więc kartki tego samego
 * formatu wyglądają tak samo duże.
 */
object DualRows {
    const val MIN_FRACTION = 0.2f
    const val MAX_FRACTION = 0.8f
    const val DEFAULT_FRACTION = 0.5f

    /** Część wysokości dla górnego rzędu, w bezpiecznych granicach (żaden rząd nie znika). */
    fun clamp(f: Float): Float = if (f.isNaN()) DEFAULT_FRACTION else f.coerceIn(MIN_FRACTION, MAX_FRACTION)

    /** Wysokości rzędów (px) w obszarze [h] z rozdzielaczem [divider] px. */
    fun heights(h: Float, f: Float, divider: Float): FloatArray {
        val free = (h - divider).coerceAtLeast(0f)
        val top = free * clamp(f)
        return floatArrayOf(top, free - top)
    }

    /** Podział po przeciągnięciu rozdzielacza o [dy] px w obszarze [h]. */
    fun dragged(f: Float, dy: Float, h: Float, divider: Float): Float {
        val free = (h - divider).coerceAtLeast(1f)
        return clamp(clamp(f) + dy / free)
    }

    /**
     * Wspólne powiększenie: drugi rząd przyjmuje skalę [zoom] pierwszego, obciętą do własnych granic [min]..[max]
     * (rząd może mieć inny „100 %”, bo ma inną wysokość). Null = różnica pomijalna, nic nie zmieniać (bez pętli).
     */
    fun follow(zoom: Float, current: Float, min: Float, max: Float): Float? {
        val z = zoom.coerceIn(min, max)
        return if (kotlin.math.abs(z - current) <= 1e-4f * current) null else z
    }

    /** Zeszyty do wyboru do dolnego rzędu: bez bieżącego, ostatnio otwierane najpierw. */
    fun <T> candidates(all: List<T>, id: (T) -> String, opened: (T) -> Long, exclude: Set<String>): List<T> =
        all.filter { id(it) !in exclude }.sortedByDescending(opened)
}
