package com.mdev.mnotes.core

import com.mdev.mnotes.core.text.NameText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NameTextTest {
    @Test
    fun noBreaksKeepsLength() {
        assertEquals("WEJ :", NameText.noBreaks("WEJ\n:"))
        assertEquals("a  b", NameText.noBreaks("a\r\nb"))
        assertEquals("a b c", NameText.noBreaks("a b c"))
    }

    @Test
    fun noBreaksLeavesPlainTextUntouched() {
        val s = "Fizyka 1 "
        assertSame(s, NameText.noBreaks(s))
    }

    @Test
    fun oneLineJoinsLinesWithSingleSpace() {
        assertEquals("WEJ :", NameText.oneLine("WEJ\n:"))
        assertEquals("a b", NameText.oneLine("a \r\n\n b"))
        assertEquals("a b", NameText.oneLine("a\u0085b"))
    }

    @Test
    fun oneLineTrimsEdgesAndKeepsInnerSpaces() {
        assertEquals("a  b", NameText.oneLine("  a  b\n"))
        assertEquals("", NameText.oneLine("\n\r\n"))
    }
}
