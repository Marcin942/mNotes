package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.RenderProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderProfileTest {
    @Test fun unknownCodeFallsBackToBalanced() {
        assertEquals(RenderProfile.BALANCED, RenderProfile.of(99).code)
        assertEquals(RenderProfile.BALANCED, RenderProfile.of(-1).code)
    }

    @Test fun everyCodeResolvesToItself() {
        for (c in 0 until RenderProfile.COUNT) assertEquals(c, RenderProfile.of(c).code)
    }

    @Test fun onlyClassicIsNotTiled() {
        for (c in 0 until RenderProfile.COUNT) assertEquals(c != RenderProfile.CLASSIC, RenderProfile.of(c).tiled)
        assertFalse(RenderProfile.of(RenderProfile.CLASSIC).tiled)
    }

    @Test fun sharpReachesFurtherThanEconomy() {
        assertTrue(RenderProfile.of(RenderProfile.SHARP).maxScale > RenderProfile.of(RenderProfile.BALANCED).maxScale)
        assertTrue(RenderProfile.of(RenderProfile.BALANCED).maxScale > RenderProfile.of(RenderProfile.ECONOMY).maxScale)
    }

    @Test fun classicScalePicksBucketAtLeastZoom() {
        val p = RenderProfile.of(RenderProfile.CLASSIC)
        assertEquals(1.25f, p.classicScale(1.0f, 595f, 842f), 1e-6f)
        assertEquals(0.3f, p.classicScale(0.1f, 595f, 842f), 1e-6f)
        assertEquals(1.25f, p.classicScale(1.25f, 595f, 842f), 1e-6f)
    }

    @Test fun classicScaleIsCappedByMaxAndPixelBudget() {
        val p = RenderProfile.of(RenderProfile.CLASSIC)
        assertEquals(4.4f, p.classicScale(40f, 100f, 100f), 1e-6f)
        val huge = p.classicScale(40f, 3000f, 4000f)
        assertTrue(huge * 3000f * huge * 4000f <= RenderProfile.CLASSIC_MAX_PIXELS * 1.001f)
    }
}
