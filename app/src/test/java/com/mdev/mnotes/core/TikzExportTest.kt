package com.mdev.mnotes.core

import com.mdev.mnotes.core.export.TikzExport
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.pen.LineStyle
import com.mdev.mnotes.core.shapes.Bezier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** FB13: eksport TikZ — kształty jako to, czym są, krzywe z uchwytami, pismo uproszczone, tekst z powrotem w LaTeX-u. */
class TikzExportTest {
    private val black = 0xFF000000.toInt()

    private fun triples(vararg xy: Float) = FloatArray(xy.size / 2 * 3) { i -> if (i % 3 == 2) 1f else xy[i / 3 * 2 + i % 3] }

    /** Elipsa jak z autokorekty: 49 punktów, punkt 0 na osi szerokości, co 1/4 kolejna oś; [rot] w układzie strony (y w dół). */
    private fun ellipse(cx: Float, cy: Float, hw: Float, hh: Float, rot: Double = 0.0): Stroke {
        val pts = FloatArray(49 * 3)
        for (i in 0..48) {
            val t = 2 * Math.PI * (i % 48) / 48
            val a = hw * cos(t); val b = hh * sin(t)
            pts[3 * i] = (cx + a * cos(rot) - b * sin(rot)).toFloat()
            pts[3 * i + 1] = (cy + a * sin(rot) + b * cos(rot)).toFloat()
            pts[3 * i + 2] = 1f
        }
        return Stroke(Ids.next(), PenKind.BALLPOINT, black, 1f, pts, ellipse = true)
    }

    private fun code(vararg els: com.mdev.mnotes.core.model.Element, freehand: Boolean = true) = TikzExport.picture(els.toList(), freehand).code

    @Test fun numbersAreShortAndLocaleFree() {
        assertEquals("1", TikzExport.num(1.0))
        assertEquals("1.23", TikzExport.num(1.234))
        assertEquals("2.5", TikzExport.num(2.5))
        assertEquals("0", TikzExport.num(-0.001))
        assertEquals("-3.1", TikzExport.num(-3.1))
        assertEquals("1.27", TikzExport.num(36 * TikzExport.CM))
    }

    @Test fun textGoesBackToLatex() {
        assertEquals("\$\\alpha\$ + \$\\beta\$", TikzExport.latexText("α + β"))
        assertEquals("\$\\alpha\\beta\$", TikzExport.latexText("αβ"))
        assertEquals("x\$^{2}\$ + a\$_{12}\$", TikzExport.latexText("x² + a₁₂"))
        assertEquals("30\$^\\circ\$, \$\\surd\$2, \$\\epsilon\$", TikzExport.latexText("30°, √2, ε"))
        assertEquals("x\$^{10}\\leq\$ 5", TikzExport.latexText("x¹⁰≤ 5"))
        // Znaki specjalne LaTeX-a jako zwykły tekst; polskie litery zostają (pdflatex z T1 i UTF-8).
        assertEquals("50\\% \\& \\#1\\_a \\{b\\} \\textbackslash{}c \\^{}\\~{} \\\$ żółć", TikzExport.latexText("50% & #1_a {b} \\c ^~ \$ żółć"))
    }

    @Test fun circleEllipseAndRotationKeepTheirForm() {
        assertTrue(code(ellipse(100f, 100f, 36f, 36f)).contains("\\draw[black, line width=1pt] (1.27,1.27) circle [radius=1.27];"))
        val e = code(ellipse(100f, 100f, 72f, 36f))
        assertTrue(e, e.contains("(2.54,1.27) ellipse [x radius=2.54, y radius=1.27];"))
        assertFalse(e.contains("rotate"))
        // Obrót o 30° przeciwnie do wskazówek zegara na ekranie: na stronie (y w dół) to −30°, w TikZ (y w górę) +30°.
        val r = code(ellipse(100f, 100f, 72f, 36f, -Math.PI / 6))
        assertTrue(r, r.contains("rotate around={30:"))
    }

    @Test fun rectangleTriangleAndOpenPolyline() {
        val rect = Stroke(Ids.next(), PenKind.BALLPOINT, black, 2f, triples(0f, 0f, 72f, 0f, 72f, 36f, 0f, 36f, 0f, 0f), poly = true)
        assertTrue(code(rect).contains("\\draw[black, line width=2pt] (0,1.27) rectangle (2.54,0);"))
        val tri = Stroke(Ids.next(), PenKind.BALLPOINT, black, 2f, triples(0f, 72f, 72f, 72f, 36f, 0f, 0f, 72f), poly = true)
        assertTrue(code(tri).contains("(0,0) -- (2.54,0) -- (1.27,2.54) -- cycle;"))
        val open = Stroke(Ids.next(), PenKind.BALLPOINT, black, 2f, triples(0f, 0f, 72f, 0f, 72f, 72f), poly = true, line = LineStyle.DASHED)
        val c = code(open)
        assertTrue(c, c.contains("dashed] (0,2.54) -- (2.54,2.54) -- (2.54,0);"))
        assertFalse(c.contains("cycle"))
    }

    @Test fun bezierKeepsHandlesAndClosedCurveFills() {
        val ctrl = floatArrayOf(0f, 0f, 24f, -24f, 48f, -24f, 72f, 0f)
        val s = Stroke(Ids.next(), PenKind.BALLPOINT, black, 1.5f, Bezier.sample(ctrl), ctrl = ctrl)
        assertTrue(code(s).contains("\\draw[black, line width=1.5pt] (0,0) .. controls (0.85,0.85) and (1.69,0.85) .. (2.54,0);"))
        val loop = floatArrayOf(0f, 0f, 30f, -30f, 60f, -30f, 72f, 0f, 60f, 30f, 30f, 30f, 0f, 0f)
        val f = Stroke(Ids.next(), PenKind.BALLPOINT, 0xFF1E88E5.toInt(), 1f, Bezier.sample(loop), ctrl = loop, fill = 0xFFFDD835.toInt())
        val c = code(f)
        assertTrue(c, c.contains("\\filldraw[mn1, line width=1pt, fill=mn2, fill opacity=0.28]"))
        assertTrue(c, c.contains(".. cycle;"))
        assertTrue(c.contains("\\definecolor{mn1}{HTML}{1E88E5}") && c.contains("\\definecolor{mn2}{HTML}{FDD835}"))
    }

    @Test fun freehandIsSimplifiedOrSkipped() {
        val xy = FloatArray(50 * 2) { i -> if (i % 2 == 0) i / 2 * 72f / 49 else 10f }
        val line = Stroke(Ids.next(), PenKind.BALLPOINT, black, 2f, FloatArray(150) { i -> if (i % 3 == 2) 0.5f else xy[i / 3 * 2 + i % 3] })
        val c = code(line)
        // 50 punktów na prostej → dwa końce; grubość ze średniego nacisku (2 pt × 0,5).
        assertTrue(c, c.contains("\\draw[black, line width=1pt] (0,0) -- (2.54,0);"))
        val dot = Stroke(Ids.next(), PenKind.BALLPOINT, 0xFFE53935.toInt(), 3f, triples(5f, 5f, 5.1f, 5f))
        assertTrue(code(dot).contains("\\fill[mn1] (0,0) circle [radius=1.5pt];"))
        val r = TikzExport.picture(listOf(line, ellipse(100f, 100f, 36f, 36f)), freehand = false)
        assertEquals(1, r.drawn)
        assertEquals(1, r.skipped)
        assertTrue(r.code.contains("circle [radius=1.27]"))
        assertFalse(r.code.contains("--"))
    }

    @Test fun colorsAreDefinedOnceAndHighlighterIsTranslucent() {
        val red = 0xFFE53935.toInt()
        val a = Stroke(Ids.next(), PenKind.BALLPOINT, red, 1f, triples(0f, 0f, 72f, 0f), poly = true)
        val b = Stroke(Ids.next(), PenKind.BALLPOINT, red, 1f, triples(0f, 36f, 72f, 36f), poly = true)
        val hl = Stroke(Ids.next(), PenKind.HIGHLIGHTER, 0xFFFDD835.toInt(), 14f, triples(0f, 72f, 72f, 72f), poly = true)
        val c = code(a, b, hl)
        assertEquals(1, Regex("definecolor\\{mn1\\}").findAll(c).count())
        assertEquals(2, Regex("\\\\draw\\[mn1,").findAll(c).count())
        assertTrue(c, c.contains("\\draw[mn2, line width=14pt, draw opacity="))
    }

    @Test fun textBoxBecomesNodeWithFontAndLines() {
        val t = TextBox(Ids.next(), 36f, 36f, 72f, 72f, 0f, "α = 30°\nzad. 2", 12f, black, style = TextBox.STYLE_BOLD)
        val c = code(t)
        assertTrue(c, c.contains("\\node[text=black, anchor=center, inner sep=0pt, align=left, text width=2.54cm, font=\\fontsize{12}{14.4}\\selectfont\\sffamily\\bfseries] at (1.27,1.27) {\$\\alpha\$ = 30\$^\\circ\$\\\\ zad. 2};"))
        val rot = code(TextBox(Ids.next(), 36f, 18f, 72f, 36f, (Math.PI / 2).toFloat(), "x", 10f, black))
        assertTrue(rot, rot.contains("rotate=-90"))
        assertEquals(0, TikzExport.picture(listOf(TextBox(Ids.next(), 0f, 0f, 10f, 10f, 0f, "  ", 10f, black))).drawn)
    }

    @Test fun simplifyKeepsEndsAndCorners() {
        val zig = floatArrayOf(0f, 0f, 10f, 5f, 20f, 0f, 30f, 5f)
        assertTrue(zig.contentEquals(TikzExport.simplify(zig, 0.35f)))
        val bent = floatArrayOf(0f, 0f, 5f, 0.1f, 10f, 0f, 10f, 5f, 10f, 10f)
        assertTrue(floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f).contentEquals(TikzExport.simplify(bent, 0.35f)))
        assertTrue(floatArrayOf(1f, 2f).contentEquals(TikzExport.simplify(floatArrayOf(1f, 2f), 0.35f)))
    }

    /** Przykład do obejrzenia bez tabletu (`docs/podglad/tikz-przyklad.tex`): tylko z MNOTES_PREVIEW_DIR, jak PreviewHarness. */
    @Test fun writesExampleWhenAsked() {
        val dir = System.getenv("MNOTES_PREVIEW_DIR") ?: return org.junit.Assume.assumeTrue(false)
        val blue = 0xFF1E88E5.toInt(); val red = 0xFFE53935.toInt()
        val tri = Stroke(Ids.next(), PenKind.BALLPOINT, black, 1.2f, triples(100f, 260f, 260f, 260f, 150f, 140f, 100f, 260f), poly = true)
        // Okrąg opisany na trójkącie: środek i promień policzone z wierzchołków (jak „Okrąg przez 3 punkty” w Geometrii).
        val (ax, ay, bx, by, cx, cy) = listOf(100.0, 260.0, 260.0, 260.0, 150.0, 140.0)
        val d = 2 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by))
        val ux = ((ax * ax + ay * ay) * (by - cy) + (bx * bx + by * by) * (cy - ay) + (cx * cx + cy * cy) * (ay - by)) / d
        val uy = ((ax * ax + ay * ay) * (cx - bx) + (bx * bx + by * by) * (ax - cx) + (cx * cx + cy * cy) * (bx - ax)) / d
        val rr = Math.hypot(ax - ux, ay - uy).toFloat()
        val circ = ellipse(ux.toFloat(), uy.toFloat(), rr, rr).let { Stroke(it.id, it.pen, blue, 1f, it.pts, ellipse = true, line = LineStyle.DASHED) }
        val wave = floatArrayOf(300f, 200f, 330f, 140f, 360f, 260f, 390f, 200f, 420f, 140f, 450f, 260f, 480f, 200f)
        val curve = Stroke(Ids.next(), PenKind.BALLPOINT, red, 1.5f, Bezier.sample(wave), ctrl = wave)
        val hand = Stroke(Ids.next(), PenKind.FOUNTAIN, black, 1.6f, FloatArray(40 * 3) { i ->
            val k = i / 3
            when (i % 3) { 0 -> 300f + k * 4.5f; 1 -> 290f + 6f * sin(k * 0.6f); else -> 0.7f }
        })
        val labels = listOf(
            TextBox(Ids.next(), 92f, 272f, 20f, 16f, 0f, "A", 11f, black), TextBox(Ids.next(), 268f, 272f, 20f, 16f, 0f, "B", 11f, black),
            TextBox(Ids.next(), 150f, 126f, 20f, 16f, 0f, "C", 11f, black), TextBox(Ids.next(), 125f, 250f, 30f, 14f, 0f, "α", 10f, blue),
            TextBox(Ids.next(), 390f, 300f, 180f, 30f, 0f, "y = sin x, x ∈ [0, 2π]\nzad. 3: a² + b² = c²", 10f, black),
        )
        val pic = TikzExport.picture(listOf(tri, circ, curve, hand) + labels).code
        File(dir).mkdirs()
        File(dir, "tikz-przyklad.tex").writeText(TikzExport.document(listOf(pic), "Przykład eksportu TikZ (FB13)"))
    }

    private operator fun <T> List<T>.component6() = this[5]

    @Test fun documentCompilesAsStandaloneWithPicturePerPage() {
        val p1 = TikzExport.picture(listOf(ellipse(100f, 100f, 36f, 36f))).code
        val p2 = TikzExport.picture(emptyList()).code
        val d = TikzExport.document(listOf(p1, p2), "Geometria\nwykład 3")
        assertTrue(d.startsWith("% Geometria wykład 3"))
        assertTrue(d.contains("\\documentclass[tikz,border=4pt]{standalone}"))
        assertEquals(2, Regex("\\\\begin\\{tikzpicture\\}").findAll(d).count())
        assertEquals(2, Regex("\\\\end\\{tikzpicture\\}").findAll(d).count())
        assertTrue(d.trimEnd().endsWith("\\end{document}"))
    }
}
