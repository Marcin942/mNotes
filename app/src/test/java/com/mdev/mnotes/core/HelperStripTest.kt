package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.HelperStrip
import com.mdev.mnotes.core.layout.HelperStrip.Config
import com.mdev.mnotes.core.layout.HelperStrip.Helper
import com.mdev.mnotes.core.layout.HelperStrip.Item
import com.mdev.mnotes.core.layout.LastOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2b-A2: listwa przy gumce, lassie i laserze — tryby, konfigurowalne menu, powrót do ostatniego narzędzia pisania. */
class HelperStripTest {

    @Test fun defaultKeepsWritingStripAndShowsMenu() {
        val cfg = Config.DEFAULT
        for (h in Helper.entries) {
            assertFalse(HelperStrip.stripShowsToolOptions(HelperStrip.WRITE_AND_MENU, h))
            assertTrue(HelperStrip.menuVisible(HelperStrip.WRITE_AND_MENU, h, cfg, panelOpen = false))
        }
        // Z piórem w ręku nie ma ani menu, ani opcji narzędzia.
        assertFalse(HelperStrip.menuVisible(HelperStrip.WRITE_AND_MENU, null, cfg, false))
        // Otwarty panel narzędzia zastępuje menu.
        assertFalse(HelperStrip.menuVisible(HelperStrip.WRITE_AND_MENU, Helper.ERASER, cfg, panelOpen = true))
        assertEquals(listOf(Item.ERASER_MODES, Item.ERASER_SIZES, Item.ERASER_HIGHLIGHTER), HelperStrip.items(Helper.ERASER, cfg))
    }

    @Test fun toolOptionsModePutsOptionsOnTheStripWithoutMenu() {
        assertTrue(HelperStrip.stripShowsToolOptions(HelperStrip.TOOL_OPTIONS, Helper.LASSO))
        assertFalse(HelperStrip.stripShowsToolOptions(HelperStrip.TOOL_OPTIONS, null))
        assertFalse(HelperStrip.menuVisible(HelperStrip.TOOL_OPTIONS, Helper.LASSO, Config.DEFAULT, false))
    }

    @Test fun menuIsConfigurablePerToolAndGroupAndSurvivesSaving() {
        val cfg = Config.DEFAULT.withMenu(Helper.LASER, false).withItem(Item.ERASER_SIZES, false)
        val back = Config.parse(cfg.encode())
        assertFalse(HelperStrip.menuVisible(HelperStrip.WRITE_AND_MENU, Helper.LASER, back, false))
        assertTrue(HelperStrip.menuVisible(HelperStrip.WRITE_AND_MENU, Helper.ERASER, back, false))
        assertEquals(listOf(Item.ERASER_MODES, Item.ERASER_HIGHLIGHTER), HelperStrip.items(Helper.ERASER, back))
        // Wszystkie grupy wyłączone = brak menu (pusty dymek nie ma sensu).
        val empty = Config.DEFAULT.withItem(Item.LASSO_SHAPE, false).withItem(Item.LASSO_MODE, false)
        assertFalse(HelperStrip.menuVisible(HelperStrip.WRITE_AND_MENU, Helper.LASSO, empty, false))
        // Nieznane nazwy w zapisie (np. z nowszej wersji) są pomijane, pusty zapis = domyślne.
        assertEquals("", Config.parse("COŚ_NOWEGO, ").encode())
        assertEquals(Config.DEFAULT.encode(), Config.parse("").encode())
    }

    @Test fun returnsToTheLastWritingTool() {
        val writing = setOf("PEN", "CURVE")
        val last = LastOf("PEN") { it in writing }
        for (t in listOf("CURVE", "ERASER", "LASSO", "LASER")) last.note(t)
        assertEquals("CURVE", last.value)          // po krzywej i gumce wraca krzywa, nie pióro
        last.note("PEN"); last.note("HAND")
        assertEquals("PEN", last.value)
    }
}
