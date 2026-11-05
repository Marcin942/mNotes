package com.mdev.mnotes.core.nav

/**
 * LP-1: pasek kart przy stanowisku. Karty stanowiska stoją obok siebie w kolejności jego rzędów (z góry na dół, a w
 * kolumnach od lewej), w miejscu pierwszej z nich; reszta kart zostaje, gdzie była. Strzałka przy karcie stanowiska
 * mówi, gdzie leży jej dokument względem tego na środku widoku.
 */
object TabOrder {
    /** Karty okna [tabs] z grupą [rows] (dokumenty stanowiska, także te, które nie są kartami) wstawioną w całości. */
    fun merge(tabs: List<String>, rows: List<String>): List<String> {
        if (rows.isEmpty()) return tabs
        val group = rows.toSet()
        val out = ArrayList<String>(tabs.size + rows.size)
        var placed = false
        for (id in tabs) {
            if (id !in group) out += id
            else if (!placed) { out += rows; placed = true }
        }
        if (!placed) out.addAll(0, rows)
        return out
    }

    /** Strzałka przy rzędzie [row] względem rzędu na środku [center]; pusta dla środkowego. */
    fun arrow(row: Int, center: Int, horizontal: Boolean): String = when {
        row < 0 || center < 0 || row == center -> ""
        row < center -> if (horizontal) "↑" else "←"
        else -> if (horizontal) "↓" else "→"
    }
}
