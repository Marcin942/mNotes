package com.mdev.mnotes.core

import com.mdev.mnotes.core.pen.LineStyle
import com.mdev.mnotes.core.pen.StrokeDash
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeDashTest {
    private fun cut(pts: FloatArray, width: Float, style: LineStyle, sharp: Boolean = false, fit: Boolean = false): List<FloatArray> {
        val out = StrokeDash.Pieces()
        StrokeDash.cut(pts, pts.size / 3, width, style, sharp, fit, out)
        return List(out.count) { out.piece(it) }
    }

    private fun line(vararg xy: Float) = FloatArray(xy.size / 2 * 3) { i -> if (i % 3 == 2) 1f else xy[i / 3 * 2 + i % 3] }

    private fun len(p: FloatArray): Float {
        var s = 0f
        for (i in 1 until p.size / 3) s += hypot(p[3 * i] - p[3 * i - 3], p[3 * i + 1] - p[3 * i - 2])
        return s
    }

    private fun gap(a: FloatArray, b: FloatArray) = hypot(b[0] - a[a.size - 3], b[1] - a[a.size - 2])

    @Test fun solidStaysInOnePiece() {
        val pts = line(0f, 0f, 10f, 0f, 10f, 10f)
        val r = cut(pts, 1f, LineStyle.SOLID)
        assertEquals(1, r.size)
        assertArrayEquals(pts, r[0], 0f)
    }

    @Test fun dashedLineStartsAndEndsWithFullDash() {
        val r = cut(line(0f, 0f, 100f, 0f), 1f, LineStyle.DASHED, sharp = true, fit = true)
        assertTrue(r.size > 10)
        assertEquals(0f, r.first()[0], 1e-3f)
        assertEquals(100f, r.last()[r.last().size - 3], 1e-3f)
        for (p in r) assertEquals(3f, len(p), 1e-2f)
        // Dopasowanie rozkłada resztę równo między przerwy, a nie zostawia urwanej kreski na końcu.
        val g = gap(r[0], r[1])
        assertTrue(g in 2f..4.5f)
        for (i in 1 until r.size) assertEquals(g, gap(r[i - 1], r[i]), 1e-2f)
    }

    @Test fun dottedLineHasDotsOnBothEnds() {
        val r = cut(line(0f, 0f, 21f, 0f), 2f, LineStyle.DOTTED, sharp = true, fit = true)
        assertTrue(r.all { it.size == 3 })
        assertEquals(0f, r.first()[0], 1e-3f)
        assertEquals(21f, r.last()[0], 1e-3f)
        // 21 pt przy kroku 4 pt: pięć równych odstępów zamiast pięciu i ćwierci.
        assertEquals(6, r.size)
        for (i in 1 until r.size) assertEquals(4.2f, r[i][0] - r[i - 1][0], 1e-2f)
    }

    @Test fun closedPolygonKeepsEveryCornerDrawn() {
        val sq = line(0f, 0f, 60f, 0f, 60f, 60f, 0f, 60f, 0f, 0f)
        for (style in listOf(LineStyle.DASHED, LineStyle.DOTTED)) {
            val r = cut(sq, 1f, style, sharp = true, fit = true)
            for (v in 0 until 4) {
                val x = sq[3 * v]; val y = sq[3 * v + 1]
                val hits = r.count { p -> (0 until p.size / 3).any { abs(p[3 * it] - x) < 1e-3f && abs(p[3 * it + 1] - y) < 1e-3f } }
                assertEquals("róg $v, $style", 1, hits)
            }
        }
        // Kreska w rogu zagina się w nim: trzy punkty, środkowy to wierzchołek, po półtorej grubości w każdą stronę.
        val corner = cut(sq, 1f, LineStyle.DASHED, sharp = true, fit = true).first { it.size == 9 && it[3] == 60f && it[4] == 0f }
        assertEquals(1.5f, 60f - corner[0], 1e-2f)
        assertEquals(1.5f, corner[7], 1e-2f)
    }

    @Test fun closedShapeHasNoSeamAtStart() {
        val n = 48
        val el = FloatArray(3 * (n + 1)) { i ->
            val a = 2.0 * Math.PI * ((i / 3) % n) / n
            when (i % 3) { 0 -> (50 * cos(a)).toFloat(); 1 -> (30 * sin(a)).toFloat(); else -> 1f }
        }
        val r = cut(el, 1f, LineStyle.DASHED, fit = true)
        // Punkt startu leży w środku jednej kreski, a przerwy są wszędzie takie same.
        assertEquals(1, r.count { p -> (1 until p.size / 3 - 1).any { p[3 * it] == el[0] && p[3 * it + 1] == el[1] } })
        val gaps = r.indices.map { gap(r[it], r[(it + 1) % r.size]) }
        assertTrue(gaps.max() - gaps.min() < 0.05f)
        val dots = cut(el, 1f, LineStyle.DOTTED, fit = true)
        assertEquals(1, dots.count { abs(it[0] - el[0]) < 1e-3f && abs(it[1] - el[1]) < 1e-3f })
    }

    @Test fun freehandPatternDoesNotMoveWhileWriting() {
        val long = FloatArray(3 * 200) { i -> when (i % 3) { 0 -> (i / 3) * 0.7f; 1 -> sin((i / 3) * 0.1f) * 5f; else -> 0.8f } }
        val short = long.copyOf(3 * 120)
        for (style in listOf(LineStyle.DASHED, LineStyle.DOTTED)) {
            val a = cut(short, 1.4f, style); val b = cut(long, 1.4f, style)
            assertTrue(b.size > a.size)
            // Wszystko poza ostatnim, jeszcze rosnącym kawałkiem zostaje na miejscu.
            for (i in 0 until a.size - 1) assertArrayEquals(a[i], b[i], 0f)
        }
    }

    @Test fun pressureIsInterpolatedAtCuts() {
        val pts = floatArrayOf(0f, 0f, 0f, 60f, 0f, 1f)
        for (p in cut(pts, 1f, LineStyle.DASHED)) for (i in 0 until p.size / 3) assertEquals(p[3 * i] / 60f, p[3 * i + 2], 1e-4f)
    }

    @Test fun shortSegmentsStaySolid() {
        // Grot strzałki: odcinki krótsze niż kreska z przerwą nie rozpadają się na strzępy.
        val r = cut(line(0f, 0f, 4f, 0f), 1f, LineStyle.DASHED, sharp = true, fit = true)
        assertEquals(1, r.size)
        assertEquals(4f, len(r[0]), 1e-3f)
    }

    @Test fun arrowHeadWingsLookTheSame() {
        // Ramię, po którym łamana wraca do grotu, kończy się tak samo jak ostatnie: oba wychodzą w całości.
        val r = cut(line(0f, 0f, 60f, 0f, 53f, -4f, 60f, 0f, 53f, 4f), 1.5f, LineStyle.DASHED, sharp = true, fit = true)
        val head = r.last()
        fun has(x: Float, y: Float) = (0 until head.size / 3).any { head[3 * it] == x && head[3 * it + 1] == y }
        assertTrue(has(53f, -4f) && has(53f, 4f) && has(60f, 0f))
        assertTrue(r.size > 3)
    }

    @Test fun thinPenUsesReadablePattern() {
        val r = cut(line(0f, 0f, 60f, 0f), 0.3f, LineStyle.DOTTED)
        assertEquals(2f, r[1][0] - r[0][0], 1e-3f)
    }

    @Test fun repeatedPointsAndSinglePoint() {
        assertEquals(1, cut(floatArrayOf(5f, 5f, 0.6f), 1f, LineStyle.DASHED).size)
        val dot = cut(floatArrayOf(5f, 5f, 0.6f, 5f, 5f, 0.9f), 1f, LineStyle.DOTTED)
        assertEquals(1, dot.size)
        assertArrayEquals(floatArrayOf(5f, 5f, 0.9f), dot[0], 0f)
        val r = cut(line(0f, 0f, 0f, 0f, 30f, 0f, 30f, 0f), 1f, LineStyle.DASHED, sharp = true, fit = true)
        assertEquals(0f, r.first()[0], 1e-3f)
        assertEquals(30f, r.last()[r.last().size - 3], 1e-3f)
    }
}
