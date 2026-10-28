package com.mdev.mnotes.core.samsung

import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.Paper
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.Template
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Konwerter notatki Samsung Notes na strony mNotes: kreski jako natywne, edytowalne obiekty, tło strony i raport
 * odstępstw. Port `narzedzia/samsung-notes-do-mnotes/scripts/convert.py` (tam opis kalibracji piór i tła).
 * Czysta logika bez Androida, więc w całości pokryta testami jednostkowymi.
 */
object SamsungConvert {
    const val UNIT_PT = 0.375              // 1600 jednostek Samsunga = 600 pt
    private const val MIN_STEP_PT = 0.3    // mNotes filtruje próbki co ok. 0,2 pt
    private const val DP_PRESSURE_STEP = 0.06
    private const val BRUSH_K = 1.2        // szerokość pędzla mNotes = rozmiar · UNIT_PT · BRUSH_K
    private const val BRUSH_A = 0.09
    private const val BRUSH_B = 1.17       // szerokość/rozmiar = A + B · nacisk (pędzel Samsunga)
    private const val PRESSURE_MIN = 0.04  // PenSpec.factor przycina do 0,04
    private const val INK_HALF_WINDOW = 2
    private const val TAPE_COLOR = 0xFFC8E9F2.toInt()

    /** Prędkość (jednostki Samsunga na ms) → współczynnik grubości InkPen2; zmierzone na kilkunastu kreskach. */
    private val INK_SPEED = arrayOf(
        doubleArrayOf(0.0, 1.0), doubleArrayOf(0.6, 1.0), doubleArrayOf(1.1, 0.88),
        doubleArrayOf(1.75, 0.66), doubleArrayOf(2.5, 0.60), doubleArrayOf(6.5, 0.55),
    )

    /**
     * Wzory tła 1–9 i 11 zmierzone na PDF-ie z eksportu Samsung Notes (strona 1600 × 2262 jednostek, 192 dpi, 1 px =
     * 1 jednostka). Plik zawiera tylko numer wzoru, więc geometria pochodzi z pomiaru: rozstaw i pierwsza linia w
     * jednostkach Samsunga (× [UNIT_PT] daje pt). Kropki mają inny rozstaw w poziomie niż w pionie, a pierwszy rząd
     * kropek leży niżej niż pierwsza linia.
     */
    private class Pattern(
        val kind: BgKind, val pitchX: Double, val pitchY: Double, val offX: Double, val offY: Double,
        val lineWidth: Double, val line: Int, val dot: Double = 0.0, val oxford: Boolean = false,
        /** Pięciolinie: liczba linii, liczba pięciolinii, rozstaw pięciolinii, lewy i prawy brzeg (jednostki). */
        val staff: SdStaff? = null,
    )
    private const val GRID_LINE = 0xFFC8C8C8.toInt()
    private const val GRID_LINE_WIDTH = 1.7          // jednostek; ok. 0,64 pt
    private const val DOT_DIAMETER = 8.5             // jednostek; ok. 3,2 pt
    private const val DOT_OFFSET_Y = 48.6
    private const val LINES_OFFSET_Y = 45.0
    private const val GRID_OFFSET_X = 0.45
    private val PATTERNS = mapOf(
        1 to Pattern(BgKind.LINES, 0.0, 72.05, 0.0, LINES_OFFSET_Y, GRID_LINE_WIDTH, GRID_LINE),
        2 to Pattern(BgKind.LINES, 0.0, 102.07, 0.0, LINES_OFFSET_Y, GRID_LINE_WIDTH, GRID_LINE),
        3 to Pattern(BgKind.LINES, 0.0, 168.13, 0.0, LINES_OFFSET_Y, GRID_LINE_WIDTH, GRID_LINE),
        4 to Pattern(BgKind.GRID, 72.66, 72.05, GRID_OFFSET_X, LINES_OFFSET_Y, GRID_LINE_WIDTH, GRID_LINE),
        5 to Pattern(BgKind.GRID, 102.29, 102.07, GRID_OFFSET_X, LINES_OFFSET_Y, GRID_LINE_WIDTH, GRID_LINE),
        6 to Pattern(BgKind.GRID, 169.0, 168.13, GRID_OFFSET_X, LINES_OFFSET_Y, GRID_LINE_WIDTH, GRID_LINE),
        7 to Pattern(BgKind.DOTS, 79.32, 72.05, 0.0, DOT_OFFSET_Y, GRID_LINE_WIDTH, GRID_LINE, DOT_DIAMETER),
        8 to Pattern(BgKind.DOTS, 108.96, 102.07, 0.0, DOT_OFFSET_Y, GRID_LINE_WIDTH, GRID_LINE, DOT_DIAMETER),
        9 to Pattern(BgKind.DOTS, 175.67, 168.13, 0.0, DOT_OFFSET_Y, GRID_LINE_WIDTH, GRID_LINE, DOT_DIAMETER),
        11 to Pattern(BgKind.LINES, 0.0, 66.04, 0.0, 44.74, 1.5, 0xFFD8D8D8.toInt(), oxford = true),
        // „Muzyka” z karty Kreatywne: 11 pięciolinii po 5 linii, zmierzone na PDF-ie z eksportu (plik nie ma żadnego obrazu tła)
        15 to Pattern(BgKind.STAFF, 0.0, 17.27, 0.0, 275.6, 2.2, 0xFF808080.toInt(), staff = SdStaff(5, 11, 174.3f, 100f, 1501f)),
    )

    /** Dwie czerwone linie marginesu papieru Oxford: środek, grubość (jednostki Samsunga) i kolor. */
    private val OXFORD_MARGINS = listOf(Triple(235.5, 3.4, 0xFFF09E9C.toInt()), Triple(249.0, 2.75, 0xFFF09E9C.toInt()))

    private val OBJECT_NAMES = mapOf(
        2 to ("pole tekstowe" to "text box"), 3 to ("obraz" to "image"), 7 to ("kształt" to "shape"),
        8 to ("linia" to "line"), 10 to ("nagranie głosu" to "voice recording"),
        11 to ("wzór matematyczny" to "formula"), 12 to ("tabela (stara)" to "table (old)"),
        13 to ("strona WWW" to "web page"), 14 to ("malowanie" to "painting"), 16 to ("wideo" to "video"),
        17 to ("łącze" to "link"), 18 to ("pociągnięcie pędzlem" to "brush stroke"), 20 to ("wykres" to "chart"),
        21 to ("matematyka" to "math"), 22 to ("tabela" to "table"), 23 to ("blok kodu" to "code block"),
        24 to ("załącznik" to "attachment"),
    )
    /** Szablon „studyjny”: tło to strona osadzonego PDF-a, nie wzór. */
    private const val PDF_TEMPLATE = 16

    private val TEMPLATE_NAMES = mapOf(
        10 to ("lista zadań" to "task list"),
        12 to ("własny obraz" to "custom image"), 13 to ("tygodniowy" to "weekly"),
        14 to ("miesięczny" to "monthly"), 16 to ("PDF" to "PDF"),
    )

    /** Zbiera pozycje raportu; powtarzające się ostrzeżenie (ten sam [key]) dopisuje tylko numer strony. */
    private class Report {
        class Entry(val level: Level, val pl: String, val en: String, val pages: MutableSet<Int> = sortedSetOf())

        val entries = ArrayList<Entry>()
        private val byKey = HashMap<String, Entry>()

        fun note(level: Level, pl: String, en: String, page: Int? = null) {
            entries.add(Entry(level, pl, en).also { e -> page?.let { e.pages.add(it) } })
        }

        fun once(key: String, level: Level, pl: String, en: String, page: Int?) {
            val e = byKey.getOrPut(key) { Entry(level, pl, en).also { entries.add(it) } }
            if (page != null) e.pages.add(page)
        }

        fun items(): List<ReportItem> = entries.map { ReportItem(it.level, it.pl, it.en, it.pages.toList()) }
    }

    // --- tytuł i strony ----------------------------------------------------------------------------

    /** Tytuł z nazwy pliku: bez rozszerzenia, ` (n)` i sufiksu `_YYMMDD_HHMMSS` dopisywanego przez Samsunga. */
    fun defaultTitle(fileName: String): String {
        var stem = fileName.substringAfterLast('/').substringBeforeLast('.', fileName.substringAfterLast('/'))
        stem = stem.replace(Regex("\\s*\\(\\d+\\)$"), "")
        stem = stem.replace(Regex("_\\d{6}_\\d{6}$"), "")
        return stem.trim().ifEmpty { "Notatka Samsung Notes" }
    }

    /** Strony bez żadnego obiektu na końcu odpadają (zostaje co najmniej jedna). */
    fun trimTrailingEmpty(pages: List<SdPage>, flow: SdFlow? = null): List<SdPage> {
        fun hasText(i: Int) = flow != null && flow.pageRange(i).any { !flow.text[it].isWhitespace() }
        var n = pages.size
        while (n > 1 && pages[n - 1].isEmpty && pages[n - 1].pdf == null && !hasText(n - 1)) n--
        return pages.subList(0, n)
    }

    // --- konwersja całej notatki -------------------------------------------------------------------

    fun convert(
        doc: SdDocument, fileName: String, opts: ConvertOptions, title: String? = null,
        measure: SdFlowMeasure = SdApproxMeasure,
    ): ConvertedNote {
        val rep = Report()
        val skipped = LinkedHashMap<Int, MutableMap<Int, Int>>() // typ obiektu → (strona → liczba)
        val kept = trimTrailingEmpty(doc.pages, doc.flow)
        val dropped = doc.pages.size - kept.size
        if (dropped > 0) {
            val pl = when {
                dropped == 1 -> "1 pustą stronę"
                dropped % 10 in 2..4 && dropped % 100 !in 12..14 -> "$dropped puste strony"
                else -> "$dropped pustych stron"
            }
            rep.note(
                Level.INFO,
                "Pominięto $pl na końcu pliku: Samsung Notes dopisuje pustą stronę po ostatniej stronie z treścią. " +
                    "Jeśli to była celowa pusta strona, dodaj ją w mNotes.",
                "Skipped $dropped empty page(s) at the end of the file: Samsung Notes appends an empty page after the last " +
                    "page with content. If it was intentional, add it in mNotes.",
            )
        }
        var strokeCount = 0
        var shapeCount = 0
        val mixedPages = sortedSetOf<Int>()
        val overflowPages = sortedSetOf<Int>()
        val ignoredKinds = sortedSetOf<Int>()
        val pages = kept.mapIndexed { i, pg ->
            val page = i + 1
            val pdfRef = pg.pdf?.takeIf { doc.pdfs.containsKey(it.media) }
            val (spec, bg) = buildPage(pg, opts, rep, page, pdfRef != null)
            val strokes = ArrayList<Stroke>()
            val objects = ArrayList<Element>()
            if (pg.error != null) {
                rep.note(
                    Level.WARN, "Strona nie do odczytania (${pg.error}): zostaje pusta kartka.",
                    "Page could not be read (${pg.error}): left as an empty sheet.", page,
                )
            }
            val obstacles = ArrayList<SdRect>()
            fun badObject(e: Exception) = rep.once(
                "bad-object", Level.WARN, "Obiekt nie do odczytania (${e.message}): pominięty.",
                "An object could not be read (${e.message}): skipped.", page,
            )
            for (o in pg.allObjects()) {
                when (o.type) {
                    1 -> try {
                        mapStroke(SdocxReader.parseStroke(o.payload, doc.strings), spec.paper, opts.dark, rep, page)?.let {
                            strokes.add(it); strokeCount++
                        }
                    } catch (e: SamsungFormatException) {
                        rep.once("bad-stroke", Level.WARN, "Kreska nie do odczytania (${e.message}): pominięta.", "A stroke could not be read (${e.message}): skipped.", page)
                    } catch (e: RuntimeException) {
                        rep.once("bad-stroke", Level.WARN, "Kreska nie do odczytania (${e.message}): pominięta.", "A stroke could not be read (${e.message}): skipped.", page)
                    }
                    3 -> try {
                        obstacles.add(unitsToPt(SdObjects.bounds(o.payload)))
                        val img = SdObjects.image(o.payload)
                        if (doc.media[img.mediaId] == null) {
                            rep.once("no-media", Level.WARN, "Obraz bez pliku w notatce: pominięty.", "An image without a file in the note: skipped.", page)
                        } else ImageConvert.element(img)?.let { objects.add(it) }
                    } catch (e: SamsungFormatException) { badObject(e) } catch (e: RuntimeException) { badObject(e) }
                    7 -> try {
                        obstacles.add(unitsToPt(SdObjects.bounds(o.payload)))
                        val shape = SdObjects.shape(o.payload)
                        val color = if (opts.dark) SamsungColor.invertArgb(shape.color) else shape.color
                        objects.addAll(SdShapes.strokes(shape, UNIT_PT, color))
                        shapeCount++
                    } catch (e: SamsungFormatException) { badObject(e) } catch (e: RuntimeException) { badObject(e) }
                    4, 100 -> Unit // kontenery: dzieci są już na liście
                    else -> skipped.getOrPut(o.type) { sortedMapOf() }.merge(page, 1, Int::plus)
                }
            }
            val elements = ArrayList<Element>()
            val flow = doc.flow
            if (flow != null) {
                val colorMap: (Int) -> Int = if (opts.dark) SamsungColor::invertArgb else { c -> c }
                val laid = SdFlowLayout.layout(flow, i, spec.w, spec.h, obstacles, measure, colorMap)
                elements.addAll(laid.elements)
                if (laid.mixedStyles) mixedPages.add(page)
                if (laid.overflow) overflowPages.add(page)
                ignoredKinds.addAll(laid.ignoredSpanKinds)
            }
            elements.addAll(objects)
            elements.addAll(strokes)
            ConvertedPage(spec, bg, elements, i, pdfRef)
        }
        flowNotes(doc, rep, mixedPages, overflowPages, ignoredKinds)
        if (shapeCount > 0) {
            rep.note(
                Level.INFO,
                "Kształty ($shapeCount) są przeniesione jako edytowalne kreski z uchwytami (elipsy i wielokąty); wypełnienie kształtu " +
                    "format nie udostępnia, więc kształt ma tylko obrys.",
                "Shapes ($shapeCount) are carried over as editable strokes with handles (ellipses and polygons); the file format " +
                    "does not expose shape fill, so a shape has only its outline.",
            )
        }
        for ((type, perPage) in skipped) {
            val (pl, en) = OBJECT_NAMES[type] ?: ("obiekt typu $type" to "object of type $type")
            val n = perPage.values.sum()
            rep.note(
                Level.WARN, "Pominięto: $pl, $n szt. (jeszcze nie przenoszone).",
                "Skipped: $en, $n item(s) (not carried over yet).", null,
            )
            rep.entries.last().pages.addAll(perPage.keys)
        }
        if (opts.dark) {
            rep.note(
                Level.INFO,
                "Wygląd ciemny, jak w trybie ciemnym aplikacji Samsung Notes: papier i pismo mają odwróconą jasność z zachowaniem " +
                    "barwy. Sam plik zawiera notatkę jasną, ciemność to wygląd aplikacji. Linie wzoru na ciemnym papierze są " +
                    "wyliczone tą samą regułą, ale nie sprawdzone na zrzucie.",
                "Dark appearance, like the Samsung Notes dark mode: paper and ink get their lightness inverted, hue kept. " +
                    "The file itself holds a light note; dark is an app appearance. Pattern lines on dark paper follow the same " +
                    "rule but were not checked against a screenshot.",
            )
        }
        return ConvertedNote(title ?: defaultTitle(fileName), pages, rep.items(), strokeCount, dropped, doc.media, doc.pdfs.filterKeys { k -> pages.any { it.pdf?.media == k } })
    }

    private fun unitsToPt(r: SdRect) = SdRect(r.x0 * UNIT_PT, r.y0 * UNIT_PT, r.x1 * UNIT_PT, r.y1 * UNIT_PT)

    /** Uwagi o tekście płynącym: tylko to, co faktycznie wystąpiło w notatce. */
    private fun flowNotes(doc: SdDocument, rep: Report, mixed: Set<Int>, overflow: Set<Int>, ignored: Set<Int>) {
        val flow = doc.flow ?: return
        if (flow.text.isNotBlank()) {
            rep.note(
                Level.INFO,
                "Tekst płynący (pisany klawiaturą w tle strony) jest przeniesiony jako pola tekstowe w tych samych miejscach, " +
                    "w rozmiarze i kolorze oryginału; pola są zwykłe, więc tekst da się edytować i przesuwać. Wygląd liter zależy od " +
                    "czcionki urządzenia (oryginał: Roboto), więc łamanie wierszy może się nieznacznie różnić.",
                "Flowing text (typed in the page background) is carried over as text boxes at the same places, with the original " +
                    "size and colour; the boxes are ordinary, so the text can be edited and moved. Glyphs depend on the device " +
                    "font (original: Roboto), so line breaks may differ slightly.",
            )
        }
        if (ignored.isNotEmpty()) {
            rep.note(
                Level.WARN,
                "Formatowanie tekstu inne niż rozmiar i kolor (np. pogrubienie, kursywa, podkreślenie, rodzaje ${ignored.joinToString()}) " +
                    "nie jest jeszcze przenoszone: tekst ma styl zwykły.",
                "Text formatting other than size and colour (e.g. bold, italic, underline, kinds ${ignored.joinToString()}) is not " +
                    "carried over yet: the text has plain style.",
            )
        }
        if (mixed.isNotEmpty()) {
            rep.note(
                Level.WARN,
                "W jednym akapicie jest tekst o różnym rozmiarze lub kolorze: pole przyjmuje styl pierwszego znaku akapitu.",
                "A paragraph mixes text sizes or colours: the box takes the style of the paragraph's first character.", null,
            )
            rep.entries.last().pages.addAll(mixed)
        }
        if (overflow.isNotEmpty()) {
            rep.note(
                Level.WARN,
                "Tekst wychodzi poza dół strony (inna czcionka urządzenia niż w oryginale): sprawdź i przesuń pola.",
                "Text runs past the bottom of the page (device font differs from the original): check and move the boxes.", null,
            )
            rep.entries.last().pages.addAll(overflow)
        }
        if (!flow.imagesComplete) {
            rep.note(
                Level.WARN, "Obrazy wstawione w tekst nie dały się odczytać: pominięte, tekst przeniesiony.",
                "Images inserted in the text could not be read: skipped, text carried over.",
            )
        }
    }

    // --- strona ------------------------------------------------------------------------------------

    /** Najmniejszy rozmiar ≥ [size], przy którym siatka mNotes (wyśrodkowana) zaczyna się w [offset]. */
    internal fun fitSize(size: Double, offset: Double, pitch: Double): Double {
        val n = max(1.0, ceil((size - 2 * offset) / pitch - 1e-9))
        return n * pitch + 2 * offset
    }

    private fun buildPage(pg: SdPage, opts: ConvertOptions, rep: Report, page: Int, pdfBackground: Boolean): Pair<PageSpec, SdBackground?> {
        val unitsW = if (pg.width > 0) pg.width else 1600
        val unitsH = if (pg.height > 0) pg.height else 2262
        var w = unitsW * UNIT_PT.toDouble()
        var h = unitsH * UNIT_PT.toDouble()
        var paper = pg.bgColor?.let { it or 0xFF000000.toInt() } ?: Paper.WHITE
        if (opts.dark) paper = SamsungColor.invertArgb(paper)
        val t = pg.templateType ?: 0
        var template = Template.BLANK
        var spacing = 0.0
        var line = Paper.autoLineColor(paper)
        var lineWidth = 0.6f
        var bg: SdBackground? = null
        val pattern = PATTERNS[t]
        if (pattern != null) {
            val u = UNIT_PT.toDouble()
            // wzór bez rozstawu poziomego (same linie) i tak ma jedną siatkę: rozstaw pionowy
            val pitchY = pattern.pitchY * u
            val pitchX = if (pattern.pitchX > 0) pattern.pitchX * u else pitchY
            val offX = pattern.offX * u
            val offY = pattern.offY * u
            val lineWidthPt = (pattern.lineWidth * u).toFloat()
            val lineColor = if (opts.dark) SamsungColor.invertArgb(pattern.line) else pattern.line
            if (opts.mode == ImportMode.ORIGINAL) {
                val margins = if (pattern.oxford) OXFORD_MARGINS.map { (x, wd, col) ->
                    SdMargin((x * u).toFloat(), (wd * u).toFloat(), if (opts.dark) SamsungColor.invertArgb(col) else col)
                } else emptyList()
                bg = SdBackground(
                    w.toFloat(), h.toFloat(), paper, pattern.kind, pitchX.toFloat(), pitchY.toFloat(), offX.toFloat(), offY.toFloat(),
                    lineColor, lineWidthPt, (pattern.dot * u).toFloat(), margins,
                    pattern.staff?.let { SdStaff(it.lines, it.groups, (it.groupPitch * u).toFloat(), (it.left * u).toFloat(), (it.right * u).toFloat()) },
                )
            } else {
                template = when (pattern.kind) {
                    BgKind.LINES -> Template.LINED; BgKind.GRID -> Template.GRID; BgKind.DOTS -> Template.DOTS; BgKind.STAFF -> Template.BLANK
                }
                if (pattern.kind == BgKind.STAFF) {
                    rep.once(
                        "staff", Level.INFO,
                        "mNotes nie ma pięciolinii jako wzoru: w trybie standardowym strona ma gładki papier. Tryb „wygląd oryginału” zachowuje pięciolinie.",
                        "mNotes has no staff paper pattern: in standard mode the page is plain. “Original look” keeps the staves.", page,
                    )
                }
                spacing = pitchY
                line = lineColor
                lineWidth = lineWidthPt
                if (pattern.oxford) {
                    rep.once(
                        "oxford", Level.INFO,
                        "Papier Oxford ma w trybie standardowym zwykłe linie bez czerwonego marginesu. Tryb „wygląd oryginału” go zachowuje.",
                        "Oxford paper uses plain lines without the red margin in standard mode. “Original look” keeps it.", page,
                    )
                }
                if (template != Template.LINED && template != Template.BLANK) {
                    w = fitSize(w, offX, pitchY)
                    h = fitSize(h, offY, pitchY)
                    rep.once(
                        "fit", Level.INFO,
                        "Strona ze wzorem ma powiększony rozmiar (${"%.1f".format(w)} × ${"%.1f".format(h)} pt), żeby siatka mNotes " +
                            "(wyśrodkowana) zgodziła się z siatką Notatek. Tryb „wygląd oryginału” zachowuje dokładny rozmiar.",
                        "Pages with a pattern are enlarged (${"%.1f".format(w)} × ${"%.1f".format(h)} pt) so the mNotes grid " +
                            "(centered) matches the Samsung Notes grid. “Original look” keeps the exact size.", page,
                    )
                }
            }
        } else if (t != 0 && !(pdfBackground && t == PDF_TEMPLATE)) {
            val (pl, en) = TEMPLATE_NAMES[t] ?: ("nieznany" to "unknown")
            rep.once(
                "tpl-$t", Level.WARN, "Wzór tła $t ($pl) nie jest jeszcze odtwarzany: strona ma gładki papier.",
                "Background pattern $t ($en) is not reproduced yet: the page has plain paper.", page,
            )
        }
        if (!pg.templateUri.isNullOrEmpty()) {
            rep.once(
                "uri", Level.WARN, "Strona ma własny obraz tła (${pg.templateUri}): pominięty, strona ma gładki papier.",
                "The page has a custom background image (${pg.templateUri}): skipped, plain paper used.", page,
            )
        }
        if (pg.hasPdf && !pdfBackground) {
            rep.once(
                "pdf", Level.WARN, "Strona ma wstawione tło PDF, ale pliku PDF nie ma w notatce: pominięte (gładki papier).",
                "The page has an inserted PDF background, but the PDF file is not in the note: skipped (plain paper).", page,
            )
        }
        if (pg.bgImage != null && pg.templateUri.isNullOrEmpty()) {
            rep.once("bgimg", Level.WARN, "Strona ma obraz tła: pominięty.", "The page has a background image: skipped.", page)
        }
        val pw = w.toFloat().coerceIn(PageSpec.MIN_SIZE, PageSpec.MAX_SIZE)
        val ph = h.toFloat().coerceIn(PageSpec.MIN_SIZE, PageSpec.MAX_SIZE)
        val spec = PageSpec(pw, ph, paper, template, line, spacing.toFloat(), lineWidth, opts = 0)
        return spec to bg?.copy(w = pw, h = ph)
    }

    // --- pióra -------------------------------------------------------------------------------------

    private enum class Kind { MARKER, BRUSH, INK, TAPE, OTHER }

    private fun penKind(st: SdStroke): Kind {
        val name = (st.pen ?: "").lowercase()
        return when {
            "marker" in name || "highlight" in name || st.props and 0x40L != 0L -> Kind.MARKER
            "brush" in name -> Kind.BRUSH
            "inkpen" in name -> Kind.INK
            "tape" in name -> Kind.TAPE
            else -> Kind.OTHER
        }
    }

    private fun inkSpeedFactor(v: Double): Double {
        if (v <= INK_SPEED[0][0]) return INK_SPEED[0][1]
        for (i in 0 until INK_SPEED.size - 1) {
            val (v0, f0) = INK_SPEED[i]
            val (v1, f1) = INK_SPEED[i + 1]
            if (v <= v1) return f0 + (f1 - f0) * (v - v0) / (v1 - v0)
        }
        return INK_SPEED.last()[1]
    }

    /** Współczynnik grubości InkPen2 w każdym punkcie, z prędkości (jednostki Samsunga na ms), lekko wygładzony. */
    internal fun inkFactors(st: SdStroke): DoubleArray {
        val n = st.count
        val raw = DoubleArray(n) { i ->
            val a = max(0, i - INK_HALF_WINDOW)
            val b = min(n - 1, i + INK_HALF_WINDOW)
            val dt = st.t[b] - st.t[a]
            val d = hypot(st.x[b] - st.x[a], st.y[b] - st.y[a])
            if (dt > 0) inkSpeedFactor(d / dt) else 1.0
        }
        return DoubleArray(n) { i ->
            var sum = 0.0
            var cnt = 0
            for (j in max(0, i - 1)..min(n - 1, i + 1)) { sum += raw[j]; cnt++ }
            (sum / cnt).coerceIn(PRESSURE_MIN.toDouble(), 1.0)
        }
    }

    /** Pociągnięcie Samsunga → kreska mNotes w pt albo null (kreska bez punktów). */
    private fun mapStroke(st: SdStroke, paper: Int, dark: Boolean, rep: Report, page: Int): Stroke? {
        if (st.count == 0) return null
        val kind = penKind(st)
        val size = (st.size?.takeIf { it != 0f } ?: 5f) * UNIT_PT
        val color = st.color ?: 0xFF000000.toInt()
        val n = st.count
        val xs = DoubleArray(n) { st.x[it] * UNIT_PT }
        val ys = DoubleArray(n) { st.y[it] * UNIT_PT }
        val ps = DoubleArray(n) { st.pressure[it].toDouble().coerceIn(0.0, 1.0) }
        val short = (st.pen ?: "?").substringAfterLast('.')
        val pen: PenKind
        val width: Double
        val mapped: Int
        when (kind) {
            Kind.MARKER -> {
                mapped = if (dark) SamsungColor.highlighterDark(SamsungColor.invertArgb(color), paper) else SamsungColor.highlighter(color, paper)
                pen = PenKind.HIGHLIGHTER; width = size.toDouble()
                ps.fill(1.0)
                rep.once(
                    "marker-$short", Level.INFO,
                    "Zakreślacz $short → zakreślacz mNotes (stała szerokość ${"%.1f".format(width)} pt, kolor przeliczony tak, " +
                        "by na papierze dawał ten sam odcień co w Notatkach; krycie mNotes jest stałe).",
                    "Highlighter $short → mNotes highlighter (fixed width ${"%.1f".format(width)} pt, colour recalculated so " +
                        "it gives the same shade on the paper as in Samsung Notes; mNotes opacity is fixed).", page,
                )
            }
            Kind.BRUSH -> {
                mapped = color; pen = PenKind.BRUSH; width = size * BRUSH_K.toDouble()
                for (i in 0 until n) ps[i] = ((BRUSH_A + BRUSH_B * ps[i]) / BRUSH_K).coerceIn(PRESSURE_MIN.toDouble(), 1.0)
                rep.once(
                    "brush", Level.INFO,
                    "Pędzel $short → pędzel mNotes; zmienna grubość odtworzona z nacisku (krzywa zmierzona na próbkach).",
                    "Brush $short → mNotes brush; variable width rebuilt from pressure (curve measured on samples).", page,
                )
            }
            Kind.INK -> {
                mapped = color; pen = PenKind.FOUNTAIN; width = size.toDouble()
                val f = inkFactors(st)
                for (i in 0 until n) ps[i] = f[i]
                rep.once(
                    "ink", Level.INFO,
                    "Pióro $short → pióro wieczne mNotes; grubość zależy od prędkości pisania (wolno pełny rozmiar, szybko do " +
                        "ok. 0,55 rozmiaru), tak jak w oryginale (krzywa zmierzona i przybliżona).",
                    "Pen $short → mNotes fountain pen; width depends on writing speed (slow = full size, fast = about 0.55) " +
                        "like the original (curve measured, approximate).", page,
                )
            }
            Kind.TAPE -> {
                mapped = TAPE_COLOR; pen = PenKind.BALLPOINT; width = size.toDouble()
                ps.fill(1.0)
                rep.once(
                    "tape", Level.WARN,
                    "Taśma $short → długopis mNotes o stałej, dużej szerokości w kolorze jasnoniebieskim (kolor taśmy zmierzony na jednej próbce).",
                    "Tape $short → mNotes ballpoint of fixed, large width in light blue (tape colour measured on a single sample).", page,
                )
            }
            Kind.OTHER -> {
                mapped = color; pen = PenKind.BALLPOINT; width = size.toDouble()
                ps.fill(1.0)
                rep.once(
                    "pen-$short", Level.WARN,
                    "Nieznane pióro $short → długopis mNotes o stałej szerokości (przybliżenie, brak kalibracji tego pióra).",
                    "Unknown pen $short → mNotes ballpoint of fixed width (approximation, this pen is not calibrated).", page,
                )
            }
        }
        val finalColor = if (dark && pen != PenKind.HIGHLIGHTER) SamsungColor.invertArgb(mapped) else mapped
        val pts = simplify(xs, ys, ps, pen == PenKind.BRUSH || pen == PenKind.FOUNTAIN)
        return Stroke(Ids.next(), pen, finalColor, width.toFloat(), pts)
    }

    /** Rzadsze próbki: gęste punkty z kwantyzacją 1/32 jednostki dałyby szum stycznych w konturze kreski. */
    internal fun simplify(x: DoubleArray, y: DoubleArray, p: DoubleArray, byPressure: Boolean): FloatArray {
        val n = x.size
        val keep = ArrayList<Int>(n)
        keep.add(0)
        if (n > 2) {
            for (i in 1 until n - 1) {
                val l = keep.last()
                if (hypot(x[i] - x[l], y[i] - y[l]) >= MIN_STEP_PT || (byPressure && abs(p[i] - p[l]) >= DP_PRESSURE_STEP)) keep.add(i)
            }
            val l = keep.last()
            if (hypot(x[n - 1] - x[l], y[n - 1] - y[l]) >= 1e-3) keep.add(n - 1)
        } else for (i in 1 until n) keep.add(i)
        val out = FloatArray(keep.size * 3)
        keep.forEachIndexed { k, i ->
            out[3 * k] = x[i].toFloat(); out[3 * k + 1] = y[i].toFloat(); out[3 * k + 2] = p[i].toFloat()
        }
        return out
    }
}
