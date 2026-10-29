package com.mdev.mnotes.core

import com.mdev.mnotes.core.export.SyncNames
import com.mdev.mnotes.core.export.SyncNames.Book
import org.junit.Assert.assertEquals
import org.junit.Test

/** 3c-A16: folder synchronizacji to kopia zapasowa — plik jednego zeszytu nie przechodzi na inny. */
class SyncNamesTest {
    private val a = Book("aaaa1111", "", "Matma", 1L)
    private val b = Book("bbbb2222", "", "Matma", 2L)
    private val exts = listOf("mnotes", "pdf")

    @Test fun repeatedTitlesGetIdSuffix() {
        assertEquals(mapOf("aaaa1111" to "Matma", "bbbb2222" to "Matma-bbbb"), SyncNames.assign(listOf(b, a), emptyMap(), exts))
    }

    @Test fun fileOfTrashedNotebookIsNotHandedOver() {
        // Pierwszy przebieg: A = Matma, B = Matma-bbbb. A w koszu — B nie przejmuje „Matma.mnotes”.
        val items = mapOf(
            "aaaa1111|mnotes" to "10:5|/Matma.mnotes", "aaaa1111|pdf" to "10:5|/Matma.pdf",
            "bbbb2222|mnotes" to "20|/Matma-bbbb.mnotes",   // znacznik w starym formacie też się czyta
        )
        val owners = SyncNames.owners(items)
        assertEquals("aaaa1111", owners["/matma.mnotes"])
        assertEquals(mapOf("bbbb2222" to "Matma-bbbb"), SyncNames.assign(listOf(b), owners, exts))
    }

    @Test fun ownNameAndOtherFoldersStayFree() {
        val owners = mapOf("/matma.mnotes" to "aaaa1111", "szkoła/fizyka.mnotes" to "cccc3333")
        assertEquals(mapOf("aaaa1111" to "Matma"), SyncNames.assign(listOf(a), owners, exts))
        assertEquals(mapOf("dddd4444" to "Fizyka"), SyncNames.assign(listOf(Book("dddd4444", "dom", "Fizyka", 3L)), owners, exts))
        assertEquals(mapOf("dddd4444" to "Fizyka-dddd"), SyncNames.assign(listOf(Book("dddd4444", "szkoła", "Fizyka", 3L)), owners, exts))
    }

    @Test fun pathFormatMatchesStamp() {
        assertEquals("/Matma.pdf", SyncNames.path("", "Matma", "pdf"))
        assertEquals("A/B/Matma.mnotes", SyncNames.path("A/B", "Matma", "mnotes"))
    }
}
