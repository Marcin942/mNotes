package com.mdev.mnotes.core.search

import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {
    private fun box(id: Int, x: Float, y: Float, text: String) = TextBox(id, x, y, 100f, 20f, 0f, text, 12f, 0xFF000000.toInt())

    @Test fun foldIgnoresCaseAndPolishDiacritics() {
        assertEquals("zolc zazolc gesla", TextSearch.fold("Żółć ZAŻÓŁĆ Gęślą"))
        assertEquals("cafe", TextSearch.fold("Café"))
    }

    @Test fun foldKeepsLength() {
        val s = "Łódź – ąęśćńóżź ß ø"
        assertEquals(s.length, TextSearch.fold(s).length)
    }

    @Test fun wordsSplitAndFold() {
        assertEquals(listOf("zolc", "test"), TextSearch.words("  Żółć   TEST "))
        assertTrue(TextSearch.words("   ").isEmpty())
    }

    @Test fun containsAllNeedsEveryWordInAnyOrder() {
        val t = TextSearch.fold("Notatki z chemii: kwas solny i zasada")
        assertTrue(TextSearch.containsAll(t, TextSearch.words("zasada kwas")))
        assertFalse(TextSearch.containsAll(t, TextSearch.words("kwas siarkowy")))
        assertFalse(TextSearch.containsAll(t, emptyList()))
    }

    @Test fun findAllReturnsNonOverlappingStarts() {
        assertEquals(listOf(0, 2, 4), TextSearch.findAll("aaaaaa", "aa"))
        assertEquals(listOf(1, 5), TextSearch.findAll("xabxxabx", "ab"))
        assertTrue(TextSearch.findAll("abc", "").isEmpty())
    }

    @Test fun textBoxesAreOrderedAndBlankOnesSkipped() {
        val c = PageContent.of(listOf(box(1, 10f, 300f, "drugi"), box(2, 10f, 50f, "pierwszy"), box(3, 10f, 100f, "   ")))
        assertEquals(listOf("pierwszy", "drugi"), TextSearch.textBoxes(c).map { it.text })
        assertEquals("pierwszy\ndrugi", TextSearch.pageText(c))
    }

    @Test fun snippetAddsEllipsesAndCollapsesWhitespace() {
        val text = "Lorem ipsum dolor sit amet,\nconsectetur adipiscing elit, sed do eiusmod tempor"
        val i = text.indexOf("adipiscing")
        val s = TextSearch.snippet(text, i, 10, radius = 12)
        assertTrue(s.startsWith("…") && s.endsWith("…"))
        assertTrue(s.contains("adipiscing"))
        assertFalse(s.contains("\n"))
        assertEquals("krótki", TextSearch.snippet("krótki", 0, 3))
    }
}
