package com.mdev.mnotes.core.search

/**
 * Paleta poleceń z historią (pomysł Fable nr 2): bez zapytania na górze są ostatnio użyte polecenia, a wyszukiwanie
 * nie zależy od wielkości liter i polskich znaków („przytnij” znajduje „Przytnij obraz”, „zrodlo” — „Źródło”),
 * przedrostek słowa liczy się wyżej niż dowolny fragment, a litery w kolejności („wsprz” → „Wstaw przed”) najniżej.
 */
object CommandRank {
    class Item(val id: String, val title: String, val extra: String = "")

    const val MAX_RECENT = 8

    /** Indeksy [items] w kolejności wyświetlania dla zapytania [query] i historii [recent] (najnowsze pierwsze). */
    fun rank(items: List<Item>, query: String, recent: List<String>): List<Int> {
        val words = TextSearch.words(query)
        val bonus = HashMap<String, Float>()
        recent.forEachIndexed { i, id -> bonus[id] = (MAX_RECENT - i).coerceAtLeast(1) * 0.25f }
        if (words.isEmpty()) {
            val rec = recent.mapNotNull { id -> items.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
            return rec + items.indices.filter { it !in rec }
        }
        val scored = ArrayList<Pair<Int, Float>>()
        for ((i, it) in items.withIndex()) {
            val t = TextSearch.fold(it.title)
            val tw = t.split(' ', '-', '/', '(', ')', '…').filter { w -> w.isNotEmpty() }
            val ex = TextSearch.fold(it.extra)
            var s = 0f
            var ok = true
            for (w in words) {
                s += when {
                    tw.any { x -> x.startsWith(w) } -> 3f
                    w in t -> 2f
                    w in ex -> 1f
                    w.length >= 3 && subsequence(w, t) -> 0.5f
                    else -> { ok = false; 0f }
                }
                if (!ok) break
            }
            if (ok) scored.add(i to s + (bonus[it.id] ?: 0f))
        }
        return scored.sortedWith(compareByDescending<Pair<Int, Float>> { it.second }.thenBy { it.first }).map { it.first }
    }

    /** Litery [w] występują w [t] w tej samej kolejności. */
    fun subsequence(w: String, t: String): Boolean {
        var j = 0
        for (c in t) if (j < w.length && c == w[j]) j++
        return j == w.length
    }

    /** Historia po użyciu polecenia [id]: na początek, bez powtórzeń, najwyżej [MAX_RECENT]. */
    fun used(recent: List<String>, id: String): List<String> = (listOf(id) + recent.filter { it != id }).take(MAX_RECENT)

    fun encode(recent: List<String>) = recent.joinToString(",")
    fun decode(s: String): List<String> = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_RECENT)
}
