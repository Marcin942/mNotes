package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Construct
import com.mdev.mnotes.core.geom.Construct.Circle
import com.mdev.mnotes.core.geom.Construct.Line
import com.mdev.mnotes.core.geom.Construct.P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

/** L1: konstrukcje geometryczne — testy liczbowe na znanych przykładach. */
class ConstructTest {
    private fun eq(e: P, a: P?, tol: Float = 1e-3f) { assertEquals(e.x, a!!.x, tol); assertEquals(e.y, a.y, tol) }

    // Trójkąt prostokątny 3-4-5: środek opisanego w połowie przeciwprostokątnej, wpisany r = 1, ortocentrum w kącie prostym.
    private val a = P(0f, 0f); private val b = P(4f, 0f); private val c = P(0f, 3f)

    @Test fun triangleCenters() {
        eq(P(2f, 1.5f), Construct.circumcenter(a, b, c))
        assertEquals(2.5f, Construct.circleThrough(a, b, c)!!.r, 1e-4f)
        eq(P(4f / 3f, 1f), Construct.centroid(a, b, c))
        eq(P(0f, 0f), Construct.orthocenter(a, b, c))
        val inc = Construct.incircle(a, b, c)!!
        eq(P(1f, 1f), inc.c)
        assertEquals(1f, inc.r, 1e-4f)
        assertNull(Construct.circleThrough(P(0f, 0f), P(1f, 1f), P(2f, 2f)))
    }

    @Test fun equilateralCentersCoincide() {
        val h = sqrt(3f) / 2f * 10f
        val p = P(0f, 0f); val q = P(10f, 0f); val r = P(5f, h)
        val g = Construct.centroid(p, q, r)
        eq(g, Construct.circumcenter(p, q, r)); eq(g, Construct.orthocenter(p, q, r)); eq(g, Construct.incircle(p, q, r)!!.c)
    }

    @Test fun intersections() {
        eq(P(2f, 2f), Construct.intersect(Line(P(0f, 0f), P(1f, 1f)), Line(P(0f, 4f), P(4f, 0f))))
        assertNull(Construct.intersect(Line(P(0f, 0f), P(1f, 0f)), Line(P(0f, 1f), P(5f, 1f))))
        val lc = Construct.intersect(Line(P(-10f, 0f), P(10f, 0f)), Circle(P(0f, 0f), 5f))
        assertEquals(2, lc.size); eq(P(-5f, 0f), lc[0]); eq(P(5f, 0f), lc[1])
        assertEquals(1, Construct.intersect(Line(P(-10f, 5f), P(10f, 5f)), Circle(P(0f, 0f), 5f)).size)   // styczna
        assertTrue(Construct.intersect(Line(P(-10f, 9f), P(10f, 9f)), Circle(P(0f, 0f), 5f)).isEmpty())
        val cc = Construct.intersect(Circle(P(0f, 0f), 5f), Circle(P(8f, 0f), 5f))
        assertEquals(2, cc.size)
        for (p in cc) { assertEquals(4f, p.x, 1e-4f); assertEquals(3f, kotlin.math.abs(p.y), 1e-4f) }
        assertTrue(Construct.intersect(Circle(P(0f, 0f), 1f), Circle(P(10f, 0f), 1f)).isEmpty())
        assertTrue(Construct.intersect(Circle(P(0f, 0f), 1f), Circle(P(0f, 0f), 2f)).isEmpty())
    }

    @Test fun transformations() {
        val l = Line(P(0f, 0f), P(10f, 10f))
        eq(P(3f, 1f), Construct.reflect(P(1f, 3f), l))
        eq(P(-1f, -3f), Construct.reflect(P(1f, 3f), P(0f, 0f)))
        eq(P(0f, 1f), Construct.rotate(P(1f, 0f), P(0f, 0f), (PI / 2).toFloat()))
        eq(P(5f, 5f), Construct.homothety(P(3f, 3f), P(1f, 1f), 2f))
        eq(P(4f, 6f), Construct.translate(P(1f, 2f), P(3f, 4f)))
        eq(P(2f, 2f), Construct.project(P(0f, 4f), l))
    }

    @Test fun pointsOnObjectsAndBisectors() {
        eq(P(5f, 0f), Construct.pointOn(Line(P(0f, 0f), P(10f, 0f)), 0.5f))
        eq(P(0f, 2f), Construct.pointOn(Circle(P(0f, 0f), 2f), 0.25f))
        val bis = Construct.bisector(P(0f, 0f), P(4f, 0f), 6f)
        eq(P(2f, -3f), bis.a); eq(P(2f, 3f), bis.b)
        // Dwusieczna kąta prostego idzie pod 45°.
        val ab = Construct.angleBisector(P(10f, 0f), P(0f, 0f), P(0f, 10f), sqrt(2f))
        eq(P(1f, 1f), ab.b)
    }
}
