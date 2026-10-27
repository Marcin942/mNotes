package com.mdev.mnotes.core.layout

import kotlin.math.max

/**
 * Największe powiększenie (F25): dawniej stałe — 6 × „100 %” widoku, ale nie mniej niż 14 px na pt. Mnożnik [factor]
 * z Ustawień skaluje oba progi, więc wartość domyślna 6 daje dokładnie dawne zachowanie.
 */
object ZoomLimits {
    const val DEFAULT_FACTOR = 6f
    const val MIN_FACTOR = 2f
    const val MAX_FACTOR = 24f

    fun maxZoom(base: Float, factor: Float): Float {
        val f = factor.coerceIn(MIN_FACTOR, MAX_FACTOR)
        return max(14f * f / DEFAULT_FACTOR, base * f)
    }
}

/**
 * Powrót do pisania (F26, opcja): ponowne dotknięcie narzędzia „chwilowego” (laser, ręka, lasso) albo dotknięcie
 * pustego miejsca tym narzędziem wraca do ostatniego narzędzia piszącego, jak odznaczenie narzędzia.
 */
object ToolReturn {
    fun shouldReturn(enabled: Boolean, temporaryTool: Boolean, reclick: Boolean, emptyTap: Boolean, hasSelection: Boolean): Boolean =
        enabled && temporaryTool && (reclick || (emptyTap && !hasSelection))
}
