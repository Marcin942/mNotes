package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.HelperStrip
import com.mdev.mnotes.core.layout.ToolPopups
import com.mdev.mnotes.core.layout.ToolPopups.Fades
import com.mdev.mnotes.core.layout.ToolPopups.Rule
import com.mdev.mnotes.core.layout.ToolPopups.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3c-A17a: jeden model wyskakujących opcji narzędzi — widoczność, gaśnięcie, zapis. */
class ToolPopupsTest {
    @Test fun fadesAfterTheChosenTimeAndNeverWithZero() {
        assertTrue(ToolPopups.visible(Rule(true, 4), 3_999))
        assertFalse(ToolPopups.visible(Rule(true, 4), 4_000))
        assertTrue("0 — nie gaśnie", ToolPopups.visible(Rule(true, 0), 3_600_000))
        assertFalse("wyłączone nie wyskakuje", ToolPopups.visible(Rule(false, 0), 0))
    }

    @Test fun fadesRoundTripAndOldDataMeansNoFade() {
        // Brak wpisu (instalacje sprzed 3c-A17a) — menu nie gaśnie, jak dotąd.
        assertEquals(0, Fades.parse("").of(Tool.ERASER))
        val f = Fades.parse("").with(Tool.ERASER, 4).with(Tool.LASER, 8).with(Tool.LASSO, 0)
        assertEquals("ERASER:4,LASER:8", f.encode())
        val g = Fades.parse(f.encode())
        assertEquals(4, g.of(Tool.ERASER)); assertEquals(0, g.of(Tool.LASSO)); assertEquals(8, g.of(Tool.LASER))
        // Śmieci i nieznane narzędzia są pomijane.
        assertEquals("LASSO:2", Fades.parse("X:3,LASSO:2,ERASER:-1,LASER:abc,").encode())
    }

    @Test fun stepsCoverTheExamplesAndOldPainterValuesSnap() {
        assertTrue(ToolPopups.STEPS.containsAll(listOf(0, 2, 4, 8)))
        assertEquals(ToolPopups.STEPS.indexOf(6), ToolPopups.stepOf(6))   // domyślny czas pędzla
        assertEquals(ToolPopups.STEPS.indexOf(8), ToolPopups.stepOf(30))  // dawny suwak 0–30 s
        assertEquals(ToolPopups.STEPS.indexOf(0), ToolPopups.stepOf(0))
        assertEquals(ToolPopups.STEPS.indexOf(2), ToolPopups.stepOf(1))   // 3c-A16: gasnące 1 s nie jest „Nie gaśnie”
    }

    @Test fun everyHelperMapsBothWays() {
        for (h in HelperStrip.Helper.entries) assertEquals(h, ToolPopups.helper(ToolPopups.of(h)))
        assertEquals(null, ToolPopups.helper(Tool.PAINTER))
    }
}
