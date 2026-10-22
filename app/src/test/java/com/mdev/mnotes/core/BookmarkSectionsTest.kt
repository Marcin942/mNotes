package com.mdev.mnotes.core

import com.mdev.mnotes.core.nav.BookmarkSections
import com.mdev.mnotes.core.nav.Mark
import com.mdev.mnotes.core.nav.Part
import com.mdev.mnotes.core.nav.SectionEnd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkSectionsTest {
    // 12 stron; zakładki na stronach 3, 7 i 10 (numery od 1: 4, 8, 11)
    private val marks = listOf(Mark(3, "Całki"), Mark(7, "Zadania"), Mark(10, "Powtórka"))
    private val n = 12

    @Test
    fun normalizeSortsDropsDuplicatesAndStrangers() {
        val raw = listOf(Mark(7, "b"), Mark(2, "a"), Mark(7, "dup"), Mark(40, "poza"), Mark(-1, "minus"))
        assertEquals(listOf(Mark(2, "a"), Mark(7, "b")), BookmarkSections.normalize(raw, n))
    }

    @Test
    fun nextBookmarkEndsBeforeIt() {
        assertEquals(3..6, BookmarkSections.range(marks, n, 0, SectionEnd.Next))
        assertEquals(7..9, BookmarkSections.range(marks, n, 1, SectionEnd.Next))
    }

    @Test
    fun lastBookmarkRunsToNotebookEnd() {
        assertEquals(10..11, BookmarkSections.range(marks, n, 2, SectionEnd.Next))
    }

    @Test
    fun chosenEndSkipsItsPageUnlessIncluded() {
        assertEquals(3..9, BookmarkSections.range(marks, n, 0, SectionEnd.At(2)))
        assertEquals(3..10, BookmarkSections.range(marks, n, 0, SectionEnd.At(2), includeEndPage = true))
    }

    @Test
    fun includeEndPageDoesNotOverflowAtNotebookEnd() {
        assertEquals(10..11, BookmarkSections.range(marks, n, 2, SectionEnd.Next, includeEndPage = true))
        assertEquals(3..11, BookmarkSections.range(marks, n, 0, SectionEnd.NotebookEnd, includeEndPage = true))
    }

    @Test
    fun introStartsAtFirstPage() {
        assertTrue(BookmarkSections.hasIntro(marks))
        assertEquals(0..2, BookmarkSections.range(marks, n, -1, SectionEnd.Next))
        assertEquals(0..6, BookmarkSections.range(marks, n, -1, SectionEnd.At(1)))
    }

    @Test
    fun noIntroWhenFirstBookmarkOnFirstPage() {
        val m = listOf(Mark(0, "A"), Mark(4, "B"))
        assertFalse(BookmarkSections.hasIntro(m))
        assertNull(BookmarkSections.range(m, n, -1, SectionEnd.Next))
    }

    @Test
    fun invalidEndsGiveNull() {
        assertNull(BookmarkSections.range(marks, n, 1, SectionEnd.At(1)))
        assertNull(BookmarkSections.range(marks, n, 1, SectionEnd.At(0)))
        assertNull(BookmarkSections.range(marks, n, 1, SectionEnd.At(9)))
        assertNull(BookmarkSections.range(marks, n, 5, SectionEnd.Next))
        assertNull(BookmarkSections.range(marks, 0, 0, SectionEnd.Next))
        assertNull(BookmarkSections.range(emptyList(), n, 0, SectionEnd.Next))
    }

    @Test
    fun sectionOfPage() {
        assertEquals(-1, BookmarkSections.sectionOf(marks, 0))
        assertEquals(-1, BookmarkSections.sectionOf(marks, 2))
        assertEquals(0, BookmarkSections.sectionOf(marks, 3))
        assertEquals(1, BookmarkSections.sectionOf(marks, 9))
        assertEquals(2, BookmarkSections.sectionOf(marks, 11))
    }

    @Test
    fun partsSplitAtEveryBookmarkInRange() {
        assertEquals(
            listOf(Part("Całki", 3..6), Part("Zadania", 7..9)),
            BookmarkSections.parts(marks, n, 0, SectionEnd.At(2), false, "Początek"),
        )
    }

    @Test
    fun partsFromIntroNameFirstSection() {
        assertEquals(
            listOf(Part("Początek", 0..2), Part("Całki", 3..6), Part("Zadania", 7..9), Part("Powtórka", 10..11)),
            BookmarkSections.parts(marks, n, -1, SectionEnd.NotebookEnd, false, "Początek"),
        )
    }

    @Test
    fun includedEndPageJoinsLastPart() {
        assertEquals(
            listOf(Part("Całki", 3..6), Part("Zadania", 7..10)),
            BookmarkSections.parts(marks, n, 0, SectionEnd.At(2), true, "Początek"),
        )
    }

    @Test
    fun singleSectionIsOnePart() {
        assertEquals(listOf(Part("Zadania", 7..9)), BookmarkSections.parts(marks, n, 1, SectionEnd.Next, false, "Początek"))
    }
}
