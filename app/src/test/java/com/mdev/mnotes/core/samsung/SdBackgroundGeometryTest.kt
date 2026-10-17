package com.mdev.mnotes.core.samsung

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SdBackgroundGeometryTest {
    private fun bg(kind: BgKind, w: Float = 600f, h: Float = 848.25f, pitch: Float = 38.25f, ox: Float = 0.1f, oy: Float = 16.71f) =
        SdBackground(w, h, 0xFFE6E6E6.toInt(), kind, pitch, ox, oy, 0xFFC8C8C8.toInt(), 1f)

    @Test fun gridHasVerticalAndHorizontalLinesFromOffsets() {
        val l = SdBackgroundGeometry.lines(bg(BgKind.GRID))
        assertEquals(0, l.size % 4)
        // pionowe: 0,10 + k·38,25 ≤ 600 → k = 0..15; poziome: 16,71 + k·38,25 ≤ 848,25 → k = 0..21
        assertEquals(16 + 22, l.size / 4)
        assertEquals(0.1f, l[0], 1e-4f)
        assertEquals(0f, l[1], 1e-4f)
        assertEquals(0.1f, l[2], 1e-4f)
        assertEquals(848.25f, l[3], 1e-4f)
        val firstH = 16 * 4
        assertEquals(16.71f, l[firstH + 1], 1e-4f)
        assertEquals(600f, l[firstH + 2], 1e-4f)
        assertEquals(16.71f + 21 * 38.25f, l[l.size - 3], 1e-3f)
    }

    @Test fun linedHasOnlyHorizontalLines() {
        val l = SdBackgroundGeometry.lines(bg(BgKind.LINES))
        assertEquals(22, l.size / 4)
        for (i in l.indices step 4) { assertEquals(l[i + 1], l[i + 3], 1e-4f); assertEquals(0f, l[i], 1e-4f) }
    }

    @Test fun dotsSitAtGridCrossingsAndHaveNoLines() {
        val b = bg(BgKind.DOTS)
        assertEquals(0, SdBackgroundGeometry.lines(b).size)
        val d = SdBackgroundGeometry.dots(b)
        assertEquals(16 * 22 * 2, d.size)
        assertEquals(0.1f, d[0], 1e-4f)
        assertEquals(16.71f, d[1], 1e-4f)
        assertEquals(0.1f + 38.25f, d[2], 1e-4f)
    }

    @Test fun gridHasNoDots() {
        assertEquals(0, SdBackgroundGeometry.dots(bg(BgKind.GRID)).size)
    }

    @Test fun degeneratePitchGivesNothing() {
        assertEquals(0, SdBackgroundGeometry.lines(bg(BgKind.GRID, pitch = 0.5f)).size)
        assertEquals(0, SdBackgroundGeometry.dots(bg(BgKind.DOTS, pitch = 0f)).size)
    }

    @Test fun offsetBeyondPageGivesNothing() {
        assertTrue(SdBackgroundGeometry.lines(bg(BgKind.LINES, oy = 900f)).isEmpty())
    }
}
