package com.mdev.mnotes.tools

import com.mdev.mnotes.core.geom.FloatList
import com.mdev.mnotes.core.guide.SetupGuide as G
import com.mdev.mnotes.core.guide.SetupScenes as S
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Paper
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.core.model.TemplateGeometry
import com.mdev.mnotes.core.pen.PenSpec
import com.mdev.mnotes.core.pen.StrokeBuilder
import com.mdev.mnotes.core.pen.StrokeOutline
import com.mdev.mnotes.ui.GlyphPart
import com.mdev.mnotes.ui.GlyphRole
import com.mdev.mnotes.ui.PenGlyphs
import com.mdev.mnotes.ui.ToolGlyphParts
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Narzędzie deweloperskie (nie test): rysuje papier i pióra tym samym kodem z `core/` co aplikacja, ale przez Java2D,
 * żeby ocenić wygląd bez urządzenia. Uruchomienie:
 *   MNOTES_PREVIEW_DIR=/ścieżka ./gradlew --offline :app:testDebugUnitTest --tests '*PreviewHarness*'
 * Bez zmiennej środowiskowej test jest pomijany. Odwzorowuje logikę `PageRenderer` (bez zakreślacza/blendów).
 */
class PreviewHarness {
    private fun g2(img: BufferedImage) = img.createGraphics().also {
        it.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        it.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    private fun col(c: Int) = Color(c, true)

    /** Jak `PageRenderer.drawPaper`: wycinek strony [w]×[h] pt przy skali [s] px/pt. */
    private fun paper(spec: PageSpec, s: Float, w: Int, h: Int): BufferedImage {
        val img = BufferedImage((w * s).toInt(), (h * s).toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = g2(img)
        g.scale(s.toDouble(), s.toDouble())
        g.color = col(spec.paper); g.fill(Rectangle2D.Float(0f, 0f, w.toFloat(), h.toFloat()))
        val geom = TemplateGeometry.build(spec.copy(w = w.toFloat(), h = h.toFloat()))
        val lw = max(spec.lineWidth, 0.9f / s)
        g.color = col(spec.line)
        g.stroke = BasicStroke(lw, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
        var i = 0
        while (i + 3 < geom.lines.size) { g.draw(Line2D.Float(geom.lines[i], geom.lines[i + 1], geom.lines[i + 2], geom.lines[i + 3])); i += 4 }
        g.stroke = BasicStroke(lw * 1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
        i = 0
        while (i + 3 < geom.heavy.size) { g.draw(Line2D.Float(geom.heavy[i], geom.heavy[i + 1], geom.heavy[i + 2], geom.heavy[i + 3])); i += 4 }
        val dw = max(lw * 2.6f, 1.4f / s)
        i = 0
        while (i + 1 < geom.dots.size) { g.fill(Ellipse2D.Float(geom.dots[i] - dw / 2, geom.dots[i + 1] - dw / 2, dw, dw)); i += 2 }
        g.dispose()
        return img
    }

    /** Jak `PageRenderer.drawStrokeData` (bez blendów). */
    private fun stroke(g: java.awt.Graphics2D, pen: PenKind, color: Int, width: Float, pts: FloatArray, n: Int) {
        val spec = PenSpec.of(pen)
        g.color = Color((color and 0xFFFFFF) or ((spec.alpha * 255).toInt() shl 24), true)
        if (n == 1 || StrokeOutline.isConstant(pts, n)) {
            var avg = 0f
            for (i in 0 until n) avg += pts[3 * i + 2]
            val w = max(width * (avg / n), 0.3f)
            g.stroke = BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val p = Path2D.Float(); p.moveTo(pts[0], pts[1])
            for (i in 1 until n - 1) {
                val x0 = pts[3 * i]; val y0 = pts[3 * i + 1]; val x1 = pts[3 * i + 3]; val y1 = pts[3 * i + 4]
                p.quadTo(x0, y0, (x0 + x1) / 2, (y0 + y1) / 2)
            }
            p.lineTo(pts[3 * n - 3], pts[3 * n - 2])
            g.draw(p)
        } else {
            val fl = FloatList()
            StrokeOutline.build(pts, n, width, fl)
            val sm = FloatList()
            StrokeOutline.smooth(fl, sm)
            val p = Path2D.Float(Path2D.WIND_NON_ZERO); p.moveTo(sm.a[0], sm.a[1])
            var i = 2
            while (i + 3 < sm.size) { p.quadTo(sm.a[i], sm.a[i + 1], sm.a[i + 2], sm.a[i + 3]); i += 4 }
            p.closePath(); g.fill(p)
        }
    }

    /** Syntetyczne „pismo odręczne” (pętle jak w cursive), próbkowane 240 Hz, zmienny nacisk i prędkość. */
    private fun sample(kind: PenKind, color: Int, width: Float, x0: Float, y0: Float, speed: Float, seed: Int): FloatArray {
        val b = StrokeBuilder()
        b.begin(kind, color, width, 0.5f, 0.2f)
        var t = 0L; var u = 0f
        val rnd = java.util.Random(seed.toLong())
        var phase = rnd.nextFloat()
        while (u < 1f) {
            val x = x0 + u * 230f + 7f * cos(2 * PI * (u * 9f + phase)).toFloat()
            val y = y0 + 9f * sin(2 * PI * (u * 9f + phase)).toFloat() - 6f * sin(u * PI).toFloat()
            val pr = (0.25f + 0.65f * sin(u * PI * 3.0).toFloat().let { it * it }).coerceIn(0.05f, 1f)
            b.add(x, y, pr, t)
            val dt = 1000f / 240f
            t += dt.toLong().coerceAtLeast(1)
            u += (speed / 240f) / 230f * (0.7f + 0.6f * sin(u * PI * 5).toFloat().let { it * it })
        }
        return b.finish(1)!!.let { it.pts }
    }

    /** Glify piór (geometria z `PenGlyphs`) w skali ikon paska, powiększone, na jasnym i ciemnym tle. */
    private fun glyphSheet(dir: String) {
        val kinds = PenKind.entries
        val cell = 120; val scale = cell / 24.0
        val inks = listOf(0xFF24407E.toInt(), 0xFFC8433F.toInt(), 0xFFFFFFFF.toInt(), 0xFFEBCB6B.toInt(), 0xFF3E8E5A.toInt())
        val img = BufferedImage(cell * kinds.size + 20, cell * 2 + 30, BufferedImage.TYPE_INT_ARGB)
        val g = g2(img)
        for (row in 0..1) {
            g.color = if (row == 0) Color(0xFC, 0xFC, 0xFD) else Color(0x38, 0x3A, 0x3E)
            g.fillRect(0, row * (cell + 10), img.width, cell + 20)
            val tool = if (row == 0) Color(0x2A, 0x2B, 0x2E) else Color(0xE6, 0xE7, 0xE9)
            kinds.forEachIndexed { i, k ->
                val ink = Color(inks[(i + row * 2) % inks.size], true)
                g.drawImage(glyph(PenGlyphs.parts(k), cell, tool, ink), 10 + i * cell, 10 + row * (cell + 10), null)
            }
        }
        g.dispose()
        ImageIO.write(img, "png", File(dir, "glyphs.png"))
    }

    /** Jeden glif (części w siatce 24) jako obraz [cell]×[cell], tak jak rysuje go `GlyphCanvas`. */
    private fun glyph(parts: List<GlyphPart>, cell: Int, tool: Color, ink: Color): BufferedImage {
        val lay = BufferedImage(cell, cell, BufferedImage.TYPE_INT_ARGB)
        val lg = g2(lay); lg.scale(cell / 24.0, cell / 24.0)
        for (p in parts) {
            val path = Path2D.Float()
            var j = 0
            while (j < p.pts.size) { if (j == 0) path.moveTo(p.pts[j], p.pts[j + 1]) else path.lineTo(p.pts[j], p.pts[j + 1]); j += 2 }
            if (p.closed) path.closePath()
            val st = BasicStroke(1.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            when (p.role) {
                GlyphRole.BODY -> { lg.color = Color(tool.red, tool.green, tool.blue, 41); lg.fill(path); lg.color = tool; lg.stroke = st; lg.draw(path) }
                GlyphRole.INK -> {
                    lg.color = ink; lg.fill(path); lg.stroke = BasicStroke(0.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); lg.draw(path)
                    lg.color = Color(tool.red, tool.green, tool.blue, 140); lg.stroke = BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); lg.draw(path)
                }
                GlyphRole.DETAIL -> { lg.color = Color(tool.red, tool.green, tool.blue, 204); lg.stroke = BasicStroke(1.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); lg.draw(path) }
                GlyphRole.CUT -> {
                    lg.composite = java.awt.AlphaComposite.Clear
                    if (p.closed) lg.fill(path) else { lg.stroke = BasicStroke(0.85f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); lg.draw(path) }
                    lg.composite = java.awt.AlphaComposite.SrcOver
                }
                GlyphRole.MARK -> { lg.color = tool; lg.stroke = st; lg.draw(path) }
                GlyphRole.DASH -> { lg.color = tool; lg.stroke = BasicStroke(1.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, floatArrayOf(2.6f, 2.2f), 0f); lg.draw(path) }
                GlyphRole.DOT -> { lg.color = tool; lg.fill(path) }
            }
        }
        lg.dispose()
        return lay
    }

    /** 2b-A1: gumka (4 tryby) i lasso (2) obok dwóch piór, w tej samej skali, na jasnym i ciemnym tle; aktywne w akcencie. */
    private fun toolSheet(dir: String) {
        val sets = listOf(PenGlyphs.parts(PenKind.BALLPOINT), PenGlyphs.parts(PenKind.HIGHLIGHTER)) +
            (0..3).map { ToolGlyphParts.eraser(it) } + listOf(ToolGlyphParts.lasso(false), ToolGlyphParts.lasso(true))
        val cell = 96
        val img = BufferedImage(cell * sets.size + 20, (cell + 10) * 3 + 10, BufferedImage.TYPE_INT_ARGB)
        val g = g2(img)
        val rows = listOf(Color(0xFC, 0xFC, 0xFD) to Color(0x2A, 0x2B, 0x2E), Color(0x38, 0x3A, 0x3E) to Color(0xE6, 0xE7, 0xE9), Color(0xFC, 0xFC, 0xFD) to Color(0x2E, 0x7D, 0x4F))
        rows.forEachIndexed { row, (bg, tool) ->
            g.color = bg; g.fillRect(0, row * (cell + 10), img.width, cell + 10)
            sets.forEachIndexed { i, parts -> g.drawImage(glyph(parts, cell, tool, Color(0xFF24407E.toInt(), true)), 10 + i * cell, 5 + row * (cell + 10), null) }
        }
        g.dispose()
        ImageIO.write(img, "png", File(dir, "tools.png"))
    }

    /** 2b-A8: glify konstrukcji narzędzia Geometria, w dwóch rzędach, na jasnym tle. */
    private fun geoSheet(dir: String) {
        val kinds = com.mdev.mnotes.core.geom.GeoTool.Kind.entries
        val cell = 72; val perRow = 9
        val rows = (kinds.size + perRow - 1) / perRow
        val img = BufferedImage(cell * perRow + 20, (cell + 8) * rows + 12, BufferedImage.TYPE_INT_ARGB)
        val g = g2(img)
        g.color = Color(0xFC, 0xFC, 0xFD); g.fillRect(0, 0, img.width, img.height)
        kinds.forEachIndexed { i, k -> g.drawImage(glyph(com.mdev.mnotes.ui.GeoGlyphParts.parts(k), cell, Color(0x2A, 0x2B, 0x2E), Color.BLACK), 10 + (i % perRow) * cell, 6 + (i / perRow) * (cell + 8), null) }
        g.dispose()
        ImageIO.write(img, "png", File(dir, "geo.png"))
    }

    /** 2b-A9: ta sama notatka jak powstała (lewa kolumna) i po [com.mdev.mnotes.core.color.ThemeMap] (prawa), dla trzech papierów. */
    private fun themeSheet(dir: String) {
        val tm = com.mdev.mnotes.core.color.ThemeMap
        val inks = listOf(0xFF1B1D21, 0xFF24407E, 0xFFC8433F, 0xFF3E8E5A, 0xFF8A4FB8, 0xFFE8A33D).map { it.toInt() }
        val hl = 0xFFFFE066.toInt()
        val papers = listOf(Paper.WHITE, Paper.IVORY, Paper.DARK)
        val sc = 2.4f; val w = 250; val h = 30 * inks.size + 40
        val pw = (w * sc).toInt(); val ph = (h * sc).toInt()
        val img = BufferedImage(pw * 2 + 30, (ph + 10) * papers.size + 10, BufferedImage.TYPE_INT_ARGB)
        val sg = g2(img); sg.color = Color(0x70, 0x70, 0x70); sg.fillRect(0, 0, img.width, img.height)
        papers.forEachIndexed { row, pp ->
            val src = PageSpec(paper = pp, template = Template.LINED)
            val t = tm.forPage(pp, false, if (Paper.isDark(pp)) tm.LIGHT else tm.DARK)!!
            for (col in 0..1) {
                val tr = col == 1
                val spec = if (tr) src.copy(paper = t.dstPaper, line = t.ink(src.line)) else src
                val page = paper(spec, sc, w, h)
                val g = g2(page); g.scale(sc.toDouble(), sc.toDouble())
                val hp = sample(PenKind.HIGHLIGHTER, hl, 10f, 12f, 28f, 260f, 99)
                stroke(g, PenKind.HIGHLIGHTER, if (tr) t.highlighter(hl) else hl, 10f, hp, hp.size / 3)
                inks.forEachIndexed { i, c0 ->
                    val c = if (Paper.isDark(pp) && i == 0) 0xFFF2F2F2.toInt() else c0
                    val pts = sample(PenKind.BALLPOINT, c, 1.4f, 12f, 30f + i * 30f, 300f, i)
                    stroke(g, PenKind.BALLPOINT, if (tr) t.ink(c) else c, 1.4f, pts, pts.size / 3)
                }
                g.dispose()
                sg.drawImage(page, 10 + col * (pw + 10), 10 + row * (ph + 10), null)
            }
        }
        sg.dispose()
        ImageIO.write(img, "png", File(dir, "theme.png"))
    }

    /** 2b-A10: glify czterech trybów Krzywej i po jednym wyniku każdego trybu na kartce (kontur z wypełnieniem). */
    private fun curveSheet(dir: String) {
        val cell = 96
        val modes = com.mdev.mnotes.core.shapes.CurveMode.entries
        val sc = 2.4f; val w = 210f; val h = 120f
        val img = BufferedImage(max(cell * modes.size, (w * sc).toInt()) + 20, cell + 20 + (h * sc).toInt() + 10, BufferedImage.TYPE_INT_ARGB)
        val g = g2(img)
        g.color = Color(0xFC, 0xFC, 0xFD); g.fillRect(0, 0, img.width, img.height)
        modes.forEachIndexed { i, m -> g.drawImage(glyph(com.mdev.mnotes.ui.CurveGlyphParts.parts(m), cell, Color(0x2A, 0x2B, 0x2E), Color.BLACK), 10 + i * cell, 10, null) }
        val page = paper(PageSpec(paper = Paper.WHITE, template = Template.GRID), sc, w.toInt(), h.toInt())
        val pg = g2(page); pg.scale(sc.toDouble(), sc.toDouble())
        val cf = com.mdev.mnotes.core.shapes.CurveFit
        val wave = FloatArray(160) { k -> val i = k / 2; if (k % 2 == 0) 6f + i * 0.45f else 60f + 18f * sin(i / 9.0).toFloat() + ((i * 7919) % 13 - 6) * 0.12f }
        val curves = listOf(
            com.mdev.mnotes.core.shapes.Bezier.fit(wave, cf.freehandTol(1f))!! to 0xFF1B1D21.toInt(),
            cf.through(floatArrayOf(48f, 90f, 62f, 40f, 80f, 75f, 95f, 30f), false)!! to 0xFF24407E.toInt(),
            cf.through(floatArrayOf(108f, 60f, 125f, 25f, 150f, 50f, 135f, 95f), true)!! to 0xFFC8433F.toInt(),
            com.mdev.mnotes.core.shapes.CurveDraft(com.mdev.mnotes.core.shapes.CurveMode.PEN).apply {
                press(160f, 90f); release(); press(178f, 40f); drag(192f, 40f); release(); press(195f, 95f); release()
            }.ctrl()!! to 0xFF3E8E5A.toInt(),
        )
        for ((c, col) in curves) {
            val s = com.mdev.mnotes.core.shapes.Bezier.sample(c)
            val path = Path2D.Float(); path.moveTo(s[0], s[1])
            for (k in 1 until s.size / 3) path.lineTo(s[3 * k], s[3 * k + 1])
            if (cf.closed(c)) { path.closePath(); pg.color = Color(col and 0xFFFFFF or (0x47 shl 24), true); pg.fill(path) }
            pg.color = col(col); pg.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); pg.draw(path)
            pg.color = Color(0x2E, 0x7D, 0x4F)
            for (a in 0 until com.mdev.mnotes.core.shapes.Bezier.anchors(c)) pg.fill(Ellipse2D.Float(c[6 * a] - 1.6f, c[6 * a + 1] - 1.6f, 3.2f, 3.2f))
        }
        pg.dispose()
        g.drawImage(page, 10, cell + 20, null)
        g.dispose()
        ImageIO.write(img, "png", File(dir, "curves.png"))
    }

    /**
     * Akapit pola tekstu zawinięty do szerokości pola (czcionka bezszeryfowa Java2D zamiast Roboto) tym samym pomiarem,
     * którym test liczy wysokości przewodnika; zwraca wysokość.
     */
    private fun textBox(g: java.awt.Graphics2D, t: com.mdev.mnotes.core.model.TextBox): Float {
        val m = com.mdev.mnotes.core.text.AwtTextMeasure
        val font = m.font(t)
        g.font = font
        val fm = g.getFontMetrics(font)
        val left = t.cx - t.w / 2; val top = t.cy - t.h / 2
        val lines = m.lines(t, t.w)
        val lh = m.lineHeight(t)
        if (t.fill != 0) { g.color = col(t.fill); g.fill(Rectangle2D.Float(left, top, t.w, t.h)) }
        g.color = col(t.color)
        lines.forEachIndexed { i, l ->
            val x = if (t.align == com.mdev.mnotes.core.model.TextBox.ALIGN_END) left + t.w - fm.getStringBounds(l, g).width.toFloat() else left
            g.drawString(l, x, top + m.ascent(t) + i * lh)
        }
        return lines.size * lh
    }

    /** 2b-A11: strony przewodnika (tym samym `Guide` co aplikacja): arkusz miniatur i kilka stron w pełnym rozmiarze. */
    private fun guideSheet(dir: String) {
        val pages = com.mdev.mnotes.core.guide.Guide.pages(true, m = com.mdev.mnotes.core.text.AwtTextMeasure)
        val report = StringBuilder()
        fun render(p: com.mdev.mnotes.core.guide.Guide.Page, s: Float): BufferedImage {
            val img = paper(p.spec, s, p.spec.w.toInt(), p.spec.h.toInt())
            val g = g2(img); g.scale(s.toDouble(), s.toDouble())
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            for (e in p.elements) when (e) {
                is com.mdev.mnotes.core.model.FillEl -> {
                    val path = Path2D.Float(Path2D.WIND_EVEN_ODD)
                    for (r in e.rings) { path.moveTo(r[0], r[1]); var i = 2; while (i < r.size) { path.lineTo(r[i], r[i + 1]); i += 2 }; path.closePath() }
                    g.color = if (e.hl) Color(e.color and 0xFFFFFF or (0x80 shl 24), true) else col(e.color); g.fill(path)
                }
                is com.mdev.mnotes.core.model.Stroke -> {
                    if (e.count == 1) { g.color = col(e.color); g.fill(Ellipse2D.Float(e.pts[0] - e.width / 2, e.pts[1] - e.width / 2, e.width, e.width)) }
                    else {
                        val path = Path2D.Float(); path.moveTo(e.pts[0], e.pts[1])
                        for (k in 1 until e.count) path.lineTo(e.pts[3 * k], e.pts[3 * k + 1])
                        g.color = col(e.color)
                        g.stroke = if (e.line == com.mdev.mnotes.core.pen.LineStyle.DASHED) BasicStroke(e.width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, floatArrayOf(5f, 4f), 0f) else BasicStroke(e.width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                        g.draw(path)
                    }
                }
                // Taśma zakryta: pełny kolor, jak w aplikacji przed odsłonięciem.
                is com.mdev.mnotes.core.model.TapeEl -> { g.color = col(e.color); g.fill(Rectangle2D.Float(e.cx - e.w / 2, e.cy - e.h / 2, e.w, e.h)) }
                is com.mdev.mnotes.core.model.TextBox -> {
                    val need = textBox(g, e)
                    if (need > e.h + 1f) report.append("${p.title}: „${e.text.take(40)}” potrzebuje ${need.toInt()} pt, pole ${e.h.toInt()} pt\n")
                }
                else -> {}
            }
            g.dispose()
            return img
        }
        val s = 0.62f
        val pw = (595 * s).toInt(); val ph = (842 * s).toInt()
        val cols = 4; val rows = (pages.size + cols - 1) / cols
        val sheet = BufferedImage(cols * (pw + 10) + 10, rows * (ph + 10) + 10, BufferedImage.TYPE_INT_ARGB)
        val sg = g2(sheet); sg.color = Color(0x70, 0x70, 0x70); sg.fillRect(0, 0, sheet.width, sheet.height)
        pages.forEachIndexed { i, p -> sg.drawImage(render(p, s), 10 + (i % cols) * (pw + 10), 10 + (i / cols) * (ph + 10), null) }
        sg.dispose()
        ImageIO.write(sheet, "png", File(dir, "guide.png"))
        for (i in listOf(0, 7, 12, 13, 17, 20)) if (i < pages.size) ImageIO.write(render(pages[i], 1.3f), "png", File(dir, "guide_${i + 1}.png"))
        File(dir, "guide-overflow.txt").writeText(report.toString())
    }

    /**
     * 3c-A10: strony przewodnika po ustawieniach — układ jak `SetupGuideScreen` (wymiary w dp), próbki z tych samych
     * `SetupScenes`. Tablet poziomo (1280×800 dp) i telefon pionowo (400×840 dp), motyw jasny i ciemny.
     */
    private fun setupSheet(dir: String) {
        class Mem : G.Store {
            val m = HashMap(mapOf("new.template" to 1, "toolbar.size" to 1, "pen.smoothing" to 1, "ui.tooltips" to 1, "ui.haptics" to 1, "ui.tool_hints" to 1, "view.minimap" to 1))
            override fun get(key: String) = m[key] ?: 0
            override fun set(key: String, v: Int) { m[key] = v }
        }
        val accent = 0xFF87CF3E.toInt()
        fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(sh: Int) = (((a shr sh) and 255) + (((b shr sh) and 255) - ((a shr sh) and 255)) * t).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        class Pal(dark: Boolean) {
            val bg = if (dark) 0xFF2A2C2F.toInt() else 0xFFF4F5F6.toInt()
            val surface = if (dark) 0xFF34363A.toInt() else 0xFFFFFFFF.toInt()
            val line = if (dark) 0xFF55585E.toInt() else 0xFFD0D3D8.toInt()
            val on = if (dark) 0xFFE8E9EB.toInt() else 0xFF24272B.toInt()
            val onVar = if (dark) 0xFFA9AEB5.toInt() else 0xFF5C626B.toInt()
            val soft = mix(surface, accent, if (dark) 0.2f else 0.18f)
            val accentText = if (dark) mix(accent, 0xFFFFFFFF.toInt(), 0.18f) else mix(accent, 0xFF000000.toInt(), 0.42f)
            val onAccent = 0xFF10180A.toInt()
        }
        fun font(sp: Float, bold: Int = java.awt.Font.PLAIN) = java.awt.Font("SansSerif", bold, 1).deriveFont(sp)
        fun wrap(g: java.awt.Graphics2D, text: String, f: java.awt.Font, w: Float): List<String> {
            val fm = g.getFontMetrics(f); val out = ArrayList<String>(); var cur = ""
            for (word in text.split(' ')) {
                val t = if (cur.isEmpty()) word else "$cur $word"
                if (fm.stringWidth(t) > w && cur.isNotEmpty()) { out += cur; cur = word } else cur = t
            }
            if (cur.isNotEmpty()) out += cur
            return out
        }
        fun text(g: java.awt.Graphics2D, s: String, f: java.awt.Font, c: Int, x: Float, y: Float, w: Float, lh: Float): Float {
            g.font = f; g.color = col(c); var yy = y
            for (l in wrap(g, s, f, w)) { g.drawString(l, x, yy + f.size2D); yy += lh }
            return yy - y
        }
        fun scene(g: java.awt.Graphics2D, shapes: List<S.Shape>, dark: Boolean, x: Float, y: Float, w: Float) {
            val k = w / S.W
            val t = g.transform; g.translate(x.toDouble(), y.toDouble()); g.scale(k.toDouble(), k.toDouble())
            for (sh in shapes) {
                g.color = col(S.color(sh.role, dark, accent))
                when (sh) {
                    is S.Box -> { val r = java.awt.geom.RoundRectangle2D.Float(sh.l, sh.t, sh.r - sh.l, sh.b - sh.t, 2 * sh.radius, 2 * sh.radius); if (sh.outline) { g.stroke = BasicStroke(1.2f); g.draw(r) } else g.fill(r) }
                    is S.Dot -> { val e = Ellipse2D.Float(sh.cx - sh.rad, sh.cy - sh.rad, 2 * sh.rad, 2 * sh.rad); if (sh.outline) { g.stroke = BasicStroke(1.4f); g.draw(e) } else g.fill(e) }
                    is S.Line -> {
                        val p = Path2D.Float(); p.moveTo(sh.pts[0], sh.pts[1]); var i = 2
                        while (i + 1 < sh.pts.size) { p.lineTo(sh.pts[i], sh.pts[i + 1]); i += 2 }
                        g.stroke = BasicStroke(sh.width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(p)
                    }
                    is S.Label -> { g.font = font(sh.size, java.awt.Font.BOLD); g.drawString(sh.text, sh.x, sh.y) }
                }
            }
            g.transform = t
        }
        /** Jedna strona: zwraca obraz [w]×[h] dp przy [sc] px/dp. */
        fun page(st: G.State, store: Mem, dark: Boolean, w: Int, h: Int, sc: Float): BufferedImage {
            val p = Pal(dark)
            val img = BufferedImage((w * sc).toInt(), (h * sc).toInt(), BufferedImage.TYPE_INT_ARGB)
            val g = g2(img); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON); g.scale(sc.toDouble(), sc.toDouble())
            g.color = col(p.bg); g.fillRect(0, 0, w, h)
            val pg = st.page
            // Górny pasek
            val head = when (st.path) { G.START -> "Pierwsze kroki"; G.HUB -> "Poznaj więcej"; else -> G.TOPICS.first { it.id == st.path }.titlePl }
            g.font = font(13.5f); g.color = col(p.onVar); g.drawString(head, 20f, 36f)
            if (st.path != G.HUB) {
                val n = st.count; val dw = n * 8f + (n - 1) * 6f + 2f; var x = w / 2f - dw / 2
                for (i in 0 until n) { val d = if (i == st.index) 10f else 8f; g.color = col(if (i <= st.index) accent else p.line); g.fill(Ellipse2D.Float(x, 31f - d / 2, d, d)); x += d + 6f }
            }
            g.color = col(p.on); g.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(Line2D.Float(w - 34f, 24f, w - 22f, 36f)); g.draw(Line2D.Float(w - 22f, 24f, w - 34f, 36f))
            val top = 56f; val navH = 68f; val bottom = h - navH
            val wide = w >= 720 && h >= 420 + 124
            val shapes = S.of(pg, store)
            val tf = font(24f, java.awt.Font.BOLD); val bf = font(15f); val cf = font(15f, java.awt.Font.BOLD); val nf = font(13.5f)
            // Wysokość bloku tekst+opcje, żeby w szerokim układzie wyśrodkować go w pionie.
            fun block(x: Float, y0: Float, cw: Float, draw: Boolean): Float {
                var y = y0
                val gg = if (draw) g else g2(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
                y += text(gg, pg.titlePl, tf, p.on, x, y, cw, 30f) + 8f
                y += text(gg, pg.bodyPl, bf, p.onVar, x, y, cw, 21f) + 16f
                val cards: List<Triple<String, String, Pair<Boolean, Boolean>>> = if (pg.sample == G.Sample.DONE) {
                    y += text(gg, "Poznaj więcej", font(15f, java.awt.Font.BOLD), p.on, x, y, cw, 20f) + 8f
                    G.TOPICS.map { Triple(it.titlePl, it.descPl, false to (it.id in st.seen)) }
                } else {
                    val cur = G.current(pg, store); val rec = pg.recommended(store)
                    pg.options.mapIndexed { i, o -> Triple(o.pl, o.notePl, (i == cur) to (i == rec)) }
                }
                for ((idx, c) in cards.withIndex()) {
                    val (t, note, flags) = c
                    val done = pg.sample == G.Sample.DONE && flags.second
                    val sel = pg.sample != G.Sample.DONE && flags.first
                    val rec = pg.sample != G.Sample.DONE && flags.second
                    val badgeW = if (rec) gg.getFontMetrics(font(12f)).stringWidth("Zalecane") + 20f + 8f else 0f
                    val textW = cw - 16f - 22f - 14f - 16f - badgeW
                    val tl = wrap(gg, t, cf, textW); val nl = if (note.isEmpty()) emptyList() else wrap(gg, note, nf, textW)
                    val ch = maxOf(56f, 20f + tl.size * 20f + nl.size * 18f)
                    if (draw) {
                        val r = java.awt.geom.RoundRectangle2D.Float(x, y, cw, ch, 24f, 24f)
                        g.color = col(if (sel) p.soft else p.surface); g.fill(r)
                        g.color = col(if (sel) accent else p.line); g.stroke = BasicStroke(if (sel) 2f else 1f); g.draw(r)
                        val cx = x + 16f + 11f; val cy = y + ch / 2
                        val ring = Ellipse2D.Float(cx - 10f, cy - 10f, 20f, 20f)
                        if (sel || done) {
                            g.color = col(accent); g.fill(Ellipse2D.Float(cx - 11f, cy - 11f, 22f, 22f))
                            g.color = col(p.onAccent); g.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                            g.draw(Path2D.Float().apply { moveTo(cx - 5f, cy); lineTo(cx - 1.5f, cy + 3.5f); lineTo(cx + 5f, cy - 3.5f) })
                        } else if (pg.sample != G.Sample.DONE) { g.color = col(p.line); g.stroke = BasicStroke(2f); g.draw(ring) }
                        if (pg.sample == G.Sample.DONE) {
                            // Strzałka „›” jak `KeyboardArrowRight`: temat otwiera się, nie wybiera.
                            val ax = x + cw - 24f
                            g.color = col(p.onVar); g.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                            g.draw(Path2D.Float().apply { moveTo(ax - 3f, cy - 6f); lineTo(ax + 3f, cy); lineTo(ax - 3f, cy + 6f) })
                        }
                        val tx = x + 16f + 22f + 14f
                        var ty = y + (ch - tl.size * 20f - nl.size * 18f) / 2
                        g.font = cf; g.color = col(p.on); for (l in tl) { g.drawString(l, tx, ty + 15f); ty += 20f }
                        g.font = nf; g.color = col(p.onVar); for (l in nl) { g.drawString(l, tx, ty + 13.5f); ty += 18f }
                        if (rec) {
                            val bw = badgeW - 8f; val bx = x + cw - 16f - bw; val by = cy - 12f
                            g.color = col(p.soft); g.fill(java.awt.geom.RoundRectangle2D.Float(bx, by, bw, 24f, 24f, 24f))
                            g.font = font(12f); g.color = col(p.accentText); g.drawString("Zalecane", bx + 10f, by + 16.5f)
                        }
                    }
                    y += ch + if (idx < cards.size - 1) 8f else 0f
                }
                return y - y0
            }
            if (wide) {
                val cw = (w - 64f - 32f) / 2
                val bh = block(32f, 0f, cw, false)
                block(32f, top + 12f + maxOf(0f, (bottom - top - 24f - bh) / 2), cw, true)
                val sw = minOf(cw, (bottom - top - 24f) * S.W / S.H)
                scene(g, shapes, dark, 32f + cw + 32f + (cw - sw) / 2, top + (bottom - top - sw * S.H / S.W) / 2, sw)
            } else {
                val sw = minOf(w - 40f, 420f)
                scene(g, shapes, dark, (w - sw) / 2, top + 8f, sw)
                val cw = minOf(w - 40f, 560f)
                block((w - cw) / 2, top + 8f + sw * S.H / S.W + 16f, cw, true)
            }
            // Dolny pasek (nad zawartością, jak w aplikacji przewijana treść znika pod nim)
            g.color = col(p.bg); g.fill(Rectangle2D.Float(0f, bottom, w.toFloat(), navH))
            g.color = col(p.line); g.fill(Rectangle2D.Float(0f, bottom, w.toFloat(), 1f))
            val by = bottom + 10f + 24f
            fun btn(label: String, right: Float, filled: Boolean): Float {
                val f = font(13.5f, java.awt.Font.BOLD); val bw = g.getFontMetrics(f).stringWidth(label) + 48f
                val x = right - bw
                if (filled) { g.color = col(accent); g.fill(java.awt.geom.RoundRectangle2D.Float(x, by - 20f, bw, 40f, 40f, 40f)) }
                g.font = f; g.color = col(if (filled) p.onAccent else p.accentText); g.drawString(label, x + 24f, by + 5f)
                return x
            }
            if (!st.first) { g.font = font(13.5f, java.awt.Font.BOLD); g.color = col(p.accentText); g.drawString("Wstecz", 36f, by + 5f) }
            when {
                st.path == G.HUB || pg.sample == G.Sample.DONE -> btn("Zacznij pisać", w - 12f, true)
                else -> {
                    val x = btn(if (st.last) "Gotowe" else "Dalej", w - 12f, true)
                    btn(if (st.path == G.START) "Pomiń przewodnik" else "Wróć do tematów", x - 8f, false)
                }
            }
            g.dispose()
            return img
        }
        val out = File(dir, "przewodnik-ustawienia"); out.mkdirs()
        val states = ArrayList<Pair<String, G.State>>()
        var s0 = G.State(); repeat(G.START_PAGES.size) { states += "start-${it + 1}-${s0.page.id}" to s0; s0 = s0.next() }
        var hub = s0
        for (t in G.TOPICS) { var s = hub.open(t.id); while (s.path == t.id) { states += "temat-${t.id}-${s.page.id}" to s; s = s.next() } ; hub = s }
        for ((name, st) in states) {
            val store = Mem().also { m -> G.choose(st.page, st.page.recommended(m), m) }
            for (dark in listOf(false, true)) {
                val th = if (dark) "ciemny" else "jasny"
                ImageIO.write(page(st, store, dark, 1280, 800, 1f), "png", File(out, "$name-tablet-$th.png"))
                if (name.startsWith("start")) ImageIO.write(page(st, store, dark, 400, 840, 1.5f), "png", File(out, "$name-telefon-$th.png"))
            }
        }
        // Tablet pionowo i telefon poziomo — po jednej stronie z najdłuższym opisem.
        val th = states.first { it.second.page.id == "theme" }.second
        val ts = Mem().also { G.choose(th.page, 0, it) }
        ImageIO.write(page(th, ts, false, 800, 1280, 1f), "png", File(out, "pion-tablet-theme-jasny.png"))
        ImageIO.write(page(th, ts, true, 840, 400, 1.5f), "png", File(out, "poziom-telefon-theme-ciemny.png"))
    }

    @Test fun render() {
        val dir = System.getenv("MNOTES_PREVIEW_DIR") ?: return assumeTrue(false)
        System.setProperty("java.awt.headless", "true")
        File(dir).mkdirs()
        setupSheet(dir)
        guideSheet(dir)
        glyphSheet(dir)
        toolSheet(dir)
        geoSheet(dir)
        themeSheet(dir)
        curveSheet(dir)

        // 1) Papier: wycinki 3 wzorów na białym/kremowym papierze, skala jak na tablecie (~2,7 px/pt).
        val tiles = listOf(
            PageSpec(paper = Paper.WHITE, template = Template.LINED),
            PageSpec(paper = Paper.IVORY, template = Template.GRID),
            PageSpec(paper = Paper.WHITE, template = Template.DOTS),
            PageSpec(paper = Paper.IVORY, template = Template.LINED),
            PageSpec(paper = Paper.DARK, template = Template.LINED),
            PageSpec(paper = Paper.BLACK, template = Template.GRID),
        )
        val s = 2.7f; val w = 150; val h = 110
        val sheet = BufferedImage((w * s).toInt() * 2 + 30, (h * s).toInt() * 3 + 40, BufferedImage.TYPE_INT_ARGB)
        val sg = g2(sheet); sg.color = Color(0x70, 0x70, 0x70); sg.fillRect(0, 0, sheet.width, sheet.height)
        tiles.forEachIndexed { i, sp ->
            sg.drawImage(paper(sp, s, w, h), 10 + (i % 2) * ((w * s).toInt() + 10), 10 + (i / 2) * ((h * s).toInt() + 10), null)
        }
        ImageIO.write(sheet, "png", File(dir, "paper.png"))

        // 2) Pióra na białym i kremowym papierze.
        val kinds = listOf(PenKind.BALLPOINT to 0xFF1B1D21, PenKind.FOUNTAIN to 0xFF24407E, PenKind.PENCIL to 0xFF454A52, PenKind.BRUSH to 0xFF3E8E5A)
        for ((name, spec) in listOf("white" to PageSpec(paper = Paper.WHITE, template = Template.LINED), "ivory" to PageSpec(paper = Paper.IVORY, template = Template.GRID))) {
            val sc = 3.2f; val ww = 260; val hh = 60 * kinds.size + 10
            val img = paper(spec, sc, ww, hh)
            val g = g2(img); g.scale(sc.toDouble(), sc.toDouble())
            kinds.forEachIndexed { i, (k, c) ->
                val ps = PenSpec.of(k)
                for (j in 0..1) {
                    val wd = if (j == 0) ps.defaultWidth else ps.defaultWidth * 0.55f
                    val pts = sample(k, c.toInt(), wd, 12f, 35f + i * 60f + j * 4f - 4f, if (j == 0) 260f else 700f, i * 3 + j)
                    stroke(g, k, c.toInt(), wd, pts, pts.size / 3)
                }
            }
            g.dispose()
            ImageIO.write(img, "png", File(dir, "pens_$name.png"))
        }
    }
}
