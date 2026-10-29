package com.mdev.mnotes.core

import com.mdev.mnotes.core.color.ThemeMap
import com.mdev.mnotes.core.export.TikzExport
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.Paper
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3c-A5: eksport „jak powstały / jasny / ciemny” — wybór trybu, kolory elementów w SVG/TikZ i tło TikZ. */
class ExportThemeTest {
    private val white = Paper.WHITE
    private val dark = 0xFF1E1F22.toInt()
    private val black = 0xFF000000.toInt()
    private val red = 0xFFD32F2F.toInt()
    private fun L(c: Int) = ThemeMap.oklab(c)[0]
    private fun stroke(color: Int, pen: PenKind = PenKind.FOUNTAIN, fill: Int = 0) =
        Stroke(Ids.next(), pen, color, 2f, floatArrayOf(10f, 10f, 1f, 50f, 20f, 1f, 40f, 60f, 1f), fill = fill)

    @Test
    fun savedModeWinsThenLegacyToggleThenAsMade() {
        assertEquals(ThemeMap.DARK, ThemeMap.exportMode(ThemeMap.DARK, legacyAsScreen = false, pageTheme = ThemeMap.LIGHT))
        assertEquals(ThemeMap.MIXED, ThemeMap.exportMode(ThemeMap.MIXED, legacyAsScreen = true, pageTheme = ThemeMap.DARK))
        assertEquals("dawne „jak na ekranie” → tryb ekranu", ThemeMap.DARK, ThemeMap.exportMode(-1, true, ThemeMap.DARK))
        assertEquals(ThemeMap.MIXED, ThemeMap.exportMode(-1, false, ThemeMap.DARK))
        assertEquals("nieznana wartość z nowszej wersji", ThemeMap.MIXED, ThemeMap.exportMode(7, false, ThemeMap.LIGHT))
    }

    /** „Jak powstały”: strona bez F11 nie jest ruszana w żadnym eksporcie; ciemny tryb zamienia jasną, jasny — ciemną. */
    @Test
    fun modesPerPage() {
        assertEquals(null, ThemeMap.forPage(white, false, ThemeMap.MIXED))
        assertEquals(null, ThemeMap.forPage(dark, false, ThemeMap.MIXED))
        assertTrue(ThemeMap.forPage(white, false, ThemeMap.DARK)!!.toDark)
        assertFalse(ThemeMap.forPage(dark, false, ThemeMap.LIGHT)!!.toDark)
    }

    @Test
    fun vectorElementsGetTheSameColoursAsTheScreen() {
        val t = ThemeMap.transform(white, toDark = true)
        val ink = t.shown(stroke(black)) as Stroke
        assertEquals(t.ink(black), ink.color)
        assertTrue("czarne pismo jasne na ciemnym", L(ink.color) > 0.75)
        val hl = t.shown(stroke(0xFFFFEB3B.toInt(), PenKind.HIGHLIGHTER)) as Stroke
        assertEquals(t.highlighter(0xFFFFEB3B.toInt()), hl.color)
        assertTrue("zakreślacz nie świeci", L(hl.color) <= 0.61)
        val filled = t.shown(stroke(red, fill = red)) as Stroke
        assertEquals(t.highlighter(red), filled.fill)
        assertEquals(ink.pts.toList(), (t.shown(stroke(black)) as Stroke).pts.toList())

        val box = TextBox(Ids.next(), 50f, 50f, 100f, 20f, 0f, "abc", 12f, black, fill = 0xFFFFF59D.toInt())
        val tb = t.shown(box) as TextBox
        assertEquals(t.surface(box.fill), tb.fill)
        assertTrue("tekst czytelny na zamienionym tle", ThemeMap.contrastWcag(tb.color, tb.fill) >= ThemeMap.TEXT_CONTRAST - 0.01)
        assertEquals("abc", tb.text)

        val bucket = FillEl(Ids.next(), 0xFFBBDEFB.toInt(), false, listOf(floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f)))
        assertEquals(t.surface(bucket.color), (t.shown(bucket) as FillEl).color)
    }

    @Test
    fun lightModeTurnsWhiteInkOnDarkPageDark() {
        val t = ThemeMap.transform(dark, toDark = false)
        val s = t.shown(stroke(0xFFFFFFFF.toInt())) as Stroke
        assertTrue(L(s.color) < 0.3)
        assertNotEquals("kolor barwny zostaje barwny", 0.0, ThemeMap.oklab((t.shown(stroke(red)) as Stroke).color).let { kotlin.math.hypot(it[1], it[2]) }, 0.05)
    }

    @Test
    fun tikzDarkPageGetsBackgroundFirst() {
        val els = listOf(stroke(0xFFE0E0E0.toInt()))
        val plain = TikzExport.picture(els).code
        assertFalse(plain.contains("mnpaper"))
        val withPaper = TikzExport.picture(els, paper = dark).code
        assertTrue(withPaper.contains("\\definecolor{mnpaper}{HTML}{1E1F22}"))
        val fill = withPaper.indexOf("\\fill[mnpaper]")
        assertTrue("tło przed rysunkiem", fill in 0 until withPaper.indexOf("\\draw"))
        assertTrue(withPaper.contains("(-0.3,-0.3) rectangle"))
    }

    @Test
    fun tapeAndOtherElementsUntouched() {
        val t = ThemeMap.transform(white, toDark = true)
        val e = com.mdev.mnotes.core.model.TapeEl(Ids.next(), 10f, 10f, 40f, 10f, 0f, red)
        assertSame(e, t.shown(e))
    }
}
