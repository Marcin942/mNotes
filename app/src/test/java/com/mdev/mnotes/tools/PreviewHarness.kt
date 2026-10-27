package com.mdev.mnotes.tools

import com.mdev.mnotes.core.geom.FloatList
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

    @Test fun render() {
        val dir = System.getenv("MNOTES_PREVIEW_DIR") ?: return assumeTrue(false)
        System.setProperty("java.awt.headless", "true")
        File(dir).mkdirs()
        glyphSheet(dir)
        toolSheet(dir)

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
