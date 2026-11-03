package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.ScrollRange
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** 3c-A4: zapas przewijania tylko z lewej — początek pierwszej strony da się odsunąć najdalej na połowę ekranu. */
class ScrollRangeTest {
    private val view = 1000f
    private val reach = view * ScrollRange.MARGIN

    @Test fun withoutReachTheOldLimitsStay() {
        // Krótsza treść stoi na środku, dłuższa przewija się od brzegu do brzegu.
        assertArrayEquals(floatArrayOf(100f, 100f), ScrollRange.of(800f, view), 1e-4f)
        assertArrayEquals(floatArrayOf(-4000f, 0f), ScrollRange.of(5000f, view), 1e-4f)
        assertEquals(100f, ScrollRange.clamp(700f, 800f, view), 1e-4f)
    }

    @Test fun startCanGoOnlyToTheMiddle() {
        // Strona mieści się w szerokości ekranu: lewy brzeg może stanąć na połowie, nie dalej.
        assertEquals(500f, ScrollRange.clamp(900f, 800f, view, reach), 1e-4f)
        assertEquals(400f, ScrollRange.clamp(400f, 800f, view, reach), 1e-4f)
        // Długi zeszyt: początek na połowie ekranu, nie dalej.
        assertEquals(500f, ScrollRange.clamp(10_000f, 5000f, view, reach), 1e-4f)
    }

    @Test fun theEndAndTheOtherEdgesStayWithoutMargin() {
        // Koniec treści: nie da się przewinąć dalej niż bez zapasu (ostatni brzeg przy brzegu okna).
        assertEquals(-4000f, ScrollRange.of(5000f, view, reach)[0], 1e-4f)
        assertEquals(-4000f, ScrollRange.clamp(-9000f, 5000f, view, reach), 1e-4f)
        // Krótka strona nie ucieka w lewo poza środek.
        assertEquals(100f, ScrollRange.clamp(-50f, 800f, view, reach), 1e-4f)
        // Druga oś (bez zapasu): stare granice.
        assertArrayEquals(floatArrayOf(-4000f, 0f), ScrollRange.of(5000f, view), 1e-4f)
    }
}
