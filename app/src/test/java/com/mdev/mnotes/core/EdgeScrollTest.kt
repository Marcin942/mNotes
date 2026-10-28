package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.EdgeScroll
import org.junit.Assert.assertEquals
import org.junit.Test

/** 2c-A7: prędkość autoprzewijania przy krawędzi okna. */
class EdgeScrollTest {
    @Test fun zeroInsideGrowsTowardEdges() {
        assertEquals(0f, EdgeScroll.factor(500f, 1000f, 100f), 0f)
        assertEquals(0f, EdgeScroll.factor(100f, 1000f, 100f), 0f)
        assertEquals(0.5f, EdgeScroll.factor(50f, 1000f, 100f), 1e-6f)
        assertEquals(1f, EdgeScroll.factor(-20f, 1000f, 100f), 0f)
        assertEquals(-0.5f, EdgeScroll.factor(950f, 1000f, 100f), 1e-6f)
        assertEquals(-1f, EdgeScroll.factor(1000f, 1000f, 100f), 0f)
    }

    @Test fun windowTooSmallForBandsDoesNotScroll() {
        assertEquals(0f, EdgeScroll.factor(10f, 150f, 100f), 0f)
    }
}
