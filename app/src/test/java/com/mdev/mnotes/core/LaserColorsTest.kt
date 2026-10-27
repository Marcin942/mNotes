package com.mdev.mnotes.core

import com.mdev.mnotes.core.pen.LaserColors
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaserColorsTest {

    /** Domyślny czerwony laser wygląda dokładnie jak przed F7. */
    @Test fun defaultRedKeepsOldLayers() {
        assertArrayEquals(intArrayOf(0xFF3B30, 0xFF3B30, 0xFF2A1F, 0xFFFFFF), LaserColors.layers(LaserColors.DEFAULT))
    }

    @Test fun coreIsDarkerButKeepsHue() {
        for (c in LaserColors.PALETTE) {
            val k = LaserColors.core(c)
            val ch = intArrayOf((c shr 16) and 255, (c shr 8) and 255, c and 255)
            val kh = intArrayOf((k shr 16) and 255, (k shr 8) and 255, k and 255)
            // Kanał dominujący bez zmian, reszta nie jaśnieje.
            assertEquals(ch.max(), kh.max())
            for (i in 0..2) assertTrue(kh[i] <= ch[i])
        }
    }
}
