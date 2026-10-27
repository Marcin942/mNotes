package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Construct
import com.mdev.mnotes.core.geom.ConstructOps
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.shapes.ShapeBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** L1: narzędzie „Konstrukcja” na zaznaczonych kształtach. */
class ConstructOpsTest {
    private fun poly(vararg xy: Float): Stroke {
        val pts = FloatArray(xy.size / 2 * 3)
        for (i in 0 until xy.size / 2) { pts[3 * i] = xy[2 * i]; pts[3 * i + 1] = xy[2 * i + 1]; pts[3 * i + 2] = 1f }
        return Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1.2f, pts, poly = true)
    }
    private fun circle(cx: Float, cy: Float, r: Float): Stroke {
        val pts = FloatArray(3 * 49) { k -> val a = 2 * PI * (k / 3) / 48; when (k % 3) { 0 -> cx + r * cos(a).toFloat(); 1 -> cy + r * sin(a).toFloat(); else -> 1f } }
        return Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1.2f, pts, ellipse = true)
    }
    private fun dot(x: Float, y: Float) = Stroke(Ids.next(), PenKind.BALLPOINT, 0, 3f, floatArrayOf(x, y, 1f))

    private val tri = poly(0f, 0f, 40f, 0f, 0f, 30f, 0f, 0f)

    @Test fun triangleOffersCentersAndBuildsCircumcircle() {
        val ops = ConstructOps.ops(listOf(tri))
        assertEquals(listOf("circum", "in", "medians", "altitudes", "bisectors", "angles"), ops.map { it.id })
        val res = ops.first { it.id == "circum" }.build(0xFF000000.toInt(), 1.2f)
        val c = res.filterIsInstance<Stroke>().first { it.ellipse }
        val f = ShapeBox.frameOf(c)
        assertNotNull(f)
        assertEquals(20f, f!!.cx, 0.05f); assertEquals(15f, f.cy, 0.05f); assertEquals(25f, f.hw, 0.1f)
    }

    @Test fun intersectionsOfSelectedShapes() {
        val l1 = poly(0f, 0f, 100f, 100f); val l2 = poly(0f, 100f, 100f, 0f)
        val x = ConstructOps.ops(listOf(l1, l2)).first { it.id == "xll" }.build(0, 1f).filterIsInstance<Stroke>()
        assertEquals(1, x.size)
        assertEquals(50f, x[0].pts[0], 1e-3f)
        val lc = ConstructOps.ops(listOf(poly(-50f, 0f, 50f, 0f), circle(0f, 0f, 20f))).first { it.id == "xlc" }.build(0, 1f).filterIsInstance<Stroke>()
        assertEquals(2, lc.size)
        val cc = ConstructOps.ops(listOf(circle(0f, 0f, 20f), circle(30f, 0f, 20f))).first { it.id == "xcc" }.build(0, 1f).filterIsInstance<Stroke>()
        assertEquals(2, cc.size)
    }

    @Test fun threePointsAndReflections() {
        val ops = ConstructOps.ops(listOf(dot(0f, 0f), dot(40f, 0f), dot(0f, 30f)))
        assertTrue(ops.any { it.id == "circle3" })
        // Odbicie trójkąta względem prostej x = 50: wierzchołek (40, 0) → (60, 0).
        val axis = poly(50f, -100f, 50f, 100f)
        val m = ConstructOps.ops(listOf(axis, tri)).first { it.id == "mirror" }.build(0, 1f)
        val t = m.single() as Stroke
        assertEquals(60f, t.pts[3], 1e-3f); assertEquals(0f, t.pts[4], 1e-3f)
        // Odbicie względem punktu (0, 0) i jednokładność.
        val p = ConstructOps.ops(listOf(dot(0f, 0f), poly(10f, 0f, 20f, 0f)))
        val pm = p.first { it.id == "pmirror" }.build(0, 1f).single() as Stroke
        assertEquals(-10f, pm.pts[0], 1e-3f)
        val h = p.first { it.id == "homothety2" }.build(0, 1f).single() as Stroke
        assertEquals(40f, h.pts[3], 1e-3f)
    }

    @Test fun nothingForPlainHandwriting() {
        val scribble = Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1f, FloatArray(3 * 30) { k -> when (k % 3) { 0 -> k * 1f; 1 -> 10f * sin(k * 0.3f); else -> 1f } })
        assertTrue(ConstructOps.ops(listOf(scribble)).isEmpty())
        // A2-9: zamiast milczenia — wskazówka (odręczny kształt bez przytrzymania); tekst sam w sobie jej nie dostaje.
        assertTrue(ConstructOps.needsHint(listOf(scribble)))
        assertFalse(ConstructOps.needsHint(listOf(tri)))
        assertFalse(ConstructOps.needsHint(listOf(TextBox(Ids.next(), 10f, 10f, 40f, 14f, 0f, "ala", 11f, 0))))
    }

    private fun labels(els: List<Element>) = els.filterIsInstance<TextBox>().associateBy { it.text }

    /** Odległość punktu od odcinka. */
    private fun distSeg(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val t = (((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return hypot(px - ax - t * dx, py - ay - t * dy)
    }

    /** A2-9: konstrukcja podpisuje punkty (A, B, C, O, H…) małymi polami w kolorze pióra, obok kropek, nie na liniach. */
    @Test fun constructionsNamePoints() {
        val t = poly(0f, 0f, 60f, 0f, 20f, 40f, 0f, 0f)   // ostrokątny: ortocentrum w środku
        val ink = 0xFF1565C0.toInt()
        val circ = ConstructOps.ops(listOf(t)).first { it.id == "circum" }.build(ink, 1.2f)
        val l = labels(circ)
        assertEquals(setOf("A", "B", "C", "O"), l.keys)
        assertTrue(l.values.all { it.color == ink && it.size == ConstructOps.LABEL_PT })
        // Nazwy wierzchołków leżą na zewnątrz trójkąta.
        val verts = listOf(0f to 0f, 60f to 0f, 20f to 40f)
        for ((n, v) in listOf("A", "B", "C").zip(verts)) {
            val b = l.getValue(n)
            assertTrue("$n za blisko", hypot(b.cx - v.first, b.cy - v.second) in 4f..16f)
            assertFalse("$n w środku trójkąta", inside(b.cx, b.cy, verts))
        }
        // Ortocentrum: nazwa obok kropki, ale nie na żadnej wysokości.
        val alt = ConstructOps.ops(listOf(t)).first { it.id == "altitudes" }.build(ink, 1.2f)
        val h = labels(alt).getValue("H")
        val hp = alt.filterIsInstance<Stroke>().first { it.count == 1 }
        assertTrue(hypot(h.cx - hp.pts[0], h.cy - hp.pts[1]) < 16f)
        for (s in alt.filterIsInstance<Stroke>().filter { it.count == 2 })
            assertTrue("H na wysokości", distSeg(h.cx, h.cy, s.pts[0], s.pts[1], s.pts[3], s.pts[4]) > 4f)
    }

    private fun inside(x: Float, y: Float, v: List<Pair<Float, Float>>): Boolean {
        fun s(a: Pair<Float, Float>, b: Pair<Float, Float>) = (b.first - a.first) * (y - a.second) - (b.second - a.second) * (x - a.first)
        val d1 = s(v[0], v[1]); val d2 = s(v[1], v[2]); val d3 = s(v[2], v[0])
        return (d1 >= 0 && d2 >= 0 && d3 >= 0) || (d1 <= 0 && d2 <= 0 && d3 <= 0)
    }

    @Test fun namesCanBeOffAndAreNotRepeated() {
        assertTrue(ConstructOps.ops(listOf(tri), names = false).first { it.id == "circum" }.build(0, 1f).none { it is TextBox })
        // Zaznaczenie ma już podpisy wierzchołków: nowa konstrukcja dodaje tylko swój punkt.
        val a = TextBox(Ids.next(), -6f, -6f, 10f, 14f, 0f, "A", 11f, 0)
        assertEquals(setOf("O"), labels(ConstructOps.ops(listOf(tri, a)).first { it.id == "circum" }.build(0, 1f)).keys)
        // Kąt prosty: ortocentrum to wierzchołek A — jedna nazwa zamiast dwóch na sobie.
        assertTrue("A=H" in labels(ConstructOps.ops(listOf(tri)).first { it.id == "altitudes" }.build(0, 1f)).keys)
        // Środek ciężkości: S po polsku, G po angielsku.
        assertTrue("G" in labels(ConstructOps.ops(listOf(tri), pl = false).first { it.id == "medians" }.build(0, 1f)).keys)
    }

    @Test fun labelGoesIntoTheWidestGap() {
        // Jedna linia w prawo: nazwa po lewej.
        val p = ConstructOps.labelAt(Construct.P(0f, 0f), listOf(0f), 10f)
        assertEquals(-10f, p.x, 1e-3f); assertEquals(0f, p.y, 1e-3f)
        // Brak linii: w prawo i w górę.
        val q = ConstructOps.labelAt(Construct.P(0f, 0f), emptyList(), 10f)
        assertTrue(q.x > 0f && q.y < 0f)
    }
}
