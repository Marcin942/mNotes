package com.mdev.mnotes.core.layout

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/**
 * Siatka kafelków rastrowych strony: kafelek ma stały rozmiar [TILE] px niezależnie od powiększenia, więc pamięć
 * i koszt renderu zależą od powierzchni ekranu, a nie od zoomu. Skala = piksele na punkt strony (pt).
 */
object TileGrid {
    const val TILE = 512

    /** Gęstość poziomów skali (kroków na oktawę): błąd skali ≤ 2^(1/8) ≈ 9%. */
    const val STEPS = 8

    /** Rzadsza siatka poziomów podczas szybkiego zoomu (≤ 19%) — mniej poziomów do wyrenderowania. */
    const val STEPS_MOTION = 4

    /** Najmniejsza skala 2^(k/[STEPS]) nie mniejsza niż [zoom] (z tolerancją 0,2%) — kafelki są zawsze nadpróbkowane, nie rozmyte. */
    fun level(zoom: Float, steps: Int = STEPS): Float {
        val k = ceil(ln(zoom.toDouble()) / ln(2.0) * steps - 0.0037 * steps).toInt()
        return 2.0.pow(k.toDouble() / steps).toFloat()
    }

    /**
     * Jak dobrze kafelki w skali [scale] pasują do powiększenia [zoom] (mniej = lepiej): skala ≥ zoom jest tylko
     * zmniejszana (ostra), więc kosztuje ok. proporcjonalnie do nadmiaru; skala < zoom byłaby powiększana (rozmyta) — kara 6×.
     */
    fun fit(scale: Float, zoom: Float): Float {
        val r = scale / zoom
        return if (r >= 0.997f) r else 1f + (1f / r - 1f) * 6f
    }

    /** Szerokość/wysokość strony w pikselach przy skali [scale]. */
    fun pixels(pt: Float, scale: Float): Int = ceil(pt * scale - 1e-3f).toInt().coerceAtLeast(1)

    /** Liczba kafelków w osi o długości [px] pikseli. */
    fun count(px: Int): Int = (px + TILE - 1) / TILE

    /** Rozmiar kafelka o indeksie [i] w osi o długości [px] (ostatni bywa węższy). */
    fun size(i: Int, px: Int): Int = minOf(TILE, px - i * TILE)

    /** Początek kafelka [i] w punktach strony. */
    fun originPt(i: Int, scale: Float): Float = i * TILE / scale

    /** Indeksy kafelków (0 until [n]) dotykających przedziału [from, to] (pt) przy skali [scale]; pusty przedział, gdy poza stroną. */
    fun range(from: Float, to: Float, scale: Float, n: Int): IntRange {
        val a = floor(from * scale / TILE).toInt().coerceAtLeast(0)
        val b = (ceil(to * scale / TILE).toInt() - 1).coerceAtMost(n - 1)
        return a..b
    }
}
