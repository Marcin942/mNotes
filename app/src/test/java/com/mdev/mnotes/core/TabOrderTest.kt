package com.mdev.mnotes.core

import com.mdev.mnotes.core.nav.TabOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** LP-1: kolejność kart przy stanowisku i strzałki. */
class TabOrderTest {

    @Test fun withoutStationTabsStay() {
        assertEquals(listOf("a", "b"), TabOrder.merge(listOf("a", "b"), emptyList()))
        assertEquals(listOf("a", "b"), TabOrder.merge(listOf("a", "b"), listOf("a")))
    }

    @Test fun groupStandsAtFirstMemberInRowOrder() {
        // karty: x, b, y, a; stanowisko: a nad b
        assertEquals(listOf("x", "a", "b", "y"), TabOrder.merge(listOf("x", "b", "y", "a"), listOf("a", "b")))
    }

    @Test fun rowsThatAreNotTabsJoinTheGroup() {
        // c dołożony z biblioteki, nie jest kartą
        assertEquals(listOf("x", "c", "a", "y"), TabOrder.merge(listOf("x", "a", "y"), listOf("c", "a")))
    }

    @Test fun groupWithoutTabsGoesFirst() {
        assertEquals(listOf("a", "c", "x"), TabOrder.merge(listOf("x"), listOf("a", "c")))
    }

    @Test fun arrowsFollowOrientation() {
        assertEquals("↑", TabOrder.arrow(0, 1, true))
        assertEquals("↓", TabOrder.arrow(2, 1, true))
        assertEquals("←", TabOrder.arrow(0, 1, false))
        assertEquals("→", TabOrder.arrow(2, 1, false))
        assertEquals("", TabOrder.arrow(1, 1, true))
        assertEquals("", TabOrder.arrow(0, -1, true))
    }
}
