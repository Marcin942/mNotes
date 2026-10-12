package com.mdev.mnotes.core.layout

/**
 * Pozycja paska narzędzi: [group] rozdzielają separatory, a mniejszy [rank] znaczy „chowaj później”.
 * [mode] to wybór użytkownika ([BarMode]): zawsze na pasku, tylko pod „⋯” albo wedle miejsca.
 */
class BarEntry(val group: Int, val rank: Int, val mode: Int = BarMode.AUTO)

/**
 * Dopasowanie paska narzędzi do miejsca. Najpierw próbuje zmieścić wszystko, potem to samo ciaśniej (bez odstępów),
 * a dopiero gdy i to nie wystarcza, chowa najmniej ważne pozycje pod „⋯”. Dzięki temu przy jednym dokumencie
 * pasek jest pełny, a przy kartach zwija się sam. Pozycje przypięte przez użytkownika ([BarMode.ALWAYS]) nie są
 * chowane przy braku miejsca, a [BarMode.MENU] zawsze stoją pod „⋯”.
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
        // Kolejność jest własna, więc separator stoi przy każdej zmianie grupy między sąsiadami.
        fun groups(vis: List<Int>) = if (vis.isEmpty()) 0 else 1 + (1 until vis.size).count { items[vis[it]].group != items[vis[it - 1]].group }
        val forced = items.indices.filter { items[it].mode == BarMode.MENU && it != pinned }.toSet()
        val all = items.indices.filter { it !in forced }
        // Przycisk „⋯” stoi na końcu bez separatora i zajmuje miejsce, gdy cokolwiek jest pod nim.
        val more = if (forced.isEmpty()) 0 else 1
        if (avail.isInfinite() || length(all.size + more, groups(all), btn, false) <= avail) return Result(forced, false)
        if (length(all.size + more, groups(all), btn, true) <= avail) return Result(forced, true)
        val vis = all.toMutableList()
        val order = all.filter { it != pinned && items[it].mode != BarMode.ALWAYS }
            .sortedWith(compareByDescending<Int> { items[it].rank }.thenByDescending { it })
        for (i in order) {
            if (length(vis.size + 1, groups(vis), btn, false) <= avail) break
            vis.remove(i)
        }
        return Result(items.indices.toSet() - vis.toSet(), false)
    }
}
