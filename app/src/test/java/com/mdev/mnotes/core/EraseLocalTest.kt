package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.EraseMath
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.pen.LineStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EraseLocalTest {

    /** Długi poziomy zakreślacz: punkty co 5 pt od x = 0 do x = 1000. */
    private fun longLine(line: LineStyle = LineStyle.SOLID, poly: Boolean = false): Stroke {
        val pts = FloatArray(3 * 201)
        for (i in 0..200) { pts[3 * i] = i * 5f; pts[3 * i + 1] = 100f; pts[3 * i + 2] = 1f }
        return Stroke(Ids.next(), PenKind.HIGHLIGHTER, 0, 12f, pts, poly = poly, line = line)
    }

    @Test fun cutKeepsDistantPointsUnchanged() {
        val s = longLine()
        val res = EraseMath.eraseLocal(s, 500f, 100f, 6f)!!
        assertEquals(2, res.pieces.size)
        val a = res.pieces[0]
        // Daleko od gumki kawałek ma dokładnie te same punkty co kreska, więc rysuje się co do piksela tak samo.
        for (i in 0 until 90) for (k in 0..2) assertEquals(s.pts[3 * i + k], a[3 * i + k], 0f)
        val b = res.pieces[1]
        val tail = b.size / 3
        for (i in 0 until 90) for (k in 0..2) assertEquals(s.pts[3 * (200 - i) + k], b[3 * (tail - 1 - i) + k], 0f)
    }

    @Test fun dirtyAreaIsLocal() {
        val s = longLine()
        val res = EraseMath.eraseLocal(s, 500f, 100f, 6f)!!
        val d = res.dirty
        assertTrue(d.contains(494f, 94f) && d.contains(506f, 106f))
        assertTrue("obszar ${d.w} pt", d.w < 60f)
        // Końce kawałków przy cięciu leżą w przemalowanym obszarze.
        val a = res.pieces[0]; val b = res.pieces[1]
        assertTrue(d.contains(a[a.size - 3], a[a.size - 2]))
        assertTrue(d.contains(b[0], b[1]))
    }

    @Test fun patternedLineRepaintsWhole() {
        val s = longLine(LineStyle.DASHED)
        assertEquals(s.bounds, EraseMath.eraseLocal(s, 500f, 100f, 6f)!!.dirty)
    }

    @Test fun polylineKeepsDenseShape() {
        val s = longLine(poly = true)
        val res = EraseMath.eraseLocal(s, 500f, 100f, 6f)!!
        assertEquals(s.bounds, res.dirty)
        assertTrue(res.pieces[0].size / 3 > 100)
    }

    @Test fun widthOnlyTouchLeavesStroke() {
        val s = longLine()
        // Okrąg dotyka brzegu kreski, ale nie zabiera żadnego punktu osi: kreska zostaje bez zmian.
        assertTrue(EraseMath.hitsStroke(s, 500f, 106f, 1f))
        assertNull(EraseMath.eraseLocal(s, 500f, 106f, 1f))
    }

    @Test fun selfOverlapRepaintsWhole() {
        // Zawrócenie: tam i z powrotem 4 pt niżej; po przecięciu na zakręcie oba kawałki nachodzą na siebie na całej długości.
        val pts = FloatArray(3 * 42)
        for (i in 0..20) { pts[3 * i] = i * 10f; pts[3 * i + 1] = 0f; pts[3 * i + 2] = 1f }
        for (i in 0..20) { pts[3 * (21 + i)] = (20 - i) * 10f; pts[3 * (21 + i) + 1] = 4f; pts[3 * (21 + i) + 2] = 1f }
        val s = Stroke(Ids.next(), PenKind.HIGHLIGHTER, 0, 12f, pts)
        val res = EraseMath.eraseLocal(s, 200f, 2f, 5f)!!
        assertEquals(2, res.pieces.size)
        assertEquals(s.bounds, res.dirty)
    }

    @Test fun pieceWithOtherAveragePressureRepaintsWhole() {
        // Stała grubość z drobnymi różnicami nacisku: kawałek o innej średniej rysowałby się inną grubością na całej długości.
        val pts = FloatArray(3 * 201)
        for (i in 0..200) { pts[3 * i] = i * 5f; pts[3 * i + 1] = 100f; pts[3 * i + 2] = if (i < 100) 0.995f else 1f }
        val s = Stroke(Ids.next(), PenKind.BALLPOINT, 0, 2f, pts)
        val res = EraseMath.eraseLocal(s, 500f, 100f, 6f)!!
        assertEquals(s.bounds, res.dirty)
    }
}
