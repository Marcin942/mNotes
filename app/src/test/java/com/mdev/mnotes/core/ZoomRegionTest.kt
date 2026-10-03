package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.ZoomRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomRegionTest {
    @Test fun clampKeepsRegionInsidePage() {
        val r = ZoomRegion(500f, -20f)
        r.clamp(595f, 842f, 200f, 100f)
        assertEquals(395f, r.x, 0.001f); assertEquals(0f, r.y, 0.001f)
        val big = ZoomRegion(30f, 30f)
        big.clamp(100f, 100f, 300f, 300f)
        assertEquals(0f, big.x, 0f); assertEquals(0f, big.y, 0f)
    }

    @Test fun shiftRightStopsAtEdge() {
        val r = ZoomRegion(0f, 10f)
        assertTrue(r.shiftRight(595f, 842f, 200f, 100f, 0.5f))
        assertEquals(100f, r.x, 0.001f)
        r.x = 390f
        assertTrue(r.shiftRight(595f, 842f, 200f, 100f, 0.5f))
        assertEquals(395f, r.x, 0.001f)
        assertFalse(r.shiftRight(595f, 842f, 200f, 100f, 0.5f))
    }

    @Test fun shiftLeftStopsAtZero() {
        val r = ZoomRegion(50f, 0f)
        assertTrue(r.shiftLeft(200f, 0.5f)); assertEquals(0f, r.x, 0f)
        assertFalse(r.shiftLeft(200f, 0.5f))
    }

    @Test fun nextLineWrapsThenRunsOutOfRoom() {
        val r = ZoomRegion(300f, 0f)
        assertTrue(r.nextLine(842f, 100f, 0.75f))
        assertEquals(0f, r.x, 0f); assertEquals(75f, r.y, 0.001f)
        r.y = 700f
        assertTrue(r.nextLine(842f, 100f, 0.75f))
        assertEquals(742f, r.y, 0.001f)
        assertFalse(r.nextLine(842f, 100f, 0.75f))
    }
}
