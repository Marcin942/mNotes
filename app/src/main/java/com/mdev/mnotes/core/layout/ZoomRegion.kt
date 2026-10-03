package com.mdev.mnotes.core.layout

/**
 * Obszar strony pokazywany w oknie powiększenia (EXT-003): lewy górny róg w punktach strony.
 * Rozmiar obszaru ([rw] × [rh]) zależy od ramki i powiększenia, więc przekazujemy go przy każdej operacji.
 */
class ZoomRegion(var x: Float = 0f, var y: Float = 0f) {
    /** Utrzymuje obszar w granicach strony; gdy strona jest mniejsza niż obszar, przyklejamy do krawędzi 0. */
    fun clamp(pw: Float, ph: Float, rw: Float, rh: Float) {
        x = x.coerceIn(0f, maxOf(0f, pw - rw))
        y = y.coerceIn(0f, maxOf(0f, ph - rh))
    }

    /** Przesuwa obszar w prawo o [frac] jego szerokości (do prawej krawędzi strony). Zwraca, czy się przesunął. */
    fun shiftRight(pw: Float, ph: Float, rw: Float, rh: Float, frac: Float = 0.6f): Boolean {
        val before = x
        x = minOf(x + frac * rw, maxOf(0f, pw - rw))
        y = y.coerceIn(0f, maxOf(0f, ph - rh))
        return x > before + 0.01f
    }

    /** Przesuwa obszar w lewo o [frac] jego szerokości (do lewej krawędzi strony). Zwraca, czy się przesunął. */
    fun shiftLeft(rw: Float, frac: Float = 0.6f): Boolean {
        val before = x
        x = maxOf(0f, x - frac * rw)
        return x < before - 0.01f
    }

    /** Nowa linia: z powrotem do lewego brzegu i niżej o [frac] wysokości obszaru. Zwraca false, gdy na stronie nie ma już miejsca. */
    fun nextLine(ph: Float, rh: Float, frac: Float = 0.75f): Boolean {
        val maxY = maxOf(0f, ph - rh)
        if (y >= maxY - 0.01f) return false
        x = 0f
        y = minOf(y + frac * rh, maxY)
        return true
    }
}
