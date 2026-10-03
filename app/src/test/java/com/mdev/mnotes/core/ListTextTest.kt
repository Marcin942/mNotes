package com.mdev.mnotes.core

import com.mdev.mnotes.core.text.ListText
import com.mdev.mnotes.core.text.ListText.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListTextTest {
    @Test
    fun bulletsAllLinesThenOff() {
        val r = ListText.toggle("a\nb\nc", 0, 5, Kind.BULLET)
        assertEquals("• a\n• b\n• c", r.text)
        assertEquals("a\nb\nc", ListText.toggle(r.text, r.selStart, r.selEnd, Kind.BULLET).text)
    }

    @Test
    fun numberingIncrementsAndSkipsBlank() {
        assertEquals("1. x\n\n2. y", ListText.toggle("x\n\ny", 0, 4, Kind.NUMBER).text)
    }

    @Test
    fun switchingKindReplacesMarker() {
        assertEquals("☐ a\n☐ b", ListText.toggle("• a\n• b", 0, 7, Kind.CHECK).text)
        assertEquals("1. a", ListText.toggle("• a", 0, 0, Kind.NUMBER).text)
    }

    @Test
    fun onlyTouchedLinesChange() {
        assertEquals("a\n• b\nc", ListText.toggle("a\nb\nc", 2, 3, Kind.BULLET).text)
        // zaznaczenie kończące się tuż po znaku nowej linii nie obejmuje kolejnego wiersza
        assertEquals("• a\nb", ListText.toggle("a\nb", 0, 2, Kind.BULLET).text)
    }

    @Test
    fun enterContinuesList() {
        val r = ListText.onEnter("1. one", "1. one\n", 7)!!
        assertEquals("1. one\n2. ", r.text); assertEquals(r.text.length, r.selStart)
        assertEquals("• a\n• ", ListText.onEnter("• a", "• a\n", 4)!!.text)
        assertEquals("  ☐ t\n  ☐ ", ListText.onEnter("  ☐ t", "  ☐ t\n", 6)!!.text)
    }

    @Test
    fun enterOnEmptyItemEndsList() {
        val r = ListText.onEnter("• a\n• ", "• a\n• \n", 7)!!
        assertEquals("• a\n", r.text); assertEquals(4, r.selStart)
    }

    @Test
    fun enterElsewhereIgnored() {
        assertNull(ListText.onEnter("plain", "plain\n", 6))
        assertNull(ListText.onEnter("• a", "• ab", 4))
    }
}
