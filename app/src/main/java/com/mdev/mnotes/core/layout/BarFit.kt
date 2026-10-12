package com.mdev.mnotes.core.layout

/** Pozycja paska narzędzi: [group] rozdzielają separatory, a mniejszy [rank] znaczy „chowaj później”. */
class BarEntry(val group: Int, val rank: Int)

/**
 * Dopasowanie paska narzędzi do miejsca. Najpierw próbuje zmieścić wszystko, potem to samo ciaśniej (bez odstępów),
 * a dopiero gdy i to nie wystarcza, chowa najmniej ważne pozycje pod „⋯”. Dzięki temu przy jednym dokumencie
 * pasek jest pełny, a przy kartach zwija się sam.
 */
object BarFit {
    class Result(val hidden: Set<Int>, val tight: Boolean)

    const val GAP = 2f
    const val SEP = 9f
    const val SEP_TIGHT = 7f

    /** Długość paska z samymi przyciskami [btn] (bez marginesów): [count] pozycji w [groups] grupach. */
    fun length(count: Int, groups: Int, btn: Float, tight: Boolean): Float {
        val seps = (groups - 1).coerceAtLeast(0)
        return if (tight) count * btn + seps * SEP_TIGHT
        else count * btn + seps * SEP + (count + seps - 1).coerceAtLeast(0) * GAP
    }

    /** [pinned]: pozycja, która nie może zniknąć (aktywne narzędzie), albo -1. */
    fun fit(items: List<BarEntry>, avail: Float, btn: Float, pinned: Int = -1): Result {
        fun groups(vis: Collection<Int>) = vis.map { items[it].group }.distinct().size
        val all = items.indices.toList()
        if (avail.isInfinite() || length(all.size, groups(all), btn, false) <= avail) return Result(emptySet(), false)
        if (length(all.size, groups(all), btn, true) <= avail) return Result(emptySet(), true)
        val vis = all.toMutableList()
        val order = all.filter { it != pinned }.sortedWith(compareByDescending<Int> { items[it].rank }.thenByDescending { it })
        for (i in order) {
            // +1: przycisk „⋯”, który stoi na końcu bez separatora.
            if (length(vis.size + 1, groups(vis), btn, false) <= avail) break
            vis.remove(i)
        }
        return Result(all.toSet() - vis.toSet(), false)
    }
}
