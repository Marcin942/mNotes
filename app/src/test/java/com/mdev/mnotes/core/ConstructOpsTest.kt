package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.ConstructOps
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.shapes.ShapeBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
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
        assertEquals(listOf("circum", "in", "medians", "altitudes", "bisectors"), ops.map { it.id })
        val res = ops.first { it.id == "circum" }.build(0xFF000000.toInt(), 1.2f)
        val c = res.filterIsInstance<Stroke>().first { it.ellipse }
        val f = ShapeBox.frameOf(c)
        assertNotNull(f)
        assertEquals(20f, f!!.cx, 0.05f); assertEquals(15f, f.cy, 0.05f); assertEquals(25f, f.hw, 0.1f)
    }

    @Test fun intersectionsOfSelectedShapes() {
        val l1 = poly(0f, 0f, 100f, 100f); val l2 = poly(0f, 100f, 100f, 0f)
        val x = ConstructOps.ops(listOf(l1, l2)).first { it.id == "xll" }.build(0, 1f)
        assertEquals(1, x.size)
        assertEquals(50f, (x[0] as Stroke).pts[0], 1e-3f)
        val lc = ConstructOps.ops(listOf(poly(-50f, 0f, 50f, 0f), circle(0f, 0f, 20f))).first { it.id == "xlc" }.build(0, 1f)
        assertEquals(2, lc.size)
        val cc = ConstructOps.ops(listOf(circle(0f, 0f, 20f), circle(30f, 0f, 20f))).first { it.id == "xcc" }.build(0, 1f)
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
    }
}
