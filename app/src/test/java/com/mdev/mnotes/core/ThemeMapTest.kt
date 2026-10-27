package com.mdev.mnotes.core

import com.mdev.mnotes.core.color.ThemeMap
import com.mdev.mnotes.core.model.Paper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 2b-A9: tryb ciemny/jasny stron — funkcja koloru (OKLab), wybór przekształcenia i macierz dla PDF. */
class ThemeMapTest {
    private val white = Paper.WHITE
    private val toDark = ThemeMap.transform(white, toDark = true)
    private fun L(c: Int) = ThemeMap.oklab(c)[0]
    private fun hueDiff(a: Int, b: Int): Double { val d = abs(ThemeMap.hue(a) - ThemeMap.hue(b)) % 360; return if (d > 180) 360 - d else d }

    @Test fun modesPickTheTransform() {
        val dark = 0xFF1E1F22.toInt()
        assertNull(ThemeMap.forPage(white, inverted = false, mode = ThemeMap.MIXED))           // jak powstała
        assertTrue(ThemeMap.forPage(white, inverted = true, mode = ThemeMap.MIXED)!!.toDark)  // F11 na tej stronie
        assertTrue(ThemeMap.forPage(white, false, ThemeMap.DARK)!!.toDark)
        assertNull(ThemeMap.forPage(dark, false, ThemeMap.DARK))                              // już ciemna
        assertTrue(!ThemeMap.forPage(dark, false, ThemeMap.LIGHT)!!.toDark)
        assertNull(ThemeMap.forPage(white, true, ThemeMap.LIGHT))                             // tryb jasny wygrywa z flagą
    }

    @Test fun paperAndBlackInkSwapButNotToExtremes() {
        val paper = toDark.dstPaper
        assertEquals(0.24, L(paper), 0.01)
        val ink = toDark.ink(0xFF000000.toInt())
        assertTrue("czarne pismo jasne, ale nie białe jak ekran: ${L(ink)}", L(ink) in 0.85..0.95)
        // Z ciemnej strony do jasnej: biały atrament robi się ciemny.
        val back = ThemeMap.transform(0xFF202124.toInt(), toDark = false)
        assertTrue(L(back.ink(0xFFFFFFFF.toInt())) < 0.25)
        assertTrue(L(back.dstPaper) > 0.95)
    }

    @Test fun greysReverseMonotonically() {
        val greys = listOf(0xFF000000, 0xFF404040, 0xFF808080, 0xFFC0C0C0).map { it.toInt() }
        val mapped = greys.map { L(toDark.ink(it)) }
        for (i in 1 until mapped.size) assertTrue("szarości mają odwrócony porządek: $mapped", mapped[i] < mapped[i - 1])
    }

    @Test fun redStaysRedAndAtLeastAsReadable() {
        val red = 0xFFE53935.toInt()
        val m = toDark.ink(red)
        assertTrue(hueDiff(red, m) < 3.0)
        assertTrue(ThemeMap.contrast(m, toDark.dstPaper) >= ThemeMap.contrast(red, white) - 1e-3)
        assertEquals(L(red), L(m), 0.04)                    // jaśnieje tylko o tyle, ile wymaga kontrast (≈ 0,02)
    }

    @Test fun navyBrightensWithTheSameHue() {
        val navy = 0xFF1A237E.toInt()
        val m = toDark.ink(navy)
        assertTrue(L(m) > L(navy) + 0.3)
        assertTrue(hueDiff(navy, m) < 6.0)
        assertTrue(ThemeMap.contrast(m, toDark.dstPaper) >= ThemeMap.contrast(navy, white) - 0.02)
    }

    @Test fun yellowHighlighterDoesNotGlowOnDark() {
        val yellow = 0xFFFFEB3B.toInt()
        val m = toDark.highlighter(yellow)
        assertTrue("L = ${L(m)}", L(m) <= 0.61)
        assertTrue(hueDiff(yellow, m) < 8.0)
        // Na jasnej stronie (z ciemnej) zakreślacz staje się pastelem.
        assertTrue(L(ThemeMap.transform(0xFF202124.toInt(), false).highlighter(0xFF8E24AA.toInt())) >= 0.79)
    }

    @Test fun creamPaperBecomesWarmDarkAndAlphaIsKept() {
        val cream = ThemeMap.transform(Paper.IVORY, true).dstPaper
        val o = ThemeMap.oklab(cream)
        assertEquals(0.24, o[0], 0.01)
        assertTrue("ciepły nalot: b = ${o[2]}", o[2] > 0)
        val half = toDark.ink(0x80000000.toInt())
        assertEquals(0x80, half ushr 24)
    }

    @Test fun imageMatrixSendsWhiteToTheNewPaper() {
        val m = toDark.imageMatrix
        fun apply(r: Float, g: Float, b: Float) = FloatArray(3) { row -> m[5 * row] * r + m[5 * row + 1] * g + m[5 * row + 2] * b + m[5 * row + 4] }
        val w = apply(255f, 255f, 255f)
        val p = toDark.dstPaper
        assertEquals((p shr 16 and 255).toFloat(), w[0], 2f); assertEquals((p and 255).toFloat(), w[2], 2f)
        val k = apply(0f, 0f, 0f)
        assertEquals(232f, k[0], 2f); assertEquals(232f, k[1], 2f)
        assertEquals(1f, m[18], 0f)
    }

    @Test fun pipetteReverseIsClose() {
        val red = 0xFFE53935.toInt()
        val back = toDark.reverse().ink(toDark.ink(red))
        assertTrue(hueDiff(red, back) < 4.0)
        assertNotNull(toDark.reverse())
    }
}
