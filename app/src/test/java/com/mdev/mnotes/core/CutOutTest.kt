package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.CutOut
import com.mdev.mnotes.core.edit.EraseMath
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** F12: gumka „Wycinanie” tnie kreski dokładnie na obrysie pętli. */
class CutOutTest {
    private fun stroke(vararg xy: Float, poly: Boolean = false): Stroke {
        val pts = FloatArray(xy.size / 2 * 3)
        for (i in 0 until xy.size / 2) { pts[3 * i] = xy[2 * i]; pts[3 * i + 1] = xy[2 * i + 1]; pts[3 * i + 2] = 1f }
        return Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1f, pts, poly = poly)
    }

    private val box = floatArrayOf(40f, -10f, 60f, -10f, 60f, 10f, 40f, 10f)

    @Test fun cutIsExactlyOnTheLoop() {
        val s = stroke(0f, 0f, 30f, 0f, 70f, 0f, 100f, 0f)
        val pieces = CutOut.cutStroke(s, box)!!
        assertEquals(2, pieces.size)
        val a = pieces[0]; val b = pieces[1]
        assertEquals(40f, a[a.size - 3], 1e-4f)   // koniec lewego kawałka na krawędzi x = 40
        assertEquals(60f, b[0], 1e-4f)            // początek prawego na x = 60
        // Oryginalne punkty poza pętlą zostają bez zmian (bez dogęszczania).
        assertEquals(3, a.size / 3)
        assertEquals(3, b.size / 3)
    }

    @Test fun untouchedAndWhollyInside() {
        assertNull(CutOut.cutStroke(stroke(0f, 50f, 100f, 50f), box))
        assertEquals(0, CutOut.cutStroke(stroke(45f, 0f, 55f, 0f), box)!!.size)
    }

    @Test fun pageLevelKeepsOthersAndRemovesInnerBoxes() {
        val far = stroke(0f, 100f, 50f, 100f)
        val cut = stroke(0f, 0f, 100f, 0f)
        val img = ImageEl(Ids.next(), "a.png", 50f, 0f, 6f, 6f)
        val big = ImageEl(Ids.next(), "b.png", 50f, 0f, 60f, 60f)
        val out = CutOut.apply(listOf(far, cut, img, big), box)!!
        assertSame(far, out[0])
        assertEquals(4, out.size)   // far, dwa kawałki, duży obraz (mały obraz w pętli zniknął)
        assertTrue(out.any { it === big })
        assertNull(CutOut.apply(listOf(far), box))
    }

    @Test fun polylineStaysPolylineAndClosedJoins() {
        val sq = stroke(0f, -20f, 50f, -20f, 50f, 20f, 0f, 20f, 0f, -20f, poly = true)
        // Pętla wycina środek górnego boku: zostaje jedna otwarta łamana z uchwytami.
        val loop = floatArrayOf(20f, -30f, 30f, -30f, 30f, -10f, 20f, -10f)
        val out = CutOut.apply(listOf(sq), loop)!!
        assertEquals(1, out.size)
        val st = out[0] as Stroke
        assertTrue(st.poly)
        assertEquals(30f, st.pts[0], 1e-4f)
        assertEquals(20f, st.pts[st.pts.size - 3], 1e-4f)
        assertTrue(EraseMath.polygonContains(loop, 25f, -20f))
    }
}
