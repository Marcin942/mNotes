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
        // 2c-A9: czerń do 0,83 (granat ok. 0,92 — muszą się różnić), dalej jasna, ale nie biała jak ekran.
        assertTrue("czarne pismo jasne, ale nie białe jak ekran: ${L(ink)}", L(ink) in 0.80..0.95)
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

    /** 2c-A2: pole tekstu z pastelowym tłem — po zamianie na ciemne i z powrotem tekst dalej czytelny. */
    @Test fun textOnPastelFillStaysReadable() {
        val pastels = listOf(0xFFFFF59D, 0xFFF8BBD0, 0xFFBBDEFB, 0xFFC8E6C9, 0xFFD1C4E9, 0xFFFFE0B2, 0xFFB2EBF2, 0xFFFFCDD2).map { it.toInt() }
        // czarny, granat, czerwony, zielony, niebieski, fioletowy
        val inks = listOf(0xFF000000, 0xFF1A237E, 0xFFD32F2F, 0xFF2E7D32, 0xFF1976D2, 0xFF7B1FA2).map { it.toInt() }
        val back = toDark.reverse()
        var oldWorst = 21.0
        for (bg in pastels) for (ink in inks) {
            val need = minOf(ThemeMap.TEXT_CONTRAST, ThemeMap.contrastWcag(ink, bg))
            val dBg = toDark.surface(bg); val dInk = toDark.textOn(ink, bg)
            val dark = ThemeMap.contrastWcag(dInk, dBg)
            assertTrue("ciemne: tło %08x, tekst %08x, kontrast %.2f".format(bg, ink, dark), dark >= need - 0.01)
            assertTrue("tło pola ciemne jak papier: ${L(dBg)}", L(dBg) < 0.45)
            assertTrue("odcień tła zostaje", hueDiff(bg, dBg) < 12.0)
            val lBg = back.surface(dBg); val lInk = back.textOn(dInk, dBg)
            val light = ThemeMap.contrastWcag(lInk, lBg)
            assertTrue("z powrotem: tło %08x, tekst %08x, kontrast %.2f".format(bg, ink, light), light >= need - 0.01)
            assertTrue("tło po powrocie jasne: ${L(lBg)}", L(lBg) > 0.8)
            oldWorst = minOf(oldWorst, ThemeMap.contrastWcag(toDark.ink(ink), toDark.ink(bg)))
        }
        // Przed poprawką tło szło przez ink(): pastel zostawał jasny, a tekst na nim jaśniał.
        assertTrue("dawny najgorszy kontrast %.2f".format(oldWorst), oldWorst < 2.0)
    }

    @Test fun wcagContrastMatchesReference() {
        assertEquals(21.0, ThemeMap.contrastWcag(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, ThemeMap.contrastWcag(0xFF777777.toInt(), 0xFF777777.toInt()), 1e-9)
        // #767676 na białym to znana granica AA (4,54:1).
        assertEquals(4.54, ThemeMap.contrastWcag(0xFF767676.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(ThemeMap.contrastWcag(0xFF1976D2.toInt(), 0xFFFFF59D.toInt()), ThemeMap.contrastWcag(0xFFFFF59D.toInt(), 0xFF1976D2.toInt()), 1e-12)
    }

    // — 2c-A9 —

    private fun dE(a: Int, b: Int): Double { val x = ThemeMap.oklab(a); val y = ThemeMap.oklab(b); return kotlin.math.sqrt((x[0] - y[0]) * (x[0] - y[0]) + (x[1] - y[1]) * (x[1] - y[1]) + (x[2] - y[2]) * (x[2] - y[2])) }

    @Test fun penPaletteSurvivesTheRoundTrip() {
        val back = toDark.reverse()
        for (c in PEN) assertTrue("%08X → ΔE %.4f".format(c, dE(c, back.ink(toDark.ink(c)))), dE(c, back.ink(toDark.ink(c))) < 0.01)
    }

    @Test fun navyAndBlackStayApartOnDark() {
        val navy = toDark.ink(0xFF1A237E.toInt()); val black = toDark.ink(0xFF000000.toInt())
        assertTrue(abs(L(navy) - L(black)) >= 0.08 || ThemeMap.contrastWcag(navy, black) >= 1.5)
    }

    @Test fun whiteInkOnALightPageStaysVisibleOnDark() {
        val w = toDark.ink(0xFFFFFFFF.toInt())
        assertTrue("WCAG ${ThemeMap.contrastWcag(w, toDark.dstPaper)}", ThemeMap.contrastWcag(w, toDark.dstPaper) >= 4.5)
        // I odwrotnie: czarna kreska na czarnym papierze w trybie jasnym.
        val toLight = ThemeMap.transform(Paper.BLACK, toDark = false)
        assertTrue(ThemeMap.contrastWcag(toLight.ink(0xFF000000.toInt()), toLight.dstPaper) >= 4.5)
    }

    // — 3c-A1b: prawie biały i prawie czarny —

    private val toLight = ThemeMap.transform(0xFF202124.toInt(), toDark = false)

    @Test fun nearWhiteWrittenOnDarkIsStoredBlack() {
        for (c in intArrayOf(0xFFFFFFFF.toInt(), 0xFFF2F2F2.toInt(), 0xFFFAFAF0.toInt())) {
            val stored = toDark.native(c)
            assertEquals("%08X".format(c), 0xFF000000.toInt(), stored)
            // W jasnym (bez przekształcenia) i w eksporcie kreska jest czarna, w ciemnym — jasna jak zwykła czerń.
            assertTrue(ThemeMap.contrastWcag(stored, white) >= 4.5)
            assertTrue(ThemeMap.contrastWcag(toDark.ink(stored), toDark.dstPaper) >= 4.5)
        }
        assertEquals(0x80000000.toInt(), toDark.native(0x80FFFFFF.toInt()))   // krycie zostaje
    }

    @Test fun nearBlackWrittenOnLightIsStoredWhite() {
        for (c in intArrayOf(0xFF000000.toInt(), 0xFF1A1A1A.toInt())) {
            assertEquals("%08X".format(c), 0xFFFFFFFF.toInt(), toLight.native(c))
            assertEquals(0xFFFFFFFF.toInt(), ThemeMap.transform(Paper.BLACK, false).native(c))
        }
        assertTrue(ThemeMap.contrastWcag(toLight.ink(toLight.native(0xFF1A1A1A.toInt())), toLight.dstPaper) >= 4.5)
    }

    @Test fun colouredAndOrdinaryInksAreStoredAsPicked() {
        // Na jasnej stronie w ciemnym: czerń, szarości, kolory i pastele (wyraźnie barwne) bez zmian.
        for (c in PEN + intArrayOf(0xFF1A1A1A.toInt(), 0xFF808080.toInt(), 0xFFC0C0C0.toInt(), 0xFFFFF59D.toInt(), 0xFFFFCDD2.toInt(), 0xFFBBDEFB.toInt()))
            assertEquals("%08X".format(c), c, toDark.native(c))
        // Na ciemnej w jasnym: biel i jasne kolory bez zmian.
        for (c in intArrayOf(0xFFFFFFFF.toInt(), 0xFFF2F2F2.toInt(), 0xFF4D4D4D.toInt(), 0xFFE53935.toInt(), 0xFF1A237E.toInt()))
            assertEquals("%08X".format(c), c, toLight.native(c))
    }

    @Test fun oldNearWhiteStrokesStayVisibleOnDark() {
        // Stare zeszyty: #F2F2F2 zapisany na jasnej stronie w trybie ciemnym zlewał się z ciemnym papierem (L ≈ 0,26).
        for (c in intArrayOf(0xFFF2F2F2.toInt(), 0xFFFAFAF0.toInt(), 0xFFFFFFFF.toInt()))
            assertTrue("%08X".format(c), ThemeMap.contrastWcag(toDark.ink(c), toDark.dstPaper) >= 4.5)
        assertTrue(ThemeMap.contrastWcag(toLight.ink(0xFF1A1A1A.toInt()), toLight.dstPaper) >= 4.5)
    }

    @Test fun elementsKeepHighlighterAndConvertTheRest() {
        val pts = floatArrayOf(0f, 0f, 1f, 10f, 10f, 1f)
        val wh = 0xFFF2F2F2.toInt(); val bk = 0xFF000000.toInt()
        val pen = toDark.native(com.mdev.mnotes.core.model.Stroke(1, com.mdev.mnotes.core.model.PenKind.BALLPOINT, wh, 2f, pts, fill = wh)) as com.mdev.mnotes.core.model.Stroke
        assertEquals(bk, pen.color); assertEquals(bk, pen.fill)
        val hl = com.mdev.mnotes.core.model.Stroke(2, com.mdev.mnotes.core.model.PenKind.HIGHLIGHTER, wh, 8f, pts)
        assertTrue(toDark.native(hl) === hl)
        val red = com.mdev.mnotes.core.model.Stroke(3, com.mdev.mnotes.core.model.PenKind.BALLPOINT, 0xFFE53935.toInt(), 2f, pts)
        assertTrue(toDark.native(red) === red)
        val text = com.mdev.mnotes.core.model.TextBox(4, 0f, 0f, 50f, 20f, 0f, "a", 12f, wh, fill = wh)
        val t = toDark.native(text) as com.mdev.mnotes.core.model.TextBox
        assertEquals(bk, t.color); assertEquals(bk, t.fill); assertEquals("a", t.text)
        val fill = com.mdev.mnotes.core.model.FillEl(5, wh, false, listOf(floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f)))
        assertEquals(bk, (toDark.native(fill) as com.mdev.mnotes.core.model.FillEl).color)
    }

    private companion object {
        /** Typowe kolory pióra: czerń, granat, niebieski, czerwony, zielony, fiolet, pomarańcz, brąz. */
        val PEN = intArrayOf(0xFF000000.toInt(), 0xFF1A237E.toInt(), 0xFF1565C0.toInt(), 0xFFE53935.toInt(), 0xFF2E7D32.toInt(), 0xFF6A1B9A.toInt(), 0xFFEF6C00.toInt(), 0xFF5D4037.toInt())
    }
}
