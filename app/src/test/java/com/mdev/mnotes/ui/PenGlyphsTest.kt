package com.mdev.mnotes.ui

import com.mdev.mnotes.core.layout.BarConfig
import com.mdev.mnotes.core.layout.BarIcons
import com.mdev.mnotes.core.model.PenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class PenGlyphsTest {
    @Test fun everyGlyphFitsTheIconGridWithRoomForStroke() {
        for (k in PenKind.entries) {
            val b = PenGlyphs.bounds(k)
            // Kontur ma ~1,35 jednostki grubości, więc punkty muszą leżeć ≥ 0,7 od krawędzi pola 24×24.
            assertTrue("$k minX=${b[0]}", b[0] >= 0.7f); assertTrue("$k minY=${b[1]}", b[1] >= 0.7f)
            assertTrue("$k maxX=${b[2]}", b[2] <= 23.3f); assertTrue("$k maxY=${b[3]}", b[3] <= 23.3f)
        }
    }

    @Test fun everyGlyphHasInkAndBodyAndPointsAreEven() {
        for (k in PenKind.entries) {
            val parts = PenGlyphs.parts(k)
            assertTrue("$k bez końcówki", parts.any { it.role == GlyphRole.INK })
            assertTrue("$k bez korpusu", parts.any { it.role == GlyphRole.BODY })
            for (p in parts) {
                assertEquals(0, p.pts.size % 2)
                assertTrue(p.pts.size >= 4)
                assertTrue(p.pts.all { it.isFinite() })
            }
        }
    }

    @Test fun inkTipSitsAtTheLowerLeftCorner() {
        // Czubek (lub ścięty brzeg zakreślacza) leży blisko lewego dolnego rogu, wszystkie narzędzia mają ten sam „kierunek”.
        for (k in PenKind.entries) {
            val ink = PenGlyphs.parts(k).first { it.role == GlyphRole.INK }.pts
            var best = Float.MAX_VALUE
            var i = 0
            while (i < ink.size) { best = minOf(best, hypot(ink[i] - 2.5f, ink[i + 1] - 21.5f)); i += 2 }
            assertTrue("$k czubek ${best} od rogu", best < 2.5f)
        }
    }

    @Test fun glyphsAreVisuallyDistinct() {
        // Długopis i ołówek miały kiedyś tę samą ikonę — kształty muszą się różnić.
        val sigs = PenKind.entries.map { k -> PenGlyphs.parts(k).joinToString("|") { p -> p.role.name + p.pts.joinToString(",") { "%.2f".format(it) } } }
        assertEquals(PenKind.entries.size, sigs.toSet().size)
    }

    @Test fun onlyFountainPenHasANibSlit() {
        for (k in PenKind.entries) assertEquals(k == PenKind.FOUNTAIN, PenGlyphs.parts(k).any { it.role == GlyphRole.CUT })
        assertFalse(PenGlyphs.parts(PenKind.PENCIL).any { it.role == GlyphRole.CUT })
    }

    private fun inGrid(parts: List<GlyphPart>, name: String) {
        for (p in parts) {
            assertEquals(0, p.pts.size % 2)
            var i = 0
            while (i < p.pts.size) {
                assertTrue("$name x=${p.pts[i]}", p.pts[i] in 0.7f..23.3f)
                assertTrue("$name y=${p.pts[i + 1]}", p.pts[i + 1] in 0.7f..23.3f)
                i += 2
            }
        }
    }

    /** 2b-A1: cztery tryby gumki to ta sama gumka i jeden inny znak; wszystko w siatce 24 jak pióra. */
    @Test fun eraserModesShareTheEraserAndDifferInOneMark() {
        val sigs = HashSet<String>()
        for (mode in 0..3) {
            val parts = ToolGlyphParts.eraser(mode)
            inGrid(parts, "gumka $mode")
            assertEquals(ToolGlyphParts.eraserBase, parts.take(ToolGlyphParts.eraserBase.size))
            val mark = ToolGlyphParts.eraserMark(mode)
            assertTrue(mark.isNotEmpty())
            assertTrue("znak gumki $mode nachodzi na korpus", mark.all { p -> (0 until p.pts.size / 2).all { p.pts[2 * it] >= 13f && p.pts[2 * it + 1] >= 13f } })
            sigs += mark.joinToString("|") { p -> p.role.name + p.pts.joinToString(",") { "%.1f".format(it) } }
        }
        assertEquals(4, sigs.size)
        // Korpus gumki leży na tej samej przekątnej co pióra (czubek w lewym dolnym rogu).
        val tip = ToolGlyphParts.eraserBase.first().pts   // pierwsza i ostatnia para to płaska krawędź czubka
        val n = tip.size
        assertTrue(hypot((tip[0] + tip[n - 2]) / 2f - 2.5f, (tip[1] + tip[n - 1]) / 2f - 21.5f) < 1.5f)
        inGrid(ToolGlyphParts.lasso(false), "lasso"); inGrid(ToolGlyphParts.lasso(true), "lasso prostokąt")
        assertTrue(ToolGlyphParts.lasso(false).any { it.role == GlyphRole.DASH })
    }

    /** 2b-A1: każda pozycja paska narzędzi ma ikonę (glif albo ikona Material). */
    /** Ciało funkcji `ToolBar` z `EditorScreen.kt` (do następnej funkcji najwyższego poziomu). */
    private fun toolBarSource(): String {
        val src = java.io.File("src/main/java/com/mdev/mnotes/ui/EditorScreen.kt").readText()
        val start = src.indexOf("private fun ToolBar(")
        assertTrue(start > 0)
        val end = Regex("\n(@Composable|private fun |fun |internal fun )").find(src, src.indexOf('\n', start))!!.range.first
        return src.substring(start, end)
    }

    /** 2c-A15: lasso i gumka rysowane glifem w stylu piór mają na pasku ten sam rozmiar co pióra. */
    @Test fun styledGlyphsMatchPenSizeOnTheBar() {
        val body = toolBarSource()
        fun sizeOf(glyph: String) = Regex("""$glyph\([^\n]*Modifier\.size\(([^)]*)\)""").find(body)?.groupValues?.get(1)
        val pen = sizeOf("PenGlyph")
        assertNotNull(pen)
        assertEquals("gumka", pen, sizeOf("EraserModeGlyph"))
        assertEquals("lasso", pen, sizeOf("LassoGlyph"))
    }

    /** 2c-A15: pozycje brane z prawdziwego paska (BarItem/tool/icon i BAR_PENS), nie z listy, którą sprawdzamy. */
    @Test fun everyBarItemHasAnIcon() {
        val body = toolBarSource()
        val src = java.io.File("src/main/java/com/mdev/mnotes/ui/EditorScreen.kt").readText()
        val pens = Regex("""PenKind\.([A-Z]+) to \d+""").findAll(src.lines().first { "val BAR_PENS" in it }).map { it.groupValues[1].lowercase() }.toList()
        val ids = Regex("""(?:BarItem|\btool|\bicon)\("([a-z]+)"""").findAll(body).map { it.groupValues[1] }.toList() + pens
        assertTrue("za mało pozycji: $ids", ids.size >= 15)
        for (id in ids) assertNotNull("brak ikony: $id", BarIcons.kind(id))
        assertEquals("pasek ≠ domyślny układ", BarConfig.DEFAULT_ORDER.toSet(), ids.toSet())
        assertNull(BarIcons.kind("nieznane"))
    }
}
