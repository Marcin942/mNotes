package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.EraseMath
import com.mdev.mnotes.core.edit.EraseSession
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F3: rozcięta prosta (łamana z uchwytami) daje dwie proste z uchwytami na końcach, a nie zwykłą gęstą kreskę. */
class PolyCutTest {

    private fun poly(vararg xy: Float): Stroke {
        val pts = FloatArray(xy.size / 2 * 3)
        for (i in 0 until xy.size / 2) { pts[3 * i] = xy[2 * i]; pts[3 * i + 1] = xy[2 * i + 1]; pts[3 * i + 2] = 1f }
        return Stroke(Ids.next(), PenKind.BALLPOINT, 0, 2f, pts, poly = true)
    }

    @Test fun lineCutInTheMiddleGivesTwoLines() {
        val s = EraseSession(listOf(poly(0f, 0f, 100f, 0f)), pixel = true, keepHighlighter = false)
        assertTrue(s.sweep(50f, -10f, 50f, 10f, 4f))
        val out = s.elements().map { it as Stroke }
        assertEquals(2, out.size)
        for (st in out) { assertTrue(st.poly); assertEquals(2, st.count) }
        val a = out[0]; val b = out[1]
        assertEquals(0f, a.pts[0], 1e-4f)
        // Punkt cięcia leży na brzegu gumki (promień 4 + 0,35 × pół grubości), nie dalej niż o ułamek punktu.
        assertEquals(50f - 4.35f, a.pts[3], 0.05f)
        assertEquals(50f + 4.35f, b.pts[0], 0.05f)
        assertEquals(100f, b.pts[3], 1e-4f)
    }

    @Test fun cutNearEndLeavesOneLine() {
        val res = EraseMath.erasePoly(poly(0f, 0f, 100f, 0f), 98f, 0f, 98f, 0f, 5f)!!
        assertEquals(1, res.pieces.size)
        assertEquals(2, res.pieces[0].size / 3)
    }

    @Test fun closedRectangleCutOnceBecomesOpenPolyline() {
        val rect = poly(0f, 0f, 100f, 0f, 100f, 60f, 0f, 60f, 0f, 0f)
        assertTrue(rect.closed)
        val res = EraseMath.erasePoly(rect, 50f, 0f, 50f, 0f, 5f)!!
        assertEquals(1, res.pieces.size)
        val q = res.pieces[0]
        // cięcie → (100,0) → (100,60) → (0,60) → (0,0) → cięcie
        assertEquals(6, q.size / 3)
        assertEquals(100f, q[3], 1e-4f); assertEquals(0f, q[4], 1e-4f)
        assertEquals(0f, q[12], 1e-4f); assertEquals(0f, q[13], 1e-4f)
    }

    @Test fun polylineKeepsVerticesOnBothSides() {
        // Litera Z: cięcie przekątnej zostawia dwie łamane, każda z wierzchołkiem rogu.
        val z = poly(0f, 0f, 60f, 0f, 0f, 60f, 60f, 60f)
        val res = EraseMath.erasePoly(z, 30f, 30f, 30f, 30f, 3f)!!
        assertEquals(2, res.pieces.size)
        assertEquals(3, res.pieces[0].size / 3)
        assertEquals(3, res.pieces[1].size / 3)
    }

    @Test fun missLeavesStroke() {
        assertNull(EraseMath.erasePoly(poly(0f, 0f, 100f, 0f), 50f, 20f, 60f, 20f, 3f))
    }
}
