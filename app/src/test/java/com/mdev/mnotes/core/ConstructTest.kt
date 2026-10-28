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

    // — 2c-A8: tolerancje względne, odrzucanie wyników daleko poza stroną, kolejne litery —

    @Test fun nearlyParallelLinesAreRejected() {
        val t = kotlin.math.tan(0.01 * PI / 180).toFloat()
        // Dawniej (próg 1e-6 bezwzględny) przecięcie wypadało ok. 57 000 pt dalej.
        assertNull(Construct.intersect(Line(P(0f, 0f), P(500f, 0f)), Line(P(0f, 10f), P(500f, 10f + 500f * t))))
        // Kąt 1° to już zwykłe przecięcie.
        val t1 = kotlin.math.tan(PI / 180).toFloat()
        assertTrue(Construct.intersect(Line(P(0f, 0f), P(500f, 0f)), Line(P(0f, 0f), P(500f, 500f * t1))) != null)
    }

    @Test fun nearlyCollinearPointsGiveNoCircle() {
        assertNull(Construct.circleThrough(P(0f, 0f), P(500f, 0.1f), P(1000f, 0f)))
        assertNull(Construct.incircle(P(0f, 0f), P(500f, 0.1f), P(1000f, 0f)))
        assertEquals(1f, Construct.incircle(a, b, c)!!.r, 1e-4f)
    }

    @Test fun bisectorOfSixtyDegreesGoesAtThirty() {
        val v = P(0f, 0f)
        val bis = Construct.angleBisector(P(10f, 0f), v, P(10f * kotlin.math.cos(PI / 3).toFloat(), 10f * kotlin.math.sin(PI / 3).toFloat()), 10f)
        assertEquals(30.0, Math.toDegrees(kotlin.math.atan2(bis.b.y.toDouble(), bis.b.x.toDouble())), 1e-3)
    }

    @Test fun tangentsFromTwiceTheRadiusMeetAtSixtyDegrees() {
        val o = Circle(P(0f, 0f), 50f); val p = P(100f, 0f)
        val ts = Construct.tangents(p, o)
        assertEquals(2, ts.size)
        for (t in ts) assertEquals(50f, t.dist(o.c), 1e-2f)
        assertEquals(60f, Construct.angleDeg(ts[0], p, ts[1]), 1e-2f)
        assertTrue(Construct.tangents(P(10f, 0f), o).isEmpty())
    }

    @Test fun resultFarOffThePageIsTooFar() {
        assertTrue(Construct.tooFar(P(60_000f, 0f), 595f, 842f))
        assertTrue(!Construct.tooFar(P(-500f, 900f), 595f, 842f))
    }

    @Test fun secondConstructionOnThePageGetsNextLetters() {
        fun seg(x0: Float, y0: Float, x1: Float, y1: Float) = com.mdev.mnotes.core.model.Stroke(com.mdev.mnotes.core.model.Ids.next(),
            com.mdev.mnotes.core.model.PenKind.BALLPOINT, 0, 1f, floatArrayOf(x0, y0, 1f, x1, y1, 1f), poly = true)
        val ops = com.mdev.mnotes.core.geom.ConstructOps
        fun xll(els: List<com.mdev.mnotes.core.model.Element>) = ops.ops(els).first { it.id == "xll" }.build(0, 1f)
        val first = xll(listOf(seg(0f, 0f, 200f, 200f), seg(0f, 200f, 200f, 0f)))
        val second = xll(listOf(seg(300f, 0f, 500f, 200f), seg(300f, 200f, 500f, 0f)))
        fun labels(els: List<com.mdev.mnotes.core.model.Element>) = els.filterIsInstance<com.mdev.mnotes.core.model.TextBox>().map { it.text }
        assertEquals(listOf("P"), labels(first))
        val named = ops.renameTaken(second, ops.pageNames(first))
        assertEquals(listOf("Q"), labels(named))
        // Trzecia dostaje R.
        assertEquals(listOf("R"), labels(ops.renameTaken(second, ops.pageNames(first + named))))
    }
}
