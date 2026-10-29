package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.GeoTool
import com.mdev.mnotes.core.geom.GeoTool.In
import com.mdev.mnotes.core.geom.GeoTool.Input
import com.mdev.mnotes.core.geom.GeoTool.Kind
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.shapes.ShapeBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 2b-A8: narzędzie Geometria — wskazywanie wejść i wynik konstrukcji jak cyrklem. */
class GeoToolTest {
    private fun poly(vararg xy: Float) = Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1.2f, FloatArray(xy.size / 2 * 3) { k -> if (k % 3 == 2) 1f else xy[k / 3 * 2 + k % 3] }, poly = true)
    private fun circle(cx: Float, cy: Float, r: Float) = Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1.2f,
        FloatArray(3 * 49) { k -> val a = 2 * PI * (k / 3) / 48; when (k % 3) { 0 -> cx + r * cos(a).toFloat(); 1 -> cy + r * sin(a).toFloat(); else -> 1f } }, ellipse = true)
    private fun dot(x: Float, y: Float) = Stroke(Ids.next(), PenKind.BALLPOINT, 0, 3f, floatArrayOf(x, y, 1f))
    private fun strokes(els: List<Element>) = els.filterIsInstance<Stroke>()
    private fun dots(els: List<Element>) = strokes(els).filter { it.count == 1 }

    @Test fun hintsGuideStepByStep() {
        assertEquals("Wskaż okrąg", GeoTool.hint(Kind.XCC, 0))
        assertEquals("Wskaż drugi okrąg", GeoTool.hint(Kind.XCC, 1))
        assertTrue(GeoTool.hint(Kind.CIRCLE3, 2).startsWith("Wskaż trzeci punkt"))
        assertEquals("Pick the second circle", GeoTool.hint(Kind.XCC, 1, pl = false))
    }

    @Test fun pickingSnapsToExistingThingsOrUsesTheTap() {
        val d = dot(100f, 100f); val tri = poly(0f, 0f, 60f, 0f, 0f, 40f, 0f, 0f); val c = circle(200f, 200f, 30f)
        val els = listOf(d, tri, c)
        // Punkt: kropka w zasięgu, inaczej miejsce dotknięcia.
        val p = GeoTool.pick(els, 104f, 97f, 8f, In.POINT) as Input.Point
        assertEquals(100f, p.p.x, 1e-3f)
        val free = GeoTool.pick(els, 400f, 400f, 8f, In.POINT) as Input.Point
        assertEquals(400f, free.p.x, 1e-3f)
        // Bok trójkąta jako odcinek.
        val l = GeoTool.pick(els, 30f, 2f, 6f, In.LINE) as Input.Ln
        assertEquals(0f, l.l.a.y, 1e-3f); assertEquals(0f, l.l.b.y, 1e-3f)
        assertNull(GeoTool.pick(els, 300f, 50f, 6f, In.LINE))
        // Okrąg: po obwodzie i w środku.
        assertNotNull(GeoTool.pick(els, 230f, 201f, 6f, In.CIRCLE))
        assertEquals(30f, (GeoTool.pick(els, 205f, 205f, 6f, In.CIRCLE) as Input.Circ).c.r, 0.1f)
        // Trójkąt w środku, obiekt pod palcem.
        assertNotNull(GeoTool.pick(els, 10f, 10f, 6f, In.TRIANGLE))
        assertTrue((GeoTool.pick(els, 100f, 101f, 6f, In.OBJECT) as Input.Obj).el === d)
    }

    @Test fun compassAndTwoCirclesIntersection() {
        val compass = GeoTool.build(Kind.COMPASS, listOf(Input.Point(com.mdev.mnotes.core.geom.Construct.P(50f, 50f)), Input.Point(com.mdev.mnotes.core.geom.Construct.P(80f, 90f))), 0, 1f)
        val ring = strokes(compass).first { it.ellipse }
        assertEquals(50f, ShapeBox.frameOf(ring)!!.hw, 0.1f)                      // r = |(30, 40)| = 50
        val els = listOf(circle(0f, 0f, 50f), circle(60f, 0f, 50f))
        val ins = listOf(GeoTool.pick(els, 50f, 0f, 6f, In.CIRCLE)!!, GeoTool.pick(els, 110f, 0f, 6f, In.CIRCLE)!!)
        val x = GeoTool.build(Kind.XCC, ins, 0, 1f)
        assertEquals(2, dots(x).size)
        assertTrue(dots(x).all { abs(it.pts[0] - 30f) < 0.01f })                // punkty przecięcia na x = 30
        val line = strokes(x).single { it.count == 2 }
        assertEquals(30f, line.pts[0], 0.01f); assertEquals(30f, line.pts[3], 0.01f)   // prosta przez nie (pionowa)
        assertEquals(setOf("P", "Q"), x.filterIsInstance<TextBox>().map { it.text }.toSet())
        // Okręgi rozłączne — brak rozwiązania.
        val far = listOf(circle(0f, 0f, 10f), circle(100f, 0f, 10f))
        assertTrue(GeoTool.build(Kind.XCC, listOf(GeoTool.pick(far, 10f, 0f, 3f, In.CIRCLE)!!, GeoTool.pick(far, 110f, 0f, 3f, In.CIRCLE)!!), 0, 1f).isEmpty())
    }

    @Test fun perpendicularParallelAndAngle() {
        val base = poly(0f, 100f, 200f, 100f)
        val l = GeoTool.pick(listOf(base), 50f, 100f, 4f, In.LINE)!!
        val p = Input.Point(com.mdev.mnotes.core.geom.Construct.P(80f, 40f))
        val perp = GeoTool.build(Kind.PERP, listOf(l, p), 0, 1f)
        val pl = strokes(perp).single { it.count == 2 }
        assertEquals(pl.pts[0], pl.pts[3], 1e-3f)                                // pionowa, prostopadła do poziomej
        assertEquals(80f, dots(perp).single().pts[0], 1e-3f); assertEquals(100f, dots(perp).single().pts[1], 1e-3f)   // spodek
        val par = strokes(GeoTool.build(Kind.PARALLEL, listOf(l, p), 0, 1f)).single { it.count == 2 }
        assertEquals(40f, par.pts[1], 1e-3f); assertEquals(40f, par.pts[4], 1e-3f)
        val l2 = GeoTool.pick(listOf(poly(0f, 100f, 0f, 0f)), 0f, 50f, 4f, In.LINE)!!
        assertEquals("90°", GeoTool.build(Kind.ANGLE, listOf(l, l2), 0, 1f).filterIsInstance<TextBox>().single().text)
    }

    @Test fun triangleCentersAndTransformations() {
        val tri = poly(0f, 0f, 40f, 0f, 0f, 30f, 0f, 0f)
        val t = GeoTool.pick(listOf(tri), 5f, 5f, 4f, In.TRIANGLE)!!
        val circ = GeoTool.build(Kind.CIRCUM, listOf(t), 0, 1f)
        val f = ShapeBox.frameOf(strokes(circ).first { it.ellipse })!!
        assertEquals(20f, f.cx, 0.05f); assertEquals(15f, f.cy, 0.05f)
        // Odbicie względem prostej x = 50: wierzchołek (40, 0) → (60, 0).
        val axis = GeoTool.pick(listOf(poly(50f, -100f, 50f, 100f)), 50f, 0f, 4f, In.LINE)!!
        val m = GeoTool.build(Kind.REFLECT, listOf(axis, Input.Obj(tri)), 0, 1f).single() as Stroke
        assertEquals(60f, m.pts[3], 1e-3f)
        // Obrót o 90° wokół (0, 0): (40, 0) → (0, 40) albo (0, −40) — długość ramienia ta sama.
        val r = GeoTool.build(Kind.ROTATE, listOf(Input.Point(com.mdev.mnotes.core.geom.Construct.P(0f, 0f)), Input.Obj(tri)), 0, 1f).single() as Stroke
        assertEquals(0f, r.pts[3], 1e-3f); assertEquals(40f, abs(r.pts[4]), 1e-3f)
        assertEquals(40f, hypot(r.pts[3], r.pts[4]), 1e-3f)
    }

    // — 2c-A8: kąt obrotu, odbicie względem punktu, styczne, dymek przy chybieniu —

    @Test fun rotationUsesThePickedAngle() {
        val o = GeoTool.Input.Point(com.mdev.mnotes.core.geom.Construct.P(0f, 0f))
        val d = GeoTool.Input.Obj(dot(100f, 0f))
        for (a in listOf(30f, 45f, 60f, 120f, 180f, -45f)) {
            val r = strokes(GeoTool.build(Kind.ROTATE, listOf(o, d), 0, 1f, angle = a)).single()
            val rad = a * PI / 180
            // 3c-B3: kąt dodatni przeciwnie do wskazówek zegara, jak w szkole i jak „kąt przy punkcie” (y ekranu w dół).
            assertEquals(100f * cos(rad).toFloat(), r.pts[0], 1e-2f)
            assertEquals(-100f * sin(rad).toFloat(), r.pts[1], 1e-2f)
        }
    }

    /** 3c-B3: kąt przy punkcie ponad 180° dostawał podpis kąta wypukłego (270° → „90°”); narzędzie przyjmuje do 180°. */
    @Test fun angleLimitsPerTool() {
        assertTrue(GeoTool.angleOk(Kind.ROTATE, 270f)); assertTrue(GeoTool.angleOk(Kind.ROTATE, -360f))
        assertTrue(GeoTool.angleOk(Kind.ANGLE_AT, 180f)); assertTrue(GeoTool.angleOk(Kind.ANGLE_AT, -120f))
        assertFalse(GeoTool.angleOk(Kind.ANGLE_AT, 270f)); assertFalse(GeoTool.angleOk(Kind.ANGLE_AT, 0f))
        assertFalse(GeoTool.angleOk(Kind.ROTATE, Float.NaN))
        val ln = GeoTool.Input.Ln(com.mdev.mnotes.core.geom.Construct.Line(com.mdev.mnotes.core.geom.Construct.P(0f, 0f), com.mdev.mnotes.core.geom.Construct.P(100f, 0f)))
        val v = GeoTool.Input.Point(com.mdev.mnotes.core.geom.Construct.P(0f, 0f))
        assertTrue(GeoTool.build(Kind.ANGLE_AT, listOf(ln, v), 0, 1f, angle = 270f).isEmpty())
        assertTrue(GeoTool.build(Kind.ANGLE_AT, listOf(ln, v), 0, 1f, angle = 120f).isNotEmpty())
    }

    /** 3c-B3: prosta zwinięta do punktu (koniec przeciągnięty na początek przy konstrukcji na żywo) nie daje wyniku. */
    @Test fun collapsedLineGivesNoResult() {
        val p = com.mdev.mnotes.core.geom.Construct.P(50f, 50f)
        val ln = GeoTool.Input.Ln(com.mdev.mnotes.core.geom.Construct.Line(p, p))
        val q = GeoTool.Input.Point(com.mdev.mnotes.core.geom.Construct.P(80f, 20f))
        for (k in listOf(Kind.PERP, Kind.PARALLEL, Kind.BISECTOR))
            assertTrue("$k", GeoTool.build(k, if (k == Kind.BISECTOR) listOf(ln) else listOf(ln, q), 0, 1f).isEmpty())
        assertTrue(GeoTool.build(Kind.REFLECT, listOf(ln, GeoTool.Input.Obj(dot(80f, 20f))), 0, 1f).isEmpty())
    }

    @Test fun reflectionThroughAPointInTheTool() {
        val c = GeoTool.Input.Point(com.mdev.mnotes.core.geom.Construct.P(50f, 50f))
        val r = strokes(GeoTool.build(Kind.PREFLECT, listOf(c, GeoTool.Input.Obj(dot(60f, 70f))), 0, 1f)).single()
        assertEquals(40f, r.pts[0], 1e-3f); assertEquals(30f, r.pts[1], 1e-3f)
    }

    @Test fun tangentsFromAPoint() {
        val p = GeoTool.Input.Point(com.mdev.mnotes.core.geom.Construct.P(100f, 0f))
        val c = GeoTool.Input.Circ(com.mdev.mnotes.core.geom.Construct.Circle(com.mdev.mnotes.core.geom.Construct.P(0f, 0f), 50f))
        val out = GeoTool.build(Kind.TANGENTS, listOf(p, c), 0, 1f)
        assertEquals(2, dots(out).size)
        assertEquals(2, strokes(out).count { it.poly && it.count == 2 })
        // Punkt wewnątrz okręgu: brak stycznych.
        val inside = GeoTool.Input.Point(com.mdev.mnotes.core.geom.Construct.P(10f, 0f))
        assertTrue(GeoTool.build(Kind.TANGENTS, listOf(inside, c), 0, 1f).isEmpty())
    }

    @Test fun missSaysWhatIsMissing() {
        assertTrue(GeoTool.missHint(Kind.XCC, 0).startsWith("Tu nie ma okręgu"))
        assertTrue(GeoTool.missHint(Kind.XLL, 1).startsWith("Tu nie ma odcinka ani prostej"))
        assertTrue(GeoTool.missHint(Kind.XLL, 1).endsWith(GeoTool.hint(Kind.XLL, 1)))
        assertEquals(GeoTool.hint(Kind.COMPASS, 0), GeoTool.missHint(Kind.COMPASS, 0))
    }
}
