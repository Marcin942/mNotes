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
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** 3c-A14: gwiazdki na okładkach — stały układ, poza grzbietem, w granicach okładki; stare wzory bez zmian. */
class CoverStarsTest {
    private val starry = listOf(CoverStyle.STARS, CoverStyle.STARS_BIG, CoverStyle.CONSTELLATIONS)

    @Test
    fun newStylesAppendedWithoutRenumberingOldOnes() {
        assertEquals(6, CoverStyle.ARCS)
        assertEquals(listOf(7, 8, 9), starry)
        assertTrue(CoverStyle.COUNT >= 10)
        for (s in CoverStyle.PLAIN..CoverStyle.ARCS) assertTrue(CoverStars.layout(s).stars.isEmpty())
    }

    @Test
    fun starsStayOnTheCoverAndOffTheSpine() {
        for (style in starry) {
            val lay = CoverStars.layout(style)
            assertTrue("$style: za mało gwiazdek", lay.stars.size >= 15)
            for (s in lay.stars) {
                assertTrue("$style: grzbiet x=${s.x}", s.x - s.r >= CoverStars.SPINE - 0.01f)
                assertTrue("$style: prawa krawędź", s.x + s.r <= 1.01f)
                assertTrue("$style: y=${s.y}", s.y in 0f..1f)
                assertTrue(s.a in 0f..1f)
            }
        }
    }

    @Test
    fun layoutIsTheSameEveryTime() {
        for (style in starry) {
            val a = CoverStars.layout(style).stars
            val b = CoverStars.layout(style).stars
            assertEquals(a.map { it.x to it.y }, b.map { it.x to it.y })
        }
    }

    @Test
    fun constellationsAreDipperAndCassiopeia() {
        val lay = CoverStars.layout(CoverStyle.CONSTELLATIONS)
        assertEquals(7 + 4, lay.lines.size)
        assertEquals(12, lay.stars.count { it.shape == CoverStars.Shape.SPARK })
    }

    @Test
    fun outlineHasArmsAroundTheCentre() {
        val o = CoverStars.outline(CoverStars.Shape.STAR, 10f, 10f, 5f, 0f)
        assertEquals(20, o.size)
        assertEquals(10f, o[0], 1e-4f); assertEquals(5f, o[1], 1e-4f) // pierwsze ramię w górę
        assertEquals(16, CoverStars.outline(CoverStars.Shape.SPARK, 0f, 0f, 1f, 0f).size)
    }

    /**
     * Podgląd (nie test): okładki z gwiazdkami na jasnym i ciemnym tle biblioteki, jak `CoverArt` (bez zawiasu).
     * Włącza go plik `build/podglad-okladki.on`; wynik w `build/podglad-okladki.png`.
     */
    @Test
    fun preview() {
        assumeTrue(File("build/podglad-okladki.on").exists())
        val colors = listOf(0xFF6B8F7B, 0xFF2F4468, 0xFFB5694F, 0xFFC9A05A, 0xFFB5A58E, 0xFF424850).map { it.toInt() }
        val cw = 150; val ch = 200; val gap = 18
        val img = BufferedImage(gap + (colors.size * 2) * (cw + gap), gap + starry.size * (ch + gap), BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val half = img.width / 2
        g.color = Color(0xF4F2EC); g.fillRect(0, 0, half, img.height)
        g.color = Color(0x1C1D20); g.fillRect(half, 0, img.width - half, img.height)
        for ((row, style) in starry.withIndex()) for (k in 0 until colors.size * 2) {
            val c = colors[k % colors.size]
            val x0 = gap + k * (cw + gap); val y0 = gap + row * (ch + gap)
            val gg = g.create(x0, y0, cw, ch) as java.awt.Graphics2D
            gg.clip(RoundRectangle2D.Float(0f, 0f, cw.toFloat(), ch.toFloat(), 12f, 12f))
            gg.paint = GradientPaint(0f, 0f, mix(c, 0xFFFFFF, 0.10f), 0f, ch.toFloat(), mix(c, 0x000000, 0.16f)); gg.fillRect(0, 0, cw, ch)
            val base = Color(c)
            val lum = (0.2126 * lin(base.red) + 0.7152 * lin(base.green) + 0.0722 * lin(base.blue))
            val light = lum > 0.4
            val ink = if (light) 0 else 0xFFFFFF
            val top = if (light) 0.15f else 0.24f
            val lay = CoverStars.layout(style)
            gg.stroke = BasicStroke(maxOf(cw * 0.004f, 0.7f))
            gg.color = Color(ink or ((top * 0.6f * 255).toInt() shl 24), true)
            for (l in lay.lines) gg.draw(Line2D.Float(l[0] * cw, l[1] * ch, l[2] * cw, l[3] * ch))
            for (s in lay.stars) {
                gg.color = Color(ink or ((top * s.a * 255).toInt() shl 24), true)
                val r = maxOf(s.r * cw, 0.7f)
                if (s.shape == CoverStars.Shape.DOT) gg.fill(Ellipse2D.Float(s.x * cw - r, s.y * ch - r, 2 * r, 2 * r))
                else {
                    val o = CoverStars.outline(s.shape, s.x * cw, s.y * ch, r, s.rot)
                    val p = Path2D.Float(); p.moveTo(o[0], o[1])
                    var i = 2; while (i < o.size) { p.lineTo(o[i], o[i + 1]); i += 2 }
                    p.closePath(); gg.fill(p)
                }
            }
            // etykieta tytułu jak w NotebookCover
            gg.color = Color(0xF2FBF8F1.toInt(), true)
            gg.fill(RoundRectangle2D.Float(cw * 0.15f, ch * 0.30f, cw * 0.78f, ch * 0.16f, 5f, 5f))
            gg.color = Color(0, 0, 0, 40); gg.fillRect(0, 0, (cw * 0.075f).toInt(), ch)
            gg.dispose()
        }
        g.dispose()
        ImageIO.write(img, "png", File("build/podglad-okladki.png"))
    }

    private fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }

    private fun mix(c: Int, to: Int, t: Float): Color {
        fun ch(sh: Int) = (((c shr sh) and 255) * (1 - t) + ((to shr sh) and 255) * t).toInt()
        return Color(ch(16), ch(8), ch(0))
    }
}
