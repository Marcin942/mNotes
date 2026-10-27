package com.mdev.mnotes.core.search

/**
 * Zapytanie w wyszukiwarce ustawień. Wielkość liter i polskie znaki nie mają znaczenia, kolejność słów też nie,
 * a końcówki fleksyjne są tolerowane przez przycinanie słów zapytania („gumka” znajdzie „gumki”, „pióro” — „pióra”).
 */
class SettingsQuery private constructor(private val stems: List<String>) {

    /** Słowa skojarzone z każdym słowem zapytania (2b-A4), liczone raz na zapytanie. */
    private val alts: List<Set<String>> = stems.map { SettingsSynonyms.alternatives(it) }

    /**
     * Czy każde słowo zapytania występuje w złączonych [parts] — wprost (fragment wyrazu) albo jako słowo skojarzone
     * ([SettingsSynonyms]; to musi trafić w cały wyraz, z tolerancją końcówki, żeby „back” nie łapało „background”).
     */
    fun matches(vararg parts: String): Boolean {
        val text = TextSearch.fold(parts.joinToString(" "))
        var words: List<String>? = null
        for (k in stems.indices) {
            if (text.contains(stems[k])) continue
            if (alts[k].isEmpty()) return false
            val ws = words ?: WORD.split(text).filter { it.isNotEmpty() }.also { words = it }
            if (alts[k].none { a -> ws.any { w -> SettingsSynonyms.wordMatches(w, a) } }) return false
        }
        return true
    }

    /** Zakresy w [text] do podświetlenia: trafienia słów zapytania rozszerzone do końca wyrazu, bez nakładania. */
    fun ranges(text: String): List<IntRange> {
        val folded = TextSearch.fold(text)
        val found = ArrayList<IntRange>()
        for (s in stems) for (start in TextSearch.findAll(folded, s)) {
            var end = start + s.length
            while (end < text.length && text[end].isLetterOrDigit()) end++
            found.add(start until end)
        }
        found.sortBy { it.first }
        val out = ArrayList<IntRange>()
        for (r in found) {
            val last = out.lastOrNull()
            if (last != null && r.first <= last.last + 1) out[out.size - 1] = last.first..maxOf(last.last, r.last) else out.add(r)
        }
        return out
    }

    companion object {
        /** Indeks kategorii „Laboratorium” w Ustawieniach (jest tylko w wariancie lab, przed „O aplikacji”). */
        const val LAB_CAT = 7

        /**
         * Kategorie przeszukiwane przez wyszukiwarkę ustawień: wszystkie z ustawieniami, bez „O aplikacji” (sam opis).
         * „Laboratorium” tylko w wariancie lab — wcześniej pomijane, więc jego słowa kluczowe nic nie znajdowały (A2-10).
         */
        fun searchableCats(hasLab: Boolean): List<Int> = (0 until LAB_CAT).toList() + if (hasLab) listOf(LAB_CAT) else emptyList()

        /** Puste lub białe zapytanie daje `null` (brak filtrowania). */
        fun of(q: String): SettingsQuery? {
            val words = TextSearch.words(q)
            return if (words.isEmpty()) null else SettingsQuery(words.map(::stem))
        }

        private val WORD = Regex("[^\\p{L}\\p{N}-]+")

        private fun stem(w: String): String = when {
            w.length >= 8 -> w.dropLast(2)
            w.length >= 5 -> w.dropLast(1)
            else -> w
        }
    }
}
