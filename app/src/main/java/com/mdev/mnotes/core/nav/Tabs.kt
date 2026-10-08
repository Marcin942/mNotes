package com.mdev.mnotes.core.nav

/**
 * Lista otwartych kart (zeszytów) z jedną aktywną. Niemodyfikowalna: każda operacja zwraca nowy stan,
 * więc nadaje się do trzymania w stanie Compose. Karty to same identyfikatory — edytor żyje tylko dla aktywnej.
 */
data class Tabs(val ids: List<String> = emptyList(), val active: Int = -1) {
    val activeId: String? get() = ids.getOrNull(active)
    val size get() = ids.size
    fun has(id: String) = id in ids

    /** Aktywuje [id] (dodając go na końcu, jeśli go nie ma). Gdy przekroczy [max], wypada najstarsza nieaktywna karta. */
    fun open(id: String, max: Int = MAX): Tabs {
        val at = ids.indexOf(id)
        if (at >= 0) return copy(active = at)
        var next = ids + id
        var act = next.size - 1
        while (next.size > max.coerceAtLeast(1)) {
            val drop = next.indices.first { it != act }
            next = next.filterIndexed { i, _ -> i != drop }
            if (drop < act) act--
        }
        return Tabs(next, act)
    }

    /** Zamyka kartę; aktywną zastępuje sąsiednia (następna, a przy ostatniej — poprzednia). */
    fun close(id: String): Tabs {
        val at = ids.indexOf(id)
        if (at < 0) return this
        val next = ids.filterIndexed { i, _ -> i != at }
        if (next.isEmpty()) return Tabs()
        val act = when {
            at < active -> active - 1
            at == active -> at.coerceAtMost(next.size - 1)
            else -> active
        }
        return Tabs(next, act)
    }

    /** Następna (+1) lub poprzednia (−1) karta, cyklicznie — pod Ctrl+Tab i Ctrl+Shift+Tab. */
    fun step(delta: Int): Tabs {
        if (ids.size < 2) return this
        return copy(active = Math.floorMod(active + delta, ids.size))
    }

    /** Aktywuje kartę o numerze [n] (od 1); 9 oznacza ostatnią, jak w przeglądarkach. */
    fun activateNumber(n: Int): Tabs {
        if (ids.isEmpty() || n < 1) return this
        return copy(active = if (n >= 9) ids.size - 1 else (n - 1).coerceAtMost(ids.size - 1))
    }

    /** Usuwa karty, których zeszyty już nie istnieją. */
    fun retain(exists: (String) -> Boolean): Tabs = ids.filter(exists).let { kept ->
        if (kept.size == ids.size) this
        else if (kept.isEmpty()) Tabs()
        else Tabs(kept, kept.indexOf(activeId).takeIf { it >= 0 } ?: 0)
    }

    companion object { const val MAX = 8 }
}
