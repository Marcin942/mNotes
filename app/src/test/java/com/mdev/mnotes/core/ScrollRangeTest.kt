package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.ScrollRange
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3c-A4: zapas przewijania wokół dokumentu — początek pierwszej strony da się odsunąć za połowę ekranu. */
class ScrollRangeTest {
    private val view = 1000f
    private val margin = view * ScrollRange.MARGIN

    @Test fun withoutMarginTheOldLimitsStay() {
        // Krótsza treść stoi na środku, dłuższa przewija się od brzegu do brzegu.
        assertArrayEquals(floatArrayOf(100f, 100f), ScrollRange.of(800f, view), 1e-4f)
        assertArrayEquals(floatArrayOf(-4000f, 0f), ScrollRange.of(5000f, view), 1e-4f)
        assertEquals(100f, ScrollRange.clamp(700f, 800f, view), 1e-4f)
    }

    @Test fun firstPageStartCanGoPastTheMiddle() {
        // Strona mieści się w szerokości ekranu: bez zapasu nie drgnie, z zapasem jej lewy brzeg może stanąć za połową.
        assertTrue(ScrollRange.clamp(600f, 800f, view) < view / 2f)
        assertEquals(600f, ScrollRange.clamp(600f, 800f, view, margin), 1e-4f)
        // Długi zeszyt: góra pierwszej strony do połowy ekranu, koniec ostatniej też pół ekranu w górę.
        val r = ScrollRange.of(5000f, view, margin)
        assertEquals(view / 2f, r[1], 1e-4f)
        assertEquals(view - 5000f - view / 2f, r[0], 1e-4f)
        assertEquals(view / 2f, ScrollRange.clamp(10_000f, 5000f, view, margin), 1e-4f)
    }
}
