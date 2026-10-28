package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.Fling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/** 2d-A1: rzut stanowiska — wzory `OverScroller`, sztuczny zegar. */
class FlingTest {
    private val density = 2f

    @Test fun distanceAndDurationFollowOverScrollerFormula() {
        val v = 4000f
        val f = Fling(0f, v, density = density)
        // Te same wzory co SplineOverScroller.getSplineFlingDistance/Duration.
        val decel = Fling.FRICTION * Fling.physicalCoeff(density)
        val l = ln(Fling.INFLEXION * v / decel)
        val d = Fling.DECELERATION_RATE
        assertEquals((decel * exp(d / (d - 1) * l)).toFloat(), f.distance, 0.5f)
        assertEquals((1000 * exp(l / (d - 1))).toFloat(), f.durationMs, 0.5f)
        // Szybszy rzut leci dalej i dłużej; w drugą stronę — lustrzanie.
        val g = Fling(0f, 2 * v, density = density)
        assertTrue(g.distance > f.distance && g.durationMs > f.durationMs)
        assertEquals(-f.distance, Fling(0f, -v, density = density).distance, 1e-3f)
        assertEquals(0f, Fling(5f, 0f, density = density).distance, 0f)
    }

    @Test fun speedStartsAtFingerAndFadesSmoothly() {
        val v = 3000f
        val f = Fling(100f, v, density = density)
        assertEquals(v, f.speedAt(0f), v * 0.01f)
        // Sztuczny zegar, klatki co 16 ms: położenie rośnie, prędkość maleje monotonicznie do zera.
        var t = 0f; var lastPos = f.at(0f); var lastV = f.speedAt(0f)
        while (!f.done(t)) {
            t += 16f
            val p = f.at(t); val s = f.speedAt(t)
            assertTrue("położenie cofnęło się przy $t ms", p >= lastPos)
            assertTrue("prędkość wzrosła przy $t ms", s <= lastV + 1e-3f)
            // Bez szarpnięć: krok klatki nie większy niż przy prędkości z poprzedniej klatki.
            assertTrue(p - lastPos <= lastV * 0.016f + 0.5f)
            lastPos = p; lastV = s
        }
        assertEquals(100f + f.distance, f.at(f.durationMs), 1e-3f)
        assertEquals(0f, f.speedAt(f.durationMs), 0f)
    }

    @Test fun stopsAtBound() {
        val f = Fling(0f, 5000f, min = -1000f, max = 150f, density = density)
        assertTrue(f.distance > 150f)
        var t = 0f
        while (!f.done(t)) { assertTrue(f.at(t) <= 150f); t += 16f }
        assertEquals(150f, f.at(t), 0f)
        assertTrue("staje na granicy przed końcem krzywej", t < f.durationMs)
        assertEquals(0f, f.speedAt(t), 0f)
        // Start już na granicy, rzut w jej stronę — nic się nie dzieje.
        val g = Fling(150f, 5000f, max = 150f, density = density)
        assertTrue(g.done(0f)); assertTrue(abs(g.at(100f) - 150f) < 1e-6f)
    }
}
