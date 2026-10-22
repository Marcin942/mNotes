package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.FillErase
import com.mdev.mnotes.core.edit.FloodFill
import com.mdev.mnotes.core.model.FillEl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class FillEraseTest {

    private fun square(l: Float, t: Float, r: Float, b: Float) = floatArrayOf(l, t, r, t, r, b, l, b)

    private fun fill(vararg rings: FloatArray) = FillEl(1, 0xFFFF8000.toInt(), false, rings.toList())

    private fun area(rings: List<FloatArray>): Double {
        // Parzysto-nieparzyście: pole liczone próbkami, bo pierścienie mogą mieć dowolny kierunek.
        val f = fill(*rings.toTypedArray())
        val b = f.bounds
        var hit = 0
        val step = 0.5f
        var y = b.t + step / 2
        while (y < b.b) { var x = b.l + step / 2; while (x < b.r) { if (f.containsPoint(x, y)) hit++; x += step }; y += step }
        return hit * step.toDouble() * step
    }

    @Test
    fun missLeavesFillUntouched() {
        assertNull(FillErase.erase(fill(square(0f, 0f, 100f, 100f)), 200f, 200f, 10f))
    }

    @Test
    fun circleInsideCutsHole() {
        val res = FillErase.erase(fill(square(0f, 0f, 100f, 100f)), 50f, 50f, 10f)!!
        val f = fill(*res.rings.toTypedArray())
        assertFalse(f.containsPoint(50f, 50f))
        assertFalse(f.containsPoint(57f, 50f))
        assertTrue(f.containsPoint(62f, 50f))
        assertTrue(f.containsPoint(5f, 5f))
        assertEquals(10000.0 - PI * 100, area(res.rings), 30.0)
        assertTrue(res.dirty.contains(40f, 40f) && res.dirty.contains(60f, 60f))
    }

    @Test
    fun circleOverEdgeBitesNotch() {
        val res = FillErase.erase(fill(square(0f, 0f, 100f, 100f)), 100f, 50f, 10f)!!
        assertEquals(1, res.rings.size)
        val f = fill(*res.rings.toTypedArray())
        assertFalse(f.containsPoint(95f, 50f))
        assertTrue(f.containsPoint(95f, 30f))
        assertTrue(f.containsPoint(85f, 50f))
        assertFalse(f.containsPoint(105f, 50f))
        assertEquals(10000.0 - PI * 50, area(res.rings), 30.0)
    }

    @Test
    fun circleOverCornerAndWholeFill() {
        val corner = FillErase.erase(fill(square(0f, 0f, 100f, 100f)), 0f, 0f, 20f)!!
        assertEquals(10000.0 - PI * 100, area(corner.rings), 30.0)
        val all = FillErase.erase(fill(square(0f, 0f, 10f, 10f)), 5f, 5f, 50f)!!
        assertTrue(all.rings.isEmpty())
    }

    @Test
    fun stripeCutInTwo() {
        val res = FillErase.erase(fill(square(0f, 0f, 200f, 10f)), 100f, 5f, 15f)!!
        assertEquals(2, res.rings.size)
        val f = fill(*res.rings.toTypedArray())
        assertFalse(f.containsPoint(100f, 5f))
        assertTrue(f.containsPoint(50f, 5f))
        assertTrue(f.containsPoint(150f, 5f))
    }

    @Test
    fun edgeCrossingWithoutVertexInside() {
        // Krawędź przecina koło dwa razy, choć żaden wierzchołek nie leży w kole.
        val res = FillErase.erase(fill(square(0f, 0f, 100f, 100f)), 50f, 102f, 5f)!!
        val f = fill(*res.rings.toTypedArray())
        assertFalse(f.containsPoint(50f, 99f))
        assertTrue(f.containsPoint(40f, 99f))
        assertTrue(f.containsPoint(50f, 90f))
    }

    @Test
    fun annulusFillKeepsHoleWhileErasingAcrossIt() {
        // Wypełnienie z dziurą; gumka przechodzi przez brzeg dziury i zewnętrzny bez psucia reszty.
        val outer = square(0f, 0f, 100f, 100f)
        val hole = square(40f, 40f, 60f, 60f)
        var f = fill(outer, hole)
        val before = area(f.rings)
        var x = 30f
        while (x <= 110f) {
            val res = FillErase.erase(f, x, 50f, 6f)
            if (res != null) f = fill(*res.rings.toTypedArray())
            x += 3f
        }
        for ((x0, y0, want) in listOf(Triple(50f, 50f, false), Triple(35f, 50f, false), Triple(80f, 50f, false), Triple(50f, 20f, true), Triple(80f, 80f, true), Triple(20f, 50f, true), Triple(50f, 38f, true), Triple(50f, 42f, false)))
            assertEquals("($x0, $y0) pierścienie ${f.rings.map { it.toList() }}", want, f.containsPoint(x0, y0))
        val lost = before - area(f.rings)
        // Pas szerokości 12 od x = 30 do 40 i od 60 do 100 (dziura już była pusta) plus półkole na początku.
        assertTrue("starte pole $lost", abs(lost - (50.0 * 12 + PI * 18)) < 15.0)
    }

    @Test
    fun manyStepsStayCheapAndConsistent() {
        // Gęste pociągnięcie gumki po dużym kole: wierzchołków nie przybywa bez końca, a pole się zgadza.
        val n = 400
        val ring = FloatArray(2 * n) { val a = 2 * PI * (it / 2) / n; if (it % 2 == 0) 300f + 200f * kotlin.math.cos(a).toFloat() else 300f + 200f * kotlin.math.sin(a).toFloat() }
        var f = fill(ring)
        var x = 50f
        while (x <= 550f) {
            val res = FillErase.erase(f, x, 300f, 8f)
            if (res != null) f = fill(*res.rings.toTypedArray())
            x += 1f
        }
        assertEquals("pola ${f.rings.map { FloodFill.area(it) }}", 2, f.rings.size)
        assertFalse(f.containsPoint(300f, 300f))
        assertTrue(f.containsPoint(300f, 250f))
        assertTrue(f.containsPoint(300f, 350f))
        assertTrue("wierzchołków ${f.vertexCount}", f.vertexCount < n + 200)
        assertNotNull(f.bounds)
        assertTrue(abs(FloodFill.area(f.rings[0])) > 1000f)
    }
}
