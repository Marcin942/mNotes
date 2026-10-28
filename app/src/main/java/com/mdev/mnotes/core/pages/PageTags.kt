package com.mdev.mnotes.core.pages

import com.mdev.mnotes.core.search.TextSearch

/**
 * Tagi stron (pomysł Fable nr 8, BACKLOG FB8): krótkie hasła przy stronie, np. #definicja, #zadanie, #egzamin, żeby
 * przed sprawdzianem jednym filtrem zebrać strony z całego zeszytu, a w bibliotece — z całego semestru.
 *
 * Tag to jedno słowo: małe litery, cyfry, „-” i „_” (spacje zamieniają się w „-”), bez wiodącego „#”. Porównania idą
 * po postaci złożonej ([TextSearch.fold]), więc „#przyklad” trafia w „przykład”. Zapis w metadanych zeszytu jako jeden
 * napis ([encode]): wiersz na stronę, identyfikator i tagi rozdzielone tabulatorem — stara wersja aplikacji nie zna
 * tego pola i po prostu go nie czyta.
 */
object PageTags {
    const val MAX_LEN = 24
    const val MAX_PER_PAGE = 8

    /** Podpowiedzi do pierwszego tagu: rodzaje treści, po które wraca się przy nauce. */
    val SUGGESTED_PL = listOf("definicja", "twierdzenie", "wzór", "zadanie", "przykład", "egzamin", "powtórka", "pytanie")
    val SUGGESTED_EN = listOf("definition", "theorem", "formula", "exercise", "example", "exam", "review", "question")

    /** Tag z tego, co wpisał użytkownik, albo null, gdy nic z niego nie zostaje. */
    fun normalize(raw: String): String? {
        val s = raw.trim().trimStart('#').trim().lowercase()
        val sb = StringBuilder()
        for (c in s) {
            when {
                c.isLetterOrDigit() || c == '_' -> sb.append(c)
                (c == '-' || c.isWhitespace()) && sb.isNotEmpty() && sb.last() != '-' -> sb.append('-')
            }
            if (sb.length >= MAX_LEN) break
        }
        return sb.toString().trimEnd('-').ifEmpty { null }
    }

    /** Tagi z wpisanego tekstu: oddzielone spacją, przecinkiem, średnikiem albo kolejnym „#”; bez powtórek. */
    fun parse(input: String): List<String> =
        dedupe(input.split(Regex("[\\s,;#]+")).mapNotNull(::normalize))

    private fun dedupe(tags: List<String>): List<String> {
        val seen = HashSet<String>()
        return tags.filter { seen.add(TextSearch.fold(it)) }
    }

    /** Tagi strony po dodaniu [tags] (na końcu, bez powtórek, najwyżej [MAX_PER_PAGE]). */
    fun added(current: List<String>, tags: List<String>): List<String> = dedupe(current + tags).take(MAX_PER_PAGE)

    /** Tagi strony bez [tag] (porównanie bez wielkości liter i diakrytyków). */
    fun removed(current: List<String>, tag: String): List<String> {
        val k = TextSearch.fold(tag)
        return current.filter { TextSearch.fold(it) != k }
    }

    fun has(current: List<String>, tag: String): Boolean = TextSearch.fold(tag).let { k -> current.any { TextSearch.fold(it) == k } }

    /** Mapa po ustawieniu tagów strony [pageId]; pusta lista usuwa wpis. */
    fun with(map: Map<String, List<String>>, pageId: String, tags: List<String>): Map<String, List<String>> {
        val clean = dedupe(tags.mapNotNull(::normalize)).take(MAX_PER_PAGE)
        return if (clean.isEmpty()) map - pageId else map + (pageId to clean)
    }

    fun encode(map: Map<String, List<String>>): String =
        map.entries.filter { it.value.isNotEmpty() && it.key.isNotEmpty() && '\t' !in it.key && '\n' !in it.key }
            .joinToString("\n") { (id, tags) -> id + "\t" + tags.joinToString("\t") }

    /** Odczyt [encode]; puste i uszkodzone wiersze są pomijane, a tagi przechodzą przez [normalize] jeszcze raz. */
    fun decode(s: String): Map<String, List<String>> {
        if (s.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, List<String>>()
        for (line in s.split('\n')) {
            val parts = line.split('\t')
            if (parts.size < 2 || parts[0].isBlank()) continue
            val tags = dedupe(parts.drop(1).mapNotNull(::normalize)).take(MAX_PER_PAGE)
            if (tags.isNotEmpty()) out[parts[0]] = tags
        }
        return out
    }

    /** Tag zeszytu i numery (0-based) jego stron w kolejności zeszytu. */
    class Usage(val tag: String, val pages: List<Int>)

    /**
     * Wszystkie tagi zeszytu dla stron w kolejności [order] (identyfikatory): najczęstsze najpierw, potem alfabetycznie.
     * Wpisy usuniętych stron nie liczą się.
     */
    fun usage(map: Map<String, List<String>>, order: List<String>): List<Usage> {
        val byKey = LinkedHashMap<String, Pair<String, MutableList<Int>>>()
        for ((i, id) in order.withIndex()) {
            for (t in map[id].orEmpty()) byKey.getOrPut(TextSearch.fold(t)) { t to ArrayList() }.second.add(i)
        }
        return byKey.entries.sortedWith(compareByDescending<Map.Entry<String, Pair<String, MutableList<Int>>>> { it.value.second.size }.thenBy { it.key })
            .map { Usage(it.value.first, it.value.second) }
    }

    /** Zapytanie rozłożone na początki tagów (słowa z „#”, złożone, bez „#”) i zwykłe słowa (złożone). */
    class Query(val tags: List<String>, val words: List<String>) {
        /** Zapytanie o tagi; bez „#” szukanie idzie dawną drogą (tekst i zakładki). */
        val isTag: Boolean get() = tags.isNotEmpty()
    }

    fun query(q: String): Query {
        val tags = ArrayList<String>(); val words = ArrayList<String>()
        for (w in q.trim().split(Regex("\\s+"))) {
            if (w.isEmpty()) continue
            if (w.startsWith("#")) normalize(w)?.let { tags.add(TextSearch.fold(it)) } else words.add(TextSearch.fold(w))
        }
        return Query(tags, words)
    }

    /**
     * Czy strona o tagach [tags] i tekście [foldedText] (pola tekstowe, złożone) pasuje do zapytania o tagi: każdy tag
     * zapytania jest początkiem tagu strony („#def” trafia w „definicja”) albo stoi w tekście strony jako „#…”, a każde
     * zwykłe słowo jest w tekście („#definicja pochodna”).
     */
    fun matchesPage(tags: List<String>, foldedText: String, q: Query): Boolean {
        if (!q.isTag) return false
        val ft = tags.map(TextSearch::fold)
        return q.tags.all { w -> ft.any { it.startsWith(w) } || foldedText.contains("#$w") } && q.words.all { foldedText.contains(it) }
    }

    /** Tagi z „#” do pokazania przy stronie, np. „#definicja #wzór”. */
    fun label(tags: List<String>): String = tags.joinToString(" ") { "#$it" }

    /** „1 strona”, „3 strony”, „5 stron” (albo po angielsku). */
    fun pagesCount(n: Int, pl: Boolean): String = if (!pl) (if (n == 1) "1 page" else "$n pages") else "$n " + when {
        n == 1 -> "strona"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "strony"
        else -> "stron"
    }
}
