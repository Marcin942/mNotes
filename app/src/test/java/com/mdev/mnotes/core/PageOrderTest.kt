package com.mdev.mnotes.core

import com.mdev.mnotes.core.model.PageOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class PageOrderTest {
    @Test fun stepSwapsOnlyPastNeighbourCentre() {
        assertEquals(3, PageOrder.step(3, 140f, 50f, 150f))
        assertEquals(4, PageOrder.step(3, 151f, 50f, 150f))
        assertEquals(2, PageOrder.step(3, 49f, 50f, 150f))
        assertEquals(0, PageOrder.step(0, -500f, null, 150f))
        assertEquals(5, PageOrder.step(5, 900f, 50f, null))
    }

    @Test fun tallNeighbourDoesNotBounceBack() {
        // mała miniatura (0..100) nad dużą (110..410); środek przeciąganej mija środek dużej (260)
        val mid = 261f
        assertEquals(1, PageOrder.step(0, mid, null, 260f))
        // po zamianie duża stoi na górze (0..300, środek 150), przeciągana pod nią: brak powrotu
        assertEquals(1, PageOrder.step(1, mid, 150f, null))
    }

    @Test fun moveReorders() {
        val l = mutableListOf("a", "b", "c", "d")
        PageOrder.move(l, 0, 2); assertEquals(listOf("b", "c", "a", "d"), l)
        PageOrder.move(l, 3, 0); assertEquals(listOf("d", "b", "c", "a"), l)
        PageOrder.move(l, 1, 9); assertEquals(listOf("d", "b", "c", "a"), l)
    }

    @Test fun autoBookmarkTitleFollowsPosition() {
        assertEquals("Strona 5", PageOrder.bookmarkTitle("Strona 2", 4, "Strona"))
        assertEquals("Page 1", PageOrder.bookmarkTitle("Page 7", 0, "Page"))
        assertEquals("Strona 3", PageOrder.bookmarkTitle("", 2, "Strona"))
        assertEquals("Wstęp", PageOrder.bookmarkTitle("Wstęp", 2, "Strona"))
        assertEquals("Strona 2 — szkic", PageOrder.bookmarkTitle("Strona 2 — szkic", 6, "Strona"))
    }
}
