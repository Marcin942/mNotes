package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Axes
import com.mdev.mnotes.core.geom.AxesSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8: układ współrzędnych — osie, podziałka, siatka i opisy dla zadanego zakresu. */
class AxesTest {

    @Test fun ticksCoverRangeOnMultiplesOfStep() {
        assertEquals(listOf(-2f, -1f, 0f, 1f, 2f), Axes.ticks(-2f, 2f, 1f))
        assertEquals(listOf(0.5f, 1f, 1.5f), Axes.ticks(0.3f, 1.6f, 0.5f))
        assertEquals(11, Axes.ticks(0f, 1f, 0.1f).size)
    }

    @Test fun polishNumberFormat() {
        assertEquals("2", Axes.format(2f))
        assertEquals("−3", Axes.format(-3f))
        assertEquals("0,5", Axes.format(0.5f))
        assertEquals("−1,25", Axes.format(-1.25f))
        assertEquals("0,1", Axes.format(0.1f + 1e-6f))
    }

    @Test fun originAxesCrossAtZero() {
        val d = Axes.build(AxesSpec(-5f, 5f, -2f, 3f, unit = 10f), 100f, 100f)
        // Oś x na y = 0: 3 jednostki pod górnym brzegiem zakresu.
        val ax = d.axes[0]
        assertEquals(130f, ax[1], 1e-4f)
        assertEquals(100f, ax[0], 1e-4f)
        assertEquals(200f + Axes.OVERHANG, ax[2], 1e-4f)
        // Oś y na x = 0.
        assertEquals(150f, d.axes[1][0], 1e-4f)
        // Siatka: 11 pionowych + 6 poziomych linii; znaczniki bez zera: 10 + 5.
        assertEquals(17, d.grid.size)
        assertEquals(15, d.ticks.size)
        assertTrue(d.labels.any { it.text == "0" })
        assertTrue(d.labels.any { it.text == "−5" })
        assertTrue(d.labels.any { it.text == "x" } && d.labels.any { it.text == "y" })
        assertTrue(d.bounds.contains(200f + Axes.OVERHANG, 130f))
    }

    @Test fun axisSitsOnEdgeWhenZeroOutsideRange() {
        // Zakres x od 2 do 6: oś y stoi na x = 2 (lewym brzegu), a oś x na y = 0.
        val d = Axes.build(AxesSpec(2f, 6f, -1f, 1f, unit = 10f, labels = false, grid = false, arrows = false), 0f, 0f)
        assertEquals(0f, d.axes[1][0], 1e-4f)
        assertEquals(10f, d.axes[0][1], 1e-4f)
        assertEquals(2, d.axes.size)
        assertTrue(d.labels.isEmpty() && d.grid.isEmpty())
    }

    @Test fun labelEverySecondCountsFromZero() {
        val d = Axes.build(AxesSpec(-4f, 4f, -1f, 1f, labelEvery = 2, xName = "", yName = ""), 0f, 0f)
        val xs = d.labels.filter { it.ay == 1 && it.text != "0" }.map { it.text }.toSet()
        assertEquals(setOf("−4", "−2", "2", "4"), xs)
        assertFalse(xs.contains("1"))
    }

    @Test fun specRoundTripsThroughSettings() {
        val s = AxesSpec(-1.5f, 8f, 0f, 4f, 0.5f, 2f, 25f, grid = false, labels = true, labelEvery = 2, arrows = false, xName = "t", yName = "v")
        assertEquals(s, AxesSpec.decode(s.encode()))
        assertEquals(AxesSpec(), AxesSpec.decode("śmieci"))
        assertEquals(0.5f, AxesSpec.parse(" 0,5 "))
        assertEquals(-2f, AxesSpec.parse("−2"))
        assertEquals(null, AxesSpec.parse("abc"))
    }

    @Test fun normalizedFixesSwappedRangeAndTooManyTicks() {
        val s = AxesSpec(xMin = 3f, xMax = -3f, xStep = 0.0001f).normalized()
        assertEquals(-3f, s.xMin, 0f)
        assertEquals(3f, s.xMax, 0f)
        assertTrue(Axes.ticks(s.xMin, s.xMax, s.xStep).size <= AxesSpec.MAX_TICKS + 1)
    }
}
