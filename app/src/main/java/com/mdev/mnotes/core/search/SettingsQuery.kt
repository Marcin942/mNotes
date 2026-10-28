package com.mdev.mnotes.core.search

/**
 * Zapytanie w wyszukiwarce ustawień. Wielkość liter i polskie znaki nie mają znaczenia, kolejność słów też nie.
 * Słowo zapytania trafia w początek wyrazu („gum” → „gumki”) albo w jego odmianę z krótką końcówką („gumka” → „gumki”,
 * „pióro” → „pióra”); 2c-A12: nie w środek innego wyrazu ani w wyraz o tym samym rdzeniu („obraz” ≠ „obracanie”).
 */
class SettingsQuery private constructor(private val words: List<String>) {

    /** Słowa skojarzone z każdym słowem zapytania (2b-A4), liczone raz na zapytanie. */
    private val alts: List<Set<String>> = words.map { SettingsSynonyms.alternatives(it) }

    /**
     * Czy każde słowo zapytania występuje w złączonych [parts] — wprost albo jako słowo skojarzone ([SettingsSynonyms];
     * to musi trafić w cały wyraz, z tolerancją końcówki, żeby „back” nie łapało „background”).
     */
    fun matches(vararg parts: String): Boolean {
        val text = TextSearch.fold(parts.joinToString(" "))
        // Wyrazy z łącznikiem liczą się i w całości („mini-mapa”), i po częściach („mapa”).
        val ws = WORD.split(text).filter { it.isNotEmpty() }
        val pieces = TOKEN.findAll(text).map { it.value }.toList()
        for (k in words.indices) {
            val q = words[k]
            if (pieces.any { SettingsSynonyms.queryMatches(it, q) } || ws.any { SettingsSynonyms.queryMatches(it, q) }) continue
            if (alts[k].none { a -> ws.any { w -> SettingsSynonyms.wordMatches(w, a) } }) return false
        }
        return true
    }

    /** Zakresy w [text] do podświetlenia: całe wyrazy, w które trafia któreś słowo zapytania. */
    fun ranges(text: String): List<IntRange> =
        TOKEN.findAll(TextSearch.fold(text)).filter { m -> words.any { SettingsSynonyms.queryMatches(m.value, it) } }.map { it.range }.toList()

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
            return if (words.isEmpty()) null else SettingsQuery(words)
        }

        private val WORD = Regex("[^\\p{L}\\p{N}-]+")
        private val TOKEN = Regex("[\\p{L}\\p{N}]+")
    }
}
