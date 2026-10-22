package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.AngleSnap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class AngleSnapTest {
    private fun rad(deg: Float) = Math.toRadians(deg.toDouble()).toFloat()
    private fun deg(rad: Float) = Math.toDegrees(rad.toDouble()).toFloat()

    @Test fun everyBuildsMultiplesWithinFullTurn() {
        assertEquals(24, AngleSnap.every(15f).size)
        assertEquals(listOf(0f, 90f, 180f, 270f), AngleSnap.every(90f))
        assertEquals(listOf(0f, 22.5f, 45f), AngleSnap.every(22.5f).take(3))
    }

    @Test fun normalizeWrapsRoundsAndDeduplicates() {
        assertEquals(listOf(0f, 30f, 330f), AngleSnap.normalize(listOf(-30f, 390f, 30f, 360f, 0.04f)))
    }

    @Test fun parseAcceptsMixedSeparatorsAndDecimalComma() {
        assertEquals(listOf(0f, 22.5f, 45f), AngleSnap.parse("45; 22,5 0°"))
        assertEquals(emptyList<Float>(), AngleSnap.parse("abc"))
    }

    @Test fun formatRoundTrips() {
        val a = listOf(0f, 22.5f, 90f)
        assertEquals("0;22.5;90", AngleSnap.format(a))
        assertEquals(a, AngleSnap.parse(AngleSnap.format(a)))
        assertEquals("22,5", AngleSnap.label(22.5f))
    }

    @Test fun snapPullsToNearestWithinTolerance() {
        val set = AngleSnap.every(45f)
        assertEquals(45f, deg(AngleSnap.snap(rad(43f), set, 4f)), 1e-3f)
        assertEquals(rad(40f), AngleSnap.snap(rad(40f), set, 4f), 1e-6f)      // poza tolerancją bez zmian
    }

    @Test fun snapKeepsWindingOfNegativeAndLargeTurns() {
        val set = AngleSnap.every(90f)
        assertEquals(-90f, deg(AngleSnap.snap(rad(-88f), set, 4f)), 1e-3f)
        assertEquals(-360f, deg(AngleSnap.snap(rad(-358f), set, 4f)), 1e-3f)
        assertEquals(360f, deg(AngleSnap.snap(rad(361f), set, 4f)), 1e-3f)
    }

    @Test fun customSetOnlySnapsToItsAngles() {
        val set = listOf(30f)
        assertEquals(30f, deg(AngleSnap.snap(rad(32f), set, 3f)), 1e-3f)
        assertEquals(rad(90f), AngleSnap.snap(rad(90f), set, 3f), 1e-6f)
    }

    @Test fun emptySetOrZeroToleranceNeverSnaps() {
        assertEquals(0.3f, AngleSnap.snap(0.3f, emptyList(), 5f), 0f)
        assertEquals(rad(1f), AngleSnap.snap(rad(1f), listOf(0f), 0f), 1e-6f)
    }

    @Test fun mirrorsAddOppositeAngles() {
        assertEquals(listOf(0f, 30f, 330f), AngleSnap.withMirrors(listOf(0f, 30f)))
        assertEquals(listOf(180f), AngleSnap.withMirrors(listOf(180f)))
    }

    @Test fun isSnappedDetectsExactAngle() {
        val set = AngleSnap.every(15f)
        assertTrue(AngleSnap.isSnapped(rad(45f), set))
        assertFalse(AngleSnap.isSnapped(rad(46f), set))
        assertTrue(AngleSnap.isSnapped(PI.toFloat(), set))
    }
}
