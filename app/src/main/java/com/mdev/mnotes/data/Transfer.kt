package com.mdev.mnotes.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.util.Base64
import com.mdev.mnotes.core.model.BoxElement
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Paper
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.PdfRef
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.core.model.TapeEl
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.geom.FloatList
import com.mdev.mnotes.core.pen.LineStyle
import com.mdev.mnotes.core.pen.PenSpec
import com.mdev.mnotes.core.pen.StrokeDash
import com.mdev.mnotes.core.pen.StrokeOutline
import com.mdev.mnotes.editor.T
import com.mdev.mnotes.render.Assets
import com.mdev.mnotes.render.CropBake
import com.mdev.mnotes.render.PageRenderer
import com.mdev.mnotes.render.PdfPool
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ExportFormat(val ext: String, val mime: String, val multi: Boolean) {
    PDF("pdf", "application/pdf", true),
    PNG("png", "image/png", false),
    SVG("svg", "image/svg+xml", false),
    MD("md", "text/markdown", true),
    MNOTES("mnotes", "application/x-mnotes", true),
}

/** Eksport zeszytu do otwartych formatów. Wywoływać poza wątkiem UI (po `doc.flush()` i barierze zapisu repo.io). */
class Exporter(private val repo: Repository, private val assets: Assets, private val pdf: PdfPool) {
    private val renderer = PageRenderer(assets)

    /** Czeka, aż wszystkie zaplanowane zapisy stron trafią na dysk. */
    fun awaitWrites() { runCatching { repo.io.submit {}.get() } }

    private fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim()

    /** [span] = opis przedziału stron lub sekcji (np. „2-5”) dopisywany do nazwy; ma pierwszeństwo przed [pageNo]. */
    fun fileName(title: String, fmt: ExportFormat, pageNo: Int? = null, zip: Boolean = false, span: String? = null): String {
        val base = safeName(title).ifEmpty { "mNotes" }.take(80)
        val suffix = when {
            span != null -> "-" + safeName(span).take(60)
            pageNo != null && !fmt.multi -> "-${pageNo + 1}"
            else -> ""
        }
        return base + suffix + "." + (if (zip) "zip" else fmt.ext)
    }

    /** Indeksy stron bez żadnej treści (ani elementów, ani tła PDF) — papier w kratkę bez notatek też jest pusty. */
    fun blankPages(nb: String): Set<Int> {
        val meta = repo.meta(nb) ?: return emptySet()
        return meta.pages.indices.filterTo(HashSet()) { i ->
            val p = meta.pages[i]
            p.spec.pdf == null && repo.readPage(nb, p.id).elements.isEmpty()
        }
    }

    /**
     * Archiwum ZIP z osobnym plikiem dla każdej części ([parts] = tytuł i indeksy stron): `01 Tytuł.pdf`. Dla PNG i SVG
     * część z jedną stroną to jeden plik, z kilkoma — folder `01 Tytuł/page-004.png`. Zwraca false przy błędzie.
     */
    fun writeParts(nb: String, fmt: ExportFormat, parts: List<Pair<String, List<Int>>>, out: OutputStream): Boolean {
        val meta = repo.meta(nb) ?: return false
        if (fmt == ExportFormat.MNOTES || parts.isEmpty()) return false
        return try {
            ZipOutputStream(out).use { z ->
                parts.forEachIndexed { n, (title, idx) ->
                    val base = "%02d %s".format(Locale.ROOT, n + 1, safeName(title).ifEmpty { "-" }.take(60))
                    when (fmt) {
                        ExportFormat.PDF -> { z.putNextEntry(ZipEntry("$base.pdf")); pdfOut(nb, meta, idx, z); z.closeEntry() }
                        ExportFormat.MD -> { z.putNextEntry(ZipEntry("$base.md")); z.write(markdown(nb, meta, idx).toByteArray()); z.closeEntry() }
                        else -> imagesInto(z, nb, meta, idx, svg = fmt == ExportFormat.SVG, single = "$base.${fmt.ext}", folder = base)
                    }
                }
            }
            true
        } catch (_: Throwable) { false }
    }

    /** [pages] = indeksy stron do wyeksportowania (null = wszystkie). Zwraca false przy błędzie. */
    fun write(nb: String, fmt: ExportFormat, pages: List<Int>?, out: OutputStream): Boolean {
        val meta = repo.meta(nb) ?: return false
        val idx = pages ?: meta.pages.indices.toList()
        return try {
            when (fmt) {
                ExportFormat.PDF -> pdfOut(nb, meta, idx, out)
                ExportFormat.PNG -> imagesOut(nb, meta, idx, out, svg = false)
                ExportFormat.SVG -> imagesOut(nb, meta, idx, out, svg = true)
                ExportFormat.MD -> out.write(markdown(nb, meta, idx).toByteArray())
                ExportFormat.MNOTES -> zipNotebook(nb, out)
            }
            out.flush()
            true
        } catch (_: Throwable) { false }
    }

    // — PDF (wektorowy: kreski, tekst i wzory trafiają do pliku jako grafika wektorowa) —

    private fun pdfOut(nb: String, meta: NotebookMeta, idx: List<Int>, out: OutputStream) {
        val doc = PdfDocument()
        renderer.bakeCrops = true
        try {
            var n = 0
            for (i in idx) {
                val p = meta.pages.getOrNull(i) ?: continue
                val spec = p.spec
                val page = doc.startPage(PdfDocument.PageInfo.Builder(spec.w.roundToInt().coerceAtLeast(1), spec.h.roundToInt().coerceAtLeast(1), n++).create())
                drawPage(page.canvas, nb, p, 1f, 2f)
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
            // Skia może trzymać bitmapy do zamknięcia dokumentu, więc kopie przyciętych obrazów zwalniamy dopiero tu
            renderer.bakeCrops = false
            renderer.releaseBaked()
        }
    }

    /** Rysuje stronę w układzie punktów; [bgScale] = px/pt dla tła PDF. */
    private fun drawPage(c: Canvas, nb: String, p: PageMeta, scale: Float, bgScale: Float) {
        val spec = p.spec
        val bg = spec.pdf?.let { pdf.render(nb, it.asset, it.page, (spec.w * bgScale).roundToInt(), (spec.h * bgScale).roundToInt()) }
        renderer.drawPaper(c, spec, scale, bg)
        renderer.drawElements(c, nb, repo.readPage(nb, p.id), 0, null, scale, true, spec.darkPaper)
    }

    // — PNG i SVG: jedna strona = jeden plik; więcej stron = archiwum ZIP —

    private fun imagesOut(nb: String, meta: NotebookMeta, idx: List<Int>, out: OutputStream, svg: Boolean) {
        val valid = idx.filter { it in meta.pages.indices }
        if (valid.size == 1) { imagePage(nb, meta, valid[0], out, svg); return }
        ZipOutputStream(out).use { z -> imagesInto(z, nb, meta, valid, svg, single = null, folder = null) }
    }

    private fun imagePage(nb: String, meta: NotebookMeta, i: Int, o: OutputStream, svg: Boolean) {
        val p = meta.pages[i]
        if (svg) o.write(svgPage(nb, p).toByteArray(Charsets.UTF_8)) else pngPage(nb, p, o)
    }

    /** Wpisuje strony do otwartego archiwum: jedna strona jako wpis [single] (gdy podany), inaczej `page-NNN` w [folder]. */
    private fun imagesInto(z: ZipOutputStream, nb: String, meta: NotebookMeta, idx: List<Int>, svg: Boolean, single: String?, folder: String?) {
        val ext = if (svg) "svg" else "png"
        val valid = idx.filter { it in meta.pages.indices }
        for (i in valid) {
            val entry = if (single != null && valid.size == 1) single
            else (if (folder != null) "$folder/" else "") + "page-%03d.%s".format(Locale.ROOT, i + 1, ext)
            z.putNextEntry(ZipEntry(entry))
            imagePage(nb, meta, i, z, svg)
            z.closeEntry()
        }
    }

    private fun pngPage(nb: String, p: PageMeta, out: OutputStream) {
        val s = min(3f, 4096f / max(p.spec.w, p.spec.h))
        val bmp = Bitmap.createBitmap((p.spec.w * s).roundToInt(), (p.spec.h * s).roundToInt(), Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(bmp)
            c.scale(s, s)
            drawPage(c, nb, p, s, s)
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        } finally { bmp.recycle() }
    }

    private fun f(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.2f".format(Locale.ROOT, v)
    private fun hex(c: Int) = "#%06X".format(Locale.ROOT, c and 0xFFFFFF)
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun pngB64(b: Bitmap): String {
        val bo = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, bo)
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
    }

    private fun svgPage(nb: String, p: PageMeta): String {
        val spec = p.spec
        val sb = StringBuilder(64 * 1024)
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"${f(spec.w)}pt\" height=\"${f(spec.h)}pt\" viewBox=\"0 0 ${f(spec.w)} ${f(spec.h)}\">\n")
        sb.append("<rect width=\"${f(spec.w)}\" height=\"${f(spec.h)}\" fill=\"${hex(spec.paper)}\"/>\n")
        val ref: PdfRef? = spec.pdf
        if (ref != null) {
            pdf.render(nb, ref.asset, ref.page, (spec.w * 2).roundToInt(), (spec.h * 2).roundToInt())?.let {
                sb.append("<image width=\"${f(spec.w)}\" height=\"${f(spec.h)}\" xlink:href=\"data:image/png;base64,${pngB64(it)}\"/>\n")
            }
        } else svgTemplate(sb, spec)
        val fl = FloatList()
        for (e in repo.readPage(nb, p.id).elements) when (e) {
            is Stroke -> svgStroke(sb, e, fl, spec.darkPaper)
            is TextBox -> svgText(sb, e)
            is TapeEl -> {
                val tr = svgBoxTransform(e)
                val rad = f(minOf(e.w, e.h) * 0.12f)
                sb.append("<rect x=\"${f(e.cx - e.w / 2)}\" y=\"${f(e.cy - e.h / 2)}\" width=\"${f(e.w)}\" height=\"${f(e.h)}\" rx=\"$rad\" fill=\"${hex(e.color)}\" fill-opacity=\"${if (e.hidden) "1" else "0.18"}\"$tr/>\n")
            }
            is FillEl -> {
                val hl = if (e.hl) " opacity=\"${f(PenSpec.of(PenKind.HIGHLIGHTER).alpha * (if (spec.darkPaper) 0.84f else 1f))}\" style=\"mix-blend-mode:${if (spec.darkPaper) "screen" else "multiply"}\"" else ""
                sb.append("<path fill=\"${hex(e.color)}\" fill-rule=\"evenodd\"$hl d=\"")
                for (ring in e.rings) if (ring.size >= 6) {
                    sb.append("M${f(ring[0])} ${f(ring[1])}")
                    var i = 2
                    while (i < ring.size) { sb.append("L${f(ring[i])} ${f(ring[i + 1])}"); i += 2 }
                    sb.append('Z')
                }
                sb.append("\"/>\n")
            }
            is ImageEl -> assets.load(nb, e.asset)?.let { b ->
                val tr = svgBoxTransform(e)
                val cr = e.crop
                val filter = svgFx(sb, e.fx)
                if (cr.isFull) {
                    sb.append("<image x=\"${f(e.cx - e.w / 2)}\" y=\"${f(e.cy - e.h / 2)}\" width=\"${f(e.w)}\" height=\"${f(e.h)}\" opacity=\"${f(e.alpha)}\"$tr$filter preserveAspectRatio=\"none\" xlink:href=\"data:image/png;base64,${pngB64(b)}\"/>\n")
                } else {
                    // wycinek: do pliku trafia tylko widoczna część (wyprostowana), a nie cały obraz z maską
                    val kept = CropBake.bake(b, cr, (e.w * 4f).roundToInt(), (e.h * 4f).roundToInt())
                    try {
                        sb.append("<image x=\"${f(e.cx - e.w / 2)}\" y=\"${f(e.cy - e.h / 2)}\" width=\"${f(e.w)}\" height=\"${f(e.h)}\" opacity=\"${f(e.alpha)}\"$tr$filter preserveAspectRatio=\"none\" xlink:href=\"data:image/png;base64,${pngB64(kept)}\"/>\n")
                    } finally { kept.recycle() }
                }
            }
        }
        sb.append("</svg>\n")
        return sb.toString()
    }

    /** Dopisuje do [sb] filtr macierzy kolorów obrazu (przesunięcia w skali 0..1, jak w SVG) i zwraca atrybut `filter`; pusty bez efektów. */
    private fun svgFx(sb: StringBuilder, fx: com.mdev.mnotes.core.model.ImageFx): String {
        if (fx.isNone) return ""
        val m = fx.matrix()
        val values = (0 until 4).joinToString(" ") { r -> (0 until 5).joinToString(" ") { c -> f(if (c == 4) m[r * 5 + c] / 255f else m[r * 5 + c]) } }
        val id = "fx${fx.hashCode().toUInt()}"
        sb.append("<filter id=\"$id\" color-interpolation-filters=\"sRGB\"><feColorMatrix type=\"matrix\" values=\"$values\"/></filter>\n")
        return " filter=\"url(#$id)\""
    }

    private fun svgTemplate(sb: StringBuilder, spec: PageSpec) {
        val g = renderer.geom(spec)
        if (g.isEmpty) return
        val lw = spec.lineWidth
        val col = hex(spec.line)
        fun lines(a: FloatArray, w: Float) {
            if (a.isEmpty()) return
            sb.append("<path stroke=\"$col\" stroke-width=\"${f(w)}\" fill=\"none\" d=\"")
            var i = 0
            while (i + 3 < a.size) { sb.append("M${f(a[i])} ${f(a[i + 1])}L${f(a[i + 2])} ${f(a[i + 3])}"); i += 4 }
            sb.append("\"/>\n")
        }
        lines(g.lines, lw); lines(g.heavy, lw * 1.5f)
        var i = 0
        while (i + 1 < g.dots.size) { sb.append("<circle cx=\"${f(g.dots[i])}\" cy=\"${f(g.dots[i + 1])}\" r=\"${f(lw * 1.3f)}\" fill=\"$col\"/>\n"); i += 2 }
        i = 0
        while (i + 3 < g.boxes.size) {
            sb.append("<rect x=\"${f(g.boxes[i])}\" y=\"${f(g.boxes[i + 1])}\" width=\"${f(g.boxes[i + 2] - g.boxes[i])}\" height=\"${f(g.boxes[i + 3] - g.boxes[i + 1])}\" rx=\"2\" fill=\"none\" stroke=\"$col\" stroke-width=\"${f(lw * 1.2f)}\"/>\n")
            i += 4
        }
    }

    private fun svgStroke(sb: StringBuilder, s: Stroke, fl: FloatList, dark: Boolean) {
        val n = s.count
        if (n <= 0) return
        val pts = s.pts
        val hl = s.pen == PenKind.HIGHLIGHTER
        val alpha = PenSpec.of(s.pen).alpha * (if (hl && dark) 0.84f else 1f)
        val common = (if (alpha < 0.999f) " opacity=\"${f(alpha)}\"" else "") + (if (hl) " style=\"mix-blend-mode:${if (dark) "screen" else "multiply"}\"" else "")
        val constant = n == 1 || StrokeOutline.isConstant(pts, n)
        val mark = sb.length
        if (constant) {
            var avg = 0f
            for (i in 0 until n) avg += pts[3 * i + 2]
            val w = max(s.width * (avg / n), 0.3f)
            if (n == 1) { sb.append("<circle cx=\"${f(pts[0])}\" cy=\"${f(pts[1])}\" r=\"${f(w / 2)}\" fill=\"${hex(s.color)}\"$common/>\n"); return }
            sb.append("<path fill=\"none\" stroke=\"${hex(s.color)}\" stroke-width=\"${f(w)}\" stroke-linecap=\"round\" stroke-linejoin=\"round\"$common d=\"")
        } else sb.append("<path fill=\"${hex(s.color)}\"$common d=\"")
        val data = sb.length
        // Linia przerywana to jedna ścieżka z wielu kawałków, tak jak na ekranie.
        if (s.line == LineStyle.SOLID) {
            if (constant) svgLine(sb, pts, 0, n, s.poly) else svgOutline(sb, pts, 0, n, s.width, fl)
        } else {
            val d = StrokeDash.Pieces()
            StrokeDash.cut(pts, n, s.width, s.line, s.poly, s.poly || s.ellipse, d)
            for (i in 0 until d.count) {
                if (constant) svgLine(sb, d.pts.a, 3 * d.start(i), d.size(i), s.poly) else svgOutline(sb, d.pts.a, 3 * d.start(i), d.size(i), s.width, fl)
            }
        }
        if (sb.length == data) sb.setLength(mark) else sb.append("\"/>\n")
    }

    /** Oś kreski o stałej grubości: [n] punktów od indeksu [o] w tablicy trójek. */
    private fun svgLine(sb: StringBuilder, a: FloatArray, o: Int, n: Int, sharp: Boolean) {
        sb.append("M${f(a[o])} ${f(a[o + 1])}")
        // Kropka: odcinek zerowej długości z okrągłymi końcami.
        if (n == 1) sb.append("h0.01")
        else if (n == 2) sb.append("L${f(a[o + 3])} ${f(a[o + 4])}")
        else if (sharp) for (i in 1 until n) sb.append("L${f(a[o + 3 * i])} ${f(a[o + 3 * i + 1])}")
        else {
            for (i in 1 until n - 1) {
                val x0 = a[o + 3 * i]; val y0 = a[o + 3 * i + 1]; val x1 = a[o + 3 * i + 3]; val y1 = a[o + 3 * i + 4]
                sb.append("Q${f(x0)} ${f(y0)} ${f((x0 + x1) / 2)} ${f((y0 + y1) / 2)}")
            }
            sb.append("L${f(a[o + 3 * n - 3])} ${f(a[o + 3 * n - 2])}")
        }
    }

    /** Zamknięty kontur kreski o zmiennej grubości. */
    private fun svgOutline(sb: StringBuilder, a: FloatArray, o: Int, n: Int, width: Float, fl: FloatList) {
        StrokeOutline.build(a, n, width, fl, o)
        val sm = FloatList()
        StrokeOutline.smooth(fl, sm)
        if (sm.size < 6) return
        sb.append("M${f(sm.a[0])} ${f(sm.a[1])}")
        var i = 2
        while (i + 3 < sm.size) { sb.append("Q${f(sm.a[i])} ${f(sm.a[i + 1])} ${f(sm.a[i + 2])} ${f(sm.a[i + 3])}"); i += 4 }
        sb.append("Z")
    }

    /** Atrybut `transform` ramki: obrót wokół środka, a przy odbiciu jeszcze lustro w jej osi x (po obrocie). */
    private fun svgBoxTransform(e: BoxElement): String {
        val parts = ArrayList<String>(2)
        if (e.rot != 0f) parts += "rotate(${f(Math.toDegrees(e.rot.toDouble()).toFloat())} ${f(e.cx)} ${f(e.cy)})"
        if (e.mirror) parts += "translate(${f(e.cx)} ${f(e.cy)}) scale(-1 1) translate(${f(-e.cx)} ${f(-e.cy)})"
        return if (parts.isEmpty()) "" else " transform=\"${parts.joinToString(" ")}\""
    }

    private fun svgText(sb: StringBuilder, t: TextBox) {
        val lay = renderer.layoutFor(t)
        val x0 = t.cx - t.w / 2; val y0 = t.cy - t.h / 2
        sb.append("<g")
        sb.append(svgBoxTransform(t))
        sb.append(">\n")
        if (t.fill != 0) sb.append("<rect x=\"${f(x0)}\" y=\"${f(y0)}\" width=\"${f(t.w)}\" height=\"${f(t.h)}\" rx=\"4\" fill=\"${hex(t.fill)}\"/>\n")
        // ręcznie ustawiona ramka przycina tekst tak samo jak na ekranie
        val clip = t.fit == TextBox.FIT_FIXED
        if (clip) sb.append("<clipPath id=\"tc${t.id}\"><rect x=\"${f(x0)}\" y=\"${f(y0)}\" width=\"${f(t.w)}\" height=\"${f(t.h)}\"/></clipPath>\n<g clip-path=\"url(#tc${t.id})\">\n")
        val fam = when (t.family) { TextBox.FAMILY_SERIF -> "serif"; TextBox.FAMILY_MONO -> "monospace"; TextBox.FAMILY_HAND -> "cursive"; else -> "sans-serif" }
        val deco = listOfNotNull("underline".takeIf { t.underline }, "line-through".takeIf { t.strike }).joinToString(" ")
        for (l in 0 until lay.lineCount) {
            val line = lay.text.substring(lay.getLineStart(l), lay.getLineEnd(l)).trimEnd('\n')
            if (line.isEmpty()) continue
            sb.append("<text x=\"${f(x0 + lay.getLineLeft(l))}\" y=\"${f(y0 + lay.getLineBaseline(l))}\" font-family=\"$fam\" font-size=\"${f(t.size)}\" fill=\"${hex(t.color)}\"")
            if (t.style and TextBox.STYLE_BOLD != 0) sb.append(" font-weight=\"bold\"")
            if (t.style and TextBox.STYLE_ITALIC != 0) sb.append(" font-style=\"italic\"")
            if (deco.isNotEmpty()) sb.append(" text-decoration=\"$deco\"")
            sb.append(" xml:space=\"preserve\">${esc(line)}</text>\n")
        }
        if (clip) sb.append("</g>\n")
        sb.append("</g>\n")
    }

    // — Markdown: tylko tekst z pól tekstowych (odręczne notatki i obrazy nie mają odpowiednika) —

    private fun markdown(nb: String, meta: NotebookMeta, idx: List<Int>): String {
        val sb = StringBuilder("# ${meta.title}\n")
        var skipped = 0
        for (i in idx) {
            val p = meta.pages.getOrNull(i) ?: continue
            val els = repo.readPage(nb, p.id).elements
            val texts = els.filterIsInstance<TextBox>().sortedWith(compareBy({ (it.cy / 24f).toInt() }, { it.cx }))
            skipped += els.count { it is Stroke || it is ImageEl }
            sb.append("\n## ${T.t("Strona", "Page")} ${i + 1}\n\n")
            for (t in texts) {
                val lines = t.text.split('\n').map { l ->
                    if (l.isBlank()) l else {
                        var s = l.trim()
                        if (t.style and TextBox.STYLE_STRIKE != 0) s = "~~$s~~"
                        if (t.style and TextBox.STYLE_ITALIC != 0) s = "*$s*"
                        if (t.style and TextBox.STYLE_BOLD != 0) s = "**$s**"
                        s
                    }
                }
                sb.append(lines.joinToString("  \n")).append("\n\n")
            }
        }
        if (skipped > 0) sb.append("---\n*${T.t("Odręczne elementy i obrazy ($skipped) nie są częścią eksportu Markdown — użyj PDF lub SVG.", "Handwriting and images ($skipped) are not part of the Markdown export — use PDF or SVG.")}*\n")
        return sb.toString()
    }

    // — .mnotes: zwykły ZIP z meta.json, stronami i zasobami —

    private fun zipNotebook(nb: String, out: OutputStream) {
        val root = repo.dir(nb)
        ZipOutputStream(out).use { z ->
            val meta = repo.meta(nb) ?: return
            z.putNextEntry(ZipEntry("meta.json")); z.write(meta.toJson().toByteArray(Charsets.UTF_8)); z.closeEntry()
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                val rel = file.relativeTo(root).path.replace(File.separatorChar, '/')
                if (rel == "meta.json" || rel.endsWith(".tmp") || rel.endsWith(".bak") || rel == "thumb.png") return@forEach
                z.putNextEntry(ZipEntry(rel))
                file.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
    }
}

/** Import PDF, obrazów i zeszytów .mnotes — kopiuje dane do prywatnego katalogu zeszytu; zwrócone meta trzeba zarejestrować przez `repo.addNotebook` na wątku UI. */
class Importer(private val repo: Repository, private val pdf: PdfPool) {
    private val cover = 0xFF3E7CB1.toInt()

    fun importPdf(input: InputStream, title: String, folderId: String?): NotebookMeta? {
        val id = newId()
        val asset = repo.importAsset(id, input, "pdf")
        val count = pdf.pageCount(id, asset).coerceAtMost(3000)
        if (count <= 0) { repo.dir(id).deleteRecursively(); return null }
        val now = System.currentTimeMillis()
        val pages = (0 until count).map { i ->
            val (w, h) = pdf.pageSize(id, asset, i) ?: (595f to 842f)
            PageMeta(newId(), PageSpec(w = w, h = h, paper = Paper.WHITE, template = Template.BLANK, pdf = PdfRef(asset, i)))
        }
        return NotebookMeta(id, title, folderId, now, now, now, cover, CoverStyle.PLAIN, pdfDoc = true, pages = pages)
    }

    /** Każdy obraz staje się stroną o proporcjach obrazu (dłuższy bok = 842 pt), obraz leży na stronie jako element. */
    fun importImages(inputs: List<Pair<() -> InputStream?, String>>, title: String, folderId: String?): NotebookMeta? {
        val id = newId()
        val now = System.currentTimeMillis()
        val pages = ArrayList<PageMeta>()
        for ((open, ext) in inputs) {
            val asset = open()?.use { repo.importAsset(id, it, ext) } ?: continue
            val (iw, ih) = Assets.imageSize(repo.assetFile(id, asset)) ?: continue
            val k = 842f / max(iw, ih)
            val w = iw * k; val h = ih * k
            val pid = newId()
            val el = ImageEl(com.mdev.mnotes.core.model.Ids.next(), asset, w / 2, h / 2, w, h)
            repo.writePage(id, pid, PageCodec.encode(PageContent.of(listOf(el))))
            pages.add(PageMeta(pid, PageSpec(w = w, h = h)))
        }
        if (pages.isEmpty()) { repo.dir(id).deleteRecursively(); return null }
        return NotebookMeta(id, title, folderId, now, now, now, cover, CoverStyle.PLAIN, pages = pages)
    }

    fun importMnotes(input: InputStream, folderId: String?): NotebookMeta? {
        val id = newId()
        val dst = repo.dir(id)
        var meta: NotebookMeta? = null
        var total = 0L
        try {
            ZipInputStream(input).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name
                    // Ochrona przed ścieżkami spoza katalogu zeszytu (zip-slip) i obcymi plikami.
                    val ok = name == "meta.json" || ((name.startsWith("p/") || name.startsWith("assets/")) && !name.contains(".."))
                    if (!ok) continue
                    val f = File(dst, name)
                    if (!f.canonicalPath.startsWith(dst.canonicalPath + File.separator)) continue
                    f.parentFile?.mkdirs()
                    FileOutputStream(f).use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val r = z.read(buf); if (r < 0) break
                            total += r
                            if (total > MAX_IMPORT) throw IllegalStateException("too big")
                            o.write(buf, 0, r)
                        }
                    }
                }
            }
            val mf = File(dst, "meta.json")
            if (mf.exists()) meta = NotebookMeta.fromJson(mf.readText())
        } catch (_: Throwable) { meta = null }
        if (meta == null) { dst.deleteRecursively(); return null }
        val now = System.currentTimeMillis()
        return meta.copy(id = id, folderId = folderId, trashedAt = 0L, opened = now, modified = now)
    }

    companion object { const val MAX_IMPORT = 2L * 1024 * 1024 * 1024 }
}
