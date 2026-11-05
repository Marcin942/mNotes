package com.mdev.mnotes.core

import com.mdev.mnotes.core.prefs.QuickOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickOverlayTest {

    /** Bez nadpisania odczyt daje zapisaną wartość. */
    @Test fun emptyReadsSaved() {
        assertEquals(0, QuickOverlay.EMPTY.read<Int>("eraser.mode", 0))
        assertEquals(14f, QuickOverlay.EMPTY.read("eraser.size", 14f))
    }

    /** Wybór z szybkiego menu zasłania zapisaną wartość, ale tylko swojego klucza. */
    @Test fun pickOverridesOnlyItsKey() {
        val q = QuickOverlay.EMPTY.pick("eraser.mode", 1, 0)
        assertEquals(1, q.read<Int>("eraser.mode", 0))
        assertEquals(false, q.read("lasso.rect", false))
        assertTrue("eraser.mode" in q)
    }

    /** Wybór równy domyślnemu nie zostawia nadpisania — późniejsza zmiana domyślnego od razu działa. */
    @Test fun pickingSavedValueDropsOverride() {
        val q = QuickOverlay.EMPTY.pick("lasso.rect", true, false).pick("lasso.rect", false, false)
        assertTrue(q.isEmpty)
        assertEquals(true, q.read("lasso.rect", true))
    }

    /** Zapis domyślnego (pełny panel) zdejmuje nadpisanie tego klucza, reszta zostaje. */
    @Test fun withoutRemovesOneKey() {
        val q = QuickOverlay.EMPTY.pick("snip.rect", false, true).pick("snip.caption", true, false).without("snip.rect")
        assertFalse("snip.rect" in q)
        assertEquals(true, q.read("snip.rect", true))
        assertEquals(true, q.read("snip.caption", false))
    }

    /** Nadpisanie w innym typie niż ustawienie nie psuje odczytu. */
    @Test fun wrongTypeIgnored() {
        val q = QuickOverlay.EMPTY.pick("eraser.size", 3, 14f)
        assertSame(QuickOverlay.EMPTY, q)
        assertEquals(14f, QuickOverlay(mapOf("eraser.size" to 3)).read("eraser.size", 14f))
    }

    /** Odłożenie narzędzia: puste nadpisania, wraca zapisane. */
    @Test fun emptyOverlayRestoresDefaults() {
        val q = QuickOverlay.EMPTY.pick("laser.color", 0x34C759, 0xFF3B30)
        assertEquals(0x34C759, q.read<Int>("laser.color", 0xFF3B30))
        assertEquals(0xFF3B30, QuickOverlay.EMPTY.read<Int>("laser.color", 0xFF3B30))
    }
}
