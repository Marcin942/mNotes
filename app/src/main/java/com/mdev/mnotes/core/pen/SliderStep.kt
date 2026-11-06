package com.mdev.mnotes.core.pen

import kotlin.math.roundToLong

/** Przyciski „−”/„+” przy suwaku: wartość o jeden krok dalej, na siatce kroku, w granicach suwaka. */
object SliderStep {
    /** Przytrzymanie przycisku − / +: pierwszy krok od razu, po [REPEAT_DELAY_MS] kolejne co [REPEAT_MS] (ok. 7 na sekundę). */
    const val REPEAT_DELAY_MS = 400L
    const val REPEAT_MS = 143L

    /**
     * Następna wartość od [value] w kierunku [dir] (−1 albo +1). Wartość spoza siatki (ustawiona palcem, np. 2,37 przy
     * kroku 0,1) dochodzi najpierw do najbliższego węzła w tym kierunku (2,4 albo 2,3), a nie skacze o pełny krok.
     */
    fun next(value: Float, dir: Int, step: Float, min: Float, max: Float): Float {
        if (step <= 0f || dir == 0) return value.coerceIn(min, max)
        val q = value.toDouble() / step
        val near = q.roundToLong()
        val onGrid = kotlin.math.abs(q - near) < 1e-3
        val n = when {
            onGrid -> near + dir
            dir > 0 -> kotlin.math.ceil(q).toLong()
            else -> kotlin.math.floor(q).toLong()
        }
        // Zaokrąglenie do 4 miejsc usuwa ogon zmiennoprzecinkowy (0,30000001 pt w podpisie i w zapisie).
        val v = Math.round(n * step.toDouble() * 10000.0) / 10000.0
        return v.toFloat().coerceIn(min, max)
    }
}
