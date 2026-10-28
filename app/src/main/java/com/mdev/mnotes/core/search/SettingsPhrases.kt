package com.mdev.mnotes.core.search

/**
 * 3c-A6: zapytania „ludzkimi słowami” w wyszukiwarce ustawień. Słowa-wypełniacze („jak”, „zmienić”, „how to”) nie muszą
 * występować w ustawieniu; opisy problemu („żeby ręka nie zasłaniała”, „ciemne tło”) prowadzą do pojęcia, które go
 * rozwiązuje; literówki poprawia najbliższe słowo ze słownika ekranu Ustawień. Wszystko na słowach złożonych ([TextSearch.fold]).
 */
object SettingsPhrases {
    /** Słowa pomijane, gdy całe zapytanie nic nie znajduje: pytajniki, zaimki, przyimki i czasowniki „ustawiania”. */
    val STOP: Set<String> = setOf(
        "jak", "gdzie", "zeby", "aby", "by", "sie", "nie", "mi", "mnie", "moj", "moja", "moje", "to", "ten", "ta", "te",
        "w", "we", "na", "do", "z", "ze", "o", "i", "a", "czy", "co", "jest", "sa", "chce", "chcialbym", "chcialabym",
        "mozna", "moge", "prosze", "zmien", "zmienic", "zmiana", "ustaw", "ustawic", "ustawienie", "ustawienia", "wlacz",
        "wlaczyc", "wylacz", "wylaczyc", "zrobic", "dla", "przy", "po", "od", "tak", "bardzo", "za", "juz", "mam", "ma",
        "byl", "bylo", "jakos", "jaki", "jaka", "jakie", "gdy", "kiedy", "tylko", "wiecej", "mniej",
        "how", "to", "the", "a", "an", "i", "my", "where", "is", "can", "change", "set", "turn", "on", "off", "enable",
        "disable", "want", "make", "do", "of", "in", "for", "with", "setting", "settings", "it",
    )

    /**
     * Opis problemu → pojęcie. [all]: każda grupa musi mieć w zapytaniu słowo zaczynające się od któregoś z jej
     * początków; [to]: zapytania zastępcze (słowa jak w etykietach i kluczach ustawień), każde szukane osobno.
     */
    class Rule(val all: List<List<String>>, val to: List<String>)

    private fun r(vararg groups: String, to: List<String>) = Rule(groups.map { it.split('|') }, to)

    val RULES: List<Rule> = listOf(
        r("zaslan|przeszkadz|cover|block", "rek|dlon|hand|palm|nadgarst", to = listOf("leworeczny", "dlon")),
        r("lewor|mankut|lefty|lefthand", to = listOf("leworeczny")),
        r("lew|left", "rek|hand", to = listOf("leworeczny")),
        r("ciemn|czarn|dark|black", "tlo|ekran|kartk|stron|background|screen|page|paper", to = listOf("ciemny", "motyw")),
        r("oczy|eyes|noc|night|wieczor", to = listOf("ciemny")),
        r("razi|swieci|jaskraw|bright|glare", to = listOf("ciemny", "motyw")),
        r("pasek|toolbar|bar|przycisk|button|ikon|icon", "zniknal|znika|ukryt|brak|gone|missing|hidden|widz", to = listOf("pasek", "polozenie")),
        r("wiekszy|wieksze|duze|duzy|bigger|large|male|maly|malenk|small|tiny", "przycisk|ikon|pasek|button|icon|toolbar", to = listOf("rozmiar pasek")),
        r("przypadk|sam|losow|random|accident|stray", "kresk|linie|rysuj|maz|stroke|line|draw|mark", to = listOf("dlon", "palec")),
        r("dlon|reka|palm|hand", "rysuj|maz|kresk|draw|stroke|mark", to = listOf("dlon")),
        r("palec|palc|finger", "rysuj|pisz|maz|draw|write", to = listOf("palec")),
        r("drz|trzes|krzyw|falist|shak|wobbl|jitter|ugly|brzydk", "pism|kresk|lini|writ|stroke|line|hand", to = listOf("wygladzanie")),
        r("gruba|grube|gruby|ciensz|cienk|cienka|thick|thin|fat", to = listOf("grubosc")),
        r("prostuj|prostuje|zmienia|zamienia|straighten|turns|becomes", "ksztal|kresk|lini|okrag|shape|line|circle|figur", to = listOf("wyprostowac", "ksztalt")),
        r("zablok|zabezp|nikt|ukryc|chronic|protect|secure|private|hide|podglad", "notat|zeszyt|aplik|ekran|note|app|screen|", to = listOf("blokada")),
        r("zgub|strac|stracic|lose|lost|backup|bezpieczn|safe", to = listOf("kopia")),
        r("wolno|wolna|wolny|zacina|laguje|tnie|slow|lag|laggy|stutter|bateri|battery", to = listOf("jakosc")),
        r("drukow|drukuj|druk|print", to = listOf("pdf", "eksport")),
        r("pusta|pusty|czysta|biala|blank|plain|empty", "kartk|stron|papier|page|paper|sheet", to = listOf("wzor", "papier")),
        r("krat|linie|linia|ruled|grid|kropk|dot", "stron|kartk|zeszyt|page|paper|nowy|nowe|new", to = listOf("wzor")),
        r("przesun|odsun|przewin|scroll|move|push", "stron|kartk|brzeg|poczat|page|edge|start", to = listOf("zapas", "przewijanie")),
        r("cofa|cofnac|undo|pomylk|blad|mistake", to = listOf("cofnij")),
        r("wibr|brzecz|buzz|vibrat|drga", to = listOf("wibracja")),
        r("angiel|english|polsk|polish|jezyk|language", to = listOf("jezyk")),
        r("mysz|mouse|komputer|pc|laptop|desktop|dex", to = listOf("zageszczony", "pasek menu")),
        r("klawiat|keyboard|skrot|shortcut|hotkey", to = listOf("skrot")),
        r("telefon|phone|komork|smartfon", to = listOf("telefon")),
        r("zakladk|karty|tabs|okna|windows|wiele|kilka|multiple|several", "zeszyt|notat|dokument|note|document|otw|open", to = listOf("karty")),
    )

    /** Zapytania zastępcze dla słów [words] (złożonych) — z każdej reguły, której wszystkie grupy trafiają. */
    fun expand(words: List<String>): List<List<String>> {
        val out = ArrayList<List<String>>()
        for (rule in RULES) {
            val ok = rule.all.all { g -> g.any { p -> p.isNotEmpty() && words.any { w -> w.startsWith(p) } } || g.contains("") }
            if (ok) for (t in rule.to) TextSearch.words(t).takeIf { it !in out }?.let { out += it }
        }
        return out
    }

    /**
     * Odległość edycyjna (wstawienie, usunięcie, zamiana, przestawienie sąsiednich liter) z przerwaniem po [max]:
     * powyżej [max] zwraca [max] + 1.
     */
    fun distance(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        val n = b.length
        var pp = IntArray(n + 1); var p = IntArray(n + 1) { it }; var c = IntArray(n + 1)
        for (i in 1..a.length) {
            c[0] = i
            var best = c[0]
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(p[j] + 1, c[j - 1] + 1, p[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, pp[j - 2] + 1)
                c[j] = v
                if (v < best) best = v
            }
            if (best > max) return max + 1
            val t = pp; pp = p; p = c; c = t
        }
        return minOf(p[n], max + 1)
    }

    /** Ile literówek wolno w słowie długości [len]: krótkie bez poprawiania (za dużo przypadkowych trafień). */
    fun allowed(len: Int): Int = when { len < 4 -> 0; len < 7 -> 1; else -> 2 }

    /**
     * Najbliższe słowo słownika dla słowa zapytania [q] (złożonego), którego słownik nie zna; null, gdy [q] jest znane
     * albo nic nie jest dość blisko. [vocab]: pary (złożone, jak na ekranie). Liczy się też początek dłuższego słowa
     * („powieksz” → „powiększenie”).
     */
    fun correct(q: String, vocab: List<Pair<String, String>>): Pair<String, String>? {
        val max = allowed(q.length)
        if (max == 0 || vocab.isEmpty()) return null
        if (SettingsSynonyms.alternatives(q).isNotEmpty() || vocab.any { SettingsSynonyms.queryMatches(it.first, q) }) return null
        var best: Pair<String, String>? = null; var bd = max + 1
        for (p in vocab) {
            val w = p.first
            if (w.length < 3) continue
            val d = minOf(distance(q, w, max), if (w.length > q.length) distance(q, w.take(q.length), max) else max + 1)
            if (d < bd || (d == bd && best != null && w.length < best.first.length)) { bd = d; best = p }
        }
        return if (bd <= max) best else null
    }

    /** Słownik z tekstów ekranu: wyrazy (małymi literami, ≥ 3 litery) → pary dla [correct]. */
    fun vocabulary(texts: Collection<String>): List<Pair<String, String>> =
        texts.asSequence().flatMap { it.lowercase().split(Regex("[^\\p{L}]+")) }.filter { it.length >= 3 }.distinct()
            .map { TextSearch.fold(it) to it }.toList()
}
