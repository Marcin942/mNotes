package com.mdev.mnotes.core

import com.mdev.mnotes.core.text.PageSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageSpanTest {
    @Test
    fun singlePageAndRange() {
        assertEquals(listOf(2), PageSpan.parse("3", 10))
        assertEquals(listOf(1, 2, 3, 4), PageSpan.parse("2-5", 10))
    }

    @Test
    fun openEnds() {
        assertEquals(listOf(7, 8, 9), PageSpan.parse("8-", 10))
        assertEquals(listOf(0, 1, 2), PageSpan.parse("-3", 10))
    }

    @Test
    fun mixedPartsAreSortedAndDeduplicated() {
        assertEquals(listOf(0, 1, 2, 7, 9), PageSpan.parse("8; 1-3, 2, 10", 10))
        assertEquals(listOf(0, 1, 2), PageSpan.parse(" 1 - 3 ", 10))
    }

    @Test
    fun invalidInputGivesNull() {
        assertNull(PageSpan.parse("", 10))
        assertNull(PageSpan.parse(" , ", 10))
        assertNull(PageSpan.parse("0", 10))
        assertNull(PageSpan.parse("11", 10))
        assertNull(PageSpan.parse("5-2", 10))
        assertNull(PageSpan.parse("2-11", 10))
        assertNull(PageSpan.parse("-", 10))
        assertNull(PageSpan.parse("a-3", 10))
        assertNull(PageSpan.parse("1", 0))
    }

    @Test
    fun labelCollapsesRuns() {
        assertEquals("3", PageSpan.label(listOf(2)))
        assertEquals("2-5", PageSpan.label(listOf(1, 2, 3, 4)))
        assertEquals("1-3_8_10", PageSpan.label(listOf(9, 0, 1, 2, 7, 7)))
    }
}
