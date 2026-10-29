package com.mdev.mnotes.core.layout

/** Jak pozycja zachowuje się na pasku narzędzi. */
object BarMode {
    /** Na pasku, gdy jest miejsce; przy ciasnocie wędruje pod „⋯” według ważności. */
    const val AUTO = 0

    /** Zawsze na pasku, także gdy jest ciasno. */
    const val ALWAYS = 1

    /** Tylko pod „⋯”. */
    const val MENU = 2
}

/**
 * Układ paska narzędzi wybrany przez użytkownika: kolejność pozycji i tryb każdej z nich. Zapisuje się jako dwa
 * krótkie napisy w ustawieniach; nieznane id (np. po starszej wersji) są pomijane, a nowe pozycje dopisują się na
 * końcu, więc układ zapisany dawniej nie psuje się po aktualizacji.
 */
class BarConfig private constructor(val order: List<String>, private val modes: Map<String, Int>) {
    fun mode(id: String): Int = modes[id] ?: BarMode.AUTO

    val isDefault: Boolean get() = order == DEFAULT_ORDER && modes.isEmpty()

    /** Przesuwa [id] o [delta] miejsc wśród pozycji, które nie są pod „⋯”; na krańcu nic się nie dzieje. */
    fun move(id: String, delta: Int): BarConfig {
        val at = order.indexOf(id)
        if (at < 0 || delta == 0) return this
        val step = if (delta > 0) 1 else -1
        var to = at
        var left = Math.abs(delta)
        var i = at + step
        while (i in order.indices && left > 0) {
            if (mode(order[i]) != BarMode.MENU || mode(id) == BarMode.MENU) { to = i; left-- }
            i += step
        }
        if (to == at) return this
        val next = order.toMutableList()
        next.removeAt(at); next.add(to, id)
        return BarConfig(next, modes)
    }

    fun withMode(id: String, mode: Int): BarConfig {
        if (id !in DEFAULT_ORDER || mode == mode(id)) return this
        val next = modes.toMutableMap()
        if (mode == BarMode.AUTO) next.remove(id) else next[id] = mode
        return BarConfig(order, next)
    }

    fun reset(): BarConfig = DEFAULT

    fun orderString(): String = order.joinToString(",")

    fun modesString(): String = modes.entries.sortedBy { order.indexOf(it.key) }.joinToString(",") { "${it.key}:${it.value}" }

    companion object {
        /** Stabilne id pozycji paska w kolejności domyślnej. */
        val DEFAULT_ORDER = listOf(
            "undo", "redo", "ballpoint", "fountain", "pencil", "brush", "highlighter", "eraser", "painter", "fill",
            "pipette", "lasso", "text", "laser", "hand", "tape", "space", "ruler", "protractor", "geometry", "curve", "snip",
        )

        val DEFAULT = BarConfig(DEFAULT_ORDER, emptyMap())

        fun parse(order: String, modes: String): BarConfig {
            val seen = LinkedHashSet<String>()
            for (p in order.split(',')) { val id = p.trim(); if (id in DEFAULT_ORDER) seen.add(id) }
            for (id in DEFAULT_ORDER) seen.add(id)
            val m = HashMap<String, Int>()
            for (p in modes.split(',')) {
                val kv = p.split(':')
                if (kv.size != 2) continue
                val id = kv[0].trim(); val v = kv[1].trim().toIntOrNull() ?: continue
                if (id in DEFAULT_ORDER && (v == BarMode.ALWAYS || v == BarMode.MENU)) m[id] = v
            }
            return BarConfig(seen.toList(), m)
        }
    }
}
