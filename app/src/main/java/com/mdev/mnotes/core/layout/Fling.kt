package com.mdev.mnotes.core.layout

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sign

/**
 * Rzut po oderwaniu palca w jednej osi (2d-A1, stanowisko): te same wzory i parametry co `android.widget.OverScroller`,
 * którym leci jeden dokument, więc przy tej samej prędkości droga i czas rzutu są takie same. Czysta funkcja czasu —
 * zegar podaje wołający (czas klatki), w testach sztuczny. Opis w `docs/STANOWISKO-PROJEKT.md` §5.
 *
 * [start] i [velocity] w px i px/s; położenie nie wychodzi poza [min]..[max] — oś, która dojdzie do granicy, staje na niej.
 * [density] = gęstość ekranu (px na dp), [friction] = `ViewConfiguration.getScrollFriction()`.
 */
class Fling(
    val start: Float,
    val velocity: Float,
    val min: Float = Float.NEGATIVE_INFINITY,
    val max: Float = Float.POSITIVE_INFINITY,
    density: Float = 1f,
    friction: Float = FRICTION,
) {
    /** Droga bez granic (px, ze znakiem prędkości) i czas (ms). */
    val distance: Float
    val durationMs: Float

    init {
        val decel = friction * physicalCoeff(density)
        val v = abs(velocity)
        if (v < 1e-3f || decel <= 0f) { distance = 0f; durationMs = 0f }
        else {
            val l = ln(INFLEXION * v / decel)
            durationMs = (1000.0 * exp(l / (DECELERATION_RATE - 1.0))).toFloat()
            distance = (decel * exp(DECELERATION_RATE / (DECELERATION_RATE - 1.0) * l)).toFloat() * sign(velocity)
        }
    }

    /** Ułamek drogi po czasie [u] ∈ 0..1 trwania: prędkość na starcie = prędkość palca, w chwili końca zero. */
    private fun curve(u: Float): Float = 1f - (1f - u.coerceIn(0f, 1f)).pow(EXP)

    /** Położenie po [tMs] ms od puszczenia (przycięte do granic). */
    fun at(tMs: Float): Float {
        if (durationMs <= 0f) return start.coerceIn(min, max)
        return (start + distance * curve(tMs / durationMs)).coerceIn(min, max)
    }

    /** Prędkość (px/s) po [tMs] ms; zero na granicy i po końcu. */
    fun speedAt(tMs: Float): Float {
        if (durationMs <= 0f || tMs >= durationMs || reachedBound(tMs)) return 0f
        val u = tMs / durationMs
        return distance * EXP * (1f - u).pow(EXP - 1f) / (durationMs / 1000f)
    }

    private fun reachedBound(tMs: Float): Boolean {
        val raw = start + distance * curve(tMs / durationMs)
        return (distance > 0f && raw >= max) || (distance < 0f && raw <= min)
    }

    /** Czy rzut się skończył (czas minął albo oś doszła do granicy). */
    fun done(tMs: Float): Boolean = durationMs <= 0f || tMs >= durationMs || reachedBound(tMs)

    companion object {
        /** `ViewConfiguration.getScrollFriction()` w Androidzie. */
        const val FRICTION = 0.015f
        /** Stałe krzywej „spline” `OverScroller`: ln 0,78 / ln 0,9 i punkt przegięcia. */
        val DECELERATION_RATE: Double = ln(0.78) / ln(0.9)
        const val INFLEXION = 0.35f
        /** Wykładnik krzywej położenia: przy nim prędkość na starcie równa prędkości palca (v·T/L = 1/INFLEXION). */
        private const val EXP = 1f / INFLEXION

        /** Fizyczny współczynnik `OverScroller`: g · cale na metr · ppi · 0,84. */
        fun physicalCoeff(density: Float): Float = (9.80665f * 39.37f * density * 160f * 0.84f)
    }
}
