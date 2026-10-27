package com.mdev.mnotes.core

import com.mdev.mnotes.core.shapes.ShapeKind
import com.mdev.mnotes.core.shapes.ShapeRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** B9: krótkie proste (kreska ułamka, minus, odcinek w rysunku) też są prostowane po przytrzymaniu. */
class ShortLineTest {

    /** Odręczna prosta od (x0, y0) do (x1, y1): [n] próbek z drżeniem [jit] pt i małym dryfem na końcu. */
    private fun shortLine(x0: Float, y0: Float, x1: Float, y1: Float, n: Int, jit: Float, seed: Int): FloatArray {
        val r = Random(seed)
        val out = FloatArray(2 * (n + 4))
        for (i in 0 until n) {
            val t = i / (n - 1f)
            out[2 * i] = x0 + (x1 - x0) * t + (r.nextFloat() - 0.5f) * 2 * jit
            out[2 * i + 1] = y0 + (y1 - y0) * t + (r.nextFloat() - 0.5f) * 2 * jit
        }
        for (k in 0 until 4) { out[2 * (n + k)] = x1 + 0.1f * k; out[2 * (n + k) + 1] = y1 + 0.05f * k }
        return out
    }

    @Test fun shortLineNeedsLowerThreshold() {
        val xy = shortLine(100f, 100f, 110f, 100.4f, 7, 0.2f, 1)
        // Dawny stały próg (18 pt) odrzuca 10-punktową prostą.
        assertNull(ShapeRecognizer.recognize(xy))
        val s = ShapeRecognizer.recognize(xy, minLine = 6f)
        assertEquals(ShapeKind.LINE, s?.kind)
        val len = hypot(s!!.xy[2] - s.xy[0], s.xy[3] - s.xy[1])
        assertTrue("długość $len", len in 9f..12f)
        // Prawie pozioma prosta przyciąga się do 0°.
        assertEquals(s.xy[1], s.xy[3], 1e-3f)
    }

    @Test fun manyShortLinesAreRecognized() {
        var ok = 0
        for (seed in 0 until 50) {
            val ang = seed * 0.37f
            val l = 6f + seed % 10
            val xy = shortLine(0f, 0f, l * kotlin.math.cos(ang), l * sin(ang), 5 + seed % 6, 0.3f, seed)
            if (ShapeRecognizer.recognize(xy, minLine = 5f)?.kind == ShapeKind.LINE) ok++
        }
        assertTrue("rozpoznane $ok/50", ok >= 45)
    }

    @Test fun shortScribbleIsNotALine() {
        // Mały zawijas (litera „c” 8 pt) nie staje się prostą.
        val xy = FloatArray(2 * 16)
        for (i in 0 until 16) { val a = 0.6f + 5f * i / 15f; xy[2 * i] = 4f * kotlin.math.cos(a); xy[2 * i + 1] = 4f * sin(a) }
        assertNull(ShapeRecognizer.recognize(xy, minLine = 5f))
        // Zygzak też nie.
        val z = floatArrayOf(0f, 0f, 3f, 3f, 6f, 0f, 9f, 3f, 12f, 0f)
        assertNotEquals(ShapeKind.LINE, ShapeRecognizer.recognize(z, minLine = 5f)?.kind)
    }

    @Test fun thresholdFollowsPenAndZoom() {
        // Grube pióro (2 pt) przy zoomie 1 dp = 1 pt: 10 pt; cienkie przy dużym przybliżeniu: 3 pt (dolna granica).
        assertEquals(10f, ShapeRecognizer.minLineFor(2f, 1f), 1e-4f)
        assertEquals(3f, ShapeRecognizer.minLineFor(0.5f, 0.2f), 1e-4f)
        assertEquals(18f, ShapeRecognizer.minLineFor(9f, 1f), 1e-4f)
    }
}
