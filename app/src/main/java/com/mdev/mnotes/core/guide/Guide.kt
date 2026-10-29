package com.mdev.mnotes.core.guide

import com.mdev.mnotes.core.color.ThemeMap
import com.mdev.mnotes.core.geom.Axes
import com.mdev.mnotes.core.geom.AxesSpec
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Paper
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TapeEl
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.pen.LineStyle
import com.mdev.mnotes.core.shapes.Bezier
import com.mdev.mnotes.core.shapes.CurveFit
import com.mdev.mnotes.core.text.TextMeasure
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Zeszyt-przewodnik (F20, przebudowany w 2b-A11): okładka ze spisem treści, rozdziały „Podstawy” → „Zaawansowane” →
 * „Sprawdź się”. Każda strona to jedna lekcja: krótki wstęp, ponumerowane polecenia do wykonania piórem na tej kartce,
 * wskazówka w karcie i ilustracja pod tekstem — narysowana tym samym kodem co aplikacja (krzywe, osie, tryb ciemny),
 * więc wygląda jak to, co użytkownik zaraz zrobi. Treść to zwykłe elementy: przewodnik jest zwyczajnym zeszytem.
 */
object Guide {
    /**
     * Strona; [textBottom] i [artTop] — koniec tekstu i początek ilustracji (test pilnuje, że się nie nakładają);
     * [chapter] — rozdział (−1 okładka); [answers] — odpowiedzi strony „Sprawdź się”, każda pod swoją taśmą.
     */
    class Page(
        val spec: PageSpec, val elements: List<Element>, val title: String = "", val textBottom: Float = 0f, val artTop: Float = 0f,
        val chapter: Int = -1, val answers: List<TextBox> = emptyList(),
    )

    /**
     * Lekcja: rozdział (0 podstawy, 1 zaawansowane), tytuł, wstęp, polecenia, wskazówka, ilustracja od y; [answers] —
     * odpowiedzi do poleceń na stronie „Sprawdź się” (null — polecenie bez odpowiedzi).
     */
    private class Lesson(val chapter: Int, val title: String, val intro: String, val steps: List<String>, val tip: String?, val answers: List<String?> = emptyList(), val art: (Float) -> List<Element>)

    // Spokojna paleta: grafit, zieleń akcentu Mint‑Y, przygaszony błękit i czerwień; szare wzory do przerysowania.
    private const val INK = 0xFF263238.toInt()
    private const val ACCENT = 0xFF2E7D4F.toInt()
    private const val MUTED = 0xFF78909C.toInt()
    private const val HINT = 0x8090A4AE.toInt()
    private const val RULE = 0xFFCFD8DC.toInt()
    private const val TIP_BG = 0xFFE8F3EC.toInt()
    private const val TIP_INK = 0xFF2F4A3A.toInt()
    private const val BLUE = 0xFF3D6B8C.toInt()
    private const val RED = 0xFFB5523B.toInt()
    private const val GOLD = 0xFFD39B2A.toInt()
    private const val BOARD = 0xFF2F3E36.toInt()
    private const val CHALK = 0xFFEDEBE0.toInt()
    private const val TAPE = 0xFFFFD54F.toInt()   // jak domyślna taśma w aplikacji: uczeń zna ją z własnych notatek
    private const val MARGIN = 48f
    private const val BODY = 11.5f
    private const val ANSWER = 10.5f

    /** Dół obszaru treści: niżej jest tylko stopka. */
    const val FOOT = 794f

    /**
     * Strony przewodnika po polsku albo angielsku; [lab] — wariant z laboratorium (kreda na zdjęciach tablicy).
     * Wysokości akapitów liczy [m] — w aplikacji StaticLayout tą samą czcionką co pole tekstu — bo szacunek z liczby
     * znaków nie zna czcionki urządzenia: przy szerszej tekst wchodził na ilustrację, przy węższej zostawały dziury.
     */
    fun pages(pl: Boolean, lab: Boolean = false, m: TextMeasure): List<Page> = Build(m).pages(pl, lab)

    private class Build(private val m: TextMeasure) {
        fun pages(pl: Boolean, lab: Boolean): List<Page> {
            val ls = lessons(pl, lab)
            val chapters = chapterNames(pl)
            val total = ls.size + 1
            val out = ArrayList<Page>(total)
            out.add(cover(pl, ls, chapters, total))
            ls.forEachIndexed { i, l -> out.add(lessonPage(l, chapters[l.chapter], i + 2, total, pl)) }
            return out
        }

        private fun chapterNames(pl: Boolean) =
            if (pl) listOf("Rozdział I · Podstawy", "Rozdział II · Zaawansowane") else listOf("Chapter I · Basics", "Chapter II · Advanced")

        private fun lessons(pl: Boolean, lab: Boolean): List<Lesson> {
            fun t(p: String, e: String) = if (pl) p else e
            return listOf(
                // — Rozdział I: podstawy —
                Lesson(0, t("Pisanie i kolory", "Writing and colours"),
                    t("Ten zeszyt to ćwiczenia — pisz po nim śmiało. Gdy coś zepsujesz, cofnij: stuknij dwoma palcami albo Ctrl+Z.", "This notebook is practice — write on it freely. If you break something, undo: tap with two fingers or press Ctrl+Z."),
                    listOf(
                        t("Wybierz długopis na pasku i przerysuj szarą falę poniżej.", "Pick the ballpoint on the toolbar and trace the grey wave below."),
                        t("Listwa obok strony zmienia grubość, styl linii i kolor jednym dotknięciem. Dotknij wybranego koloru drugi raz — otworzy się panel pióra.", "The strip beside the page changes width, line style and colour in one tap. Tap the chosen colour again to open the pen panel."),
                        t("Zakreślaczem zaznacz słowo „ważne” — kładzie się pod pismem, więc tekst zostaje czytelny.", "Mark the word “important” with the highlighter — it goes under the ink, so the text stays readable."),
                    ),
                    t("Przytrzymaj kropkę koloru na listwie, żeby ją przestawić, schować albo przenieść.", "Long-press a colour dot on the strip to move, hide or reorder it."),
                ) { y -> wave(MARGIN, y + 26f, 80) + listOf(text(MARGIN, y + 70f, t("ważne", "important"), 16f, INK)) },
                Lesson(0, t("Kształty: narysuj i przytrzymaj", "Shapes: draw and hold"),
                    t("Narysuj kształt i nie odrywaj pióra przez pół sekundy — wyprostuje się. Trzymając dalej, zmieniasz jego rozmiar.", "Draw a shape and keep the pen down for half a second — it snaps. Keep holding to resize it."),
                    listOf(
                        t("Przerysuj szare wzory: koło, prostokąt, trójkąt — każdy jednym pociągnięciem, z przytrzymaniem na końcu.", "Trace the grey shapes: circle, rectangle, triangle — each in one stroke, holding at the end."),
                        t("Narysuj strzałkę i krótką kreskę ułamkową — też się prostują.", "Draw an arrow and a short fraction bar — they snap too."),
                        t("Gotowy kształt jest zaznaczony z punktami — przeciągnij jeden i zobacz, jak zmienia się figura.", "The finished shape stays selected with points — drag one and watch the figure change."),
                    ),
                    t("√ i ∑ narysowane jednym pociągnięciem i przytrzymane też się wyrównują (panel pióra → „Auto popraw”).", "√ and ∑ drawn in one stroke and held get straightened too (pen panel → “Auto-correct”)."),
                ) { y ->
                    dashed(listOf(circle(MARGIN + 55f, y + 60f, 50f), rect(MARGIN + 140f, y + 10f, MARGIN + 260f, y + 110f), listOf(MARGIN + 300f, y + 110f, MARGIN + 360f, y + 10f, MARGIN + 420f, y + 110f, MARGIN + 300f, y + 110f))) +
                        dashed(listOf(listOf(MARGIN, y + 160f, MARGIN + 160f, y + 160f), listOf(MARGIN + 144f, y + 150f, MARGIN + 160f, y + 160f, MARGIN + 144f, y + 170f)))
                },
                Lesson(0, t("Gumka: cztery sposoby", "Eraser: four ways"),
                    t("Gumka ma cztery tryby; zmieniasz je ikonami na listwie, gdy gumka jest w ręku.", "The eraser has four modes; switch them with the icons on the strip while the eraser is active."),
                    listOf(
                        t("Obiektowa: przejedź po pierwszej fali — znika cała.", "Object: run over the first wave — it disappears whole."),
                        t("Pikselowa: zetrzyj środek drugiej — zostaną dwa kawałki.", "Pixel: erase the middle of the second — two pieces remain."),
                        t("Lasso gumki: obrysuj trzecią — usuwa to, co obrysujesz.", "Lasso eraser: circle the third — it removes what you circle."),
                        t("Wycinanie: obrysuj połowę czwartej — kreska zostaje przecięta dokładnie na obrysie.", "Cut out: circle half of the fourth — the stroke is cut exactly along the loop."),
                    ),
                    t("„Gumka pomija zakreślacz” na listwie gumki chroni zakreślenia, gdy poprawiasz pismo.", "“Eraser ignores highlighter” on the eraser strip protects highlights while you fix the writing."),
                ) { y ->
                    val names = listOf(t("obiektowa", "object"), t("pikselowa", "pixel"), t("lasso", "lasso"), t("wycinanie", "cut out"))
                    (0 until 4).flatMap { listOf(text(MARGIN, y + 14f + it * 56f, names[it], 9f, MUTED, w = 74f)) + wave(MARGIN + 82f, y + 22f + it * 56f, 66, INK) }
                },
                Lesson(0, t("Lasso: zaznacz, przesuń, zapisz", "Lasso: select, move, save"),
                    t("Lasso zaznacza obiekty; zaznaczenie przeciągasz, skalujesz i obracasz.", "The lasso selects objects; you drag, resize and rotate the selection."),
                    listOf(
                        t("Obrysuj kwadrat i koło razem i przeciągnij je w inne miejsce.", "Circle the square and the circle together and drag them elsewhere."),
                        t("„Skaluj i obracaj” na pasku zaznaczenia pokazuje uchwyty.", "“Resize & rotate” on the selection bar shows the handles."),
                        t("„Tylko zahacz” na listwie lassa: wystarczy musnąć obiekt. Prostokąt łapie to, co w ramce.", "“Just touch” on the lasso strip: brushing an object is enough. The rectangle catches what is inside the frame."),
                    ),
                    t("Przytrzymaj zaznaczenie — menu ma m.in. „Zaznaczenie jako ▸” (wycinek, naklejka) i „Konstrukcja ▸”.", "Long-press the selection — the menu has “Selection as ▸” (snip, sticker) and “Construction ▸”, among others."),
                ) { y -> solid(listOf(rect(MARGIN + 20f, y + 10f, MARGIN + 120f, y + 110f), circle(MARGIN + 220f, y + 60f, 50f))) },
                Lesson(0, t("Tekst, wzory i greckie litery", "Text, formulas and Greek letters"),
                    t("Narzędzie Tekst: dotknij miejsca i pisz z klawiatury. Pole rośnie samo; uchwyty boków zmieniają szerokość.", "Text tool: tap a spot and type. The box grows by itself; side handles change its width."),
                    listOf(
                        t("W ramce poniżej wpisz: \\alpha + \\beta = 90° — \\alpha zamieni się w α.", "In the frame below type: \\alpha + \\beta = 90° — \\alpha turns into α."),
                        t("Indeksy: x^2 daje x², a_1 daje a₁. Zamiana następuje po kolejnym znaku (np. spacji), więc x^12 też działa.", "Indices: x^2 gives x², a_1 gives a₁. The change happens after the next character (e.g. a space), so x^12 works too."),
                        t("Dotknięcie Tekstem pod ostatnią stroną zakłada nową stronę.", "A Text tap below the last page creates a new page."),
                    ),
                    t("Zamianę wyłączysz w Ustawieniach → „Grecki i indeksy w tekście”.", "Turn the conversion off in Settings → “Greek and indices in text”."),
                ) { y ->
                    dashed(listOf(rect(MARGIN, y + 8f, MARGIN + 499f, y + 68f))) + listOf(
                        text(MARGIN, y + 84f, t("Tak ma wyglądać:", "It should look like this:"), 9f, MUTED),
                        text(MARGIN, y + 100f, "α + β = 90°     x² + y² = r²     a₁, a₂, …", 15f, BLUE),
                    )
                },
                Lesson(0, t("Strony i panel stron", "Pages and the pages panel"),
                    t("Panel stron pokazuje miniatury. Przeciągnij miniaturę, żeby zmienić kolejność stron.", "The pages panel shows thumbnails. Drag a thumbnail to reorder pages."),
                    listOf(
                        t("Otwórz panel stron i dodaj stronę; obok „Dodaj stronę” jest strona pozioma.", "Open the pages panel and add a page; next to “Add page” there is a landscape page."),
                        t("„⋮” → „Styl strony…”: wzór, papier, rozmiar i orientacja — dla tej strony albo całego zeszytu.", "“⋮” → “Page style…”: template, paper, size and orientation — for this page or the whole notebook."),
                        t("Zakładka (Ctrl+B) oznacza początek tematu, a tag z „⋮” przy miniaturze — rodzaj strony: szukaj potem „#definicja”.", "A bookmark (Ctrl+B) marks the start of a topic, a tag from the thumbnail’s “⋮” — the kind of page: then search “#definition”."),
                    ),
                    t("Ustawienia mają wyszukiwarkę: wpisz potocznie, np. „gumka” albo „zoom”.", "Settings have a search box: type plain words, e.g. “eraser” or “zoom”."),
                ) { y ->
                    val thumbs = (0 until 3).flatMap { i ->
                        listOf(stroke(rect(MARGIN, y + 8f + i * 76f, MARGIN + 50f, y + 74f + i * 76f).toFloatArray(), INK, LineStyle.SOLID, true).withStyle(width = 1f), text(MARGIN + 19f, y + 30f + i * 76f, "${i + 1}", 12f, MUTED))
                    }
                    val arrow = curve(floatArrayOf(MARGIN + 60f, y + 196f, MARGIN + 92f, y + 120f, MARGIN + 60f, y + 70f), false, ACCENT, LineStyle.DASHED)
                    val head = stroke(floatArrayOf(MARGIN + 70f, y + 64f, MARGIN + 60f, y + 70f, MARGIN + 70f, y + 78f), ACCENT, LineStyle.SOLID, true)
                    val land = stroke(rect(MARGIN + 140f, y + 8f, MARGIN + 230f, y + 74f).toFloatArray(), INK, LineStyle.SOLID, true).withStyle(width = 1f)
                    thumbs + listOf(arrow, head, land, text(MARGIN + 150f, y + 82f, t("pozioma", "landscape"), 9f, MUTED))
                },

                // — Sprawdzian rozdziału I: pytania z odpowiedziami pod taśmą (2c-A14) —
                Lesson(0, t("Sprawdź się: podstawy", "Try it yourself: basics"),
                    t("Najpierw spróbuj sam, dopiero potem odsłoń odpowiedź pod żółtą taśmą.", "Try first, and only then uncover the answer under the yellow tape."),
                    listOf(
                        t("Jak otworzysz panel pióra z grubością i „Auto popraw”?", "How do you open the pen panel with width and “Auto-correct”?"),
                        t("Narysuj w ramce koło jednym pociągnięciem. Co zrobić, żeby stało się równym okręgiem?", "Draw a circle in the frame in one stroke. What makes it a perfect circle?"),
                        t("Którym trybem gumki przetniesz kreskę dokładnie wzdłuż obrysu?", "Which eraser mode cuts a stroke exactly along a loop?"),
                        t("Wpisz Tekstem a_1 + a_2 = x^2 i spację. Co zobaczysz?", "Type a_1 + a_2 = x^2 and a space with the Text tool. What do you see?"),
                        t("Jak oznaczysz stronę, żeby znaleźć ją potem, wpisując „#definicja”?", "How do you mark a page so you can find it later by typing “#definition”?"),
                    ),
                    t("Taśma (na pasku albo pod „⋯”, klawisz K): dotknij paska, żeby odsłonić, i drugi raz, żeby zakryć. Przeciągnięciem Taśmą zakryjesz też własne notatki do powtórki.", "Tape (on the toolbar or under “⋯”, key K): tap the strip to uncover it and again to cover it. Drag with the Tape to cover your own notes for revision."),
                    answers = listOf(
                        t("Dotknij wybranego koloru na listwie drugi raz.", "Tap the chosen colour on the strip a second time."),
                        t("Na końcu nie odrywaj pióra przez pół sekundy — kształt się wyprostuje.", "Keep the pen down for half a second at the end — the shape snaps."),
                        t("„Wycinanie” — czwarty tryb na listwie gumki.", "“Cut out” — the fourth mode on the eraser strip."),
                        t("a₁ + a₂ = x² — zamiana następuje po kolejnym znaku.", "a₁ + a₂ = x² — the change happens after the next character."),
                        t("Tagiem z „⋮” przy miniaturze strony w panelu stron.", "With a tag from the “⋮” on the page thumbnail in the pages panel."),
                    ),
                ) { y -> dashed(listOf(rect(MARGIN, y + 8f, MARGIN + 499f, y + 168f))) + listOf(text(MARGIN + 8f, y + 14f, t("Miejsce na próby", "Room to practise"), 9f, MUTED)) },

                // — Rozdział II: zaawansowane —
                Lesson(1, t("Krzywe Béziera", "Bézier curves"),
                    t("Narzędzie Krzywa ma cztery tryby na początku listwy. Krzywa ma uchwyty — poprawisz ją po narysowaniu.", "The Curve tool has four modes at the start of the strip. A curve has handles — you can adjust it afterwards."),
                    listOf(
                        t("„Przez wskazane punkty”: stuknij po kolei kropki A–E, na końcu stuknij E drugi raz.", "“Through tapped points”: tap dots A–E in order, then tap E once more."),
                        t("„Kontur zamknięty”: stuknij pięć kropek pierścienia, a potem pierwszą — figura się domknie. Wiaderko na listwie ją wypełni.", "“Closed outline”: tap the five ring dots, then the first one — the shape closes. The bucket on the strip fills it."),
                        t("„Piórko”: stuknij, przeciągnij (wyciągasz uchwyty), stuknij — powstaje łuk jak w programach graficznych.", "“Pen”: tap, drag (pulling out handles), tap — you get an arc as in drawing programs."),
                        t("„Odręczna”: narysuj falę — stanie się krzywą z kilkoma węzłami.", "“Freehand”: draw a wave — it becomes a curve with a few nodes."),
                    ),
                    t("✓ na karcie u góry albo Enter kończy krzywą, ✕ albo Esc ją porzuca.", "✓ on the card at the top or Enter finishes the curve, ✕ or Esc discards it."),
                ) { y ->
                    val through = listOf(10f to 90f, 60f to 30f, 110f to 80f, 160f to 20f, 210f to 70f)
                    val out = ArrayList<Element>()
                    through.forEachIndexed { i, (dx, dy) -> out.add(dot(MARGIN + dx, y + dy)); out.add(text(MARGIN + dx - 4f, y + dy - 22f, "ABCDE"[i].toString(), 10f, MUTED, w = 12f)) }
                    for (i in 0 until 5) { val a = -PI / 2 + 2 * PI * i / 5; out.add(dot(MARGIN + 300f + 45f * cos(a).toFloat(), y + 62f + 45f * sin(a).toFloat())) }
                    out.add(dot(MARGIN + 390f, y + 100f)); out.add(dot(MARGIN + 435f, y + 30f)); out.add(dot(MARGIN + 480f, y + 100f))
                    out.add(stroke(floatArrayOf(MARGIN + 410f, y + 30f, MARGIN + 460f, y + 30f), HINT, LineStyle.DASHED, true))
                    out.add(text(MARGIN, y + 124f, t("przez punkty", "through points"), 9f, MUTED))
                    out.add(text(MARGIN + 270f, y + 124f, t("kontur", "outline"), 9f, MUTED))
                    out.add(text(MARGIN + 400f, y + 124f, t("piórko", "pen"), 9f, MUTED))
                    out
                },
                Lesson(1, t("Geometria: konstrukcje jak cyrklem", "Geometry: constructions like a compass"),
                    t("Narzędzie Geometria (na pasku albo pod „⋯”): wybierz konstrukcję na listwie, potem wskazuj kształty — karta u góry mówi, co dalej.", "The Geometry tool (on the toolbar or under “⋯”): pick a construction on the strip, then point at shapes — the card at the top says what is next."),
                    listOf(
                        t("„Przecięcie okręgów…”: dotknij jednego okręgu, potem drugiego — pojawią się P, Q i prosta przez nie.", "“Circle intersection…”: tap one circle, then the other — P, Q and the line through them appear."),
                        t("„Symetralna odcinka”: dotknij odcinka AB.", "“Perpendicular bisector”: tap segment AB."),
                        t("„Kąt: łuk i miara”: dotknij obu ramion kąta po prawej.", "“Angle: arc and value”: tap both arms of the angle on the right."),
                    ),
                    t("Punkt łapie wierzchołki, środki i przecięcia: przesuń palec, patrząc na lupę, i podnieś. To samo jest w menu „Konstrukcja ▸”.", "Points snap to vertices, midpoints and intersections: slide your finger watching the loupe, then lift. The same is in the “Construction ▸” menu."),
                ) { y ->
                    listOf(
                        ring(MARGIN + 60f, y + 70f, 50f), ring(MARGIN + 125f, y + 70f, 50f),
                        stroke(floatArrayOf(MARGIN + 215f, y + 115f, MARGIN + 335f, y + 35f), INK, LineStyle.SOLID, true),
                        dot(MARGIN + 215f, y + 115f, INK), dot(MARGIN + 335f, y + 35f, INK),
                        text(MARGIN + 200f, y + 120f, "A", 10f, MUTED, w = 12f), text(MARGIN + 340f, y + 18f, "B", 10f, MUTED, w = 12f),
                        stroke(floatArrayOf(MARGIN + 385f, y + 120f, MARGIN + 495f, y + 120f), INK, LineStyle.SOLID, true),
                        stroke(floatArrayOf(MARGIN + 385f, y + 120f, MARGIN + 462f, y + 32f), INK, LineStyle.SOLID, true),
                    )
                },
                Lesson(1, t("Układ współrzędnych", "Coordinate axes"),
                    t("„⋮” → „Układ współrzędnych…” wstawia osie z podziałką. Układ dociska się do kratki strony.", "“⋮” → “Coordinate axes…” inserts axes with ticks. The axes snap to the page grid."),
                    listOf(
                        t("Przerysuj szary wykres na osiach poniżej.", "Trace the grey graph on the axes below."),
                        t("Kreska prowadzona wzdłuż linii siatki układu lekko się do niej dociska — wykres „po kratkach”.", "A stroke drawn along a grid line of the axes gently snaps to it — a graph “on the squares”."),
                        t("Przesuń układ lassem — zostanie na kratce.", "Move the axes with the lasso — they stay on the grid."),
                    ),
                    t("Docisk wyłączysz albo osłabisz w Ustawieniach → „Układ współrzędnych”.", "Turn the snapping off or weaken it in Settings → “Coordinate axes”."),
                ) { y -> axes(MARGIN + 40f, y + 10f) },
                Lesson(1, t("Stanowiska: kilka zeszytów naraz", "Workspaces: several notebooks at once"),
                    t("Stanowisko to kilka dokumentów jeden pod drugim na wspólnym polu, np. wykład w PDF, a pod nim zadania.", "A workspace is several documents one below another on a shared field, e.g. a PDF lecture with exercises below it."),
                    listOf(
                        t("„⋮” → „Dołóż zeszyt poniżej…” i wybierz zeszyt. Rząd przewijasz w bok osobno, w pionie i powiększeniu — wszystkie razem.", "“⋮” → “Add notebook below…” and pick a notebook. Each row scrolls sideways on its own; vertically and in zoom they move together."),
                        t("Zaznacz coś lassem w jednym rzędzie i przeciągnij do drugiego — obiekt przeniesie się do tamtego zeszytu.", "Lasso something in one row and drag it to the other — the object moves to that notebook."),
                        t("„⋮” → „Stanowiska…” zapisuje układ pod nazwą; otworzysz go potem jednym dotknięciem.", "“⋮” → “Workspaces…” saves the layout under a name; open it later with one tap."),
                    ),
                    t("Panel stron pokazuje strony rzędu, którego ostatnio dotknąłeś.", "The pages panel shows the pages of the row you touched last."),
                ) { y ->
                    val out = ArrayList<Element>()
                    out.add(text(MARGIN, y + 2f, t("Wykład.pdf", "Lecture.pdf"), 9f, MUTED))
                    for (i in 0 until 3) out.add(stroke(rect(MARGIN + i * 120f, y + 18f, MARGIN + 100f + i * 120f, y + 88f).toFloatArray(), INK, LineStyle.SOLID, true).withStyle(width = 1f))
                    out.add(text(MARGIN, y + 104f, t("Zadania", "Exercises"), 9f, MUTED))
                    for (i in 0 until 2) out.add(stroke(rect(MARGIN + i * 120f, y + 120f, MARGIN + 100f + i * 120f, y + 190f).toFloatArray(), INK, LineStyle.SOLID, true).withStyle(width = 1f))
                    out.add(star(MARGIN + 170f, y + 52f, 14f, 6f, GOLD))
                    out.add(curve(floatArrayOf(MARGIN + 160f, y + 66f, MARGIN + 110f, y + 110f, MARGIN + 60f, y + 150f), false, ACCENT, LineStyle.DASHED))
                    out
                },
                Lesson(1, t("Profile pracy", "Work profiles"),
                    t("Profil przełącza naraz dotyk palcem, gesty, pasek narzędzi i widok. Wyłączenie profilu przywraca ustawienia sprzed niego.", "A profile switches finger touch, gestures, the toolbar and the view at once. Turning it off restores the settings from before."),
                    listOf(
                        t("„⋮” → Profil pracy → „Tylko rysik”. Dotknij strony palcem — nie pisze, pisze tylko rysik.", "“⋮” → Work profile → “Stylus only”. Touch the page with a finger — it does not write, only the stylus does."),
                        t("Przy numerze strony pojawi się plakietka profilu. Dotknij jej → „Wyłącz profil” — palec działa znów jak wcześniej.", "A profile badge appears by the page number. Tap it → “Turn off profile” — the finger works as before again."),
                        t("W tym samym menu „Włączaj … przy otwieraniu tego zeszytu” przypina profil do zeszytu, np. „Rysowanie” do szkicownika.", "In the same menu “Turn on … when opening this notebook” pins the profile to the notebook, e.g. “Drawing” to a sketchbook."),
                    ),
                    t("Zmienisz ustawienie przy włączonym profilu — aplikacja zapyta, czy zapisać je w profilu. Własne profile: Ustawienia → Profile pracy.", "Change a setting while a profile is on and the app asks whether to save it in the profile. Your own profiles: Settings → Work profiles."),
                ) { y ->
                    listOf(t("Wykład", "Lecture"), t("Rysowanie", "Drawing"), t("Tylko rysik", "Stylus only")).flatMapIndexed { i, n ->
                        val x = MARGIN + i * 150f
                        val on = i == 2
                        listOf(roundRect(x, y + 8f, x + 130f, y + 44f, if (on) ACCENT else TIP_BG, 18f), text(x + 16f, y + 19f, n, 11f, if (on) CHALK else TIP_INK, bold = on, w = 100f))
                    } + wave(MARGIN, y + 96f, 70, INK)
                },
                Lesson(1, t("Tablica: płótno bez brzegów", "Board: a canvas without edges"),
                    t("Biblioteka → „Nowy” → „Nowa tablica (nieskończone płótno)”. Tablica to jedna strona, która rośnie, gdy piszesz blisko brzegu.", "Library → “New” → “New board (infinite canvas)”. A board is one page that grows when you write near its edge."),
                    listOf(
                        t("Pisz coraz bliżej prawego brzegu — płótno urośnie. W lewo i w górę też rośnie, a widok zostaje w miejscu.", "Write closer and closer to the right edge — the canvas grows. It grows left and up too, and the view stays put."),
                        t("Zsuń dwa palce, żeby zobaczyć całość, i rozsuń je nad szczegółem — jak na mapie.", "Pinch to see everything and spread two fingers over a detail — like on a map."),
                        t("Panel stron → „⋮” przy miniaturze → „Kopiuj stronę jako obraz” kopiuje sam obszar z treścią, nie całe płótno.", "Pages panel → “⋮” on the thumbnail → “Copy page as image” copies just the area with content, not the whole canvas."),
                    ),
                    t("Tablica ma zawsze jedną stronę — na nowy temat załóż nową tablicę albo zwykły zeszyt.", "A board always has one page — start a new board or a regular notebook for a new topic."),
                ) { y ->
                    val l = MARGIN + 150f; val top = y + 50f; val r = l + 200f; val b = top + 100f
                    // Strzałki od kartki na zewnątrz: płótno rośnie na wszystkie strony.
                    val arrows = listOf(floatArrayOf(r + 8f, (top + b) / 2f, r + 70f, (top + b) / 2f), floatArrayOf(l - 8f, (top + b) / 2f, l - 70f, (top + b) / 2f),
                        floatArrayOf((l + r) / 2f, top - 6f, (l + r) / 2f, top - 38f), floatArrayOf((l + r) / 2f, b + 6f, (l + r) / 2f, b + 38f))
                    dashed(listOf(rect(MARGIN + 50f, y + 8f, MARGIN + 450f, y + 192f))) + listOf(stroke(rect(l, top, r, b).toFloatArray(), INK, LineStyle.SOLID, true).withStyle(width = 1f)) +
                        wave(l + 16f, top + 50f, 29, INK) + arrows.flatMap { a ->
                            val dx = a[2] - a[0]; val dy = a[3] - a[1]; val len = kotlin.math.hypot(dx, dy); val ux = dx / len; val uy = dy / len
                            listOf(
                                stroke(a, ACCENT, LineStyle.SOLID, true),
                                stroke(floatArrayOf(a[2] - 8f * ux - 5f * uy, a[3] - 8f * uy + 5f * ux, a[2], a[3], a[2] - 8f * ux + 5f * uy, a[3] - 8f * uy - 5f * ux), ACCENT, LineStyle.SOLID, true),
                            )
                        }
                },
                Lesson(1, t("Naklejki", "Stickers"),
                    t("Naklejka to zapisany rysunek do wielokrotnego użycia: strzałka, symbol, schemat.", "A sticker is a saved drawing to reuse: an arrow, a symbol, a diagram."),
                    listOf(
                        t("Zaznacz lassem gwiazdkę poniżej, przytrzymaj zaznaczenie → „Zaznaczenie jako ▸” → „Zapisz jako naklejkę”.", "Lasso the star below, long-press the selection → “Selection as ▸” → “Save as sticker”."),
                        t("„⋮” → „Naklejki…” i dotknij jej — wstawi się na stronę.", "“⋮” → “Stickers…” and tap it — it is inserted on the page."),
                        t("Naklejka zachowuje swoje kolory, a tło ma przezroczyste — pasuje też na ciemną stronę.", "A sticker keeps its colours and has a transparent background — it fits dark pages too."),
                    ),
                    t("Naklejki są wspólne dla wszystkich zeszytów.", "Stickers are shared by all notebooks."),
                ) { y ->
                    val heart = curve(floatArrayOf(MARGIN + 200f, y + 40f, MARGIN + 225f, y + 18f, MARGIN + 248f, y + 40f, MARGIN + 200f, y + 104f, MARGIN + 152f, y + 40f, MARGIN + 175f, y + 18f), true, RED, LineStyle.SOLID)
                    val smile = curve(floatArrayOf(MARGIN + 312f, y + 66f, MARGIN + 340f, y + 82f, MARGIN + 368f, y + 66f), false, BLUE, LineStyle.SOLID)
                    listOf(star(MARGIN + 60f, y + 60f, 46f, 19f, GOLD), heart, ring(MARGIN + 340f, y + 60f, 44f, BLUE), dot(MARGIN + 324f, y + 46f, BLUE), dot(MARGIN + 356f, y + 46f, BLUE), smile)
                },
                Lesson(1, t("Wycinek strony i zdjęcia", "Page snips and photos"),
                    t("Fragment strony możesz zamienić w obraz, a zdjęcie kartki — oczyścić i wyprostować.", "You can turn part of a page into an image, and clean up and straighten a photo of a page."),
                    listOf(
                        t("Zaznacz lassem wzór w ramce, przytrzymaj → „Zaznaczenie jako ▸” → „Wycinek jako obraz” albo „do schowka”.", "Lasso the formula in the frame, long-press → “Selection as ▸” → “Snip as image” or “to clipboard”."),
                        t("Zdjęcie kartki: zaznacz je, „Efekty obrazu” → „Auto: białe tło”, potem jasność i temperatura.", "Photo of a page: select it, “Image effects” → “Auto: white background”, then brightness and temperature."),
                        t("Obraz przytniesz i wyprostujesz z menu zaznaczenia.", "Crop and straighten an image from the selection menu."),
                    ),
                    t("Jeden zaznaczony obraz może stać się tłem strony — zablokowanym, pod pismem.", "A single selected image can become the page background — locked, under the writing."),
                ) { y -> dashed(listOf(rect(MARGIN, y + 8f, MARGIN + 270f, y + 78f))) + listOf(text(MARGIN + 20f, y + 28f, "∫₀¹ x² dx = 1/3", 18f, INK)) },
                Lesson(1, t("Tryb wykładu", "Lecture mode"),
                    t("„⋮” → „Tryb wykładu…”: u dołu pojawia się kapsuła „Zdjęcie na nową stronę”. Zdjęcie trafia na nową stronę za bieżącą, z datą.", "“⋮” → “Lecture mode…”: a capsule “Photo on a new page” appears at the bottom. The photo goes on a new page after the current one, with the date."),
                    listOf(
                        t("Zrób zdjęcie tablicy albo slajdu i pisz notatki obok niego.", "Take a photo of the board or a slide and write notes next to it."),
                        t("Profil pracy „Wykład” przestawia dotyk na zajęcia: palec przewija, a dłoń oparta o ekran nie pisze.", "The “Lecture” work profile sets touch up for class: the finger scrolls and a palm resting on the screen does not write."),
                        t("„✕” na kapsule kończy tryb wykładu.", "“✕” on the capsule ends lecture mode."),
                    ),
                    if (lab) t("Zdjęcia z wykładu przechodzą przez kredę tak samo jak „Zdjęcie tablicy” z następnej strony.", "Lecture photos go through chalk cleaning just like the “Board photo” on the next page.")
                    else t("Zdjęcie robi systemowa aplikacja aparatu — mNotes nie potrzebuje do tego uprawnień.", "The system camera app takes the photo — mNotes needs no permission for it."),
                ) { y ->
                    listOf(
                        roundRect(MARGIN, y + 8f, MARGIN + 330f, y + 40f, TIP_BG, 16f),
                        text(MARGIN + 16f, y + 17f, t("Tryb wykładu  ·  Zdjęcie na nową stronę  ·  ✕", "Lecture mode  ·  Photo on a new page  ·  ✕"), 10f, TIP_INK, w = 300f),
                        stroke(rect(MARGIN, y + 58f, MARGIN + 130f, y + 208f).toFloatArray(), INK, LineStyle.SOLID, true).withStyle(width = 1f),
                        roundRect(MARGIN + 12f, y + 70f, MARGIN + 118f, y + 124f, BOARD),
                    ) + (0 until 4).map { stroke(floatArrayOf(MARGIN + 12f, y + 142f + it * 16f, MARGIN + 118f, y + 142f + it * 16f), HINT, LineStyle.SOLID, true).withStyle(width = 0.8f) }
                },
                Lesson(1, t("Zdjęcie tablicy", "Board photo"),
                    t("Ikona aparatu przy tablicy w nagłówku zeszytu robi zdjęcie i kładzie je na nowej stronie za bieżącą — bez trybu wykładu.", "The camera-by-the-board icon in the notebook header takes a photo and puts it on a new page after the current one — no lecture mode needed."),
                    listOfNotNull(
                        t("Dotknij ikony i sfotografuj tablicę; stań na wprost, żeby pismo nie wyszło skośne.", "Tap the icon and photograph the board; stand square to it so the writing does not come out slanted."),
                        if (lab) t("W tej wersji zdjęcie ciemnej tablicy od razu przechodzi przez kredę: zostaje samo pismo kredą. Jedno cofnięcie (↶) wraca do zdjęcia.", "In this version a dark board photo goes straight through chalk cleaning: only the chalk writing stays. One undo (↶) brings the photo back.")
                        else t("Zaznacz zdjęcie lassem: „Efekty obrazu” rozjaśnią tło, a menu zaznaczenia je przytnie i wyprostuje.", "Lasso the photo: “Image effects” brighten the background, and the selection menu crops and straightens it."),
                        if (lab) t("Kilka zdjęć tej samej tablicy zaznacz razem → „Połącz zdjęcia tablicy (bez osób)” — zostaje tablica bez zasłaniających ją ludzi.", "Select several photos of the same board → “Merge board photos (without people)” — the board stays, without the people in front of it.") else null,
                        t("Pisz po zdjęciu albo obok — pismo kładzie się na nim.", "Write on the photo or next to it — the writing goes on top."),
                    ),
                    if (lab) t("Kroki kredy (listwy, ramy, blada kreda) ustawisz w Ustawieniach → Rozszerzenia → Kreda.", "Chalk steps (ledges, frames, faint chalk) are in Settings → Extensions → Chalk.")
                    else t("To ten sam aparat co w trybie wykładu, tylko bez kapsuły — na jedno zdjęcie w przerwie.", "It is the same camera as in lecture mode, just without the capsule — for a single photo in a break."),
                ) { y ->
                    val parab = FloatArray(2 * 9) { k -> val i = k / 2; val x = -1f + i * 0.75f; if (k % 2 == 0) MARGIN + 150f + x * 34f else y + 128f - (x - 2f) * (x - 2f) * 8f }
                    listOf(
                        roundRect(MARGIN, y + 8f, MARGIN + 300f, y + 158f, BOARD),
                        text(MARGIN + 18f, y + 22f, "f(x) = x² − 4x + 3", 15f, CHALK),
                        curve(parab, false, CHALK, LineStyle.SOLID),
                    )
                },
                Lesson(1, t("Tryb ciemny stron", "Dark pages"),
                    t("„⋮” → Strony: „Jak powstały”, „Jasne” albo „Ciemne”. Kolory pisma zostają rozpoznawalne — czerwony dalej jest czerwony.", "“⋮” → Pages: “As made”, “Light” or “Dark”. Ink colours stay recognisable — red is still red."),
                    listOf(
                        t("Napisz coś czarnym, czerwonym i zakreślaczem, potem wybierz „Ciemne”.", "Write something in black, red and highlighter, then choose “Dark”."),
                        t("„Styl strony” → „Odwróć kolory tej strony (F11)” zmienia tylko jedną stronę (w trybie „Jak powstały”).", "“Page style” → “Invert this page's colours (F11)” changes just one page (in “As made” mode)."),
                        t("Zdjęcia zostają jak w aparacie; odwracanie ich włączysz w Ustawieniach → Wygląd.", "Photos stay as shot; inverting them can be turned on in Settings → Appearance."),
                    ),
                    t("Eksport jest w oryginale, chyba że w oknie eksportu włączysz „Kolory jak na ekranie”.", "Export stays original unless you turn on “Colours as on screen” in the export window."),
                ) { y -> darkDemo(MARGIN, y + 8f, pl) },
                Lesson(1, t("Eksport i udostępnianie", "Export and sharing"),
                    t("„⋮” → „Eksport i udostępnianie…” (Ctrl+E).", "“⋮” → “Export & share…” (Ctrl+E)."),
                    listOf(
                        t("PDF — do druku i wysłania; PNG i SVG — strony jako obrazy; Markdown — sam tekst; .mnotes — cały zeszyt do przeniesienia.", "PDF — to print and send; PNG and SVG — pages as images; Markdown — text only; .mnotes — the whole notebook to move."),
                        t("Zakres: cały zeszyt, bieżąca strona, przedział (np. 2-5, 8) albo tematy od zakładki do zakładki.", "Range: whole notebook, current page, a span (e.g. 2-5, 8) or topics from bookmark to bookmark."),
                        t("„Udostępnij…” wysyła plik prosto do innej aplikacji, „Drukuj…” — do drukarki.", "“Share…” sends the file straight to another app, “Print…” to a printer."),
                    ),
                    t("Puste strony pominiesz jednym przełącznikiem w tym samym oknie.", "Skip blank pages with one switch in the same window."),
                ) { y ->
                    listOf("PDF", "PNG", "SVG", "MD", ".mnotes").flatMapIndexed { i, f ->
                        val x = MARGIN + i * 96f
                        listOf(
                            stroke(floatArrayOf(x, y + 10f, x + 44f, y + 10f, x + 58f, y + 24f, x + 58f, y + 82f, x, y + 82f, x, y + 10f), if (i == 0) ACCENT else INK, LineStyle.SOLID, true).withStyle(width = 1.2f),
                            stroke(floatArrayOf(x + 44f, y + 10f, x + 44f, y + 24f, x + 58f, y + 24f), MUTED, LineStyle.SOLID, true).withStyle(width = 0.9f),
                            text(x, y + 92f, f, 10f, if (i == 0) ACCENT else INK, bold = true),
                        )
                    }
                },
                // 3c-A8, 3c-A9: notatki z tabletu na telefonie — folder wymiany i tryb do odczytu.
                Lesson(1, t("Telefon: folder wymiany i tryb do odczytu", "Phone: exchange folder and reading mode"),
                    t("Tablet zapisuje zeszyty do folderu, aplikacja synchronizująca przenosi folder, a telefon wczytuje nowsze wersje do czytania.", "The tablet writes notebooks to a folder, a sync app moves the folder, and the phone reads the newer versions."),
                    listOf(
                        t("Tablet: Ustawienia → „Folder synchronizacji” → wybierz folder i włącz kopię po wyjściu z aplikacji.", "Tablet: Settings → “Sync folder” → choose a folder and turn on copying when leaving the app."),
                        t("Telefon: Ustawienia → „Folder wymiany: wczytuj z innego urządzenia” → ten sam folder. Kopie trafią do folderu „Folder wymiany”.", "Phone: Settings → “Exchange folder: read from another device” → the same folder. Copies land in the “Exchange folder” folder."),
                        t("Na telefonie zeszyt otwiera się bez pasków: palec przewija, dwa palce powiększają. „Edytuj” w kapsule wraca do pasków.", "On a phone a notebook opens without bars: a finger scrolls, two fingers zoom. “Edit” in the capsule brings the bars back."),
                    ),
                    t("Dysk Google często nie daje się wybrać jako folder: wybierz folder w pamięci urządzenia i synchronizuj go osobną aplikacją.", "Google Drive often can't be picked as a folder: choose a folder on the device and sync it with a separate app."),
                ) { y ->
                    listOf(
                        stroke(floatArrayOf(MARGIN, y + 8f, MARGIN + 150f, y + 8f, MARGIN + 150f, y + 108f, MARGIN, y + 108f, MARGIN, y + 8f), INK, LineStyle.SOLID, true).withStyle(width = 1.4f),
                        text(MARGIN + 12f, y + 116f, t("tablet", "tablet"), 10f, INK, bold = true),
                        stroke(floatArrayOf(MARGIN + 170f, y + 58f, MARGIN + 260f, y + 58f), ACCENT, LineStyle.DASHED, true).withStyle(width = 1.4f),
                        stroke(floatArrayOf(MARGIN + 250f, y + 50f, MARGIN + 260f, y + 58f, MARGIN + 250f, y + 66f), ACCENT, LineStyle.SOLID, true).withStyle(width = 1.4f),
                        text(MARGIN + 172f, y + 36f, ".mnotes", 10f, ACCENT, bold = true),
                        stroke(floatArrayOf(MARGIN + 280f, y + 18f, MARGIN + 330f, y + 18f, MARGIN + 330f, y + 108f, MARGIN + 280f, y + 108f, MARGIN + 280f, y + 18f), INK, LineStyle.SOLID, true).withStyle(width = 1.4f),
                        text(MARGIN + 278f, y + 116f, t("telefon", "phone"), 10f, INK, bold = true),
                    )
                },

                // — Sprawdzian rozdziału II: zadania łączące funkcje z nauką, odpowiedzi pod taśmą (A2-11, 2c-A14) —
                Lesson(1, t("Sprawdź się: zaawansowane", "Try it yourself: advanced"),
                    t("Kilka zadań z matematyki — każde używa innej funkcji. Odpowiedź odsłoń dopiero po próbie.", "A few maths tasks — each uses a different feature. Uncover the answer only after trying."),
                    listOf(
                        t("Przerysuj szary trójkąt jednym pociągnięciem i przytrzymaj pióro, aż się wyprostuje. Zaznacz go lassem, przytrzymaj zaznaczenie → „Konstrukcja” → „Okrąg opisany”. Czy środek O leży w środku trójkąta?", "Trace the grey triangle in one stroke and hold the pen until it snaps. Select it with the lasso, long-press the selection → “Construction” → “Circumcircle”. Is the centre O inside the triangle?"),
                        t("Narzędziem Geometria → „Wysokości i ortocentrum” dotknij tego samego trójkąta. Gdzie przecinają się wysokości?", "With the Geometry tool → “Altitudes and orthocentre” tap the same triangle. Where do the altitudes meet?"),
                        t("Zaznacz lassem oba okręgi po prawej → „Konstrukcja” → „Przecięcie okręgów i prosta przez punkty”. Jak prosta PQ leży względem odcinka łączącego środki?", "Lasso both circles on the right → “Construction” → “Circle intersection and the line through it”. How does the line PQ lie against the segment joining the centres?"),
                        t("Narzędziem Tekst wpisz: \\alpha + \\beta = 90° oraz x^2 + y^2 = r^2. Co zobaczysz?", "With the Text tool type: \\alpha + \\beta = 90° and x^2 + y^2 = r^2. What do you see?"),
                        t("„⋮” → „Układ współrzędnych…” → „t ≥ 0”. Krzywą „przez wskazane punkty” naszkicuj prędkość v(t) samochodu, który rusza i przyspiesza. Skąd zaczyna się wykres?", "“⋮” → “Coordinate axes…” → “t ≥ 0”. With the curve “through tapped points” sketch the velocity v(t) of a car that starts and speeds up. Where does the graph start?"),
                    ),
                    t("Odpowiedzi odsłaniasz Taśmą (klawisz K): dotknięcie paska odkrywa, drugie zakrywa.", "Uncover the answers with the Tape (key K): one tap uncovers, another covers."),
                    answers = listOf(
                        t("Tak: trójkąt jest ostrokątny (kąty ok. 70°, 47° i 63°), a wtedy środek okręgu opisanego leży wewnątrz.", "Yes: the triangle is acute (angles about 70°, 47° and 63°), and then the circumcentre lies inside."),
                        t("Też wewnątrz — w trójkącie ostrokątnym ortocentrum leży w jego środku.", "Inside too — in an acute triangle the orthocentre lies within it."),
                        t("Jest do niego prostopadła; okręgi mają równe promienie, więc przechodzi przez jego środek.", "It is perpendicular to it; the circles have equal radii, so it passes through its midpoint."),
                        t("α + β = 90° oraz x² + y² = r²", "α + β = 90° and x² + y² = r²"),
                        t("Z zera: v(0) = 0, a potem rośnie — samochód stał, a później jedzie coraz szybciej.", "From zero: v(0) = 0, then it rises — the car stood still and then goes faster and faster."),
                    ),
                ) { y -> dashed(listOf(listOf(MARGIN + 20f, y + 150f, MARGIN + 200f, y + 150f, MARGIN + 70f, y + 10f, MARGIN + 20f, y + 150f))) + listOf(ring(MARGIN + 330f, y + 80f, 55f), ring(MARGIN + 400f, y + 80f, 55f)) },
            )
        }

        // — Układ stron —

        private fun cover(pl: Boolean, ls: List<Lesson>, chapters: List<String>, total: Int): Page {
            fun t(p: String, e: String) = if (pl) p else e
            val spec = PageSpec.of(PageFormat.A4, template = Template.GRID)
            val w = spec.w - 2 * MARGIN
            val els = ArrayList<Element>()
            val title = t("Przewodnik po mNotes", "mNotes guide")
            els.add(text(MARGIN, 64f, title, 30f, ACCENT, bold = true, w = w))
            val sub = t("Zeszyt do ćwiczeń: każda strona to jedno krótkie zadanie. Pisz po nim śmiało — cofasz dwoma palcami albo Ctrl+Z.", "A workbook: every page is one short task. Write on it freely — undo with two fingers or Ctrl+Z.")
            val sh = textHeight(sub, 12f, w)
            els.add(text(MARGIN, 112f, sub, 12f, MUTED, w = w, h = sh))
            val deco = FloatArray(2 * 7) { k -> val i = k / 2; if (k % 2 == 0) MARGIN + i * (w / 6f) else 112f + sh + 30f + 12f * sin(i * 1.3f) }
            els.add(curve(deco, false, ACCENT, LineStyle.SOLID).withStyle(width = 2f))
            var y = 112f + sh + 64f
            els.add(text(MARGIN, y, t("Spis treści", "Contents"), 14f, INK, bold = true)); y += 30f
            var ch = -1
            ls.forEachIndexed { i, l ->
                if (l.chapter != ch) { ch = l.chapter; y += 6f; els.add(text(MARGIN, y, chapters[ch], 10f, ACCENT, bold = true, w = w)); y += 20f }
                els.add(text(MARGIN + 14f, y, l.title, BODY, INK, w = w - 64f))
                els.add(text(MARGIN + w - 40f, y, "${i + 2}", BODY, MUTED, w = 40f, align = TextBox.ALIGN_END))
                y += 21f
            }
            footer(els, w, 1, total, pl)
            return Page(spec, els, title, y, y)
        }

        private fun lessonPage(l: Lesson, chapter: String, no: Int, total: Int, pl: Boolean): Page {
            val spec = PageSpec.of(PageFormat.A4, template = Template.GRID)
            val w = spec.w - 2 * MARGIN
            val els = ArrayList<Element>()
            val answers = ArrayList<TextBox>()
            els.add(text(MARGIN, 32f, chapter.uppercase(), 9f, ACCENT, bold = true, w = w))
            // Kreska i tekst pod tytułem idą za jego wysokością: przy szerszej czcionce tytuł zawija się na dwa wiersze.
            val titleH = textHeight(l.title, 22f, w, bold = true)
            els.add(text(MARGIN, 48f, l.title, 22f, INK, bold = true, w = w, h = titleH))
            val rule = 48f + titleH + 12f
            els.add(stroke(floatArrayOf(MARGIN, rule, MARGIN + 56f, rule), ACCENT, LineStyle.SOLID, false).withStyle(width = 2.4f))
            var y = rule + 14f
            val ih = textHeight(l.intro, BODY, w)
            els.add(text(MARGIN, y, l.intro, BODY, INK, w = w, h = ih)); y += ih + 10f
            l.steps.forEachIndexed { i, s ->
                // Numer w akcencie z wcięciem wiszącym: polecenia czyta się jak listę zadań.
                val sh = textHeight(s, BODY, w - 22f)
                els.add(text(MARGIN, y, "${i + 1}", BODY, ACCENT, bold = true, w = 16f))
                els.add(text(MARGIN + 22f, y, s, BODY, INK, w = w - 22f, h = sh))
                y += sh + 6f
                val a = l.answers.getOrNull(i) ?: return@forEachIndexed
                // Odpowiedź pod taśmą (nad nią w kolejności rysowania): uczeń odsłania ją narzędziem Taśma dopiero po próbie.
                val ah = textHeight(a, ANSWER, w - 22f)
                val box = text(MARGIN + 22f, y, a, ANSWER, TIP_INK, w = w - 22f, h = ah)
                els.add(box); answers.add(box)
                els.add(TapeEl(Ids.next(), box.cx, box.cy, box.w + 8f, ah + 6f, 0f, TAPE, hidden = true))
                y += ah + 10f
            }
            if (l.tip != null) {
                y += 6f
                val th = textHeight(l.tip, 10.5f, w - 28f)
                val label = if (pl) "WSKAZÓWKA" else "TIP"
                val lh = textHeight(label, 8.5f, w - 28f, bold = true)
                els.add(roundRect(MARGIN, y, MARGIN + w, y + 7f + lh + 4f + th + 9f, TIP_BG))
                els.add(text(MARGIN + 14f, y + 7f, label, 8.5f, ACCENT, bold = true))
                els.add(text(MARGIN + 14f, y + 7f + lh + 4f, l.tip, 10.5f, TIP_INK, w = w - 28f, h = th))
                y += 7f + lh + 4f + th + 9f
            }
            val artTop = y + 22f
            els.addAll(l.art(artTop))
            footer(els, w, no, total, pl)
            return Page(spec, els, l.title, y, artTop, l.chapter, answers)
        }

        private fun footer(els: MutableList<Element>, w: Float, no: Int, total: Int, pl: Boolean) {
            els.add(stroke(floatArrayOf(MARGIN, FOOT + 6f, MARGIN + w, FOOT + 6f), RULE, LineStyle.SOLID, false).withStyle(width = 0.6f))
            els.add(text(MARGIN, FOOT + 12f, if (pl) "mNotes · Przewodnik" else "mNotes · Guide", 8.5f, MUTED))
            els.add(text(MARGIN + w - 60f, FOOT + 12f, "$no / $total", 8.5f, MUTED, w = 60f, align = TextBox.ALIGN_END))
        }

        /** Wysokość akapitu [s] zawiniętego na szerokość [w] — z metryki czcionki, tej samej, którą pole tekstu się rysuje. */
        private fun textHeight(s: String, size: Float, w: Float, bold: Boolean = false): Float = m.textHeight(probe(s, size, bold), w)

        private fun width(s: String, size: Float, bold: Boolean): Float = m.naturalWidth(probe(s, size, bold))

        private fun probe(s: String, size: Float, bold: Boolean) = TextBox(0, 0f, 0f, 0f, 0f, 0f, s, size, 0, style = if (bold) TextBox.STYLE_BOLD else 0)

        // — Elementy —

        // Etykieta bez szerokości dostaje szerokość swojego tekstu (z zapasem na zaokrąglenia), więc się nie zawija.
        private fun text(x: Float, y: Float, s: String, size: Float, color: Int, bold: Boolean = false, w: Float = width(s, size, bold) + 4f, h: Float = textHeight(s, size, w, bold), align: Int = TextBox.ALIGN_START) =
            TextBox(Ids.next(), x + w / 2f, y + h / 2f, w, h, 0f, s, size, color, style = if (bold) TextBox.STYLE_BOLD else 0, align = align)

        private fun stroke(xy: FloatArray, color: Int, line: LineStyle, poly: Boolean): Stroke {
            val pts = FloatArray(xy.size / 2 * 3)
            for (i in 0 until xy.size / 2) { pts[3 * i] = xy[2 * i]; pts[3 * i + 1] = xy[2 * i + 1]; pts[3 * i + 2] = 1f }
            return Stroke(Ids.next(), PenKind.BALLPOINT, color, 1.6f, pts, poly = poly, line = line)
        }

        /** Gładka krzywa przez punkty (z uchwytami, jak z narzędzia Krzywa). */
        private fun curve(xy: FloatArray, closed: Boolean, color: Int, line: LineStyle): Stroke {
            val c = CurveFit.through(xy, closed)!!
            return Stroke(Ids.next(), PenKind.BALLPOINT, color, 1.6f, Bezier.sample(c), line = line, ctrl = c)
        }

        /** Kropka do stuknięcia: kreska z jednym punktem rysuje się jako koło. */
        private fun dot(x: Float, y: Float, color: Int = MUTED) = Stroke(Ids.next(), PenKind.BALLPOINT, color, 5f, floatArrayOf(x, y, 1f))

        /** Okrąg jako rozpoznany kształt (elipsa z ramką), żeby „Konstrukcja” i Geometria działały na nim od razu. */
        private fun ring(cx: Float, cy: Float, r: Float, color: Int = INK): Stroke {
            val xy = circle(cx, cy, r)
            val pts = FloatArray(xy.size / 2 * 3)
            for (i in 0 until xy.size / 2) { pts[3 * i] = xy[2 * i]; pts[3 * i + 1] = xy[2 * i + 1]; pts[3 * i + 2] = 1f }
            return Stroke(Ids.next(), PenKind.BALLPOINT, color, 1.6f, pts, ellipse = true)
        }

        private fun star(cx: Float, cy: Float, ro: Float, ri: Float, color: Int): Stroke {
            val xy = FloatArray(22) { k -> val i = (k / 2) % 10; val r = if (i % 2 == 0) ro else ri; val a = -PI / 2 + PI * i / 5; if (k % 2 == 0) cx + r * cos(a).toFloat() else cy + r * sin(a).toFloat() }
            return stroke(xy, color, LineStyle.SOLID, true)
        }

        /** Karta / tablica: zaokrąglony prostokąt jako wypełnienie (kolor jednolity). */
        private fun roundRect(l: Float, t: Float, r: Float, b: Float, color: Int, rad: Float = 6f): FillEl {
            val ring = ArrayList<Float>()
            val corners = listOf(Triple(r - rad, t + rad, -90.0), Triple(r - rad, b - rad, 0.0), Triple(l + rad, b - rad, 90.0), Triple(l + rad, t + rad, 180.0))
            for ((cx, cy, a0) in corners) for (k in 0..4) {
                val a = Math.toRadians(a0 + 22.5 * k)
                ring.add(cx + rad * cos(a).toFloat()); ring.add(cy + rad * sin(a).toFloat())
            }
            return FillEl(Ids.next(), color, false, listOf(ring.toFloatArray()))
        }

        private fun wave(x0: Float, y: Float, n: Int, color: Int = HINT): List<Element> {
            val xy = FloatArray(2 * n) { k -> val i = k / 2; if (k % 2 == 0) x0 + i * 6f else y + 14f * sin(i * 0.25f) }
            return listOf(stroke(xy, color, if (color == HINT) LineStyle.DASHED else LineStyle.SOLID, false))
        }

        private fun circle(cx: Float, cy: Float, r: Float): List<Float> = (0..48).flatMap { i -> val a = 2 * PI * i / 48; listOf(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat()) }
        private fun rect(l: Float, t: Float, r: Float, b: Float) = listOf(l, t, r, t, r, b, l, b, l, t)

        private fun dashed(shapes: List<List<Float>>) = shapes.map { stroke(it.toFloatArray(), HINT, LineStyle.DASHED, it.size <= 12) }
        private fun solid(shapes: List<List<Float>>) = shapes.map { stroke(it.toFloatArray(), INK, LineStyle.SOLID, it.size <= 12) }

        private fun axes(left: Float, top: Float): List<Element> {
            val d = Axes.build(AxesSpec(-4f, 4f, -2f, 2f, unit = 28f, labels = false), left, top)
            val out = ArrayList<Element>()
            d.grid.forEach { out.add(stroke(it, HINT, LineStyle.SOLID, true).withStyle(width = 0.4f)) }
            d.axes.forEach { out.add(stroke(it, INK, LineStyle.SOLID, true)) }
            // Szkic sinusa do przerysowania na osiach.
            val xs = FloatArray(2 * 60) { k -> val i = k / 2; val x = -4f + 8f * i / 59f; if (k % 2 == 0) left + (x + 4f) * 28f else top + (2f - 1.6f * sin(x * 1.2f)) * 28f }
            out.add(stroke(xs, HINT, LineStyle.DASHED, false))
            return out
        }

        /**
         * Ta sama notatka na jasnej i ciemnej stronie; kolory prawej liczy [ThemeMap] — dokładnie to, co pokaże aplikacja.
         * Zakreślacz po prawej jako gotowa barwa (jak „screen” na ciemnym papierze), bo ilustracja leży na jasnej kartce.
         */
        private fun darkDemo(x0: Float, y0: Float, pl: Boolean): List<Element> {
            val tm = ThemeMap.transform(Paper.WHITE, true)
            val out = ArrayList<Element>()
            val inks = listOf(0xFF1B1D21.toInt(), RED, BLUE)
            val hl = 0xFFFFE066.toInt()
            for (side in 0..1) {
                val l = x0 + side * 240f
                if (side == 0) out.add(stroke(rect(l, y0, l + 210f, y0 + 140f).toFloatArray(), RULE, LineStyle.SOLID, true).withStyle(width = 1f))
                else out.add(roundRect(l, y0, l + 210f, y0 + 140f, tm.dstPaper, 3f))
                val band = floatArrayOf(l + 14f, y0 + 18f, l + 120f, y0 + 18f, l + 120f, y0 + 34f, l + 14f, y0 + 34f)
                out.add(if (side == 0) FillEl(Ids.next(), hl, true, listOf(band)) else FillEl(Ids.next(), screen(tm.dstPaper, tm.highlighter(hl), 0.42f), false, listOf(band)))
                inks.forEachIndexed { i, c ->
                    val xy = FloatArray(2 * 30) { k -> val j = k / 2; if (k % 2 == 0) l + 16f + j * 6f else y0 + 28f + i * 34f + 6f * sin(j * 0.6f) }
                    out.add(stroke(xy, if (side == 0) c else tm.ink(c), LineStyle.SOLID, false))
                }
                out.add(text(l, y0 + 146f, if (side == 0) (if (pl) "Jak powstała" else "As made") else (if (pl) "Ciemne" else "Dark"), 9f, MUTED))
            }
            return out
        }

        /** Barwa zakreślacza „screen” o kryciu [a] na papierze [paper] (jak renderer na ciemnym papierze). */
        private fun screen(paper: Int, c: Int, a: Float): Int {
            fun ch(sh: Int): Int {
                val p = (paper shr sh and 0xFF) / 255f; val s = (c shr sh and 0xFF) / 255f
                return ((p * (1 - a) + (1 - (1 - p) * (1 - s)) * a) * 255f + 0.5f).toInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}
