package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.EraseMath
import com.mdev.mnotes.core.geom.FloatList
import com.mdev.mnotes.core.model.ElementsEdit
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.pen.PenSpec
import com.mdev.mnotes.core.pen.StrokeBuilder
import com.mdev.mnotes.core.pen.StrokeOutline
import com.mdev.mnotes.core.shapes.ShapeKind
import com.mdev.mnotes.core.shapes.ShapeRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Test

class PenAndShapesTest {

    private fun stroke(vararg xy: Float, width: Float = 2f): Stroke {
        val pts = FloatArray(xy.size / 2 * 3)
        for (i in 0 until xy.size / 2) { pts[3 * i] = xy[2 * i]; pts[3 * i + 1] = xy[2 * i + 1]; pts[3 * i + 2] = 1f }
        return Stroke(Ids.next(), PenKind.BALLPOINT, 0xFF000000.toInt(), width, pts)
    }

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, n: Int = 40): FloatArray {
        val out = FloatArray(2 * n)
        for (i in 0 until n) { val t = i / (n - 1f); out[2 * i] = x0 + (x1 - x0) * t; out[2 * i + 1] = y0 + (y1 - y0) * t }
        return out
    }

    private fun poly(vararg pts: Pair<Float, Float>, per: Int = 20): FloatArray {
        val l = FloatList()
        for (i in 0 until pts.size - 1) {
            for (k in 0 until per) {
                val t = k / per.toFloat()
                l.add(pts[i].first + (pts[i + 1].first - pts[i].first) * t, pts[i].second + (pts[i + 1].second - pts[i].second) * t)
            }
        }
        l.add(pts.last().first, pts.last().second)
        return l.toArray()
    }

    @Test fun penFactorMonotonicInPressure() {
        val s = PenSpec.of(PenKind.FOUNTAIN)
        assertTrue(s.factor(0.1f, 0f) < s.factor(0.9f, 0f))
        assertTrue(s.factor(0.5f, 2f) < s.factor(0.5f, 0f))
        assertTrue(PenSpec.of(PenKind.BALLPOINT).constantWidth)
        assertFalse(PenSpec.of(PenKind.BRUSH).constantWidth)
    }

    @Test fun builderTapersEndsAndKeepsDot() {
        val b = StrokeBuilder()
        b.begin(PenKind.FOUNTAIN, 0xFF000000.toInt(), 3f, 0f, 0.1f)
        for (i in 0..60) b.add(i * 1f, 0f, 0.8f, i * 4L)
        val s = b.finish(1)!!
        assertTrue(s.pts[2] < s.pts[3 * 30 + 2])
        assertTrue(s.pts[3 * (s.count - 1) + 2] < s.pts[3 * 30 + 2])
        b.begin(PenKind.BALLPOINT, 0xFF000000.toInt(), 1.4f, 0f)
        b.add(5f, 5f, 0.5f, 0)
        assertEquals(2, b.finish(2)!!.count)
        b.begin(PenKind.BALLPOINT, 0xFF000000.toInt(), 1.4f, 0f)
        assertNull(b.finish(3))
    }

    @Test fun outlineIsClosedAroundStroke() {
        val s = stroke(0f, 0f, 10f, 0f, 20f, 0f, width = 4f)
        val out = FloatList()
        StrokeOutline.build(s.pts, s.count, s.width, out)
        var minY = 1e9f; var maxY = -1e9f; var minX = 1e9f; var maxX = -1e9f
        for (i in 0 until out.size / 2) { minY = minOf(minY, out[2 * i + 1]); maxY = maxOf(maxY, out[2 * i + 1]); minX = minOf(minX, out[2 * i]); maxX = maxOf(maxX, out[2 * i]) }
        assertEquals(-2f, minY, 0.01f); assertEquals(2f, maxY, 0.01f)
        assertEquals(-2f, minX, 0.05f); assertEquals(22f, maxX, 0.05f)
        out.clear()
        StrokeOutline.build(floatArrayOf(5f, 5f, 1f), 1, 4f, out)
        assertTrue(out.size >= 16)
        assertTrue(StrokeOutline.isConstant(s.pts))
    }

    @Test fun smoothedOutlineStaysWithinTheOutlineBounds() {
        val s = stroke(0f, 0f, 10f, 5f, 20f, 0f, width = 4f)
        val poly = FloatList(); val sm = FloatList()
        StrokeOutline.build(s.pts, s.count, s.width, poly)
        StrokeOutline.smooth(poly, sm)
        // początek + czwórka (cx, cy, ex, ey) na każdy wierzchołek wielokąta
        assertEquals(2 + 2 * poly.size, sm.size)
        var i = 0
        while (i < sm.size) {
            // krzywe przez środki krawędzi leżą w otoczce wypukłej wielokąta → w jego prostokącie
            assertTrue(sm[i] in -2.05f..22.05f); assertTrue(sm[i + 1] in -2.05f..7.05f)
            i += 2
        }
        poly.clear(); StrokeOutline.smooth(poly, sm)
        assertEquals(0, sm.size)
    }

    @Test fun diffRoundTrips() {
        val a = stroke(0f, 0f, 1f, 1f); val b = stroke(5f, 5f, 6f, 6f); val c = stroke(9f, 9f, 8f, 8f)
        val before = PageContent.of(listOf(a, b, c))
        val moved = b.transformed(com.mdev.mnotes.core.geom.Affine.translation(3f, 3f))
        val d = stroke(2f, 2f, 3f, 3f)
        val after = PageContent.of(listOf(a, moved, d))
        val e = ElementsEdit.diff(before, after)
        assertEquals(after.elements.map { it.id }, before.apply(e).elements.map { it.id })
        assertTrue(before.apply(e).elements[1] === moved)
        assertEquals(before.elements.map { it.id }, after.revert(e).elements.map { it.id })
        // Zmiana kolejności
        val reordered = PageContent.of(listOf(c, a, b))
        val e2 = ElementsEdit.diff(before, reordered)
        assertEquals(listOf(c.id, a.id, b.id), before.apply(e2).elements.map { it.id })
        assertTrue(ElementsEdit.diff(before, before).isEmpty)
    }

    @Test fun eraserHitsAndSplits() {
        val s = Stroke(Ids.next(), PenKind.BALLPOINT, 0, 2f, FloatArray(3 * 101).also {
            for (i in 0..100) { it[3 * i] = i.toFloat(); it[3 * i + 1] = 0f; it[3 * i + 2] = 1f }
        })
        assertTrue(EraseMath.hitsStroke(s, 50f, 3f, 3f))
        assertFalse(EraseMath.hitsStroke(s, 50f, 10f, 3f))
        val parts = EraseMath.erasePartial(s, 50f, 0f, 5f)!!
        assertEquals(2, parts.size)
        assertTrue(parts[0][3 * (parts[0].size / 3 - 1)] < 46f)
        assertTrue(parts[1][0] > 54f)
        assertNull(EraseMath.erasePartial(s, 50f, 30f, 5f))
        assertEquals(0, EraseMath.erasePartial(s, 50f, 0f, 80f)!!.size)
    }

    @Test fun lassoContains() {
        val sq = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)
        assertTrue(EraseMath.polygonContains(sq, 5f, 5f))
        assertFalse(EraseMath.polygonContains(sq, 15f, 5f))
        assertTrue(EraseMath.selectedByLasso(stroke(2f, 2f, 8f, 8f), sq))
        assertFalse(EraseMath.selectedByLasso(stroke(20f, 2f, 28f, 8f), sq))
    }

    @Test fun recognizesLine() {
        val rnd = Random(1)
        val l = line(10f, 10f, 200f, 14f)
        for (i in l.indices) l[i] += (rnd.nextFloat() - 0.5f) * 0.8f
        val s = ShapeRecognizer.recognize(l)
        assertNotNull(s); assertEquals(ShapeKind.LINE, s!!.kind)
        assertEquals(s.xy[1], s.xy[3], 0.5f) // przyciągnięta do poziomu (~1°)
    }

    @Test fun recognizesRectTriangleEllipse() {
        val rect = poly(30f to 30f, 230f to 34f, 228f to 150f, 32f to 148f, 30f to 30f)
        assertEquals(ShapeKind.RECT, ShapeRecognizer.recognize(rect)?.kind)
        val tri = poly(100f to 20f, 180f to 160f, 20f to 160f, 100f to 22f)
        assertEquals(ShapeKind.TRIANGLE, ShapeRecognizer.recognize(tri)?.kind)
        val el = FloatArray(2 * 80)
        for (i in 0 until 80) { val a = 2 * PI.toFloat() * i / 79f; el[2 * i] = 100f + 80f * cos(a); el[2 * i + 1] = 100f + 50f * sin(a) }
        assertEquals(ShapeKind.ELLIPSE, ShapeRecognizer.recognize(el)?.kind)
    }

    @Test fun recognizesArrowAndRejectsScribble() {
        val arrow = poly(20f to 100f, 200f to 100f, 180f to 85f, 200f to 100f, 180f to 115f)
        assertEquals(ShapeKind.ARROW, ShapeRecognizer.recognize(arrow)?.kind)
        val rnd = Random(7)
        val scribble = FloatArray(2 * 120) { rnd.nextFloat() * 150f }
        assertNull(ShapeRecognizer.recognize(scribble))
        assertNull(ShapeRecognizer.recognize(floatArrayOf(0f, 0f, 1f, 1f)))
    }
}
