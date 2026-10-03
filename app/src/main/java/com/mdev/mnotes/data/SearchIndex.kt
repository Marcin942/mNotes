package com.mdev.mnotes.data

import com.mdev.mnotes.core.search.TextSearch

/** Trafienie w bibliotece: indeks strony (0-based) i fragment tekstu do pokazania pod tytułem. */
class LibHit(val pageIndex: Int, val snippet: String)

/**
 * Indeks tekstu zeszytów (pola tekstowe + nazwy zakładek) do wyszukiwania w bibliotece.
 * Tekst jest czytany ze stron leniwie i trzymany w pamięci wyłącznie w postaci złożonej (bez diakrytyków);
 * wpis odświeża się, gdy zmieni się data modyfikacji zeszytu. Zeszyty zablokowane nie są indeksowane.
 * Wołać poza wątkiem UI.
 */
class SearchIndex(private val repo: Repository) {
    private class Entry(val stamp: Long, val pageIds: List<String>, val raw: List<String>, val folded: List<String>)

    private val cache = HashMap<String, Entry>()

    private fun entry(nb: NotebookMeta): Entry {
        synchronized(cache) { cache[nb.id]?.let { if (it.stamp == nb.modified && it.pageIds.size == nb.pages.size) return it } }
        val raw = nb.pages.map { p -> TextSearch.pageText(repo.readPage(nb.id, p.id)) }
        val e = Entry(nb.modified, nb.pages.map { it.id }, raw, raw.map(TextSearch::fold))
        synchronized(cache) { cache[nb.id] = e }
        return e
    }

    fun forget(id: String) { synchronized(cache) { cache.remove(id) } }

    /** Pierwsza strona zeszytu, na której występują wszystkie [words] (albo zakładka o pasującej nazwie). */
    fun find(nb: NotebookMeta, words: List<String>): LibHit? {
        if (words.isEmpty() || nb.locked) return null
        for (b in nb.bookmarks) {
            if (b.title.isNotBlank() && TextSearch.containsAll(TextSearch.fold(b.title), words)) {
                val i = nb.pages.indexOfFirst { it.id == b.pageId }
                if (i >= 0) return LibHit(i, b.title)
            }
        }
        val e = entry(nb)
        for (i in e.folded.indices) {
            if (!TextSearch.containsAll(e.folded[i], words)) continue
            val at = e.folded[i].indexOf(words[0]).coerceAtLeast(0)
            return LibHit(i, TextSearch.snippet(e.raw[i], at, words[0].length))
        }
        return null
    }

    /** Wyniki dla całej listy zeszytów; zapisy w toku są dokończone, żeby indeks widział najnowszy tekst. */
    fun scan(books: List<NotebookMeta>, words: List<String>): Map<String, LibHit> {
        if (words.isEmpty()) return emptyMap()
        runCatching { repo.io.submit {}.get() }
        val out = LinkedHashMap<String, LibHit>()
        for (nb in books) find(nb, words)?.let { out[nb.id] = it }
        return out
    }
}
