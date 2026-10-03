package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Ruler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class RulerTest {
    private val eps = 1e-3f

    @Test
    fun localAndDocCoordinatesRoundTrip() {
        val r = Ruler(100f, 50f, 0.6f, 400f)
        val u = r.u(130f, 90f); val v = r.v(130f, 90f)
        assertEquals(130f, r.x(u, v), eps)
        assertEquals(90f, r.y(u, v), eps)
    }

    @Test
    fun rotatedRulerHasEdgesInTheRightPlace() {
        val r = Ruler(0f, 0f, (PI / 2).toFloat(), 400f, 70f) // pionowa: długa oś wzdłuż y
        // dolna krawędź (v = +35) leży po stronie −x
        assertEquals(1, r.edgeAt(-40f, 100f, 22f, 8f, 12f))
        assertEquals(-1, r.edgeAt(40f, -100f, 22f, 8f, 12f))
        assertEquals(0, r.edgeAt(0f, 0f, 22f, 8f, 12f))        // środek korpusu
        assertEquals(0, r.edgeAt(-40f, 260f, 22f, 8f, 12f))    // za końcem linijki
        assertEquals(0, r.edgeAt(-80f, 0f, 22f, 8f, 12f))      // za daleko od krawędzi
    }

    @Test
    fun bodyExcludesTheEdgeBand() {
        val r = Ruler(0f, 0f, 0f, 400f, 70f)
        assertTrue(r.inBody(0f, 0f, 8f))
        assertTrue(r.inBody(190f, 20f, 8f))
        assertFalse(r.inBody(0f, 30f, 8f))   // pas przy krawędzi należy do rysowania
        assertFalse(r.inBody(210f, 0f, 8f))
    }

    @Test
    fun snapPullsToMultiplesOfFortyFiveDegrees() {
        val tol = (2.2 * PI / 180).toFloat()
        assertEquals(0f, Ruler.snap(0.02f, tol), 1e-6f)
        assertEquals((PI / 4).toFloat(), Ruler.snap((PI / 4).toFloat() - 0.03f, tol), 1e-6f)
        assertEquals((-PI / 2).toFloat(), Ruler.snap((-PI / 2).toFloat() + 0.02f, tol), 1e-6f)
        assertEquals(0.3f, Ruler.snap(0.3f, tol), 1e-6f)                 // poza tolerancją bez zmian
    }
}
