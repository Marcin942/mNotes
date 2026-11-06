package com.mdev.mnotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class CoverScienceTest {
    private val science = listOf(CoverStyle.INTEGRALS, CoverStyle.MOLECULES, CoverStyle.DECAY)
    private val ratios = listOf(1.2f, 4f / 3f, 1.5f)

    @Test
    fun otherStylesAreEmpty() {
        for (s in CoverStyle.PLAIN..CoverStyle.CONSTELLATIONS) {
            val lay = CoverScience.layout(s, 4f / 3f)
            assertTrue("$s", lay.polys.isEmpty() && lay.discs.isEmpty())
        }
    }

    @Test
    fun everyScienceStyleDrawsSomething() {
        for (s in science) {
            val lay = CoverScience.layout(s, 4f / 3f)
            assertTrue("$s: łamane", lay.polys.size >= 5)
            assertTrue("$s: wzór nie jest za gęsty", lay.polys.size + lay.discs.size < 120)
        }
    }

    @Test
    fun patternsStayOffTheSpineAndInsideTheCover() {
        for (s in science) for (ar in ratios) {
            val lay = CoverScience.layout(s, ar)
            val eps = 0.003f
            fun check(x: Float, y: Float, what: String) {
                assertTrue("$s/$ar $what: x=$x poza okładką lub w grzbiecie", x >= CoverScience.SPINE - eps && x <= 1f + eps)
                assertTrue("$s/$ar $what: y=$y poza okładką", y >= -eps && y <= ar + eps)
            }
            for (p in lay.polys) { var k = 0; while (k < p.pts.size) { check(p.pts[k], p.pts[k + 1], "łamana"); k += 2 } }
            for (d in lay.discs) { check(d.x - d.r, d.y, "koło"); check(d.x + d.r, d.y, "koło"); check(d.x, d.y - d.r, "koło"); check(d.x, d.y + d.r, "koło") }
        }
    }

    @Test
    fun layoutIsDeterministicAndCached() {
        for (s in science) assertTrue(CoverScience.layout(s, 4f / 3f) === CoverScience.layout(s, 4f / 3f))
        assertEquals(CoverScience.layout(CoverStyle.MOLECULES, 1.33f).polys.size, CoverScience.layout(CoverStyle.MOLECULES, 1.34f).polys.size)
    }

    @Test
    fun styleCodesAreStable() {
        assertEquals(10, CoverStyle.INTEGRALS); assertEquals(11, CoverStyle.MOLECULES); assertEquals(12, CoverStyle.DECAY)
        assertEquals(13, CoverStyle.COUNT)
    }

    /**
     * Podgląd (nie test): wzory na kilku kolorach okładek, jasnych i ciemnych, jak `CoverArt` (bez zawiasu).
     * Włącza go plik `build/podglad-nauka.on`; wynik w `build/podglad-nauka.png`.
     */
    @Test
    fun preview() {
        assumeTrue(File("build/podglad-nauka.on").exists())
        val colors = listOf(0xFF6B8F7B, 0xFF2F4468, 0xFFB5694F, 0xFFC9A05A, 0xFF7B5E4B, 0xFF35594A, 0xFF8CA3B3, 0xFF424850).map { it.toInt() }
        val cw = 216; val ch = 288; val gap = 20
        val img = BufferedImage(gap + colors.size * (cw + gap), gap + science.size * (ch + gap), BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.color = Color(0xF4F2EC); g.fillRect(0, 0, img.width, img.height)
        for ((row, style) in science.withIndex()) for ((k, c) in colors.withIndex()) {
            val x0 = gap + k * (cw + gap); val y0 = gap + row * (ch + gap)
            val gg = g.create(x0, y0, cw, ch) as java.awt.Graphics2D
            gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            gg.clip(RoundRectangle2D.Float(0f, 0f, cw.toFloat(), ch.toFloat(), 12f, 12f))
            gg.paint = GradientPaint(0f, 0f, mix(c, 0xFFFFFF, 0.10f), 0f, ch.toFloat(), mix(c, 0x000000, 0.16f)); gg.fillRect(0, 0, cw, ch)
            val base = Color(c)
            val light = (0.2126 * lin(base.red) + 0.7152 * lin(base.green) + 0.0722 * lin(base.blue)) > 0.4
            val ink = if (light) 0 else 0xFFFFFF
            val top = if (light) 0.20f else 0.30f
            val lay = CoverScience.layout(style, ch.toFloat() / cw)
            fun col(a: Float) = Color(ink or ((top * a * 255).toInt() shl 24), true)
            for (p in lay.polys) {
                val path = Path2D.Float(); path.moveTo(p.pts[0] * cw, p.pts[1] * cw)
                var i = 2; while (i < p.pts.size) { path.lineTo(p.pts[i] * cw, p.pts[i + 1] * cw); i += 2 }
                if (p.closed) path.closePath()
                gg.color = col(p.a)
                if (p.fill) gg.fill(path)
                else {
                    val wd = maxOf(p.width * cw, 0.7f)
                    gg.stroke = if (p.dash) BasicStroke(wd, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, floatArrayOf(cw * 0.018f, cw * 0.014f), 0f)
                    else BasicStroke(wd, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    gg.draw(path)
                }
            }
            for (d in lay.discs) {
                gg.color = col(d.a)
                val r = d.r * cw
                val e = Ellipse2D.Float(d.x * cw - r, d.y * cw - r, 2 * r, 2 * r)
                if (d.fill) gg.fill(e) else { gg.stroke = BasicStroke(maxOf(d.width * cw, 0.7f)); gg.draw(e) }
            }
            gg.color = Color(0xF2FBF8F1.toInt(), true)
            gg.fill(RoundRectangle2D.Float(cw * 0.15f, ch * 0.30f, cw * 0.78f, ch * 0.18f, 5f, 5f))
            gg.color = Color(0, 0, 0, 40); gg.fillRect(0, 0, (cw * 0.075f).toInt(), ch)
            gg.dispose()
        }
        g.dispose()
        ImageIO.write(img, "png", File("build/podglad-nauka.png"))
    }

    private fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }

    private fun mix(c: Int, to: Int, t: Float): Color {
        fun ch(sh: Int) = (((c shr sh) and 255) * (1 - t) + ((to shr sh) and 255) * t).toInt()
        return Color(ch(16), ch(8), ch(0))
    }
}
