package com.mdev.mnotes.core.layout

/**
 * 2c-A6: wspólny budżet bitmap stanowiska. Każdy pas miał własny pełny budżet profilu, więc trzy PDF-y zajmowały
 * trzy razy tyle pamięci. Teraz budżet profilu dzieli się między pasy proporcjonalnie do ich widocznej części.
 * Pas całkiem poza ekranem trzyma dawny przydział jeszcze [releaseMs] (rzut tam i z powrotem nie rysuje wszystkiego
 * od nowa), potem dostaje 0 — także na bazy stron.
 */
class CacheBudget(private val releaseMs: Long = 2000L) {
    class Share(val tileBytes: Long, val baseBytes: Long)

    private var weights = FloatArray(0)
    private var hiddenAt = LongArray(0)

    /** Przydziały pasów o widocznych długościach [visible] (px w poprzek pasa) w chwili [now] ms. */
    fun shares(tileBytes: Long, baseBytes: Long, visible: FloatArray, now: Long): List<Share> {
        // Zmiana liczby pasów: dawne wagi nie pasują do nowych indeksów, ukryte liczą się od teraz.
        if (visible.size != weights.size) { weights = FloatArray(visible.size); hiddenAt = LongArray(visible.size) { now } }
        val w = FloatArray(visible.size)
        for (i in visible.indices) {
            if (visible[i] > 0f) { weights[i] = visible[i]; hiddenAt[i] = -1L; w[i] = visible[i] }
            else {
                if (hiddenAt[i] < 0L) hiddenAt[i] = now
                w[i] = if (now - hiddenAt[i] < releaseMs) weights[i] else 0f
            }
        }
        val sum = w.sumOf { it.toDouble() }
        // W double i w dół: suma przydziałów nie przekroczy budżetu przez zaokrąglenia.
        return w.map { x ->
            if (sum <= 0.0 || x <= 0f) Share(0L, 0L)
            else Share((tileBytes * (x / sum)).toLong(), (baseBytes * (x / sum)).toLong())
        }
    }

    /** Najbliższa chwila po [now], w której ukryty pas straci przydział; null — żaden nie czeka. */
    fun nextRelease(now: Long): Long? =
        hiddenAt.indices.filter { hiddenAt[it] >= 0L && weights[it] > 0f && now - hiddenAt[it] < releaseMs }
            .minOfOrNull { hiddenAt[it] + releaseMs }
}
