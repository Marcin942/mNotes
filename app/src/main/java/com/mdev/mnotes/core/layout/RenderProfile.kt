package com.mdev.mnotes.core.layout

import kotlin.math.min
import kotlin.math.sqrt

/**
 * Profil jakości renderowania strony (ustawienie użytkownika). Czysta logika — używa jej [com.mdev.mnotes.render.PageCache].
 *
 * Tryby kafelkowe ([tiled]) trzymają małą bitmapę całej strony ([baseScale]) i kafelki w skali bieżącego powiększenia
 * (do [maxScale] px/pt); powyżej tej skali obraz jest powiększany z kafelków. Tryb klasyczny ([tiled] = false) to jedna
 * bitmapa całej strony w skali z progów [CLASSIC_BUCKETS] — tania i płynna, ale przy dużym zoomie widać piksele.
 */
class RenderProfile(
    val code: Int,
    val tiled: Boolean,
    val baseScale: Float,
    val maxScale: Float,
    /** Zapas skali (po ustaniu zoomu) renderowany w tle, by kolejne przybliżenie było od razu ostre; 0 = bez zapasu. */
    val headroom: Float,
    /** Wyprzedzenie predykcji zoomu w sekundach (0 = bez predykcji). */
    val lookaheadS: Float,
    val workers: Int,
    val tileBytes: Long,
    val baseBytes: Long,
    val fadeMs: Int,
) {
    /** Skala bitmapy całej strony w trybie klasycznym: najmniejszy próg ≥ [zoom], ograniczony budżetem pikseli. */
    fun classicScale(zoom: Float, pageW: Float, pageH: Float): Float {
        val budget = sqrt(CLASSIC_MAX_PIXELS / (pageW * pageH))
        val s = min(zoom, maxScale).coerceAtLeast(CLASSIC_BUCKETS[0])
        for (b in CLASSIC_BUCKETS) if (b >= s * 0.999f) return min(b, budget)
        return min(maxScale, budget)
    }

    companion object {
        const val BALANCED = 0
        const val SHARP = 1
        const val ECONOMY = 2
        const val CLASSIC = 3
        const val COUNT = 4

        const val CLASSIC_MAX_PIXELS = 10_200_000f
        val CLASSIC_BUCKETS = floatArrayOf(0.3f, 0.45f, 0.65f, 0.9f, 1.25f, 1.75f, 2.4f, 3.2f, 4.4f)

        private const val MB = 1024L * 1024

        private val ALL = listOf(
            RenderProfile(BALANCED, true, 1.6f, 12f, 1.4f, 0.35f, 3, 170 * MB, 80 * MB, 100),
            RenderProfile(SHARP, true, 1.6f, 30f, 1.4f, 0.35f, 3, 260 * MB, 100 * MB, 100),
            RenderProfile(ECONOMY, true, 1.2f, 4f, 0f, 0f, 2, 90 * MB, 48 * MB, 0),
            RenderProfile(CLASSIC, false, 1.6f, 4.4f, 0f, 0f, 1, 0, 300 * MB, 0),
        )

        /** Profil dla zapisanego kodu; nieznany kod → zrównoważony. */
        fun of(code: Int): RenderProfile = ALL.firstOrNull { it.code == code } ?: ALL[BALANCED]
    }
}
