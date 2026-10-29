package com.mdev.mnotes.core.guide

/**
 * 3c-A10: przewodnik po ustawieniach („okładka” aplikacji; projekt w `docs/features/PRZEWODNIK-USTAWIEN.md`). Tu jest
 * cała treść i logika — strony, opcje, zalecenia, kolejność, stan — bez Androida, żeby aplikacja, testy i podglądy
 * korzystały z jednego źródła. Ustawienia są widziane przez [Store] pod tymi samymi kluczami co w `Settings`.
 */
object SetupGuide {
    /** Dostęp do ustawień: wartości jako Int (przełączniki 0/1, wygładzanie 0–2 — przelicza adapter w `ui`). */
    interface Store {
        fun get(key: String): Int
        fun set(key: String, v: Int)
    }

    /** Rodzaj żywej próbki obok opcji (rysuje ją `ui`; makiety z [SetupScenes]). */
    enum class Sample { WELCOME, HAND, INPUT, PAGE, THEME, TOOLBAR, DONE, WRITE, ERASER, GESTURES, MATH, VIEW, HINTS }

    class Option(
        val pl: String, val en: String,
        val notePl: String = "", val noteEn: String = "",
        /** Ustawienia, które opcja zapisuje (mogą zależeć od innych, np. pasek z boku po stronie wolnej ręki). */
        val sets: (Store) -> Map<String, Int>,
    )

    class Page(
        val id: String,
        val titlePl: String, val titleEn: String,
        val bodyPl: String, val bodyEn: String,
        val sample: Sample,
        val options: List<Option>,
        /** Indeks opcji zalecanej (może zależeć od wcześniejszych wyborów, np. zastosowania). */
        val recommended: (Store) -> Int = { 0 },
    )

    class Topic(val id: String, val titlePl: String, val titleEn: String, val descPl: String, val descEn: String, val pages: List<Page>)

    // — klucze spoza `Settings` (zapisuje je przewodnik) —
    const val USE = "setup.use"
    const val USE_NOTES = 0
    const val USE_MATH = 1
    const val USE_PDF = 2
    const val USE_DRAW = 3

    /** Strona bez zalecanej opcji. */
    const val NONE = -1

    const val START = "start"
    /** Lista tematów „Poznaj więcej” (strona „Gotowe” bez ścieżki startu). */
    const val HUB = "hub"

    private fun one(key: String, v: Int): (Store) -> Map<String, Int> = { mapOf(key to v) }
    private fun onOff(key: String, onPl: String, onEn: String, offPl: String, offEn: String, notePl: String = "", noteEn: String = "") = listOf(
        Option(onPl, onEn, notePl, noteEn, one(key, 1)),
        Option(offPl, offEn, sets = one(key, 0)),
    )

    /** Pasek z boku stoi po stronie wolnej ręki: praworęczny — z lewej (2), leworęczny — z prawej (3). */
    fun sideToolbar(s: Store): Int = if (s.get("input.hand") == 1) 3 else 2

    val DONE_PAGE = Page(
        "done", "Gotowe", "All set",
        "Możesz zacząć pisać. Resztę funkcji pokażemy, kiedy zechcesz — wybierz temat poniżej albo wróć tu z menu.",
        "You can start writing. We'll show the rest whenever you like — pick a topic below or come back from the menu.",
        Sample.DONE, emptyList(),
    )

    val START_PAGES: List<Page> = listOf(
        Page(
            "welcome", "Witaj w mNotes", "Welcome to mNotes",
            "Kilka krótkich pytań — wszystko zmienisz później w Ustawieniach. Do czego głównie będziesz używać mNotes?",
            "A few short questions — you can change everything later in Settings. What will you mostly use mNotes for?",
            Sample.WELCOME,
            listOf(
                Option("Notatki", "Notes", "wykłady, spotkania, plany", "lectures, meetings, plans", one(USE, USE_NOTES)),
                Option("Matematyka i nauki ścisłe", "Maths & science", "wzory, wykresy, konstrukcje", "formulas, graphs, constructions", one(USE, USE_MATH)),
                Option("Czytanie PDF", "Reading PDFs", "podkreślanie i dopiski", "highlighting and margin notes", one(USE, USE_PDF)),
                Option("Rysowanie", "Drawing", "szkice, schematy", "sketches, diagrams", one(USE, USE_DRAW)),
            ),
            // Pytanie o zastosowanie nie ma dobrej odpowiedzi — zalecenie byłoby tu podpowiadaniem na siłę.
            recommended = { NONE },
        ),
        Page(
            "hand", "Którą ręką piszesz?", "Which hand do you write with?",
            "Listwa pióra, mini-mapa i kapsuła Cofnij ustawią się pod Twoją rękę.",
            "The pen strip, mini-map and undo capsule arrange themselves for your hand.",
            Sample.HAND,
            listOf(
                Option("Prawą", "Right hand", sets = one("input.hand", 0)),
                Option("Lewą", "Left hand", "też: strona da się odsunąć spod dłoni", "also: the page can be pushed out from under your palm", one("input.hand", 1)),
            ),
        ),
        Page(
            "input", "Rysik i palec", "Stylus and finger",
            "Rysik zawsze pisze. Wybierz, co ma robić palec — spróbuj na kartce w podglądzie.",
            "The stylus always writes. Choose what your finger does — try it on the preview page.",
            Sample.INPUT,
            listOf(
                Option("Palec przewija i powiększa", "Finger scrolls and zooms", "najwygodniej z rysikiem", "best with a stylus", one("input.finger", 0)),
                Option("Palec też pisze", "Finger writes too", "gdy nie masz rysika", "if you have no stylus", one("input.finger", 1)),
                Option("Palec nic nie robi", "Finger does nothing", "tylko rysik, nic przypadkiem", "stylus only, nothing by accident", one("input.finger", 2)),
            ),
        ),
        Page(
            "page", "Jaka kartka?", "What kind of paper?",
            "Wzór nowych stron. Każdą stronę możesz potem zmienić osobno.",
            "The pattern for new pages. You can change any page separately later.",
            Sample.PAGE,
            listOf(
                Option("Gładka", "Blank", sets = one("new.template", 0)),
                Option("Linie", "Lined", sets = one("new.template", 1)),
                Option("Kratka", "Grid", sets = one("new.template", 2)),
                Option("Kropki", "Dots", sets = one("new.template", 3)),
            ),
            recommended = { s -> when (s.get(USE)) { USE_MATH -> 2; USE_DRAW -> 0; else -> 1 } },
        ),
        Page(
            "theme", "Jasno czy ciemno?", "Light or dark?",
            "Ciemny tryb odwraca też strony, zachowując kolory pisma. Zmiana działa od razu — zobacz ten ekran.",
            "Dark mode turns the pages dark too and keeps your ink colours. It applies right away — look at this screen.",
            Sample.THEME,
            listOf(
                Option("Jak w systemie", "Follow the system", "strony zostają takie, jakie powstały", "pages stay as they were made", { mapOf("ui.theme" to 0, "pages.theme" to 0) }),
                Option("Jasny", "Light", sets = { mapOf("ui.theme" to 1, "pages.theme" to 1) }),
                Option("Ciemny, także strony", "Dark, pages too", sets = { mapOf("ui.theme" to 2, "pages.theme" to 2) }),
            ),
        ),
        Page(
            "toolbar", "Gdzie pasek narzędzi?", "Where should the toolbar go?",
            "Na dużym ekranie wygodnie u góry; z boku zostawia więcej wysokości na kartkę.",
            "On a large screen the top is handy; on the side it leaves more height for the page.",
            Sample.TOOLBAR,
            listOf(
                Option("U góry", "Top", sets = one("toolbar.position", 0)),
                Option("Na dole", "Bottom", sets = one("toolbar.position", 1)),
                Option("Z boku, po stronie wolnej ręki", "On the side of your free hand", sets = { s -> mapOf("toolbar.position" to sideToolbar(s)) }),
            ),
        ),
        DONE_PAGE,
    )

    val TOPICS: List<Topic> = listOf(
        Topic(
            "write", "Pisanie i kształty", "Writing and shapes", "prostowanie, wygładzanie", "straightening, smoothing",
            listOf(
                Page(
                    "hold_shape", "Prostowanie kształtów", "Straightening shapes",
                    "Narysuj okrąg albo linię i przytrzymaj rysik na końcu — zamieni się w równy kształt.",
                    "Draw a circle or a line and hold the stylus at the end — it turns into a neat shape.",
                    Sample.WRITE, onOff("input.hold_to_shape", "Prostuj po przytrzymaniu", "Straighten on hold", "Nie prostuj", "Don't straighten"),
                ),
                Page(
                    "smoothing", "Wygładzanie pisma", "Smoothing",
                    "Więcej wygładzania to równiejsze linie, mniej — wierniejsze pismo.",
                    "More smoothing gives even lines; less keeps your handwriting truer.",
                    Sample.WRITE,
                    listOf(
                        Option("Mało", "Low", sets = one("pen.smoothing", 0)),
                        Option("Średnio", "Medium", sets = one("pen.smoothing", 1)),
                        Option("Dużo", "High", sets = one("pen.smoothing", 2)),
                    ),
                    recommended = { 1 },
                ),
            ),
        ),
        Topic(
            "erase", "Gumka i zaznaczanie", "Eraser and selection", "jak ściera gumka, co łapie lasso", "how the eraser works, what the lasso picks",
            listOf(
                Page(
                    "eraser", "Jak ściera gumka?", "How should the eraser work?",
                    "Obiektowa usuwa całą kreskę jednym dotknięciem, pikselowa ściera tylko to, czego dotknie.",
                    "Stroke eraser removes a whole stroke with one touch; pixel eraser rubs out only what it touches.",
                    Sample.ERASER,
                    listOf(
                        Option("Całe kreski", "Whole strokes", sets = one("eraser.mode", 0)),
                        Option("Tylko dotknięte miejsce", "Only what it touches", sets = one("eraser.mode", 1)),
                        Option("Obrysuj, co usunąć", "Circle what to erase", sets = one("eraser.mode", 2)),
                    ),
                ),
                Page(
                    "lasso", "Co zaznacza lasso?", "What does the lasso pick?",
                    "Lasso to pętla wokół obiektów. Może łapać tylko to, co jest w środku, albo wszystko, czego dotknie.",
                    "The lasso is a loop around things. It can pick only what's inside, or anything it touches.",
                    Sample.ERASER, onOff("lasso.touch", "Wszystko, czego dotknie", "Anything it touches", "To, co w środku", "What's inside"),
                    recommended = { 1 },
                ),
            ),
        ),
        Topic(
            "gestures", "Gesty", "Gestures", "dotknięcia palcem, przycisk rysika", "finger taps, stylus button",
            listOf(
                Page(
                    "double_tap", "Podwójne dotknięcie palcem", "Double tap with a finger",
                    "Szybki skrót bez szukania przycisku.", "A quick shortcut without looking for a button.",
                    Sample.GESTURES,
                    listOf(
                        Option("Cofnij", "Undo", sets = one("input.double_tap", 0)),
                        Option("Ponów", "Redo", sets = one("input.double_tap", 1)),
                        Option("Przybliż / oddal", "Zoom in / out", sets = one("input.double_tap", 2)),
                        Option("Nic", "Nothing", sets = one("input.double_tap", 3)),
                    ),
                ),
                Page(
                    "two_finger", "Cofanie dwoma palcami", "Two-finger undo",
                    "Stuknij dwoma palcami naraz, żeby cofnąć ostatni ruch.", "Tap with two fingers at once to undo the last step.",
                    Sample.GESTURES, onOff("input.two_finger_undo", "Włączone", "On", "Wyłączone", "Off"),
                ),
                Page(
                    "pen_button", "Przycisk rysika", "Stylus button",
                    "Co robi rysik, gdy trzymasz wciśnięty jego przycisk.", "What the stylus does while you hold its button.",
                    Sample.GESTURES,
                    listOf(
                        Option("Gumka", "Eraser", sets = one("input.pen_button", 0)),
                        Option("Lasso", "Lasso", sets = one("input.pen_button", 1)),
                        Option("Laser", "Laser", sets = one("input.pen_button", 3)),
                        Option("Nic", "Nothing", sets = one("input.pen_button", 4)),
                    ),
                ),
            ),
        ),
        Topic(
            "math", "Matematyka", "Maths", "symbole w tekście, podpisy punktów", "symbols in text, point labels",
            listOf(
                Page(
                    "math_text", "Symbole w tekście", "Symbols in text",
                    "Wpisz \\alpha, x^2 albo a_1 w polu tekstu — zamieni się w α, x², a₁.",
                    "Type \\alpha, x^2 or a_1 in a text box — it becomes α, x², a₁.",
                    Sample.MATH, onOff("text.math_unicode", "Zamieniaj", "Convert", "Zostaw, jak wpisano", "Leave as typed"),
                ),
                Page(
                    "construct_names", "Podpisy w konstrukcjach", "Labels in constructions",
                    "Konstrukcje (środek, okrąg opisany…) podpisują nowe punkty literami A, B, O.",
                    "Constructions (midpoint, circumcircle…) label new points with A, B, O.",
                    Sample.MATH, onOff("construct.names", "Podpisuj punkty", "Label points", "Bez podpisów", "No labels"),
                ),
            ),
        ),
        Topic(
            "view", "Widok", "View", "zapas przewijania, mini-mapa, rozmiar paska", "scroll margin, mini-map, toolbar size",
            listOf(
                Page(
                    "scroll_margin", "Odsuwanie kartki", "Pushing the page aside",
                    "Kartkę da się przewinąć o pół ekranu poza brzeg — wygodniej pisać przy brzegu, gdy dłoń leży na ekranie.",
                    "The page can scroll half a screen past its edge — easier to write near the edge with your palm on the screen.",
                    Sample.VIEW, onOff("view.scroll_margin", "Włączone", "On", "Wyłączone", "Off"),
                ),
                Page(
                    "minimap", "Mini-mapa", "Mini-map",
                    "Przy dużym powiększeniu w rogu widać, którą część strony oglądasz.",
                    "When zoomed in, a corner map shows which part of the page you're looking at.",
                    Sample.VIEW, onOff("view.minimap", "Pokazuj", "Show", "Nie pokazuj", "Don't show"),
                    recommended = { 1 },
                ),
                Page(
                    "toolbar_size", "Rozmiar przycisków", "Button size",
                    "Większe przyciski łatwiej trafić palcem, mniejsze zostawiają więcej miejsca.",
                    "Larger buttons are easier to hit with a finger; smaller ones leave more room.",
                    Sample.TOOLBAR,
                    listOf(
                        Option("Małe", "Small", sets = one("toolbar.size", 0)),
                        Option("Zwykłe", "Normal", sets = one("toolbar.size", 1)),
                        Option("Duże", "Large", sets = one("toolbar.size", 2)),
                    ),
                    recommended = { 1 },
                ),
            ),
        ),
        Topic(
            "hints", "Podpowiedzi", "Hints", "dymki, nazwy przycisków, wibracja", "tips, button names, vibration",
            listOf(
                Page(
                    "tool_hints", "Dymki o narzędziach", "Tool tips",
                    "Po wybraniu mniej oczywistego narzędzia (taśma, pędzel formatu) krótki dymek mówi, jak go użyć.",
                    "After picking a less obvious tool (tape, format painter) a short tip says how to use it.",
                    Sample.HINTS, onOff("ui.tool_hints", "Pokazuj", "Show", "Nie pokazuj", "Don't show"),
                ),
                Page(
                    "tooltips", "Nazwy przycisków", "Button names",
                    "Przytrzymaj przycisk albo najedź rysikiem, żeby zobaczyć jego nazwę.",
                    "Hold a button or hover the stylus over it to see its name.",
                    Sample.HINTS, onOff("ui.tooltips", "Pokazuj", "Show", "Nie pokazuj", "Don't show"),
                ),
                Page(
                    "haptics", "Wibracja", "Vibration",
                    "Delikatne drgnięcie przy dotknięciu przycisku paska.", "A gentle tick when you tap a toolbar button.",
                    Sample.HINTS, onOff("ui.haptics", "Włączona", "On", "Wyłączona", "Off"),
                ),
            ),
        ),
    )

    /** Klucze ustawień używane przez przewodnik (adapter w `ui` musi znać każdy — pilnuje tego test). */
    fun keys(probe: Store): Set<String> {
        val out = HashSet<String>()
        for (p in START_PAGES + TOPICS.flatMap { it.pages }) for (o in p.options) out += o.sets(probe).keys
        return out
    }

    fun pages(path: String): List<Page> = when (path) {
        START -> START_PAGES
        HUB -> listOf(DONE_PAGE)
        else -> TOPICS.firstOrNull { it.id == path }?.pages ?: listOf(DONE_PAGE)
    }

    /** Czy zalecenie strony [p] wskazuje istniejącą opcję. */
    fun recommendedIn(p: Page, s: Store): Boolean = p.recommended(s).let { it == NONE || it in p.options.indices }

    /** Zapisuje ustawienia opcji [i] strony [p]. */
    fun choose(p: Page, i: Int, s: Store) { p.options.getOrNull(i)?.sets?.invoke(s)?.forEach { (k, v) -> s.set(k, v) } }

    /** Opcja, której wszystkie ustawienia mają bieżące wartości; −1 — żadna (np. pasek ustawiony gdzie indziej). */
    fun current(p: Page, s: Store): Int = p.options.indexOfFirst { o -> o.sets(s).all { (k, v) -> s.get(k) == v } }

    /**
     * Stan przewodnika: ścieżka ([START], [HUB] albo id tematu), strona na niej, czy ścieżka startu jest za nami i
     * które tematy przejrzano. Zapisywany jako zwykłe ustawienie tekstowe ([encode]).
     */
    data class State(val path: String = START, val index: Int = 0, val startDone: Boolean = false, val seen: Set<String> = emptySet()) {
        val page: Page get() = pages(path).let { it[index.coerceIn(0, it.size - 1)] }
        val count: Int get() = pages(path).size
        val first: Boolean get() = index <= 0
        val last: Boolean get() = index >= count - 1

        /** „Dalej”: następna strona; za ostatnią stroną tematu — lista tematów, temat przejrzany. */
        fun next(): State = when {
            !last -> copy(index = index + 1)
            path == START || path == HUB -> copy(path = HUB, index = 0, startDone = true)
            else -> copy(path = HUB, index = 0, seen = seen + path)
        }
        fun back(): State = if (first) this else copy(index = index - 1)
        /** „Pomiń”: przy starcie kończy przewodnik (nie wyskoczy znowu), w temacie wraca do listy tematów. */
        fun skip(): State = if (path == START) copy(path = HUB, index = 0, startDone = true) else copy(path = HUB, index = 0)
        fun open(topic: String): State = copy(path = topic, index = 0)

        fun encode(): String = "$path|$index|${if (startDone) 1 else 0}|${seen.sorted().joinToString(",")}"

        companion object {
            fun decode(s: String): State {
                val p = s.split('|')
                if (p.size < 4) return State()
                val path = p[0].takeIf { it == START || it == HUB || TOPICS.any { t -> t.id == it } } ?: START
                val idx = (p[1].toIntOrNull() ?: 0).coerceIn(0, pages(path).size - 1)
                val seen = p[3].split(',').filter { id -> TOPICS.any { it.id == id } }.toSet()
                return State(path, idx, p[2] == "1", seen)
            }
        }
    }

    enum class Entry { FULL, SUGGEST, NONE }

    /**
     * Czy pokazać przewodnik przy starcie: nowa instalacja — cały, raz; stara instalacja albo przerwany przewodnik —
     * tylko propozycja w bibliotece (do odrzucenia); po skończonym starcie albo odrzuceniu — nic.
     */
    fun entry(state: String, oldInstall: Boolean, dismissed: Boolean): Entry {
        if (dismissed) return Entry.NONE
        if (state.isEmpty()) return if (oldInstall) Entry.SUGGEST else Entry.FULL
        return if (State.decode(state).startDone) Entry.NONE else Entry.SUGGEST
    }
}
