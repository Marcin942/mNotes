package com.mdev.mnotes.core

import com.mdev.mnotes.core.pages.PageTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FB8: tagi stron — postać tagu, zapis w metadanych, zestawienie zeszytu i wyszukiwanie. */
class PageTagsTest {
    @Test fun normalizeKeepsOneLowercaseWord() {
        assertEquals("definicja", PageTags.normalize("#Definicja"))
        assertEquals("przykład", PageTags.normalize("  ##Przykład "))
        assertEquals("wzór-skróconego-mnożenia", PageTags.normalize("wzór  skróconego - mnożenia"))
        assertEquals("zad_3", PageTags.normalize("zad_3!"))
        assertNull(PageTags.normalize("#"))
        assertNull(PageTags.normalize(" - . "))
        assertEquals(PageTags.MAX_LEN, PageTags.normalize("a".repeat(40))!!.length)
    }

    @Test fun parseSplitsAndDropsRepeats() {
        assertEquals(listOf("definicja", "zadanie", "egzamin"), PageTags.parse("#definicja, zadanie;  #Egzamin #DEFINICJA"))
        // Powtórka bez diakrytyków to ten sam tag.
        assertEquals(listOf("przykład"), PageTags.parse("przykład przyklad"))
        assertEquals(emptyList<String>(), PageTags.parse(" , # ; "))
    }

    @Test fun addRemoveAndLimit() {
        var t = PageTags.added(emptyList(), listOf("definicja", "wzór"))
        t = PageTags.added(t, listOf("Wzor", "zadanie"))
        assertEquals(listOf("definicja", "wzór", "zadanie"), t)
        assertTrue(PageTags.has(t, "WZOR"))
        assertEquals(listOf("definicja", "zadanie"), PageTags.removed(t, "wzor"))
        val many = PageTags.added(emptyList(), (1..20).map { "t$it" })
        assertEquals(PageTags.MAX_PER_PAGE, many.size)
    }

    @Test fun encodeDecodeRoundTripAndTolerance() {
        val m = mapOf("p1" to listOf("definicja", "wzór"), "p2" to listOf("egzamin"), "p3" to emptyList())
        val back = PageTags.decode(PageTags.encode(m))
        assertEquals(mapOf("p1" to listOf("definicja", "wzór"), "p2" to listOf("egzamin")), back)
        // Uszkodzone wiersze, pusty identyfikator i obce znaki nie psują reszty.
        val messy = PageTags.decode("p1\tDefinicja\t#wzór\n\nbez-tabulatora\n\tsierota\np2\t \t!!\np3\tzadanie\r")
        assertEquals(mapOf("p1" to listOf("definicja", "wzór"), "p3" to listOf("zadanie")), messy)
        assertEquals(emptyMap<String, List<String>>(), PageTags.decode(""))
    }

    @Test fun withSetsOrClearsPage() {
        var m = PageTags.with(emptyMap(), "p1", listOf("#Zadanie", "zadanie", "wzór"))
        assertEquals(listOf("zadanie", "wzór"), m["p1"])
        m = PageTags.with(m, "p1", emptyList())
        assertFalse("p1" in m)
    }

    @Test fun usageCountsOnlyLivePagesMostFrequentFirst() {
        val m = mapOf(
            "a" to listOf("wzór", "definicja"), "b" to listOf("definicja"), "c" to listOf("Definicja", "zadanie"),
            "gone" to listOf("egzamin"),
        )
        val u = PageTags.usage(m, listOf("c", "a", "b"))
        assertEquals(listOf("definicja", "wzór", "zadanie"), u.map { it.tag.lowercase() })
        assertEquals(listOf(0, 1, 2), u[0].pages)
        assertEquals(listOf(1), u[1].pages)
        assertTrue(u.none { it.tag == "egzamin" })
    }

    @Test fun queryMatchesTagPrefixesWithoutDiacritics() {
        val q = PageTags.query("#def #Przykład")
        assertEquals(listOf("def", "przyklad"), q.tags)
        assertTrue(q.isTag)
        assertFalse(PageTags.query("pochodna funkcji").isTag)
        assertFalse("bez # to zwykłe szukanie", PageTags.matchesPage(listOf("pochodna"), "", PageTags.query("pochodna")))
        assertTrue(PageTags.matchesPage(listOf("definicja", "przykład"), "", q))
        assertFalse(PageTags.matchesPage(listOf("definicja"), "", q))
        assertFalse("tylko początek tagu", PageTags.matchesPage(listOf("niedefinicja"), "", PageTags.query("#def")))
    }

    @Test fun tagQueryCombinesTagsHashtagsInTextAndPlainWords() {
        val q = PageTags.query("#zad całka")
        assertEquals(listOf("calka"), q.words)
        assertTrue(PageTags.matchesPage(listOf("zadanie"), "oblicz calke... calka oznaczona", q))
        assertFalse("słowo spoza tekstu", PageTags.matchesPage(listOf("zadanie"), "pochodna", q))
        // „#zadanie” wpisane w polu tekstowym strony też jest tagiem.
        assertTrue(PageTags.matchesPage(emptyList(), "#zadanie 3: calka z x", q))
        assertFalse(PageTags.matchesPage(emptyList(), "zadanie 3: calka", q))
    }

    @Test fun labelsAndPolishPlurals() {
        assertEquals("#definicja #wzór", PageTags.label(listOf("definicja", "wzór")))
        assertEquals("1 strona", PageTags.pagesCount(1, true))
        assertEquals("3 strony", PageTags.pagesCount(3, true))
        assertEquals("5 stron", PageTags.pagesCount(5, true))
        assertEquals("12 stron", PageTags.pagesCount(12, true))
        assertEquals("22 strony", PageTags.pagesCount(22, true))
        assertEquals("2 pages", PageTags.pagesCount(2, false))
    }
}
