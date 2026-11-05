package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.edit.EraseSession
import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.shapes.Bezier
import com.mdev.mnotes.core.shapes.CurveMemo
import com.mdev.mnotes.core.shapes.PointEdit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** LP-3: krzywa ↔ zwykła kreska bez strat — pamięć punktów, przekształcenia, gumka i zapis wstecznie zgodny. */
class CurveMemoTest {
    // Dwa odcinki z wyraźnym zgięciem: wygładzenie przy dopasowaniu dałoby inne punkty.
    private val ctrl = floatArrayOf(10f, 10f, 10f, 60f, 80f, 60f, 80f, 10f, 80f, -20f, 120f, -20f, 140f, 10f)

    private fun curve(c: FloatArray = ctrl) = Stroke(Ids.next(), PenKind.FOUNTAIN, 0xFF223344.toInt(), 1.4f, Bezier.sample(c), ctrl = c)

    /** Najmniejsza odległość punktu od krzywej (gęsta próbka). */
    private fun distToCurve(c: FloatArray, x: Float, y: Float): Float {
        val segs = CurveMemo.segments(c)
        var best = Float.MAX_VALUE
        val steps = 4000 * segs
        for (k in 0..steps) {
            val q = CurveMemo.point(c, segs * k.toFloat() / steps)
            best = minOf(best, hypot(q[0] - x, q[1] - y))
        }
        return best
    }

    @Test fun curveToStrokeAndBackManyTimesKeepsTheSamePoints() {
        val first = curve()
        var s = first
        repeat(25) {
            val plain = CurveMemo.toPlain(s)!!
            assertNull(plain.ctrl)
            assertArrayEquals(s.pts, plain.pts, 0f)
            s = PointEdit.toCurve(plain)!!
            assertArrayEquals(ctrl, s.ctrl, 0f)
            assertArrayEquals(first.pts, s.pts, 0f)
            assertEquals(first.id, s.id)
        }
    }

    @Test fun plainStrokeWithoutMemoIsStillFittedWithSmoothing() {
        val c = curve()
        val plain = Stroke(c.id, c.pen, c.color, c.width, c.pts)
        val fitted = PointEdit.toCurve(plain)!!
        assertNotNull(fitted.ctrl)
        assertTrue(!fitted.ctrl!!.contentEquals(ctrl))
    }

    @Test fun memoSurvivesSavingAndRestoresTheCurve() {
        var s = curve()
        repeat(5) {
            val plain = CurveMemo.toPlain(s)!!
            val back = PageCodec.decode(PageCodec.encode(PageContent.of(listOf(plain)))).elements[0] as Stroke
            assertNull(back.ctrl)
            assertArrayEquals(ctrl, back.memo, 0.006f)
            s = PointEdit.toCurve(back)!!
            assertArrayEquals(ctrl, s.ctrl, 0.006f)
        }
    }

    @Test fun memoTailDoesNotChangeWhatOldReadersSee() {
        // Starsza wersja czyta rekord do fill i pomija ogon: punkty muszą być te same co w kresce bez pamięci.
        val plain = CurveMemo.toPlain(curve())!!
        val bare = Stroke(plain.id, plain.pen, plain.color, plain.width, plain.pts)
        val a = PageCodec.decode(PageCodec.encode(PageContent.of(listOf(plain)))).elements[0] as Stroke
        val b = PageCodec.decode(PageCodec.encode(PageContent.of(listOf(bare)))).elements[0] as Stroke
        assertArrayEquals(b.pts, a.pts, 0f)
        assertNull(b.memo)
        assertNotNull(a.memo)
    }

    @Test fun closedFilledCurveKeepsFillThroughTheCycle() {
        val loop = floatArrayOf(0f, 0f, 40f, -30f, 80f, 30f, 40f, 40f, 0f, 50f, -40f, 30f, 0f, 0f)
        val c = Bezier.withCtrl(Stroke(Ids.next(), PenKind.FOUNTAIN, 0xFF112233.toInt(), 1f, Bezier.sample(loop), fill = 0x40FF0000), loop)
        val plain = CurveMemo.toPlain(c)!!
        val saved = PageCodec.decode(PageCodec.encode(PageContent.of(listOf(plain)))).elements[0] as Stroke
        assertEquals(c.fill, saved.fill)
        assertEquals(c.fill, PointEdit.toCurve(saved)!!.fill)
    }

    @Test fun subOfFullRangeIsTheSameCurve() {
        assertArrayEquals(ctrl, CurveMemo.sub(ctrl, 0f, 2f), 0f)
        assertArrayEquals(ctrl.copyOfRange(0, 8), CurveMemo.sub(ctrl, 0f, 1f), 0f)
        assertArrayEquals(ctrl.copyOfRange(6, 14), CurveMemo.sub(ctrl, 1f, 2f), 0f)
    }

    @Test fun subCurveLiesOnTheOriginalSection() {
        for ((u0, u1) in listOf(0.3f to 0.8f, 0.25f to 1.6f, 1.1f to 1.9f, 0f to 1.5f, 0.5f to 2f)) {
            val s = CurveMemo.sub(ctrl, u0, u1)!!
            val segs = CurveMemo.segments(s)
            val a = CurveMemo.point(ctrl, u0); val b = CurveMemo.point(ctrl, u1)
            assertEquals(a[0], s[0], 1e-3f); assertEquals(a[1], s[1], 1e-3f)
            assertEquals(b[0], s[s.size - 2], 1e-3f); assertEquals(b[1], s[s.size - 1], 1e-3f)
            for (k in 0..50) {
                val q = CurveMemo.point(s, segs * k / 50f)
                assertTrue("$u0..$u1 k=$k", distToCurve(ctrl, q[0], q[1]) < 0.05f)
            }
            // Środek odcinka parametrem leży w środku parametru oryginału (podział jest dokładny, nie przybliżony).
            if (segs == 1 && u1 <= 1f) {
                val mid = CurveMemo.point(s, 0.5f); val ref = CurveMemo.point(ctrl, (u0 + u1) / 2f)
                assertEquals(ref[0], mid[0], 1e-3f); assertEquals(ref[1], mid[1], 1e-3f)
            }
        }
        assertNull(CurveMemo.sub(ctrl, 1f, 1f))
    }

    @Test fun paramsOfSamplesFollowTheCurve() {
        val pts = Bezier.sample(ctrl)
        val us = CurveMemo.params(ctrl, pts)
        assertEquals(0f, us[0], 0f)
        assertEquals(2f, us[us.size - 1], 0f)
        for (i in 1 until us.size) assertTrue(us[i] >= us[i - 1])
        for (i in us.indices) {
            val q = CurveMemo.point(ctrl, us[i])
            assertTrue("i=$i", hypot(q[0] - pts[3 * i], q[1] - pts[3 * i + 1]) < 0.1f)
        }
    }

    @Test fun affineTransformMovesMemoWithTheStroke() {
        val plain = CurveMemo.toPlain(curve())!!
        val m = Affine.scale(2f, 0.5f, 30f, 10f).then(Affine.rotation(0.7f, 5f, 5f)).then(Affine.translation(12f, -3f))
        val moved = plain.transformed(m)
        val memo = moved.memo!!
        for (k in 0 until ctrl.size / 2) {
            assertEquals(m.mapX(ctrl[2 * k], ctrl[2 * k + 1]), memo[2 * k], 1e-4f)
            assertEquals(m.mapY(ctrl[2 * k], ctrl[2 * k + 1]), memo[2 * k + 1], 1e-4f)
        }
        // Powrót do krzywej po przekształceniu daje przekształconą krzywą, a ta pokrywa się z przekształconą kreską.
        val back = PointEdit.toCurve(moved)!!
        assertArrayEquals(memo, back.ctrl, 0f)
        for (i in 0 until moved.count) assertTrue(distToCurve(memo, moved.pts[3 * i], moved.pts[3 * i + 1]) < 0.1f)
        // Jednoosiowe rozciągnięcie też jest przekształceniem afinicznym.
        val stretched = plain.transformed(Affine.scale(1f, 3f, 0f, 20f))
        assertEquals(ctrl[1] * 3f - 40f, stretched.memo!![1], 1e-4f)
    }

    @Test fun styleLockAndIdKeepMemoButVertexEditDropsIt() {
        val plain = CurveMemo.toPlain(curve())!!
        assertNotNull(plain.withStyle(color = 0xFF00FF00.toInt()).memo)
        assertNotNull(plain.withLocked(true).memo)
        assertNotNull(plain.withId(Ids.next()).memo)
        assertNull(plain.withVertex(3, 0f, 0f).memo)
    }

    @Test fun eraserPiecesRememberTheirExactSubCurves() {
        for (start in listOf(curve(), CurveMemo.toPlain(curve())!!)) {
            val s = EraseSession(listOf(start), pixel = true, keepHighlighter = false)
            // Cięcie w poprzek pierwszego łuku (x = 45) i w poprzek drugiego (x = 110).
            assertTrue(s.sweep(45f, 30f, 45f, 80f, 2f))
            assertTrue(s.sweep(110f, -40f, 110f, 0f, 2f))
            val pieces = s.elements().map { it as Stroke }
            assertEquals(3, pieces.size)
            for (p in pieces) {
                val memo = p.memo
                assertNotNull(memo)
                memo!!
                // Pamiętany odcinek leży na oryginalnej krzywej i pokrywa się z kawałkiem kreski od końca do końca.
                val segs = CurveMemo.segments(memo)
                for (k in 0..40) {
                    val q = CurveMemo.point(memo, segs * k / 40f)
                    assertTrue(distToCurve(ctrl, q[0], q[1]) < 0.05f)
                }
                assertEquals(p.pts[0], memo[0], 0.3f); assertEquals(p.pts[1], memo[1], 0.3f)
                assertEquals(p.pts[3 * p.count - 3], memo[memo.size - 2], 0.3f)
                assertEquals(p.pts[3 * p.count - 2], memo[memo.size - 1], 0.3f)
                for (i in 0 until p.count) assertTrue(distToCurve(memo, p.pts[3 * i], p.pts[3 * i + 1]) < 0.15f)
                // Kawałek wraca do krzywej bez wygładzania.
                assertArrayEquals(memo, PointEdit.toCurve(p)!!.ctrl, 0f)
            }
        }
    }

    @Test fun erasingAPlainStrokeGivesNoMemo() {
        val c = curve()
        val plain = Stroke(c.id, c.pen, c.color, c.width, c.pts)
        val s = EraseSession(listOf(plain), pixel = true, keepHighlighter = false)
        assertTrue(s.sweep(45f, 30f, 45f, 80f, 2f))
        for (e in s.elements()) assertNull((e as Stroke).memo)
    }
}
