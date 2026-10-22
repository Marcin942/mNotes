package com.mdev.mnotes.core.edit

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Przyciąganie kąta obrotu do wybranych „równych” kątów. Kąty są w stopniach, rosną zgodnie z ruchem wskazówek
 * zegara (tak jak obrót uchwytu na ekranie); zbiór jest zapisany w ustawieniach jako tekst „0,15,30,…”.
 */
object AngleSnap {
    const val DEFAULT_STEP = 15f
    const val DEFAULT_TOLERANCE = 4f
    const val MIN_TOLERANCE = 1f
    const val MAX_TOLERANCE = 12f

    /** Kąty co [step] stopni w [0, 360). */
    fun every(step: Float): List<Float> {
        if (step < 1f || step > 360f) return listOf(0f)
        val n = (360f / step).roundToInt().coerceAtLeast(1)
        return normalize(List(n) { it * step })
    }

    /** Zakres [0, 360), zaokrąglenie do 0,1°, bez powtórzeń, rosnąco. */
    fun normalize(angles: List<Float>): List<Float> =
        angles.filter { it.isFinite() }
            .map { ((((it * 10).roundToInt() % 3600) + 3600) % 3600) / 10f }
            .distinct().sorted()

    /** Kąty rozdzielone średnikiem albo spacją; przecinek jest przecinkiem dziesiętnym („22,5”). */
    fun parse(text: String): List<Float> =
        normalize(text.split(';', ' ').mapNotNull { it.replace('°', ' ').trim().replace(',', '.').toFloatOrNull() })

    /** Postać do ustawień: „0;22.5;90”. */
    fun format(angles: List<Float>): String = normalize(angles).joinToString(";") { plain(it) }

    private fun plain(a: Float): String = if (a == a.toInt().toFloat()) a.toInt().toString() else (a * 10).roundToInt().let { "${it / 10}.${it % 10}" }

    /** Do wyświetlenia: „45”, „22,5” (bez znaku stopnia). */
    fun label(a: Float): String = plain(a).replace('.', ',')

    /** Kąt [deg] sprowadzony do (−180, 180]. */
    fun wrap(deg: Float): Float {
        var d = deg % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /** Odbicie zbioru: obok każdego kąta także jego przeciwny (30° → 330°), bo obrót działa w obie strony. */
    fun withMirrors(angles: List<Float>): List<Float> = normalize(angles + angles.map { -it })

    /**
     * Przyciąga obrót [rad] (radiany, dowolny zwój) do najbliższego kąta z [angles], jeśli leży w [tolDeg] stopni;
     * w przeciwnym razie zwraca [rad]. Zwój obrotu zostaje (−370° przyciągnięte do −360°, nie do 0°).
     */
    fun snap(rad: Float, angles: List<Float>, tolDeg: Float): Float {
        if (angles.isEmpty()) return rad
        val deg = Math.toDegrees(rad.toDouble()).toFloat()
        var best = Float.NaN
        var bestAbs = tolDeg
        for (t in angles) {
            val diff = wrap(deg - t)
            if (abs(diff) <= bestAbs) { bestAbs = abs(diff); best = deg - diff }
        }
        return if (best.isNaN()) rad else Math.toRadians(best.toDouble()).toFloat()
    }

    /** Czy [rad] leży dokładnie na którymś z [angles] (do wibracji przy wejściu w przyciągnięcie). */
    fun isSnapped(rad: Float, angles: List<Float>): Boolean {
        val deg = Math.toDegrees(rad.toDouble()).toFloat()
        return angles.any { abs(wrap(deg - it)) < 0.01f }
    }
}
