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
        g.stroke = BasicStroke(lw * 1.7f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
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
            val p = Path2D.Float(Path2D.WIND_NON_ZERO); p.moveTo(fl.a[0], fl.a[1])
            var i = 2
            while (i + 1 < fl.size) { p.lineTo(fl.a[i], fl.a[i + 1]); i += 2 }
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

    @Test fun render() {
        val dir = System.getenv("MNOTES_PREVIEW_DIR") ?: return assumeTrue(false)
        System.setProperty("java.awt.headless", "true")
        File(dir).mkdirs()

        // 1) Papier: wycinki 3 wzorów na białym/kremowym papierze, skala jak na tablecie (~2,7 px/pt).
        val tiles = listOf(
            PageSpec(paper = Paper.WHITE, template = Template.LINED),
            PageSpec(paper = Paper.IVORY, template = Template.GRID),
            PageSpec(paper = Paper.WHITE, template = Template.DOTS),
            PageSpec(paper = Paper.IVORY, template = Template.LINED),
        )
        val s = 2.7f; val w = 150; val h = 110
        val sheet = BufferedImage((w * s).toInt() * 2 + 30, (h * s).toInt() * 2 + 30, BufferedImage.TYPE_INT_ARGB)
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
