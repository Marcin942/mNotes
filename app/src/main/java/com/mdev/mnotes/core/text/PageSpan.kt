package com.mdev.mnotes.core.text

/**
 * Przedział stron zapisany jak w oknie drukowania: `3`, `2-5`, `7-` (do końca), `-3` (od początku), kilka części
 * po przecinku lub średniku (`1-3, 8, 10-`). Numery są liczone od 1. Logika czysta — testowana jednostkowo.
 */
object PageSpan {
    /** Indeksy stron (od 0, rosnąco, bez powtórzeń) albo null, gdy zapis jest błędny lub wskazuje strony spoza zeszytu. */
    fun parse(text: String, count: Int): List<Int>? {
        val parts = text.split(',', ';').map { part -> part.filterNot { it.isWhitespace() } }.filter { it.isNotEmpty() }
        if (parts.isEmpty() || count <= 0) return null
        val out = sortedSetOf<Int>()
        for (part in parts) {
            val dash = part.indexOf('-')
            val (from, to) = if (dash < 0) {
                val n = part.toIntOrNull() ?: return null
                n to n
            } else {
                val a = part.substring(0, dash)
                val b = part.substring(dash + 1)
                if (a.isEmpty() && b.isEmpty()) return null
                val from = if (a.isEmpty()) 1 else a.toIntOrNull() ?: return null
                val to = if (b.isEmpty()) count else b.toIntOrNull() ?: return null
                from to to
            }
            if (from < 1 || to < from || to > count) return null
            for (n in from..to) out.add(n - 1)
        }
        return out.toList()
    }

    /** Krótka nazwa do pliku: `3`, `2-5`, `1-3_8` (ciągi kolejnych stron zwinięte, części łączone znakiem `_`). */
    fun label(pages: List<Int>): String {
        val sorted = pages.distinct().sorted()
        val runs = ArrayList<String>()
        var i = 0
        while (i < sorted.size) {
            var j = i
            while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
            runs.add(if (j == i) "${sorted[i] + 1}" else "${sorted[i] + 1}-${sorted[j] + 1}")
            i = j + 1
        }
        return runs.joinToString("_")
    }
}
