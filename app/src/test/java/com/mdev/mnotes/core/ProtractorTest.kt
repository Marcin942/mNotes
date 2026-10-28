package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Construct
import com.mdev.mnotes.core.geom.ConstructOps
import com.mdev.mnotes.core.geom.Protractor
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** FB4 (A2-13): kątomierz i odczyt kąta przez zaznaczenie. */
class ProtractorTest {
    private fun at(p: Protractor, deg: Double, len: Float): Pair<Float, Float> {
        val r = Math.toRadians(deg)
        return p.x(len * cos(r).toFloat(), -len * sin(r).toFloat()) to p.y(len * cos(r).toFloat(), -len * sin(r).toFloat())
    }

    @Test fun readsAngleOnTheScale() {
        val p = Protractor(100f, 200f, 0f, 80f)
        assertEquals(0f, p.degAt(150f, 200f), 1e-3f)          // prawy koniec bazy
        assertEquals(90f, p.degAt(100f, 150f), 1e-3f)         // szczyt łuku (y w dół)
        assertEquals(180f, p.degAt(40f, 200f), 1e-3f)
        val (x, y) = at(p, 37.0, 50f)
        assertEquals(37f, p.degAt(x, y), 1e-2f)
        // Obrócony o 90°: ta sama miara względem jego linii bazowej.
        val q = Protractor(0f, 0f, (PI / 2).toFloat(), 80f)
        val (qx, qy) = at(q, 30.0, 40f)
        assertEquals(30f, q.degAt(qx, qy), 1e-2f)
    }

    @Test fun rayFromCentreSnapsToWholeDegrees() {
        val p = Protractor(0f, 0f, 0.3f, 90f)
        val (x, y) = at(p, 29.6, 70f)
        val r = p.ray(x, y)
        assertEquals(30f, r[2], 1e-3f)
        assertEquals(70f, hypot(r[0] - p.cx, r[1] - p.cy), 1e-2f)            // długość idzie za piórem
        assertEquals(30f, p.degAt(r[0], r[1]), 1e-2f)
        assertEquals(45f, p.ray(at(p, 47.0, 50f).first, at(p, 47.0, 50f).second, stepDeg = 5f)[2], 1e-3f)
        assertEquals(150f, Protractor.shown(210f), 0f)
    }

    @Test fun bodyIsTheHalfDiscAndTheStrip() {
        val p = Protractor(0f, 0f, 0f, 100f)
        assertTrue(p.inBody(0f, -60f, 10f))       // w półkolu
        assertTrue(p.inBody(80f, 5f, 10f))        // pasek pod linią bazową
        assertFalse(p.inBody(0f, 60f, 10f))       // pod paskiem
        assertFalse(p.inBody(0f, -120f, 10f))     // poza łukiem
        assertFalse(p.inBody(2f, -2f, 10f))       // przy środku: tu zaczyna się promień piórem
        assertTrue(p.nearCenter(2f, -2f, 10f))
        assertEquals(1f, Protractor.tick(30), 0f); assertEquals(0.6f, Protractor.tick(45), 0f); assertEquals(0.35f, Protractor.tick(47), 0f)
    }

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float) =
        Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1.2f, floatArrayOf(x0, y0, 1f, x1, y1, 1f), poly = true)

    @Test fun angleBetweenSelectedLines() {
        val k = Construct.corner(Construct.Line(Construct.P(0f, 0f), Construct.P(100f, 0f)), Construct.Line(Construct.P(0f, 0f), Construct.P(50f, -50f)))
        assertNotNull(k); assertEquals(45f, k!!.deg, 1e-3f)
        // Przecinające się w środku: wierzchołek w przecięciu, ramiona do dalszych końców.
        val x = Construct.corner(Construct.Line(Construct.P(-10f, 0f), Construct.P(100f, 0f)), Construct.Line(Construct.P(0f, 10f), Construct.P(0f, -90f)))!!
        assertEquals(90f, x.deg, 1e-3f); assertEquals(0f, x.v.x, 1e-4f)
        assertNull(Construct.corner(Construct.Line(Construct.P(0f, 0f), Construct.P(1f, 0f)), Construct.Line(Construct.P(0f, 5f), Construct.P(1f, 5f))))
        // W menu: miara już w nazwie, „wstaw” daje łuk i podpis.
        val op = ConstructOps.ops(listOf(line(0f, 0f, 100f, 0f), line(0f, 0f, 60f, -35f))).first { it.id == "angle" }
        assertTrue(op.pl, op.pl.endsWith("30,3°"))
        val els = op.build(0, 1f)
        assertEquals(1, els.filterIsInstance<Stroke>().size)
        assertEquals("30,3°", els.filterIsInstance<TextBox>().single().text)
    }

    @Test fun triangleAnglesSumTo180() {
        val tri = Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1.2f, floatArrayOf(0f, 0f, 1f, 40f, 0f, 1f, 0f, -30f, 1f, 0f, 0f, 1f), poly = true)
        val op = ConstructOps.ops(listOf(tri)).first { it.id == "angles" }
        assertTrue(op.pl, "90°" in op.pl)
        val labels = op.build(0, 1f).filterIsInstance<TextBox>().map { it.text.removeSuffix("°").replace(',', '.').toFloat() }
        assertEquals(3, labels.size)
        assertEquals(180f, labels.sum(), 0.15f)
        assertEquals("36.9°", ConstructOps.deg(36.87f, pl = false))
        assertEquals("90°", ConstructOps.deg(89.98f))
    }

    /** 2c-A15: odcinki przecinające się jak „X” — kąt ostry, a nie przypadkowy rozwarty z dalszych końców. */
    @Test fun crossingSegmentsGiveTheAcuteAngle() {
        fun L(ax: Float, ay: Float, bx: Float, by: Float) = Construct.Line(Construct.P(ax, ay), Construct.P(bx, by))
        // Przecięcie w (0, 0); dalsze końce (100, 0) i (−70, −40,4) dają 150°, a kąt ostry to 30°.
        val x = Construct.corner(L(-40f, 0f, 100f, 0f), L(50f, 28.87f, -70f, -40.41f))!!
        assertEquals(30f, x.deg, 0.05f)
        // Wspólny koniec („V”) — bez zmian, także rozwarty.
        val v = Construct.corner(L(0f, 0f, 100f, 0f), L(0f, 0f, -60f, 34.64f))!!
        assertEquals(150f, v.deg, 0.05f)
        // Jeden odcinek kończy się na drugim („T” krzywo): ramię bez drugiej strony zostaje, ostry po stronie drugiego.
        val t = Construct.corner(L(-80f, 0f, 100f, 0f), L(0f, 0f, -60f, -34.64f))!!
        assertEquals(30f, t.deg, 0.05f)
    }
}
