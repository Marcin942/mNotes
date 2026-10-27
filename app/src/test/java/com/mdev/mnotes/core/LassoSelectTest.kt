package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.EraseMath
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class LassoSelectTest {

    private fun line(vararg xy: Float): Stroke {
        val pts = FloatArray(xy.size / 2 * 3)
        for (i in 0 until xy.size / 2) { pts[3 * i] = xy[2 * i]; pts[3 * i + 1] = xy[2 * i + 1]; pts[3 * i + 2] = 1f }
        return Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1.5f, pts)
    }

    private fun rect(x0: Float, y0: Float, x1: Float, y1: Float) = floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1)

    /**
     * B10: prosta (dwa punkty) po przekątnej ramki, której końce wystają o włos poza ramkę: dawniej liczyły się tylko
     * wierzchołki (oba poza), więc linia leżąca w 95 % w ramce nie wpadała do zaznaczenia.
     */
    @Test fun diagonalLineInRectIsSelected() {
        val d = line(10f, 10f, 90f, 90f)
        assertTrue(EraseMath.selectedByLasso(d, rect(14f, 14f, 86f, 86f)))
        // Prostokąt przeciągnięty od dołu do góry (inna kolejność rogów) też.
        assertTrue(EraseMath.selectedByLasso(d, rect(86f, 86f, 14f, 14f)))
        // Linia w większości poza ramką nie wpada.
        assertFalse(EraseMath.selectedByLasso(line(80f, 80f, 300f, 300f), rect(10f, 10f, 90f, 90f)))
    }

    /** B3: ramka na większości rozpoznanej prostej (końce poza ramką) zaznacza ją. */
    @Test fun mostlyCoveredStraightLineIsSelected() {
        assertTrue(EraseMath.selectedByLasso(line(0f, 50f, 100f, 50f), rect(10f, 40f, 95f, 60f)))
        assertFalse(EraseMath.selectedByLasso(line(0f, 50f, 100f, 50f), rect(60f, 40f, 95f, 60f)))
    }

    /** Rozpoznany prostokąt (łamana 5 punktów) w ramce z zapasem. */
    @Test fun rectangleShapeInRectIsSelected() {
        val sq = line(20f, 20f, 80f, 20f, 80f, 80f, 20f, 80f, 20f, 20f)
        assertTrue(EraseMath.selectedByLasso(sq, rect(19f, 19f, 81f, 81f)))
    }

    private fun circle(cx: Float, cy: Float, r: Float, from: Double, n: Int = 64): FloatArray {
        val out = FloatArray(2 * (n + 1))
        for (i in 0..n) { val a = from + 2 * PI * i / n; out[2 * i] = cx + r * cos(a).toFloat(); out[2 * i + 1] = cy + r * sin(a).toFloat() }
        return out
    }

    /** B7: pętla lassa z dwoma nachodzącymi okrążeniami obejmuje też część wspólną. */
    @Test fun overlappingLoopsKeepTheCommonPart() {
        val a = circle(0f, 0f, 50f, 0.0)
        val b = circle(40f, 0f, 50f, 0.0)
        val poly = a + b
        val lens = line(20f, -5f, 20f, 5f)
        assertTrue(EraseMath.selectedByLasso(lens, poly))
        assertTrue(EraseMath.selectedByLasso(line(-35f, -3f, -30f, 3f), poly))
        assertTrue(EraseMath.selectedByLasso(line(75f, -3f, 80f, 3f), poly))
        assertFalse(EraseMath.selectedByLasso(line(200f, 0f, 210f, 0f), poly))
    }

    /** Ósemka (okrążenia w przeciwnych kierunkach): obie pętle są w środku. */
    @Test fun figureEightBothLobesInside() {
        val left = circle(-50f, 0f, 50f, 0.0)
        val right = FloatArray(left.size)
        for (i in 0 until left.size / 2) { right[2 * i] = -left[2 * i]; right[2 * i + 1] = left[2 * i + 1] }
        val poly = left + right
        assertTrue(EraseMath.polygonContains(poly, -50f, 0f))
        assertTrue(EraseMath.polygonContains(poly, 50f, 0f))
        assertFalse(EraseMath.polygonContains(poly, 0f, 80f))
    }

    /** F1: „tylko zahacz” łapie obiekt, który pętla ledwo musnęła; zbalansowane go pomija. */
    @Test fun touchModeCatchesGrazedObjects() {
        val loop = rect(0f, 0f, 50f, 50f)
        val crossing = line(45f, 25f, 200f, 25f)            // tylko 5 pt w pętli
        assertFalse(EraseMath.lassoSelects(crossing, loop, touch = false, slop = 2f))
        assertTrue(EraseMath.lassoSelects(crossing, loop, touch = true, slop = 2f))
        val grazed = line(51f, 60f, 51f, 120f)              // róg pętli jest ok. 10 pt od końca kreski: za daleko
        assertFalse(EraseMath.lassoSelects(grazed, loop, touch = true, slop = 2f))
        val brushed = line(51f, 10f, 51f, 40f)              // biegnie tuż przy boku pętli (1 pt), w całości poza nią
        assertTrue(EraseMath.lassoSelects(brushed, loop, touch = true, slop = 2f))
        assertFalse(EraseMath.lassoSelects(line(80f, 10f, 80f, 40f), loop, touch = true, slop = 2f))
    }

    @Test fun touchModeBoxCornerInside() {
        val img = com.mdev.mnotes.core.model.ImageEl(Ids.next(), "a.png", 60f, 60f, 40f, 40f)   // od (40,40) do (80,80)
        val loop = rect(0f, 0f, 50f, 50f)
        assertFalse(EraseMath.lassoSelects(img, loop, touch = false, slop = 2f))
        assertTrue(EraseMath.lassoSelects(img, loop, touch = true, slop = 2f))
    }

    @Test fun samplesAreSpreadAlongLength() {
        val s = EraseMath.samplesAlong(line(0f, 0f, 100f, 0f), 4)
        assertEquals(8, s.size)
        assertEquals(12.5f, s[0], 1e-3f)
        assertEquals(87.5f, s[6], 1e-3f)
        // Kreska z jednym punktem daje jedną próbkę.
        assertEquals(2, EraseMath.samplesAlong(line(5f, 5f)).size)
    }
}
