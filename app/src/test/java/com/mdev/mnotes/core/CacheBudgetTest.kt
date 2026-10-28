package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.CacheBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2c-A6: budżet bitmap stanowiska dzielony między pasy. */
class CacheBudgetTest {
    private val tiles = 96L shl 20
    private val bases = 48L shl 20

    @Test fun threeLanesShareOneBudget() {
        val b = CacheBudget()
        val s = b.shares(tiles, bases, floatArrayOf(400f, 400f, 200f), 0L)
        assertTrue(s.sumOf { it.tileBytes } <= tiles)
        assertTrue(s.sumOf { it.baseBytes } <= bases)
        assertEquals(tiles * 0.4, s[0].tileBytes.toDouble(), tiles / 1000.0)
        assertEquals(tiles * 0.2, s[2].tileBytes.toDouble(), tiles / 1000.0)
    }

    @Test fun laneScrolledAwayDropsToZeroAfterTwoSecondsAndComesBack() {
        val b = CacheBudget()
        b.shares(tiles, bases, floatArrayOf(500f, 500f, 0f), 0L)
        // Pas 1 wyjeżdża za ekran: przez 2 s trzyma przydział (suma dalej w budżecie).
        val mid = b.shares(tiles, bases, floatArrayOf(1000f, 0f, 0f), 100L)
        assertTrue(mid[1].tileBytes > 0L)
        assertTrue(mid.sumOf { it.tileBytes } <= tiles)
        assertEquals(2100L, b.nextRelease(100L))
        val late = b.shares(tiles, bases, floatArrayOf(1000f, 0f, 0f), 2100L)
        assertEquals(0L, late[1].tileBytes)
        assertEquals(0L, late[1].baseBytes)
        assertEquals(tiles, late[0].tileBytes)
        assertNull(b.nextRelease(2100L))
        // Pas wraca na ekran: przydział wraca.
        val back = b.shares(tiles, bases, floatArrayOf(500f, 500f, 0f), 3000L)
        assertEquals(tiles * 0.5, back[1].tileBytes.toDouble(), tiles / 1000.0)
    }

    @Test fun laneNeverShownGetsNothing() {
        val s = CacheBudget().shares(tiles, bases, floatArrayOf(800f, 0f), 0L)
        assertEquals(0L, s[1].tileBytes)
        assertEquals(tiles, s[0].tileBytes)
    }
}
