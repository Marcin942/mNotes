package com.mdev.mnotes.core

import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.shapes.Bezier
import com.mdev.mnotes.core.shapes.PointEdit
import com.mdev.mnotes.core.shapes.ShapeRecognizer
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

/** F21: edycja punktów kształtu (podwójne dotknięcie, menu punktu). */
class PointEditTest {
    private fun poly(vararg xy: Float): Stroke {
        val pts = FloatArray(xy.size / 2 * 3)
        for (i in 0 until xy.size / 2) { pts[3 * i] = xy[2 * i]; pts[3 * i + 1] = xy[2 * i + 1]; pts[3 * i + 2] = 1f }
        return Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1f, pts, poly = true)
    }

    @Test fun ellipseBecomesFourArcCurveOnTheEllipse() {
        val xy = FloatArray(2 * 60) { k -> val a = 2 * PI * (k / 2) / 59; if (k % 2 == 0) 100f + 60f * cos(a).toFloat() else 100f + 30f * sin(a).toFloat() }
        val shape = ShapeRecognizer.recognize(xy)!!
        val pts = FloatArray(shape.xy.size / 2 * 3) { i -> if (i % 3 == 2) 1f else shape.xy[2 * (i / 3) + i % 3] }
        val ell = Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1f, pts, ellipse = true)
        val c = PointEdit.toCurve(ell)!!
        assertEquals(5, Bezier.anchors(c.ctrl!!))
        // Każdy punkt krzywej leży na elipsie (z dokładnością do ułamka punktu).
        val s = c.pts
        for (i in 0 until s.size / 3) {
            val dx = (s[3 * i] - 100f) / 60f; val dy = (s[3 * i + 1] - 100f) / 30f
            assertEquals(1f, hypot(dx, dy), 0.01f)
        }
    }

    @Test fun freehandBecomesCurveAndPolyDoesNot() {
        val wave = Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1f, FloatArray(3 * 50) { i -> when (i % 3) { 0 -> (i / 3) * 4f; 1 -> 20f * sin((i / 3) * 0.2f); else -> 1f } })
        assertNotNull(PointEdit.toCurve(wave)?.ctrl)
        assertNull(PointEdit.toCurve(poly(0f, 0f, 10f, 10f)))
    }

    @Test fun removeAndInsertVertices() {
        val sq = poly(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f, 0f, 0f)
        val tri = PointEdit.removeVertex(sq, 1)!!
        assertTrue(tri.closed)
        assertEquals(3, tri.vertexCount)
        assertNull(PointEdit.removeVertex(poly(0f, 0f, 5f, 5f), 0))
        val ins = PointEdit.insertAfter(poly(0f, 0f, 10f, 0f), 0)!!
        assertEquals(3, ins.count)
        assertEquals(5f, ins.pts[3], 1e-4f)
    }

    @Test fun anchorRemovalAndSmoothToggle() {
        val c = floatArrayOf(0f, 0f, 10f, 10f, 20f, 10f, 30f, 0f, 40f, -10f, 50f, -10f, 60f, 0f)
        val r = PointEdit.removeAnchor(c, 3)!!
        assertEquals(2, Bezier.anchors(r))
        assertEquals(60f, r[r.size - 2], 0f)
        assertNull(PointEdit.removeAnchor(r, 0))
        // Kotwica środkowa wyżej niż sąsiednie (inaczej „ostra” z uchwytami do sąsiadów byłaby i tak prosta).
        val sharp = floatArrayOf(0f, 0f, 10f, 10f, 20f, 30f, 30f, 20f, 40f, 30f, 50f, 10f, 60f, 0f)
        assertFalse(Bezier.smooth(sharp, 3))
        assertTrue(Bezier.smooth(PointEdit.toggleSmooth(sharp, 3), 3))
        assertFalse(Bezier.smooth(PointEdit.toggleSmooth(PointEdit.toggleSmooth(sharp, 3), 3), 3))
    }
}
