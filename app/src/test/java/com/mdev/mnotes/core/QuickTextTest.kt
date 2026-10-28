package com.mdev.mnotes.core

import com.mdev.mnotes.core.text.QuickText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3c-A12: listwa okna tekstu i zapis przy stuknięciu obok okna. */
class QuickTextTest {

    @Test
    fun sizeStepsWalkTheTypicalSizesAndStopAtTheEnds() {
        assertEquals(16f, QuickText.step(14f, +1))
        assertEquals(12f, QuickText.step(14f, -1))
        assertEquals("rozmiar spoza listy (suwak) idzie do najbliższego w górę", 16f, QuickText.step(15f, +1))
        assertEquals(14f, QuickText.step(15f, -1))
        assertEquals(96f, QuickText.step(96f, +1))
        assertEquals(8f, QuickText.step(8f, -1))
        assertEquals(8f, QuickText.step(5f, -1))
        assertEquals(8f, QuickText.step(5f, +1))
    }

    @Test
    fun swatchesStartWithTheCurrentColourWithoutRepeats() {
        assertEquals(listOf(3, 1, 2, 4), QuickText.swatches(3, listOf(1, 3, 2, 4, 5)))
        assertEquals(listOf(7), QuickText.swatches(7, emptyList()))
    }

    @Test
    fun tapOutsideKeepsTypedTextButNeverDeletes() {
        assertTrue("nowy, coś wpisano", QuickText.keepOnDismiss(true, "", "Notatka", false))
        assertFalse("nowy, pusty", QuickText.keepOnDismiss(true, "", "  ", false))
        assertFalse("istniejący bez zmian", QuickText.keepOnDismiss(false, "abc", "abc", false))
        assertTrue("istniejący, zmieniona treść", QuickText.keepOnDismiss(false, "abc", "abcd", false))
        assertTrue("istniejący, zmieniony tylko wygląd", QuickText.keepOnDismiss(false, "abc", "abc", true))
        assertFalse("wyczyszczony do zera — usuwa tylko „Gotowe”", QuickText.keepOnDismiss(false, "abc", "", false))
    }
}
