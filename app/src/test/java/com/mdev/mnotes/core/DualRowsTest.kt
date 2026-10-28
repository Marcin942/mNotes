package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.DualRows
import org.junit.Assert.assertEquals
import org.junit.Test

/** Wybór dokumentu do dołożenia na stanowisko (geometria rzędów F4 zastąpiona przez `Workspace`, 2d-A1). */
class DualRowsTest {

    @Test fun candidatesSkipCurrentAndSortByRecent() {
        data class Nb(val id: String, val opened: Long)
        val list = listOf(Nb("a", 1), Nb("b", 5), Nb("c", 3))
        assertEquals(listOf("b", "c"), DualRows.candidates(list, { it.id }, { it.opened }, setOf("a")).map { it.id })
    }
}
