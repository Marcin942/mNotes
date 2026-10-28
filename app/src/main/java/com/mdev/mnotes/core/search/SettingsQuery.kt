package com.mdev.mnotes.core.search

/**
 * Zapytanie w wyszukiwarce ustawień. Wielkość liter i polskie znaki nie mają znaczenia, kolejność słów też nie.
 * Słowo zapytania trafia w początek wyrazu („gum” → „gumki”) albo w jego odmianę z krótką końcówką („gumka” → „gumki”,
 * „pióro” → „pióra”); 2c-A12: nie w środek innego wyrazu ani w wyraz o tym samym rdzeniu („obraz” ≠ „obracanie”).
 *
 * 3c-A6: zapytanie ma kilka wariantów, od najpewniejszego: całe słowa, bez wypełniaczy ([SettingsPhrases.STOP]),
 * pojęcia z opisu problemu ([SettingsPhrases.RULES]) i poprawione literówki. Ustawienie pasuje, gdy pasuje którykolwiek;
 * [score] mówi, jak pewnie (do kolejności wyników).
 */
class SettingsQuery private constructor(private val alts: List<Alt>, val suggestion: String?) {

    private class Alt(val words: List<String>, val weight: Int) {
        /** Słowa skojarzone z każdym słowem (2b-A4), liczone raz na zapytanie. */
        val syn: List<Set<String>> = words.map { SettingsSynonyms.alternatives(it) }
    }

    /** Słowa pierwszego wariantu (do testów i podświetlania). */
    val words: List<String> get() = alts.first().words

    /**
     * Czy któryś wariant zapytania występuje w złączonych [parts] — każde jego słowo wprost albo jako słowo skojarzone
     * ([SettingsSynonyms]; to musi trafić w cały wyraz, z tolerancją końcówki, żeby „back” nie łapało „background”).
     */
    fun matches(vararg parts: String): Boolean = score(*parts) > 0

    /** 0 — nie pasuje; im więcej, tym pewniej (waga wariantu, potem liczba słów trafionych wprost). */
    fun score(vararg parts: String): Int {
        val text = TextSearch.fold(parts.joinToString(" "))
        // Wyrazy z łącznikiem liczą się i w całości („mini-mapa”), i po częściach („mapa”).
        val ws = WORD.split(text).filter { it.isNotEmpty() }
        val pieces = TOKEN.findAll(text).map { it.value }.toList()
        var best = 0
        for (a in alts) {
            if (a.weight * 100 + 99 <= best) continue
            var direct = 0
            var ok = true
            for (k in a.words.indices) {
                val q = a.words[k]
                if (pieces.any { SettingsSynonyms.queryMatches(it, q) } || ws.any { SettingsSynonyms.queryMatches(it, q) }) { direct++; continue }
                if (a.syn[k].none { s -> ws.any { w -> SettingsSynonyms.wordMatches(w, s) } }) { ok = false; break }
            }
            if (ok) best = maxOf(best, a.weight * 100 + direct)
        }
        return best
    }

    /** Zakresy w [text] do podświetlenia: całe wyrazy, w które trafia któreś słowo któregoś wariantu. */
    fun ranges(text: String): List<IntRange> {
        val all = alts.flatMap { it.words }.distinct()
        return TOKEN.findAll(TextSearch.fold(text)).filter { m -> all.any { SettingsSynonyms.queryMatches(m.value, it) } }.map { it.range }.toList()
    }

    companion object {
        /** Indeks kategorii „Laboratorium” w Ustawieniach (jest tylko w wariancie lab, przed „O aplikacji”). */
        const val LAB_CAT = 7

        /** Wagi wariantów: wprost, bez wypełniaczy, z opisu problemu, po poprawie literówek. */
        const val EXACT = 4
        const val STRIPPED = 3
        const val PHRASE = 2
        const val TYPO = 1

        /**
         * Kategorie przeszukiwane przez wyszukiwarkę ustawień: wszystkie z ustawieniami, bez „O aplikacji” (sam opis).
         * „Laboratorium” tylko w wariancie lab — wcześniej pomijane, więc jego słowa kluczowe nic nie znajdowały (A2-10).
         */
        fun searchableCats(hasLab: Boolean): List<Int> = (0 until LAB_CAT).toList() + if (hasLab) listOf(LAB_CAT) else emptyList()

        /**
         * Puste lub białe zapytanie daje `null` (brak filtrowania). [vocab]: złożone słowa ekranu Ustawień (etykiety i
         * klucze) — słowo spoza niego, bliskie któremuś, daje wariant poprawiony i [suggestion] („Czy chodziło o…”).
         */
        fun of(q: String, vocab: List<Pair<String, String>> = emptyList()): SettingsQuery? {
            val words = TextSearch.words(q).map { it.trim(*PUNCT) }.filter { it.isNotEmpty() }
            if (words.isEmpty()) return null
            val alts = ArrayList<Alt>()
            fun add(ws: List<String>, weight: Int) { if (ws.isNotEmpty() && alts.none { it.words == ws }) alts += Alt(ws, weight) }
            add(words, EXACT)
            val content = words.filter { it !in SettingsPhrases.STOP }
            add(content, STRIPPED)
            for (p in SettingsPhrases.expand(words)) add(p, PHRASE)
            var suggestion: String? = null
            if (vocab.isNotEmpty()) {
                val fix = content.map { SettingsPhrases.correct(it, vocab) }
                if (fix.any { it != null }) {
                    val fixed = content.mapIndexed { i, w -> fix[i]?.first ?: w }
                    add(fixed, TYPO)
                    for (p in SettingsPhrases.expand(fixed)) add(p, TYPO)
                    suggestion = content.mapIndexed { i, w -> fix[i]?.second ?: w }.joinToString(" ")
                }
            }
            return SettingsQuery(alts, suggestion)
        }

        private val PUNCT = charArrayOf('?', '!', '.', ',', ';', ':', '„', '”', '"', '\'', '(', ')')
        private val WORD = Regex("[^\\p{L}\\p{N}-]+")
        private val TOKEN = Regex("[\\p{L}\\p{N}]+")
    }
}
