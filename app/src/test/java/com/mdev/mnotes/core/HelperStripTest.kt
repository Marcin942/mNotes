package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.HelperStrip
import com.mdev.mnotes.core.layout.HelperStrip.Config
import com.mdev.mnotes.core.layout.HelperStrip.Helper
import com.mdev.mnotes.core.layout.HelperStrip.Item
import com.mdev.mnotes.core.layout.LastOf
import com.mdev.mnotes.editor.Tool
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

    /** 2c-A15: dawny przełącznik F7 „toolbar.tool_strip” przechodzi na tryb listwy; zapisany nowy tryb wygrywa. */
    @Test fun oldToolStripSwitchMigrates() {
        val prefs = mutableMapOf<String, Any>(HelperStrip.OLD_TOOL_STRIP to true)
        fun migrate() {
            val old = prefs.remove(HelperStrip.OLD_TOOL_STRIP) as Boolean?
            HelperStrip.fromToolStrip(old, "toolbar.helper_strip" in prefs)?.let { prefs["toolbar.helper_strip"] = it }
        }
        migrate()
        assertEquals(mapOf<String, Any>("toolbar.helper_strip" to HelperStrip.TOOL_OPTIONS), prefs)
        migrate()   // drugie uruchomienie: nic się nie zmienia
        assertEquals(mapOf<String, Any>("toolbar.helper_strip" to HelperStrip.TOOL_OPTIONS), prefs)
        assertEquals(HelperStrip.WRITE_AND_MENU, HelperStrip.fromToolStrip(false, hasNew = false))
        assertEquals(null, HelperStrip.fromToolStrip(true, hasNew = true))
        assertEquals(null, HelperStrip.fromToolStrip(null, hasNew = false))
    }

    /** 2c-A15: zmiana koloru lub grubości z listwy przy gumce/lassie wraca do ostatniego narzędzia pisania (tunePen). */
    @Test fun tuningThePenReturnsToWriting() {
        val last = LastOf(Tool.PEN) { it == Tool.PEN || it == Tool.CURVE }
        for (t in listOf(Tool.CURVE, Tool.ERASER)) last.note(t)
        for (t in listOf(Tool.ERASER, Tool.LASSO, Tool.LASER, Tool.HAND, Tool.TEXT, Tool.TAPE, Tool.SPACE, Tool.GEO))
            assertEquals("$t", Tool.CURVE, t.afterTune(last.value))
        // Pędzel formatu i wiaderko biorą kolor z pióra — zostają w ręku.
        for (t in listOf(Tool.PEN, Tool.PAINTER, Tool.FILL, Tool.CURVE)) assertEquals("$t", t, t.afterTune(last.value))
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
