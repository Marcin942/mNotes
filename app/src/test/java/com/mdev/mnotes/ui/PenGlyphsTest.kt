package com.mdev.mnotes.ui

import com.mdev.mnotes.core.model.PenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
