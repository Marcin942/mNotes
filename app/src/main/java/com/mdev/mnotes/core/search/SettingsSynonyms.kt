package com.mdev.mnotes.core.search

/**
 * Słowa skojarzone w wyszukiwarce ustawień (2b-A4): grupy synonimów PL i EN — potoczne nazwy i czasowniki. Słowo
 * zapytania, które zaczyna jedno ze słów grupy, trafia w każde ustawienie zawierające dowolne słowo tej grupy („mazanie”
 * znajdzie „Gumka…”, „lupa” — „Największe powiększenie”). Kolejność i forma bez znaczenia (porównanie po [TextSearch.fold]).
 */
object SettingsSynonyms {
    val GROUPS: List<List<String>> = listOf(
        listOf("gumka", "gumki", "mazanie", "mazać", "ścieranie", "ścierać", "wycieranie", "wymaż", "kasowanie", "usuwanie", "eraser", "erase", "rubber", "wipe"),
        listOf("pióro", "pióra", "długopis", "pisak", "rysik", "atrament", "pisanie", "pen", "pens", "stylus", "ink", "writing"),
        listOf("powiększenie", "przybliżenie", "przybliżanie", "oddalanie", "zoom", "lupa", "skala", "magnify", "magnifier", "scale"),
        listOf("lasso", "zaznaczanie", "zaznaczenie", "zaznacz", "wybór", "pętla", "selection", "select", "marquee", "loop"),
        listOf("kolor", "kolory", "barwa", "barwy", "odcień", "paleta", "colour", "color", "hue", "palette"),
        listOf("motyw", "ciemny", "jasny", "nocny", "amoled", "theme", "dark", "light", "night"),
        listOf("strona", "strony", "kartka", "kartki", "arkusz", "page", "pages", "sheet"),
        listOf("zeszyt", "zeszyty", "notatnik", "notatka", "notatki", "dokument", "notebook", "note", "notes", "document"),
        listOf("pasek", "listwa", "belka", "przyciski", "toolbar", "bar", "strip", "buttons"),
        listOf("palec", "palce", "dotyk", "dotknięcie", "stuknięcie", "stuknij", "puknięcie", "gest", "gesty", "finger", "touch", "gesture", "tap"),
        listOf("dłoń", "dłoni", "ręka", "nadgarstek", "palm", "wrist"),
        listOf("cofnij", "cofanie", "ponów", "wstecz", "undo", "redo", "back"),
        listOf("tekst", "pole", "klawiatura", "litery", "pisanie z klawiatury", "text", "typing", "keyboard", "font"),
        listOf("kształt", "kształty", "figura", "figury", "prostowanie", "wyprostuj", "okrąg", "prostokąt", "shape", "shapes", "snap", "straighten"),
        listOf("siatka", "kratka", "linie", "liniatura", "grid", "lines", "ruled"),
        listOf("zdjęcie", "zdjęcia", "obraz", "obrazy", "fotografia", "foto", "image", "images", "photo", "picture"),
        listOf("eksport", "udostępnij", "wyślij", "pdf", "export", "share", "send"),
        listOf("kopia", "kopie", "zapasowa", "archiwum", "synchronizacja", "backup", "sync", "restore", "przywróć"),
        listOf("prywatność", "blokada", "hasło", "pin", "biometria", "odcisk", "privacy", "lock", "password", "fingerprint"),
        listOf("język", "polski", "angielski", "language", "english", "polish"),
        listOf("animacja", "ruch", "płynność", "przejście", "animation", "motion", "transition"),
        listOf("dymek", "dymki", "podpowiedź", "komunikat", "napis", "tooltip", "hint", "toast", "message"),
        listOf("skrót", "skróty", "klawisz", "klawisze", "shortcut", "shortcuts", "hotkey", "key"),
        listOf("czas", "opóźnienie", "szybkość", "długość", "milisekundy", "delay", "time", "speed", "duration"),
        listOf("rozmiar", "wielkość", "grubość", "duży", "mały", "size", "width", "thickness", "big", "small"),
        listOf("wygładzanie", "gładkość", "drżenie", "stabilizacja", "smoothing", "stabilizer", "jitter"),
        listOf("zakreślacz", "marker", "podkreślanie", "highlighter", "highlight"),
        listOf("laser", "wskaźnik", "prezentacja", "pokaz", "pointer", "presentation"),
        listOf("minimapa", "mini-mapa", "mini", "mapa", "podgląd", "nawigacja", "overview", "minimap", "navigation"),
        listOf("wzór", "szablon", "tło", "papier", "template", "pattern", "background", "paper"),
        listOf("krzywa", "krzywe", "łuk", "bezier", "béziera", "curve", "arc", "spline"),
        listOf("pierwiastek", "suma", "całka", "symbol", "symbole", "matematyka", "root", "sum", "integral", "math"),
        listOf("grecki", "alfa", "indeks", "potęga", "wykładnik", "latex", "greek", "subscript", "superscript", "exponent"),
        listOf("konstrukcja", "konstrukcje", "geometria", "cyrkiel", "trójkąt", "construction", "geometry", "compass", "triangle"),
        listOf("kreda", "tablica", "tablicy", "chalk", "blackboard", "board", "whiteboard"),
        listOf("telefon", "nearby", "bluetooth", "parowanie", "łącze", "połączenie", "phone", "pairing", "pair", "link", "connection"),
        listOf("oświetlenie", "światło", "jasność", "cień", "cienie", "lighting", "brightness", "shadow", "shading"),
        listOf("próg", "czułość", "kontrast", "odcięcie", "threshold", "sensitivity", "contrast", "cutoff"),
        listOf("automatycznie", "automatyczny", "samo", "auto", "automatic", "automatically"),
        listOf("układ", "rozkład", "położenie", "pozycja", "kolejność", "layout", "position", "order", "arrange"),
        listOf("wibracja", "wibracje", "drgania", "haptyka", "vibration", "haptic", "haptics"),
        listOf("pipeta", "próbnik", "pobierz kolor", "eyedropper", "picker", "sample"),
        listOf("pędzel", "malarz", "format", "styl", "painter", "brush", "style"),
        listOf("wiaderko", "wypełnienie", "zamaluj", "fill", "bucket", "paint"),
        listOf("taśma", "zakrywanie", "ukryj", "nauka na pamięć", "tape", "cover", "hide", "memorize"),
        listOf("nowa", "nowy", "dodaj", "tworzenie", "new", "add", "create"),
        listOf("obrót", "obracanie", "kąt", "kąty", "rotate", "rotation", "angle"),
        listOf("przyciąganie", "przyciągaj", "przyciągnij", "magnes", "przyczep", "przyklej", "attach", "magnet", "stick"),
        listOf("wykład", "zajęcia", "aparat", "lecture", "class", "camera"),
        listOf("jakość", "ostrość", "wydajność", "bateria", "pamięć", "quality", "sharpness", "performance", "battery", "memory"),
        listOf("karty", "zakładki", "okna", "tabs", "windows"),
        listOf("jednostki", "milimetry", "centymetry", "cale", "units", "millimeters", "inches"),
    )

    private val folded: List<List<String>> = GROUPS.map { g -> g.map { TextSearch.fold(it) } }

    /**
     * Słowa skojarzone ze słowem zapytania [word] (złożonym, bez polskich znaków): członkowie każdej grupy, której
     * któreś słowo zaczyna się od [word] albo jest jego odmianą. Krótkie słowa (< 3 litery) nie rozszerzają się.
     */
    fun alternatives(word: String): Set<String> {
        if (word.length < 3) return emptySet()
        val out = HashSet<String>()
        for (g in folded) if (g.any { queryMatches(it, word) }) out.addAll(g)
        out.remove(word)
        return out
    }

    /**
     * Czy wyraz tekstu [w] to słowo skojarzone [a] (oba złożone): ten sam wyraz albo jego odmiana ([inflected]).
     * Fragment innego wyrazu się nie liczy.
     */
    fun wordMatches(w: String, a: String): Boolean = w == a || inflected(w, a)

    /** Wyraz [w] zaczyna się od słowa zapytania [q] („gum” → „gumki”) albo jest jego odmianą. */
    fun queryMatches(w: String, q: String): Boolean = w.startsWith(q) || inflected(w, q)

    /**
     * 2c-A12: czy [w] to [q] z inną końcówką — wspólny rdzeń ([stem]) i 1–3 litery po nim („gumki” ↔ „gumka”,
     * „telefonem” ↔ „telefon”). Dłuższy ogon to inny wyraz („obracanie” ≠ „obraz”, „sharpness” ≠ „share”), sam rdzeń
     * też („koło” ≠ „kolor”). Słowa krótsze niż 5 liter i [NO_STEM] pasują tylko jako początek wyrazu.
     */
    fun inflected(w: String, q: String): Boolean {
        if (q.length < 5 || q in NO_STEM) return false
        val s = stem(q)
        return w.startsWith(s) && w.length - s.length in 1..3
    }

    /** Słowa bez przycinania: ich rdzeń z krótką końcówką to inny wyraz („ruled” → „ruler”). */
    val NO_STEM = setOf("ruled")

    /** Rdzeń jak w [SettingsQuery]: bez jednej (5–7 liter) albo dwóch (≥ 8 liter) ostatnich liter. */
    fun stem(w: String): String = when { w.length >= 8 -> w.dropLast(2); w.length >= 5 -> w.dropLast(1); else -> w }

    /** Synonimy, jakie wyszukiwarka dopasuje do tekstu [text] (etykieta + słowa kluczowe), bez słów samego tekstu. */
    fun of(text: String): Set<String> {
        val words = TextSearch.fold(text).split(Regex("[^\\p{L}\\p{N}-]+")).filter { it.length >= 3 }.toSet()
        val out = HashSet<String>()
        for (g in folded) if (g.any { m -> words.any { w -> wordMatches(w, m) } }) out.addAll(g)
        out.removeAll(words)
        return out
    }
}
