package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.Snip
import com.mdev.mnotes.core.geom.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F14: wycinek strony jako obraz. */
class SnipTest {

    @Test fun planAddsMarginAndClipsToPage() {
        val p = Snip.plan(Bounds(2f, 100f, 102f, 150f), 420f, 595f)!!
        assertEquals(0f, p.rect.l, 0f)          // margines przycięty do brzegu strony
        assertEquals(96f, p.rect.t, 0f)
        assertEquals(106f, p.rect.r, 0f)
        assertEquals(3f, p.scale, 0f)
        assertEquals(318, p.wPx)
    }

    @Test fun bigSnipIsScaledDown() {
        val p = Snip.plan(Bounds(0f, 0f, 2000f, 500f), 3000f, 3000f, maxPx = 3000)!!
        assertTrue(p.wPx <= 3000)
        assertTrue(p.scale < 3f)
        assertNull(Snip.plan(Bounds(500f, 500f, 600f, 600f), 100f, 100f))
    }

    @Test fun placementPrefersRightThenBelow() {
        val right = Snip.placement(Bounds(10f, 10f, 110f, 60f), 420f, 595f)
        assertEquals(170f, right[0], 1e-4f)
        assertEquals(35f, right[1], 1e-4f)
        val below = Snip.placement(Bounds(200f, 10f, 400f, 60f), 420f, 595f)
        assertEquals(300f, below[0], 1e-4f)
        assertEquals(95f, below[1], 1e-4f)
        // Brak miejsca wokół: na wycinku, przesunięty i w granicach strony.
        val over = Snip.placement(Bounds(0f, 0f, 420f, 595f), 420f, 595f)
        assertTrue(over[0] <= 420f - 210f + 1e-3f && over[1] <= 595f - 297.5f + 1e-3f)
    }
}
