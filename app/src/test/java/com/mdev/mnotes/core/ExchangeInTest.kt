package com.mdev.mnotes.core

import com.mdev.mnotes.core.export.ExchangeIn
import com.mdev.mnotes.core.export.ExchangeIn.Act
import com.mdev.mnotes.core.export.ExchangeIn.Copy
import com.mdev.mnotes.core.export.ExchangeIn.Entry
import com.mdev.mnotes.core.export.ExchangeIn.Seen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3c-A8: które pliki z folderu wymiany wczytać i jak — nowe kopie, podmiana nowszą wersją, zmiana nazwy na tablecie. */
class ExchangeInTest {
    private val a1 = Entry("Szkoła/Analiza.mnotes", 1000, 50)
    private val seenA1 = mapOf(a1.path to Seen(1000, 50, "srcA"))

    @Test fun candidatesAreMnotesOnly() {
        assertTrue(ExchangeIn.candidate("Analiza.mnotes"))
        assertTrue(ExchangeIn.candidate("Analiza.MNOTES"))
        assertFalse(ExchangeIn.candidate("Analiza.pdf"))
        assertFalse(ExchangeIn.candidate("Analiza.md"))
        assertFalse("plik ukryty", ExchangeIn.candidate(".Analiza.mnotes"))
        assertFalse("blokada edytora", ExchangeIn.candidate("~Analiza.mnotes"))
    }

    @Test fun unknownAreNewOrChangedFiles() {
        val b = Entry("B.mnotes", 5, 5)
        assertEquals(listOf(b), ExchangeIn.unknown(listOf(a1, b), seenA1))
        assertEquals(listOf(a1.copy(modified = 2000)), ExchangeIn.unknown(listOf(a1.copy(modified = 2000)), seenA1))
        assertEquals("inny rozmiar przy tym samym czasie", 1, ExchangeIn.unknown(listOf(a1.copy(size = 51)), seenA1).size)
    }

    @Test fun firstRunAddsCopy() {
        val p = ExchangeIn.plan(listOf(a1), seenA1, emptyMap(), emptySet(), emptySet(), emptySet())
        assertEquals(listOf(Act.Add("srcA", a1)), p.acts)
    }

    @Test fun unchangedFileDoesNothing() {
        val p = ExchangeIn.plan(listOf(a1), seenA1, mapOf("srcA" to Copy("nb1", 1000)), setOf("nb1"), emptySet(), emptySet())
        assertTrue(p.acts.isEmpty())
    }

    @Test fun newerVersionReplacesSameNotebook() {
        val a2 = a1.copy(modified = 2000, size = 70)
        val p = ExchangeIn.plan(listOf(a2), mapOf(a2.path to Seen(2000, 70, "srcA")), mapOf("srcA" to Copy("nb1", 1000)), setOf("nb1"), emptySet(), emptySet())
        assertEquals(listOf(Act.Replace("srcA", a2, "nb1")), p.acts)
    }

    @Test fun renamedOnTabletReplacesInsteadOfDuplicating() {
        // Stary plik zostaje jako archiwum, nowy (inna nazwa) jest nowszy i ma to samo źródło.
        val renamed = Entry("Szkoła/Analiza 1.mnotes", 3000, 80)
        val seen = seenA1 + (renamed.path to Seen(3000, 80, "srcA"))
        val p = ExchangeIn.plan(listOf(a1, renamed), seen, mapOf("srcA" to Copy("nb1", 1000)), setOf("nb1"), emptySet(), emptySet())
        assertEquals(listOf(Act.Replace("srcA", renamed, "nb1")), p.acts)
        // Pierwsze wczytanie z obu plikami naraz: jedna kopia, z nowszego.
        val first = ExchangeIn.plan(listOf(a1, renamed), seen, emptyMap(), emptySet(), emptySet(), emptySet())
        assertEquals(listOf(Act.Add("srcA", renamed)), first.acts)
    }

    @Test fun openCopyWaitsForNextRun() {
        val a2 = a1.copy(modified = 2000)
        val p = ExchangeIn.plan(listOf(a2), mapOf(a2.path to Seen(2000, 50, "srcA")), mapOf("srcA" to Copy("nb1", 1000)), setOf("nb1"), setOf("nb1"), emptySet())
        assertTrue(p.acts.isEmpty())
        assertEquals(listOf("srcA"), p.busy)
    }

    @Test fun deletedCopyComesBackOnlyWithNewerVersion() {
        val copies = mapOf("srcA" to Copy("nb1", 1000))
        assertTrue(ExchangeIn.plan(listOf(a1), seenA1, copies, emptySet(), emptySet(), emptySet()).acts.isEmpty())
        val a2 = a1.copy(modified = 2000)
        val p = ExchangeIn.plan(listOf(a2), mapOf(a2.path to Seen(2000, 50, "srcA")), copies, emptySet(), emptySet(), emptySet())
        assertEquals(listOf(Act.Add("srcA", a2)), p.acts)
    }

    @Test fun ownNotebooksAndUnreadFilesAreSkipped() {
        val b = Entry("B.mnotes", 5, 5)
        val p = ExchangeIn.plan(listOf(a1, b), seenA1, emptyMap(), setOf("srcA"), emptySet(), own = setOf("srcA"))
        assertTrue("własny zeszyt i plik bez znanego źródła", p.acts.isEmpty())
        // Plik, który się nie rozpakował, ma puste źródło: nie jest wczytywany, ale też nie pobierany ponownie bez zmiany.
        val bad = mapOf(b.path to Seen(5, 5, ""))
        assertTrue(ExchangeIn.plan(listOf(b), bad, emptyMap(), emptySet(), emptySet(), emptySet()).acts.isEmpty())
        assertTrue(ExchangeIn.unknown(listOf(b), bad).isEmpty())
    }

    @Test fun pathsAndTitles() {
        assertEquals(listOf("Szkoła", "Rok 2"), ExchangeIn.dirs("Szkoła/Rok 2/Analiza.mnotes"))
        assertEquals(emptyList<String>(), ExchangeIn.dirs("Analiza.mnotes"))
        assertEquals("Analiza", ExchangeIn.titleOf("Szkoła/Analiza.mnotes"))
        assertEquals("Analiza", ExchangeIn.titleOf("Analiza.MNOTES"))
    }
}
