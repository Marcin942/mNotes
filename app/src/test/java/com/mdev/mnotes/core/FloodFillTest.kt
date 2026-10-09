package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.edit.EraseMath
import com.mdev.mnotes.core.edit.FloodFill
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class FloodFillTest {
    private val black = 0xFF000000.toInt()

    private fun circle(id: Int, cx: Float, cy: Float, r: Float, width: Float = 2f): Stroke {
        val n = 97
        val pts = FloatArray(n * 3)
        for (i in 0 until n) {
            val a = 2 * PI * i / (n - 1)
            pts[3 * i] = cx + r * cos(a).toFloat(); pts[3 * i + 1] = cy + r * sin(a).toFloat(); pts[3 * i + 2] = 1f
        }
        return Stroke(id, PenKind.BALLPOINT, black, width, pts)
    }

    private fun fillOf(rings: List<FloatArray>) = FillEl(1, 0xFFFF0000.toInt(), false, rings)

    @Test
    fun annulusBetweenTwoCircles() {
        val els = listOf(circle(1, 300f, 400f, 50f), circle(2, 300f, 400f, 150f))
        val rings = FloodFill.fill(els, 600f, 800f, 300f + 100f, 400f)!!
        val f = fillOf(rings)
        assertTrue(f.containsPoint(300f, 400f + 100f))
        assertTrue(f.containsPoint(300f - 120f, 400f))
        assertFalse("środek r1 nie jest wypełniony", f.containsPoint(300f, 400f))
        assertFalse("poza r2 nie jest wypełnione", f.containsPoint(300f, 400f + 200f))
        assertFalse(f.containsPoint(20f, 20f))
        // Wypełnienie wchodzi pod linie: na samych okręgach też jest kolor.
        assertTrue(f.containsPoint(300f + 150f, 400f))
        assertTrue(f.containsPoint(300f + 50f, 400f))
        // Pole ≈ π(r2² − r1²), z zapasem na wejście pod linie.
        val area = rings.sumOf { FloodFill.area(it).toDouble() }.let { abs(it) }
        val expect = PI * (150.0 * 150.0 - 50.0 * 50.0)
        assertTrue("pole $area", abs(area - expect) / expect < 0.08)
    }

    @Test
    fun unboundedFillsWholePageExceptClosedShapes() {
        val els = listOf(circle(1, 300f, 400f, 50f))
        val f = fillOf(FloodFill.fill(els, 600f, 800f, 10f, 10f)!!)
        assertTrue(f.containsPoint(590f, 790f))
        assertTrue(f.containsPoint(300f, 300f))
        assertFalse(f.containsPoint(300f, 400f))
        assertTrue(f.bounds.l <= 0.01f && f.bounds.t <= 0.01f && f.bounds.r >= 599.9f && f.bounds.b >= 799.9f)
        assertTrue(f.bounds.r <= 600f && f.bounds.b <= 800f)
    }

    @Test
    fun emptyPageFillsEverything() {
        val f = fillOf(FloodFill.fill(emptyList(), 600f, 800f, 300f, 400f)!!)
        assertEquals(1, f.rings.size)
        assertEquals(4, f.rings[0].size / 2)
        assertTrue(f.containsPoint(1f, 799f))
    }

    @Test
    fun tapOnLineOrOutsidePageDoesNothing() {
        val els = listOf(circle(1, 300f, 400f, 50f, width = 6f))
        assertNull(FloodFill.fill(els, 600f, 800f, 350f, 400f))
        assertNull(FloodFill.fill(els, 600f, 800f, -5f, 400f))
        assertNull(FloodFill.fill(els, 600f, 800f, 300f, 801f))
    }

    @Test
    fun existingFillIsNotAWall() {
        val els = listOf(circle(1, 300f, 400f, 50f), fillOf(listOf(floatArrayOf(0f, 0f, 600f, 0f, 600f, 800f, 0f, 800f))))
        val f = fillOf(FloodFill.fill(els, 600f, 800f, 300f, 400f)!!)
        assertTrue(f.containsPoint(300f, 400f))
        assertFalse(f.containsPoint(300f, 300f))
    }

    @Test
    fun codecRoundTripAndLasso() {
        val f = FillEl(5, 0x80FFEE00.toInt(), true, listOf(floatArrayOf(10f, 10f, 110f, 10.25f, 60f, 90f), floatArrayOf(50f, 30f, 60f, 30f, 55f, 40f)), locked = true)
        val back = PageCodec.decode(PageCodec.encode(PageContent.of(listOf(circle(1, 0f, 0f, 5f), f)))).elements[1] as FillEl
        assertEquals(f.color, back.color)
        assertTrue(back.hl && back.locked)
        assertEquals(2, back.rings.size)
        for (k in f.rings.indices) for (i in f.rings[k].indices) assertEquals(f.rings[k][i], back.rings[k][i], 0.006f)
        assertTrue(EraseMath.selectedByLasso(f, floatArrayOf(0f, 0f, 200f, 0f, 200f, 200f, 0f, 200f)))
        assertFalse(EraseMath.selectedByLasso(f, floatArrayOf(300f, 0f, 400f, 0f, 400f, 100f)))
        assertTrue(EraseMath.hitsElement(f, 30f, 20f, 1f))
        assertFalse(EraseMath.hitsElement(f, 55f, 33f, 1f))
    }
}
