package com.mdev.mnotes.core.layout

/**
 * 3c-A17a: wyskakujące opcje przy ikonie narzędzia (szybki przycisk „Pobierz format” przy pędzlu, menu przy gumce, lassie
 * i laserze) według jednego modelu: dla każdego narzędzia osobno czy wyskakują i po ilu sekundach gasną (0 — nie gasną).
 * Zgaszone nie przeszkadza: drugie dotknięcie narzędzia i tak otwiera jego panel.
 */
object ToolPopups {
    enum class Tool { PAINTER, ERASER, LASSO, LASER, SNIP }

    /** Czasy do wyboru w Ustawieniach (s); 0 = nie gaśnie. */
    val STEPS = listOf(0, 2, 4, 6, 8)

    data class Rule(val show: Boolean, val sec: Int)

    /** Indeks najbliższego kroku dla zapisanego czasu (stare wersje pędzla pozwalały na 0–30 s). */
    fun stepOf(sec: Int): Int = STEPS.indices.minBy { kotlin.math.abs(STEPS[it] - sec) }

    /** Czy okienko jeszcze widać: pokazane i bez gaśnięcia albo krócej niż [Rule.sec] od ostatniego dotknięcia. */
    fun visible(r: Rule, idleMs: Long): Boolean = r.show && (r.sec <= 0 || idleMs < r.sec * 1000L)

    /**
     * Czasy gaśnięcia menu gumki, lassa i lasera zapisane jednym tekstem „ERASER:4,LASSO:0”; brak wpisu = nie gaśnie,
     * jak przed 3c-A17a. Pędzel ma własne, starsze ustawienie (`painter.quick_sec`), czytane przez ten sam [Rule].
     */
    class Fades private constructor(private val sec: Map<Tool, Int>) {
        fun of(t: Tool): Int = sec[t] ?: 0
        fun with(t: Tool, s: Int) = Fades(sec + (t to s.coerceIn(0, 60)))
        fun encode(): String = sec.filterValues { it > 0 }.entries.sortedBy { it.key.ordinal }.joinToString(",") { "${it.key.name}:${it.value}" }

        companion object {
            fun parse(s: String): Fades = Fades(
                s.split(',').mapNotNull { e ->
                    val p = e.trim().split(':')
                    val t = Tool.entries.firstOrNull { it.name == p.getOrNull(0) } ?: return@mapNotNull null
                    val v = p.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
                    t to v.coerceAtMost(60)
                }.toMap(),
            )
        }
    }

    fun helper(t: Tool): HelperStrip.Helper? = when (t) {
        Tool.ERASER -> HelperStrip.Helper.ERASER
        Tool.LASSO -> HelperStrip.Helper.LASSO
        Tool.LASER -> HelperStrip.Helper.LASER
        Tool.SNIP -> HelperStrip.Helper.SNIP
        Tool.PAINTER -> null
    }

    fun of(h: HelperStrip.Helper): Tool = when (h) {
        HelperStrip.Helper.ERASER -> Tool.ERASER
        HelperStrip.Helper.LASSO -> Tool.LASSO
        HelperStrip.Helper.LASER -> Tool.LASER
        HelperStrip.Helper.SNIP -> Tool.SNIP
    }
}
