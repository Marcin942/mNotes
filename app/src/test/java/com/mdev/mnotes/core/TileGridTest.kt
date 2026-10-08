package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.TileGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileGridTest {
    @Test fun levelNeverBelowZoomAndWithinOneStep() {
        val step = Math.pow(2.0, 1.0 / TileGrid.STEPS).toFloat()
        var z = 0.2f
        while (z < 30f) {
            val l = TileGrid.level(z)
            assertTrue("level($z)=$l", l >= z * 0.997f && l <= z * step * 1.001f)
            z *= 1.013f
        }
    }

    @Test fun levelOfExactPowerIsStable() {
        assertEquals(1f, TileGrid.level(1f), 1e-6f)
        assertEquals(4f, TileGrid.level(4f), 1e-5f)
        assertEquals(TileGrid.level(2.5f), TileGrid.level(TileGrid.level(2.5f)), 1e-5f)
    }

    @Test fun motionLevelsAreCoarserButNeverBelowZoom() {
        val step = Math.pow(2.0, 1.0 / TileGrid.STEPS_MOTION).toFloat()
        var z = 0.3f
        while (z < 30f) {
            val l = TileGrid.level(z, TileGrid.STEPS_MOTION)
            assertTrue("level($z)=$l", l >= z * 0.997f && l <= z * step * 1.001f)
            z *= 1.017f
        }
    }

    @Test fun fitPrefersDownscalingOverUpscaling() {
        assertTrue(TileGrid.fit(1.3f, 1f) < TileGrid.fit(0.9f, 1f))      // 30% za dużo lepsze niż 10% za mało
        assertTrue(TileGrid.fit(1f, 1f) < TileGrid.fit(1.3f, 1f))
        assertTrue(TileGrid.fit(2f, 1f) < TileGrid.fit(0.6f, 1f))
        assertEquals(1f, TileGrid.fit(1f, 1f), 1e-6f)
    }

    @Test fun tilesCoverPageExactly() {
        for (scale in floatArrayOf(0.3f, 1f, 4.37f, 25f)) {
            val px = TileGrid.pixels(595f, scale)
            val n = TileGrid.count(px)
            var sum = 0
            for (i in 0 until n) sum += TileGrid.size(i, px)
            assertEquals(px, sum)
            assertTrue(TileGrid.size(n - 1, px) in 1..TileGrid.TILE)
        }
    }

    @Test fun rangeClampsToPageAndFindsEdgeTiles() {
        val scale = 2f
        val n = TileGrid.count(TileGrid.pixels(595f, scale))   // 1190 px → 3 kafelki
        assertEquals(3, n)
        assertEquals(0..0, TileGrid.range(0f, 100f, scale, n))
        assertEquals(0..1, TileGrid.range(100f, 300f, scale, n))   // 200–600 px
        assertEquals(0..2, TileGrid.range(-500f, 5000f, scale, n))
        assertTrue(TileGrid.range(1300f, 1500f, scale, n).isEmpty())  // poza stroną (za ostatnim kafelkiem)
        // krawędź dokładnie na granicy kafelka (256 pt = 512 px) nie dołącza następnego
        assertEquals(0..0, TileGrid.range(0f, 256f, scale, n))
    }

    @Test fun originMatchesIndex() {
        assertEquals(256f, TileGrid.originPt(1, 2f), 1e-4f)
        assertEquals(512f / 4.37f * 3, TileGrid.originPt(3, 4.37f), 1e-3f)
    }
}
