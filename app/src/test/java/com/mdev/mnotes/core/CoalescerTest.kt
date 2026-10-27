package com.mdev.mnotes.core

import com.mdev.mnotes.core.cache.Coalescer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoalescerTest {

    /** B2: kafel listy stron złożony od nowa w trakcie renderu miniatury też dostaje wynik. */
    @Test fun secondRequestDuringWorkGetsResult() {
        val c = Coalescer<String, Int>()
        val got = ArrayList<String>()
        assertTrue(c.join("p:1:5") { got.add("stary kafel $it") })
        assertFalse(c.join("p:1:5") { got.add("nowy kafel $it") })
        assertTrue(c.isPending("p:1:5"))
        c.finish("p:1:5", 7)
        assertEquals(listOf("stary kafel 7", "nowy kafel 7"), got)
        assertFalse(c.isPending("p:1:5"))
    }

    @Test fun keysAreIndependentAndRestartable() {
        val c = Coalescer<String, Int>()
        var a = 0; var b = 0
        assertTrue(c.join("a") { a = it })
        assertTrue(c.join("b") { b = it })
        c.finish("b", 2)
        assertEquals(0, a); assertEquals(2, b)
        c.finish("a", 1)
        assertEquals(1, a)
        // Po zakończeniu ten sam klucz rusza od nowa (np. nieudany render).
        assertTrue(c.join("a") { a = it })
    }
}
