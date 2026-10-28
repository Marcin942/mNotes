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
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.pen.LineStyle
import com.mdev.mnotes.core.shapes.Bezier
import com.mdev.mnotes.core.shapes.CurveFit
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
    /** Strona; [textBottom] i [artTop] — koniec tekstu i początek ilustracji (test pilnuje, że się nie nakładają). */
    class Page(val spec: PageSpec, val elements: List<Element>, val title: String = "", val textBottom: Float = 0f, val artTop: Float = 0f)

    /** Lekcja: rozdział (0 podstawy, 1 zaawansowane, 2 sprawdzian), tytuł, wstęp, polecenia, wskazówka, ilustracja od y. */
    private class Lesson(val chapter: Int, val title: String, val intro: String, val steps: List<String>, val tip: String?, val art: (Float) -> List<Element>)

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
    private const val MARGIN = 48f
    private const val BODY = 11.5f

    /** Dół obszaru treści: niżej jest tylko stopka. */
    const val FOOT = 794f

    /** Strony przewodnika po polsku albo angielsku; [lab] — wariant z laboratorium (kreda na zdjęciach tablicy). */
    fun pages(pl: Boolean, lab: Boolean = false): List<Page> {
        val ls = lessons(pl, lab)
        val chapters = chapterNames(pl)
        val total = ls.size + 1
        val out = ArrayList<Page>(total)
        out.add(cover(pl, ls, chapters, total))
        ls.forEachIndexed { i, l -> out.add(lessonPage(l, chapters[l.chapter], i + 2, total, pl)) }
        return out
    }

    private fun chapterNames(pl: Boolean) =
        if (pl) listOf("Rozdział I · Podstawy", "Rozdział II · Zaawansowane", "Na koniec") else listOf("Chapter I · Basics", "Chapter II · Advanced", "Finally")

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
                    t("Zakładka (Ctrl+B) oznacza początek tematu; eksport może potem wziąć sam ten temat.", "A bookmark (Ctrl+B) marks the start of a topic; export can later take just that topic."),
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
                t("To samo jest w menu zaznaczenia (przytrzymaj → „Konstrukcja ▸”). Kątomierz z paska mierzy kąty ręcznie.", "The same is in the selection menu (long-press → “Construction ▸”). The protractor on the toolbar measures angles by hand."),
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
            Lesson(1, t("Tryb wykładu i zdjęcie tablicy", "Lecture mode and board photos"),
                t("„⋮” → „Tryb wykładu…”: u dołu pojawia się kapsuła „Zdjęcie na nową stronę”. Zdjęcie trafia na nową stronę za bieżącą, z datą.", "“⋮” → “Lecture mode…”: a capsule “Photo on a new page” appears at the bottom. The photo goes on a new page after the current one, with the date."),
                listOfNotNull(
                    t("Zrób zdjęcie tablicy albo slajdu i pisz notatki obok niego.", "Take a photo of the board or a slide and write notes next to it."),
                    if (lab) t("W tej wersji zdjęcie ciemnej tablicy od razu przechodzi przez kredę: zostaje samo pismo kredą. Jedno cofnięcie (↶) wraca do zdjęcia.", "In this version a dark board photo goes straight through chalk cleaning: only the chalk writing stays. One undo (↶) brings the photo back.") else null,
                    t("„✕” na kapsule kończy tryb wykładu.", "“✕” on the capsule ends lecture mode."),
                ),
                if (lab) t("Kroki kredy (listwy, ramy, blada kreda) ustawisz w Ustawieniach → Laboratorium → Kreda.", "Chalk steps (ledges, frames, faint chalk) are in Settings → Lab → Chalk.")
                else t("Zdjęcie robi systemowa aplikacja aparatu — mNotes nie potrzebuje do tego uprawnień.", "The system camera app takes the photo — mNotes needs no permission for it."),
            ) { y ->
                val parab = FloatArray(2 * 9) { k -> val i = k / 2; val x = -1f + i * 0.75f; if (k % 2 == 0) MARGIN + 150f + x * 34f else y + 128f - (x - 2f) * (x - 2f) * 8f }
                listOf(
                    roundRect(MARGIN, y + 8f, MARGIN + 300f, y + 158f, BOARD),
                    text(MARGIN + 18f, y + 22f, "f(x) = x² − 4x + 3", 15f, CHALK),
                    curve(parab, false, CHALK, LineStyle.SOLID),
                    stroke(rect(MARGIN + 340f, y + 8f, MARGIN + 470f, y + 158f).toFloatArray(), INK, LineStyle.SOLID, true).withStyle(width = 1f),
                    roundRect(MARGIN + 352f, y + 20f, MARGIN + 458f, y + 74f, BOARD),
                ) + (0 until 4).map { stroke(floatArrayOf(MARGIN + 352f, y + 92f + it * 16f, MARGIN + 458f, y + 92f + it * 16f), HINT, LineStyle.SOLID, true).withStyle(width = 0.8f) }
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

            // — Sprawdzian: zadania łączące funkcje z nauką; po nich widać, czy przewodnik zadziałał (A2-11) —
            Lesson(2, t("Sprawdź się", "Try it yourself"),
                t("Kilka zadań z matematyki — każde używa innej funkcji.", "A few maths tasks — each uses a different feature."),
                listOf(
                    t("Przerysuj szary trójkąt jednym pociągnięciem i przytrzymaj pióro, aż się wyprostuje. Zaznacz go lassem, przytrzymaj zaznaczenie → „Konstrukcja” → „Okrąg opisany”. Czy środek O leży w środku trójkąta?", "Trace the grey triangle in one stroke and hold the pen until it snaps. Select it with the lasso, long-press the selection → “Construction” → “Circumcircle”. Is the centre O inside the triangle?"),
                    t("Narzędziem Geometria → „Wysokości i ortocentrum” dotknij tego samego trójkąta.", "With the Geometry tool → “Altitudes and orthocentre” tap the same triangle."),
                    t("Zaznacz lassem oba okręgi po prawej → „Konstrukcja” → „Przecięcie okręgów”. Pojawią się punkty P i Q.", "Lasso both circles on the right → “Construction” → “Circle intersection”. Points P and Q appear."),
                    t("Narzędziem Tekst wpisz: \\alpha + \\beta = 90° oraz x^2 + y^2 = r^2 — znaki zamienią się same.", "With the Text tool type: \\alpha + \\beta = 90° and x^2 + y^2 = r^2 — the symbols convert as you type."),
                    t("„⋮” → „Układ współrzędnych…” → „t ≥ 0”. Krzywą „przez wskazane punkty” naszkicuj prędkość v(t) samochodu, który rusza i przyspiesza.", "“⋮” → “Coordinate axes…” → “t ≥ 0”. With the curve “through tapped points” sketch the velocity v(t) of a car that starts and speeds up."),
                ),
                null,
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
        els.add(text(MARGIN, 32f, chapter.uppercase(), 9f, ACCENT, bold = true, w = w))
        els.add(text(MARGIN, 48f, l.title, 22f, INK, bold = true, w = w))
        els.add(stroke(floatArrayOf(MARGIN, 86f, MARGIN + 56f, 86f), ACCENT, LineStyle.SOLID, false).withStyle(width = 2.4f))
        var y = 100f
        val ih = textHeight(l.intro, BODY, w)
        els.add(text(MARGIN, y, l.intro, BODY, INK, w = w, h = ih)); y += ih + 10f
        l.steps.forEachIndexed { i, s ->
            // Numer w akcencie z wcięciem wiszącym: polecenia czyta się jak listę zadań.
            val sh = textHeight(s, BODY, w - 22f)
            els.add(text(MARGIN, y, "${i + 1}", BODY, ACCENT, bold = true, w = 16f))
            els.add(text(MARGIN + 22f, y, s, BODY, INK, w = w - 22f, h = sh))
            y += sh + 6f
        }
        if (l.tip != null) {
            y += 6f
            val th = textHeight(l.tip, 10.5f, w - 28f)
            els.add(roundRect(MARGIN, y, MARGIN + w, y + th + 30f, TIP_BG))
            els.add(text(MARGIN + 14f, y + 7f, if (pl) "WSKAZÓWKA" else "TIP", 8.5f, ACCENT, bold = true))
            els.add(text(MARGIN + 14f, y + 21f, l.tip, 10.5f, TIP_INK, w = w - 28f, h = th))
            y += th + 30f
        }
        val artTop = y + 22f
        els.addAll(l.art(artTop))
        footer(els, w, no, total, pl)
        return Page(spec, els, l.title, y, artTop)
    }

    private fun footer(els: MutableList<Element>, w: Float, no: Int, total: Int, pl: Boolean) {
        els.add(stroke(floatArrayOf(MARGIN, FOOT + 6f, MARGIN + w, FOOT + 6f), RULE, LineStyle.SOLID, false).withStyle(width = 0.6f))
        els.add(text(MARGIN, FOOT + 12f, if (pl) "mNotes · Przewodnik" else "mNotes · Guide", 8.5f, MUTED))
        els.add(text(MARGIN + w - 60f, FOOT + 12f, "$no / $total", 8.5f, MUTED, w = 60f, align = TextBox.ALIGN_END))
    }

    /**
     * Wysokość akapitu z zapasem (wiersze z liczby znaków; średni znak liczony szeroko, 0,6 em, żeby akapit nie wchodził
     * na ilustrację przy szerszej czcionce); pole i tak przelicza układ przy edycji.
     */
    fun textHeight(s: String, size: Float, w: Float): Float {
        val perLine = (w / (size * 0.6f)).toInt().coerceAtLeast(8)
        val lines = (s.length + perLine - 1) / perLine
        return lines.coerceAtLeast(1) * size * 1.38f + 4f
    }

    // — Elementy —

    private fun text(x: Float, y: Float, s: String, size: Float, color: Int, bold: Boolean = false, w: Float = s.length * size * 0.6f + 8f, h: Float = size * 1.4f, align: Int = TextBox.ALIGN_START) =
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
