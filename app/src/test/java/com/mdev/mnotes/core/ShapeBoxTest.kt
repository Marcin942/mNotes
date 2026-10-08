package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.shapes.ShapeBox
import com.mdev.mnotes.core.shapes.ShapeKind
import com.mdev.mnotes.core.shapes.ShapeRecognizer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeBoxTest {

    private fun stroke(xy: FloatArray, poly: Boolean, ellipse: Boolean): Stroke {
        val n = xy.size / 2
        val pts = FloatArray(3 * n)
        for (i in 0 until n) { pts[3 * i] = xy[2 * i]; pts[3 * i + 1] = xy[2 * i + 1]; pts[3 * i + 2] = 1f }
        return Stroke(1, PenKind.BALLPOINT, 0xFF000000.toInt(), 2f, pts, false, poly, ellipse)
    }

    private fun rect(cx: Float, cy: Float, w: Float, h: Float, th: Float = 0f): Stroke {
        val c = cos(th); val s = sin(th)
        fun p(a: Float, b: Float) = floatArrayOf(cx + a * w / 2 * c - b * h / 2 * s, cy + a * w / 2 * s + b * h / 2 * c)
        val v = listOf(p(-1f, -1f), p(1f, -1f), p(1f, 1f), p(-1f, 1f), p(-1f, -1f))
        return stroke(v.flatMap { it.toList() }.toFloatArray(), poly = true, ellipse = false)
    }

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float): Stroke {
        val xy = FloatArray(98)
        for (k in 0..48) { val a = 2f * PI.toFloat() * k / 48; xy[2 * k] = cx + rx * cos(a); xy[2 * k + 1] = cy + ry * sin(a) }
        return stroke(xy, poly = false, ellipse = true)
    }

    private fun bbox(s: Stroke): FloatArray {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in 0 until s.count) { l = minOf(l, s.pts[3 * i]); r = maxOf(r, s.pts[3 * i]); t = minOf(t, s.pts[3 * i + 1]); b = maxOf(b, s.pts[3 * i + 1]) }
        return floatArrayOf(l, t, r, b)
    }

    private fun roundTrip(s: Stroke) = PageCodec.decode(PageCodec.encode(PageContent.of(listOf(s)))).elements.single() as Stroke

    private fun assertNear(e: Float, a: Float, tol: Float = 0.01f) = assertTrue("oczekiwano $e, jest $a", abs(e - a) <= tol)

    @Test fun rectFrameDetected() {
        val f = ShapeBox.frameOf(rect(100f, 80f, 60f, 40f))!!
        assertNear(100f, f.cx); assertNear(80f, f.cy); assertNear(30f, f.hw); assertNear(20f, f.hh)
    }

    @Test fun rotatedRectFrameDetected() {
        val f = ShapeBox.frameOf(rect(100f, 80f, 60f, 40f, 0.5f))!!
        assertNear(30f, f.hw); assertNear(20f, f.hh)
        assertNear(cos(0.5f), f.ux); assertNear(sin(0.5f), f.uy)
    }

    @Test fun ellipseFrameDetected() {
        val f = ShapeBox.frameOf(ellipse(50f, 60f, 40f, 25f))!!
        assertNear(50f, f.cx); assertNear(60f, f.cy); assertNear(40f, f.hw); assertNear(25f, f.hh)
    }

    @Test fun nonShapesHaveNoFrame() {
        // Zdeformowany prostokąt (przesunięty jeden wierzchołek), trójkąt, zwykła kreska i zablokowana elipsa.
        val r = rect(0f, 0f, 60f, 40f).withVertex(2, 50f, 30f)
        assertNull(ShapeBox.frameOf(r))
        assertNull(ShapeBox.frameOf(stroke(floatArrayOf(0f, 0f, 50f, 0f, 25f, 40f, 0f, 0f), poly = true, ellipse = false)))
        assertNull(ShapeBox.frameOf(stroke(floatArrayOf(0f, 0f, 50f, 0f, 25f, 40f), poly = false, ellipse = false)))
        assertNull(ShapeBox.frameOf(ellipse(0f, 0f, 30f, 20f).withLocked(true)))
    }

    @Test fun rightEdgeResizeKeepsLeftEdge() {
        val s = rect(100f, 100f, 60f, 40f)
        val f = ShapeBox.frameOf(s)!!
        val g = ShapeBox.resized(f, 3, 200f, 100f, 0f) // prawy środek → x = 200
        val out = ShapeBox.applyTo(s, f, g)
        val b = bbox(out)
        assertNear(70f, b[0]); assertNear(200f, b[2]); assertNear(80f, b[1]); assertNear(120f, b[3])
    }

    @Test fun cornerResizeKeepsOppositeCorner() {
        val s = rect(100f, 100f, 60f, 40f) // 70..130, 80..120
        val f = ShapeBox.frameOf(s)!!
        val g = ShapeBox.resized(f, 0, 40f, 50f, 0f) // lewy górny → (40, 50)
        val b = bbox(ShapeBox.applyTo(s, f, g))
        assertNear(40f, b[0]); assertNear(50f, b[1]); assertNear(130f, b[2]); assertNear(120f, b[3])
    }

    @Test fun squareSnapMakesEqualSides() {
        val s = rect(100f, 100f, 60f, 60f)
        val f = ShapeBox.frameOf(s)!!
        val g = ShapeBox.resized(f, 3, 134f, 100f, 8f) // szerokość 64 przy 60 wysokości → kwadrat
        assertNear(g.hw, g.hh)
        val b = bbox(ShapeBox.applyTo(s, f, g))
        assertNear(70f, b[0]) // lewa krawędź stoi
        assertNear(b[2] - b[0], b[3] - b[1])
    }

    @Test fun noSnapOutsideTolerance() {
        val s = rect(100f, 100f, 60f, 60f)
        val f = ShapeBox.frameOf(s)!!
        val g = ShapeBox.resized(f, 3, 160f, 100f, 8f)
        assertNear(45f, g.hw); assertNear(30f, g.hh)
    }

    @Test fun cannotFlipOrCollapse() {
        val s = rect(100f, 100f, 60f, 40f)
        val f = ShapeBox.frameOf(s)!!
        val g = ShapeBox.resized(f, 3, 0f, 100f, 0f) // prawy bok za lewy
        assertNear(ShapeBox.MIN_SIZE / 2f, g.hw)
        val b = bbox(ShapeBox.applyTo(s, f, g))
        assertNear(70f, b[0]); assertNear(70f + ShapeBox.MIN_SIZE, b[2])
    }

    @Test fun rotatedRectResizesAlongItsOwnAxes() {
        val th = 0.6f
        val s = rect(100f, 100f, 60f, 40f, th)
        val f = ShapeBox.frameOf(s)!!
        // Przeciągnięcie prawego środka o 30 pt wzdłuż osi szerokości powiększa szerokość o 30, wysokość bez zmian.
        val hx = ShapeBox.handleX(f, 3); val hy = ShapeBox.handleY(f, 3)
        val g = ShapeBox.resized(f, 3, hx + 30f * cos(th), hy + 30f * sin(th), 0f)
        assertNear(45f, g.hw, 0.05f); assertNear(20f, g.hh, 0.05f)
        val out = ShapeBox.applyTo(s, f, g)
        val f2 = ShapeBox.frameOf(out)!! // nadal idealny prostokąt
        assertNear(45f, f2.hw, 0.05f)
        // Lewy bok (p0→p3) nie ruszył się.
        assertNear(s.pts[0], out.pts[0], 0.05f); assertNear(s.pts[1], out.pts[1], 0.05f)
    }

    @Test fun circleBecomesEllipse() {
        val s = ellipse(100f, 100f, 30f, 30f)
        val f = ShapeBox.frameOf(s)!!
        val g = ShapeBox.resized(f, 3, 180f, 100f, 0f) // prawy środek: 70..180
        val out = ShapeBox.applyTo(s, f, g)
        val b = bbox(out)
        assertNear(70f, b[0], 0.05f); assertNear(180f, b[2], 0.05f)
        assertNear(70f, b[1], 0.05f); assertNear(130f, b[3], 0.05f)
        assertTrue(out.ellipse)
        // Punkty leżą na elipsie o półosiach 55 i 30 ze środkiem (125, 100).
        for (i in 0 until out.count) {
            val dx = (out.pts[3 * i] - 125f) / 55f; val dy = (out.pts[3 * i + 1] - 100f) / 30f
            assertNear(1f, hypot(dx, dy), 0.001f)
        }
        // Kolejna zmiana nadal rozpoznaje ramkę.
        assertNotNull(ShapeBox.frameOf(out))
    }

    @Test fun ellipseSnapsBackToCircle() {
        val s = ellipse(100f, 100f, 30f, 40f)
        val f = ShapeBox.frameOf(s)!!
        val g = ShapeBox.resized(f, 5, 100f, 127f, 8f) // dolny środek: wysokość 67 (górna krawędź y=60) przy szerokości 60
        assertNear(g.hw, g.hh)
    }

    @Test fun closedStaysClosedAndPressureKept() {
        val s = rect(100f, 100f, 60f, 40f)
        val f = ShapeBox.frameOf(s)!!
        val out = ShapeBox.applyTo(s, f, ShapeBox.resized(f, 2, 170f, 20f, 0f))
        assertTrue(out.closed)
        for (i in 0 until out.count) assertEquals(1f, out.pts[3 * i + 2], 0f)
        assertEquals(s.width, out.width, 0f)
    }

    @Test fun survivesUniformTransformAndCodec() {
        val s = ellipse(100f, 100f, 30f, 20f)
        val t = s.transformed(Affine.rotation(0.7f, 10f, 10f))
        assertTrue(t.ellipse)
        val f = ShapeBox.frameOf(t)!!
        assertNear(30f, f.hw, 0.05f); assertNear(20f, f.hh, 0.05f)
        // Zapis i odczyt zachowują znacznik (a ramka po kwantyzacji 0,01 pt nadal się wykrywa).
        val back = roundTrip(t)
        assertTrue(back.ellipse)
        assertNotNull(ShapeBox.frameOf(back))
        val r = rect(100f, 100f, 33.33f, 21.77f, 0.3f)
        assertFalse(r.ellipse)
        assertNotNull(ShapeBox.frameOf(roundTrip(r)))
    }

    @Test fun recognizedShapesAreResizable() {
        // Wyniki rozpoznawania (koło i kwadrat narysowane „ręcznie”) mają ramkę; kolejne wyniki to ten sam układ.
        val circ = FloatArray(2 * 60)
        for (k in 0 until 60) { val a = 2f * PI.toFloat() * k / 59; circ[2 * k] = 200f + 50f * cos(a); circ[2 * k + 1] = 200f + 50f * sin(a) }
        val sh = ShapeRecognizer.recognize(circ)!!
        assertEquals(ShapeKind.ELLIPSE, sh.kind)
        assertNotNull(ShapeBox.frameOf(stroke(sh.xy, sh.poly, sh.kind == ShapeKind.ELLIPSE)))
        val sq = floatArrayOf(0f, 0f, 40f, 0f, 80f, 0f, 80f, 40f, 80f, 80f, 40f, 80f, 0f, 80f, 0f, 40f, 0f, 1f)
        val sr = ShapeRecognizer.recognize(sq)!!
        assertEquals(ShapeKind.RECT, sr.kind)
        assertNotNull(ShapeBox.frameOf(stroke(sr.xy, sr.poly, false)))
    }

    // — regulacja prostokąta piórem po rozpoznaniu: poziom → szerokość, pion → wysokość —

    private fun cornersOf(cx: Float, cy: Float, w: Float, h: Float) = floatArrayOf(
        cx - w / 2, cy - h / 2, cx + w / 2, cy - h / 2, cx + w / 2, cy + h / 2, cx - w / 2, cy + h / 2, cx - w / 2, cy - h / 2,
    )

    @Test fun dragHorizontalChangesOnlyWidth() {
        val f = ShapeBox.frameOfCorners(cornersOf(100f, 100f, 60f, 40f))!!
        val g = ShapeBox.dragged(f, 130f, 120f, 150f, 120f, 0f)
        assertNear(50f, g.hw); assertNear(20f, g.hh); assertNear(100f, g.cx); assertNear(100f, g.cy)
    }

    @Test fun dragVerticalChangesOnlyHeight() {
        val f = ShapeBox.frameOfCorners(cornersOf(100f, 100f, 60f, 40f))!!
        val g = ShapeBox.dragged(f, 70f, 80f, 70f, 50f, 0f) // lewy górny róg w górę
        assertNear(30f, g.hw); assertNear(50f, g.hh)
    }

    @Test fun dragDiagonalChangesBothIndependently() {
        val f = ShapeBox.frameOfCorners(cornersOf(100f, 100f, 60f, 40f))!!
        val g = ShapeBox.dragged(f, 130f, 120f, 140f, 140f, 0f)
        assertNear(40f, g.hw); assertNear(40f, g.hh)
    }

    @Test fun dragSnapsToSquareAndNeverCollapses() {
        val f = ShapeBox.frameOfCorners(cornersOf(100f, 100f, 60f, 40f))!!
        val sq = ShapeBox.dragged(f, 130f, 120f, 140f, 120f, 6f) // 80 × 40 → bez przyciągania
        assertNear(40f, sq.hw); assertNear(20f, sq.hh)
        val eq = ShapeBox.dragged(f, 130f, 120f, 127f, 120f, 6f) // 54 × 40 → nadal ≥ 6
        assertNear(27f, eq.hw)
        val near = ShapeBox.dragged(f, 130f, 120f, 123f, 120f, 6f) // 46 × 40 → różnica 6 → nie
        assertNear(23f, near.hw)
        val snap = ShapeBox.dragged(f, 130f, 120f, 121f, 120f, 6f) // 42 × 40 → kwadrat 41
        assertEquals(snap.hw, snap.hh, 0f)
        val tiny = ShapeBox.dragged(f, 130f, 120f, -500f, 120f, 0f)
        assertNear(ShapeBox.MIN_SIZE / 2f, tiny.hw)
    }

    @Test fun dragRotatedRectUsesItsOwnAxes() {
        val c = cos(0.5f); val s = sin(0.5f)
        val st = rect(100f, 100f, 60f, 40f, 0.5f)
        val f = ShapeBox.frameOfCorners(FloatArray(10) { i -> if (i % 2 == 0) st.pts[3 * (i / 2)] else st.pts[3 * (i / 2) + 1] })!!
        // Pióro na prawej stronie (oś szerokości) przesunięte wzdłuż osi szerokości o 10 pt.
        val dx = 10f * c; val dy = 10f * s
        val g = ShapeBox.dragged(f, f.x(1f, 1f), f.y(1f, 1f), f.x(1f, 1f) + dx, f.y(1f, 1f) + dy, 0f)
        assertNear(40f, g.hw, 0.05f); assertNear(20f, g.hh, 0.05f)
    }
}
