package com.mdev.mnotes.core.samsung

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
 * odstępstw. Port `skills/samsung-notes-do-mnotes/scripts/convert.py` (tam opis kalibracji piór i tła).
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

    /** Prędkość (jednostki Samsunga na ms) → współczynnik grubości InkPen2; zmierzone na kilkunastu kreskach. */
    private val INK_SPEED = arrayOf(
        doubleArrayOf(0.0, 1.0), doubleArrayOf(0.6, 1.0), doubleArrayOf(1.1, 0.88),
        doubleArrayOf(1.75, 0.66), doubleArrayOf(2.5, 0.60), doubleArrayOf(6.5, 0.55),
    )

    /** Siatka „średnia” (wzór 5) zmierzona na stronie 1600 × 2262: rozstaw 102 jednostki, linie ok. #C8C8C8. */
    private const val GRID_PITCH_PT = 38.25
    private const val GRID_OFFSET_X_PT = 0.10
    private const val GRID_OFFSET_Y_PT = 16.71
    private const val GRID_LINE = 0xFFC8C8C8.toInt()
    private const val GRID_LINE_WIDTH_PT = 1.0f
    private val PITCH_FACTOR = doubleArrayOf(2.0 / 3, 1.0, 4.0 / 3, 2.0 / 3, 1.0, 4.0 / 3, 2.0 / 3, 1.0, 4.0 / 3)
    private val VERIFIED_TEMPLATES = setOf(5)
    private val TEMPLATE_WORDS = listOf(
        "wąskie linie" to "narrow lines", "średnie linie" to "medium lines", "szerokie linie" to "wide lines",
        "wąska kratka" to "narrow grid", "średnia kratka" to "medium grid", "szeroka kratka" to "wide grid",
        "wąskie kropki" to "narrow dots", "średnie kropki" to "medium dots", "szerokie kropki" to "wide dots",
    )

    private val OBJECT_NAMES = mapOf(
        2 to ("pole tekstowe" to "text box"), 3 to ("obraz" to "image"), 7 to ("kształt" to "shape"),
        8 to ("linia" to "line"), 10 to ("nagranie głosu" to "voice recording"),
        11 to ("wzór matematyczny" to "formula"), 12 to ("tabela (stara)" to "table (old)"),
        13 to ("strona WWW" to "web page"), 14 to ("malowanie" to "painting"), 16 to ("wideo" to "video"),
        17 to ("łącze" to "link"), 18 to ("pociągnięcie pędzlem" to "brush stroke"), 20 to ("wykres" to "chart"),
        21 to ("matematyka" to "math"), 22 to ("tabela" to "table"), 23 to ("blok kodu" to "code block"),
        24 to ("załącznik" to "attachment"),
    )
    private val TEMPLATE_NAMES = mapOf(
        10 to ("lista zadań" to "task list"), 11 to ("papier Oxford" to "Oxford paper"),
        12 to ("własny obraz" to "custom image"), 13 to ("tygodniowy" to "weekly"),
        14 to ("miesięczny" to "monthly"), 15 to ("rękopis" to "manuscript"), 16 to ("PDF" to "PDF"),
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
    fun trimTrailingEmpty(pages: List<SdPage>): List<SdPage> {
        var n = pages.size
        while (n > 1 && pages[n - 1].isEmpty) n--
        return pages.subList(0, n)
    }

    // --- konwersja całej notatki -------------------------------------------------------------------

    fun convert(doc: SdDocument, fileName: String, opts: ConvertOptions, title: String? = null): ConvertedNote {
        val rep = Report()
        val skipped = LinkedHashMap<Int, MutableMap<Int, Int>>() // typ obiektu → (strona → liczba)
        val kept = trimTrailingEmpty(doc.pages)
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
        val pages = kept.mapIndexed { i, pg ->
            val page = i + 1
            val (spec, bg) = buildPage(pg, opts, rep, page)
            val strokes = ArrayList<Stroke>()
            if (pg.error != null) {
                rep.note(
                    Level.WARN, "Strona nie do odczytania (${pg.error}): zostaje pusta kartka.",
                    "Page could not be read (${pg.error}): left as an empty sheet.", page,
                )
            }
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
                    4, 100 -> Unit // kontenery: dzieci są już na liście
                    else -> skipped.getOrPut(o.type) { sortedMapOf() }.merge(page, 1, Int::plus)
                }
            }
            ConvertedPage(spec, bg, strokes, i)
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
        return ConvertedNote(title ?: defaultTitle(fileName), pages, rep.items(), strokeCount, dropped)
    }

    // --- strona ------------------------------------------------------------------------------------

    /** Najmniejszy rozmiar ≥ [size], przy którym siatka mNotes (wyśrodkowana) zaczyna się w [offset]. */
    internal fun fitSize(size: Double, offset: Double, pitch: Double): Double {
        val n = max(1.0, ceil((size - 2 * offset) / pitch - 1e-9))
        return n * pitch + 2 * offset
    }

    private fun buildPage(pg: SdPage, opts: ConvertOptions, rep: Report, page: Int): Pair<PageSpec, SdBackground?> {
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
        val pattern = t in 1..9
        if (pattern) {
            val kind = BgKind.entries[(t - 1) / 3]
            val pitch = GRID_PITCH_PT * PITCH_FACTOR[t - 1]
            val k = pitch / GRID_PITCH_PT
            val offX = GRID_OFFSET_X_PT * k
            val offY = GRID_OFFSET_Y_PT * k
            val lineColor = if (opts.dark) SamsungColor.invertArgb(GRID_LINE) else GRID_LINE
            if (t !in VERIFIED_TEMPLATES) {
                val (pl, en) = TEMPLATE_WORDS[t - 1]
                rep.once(
                    "tpl-$t", Level.WARN,
                    "Wzór tła $t ($pl) nie był sprawdzony na próbce: rozstaw i położenie linii są przybliżone.",
                    "Background pattern $t ($en) was not checked against a sample: spacing and line position are approximate.", page,
                )
            }
            if (opts.mode == ImportMode.ORIGINAL) {
                bg = SdBackground(w.toFloat(), h.toFloat(), paper, kind, pitch.toFloat(), offX.toFloat(), offY.toFloat(), lineColor, GRID_LINE_WIDTH_PT)
            } else {
                template = when (kind) { BgKind.LINES -> Template.LINED; BgKind.GRID -> Template.GRID; BgKind.DOTS -> Template.DOTS }
                spacing = pitch
                line = lineColor
                lineWidth = GRID_LINE_WIDTH_PT
                if (template != Template.LINED) {
                    w = fitSize(w, offX, pitch)
                    h = fitSize(h, offY, pitch)
                    rep.once(
                        "fit", Level.INFO,
                        "Strona ze wzorem ma powiększony rozmiar (${"%.1f".format(w)} × ${"%.1f".format(h)} pt), żeby siatka mNotes " +
                            "(wyśrodkowana) zgodziła się z siatką Notatek. Tryb „wygląd oryginału” zachowuje dokładny rozmiar.",
                        "Pages with a pattern are enlarged (${"%.1f".format(w)} × ${"%.1f".format(h)} pt) so the mNotes grid " +
                            "(centered) matches the Samsung Notes grid. “Original look” keeps the exact size.", page,
                    )
                }
            }
        } else if (t != 0) {
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
        if (pg.hasPdf) {
            rep.once(
                "pdf", Level.WARN, "Strona ma wstawione tło PDF: pominięte (gładki papier).",
                "The page has an inserted PDF background: skipped (plain paper).", page,
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

    private enum class Kind { MARKER, BRUSH, INK, OTHER }

    private fun penKind(st: SdStroke): Kind {
        val name = (st.pen ?: "").lowercase()
        return when {
            "marker" in name || "highlight" in name || st.props and 0x40L != 0L -> Kind.MARKER
            "brush" in name -> Kind.BRUSH
            "inkpen" in name -> Kind.INK
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
