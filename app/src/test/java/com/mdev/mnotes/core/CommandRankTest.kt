package com.mdev.mnotes.core

import com.mdev.mnotes.core.search.CommandRank
import com.mdev.mnotes.core.search.CommandRank.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pomysł Fable nr 2: paleta poleceń z historią i wyszukiwaniem bez polskich znaków. */
class CommandRankTest {
    private val items = listOf(
        Item("crop", "Przytnij obraz"),
        Item("insertImage", "Wstaw obraz…", "Ctrl+I"),
        Item("addPage", "Dodaj stronę", "Ctrl+Shift+N"),
        Item("export", "Eksport i udostępnianie…", "Ctrl+E"),
        Item("source", "Źródło dokumentu"),
        Item("before", "Wstaw stronę przed"),
    )
    private fun titles(q: String, recent: List<String> = emptyList()) = CommandRank.rank(items, q, recent).map { items[it].id }

    @Test fun foldedPrefixMatches() {
        assertEquals("crop", titles("przytnij").first())
        assertEquals("source", titles("zrodlo").first())
        assertEquals("addPage", titles("DODAJ str").first())
        assertTrue(titles("ctrl+e").contains("export"))
    }

    @Test fun prefixBeatsInnerMatchAndSubsequenceIsLast() {
        val r = titles("obr")
        assertEquals(setOf("crop", "insertImage"), r.toSet())
        assertEquals(listOf("before"), titles("wsprz"))
        assertTrue(titles("xyz").isEmpty())
    }

    @Test fun historyComesFirstAndBreaksTies() {
        assertEquals(listOf("export", "crop"), titles("", listOf("export", "crop")).take(2))
        // Przy równym dopasowaniu wygrywa ostatnio użyte.
        assertEquals("insertImage", titles("obraz", listOf("insertImage")).first())
        val rec = CommandRank.used(CommandRank.used(listOf("a", "b"), "c"), "a")
        assertEquals(listOf("a", "c", "b"), rec)
        assertEquals(rec, CommandRank.decode(CommandRank.encode(rec)))
        assertEquals(CommandRank.MAX_RECENT, CommandRank.used((1..20).map { "$it" }, "x").size)
    }
}
