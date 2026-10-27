package com.mdev.mnotes.core

import com.mdev.mnotes.core.shapes.Bezier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** F17: dopasowanie krzywej Béziera do odręcznej kreski i edycja punktów kontrolnych. */
class BezierTest {

    private fun arc(n: Int, r: Float, a0: Double, a1: Double, jit: Float = 0f, seed: Int = 1): FloatArray {
        val rnd = Random(seed)
        return FloatArray(2 * n) { k -> val i = k / 2; val a = a0 + (a1 - a0) * i / (n - 1); if (k % 2 == 0) r * cos(a).toFloat() + (rnd.nextFloat() - 0.5f) * jit else r * sin(a).toFloat() + (rnd.nextFloat() - 0.5f) * jit }
    }

    @Test fun straightLineIsOneSegment() {
        val c = Bezier.fit(floatArrayOf(0f, 0f, 10f, 0f, 20f, 0f, 30f, 0f, 40f, 0f))!!
        assertEquals(2, Bezier.anchors(c))
        assertTrue(Bezier.maxDistance(floatArrayOf(0f, 0f, 20f, 0f, 40f, 0f), c) < 0.1f)
    }

    @Test fun arcFitsWithinTolerance() {
        val pts = arc(80, 60f, 0.0, PI, jit = 0.6f)
        val c = Bezier.fit(pts, tol = 1.2f)!!
        assertTrue("kotwic: ${Bezier.anchors(c)}", Bezier.anchors(c) in 2..6)
        assertTrue(Bezier.maxDistance(pts, c) < 1.6f)
        // Krzywa zaczyna i kończy się w punktach kreski.
        assertEquals(pts[0], c[0], 1e-4f)
        assertEquals(pts[pts.size - 1], c[c.size - 1], 1e-4f)
    }

    @Test fun sCurveNeedsSeveralSegmentsButFewAnchors() {
        val n = 120
        val pts = FloatArray(2 * n) { k -> val i = k / 2; val x = i * 2f; if (k % 2 == 0) x else 30f * sin(x / 40f) }
        val c = Bezier.fit(pts, tol = 1f)!!
        assertTrue(Bezier.anchors(c) in 2..8)
        assertTrue(Bezier.maxDistance(pts, c) < 1.4f)
    }

    @Test fun sampleHitsAnchorsAndIsDense() {
        val c = floatArrayOf(0f, 0f, 0f, 30f, 60f, 30f, 60f, 0f)
        val s = Bezier.sample(c, 1.5f)
        assertEquals(0f, s[0], 0f); assertEquals(60f, s[s.size - 3], 1e-4f); assertEquals(0f, s[s.size - 2], 1e-4f)
        for (i in 1 until s.size / 3) assertTrue(kotlin.math.hypot(s[3 * i] - s[3 * i - 3], s[3 * i + 1] - s[3 * i - 2]) < 2.5f)
        assertNull(Bezier.fit(floatArrayOf(5f, 5f)))
    }

    @Test fun movingAnchorDragsItsHandles() {
        val c = floatArrayOf(0f, 0f, 10f, 10f, 20f, 10f, 30f, 0f, 40f, -10f, 50f, -10f, 60f, 0f)
        val m = Bezier.moved(c, 3, 30f, 5f)
        assertEquals(15f, m[5], 1e-4f)   // uchwyt wejściowy kotwicy 3 przesunięty o +5 w y
        assertEquals(-5f, m[9], 1e-4f)   // uchwyt wyjściowy też
        assertEquals(0f, m[1], 0f)       // pozostałe bez zmian
    }

    @Test fun movingHandleOfSmoothAnchorKeepsItSmooth() {
        val c = floatArrayOf(0f, 0f, 10f, 10f, 20f, 0f, 30f, 0f, 40f, 0f, 50f, 10f, 60f, 0f)
        assertTrue(Bezier.smooth(c, 3))
        val m = Bezier.moved(c, 2, 25f, -8f)
        assertTrue(Bezier.smooth(m, 3))
        // Uchwyt przy kotwicy z załamaniem rusza się sam.
        val sharp = floatArrayOf(0f, 0f, 10f, 10f, 20f, 10f, 30f, 0f, 40f, 10f, 50f, 10f, 60f, 0f)
        assertFalse(Bezier.smooth(sharp, 3))
        val m2 = Bezier.moved(sharp, 2, 22f, 12f)
        assertEquals(40f, m2[8], 0f); assertEquals(10f, m2[9], 0f)
    }
}
