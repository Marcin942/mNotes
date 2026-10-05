package com.mdev.mnotes.editor

import kotlin.math.hypot

/**
 * Ślad lasera we współrzędnych dokumentu. Nic nie znika, dopóki laser jest używany: ślad zaczyna
 * blednąć dopiero po [HOLD_MS] bezczynności (od ostatniego punktu albo uniesienia rysika), a potem
 * gaśnie płynnie przez [FADE_MS]. Każde dotknięcie to osobny odcinek, więc przerwy nie łączą się linią.
 */
class LaserTrail {
    class Run {
        var x = FloatArray(128); var y = FloatArray(128); var n = 0
        fun push(px: Float, py: Float) {
            if (n == x.size) { x = x.copyOf(n * 2); y = y.copyOf(n * 2) }
            x[n] = px; y[n] = py; n++
        }
    }

    val runs = ArrayList<Run>()
    var down = false; private set
    private var lastActiveT = 0L
    private var total = 0

    val isEmpty get() = runs.isEmpty()

    /** Początek odcinka; ślad, który zdążył zgasnąć, jest już pusty. */
    fun begin(now: Long) {
        if (fade(now) <= 0f) clear()
        runs.add(Run())
        down = true; lastActiveT = now
    }

    /**
     * Ostatni punkt to ruchoma głowa: dopóki jest bliżej niż [minDist] od ostatniego zatwierdzonego (przedostatniego),
     * tylko go przesuwamy. Porównanie z samą głową prostowałoby ślad przy wolnym ruchu.
     */
    fun add(px: Float, py: Float, minDist: Float, now: Long) {
        val r = runs.lastOrNull() ?: return
        lastActiveT = now
        if (r.n >= 2 && hypot(px - r.x[r.n - 2], py - r.y[r.n - 2]) < minDist) { r.x[r.n - 1] = px; r.y[r.n - 1] = py; return }
        r.push(px, py); total++
        if (total > MAX_POINTS) trim()
    }

    fun end(now: Long) { down = false; lastActiveT = now }

    fun clear() { runs.clear(); total = 0; down = false }

    /** 1 = pełna widoczność, 0 = zgasł. Gładka krzywa, żeby koniec nie „odcinał się” nagle. */
    fun fade(now: Long): Float {
        if (runs.isEmpty()) return 0f
        if (down) return 1f
        val idle = now - lastActiveT
        if (idle <= HOLD_MS) return 1f
        val k = 1f - (idle - HOLD_MS).toFloat() / FADE_MS
        if (k <= 0f) return 0f
        return k * k * (3f - 2f * k)
    }

    /** Za ile ms trzeba przerysować: 0 = następna klatka (rysowanie albo gaszenie), >0 = tyle czekamy do początku gaszenia, -1 = nic. */
    fun nextFrameIn(now: Long): Long {
        if (runs.isEmpty()) return -1
        if (down) return 0
        val idle = now - lastActiveT
        return if (idle < HOLD_MS) HOLD_MS - idle else 0
    }

    private fun trim() {
        while (total > MAX_POINTS && runs.size > 1) total -= runs.removeAt(0).n
        val r = runs[0]
        if (total > MAX_POINTS && r.n > 2) {
            val drop = r.n / 4
            System.arraycopy(r.x, drop, r.x, 0, r.n - drop); System.arraycopy(r.y, drop, r.y, 0, r.n - drop)
            r.n -= drop; total -= drop
        }
    }

    companion object {
        const val HOLD_MS = 1625L
        const val FADE_MS = 585L
        const val MAX_POINTS = 40_000
    }
}
