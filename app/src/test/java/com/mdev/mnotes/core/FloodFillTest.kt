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

    private fun line(id: Int, vararg xy: Float): Stroke {
        val n = xy.size / 2
        val pts = FloatArray(n * 3)
        for (i in 0 until n) { pts[3 * i] = xy[2 * i]; pts[3 * i + 1] = xy[2 * i + 1]; pts[3 * i + 2] = 1f }
        return Stroke(id, PenKind.BALLPOINT, black, 2f, pts)
    }

    /** Okrąg ze szparą na prawej stronie: końce kreski są [gap] pt od siebie (licząc grubość kreski). */
    private fun gappedCircle(cx: Float, cy: Float, r: Float, gap: Float): Stroke {
        val a0 = kotlin.math.asin((gap + 2f) / 2f / r)
        val n = 200
        val pts = FloatArray(n * 3)
        for (i in 0 until n) {
            val a = a0 + (2 * PI - 2 * a0) * i / (n - 1)
            pts[3 * i] = cx + r * cos(a).toFloat(); pts[3 * i + 1] = cy + r * sin(a).toFloat(); pts[3 * i + 2] = 1f
        }
        return Stroke(1, PenKind.BALLPOINT, black, 2f, pts)
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

    @Test
    fun sharpCornerFilledToTheTipAlsoWithClosedGaps() {
        // Klin o kącie ~14° z wierzchołkiem w (100, 700), domknięty pionową linią x = 500.
        val els = listOf(line(1, 100f, 700f, 500f, 600f), line(2, 100f, 700f, 500f, 700f), line(3, 500f, 590f, 500f, 710f))
        val half = kotlin.math.atan(100f / 400f) / 2
        for (close in listOf(false, true)) {
            val f = fillOf(FloodFill.fill(els, 600f, 800f, 400f, 680f, closeGaps = close)!!)
            // 12 pt od wierzchołka klin ma ~3 pt szerokości: kreski zajmują 2 pt, pogrubione ściany zakryłyby resztę.
            val d = 12f
            assertTrue("czubek klina, domykanie=$close", f.containsPoint(100f + d * cos(half), 700f - d * sin(half)))
            assertFalse(f.containsPoint(300f, 300f))
        }
    }

    @Test
    fun smallGapLeaksOnlyWithoutGapClosing() {
        val els = listOf(gappedCircle(300f, 400f, 100f, 1.5f))
        val open = fillOf(FloodFill.fill(els, 600f, 800f, 300f, 400f)!!)
        assertTrue("bez domykania szpara przecieka", open.containsPoint(20f, 20f))
        val closed = fillOf(FloodFill.fill(els, 600f, 800f, 300f, 400f, closeGaps = true)!!)
        assertFalse(closed.containsPoint(20f, 20f))
        assertTrue(closed.containsPoint(300f + 95f, 400f + 25f))
        // Bez otoczki na zewnątrz kształtu: pas zapasu za linią należy do obszaru zewnętrznego.
        assertFalse(closed.containsPoint(300f - 101.6f, 400f))
        assertTrue(closed.containsPoint(300f - 99f, 400f))
        // Dotknięcie z zewnątrz wypełnia stronę bez wnętrza okręgu.
        val outside = fillOf(FloodFill.fill(els, 600f, 800f, 20f, 20f, closeGaps = true)!!)
        assertFalse(outside.containsPoint(300f, 400f))
        assertTrue(outside.containsPoint(300f - 101.6f, 400f))
    }

    /** Trójkąt z bardzo ostrym wierzchołkiem u góry: kreski o grubości [w] rozchodzą się pod kątem 2·[deg]. */
    private fun wedge(w: Float, deg: Float): List<Stroke> {
        val t = kotlin.math.tan(deg * PI / 180).toFloat()
        val h = 300f
        fun ln(id: Int, vararg xy: Float) = line(id, *xy).let { Stroke(it.id, it.pen, it.color, w, it.pts) }
        return listOf(
            ln(1, 300f, 100f, 300f - h * t, 100f + h),
            ln(2, 300f, 100f, 300f + h * t, 100f + h),
            ln(3, 300f - h * t - 20f, 100f + h, 300f + h * t + 20f, 100f + h),
        )
    }

    @Test
    fun sharpWedgeIsFilledToItsTipAlsoWithClosedGaps() {
        for (closeGaps in listOf(false, true)) for (deg in listOf(3f, 6f, 10f)) for (w in listOf(1f, 2f, 4f)) {
            val t = kotlin.math.tan(deg * PI / 180).toFloat()
            val f = fillOf(FloodFill.fill(wedge(w, deg), 600f, 800f, 300f, 380f, closeGaps)!!)
            // Każdy punkt papieru w klinie (poza kreskami) musi mieć kolor, także w samej szczelinie węższej niż komórka siatki.
            var y = 100f
            while (y < 395f) {
                val half = (y - 100f) * t
                var x = 300f - half
                while (x <= 300f + half) {
                    val d = (half - abs(x - 300f)) / kotlin.math.sqrt(1f + t * t)
                    if (d > w * 0.5f + 0.05f) assertTrue("kolor w ($x, $y) kąt $deg grubość $w domknięcie $closeGaps", f.containsPoint(x, y))
                    x += 0.1f
                }
                y += 0.25f
            }
        }
    }

    @Test
    fun filledOutlineIsSmoothNotStaircase() {
        val rings = FloodFill.fill(listOf(circle(1, 300f, 400f, 120f)), 600f, 800f, 300f, 400f)!!
        val ring = rings.single()
        val n = ring.size / 2
        assertTrue("za mało wierzchołków na gładkie koło: $n", n >= 40)
        // Schodki dawałyby zakręty o 90°, gładki obrys nie ma ostrych zakrętów (okrąg r = 120 pt).
        var maxTurn = 0.0
        for (i in 0 until n) {
            val a = (i + n - 1) % n; val b = (i + 1) % n
            val v1x = ring[2 * i] - ring[2 * a]; val v1y = ring[2 * i + 1] - ring[2 * a + 1]
            val v2x = ring[2 * b] - ring[2 * i]; val v2y = ring[2 * b + 1] - ring[2 * i + 1]
            val ang = abs(kotlin.math.atan2(v1x * v2y - v1y * v2x, v1x * v2x + v1y * v2y).toDouble())
            if (ang > maxTurn) maxTurn = ang
        }
        assertTrue("najostrzejszy zakręt ${maxTurn * 180 / PI}°", maxTurn < PI / 6)
        // Odchylenie od okręgu poniżej pół punktu (wygładzanie nie przesuwa konturu).
        for (i in 0 until n) {
            val d = kotlin.math.hypot(ring[2 * i] - 300f, ring[2 * i + 1] - 400f)
            assertTrue("promień $d", d in 118f..124f)
        }
    }

    @Test
    fun smoothingKeepsPageBorderAndCornersStraight() {
        val rings = FloodFill.fill(emptyList(), 600f, 800f, 10f, 10f)!!
        assertEquals(4, rings.single().size / 2)
    }

    /** Domykanie szpar nie może zabierać kolorowi niczego, co sięga bez niego (ostre rogi zamkniętych figur). */
    @Test
    fun closingGapsNeverFillsLessThanPlainFillOnClosedShapes() {
        val rnd = java.util.Random(7)
        repeat(100) { k ->
            val nv = 3 + rnd.nextInt(4)
            val w = 1f + rnd.nextInt(4)
            val ang = (0 until nv).map { rnd.nextDouble() * 2 * PI }.sorted()
            val vs = ang.map { a -> val r = 60f + rnd.nextFloat() * 140f; floatArrayOf(300f + r * cos(a).toFloat(), 400f + r * sin(a).toFloat()) }
            val strokes = (0 until nv).map { i ->
                val a = vs[i]; val b = vs[(i + 1) % nv]
                line(i + 1, a[0], a[1], b[0], b[1]).let { Stroke(it.id, it.pen, it.color, w, it.pts) }
            }
            val plain = fillOf(FloodFill.fill(strokes, 600f, 800f, 300f, 400f, false) ?: return@repeat)
            val closed = fillOf(FloodFill.fill(strokes, 600f, 800f, 300f, 400f, true) ?: return@repeat)
            var y = 0f
            while (y < 800f) {
                var x = 0f
                while (x < 600f) {
                    if (plain.containsPoint(x, y)) assertTrue("figura $k: brak koloru w ($x, $y) przy domykaniu szpar", closed.containsPoint(x, y))
                    x += 1f
                }
                y += 1f
            }
        }
    }
}
