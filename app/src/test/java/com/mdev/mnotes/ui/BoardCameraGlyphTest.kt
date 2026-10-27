package com.mdev.mnotes.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardCameraGlyphTest {
    @Test fun fitsTheIconGridLikePenGlyphs() {
        val b = BoardCameraGlyph.bounds()
        // jak PenGlyphsTest: kontur ~1,35 grubości, punkty ≥ 0,7 od krawędzi pola 24×24
        assertTrue("minX=${b[0]}", b[0] >= 0.7f); assertTrue("minY=${b[1]}", b[1] >= 0.7f)
        assertTrue("maxX=${b[2]}", b[2] <= 23.3f); assertTrue("maxY=${b[3]}", b[3] <= 23.3f)
    }

    @Test fun boardChalkAndCameraAreThere() {
        val parts = BoardCameraGlyph.parts
        assertEquals(2, parts.count { it.role == GlyphRole.BODY })  // tablica i aparat
        assertTrue(parts.any { it.role == GlyphRole.MARK && !it.closed })  // kreda jak pismo
        for (p in parts) { assertEquals(0, p.pts.size % 2); assertTrue(p.pts.all { it.isFinite() }) }
    }
}
