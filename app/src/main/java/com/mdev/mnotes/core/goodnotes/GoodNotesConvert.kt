package com.mdev.mnotes.core.goodnotes

import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.Paper
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.core.pen.LineStyle
import com.mdev.mnotes.core.samsung.BgKind
import com.mdev.mnotes.core.samsung.ConvertOptions
import com.mdev.mnotes.core.samsung.ConvertedNote
import com.mdev.mnotes.core.samsung.ConvertedPage
import com.mdev.mnotes.core.samsung.ImportMode
import com.mdev.mnotes.core.samsung.Level
import com.mdev.mnotes.core.samsung.SamsungColor
import com.mdev.mnotes.core.samsung.SamsungConvert
import com.mdev.mnotes.core.samsung.SamsungPdf
import com.mdev.mnotes.core.samsung.SdBackground
import com.mdev.mnotes.core.samsung.SdPdfPage
import com.mdev.mnotes.core.samsung.SdShapes
import java.io.InputStream
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Import eksportu PDF z GoodNotes (iOS) — eksperyment oparty na jednym pliku wzorcowym. GoodNotes zapisuje stronę
 * jako treść PDF-a z Quartz: papier to wypełnienie na całą stronę, wzór (kratka, linie, kropki) to cienkie
 * prostokąty, pismo to krzywe `S`, a tekst jest niewidocznym OCR-em. To wszystko zamieniamy na obiekty mNotes;
 * co się nie da, zostaje w tle PDF strony (razem z tłem), więc nic nie ginie po cichu.
 */
object GoodNotesConvert {
    private val MARKERS = listOf("Quartz PDFContext", "GoodNotes", "Goodnotes")
    private const val TOL = 0.15
    private const val MAX_RINGS = 2_000
    private const val MAX_PAGE_COORDS = 8_000_000L
    private val NORMAL = setOf("Normal", "Compatible")
    private val MULTIPLY = setOf("Multiply", "Darken")

    /** Tani test bez ładowania pliku: GoodNotes z iOS zostawia w PDF-ie producenta „Quartz PDFContext”. */
    fun looksCompatible(input: InputStream): Boolean {
        val pats = MARKERS.map { it.toByteArray(Charsets.US_ASCII) }
        val longest = pats.maxOf { it.size }
        val buf = ByteArray(64 * 1024 + longest)
        var keep = 0
        var total = 0L
        while (true) {
            val n = input.read(buf, keep, buf.size - keep)
            if (n < 0) return false
            total += n
            if (total > SamsungPdf.MAX_PDF) return false
            val end = keep + n
            for (p in pats) if (indexOf(buf, end, p) >= 0) return true
            keep = minOf(longest - 1, end)
            System.arraycopy(buf, end - keep, buf, 0, keep)
        }
    }

    private fun indexOf(b: ByteArray, end: Int, pat: ByteArray): Int {
        var i = 0
        while (i + pat.size <= end) {
            var j = 0
            while (j < pat.size && b[i + j] == pat[j]) j++
            if (j == pat.size) return i
            i++
        }
        return -1
    }

    /** Pełne rozpoznanie na wczytanym pliku: czy struktura stron wygląda na eksport z GoodNotes. Nie rzuca. */
    fun detect(bytes: ByteArray): Boolean = try { looksLikeGoodNotes(PdfDocument(bytes)) } catch (_: Exception) { false }

    fun defaultTitle(fileName: String): String {
        var stem = fileName.substringAfterLast('/').substringBeforeLast('.', fileName.substringAfterLast('/'))
        stem = stem.replace(Regex("\\s*\\(\\d+\\)$"), "")
        return stem.trim().ifEmpty { "Notatka GoodNotes" }
    }

    /** Czy to wygląda na eksport z GoodNotes: producent albo Quartz z papierem na całą stronę na większości pierwszych stron. */
    internal fun looksLikeGoodNotes(doc: PdfDocument): Boolean {
        val tag = (doc.info("Producer").orEmpty() + " " + doc.info("Creator").orEmpty()).lowercase()
        if ("goodnotes" in tag) return true
        if ("quartz pdfcontext" !in tag) return false
        val probe = doc.pages.take(5)
        if (probe.isEmpty()) return false
        var hits = 0
        for (pg in probe) {
            val ok = try {
                val items = PdfInterpreter(doc, pg.resources).run(doc.contents(pg))
                GoodNotesBackground.findPaper(items, PageMap(pg.cropBox, pg.rotate)) != null
            } catch (_: Exception) { false }
            if (ok) hits++
        }
        return hits * 2 >= probe.size
    }

    fun convert(bytes: ByteArray, fileName: String, opts: ConvertOptions): ConvertedNote {
        val doc = PdfDocument(bytes)
        if (doc.pages.isEmpty()) throw PdfException("PDF nie ma stron")
        val rep = Report()
        val media = Media()
        val pages = ArrayList<ConvertedPage>()
        val pdfSources = ArrayList<PdfWriter.Source>()
        var strokes = 0
        val genuine = looksLikeGoodNotes(doc)
        if (!genuine) {
            rep.note(
                Level.INFO,
                "Plik nie wygląda na eksport z GoodNotes: strony wstawione jako tło PDF, bez zamiany na obiekty.",
                "The file does not look like a GoodNotes export: pages inserted as a PDF background, not converted to objects.",
            )
        }
        for ((idx, pg) in doc.pages.withIndex()) {
            val page1 = idx + 1
            val pm = PageMap(pg.cropBox, pg.rotate)
            val w = clampSize(pm.w, PageSpec.MIN_SIZE, PageSpec.MAX_SIZE)
            val h = clampSize(pm.h, PageSpec.MIN_SIZE, PageSpec.MAX_SIZE)
            if (w.toDouble() != pm.w.coerceAtLeast(0.0) || h.toDouble() != pm.h.coerceAtLeast(0.0)) {
                if (abs(w - pm.w) > 0.01 || abs(h - pm.h) > 0.01) {
                    rep.once(
                        "clamp", Level.WARN,
                        "Strona ma rozmiar poza dozwolonym zakresem mNotes: rozmiar został przycięty.",
                        "The page size is outside the range mNotes allows: the size was clamped.", page1,
                    )
                }
            }
            val plain = {
                pdfSources.add(PdfWriter.Source(pg, null))
                pages.add(
                    ConvertedPage(
                        PageSpec(w, h, Paper.WHITE, Template.BLANK, Paper.autoLineColor(Paper.WHITE), 0f, 0.6f, opts = 0),
                        null, emptyList(), idx, SdPdfPage(0, pdfSources.size - 1),
                    ),
                )
            }
            if (!genuine) { plain(); continue }
            val items = try {
                PdfInterpreter(doc, pg.resources).run(doc.contents(pg))
            } catch (e: Exception) {
                rep.once(
                    "page-fail", Level.WARN,
                    "Strony nie udało się odczytać: wstawiona jako tło PDF.",
                    "The page could not be read: inserted as a PDF background.", page1,
                )
                plain(); continue
            }
            val conv = PageConv(doc, pg, pm, items, rep, media, page1)
            val bg = GoodNotesBackground.analyse(items, pm)
            val first = conv.run(bg, 0.0, 0.0)
            val needsPdf = first.leftover > 0 || !bg.clean
            if (needsPdf) {
                if (!bg.clean && first.leftover == 0) {
                    rep.once(
                        "bg-unclean", Level.WARN,
                        "Tło strony (wzór, którego nie umiem odtworzyć) zostało jako tło PDF; pismo na nim jest edytowalne.",
                        "The page background (a pattern that cannot be reproduced) stays as a PDF background; the ink on it is editable.", page1,
                    )
                }
                val content = if (first.converted.isEmpty()) null else blank(doc.contents(pg), first.converted)
                pdfSources.add(PdfWriter.Source(pg, content))
                strokes += first.strokes
                pages.add(
                    ConvertedPage(
                        PageSpec(w, h, bg.paper, Template.BLANK, Paper.autoLineColor(bg.paper), 0f, 0.6f, opts = 0),
                        null, first.elements, idx, SdPdfPage(0, pdfSources.size - 1),
                    ),
                )
                continue
            }
            val pat = bg.pattern
            val sp = pageSpec(pat, bg.paper, pm, w, h, opts, rep, page1)
            val res = if (sp.dx != 0.0 || sp.dy != 0.0) conv.run(bg, sp.dx, sp.dy) else first
            strokes += res.strokes
            pages.add(ConvertedPage(sp.spec, sp.background, res.elements, idx))
        }
        if (pdfSources.isNotEmpty()) {
            rep.once(
                "pdf-bg", Level.WARN,
                "Część stron zachowała tło PDF (elementy, których nie umiem zamienić na obiekty mNotes).",
                "Some pages kept a PDF background (elements that cannot be converted to mNotes objects).", null,
            )
        }
        val pdfs = if (pdfSources.isEmpty()) emptyMap() else mapOf(0 to PdfWriter.write(doc, pdfSources))
        val title = doc.info("Title")?.trim()?.takeIf { it.isNotEmpty() } ?: defaultTitle(fileName)
        return ConvertedNote(title, pages, rep.items(), strokes, 0, media.bytes, pdfs)
    }

    /** Zamazuje spacjami zakresy zamienionych elementów w strumieniu strony; reszta (q, cm, klipy, tło) zostaje. */
    private fun blank(content: ByteArray, done: List<ContentItem>): ByteArray {
        val out = content.copyOf()
        for (it in done) {
            val s = it.start.coerceIn(0, out.size)
            val e = it.end.coerceIn(s, out.size)
            java.util.Arrays.fill(out, s, e, ' '.code.toByte())
        }
        return out
    }

    // --- rozmiar i wzór strony ------------------------------------------------------------------

    private class PageOut(val spec: PageSpec, val background: SdBackground?, val dx: Double, val dy: Double)

    private fun alignAxis(extent: Double, first: Double, pitch: Double): DoubleArray {
        val r = ((first % pitch) + pitch) % pitch
        return if (r < pitch / 2) doubleArrayOf(0.0, SamsungConvert.fitSize(extent, r, pitch))
        else {
            val d = pitch - r
            doubleArrayOf(d, ceil((extent + d) / pitch - 1e-9) * pitch)
        }
    }

    private fun pageSpec(pat: BgPattern?, paper: Int, pm: PageMap, w: Float, h: Float, opts: ConvertOptions, rep: Report, page: Int): PageOut {
        val line = Paper.autoLineColor(paper)
        if (pat == null) return PageOut(PageSpec(w, h, paper, Template.BLANK, line, 0f, 0.6f, opts = 0), null, 0.0, 0.0)
        val equal = abs(pat.pitchX - pat.pitchY) <= 0.005 * pat.pitchY
        val bgOf = { pw: Float, ph: Float ->
            SdBackground(
                pw, ph, paper, pat.kind, pat.pitchX.toFloat(), pat.pitchY.toFloat(), pat.offX.toFloat(), pat.offY.toFloat(),
                pat.color, pat.thick.toFloat(), pat.dot.toFloat(),
            )
        }
        if (opts.mode == ImportMode.ORIGINAL) {
            return PageOut(PageSpec(w, h, paper, Template.BLANK, line, 0f, 0.6f, opts = 0), bgOf(w, h), 0.0, 0.0)
        }
        if (!equal) {
            rep.once(
                "uneven", Level.INFO,
                "Wzór tła ma różny rozstaw poziomy i pionowy, którego nie ma w mNotes: w trybie standardowym zachowano go jako tło.",
                "The background pattern has different horizontal and vertical spacing, which mNotes lacks: kept as a background in standard mode.", page,
            )
            return PageOut(PageSpec(w, h, paper, Template.BLANK, line, 0f, 0.6f, opts = 0), bgOf(w, h), 0.0, 0.0)
        }
        val sp = pat.pitchY
        if (pat.kind == BgKind.LINES) {
            val dy = ((-pat.offY % sp) + sp) % sp
            val ph = clampSize(pm.h + dy, PageSpec.MIN_SIZE, PageSpec.MAX_SIZE)
            return PageOut(PageSpec(w, ph, paper, Template.LINED, pat.color, sp.toFloat(), pat.thick.toFloat(), opts = 0), null, 0.0, dy)
        }
        val ax = alignAxis(pm.w, pat.offX, sp)
        val ay = alignAxis(pm.h, pat.offY, sp)
        // zaokrąglenie do 0,001 pt zbija szum liczb zmiennoprzecinkowych (464,00003 zamiast 464)
        val pw = clampSize(Math.round(ax[1] * 1000) / 1000.0, PageSpec.MIN_SIZE, PageSpec.MAX_SIZE)
        val ph = clampSize(Math.round(ay[1] * 1000) / 1000.0, PageSpec.MIN_SIZE, PageSpec.MAX_SIZE)
        val tpl = if (pat.kind == BgKind.DOTS) Template.DOTS else Template.GRID
        val width = if (pat.kind == BgKind.DOTS) pat.dot else pat.thick
        if (abs(pw - pm.w) > 0.01 || abs(ph - pm.h) > 0.01) {
            rep.once(
                "fit", Level.INFO,
                "Strona ze wzorem ma powiększony rozmiar (${"%.1f".format(pw)} × ${"%.1f".format(ph)} pt), żeby siatka mNotes " +
                    "(wyśrodkowana) zgodziła się z siatką GoodNotes. Tryb „wygląd oryginału” zachowuje dokładny rozmiar.",
                "Pages with a pattern are enlarged (${"%.1f".format(pw)} × ${"%.1f".format(ph)} pt) so the mNotes grid " +
                    "(centered) matches the GoodNotes grid. “Original look” keeps the exact size.", page,
            )
        }
        return PageOut(PageSpec(pw, ph, paper, tpl, pat.color, sp.toFloat(), width.toFloat(), opts = 0), null, ax[0], ay[0])
    }

    // --- obrazy -------------------------------------------------------------------------------

    private class Media {
        val bytes = LinkedHashMap<Int, ByteArray>()
        private val ids = IdentityHashMap<PdfStream, Int>()

        fun register(doc: PdfDocument, s: PdfStream): Int? {
            val known = ids[s]
            if (known != null) return known.takeIf { it >= 0 }
            val enc = PdfImages.extract(doc, s)
            if (enc == null) { ids[s] = -1; return null }
            val n = bytes.size
            bytes[n] = enc.bytes
            ids[s] = n
            return n
        }
    }

    // --- elementy strony ----------------------------------------------------------------------

    private class PassResult(val elements: List<Element>, val leftover: Int, val converted: List<ContentItem>, val strokes: Int)

    private enum class Verdict { OK, DROP, LEFT }

    private class PageConv(
        val doc: PdfDocument,
        val pg: PdfPage,
        val pm: PageMap,
        val items: List<ContentItem>,
        val rep: Report,
        val media: Media,
        val page: Int,
    ) {
        private var dx = 0.0
        private var dy = 0.0
        private var paper = 0

        fun run(bg: BgAnalysis, shiftX: Double, shiftY: Double): PassResult {
            dx = shiftX; dy = shiftY; paper = bg.paper
            val out = ArrayList<Element>()
            val done = ArrayList<ContentItem>()
            var left = 0
            var strokes = 0
            for (it in items) {
                if (it in bg.members) continue
                when (it) {
                    is PathItem -> {
                        if (it.clip) { if (it.fill || it.stroke) left += leave("clip-paint", "Rysunek zapisany razem z przycięciem", "A drawing combined with a clip"); continue }
                        if (!it.fill && !it.stroke) continue
                        val els = path(it)
                        if (els == null) { left++; continue }
                        out.addAll(els); done.add(it)
                        strokes += els.count { e -> e is Stroke }
                    }
                    is ImageItem -> {
                        val el = image(it)
                        if (el == null) { left++; continue }
                        if (el !== EMPTY) out.add(el)
                        done.add(it)
                    }
                    is TextItem -> {
                        if (!it.visible) {
                            rep.once(
                                "ocr", Level.INFO,
                                "Niewidoczny tekst (rozpoznawanie pisma w GoodNotes) pominięty: mNotes go nie przenosi.",
                                "Invisible text (GoodNotes handwriting recognition) skipped: mNotes does not carry it over.", page,
                            )
                        } else left += leave("text", "Widoczny tekst", "Visible text")
                    }
                    is FormItem -> left += leave("form", "Obiekt złożony (formularz PDF)", "A composite object (PDF form)")
                    is OtherItem -> left += leave("other-${it.what}", "Element PDF (${it.what})", "A PDF element (${it.what})")
                }
            }
            return PassResult(out, left, done, strokes)
        }

        private fun leave(key: String, pl: String, en: String): Int {
            rep.once(
                "left-$key", Level.WARN,
                "$pl został w tle PDF strony: nie da się go zamienić na obiekt mNotes.",
                "$en stays in the page's PDF background: it cannot be converted to an mNotes object.", page,
            )
            return 1
        }

        /** Współrzędne z obu przebiegów strony; ponad [MAX_PAGE_COORDS] to wrogi albo zepsuty plik (krzywe po 2¹⁶ punktów). */
        private var coords = 0L

        private fun spend(f: DoubleArray): DoubleArray {
            coords += f.size
            if (coords > MAX_PAGE_COORDS) throw PdfException("strona ma za dużo punktów")
            return f
        }

        private fun why(key: String, pl: String, en: String): Nothing? {
            leave(key, pl, en)
            return null
        }

        private fun mx(x: Double, y: Double) = (pm.x(x, y) + dx).toFloat()
        private fun my(x: Double, y: Double) = (pm.y(x, y) + dy).toFloat()

        private fun clipVerdict(st: GState, b: DoubleArray): Verdict {
            if (!st.clipped) return Verdict.OK
            val cb = st.clipBox ?: return Verdict.OK
            if (!st.clipExact) return Verdict.LEFT
            val e = 0.5
            if (b[0] >= cb[0] - e && b[1] >= cb[1] - e && b[2] <= cb[2] + e && b[3] <= cb[3] + e) return Verdict.OK
            if (b[2] < cb[0] || b[0] > cb[2] || b[3] < cb[1] || b[1] > cb[3]) return Verdict.DROP
            val c = pg.cropBox
            return if (cb[0] <= c[0] + e && cb[1] <= c[1] + e && cb[2] >= c[2] - e && cb[3] >= c[3] - e) Verdict.OK else Verdict.LEFT
        }

        private fun blendOk(st: GState): Boolean = st.blend in NORMAL || st.blend in MULTIPLY

        /** Wszystkie elementy z jednego rysunku albo null (rysunek zostaje w tle PDF). Pusta lista = nic widocznego. */
        private fun path(p: PathItem): List<Element>? {
            val st = p.state
            if (st.softMask) return why("smask", "Rysunek z maską przezroczystości", "A drawing with a transparency mask")
            if (!blendOk(st)) return why("blend", "Rysunek z trybem mieszania ${st.blend}", "A drawing with blend mode ${st.blend}")
            val scale = st.ctm.scale
            val pad = if (p.stroke) st.lineWidth * scale / 2 else 0.0
            val raw = PathMath.bbox(p.subs)
            val bb = doubleArrayOf(raw[0] - pad, raw[1] - pad, raw[2] + pad, raw[3] + pad)
            when (clipVerdict(st, bb)) {
                Verdict.DROP -> return emptyList()
                Verdict.LEFT -> return why("clip", "Rysunek przycięty niestandardowym obrysem", "A drawing cut by a custom clip")
                Verdict.OK -> {}
            }
            val out = ArrayList<Element>()
            if (p.fill) {
                if (st.fillCs.kind == CsKind.PATTERN) return why("pattern", "Wypełnienie wzorem", "A pattern fill")
                val c = st.fillArgb ?: return why("color", "Rysunek w nieobsługiwanej przestrzeni barw", "A drawing in an unsupported colour space")
                val el = fill(p, c, st.fillAlpha, st.blend in MULTIPLY) ?: return null
                if (el !== EMPTY) out.add(el)
            }
            if (p.stroke) {
                if (st.strokeCs.kind == CsKind.PATTERN) return why("pattern", "Obrys wzorem", "A pattern stroke")
                val c = st.strokeArgb ?: return why("color", "Rysunek w nieobsługiwanej przestrzeni barw", "A drawing in an unsupported colour space")
                out.addAll(stroke(p, c, st.strokeAlpha, st.blend in MULTIPLY))
            }
            return out
        }

        private fun highlighter(c: Int, alpha: Double): Int {
            val a = (alpha * 255).roundToInt().coerceIn(0, 255)
            return SamsungColor.highlighter(SamsungColor.pack(a, SamsungColor.r(c), SamsungColor.g(c), SamsungColor.b(c)), paper)
        }

        private fun fill(p: PathItem, c: Int, alpha: Double, multiply: Boolean): Element? {
            val hl = alpha < 0.99 || multiply
            val rings = ArrayList<FloatArray>()
            for (sp in p.subs) {
                val f = spend(PathMath.flatten(sp, TOL))
                var n = f.size / 2
                if (n >= 2 && f[0] == f[2 * n - 2] && f[1] == f[2 * n - 1]) n--
                if (n < 3) continue
                rings.add(FloatArray(2 * n) { i -> if (i % 2 == 0) mx(f[i], f[i + 1]) else my(f[i - 1], f[i]) })
            }
            if (rings.isEmpty()) return EMPTY
            // `nested` porównuje każdy obrys z każdym: tysiące obrysów z wrogiego PDF to minuty pracy
            if (rings.size > MAX_RINGS) return why("rings", "Wypełnienie z bardzo wieloma obrysami", "A fill with very many outlines")
            if (!p.evenOdd && rings.size > 1 && nested(rings)) {
                return why("nested", "Wypełnienie z zagnieżdżonymi obrysami", "A fill with nested outlines")
            }
            val color = if (hl) highlighter(c, alpha) else c or 0xFF000000.toInt()
            return FillEl(Ids.next(), color, hl, rings)
        }

        private fun nested(rings: List<FloatArray>): Boolean {
            for (i in rings.indices) for (j in rings.indices) {
                if (i != j && inside(rings[j][0], rings[j][1], rings[i])) return true
            }
            return false
        }

        private fun inside(x: Float, y: Float, ring: FloatArray): Boolean {
            var c = false
            val n = ring.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = ring[2 * i]; val yi = ring[2 * i + 1]; val xj = ring[2 * j]; val yj = ring[2 * j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
                j = i
            }
            return c
        }

        private fun stroke(p: PathItem, c: Int, alpha: Double, multiply: Boolean): List<Element> {
            val st = p.state
            val scale = st.ctm.scale
            val width = max(0.25, st.lineWidth * scale).toFloat()
            val hl = alpha < 0.99 || multiply
            val color = if (hl) highlighter(c, alpha) else c or 0xFF000000.toInt()
            val pen = if (hl) PenKind.HIGHLIGHTER else PenKind.BALLPOINT
            val dashed = st.dash.isNotEmpty() && st.dash.any { it > 0 }
            val line = when {
                !dashed -> LineStyle.SOLID
                st.dash[0] * scale <= 1.2 * width -> LineStyle.DOTTED
                else -> LineStyle.DASHED
            }
            val out = ArrayList<Element>()
            for (sp in p.subs) {
                val f = spend(PathMath.flatten(sp, TOL))
                var n = f.size / 2
                if (n == 0) continue
                val xs = ArrayList<Float>()
                val ys = ArrayList<Float>()
                if (n == 1) {
                    if (st.cap == 0) continue
                    xs.add(mx(f[0], f[1])); ys.add(my(f[0], f[1]))
                    xs.add(xs[0] + 0.01f); ys.add(ys[0])
                } else {
                    for (i in 0 until n) { xs.add(mx(f[2 * i], f[2 * i + 1])); ys.add(my(f[2 * i], f[2 * i + 1])) }
                    if (sp.closed && (xs.first() != xs.last() || ys.first() != ys.last())) { xs.add(xs.first()); ys.add(ys.first()) }
                }
                n = xs.size
                val draw = sp.segs.filter { it.kind == SegKind.LINE || it.kind == SegKind.CURVE }
                if (sp.closed && sp.hasCurves && !hl && !dashed && draw.isNotEmpty() && draw.all { it.kind == SegKind.CURVE } && draw.size in 4..8) {
                    val e = SdShapes.ellipseStroke(List(n) { doubleArrayOf(xs[it].toDouble(), ys[it].toDouble()) }, width, color)
                    if (e != null) { out.add(e); continue }
                }
                val pts = FloatArray(3 * n) { i -> when (i % 3) { 0 -> xs[i / 3]; 1 -> ys[i / 3]; else -> 1f } }
                val poly = !sp.hasCurves && sp.drawSegments <= 12
                out.add(Stroke(Ids.next(), pen, color, width, pts, poly = poly, line = line))
            }
            return out
        }

        private fun image(im: ImageItem): Element? {
            val st = im.state
            if (st.softMask) return why("smask", "Obraz z maską przezroczystości", "An image with a transparency mask")
            if (st.blend !in NORMAL) return why("blend", "Obraz z trybem mieszania ${st.blend}", "An image with blend mode ${st.blend}")
            val m = st.ctm
            val px = doubleArrayOf(m.x(0.0, 1.0), m.x(1.0, 1.0), m.x(0.0, 0.0), m.x(1.0, 0.0))
            val py = doubleArrayOf(m.y(0.0, 1.0), m.y(1.0, 1.0), m.y(0.0, 0.0), m.y(1.0, 0.0))
            val bb = doubleArrayOf(px.min(), py.min(), px.max(), py.max())
            when (clipVerdict(st, bb)) {
                Verdict.DROP -> return EMPTY
                Verdict.LEFT -> return why("clip", "Obraz przycięty niestandardowym obrysem", "An image cut by a custom clip")
                Verdict.OK -> {}
            }
            val tlx = pm.x(px[0], py[0]) + dx; val tly = pm.y(px[0], py[0]) + dy
            val ux = pm.x(px[1], py[1]) + dx - tlx; val uy = pm.y(px[1], py[1]) + dy - tly
            val vx = pm.x(px[2], py[2]) + dx - tlx; val vy = pm.y(px[2], py[2]) + dy - tly
            val w = hypot(ux, uy)
            val h = hypot(vx, vy)
            if (w < 0.5 || h < 0.5) return EMPTY
            if (abs(ux * vx + uy * vy) > 0.02 * w * h) return why("shear", "Pochylony obraz", "A skewed image")
            val n = media.register(doc, im.stream) ?: return why("image", "Obraz w formacie, którego nie odczytam", "An image in a format that cannot be read")
            val cross = ux * vy - uy * vx
            val rot = if (cross > 0) atan2(uy, ux) else atan2(-uy, -ux)
            return ImageEl(
                Ids.next(), "sd:$n", (tlx + (ux + vx) / 2).toFloat(), (tly + (uy + vy) / 2).toFloat(),
                w.toFloat(), h.toFloat(), rot.toFloat(), st.fillAlpha.toFloat(), mirror = cross <= 0,
            )
        }
    }

    /** Znacznik „zamienione, ale nic nie powstało” (przycięte poza stroną, zbyt małe). */
    private val EMPTY: Element = FillEl(0, 0, false, emptyList())
}
