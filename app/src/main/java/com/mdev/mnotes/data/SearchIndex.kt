package com.mdev.mnotes.data

import com.mdev.mnotes.core.pages.PageTags
import com.mdev.mnotes.core.search.TextSearch
import com.mdev.mnotes.editor.T

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

    /**
     * Pierwsza strona zeszytu, na której występują wszystkie [words] (albo zakładka o pasującej nazwie). Zapytanie
     * z „#” ([tagQuery]) szuka stron z tagami (FB8); fragment mówi wtedy, ile stron pasuje, np. „#definicja · 5 stron”.
     */
    fun find(nb: NotebookMeta, words: List<String>, tagQuery: PageTags.Query? = null): LibHit? {
        if (words.isEmpty() || nb.locked) return null
        if (tagQuery != null && tagQuery.isTag) {
            val e = entry(nb)
            val hits = nb.pages.indices.filter { i -> PageTags.matchesPage(nb.pageTags[nb.pages[i].id].orEmpty(), e.folded.getOrElse(i) { "" }, tagQuery) }
            if (hits.isEmpty()) return null
            val first = nb.pageTags[nb.pages[hits[0]].id].orEmpty()
            val label = PageTags.label(first).ifEmpty { "#" + tagQuery.tags.joinToString(" #") }
            return LibHit(hits[0], label + " · " + PageTags.pagesCount(hits.size, T.pl))
        }
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
        val tq = PageTags.query(words.joinToString(" "))
        for (nb in books) find(nb, words, tq)?.let { out[nb.id] = it }
        return out
    }
}
