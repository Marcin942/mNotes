package com.mdev.mnotes.core

import com.mdev.mnotes.core.search.SettingsQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsQueryTest {
    private fun q(s: String) = SettingsQuery.of(s)!!

    @Test fun emptyQueryMeansNoFilter() {
        assertNull(SettingsQuery.of(""))
        assertNull(SettingsQuery.of("   "))
        assertNotNull(SettingsQuery.of(" a "))
    }

    @Test fun ignoresCaseAndDiacritics() {
        assertTrue(q("PIORO").matches("Pióro"))
        assertTrue(q("wygladzanie").matches("Wygładzanie: 50%"))
        assertTrue(q("zolc").matches("Żółć"))
    }

    @Test fun wordOrderDoesNotMatter() {
        assertTrue(q("rozmiar gumki").matches("Gumka i lasso", "Rozmiar gumki: 12 pt"))
        assertTrue(q("gumki rozmiar").matches("Gumka i lasso", "Rozmiar gumki: 12 pt"))
    }

    @Test fun everyWordMustMatch() {
        assertFalse(q("gumka motyw").matches("Gumka i lasso", "Rozmiar gumki"))
    }

    @Test fun toleratesInflection() {
        assertTrue(q("gumka").matches("Rozmiar gumki"))
        assertTrue(q("gumke").matches("Domyślny tryb gumki"))
        assertTrue(q("pióro").matches("Ustaw pióra"))
        assertTrue(q("dotknięcie").matches("Podwójne dotknięcia palcem"))
    }

    @Test fun shortWordsAreNotStemmed() {
        assertFalse(q("pen").matches("Pędzel"))
        assertTrue(q("dex").matches("Mysz i klawiatura (DeX / Linux)"))
    }

    @Test fun partsAreJoinedSoGroupTitleAndLabelCombine() {
        assertTrue(q("lasso kształt").matches("Gumka i lasso", "Kształt lassa", ""))
        assertFalse(q("lasso kształt").matches("Pióro", "Kształt gumki"))
    }

    @Test fun rangesExtendToWordEnd() {
        val r = q("gumka").ranges("Domyślny tryb gumki")
        assertEquals(listOf(14..18), r)
    }

    @Test fun rangesMergeOverlaps() {
        val r = q("gum gumk").ranges("gumki")
        assertEquals(listOf(0..4), r)
    }

    @Test fun rangesEmptyWhenNothingFound() {
        assertTrue(q("xyz").ranges("Motyw").isEmpty())
    }

    /** A2-10: w wariancie lab wyszukiwarka obejmuje „Laboratorium”; „O aplikacji” nigdy (tam nic się nie ustawia). */
    @Test fun labCategoryIsSearchedOnlyInLab() {
        assertEquals((0..6).toList(), SettingsQuery.searchableCats(hasLab = false))
        assertEquals((0..7).toList(), SettingsQuery.searchableCats(hasLab = true))
        assertTrue(SettingsQuery.LAB_CAT in SettingsQuery.searchableCats(true))
        assertFalse(SettingsQuery.LAB_CAT in SettingsQuery.searchableCats(false))
    }
}
