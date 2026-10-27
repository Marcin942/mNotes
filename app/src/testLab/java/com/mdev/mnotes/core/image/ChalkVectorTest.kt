package com.mdev.mnotes.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChalkVectorTest {
    private val ink = Raster.argb(255, 240, 240, 240)

    private fun canvas(w: Int, h: Int) = Raster(w, h)

    private fun thick(r: Raster, x0: Int, y0: Int, x1: Int, y1: Int, half: Int) {
        for (y in y0 - half..y1 + half) for (x in x0 - half..x1 + half) if (x in 0 until r.w && y in 0 until r.h) r[x, y] = ink
    }

    @Test
    fun thickHorizontalBarBecomesOneLine() {
        val r = canvas(80, 30)
        thick(r, 10, 15, 69, 15, 2)  // pasek 5 px grubości
        val lines = ChalkVector.vectorize(r)
        assertEquals(1, lines.size)
        val l = lines[0]
        assertEquals(2, l.size)  // prosta po uproszczeniu: dwa punkty
        val xs = listOf(l.pts[0], l.pts[2]).sorted()
        assertTrue("początek ${xs[0]}", xs[0] in 8f..14f)
        assertTrue("koniec ${xs[1]}", xs[1] in 65f..71f)
        assertTrue("y ${l.pts[1]}", abs(l.pts[1] - 15f) <= 1f)
        assertTrue("grubość ${l.width}", l.width in 3.5f..7f)
    }

    @Test
    fun lShapeKeepsTheCorner() {
        val r = canvas(60, 60)
        thick(r, 10, 10, 10, 50, 1)
        thick(r, 10, 50, 50, 50, 1)
        val lines = ChalkVector.vectorize(r)
        val pts = lines.flatMap { l -> (0 until l.size).map { l.pts[2 * it] to l.pts[2 * it + 1] } }
        assertTrue("brak narożnika w $pts", pts.any { (x, y) -> abs(x - 10f) <= 2f && abs(y - 50f) <= 2f })
        assertTrue(lines.sumOf { it.length().toDouble() } in 70.0..90.0)
    }

    @Test
    fun ringIsTracedAsClosedLoop() {
        val r = canvas(50, 50)
        for (y in 0 until 50) for (x in 0 until 50) {
            val d = Math.hypot(x - 25.0, y - 25.0)
            if (d in 13.0..16.0) r[x, y] = ink
        }
        val lines = ChalkVector.vectorize(r)
        assertTrue(lines.isNotEmpty())
        val total = lines.sumOf { it.length().toDouble() }
        // 2π·14.5 ≈ 91; uproszczenie cięciwami i krótkie łuki między pikselami „schodków” skracają obwód
        assertTrue("obwód $total", total in 65.0..105.0)
        val xs = lines.flatMap { l -> (0 until l.size).map { l.pts[2 * it] } }
        assertTrue(xs.minOrNull()!! < 14f && xs.maxOrNull()!! > 36f)
    }

    @Test
    fun emptyAndSpecksGiveNothing() {
        assertEquals(0, ChalkVector.vectorize(canvas(10, 10)).size)
        val r = canvas(20, 20); r[5, 5] = ink
        assertEquals(0, ChalkVector.vectorize(r).size)
    }

    /** Gruba kreska od (x0, y0) do (x1, y1): wszystkie piksele bliżej osi niż [half]. */
    private fun segment(r: Raster, x0: Float, y0: Float, x1: Float, y1: Float, half: Float) {
        val dx = x1 - x0; val dy = y1 - y0; val len2 = dx * dx + dy * dy
        for (y in 0 until r.h) for (x in 0 until r.w) {
            val t = (((x - x0) * dx + (y - y0) * dy) / len2).coerceIn(0f, 1f)
            if (Math.hypot((x - x0 - t * dx).toDouble(), (y - y0 - t * dy).toDouble()) <= half) r[x, y] = ink
        }
    }

    @Test
    fun diagonalLineIsNotChoppedByStaircaseJunctions() {
        for (half in listOf(1.5f, 2.5f, 4f)) {
            val r = canvas(90, 90)
            segment(r, 10f, 12f, 78f, 80f, half)
            val lines = ChalkVector.vectorize(r)
            assertTrue("grubość ${2 * half}: ${lines.size} łamanych ${lines.map { it.length() }}", lines.size <= 2)
            assertTrue(lines.sumOf { it.length().toDouble() } in 80.0..100.0)
        }
    }

    @Test
    fun ringIsAtMostTwoLines() {
        val r = canvas(50, 50)
        for (y in 0 until 50) for (x in 0 until 50) if (Math.hypot(x - 25.0, y - 25.0) in 13.0..16.0) r[x, y] = ink
        val lines = ChalkVector.vectorize(r)
        assertTrue("${lines.size} łamanych", lines.size in 1..2)
    }

    @Test
    fun barsOfDifferentThicknessKeepTheirOwnWidth() {
        val r = canvas(100, 60)
        thick(r, 10, 15, 89, 15, 1)  // 3 px
        thick(r, 10, 42, 89, 42, 5)  // 11 px
        val lines = ChalkVector.vectorize(r).sortedBy { it.pts[1] }
        assertEquals(2, lines.size)
        assertTrue("cienki ${lines[0].width}", lines[0].width in 2f..4.5f)
        assertTrue("gruby ${lines[1].width}", lines[1].width in 8.5f..13f)
    }

    @Test
    fun crossKeepsStraightThroughLinesWhole() {
        val r = canvas(80, 80)
        segment(r, 10f, 40f, 70f, 40f, 2f)
        segment(r, 40f, 10f, 40f, 70f, 2f)
        val lines = ChalkVector.vectorize(r)
        // dwie proste przez skrzyżowanie: po złączeniu w węźle po kącie dwie łamane, nie cztery ramiona
        assertTrue("${lines.size} łamanych", lines.size <= 2)
        assertTrue(lines.all { it.length() > 50f })
    }

    @Test
    fun douglasPeuckerDropsNearlyCollinearPoints() {
        val pts = floatArrayOf(0f, 0f, 1f, 0.2f, 2f, -0.1f, 3f, 0f, 3f, 5f)
        val s = ChalkVector.simplify(pts, 0.5f)
        assertEquals(listOf(0f, 0f, 3f, 0f, 3f, 5f), s.toList())
    }
}

class BoardStackTest {
    @Test
    fun medianRemovesThePersonSeenOnOnePhoto() {
        val board = Synthetic.photo(chalk = true)
        val withPerson = board.copy().also { for (y in 80 until 260) for (x in 150 until 220) it[x, y] = Raster.rgbF(0.8f, 0.6f, 0.5f) }
        val other = board.copy().also { for (y in 80 until 260) for (x in 250 until 300) it[x, y] = Raster.rgbF(0.3f, 0.3f, 0.6f) }
        val m = BoardStack.median(listOf(withPerson, board, other))
        assertEquals(board[180, 150], m[180, 150])
        assertEquals(board[270, 150], m[270, 150])
    }

    @Test
    fun alignedMedianStraightensEveryPhotoToTheFirstSize() {
        val a = Synthetic.photo(seed = 1)
        val q2 = Quad.of(75f, 55f, 335f, 72f, 322f, 232f, 64f, 218f)
        val b = Synthetic.photo(seed = 2, quad = q2)
        val m = BoardStack.alignedMedian(listOf(a, b), listOf(Synthetic.QUAD, q2))
        assertEquals(Synthetic.QUAD.rectSize().first, m.w)
        assertTrue(Raster.luma(m[m.w / 2, m.h / 2]) > 0.6f)  // kreda w połowie na obu zdjęciach
    }

    @Test
    fun runManyHidesAPersonPresentOnOnlyOnePhoto() {
        val clean = Synthetic.photo(seed = 4)
        val person = Synthetic.photo(seed = 5).also { for (y in 100 until 300) for (x in 170 until 230) it[x, y] = Raster.rgbF(0.85f, 0.7f, 0.6f) }
        // bez filtra ram: jasna „osoba” to też gruby pas i znika sama; tu sprawdzamy medianę
        val noCrop = BoardOptions(crop = false, frames = false)
        val res = BoardPipeline.runMany(listOf(person, clean, Synthetic.photo(seed = 6)), noCrop)
        val one = BoardPipeline.run(person, noCrop)
        fun opaqueNear(r: Raster) = (r.h * 3 / 5 until r.h * 3 / 4).sumOf { y -> (r.w * 2 / 5 until r.w * 3 / 5).count { x -> Raster.alpha(r[x, y]) > 128 } }
        // osoba na jednym zdjęciu daje kryjącą plamę nad kredą; po medianie trzech zdjęć jej nie ma
        assertTrue("bez mediany ${opaqueNear(one.image)}", opaqueNear(one.image) > 100)
        assertTrue("po medianie ${opaqueNear(res.image)}", opaqueNear(res.image) < 20)
    }

    @Test
    fun addedShowsOnlyNewChalk() {
        val before = Raster(4, 1, intArrayOf(0, Raster.argb(255, 255, 255, 255), 0, 0))
        val after = Raster(4, 1, intArrayOf(0, Raster.argb(255, 255, 255, 255), Raster.argb(200, 255, 255, 255), Raster.argb(30, 255, 255, 255)))
        val d = BoardStack.added(before, after)
        assertEquals(listOf(0, 0, 200, 0), d.px.map { Raster.alpha(it) })
    }
}
