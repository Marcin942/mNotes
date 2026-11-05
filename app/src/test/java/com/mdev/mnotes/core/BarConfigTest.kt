package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.BarConfig
import com.mdev.mnotes.core.layout.BarMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarConfigTest {
    private val ids = BarConfig.DEFAULT_ORDER

    @Test fun emptyStringsGiveDefault() {
        val c = BarConfig.parse("", "")
        assertEquals(ids, c.order); assertTrue(c.isDefault)
    }

    @Test fun unknownDuplicatedAndMissingIdsAreRepaired() {
        val c = BarConfig.parse("ruler,xyz,undo,ruler", "")
        assertEquals(listOf("ruler", "undo") + ids.filter { it != "ruler" && it != "undo" }, c.order)
        assertFalse(c.isDefault)
    }

    @Test fun badModesAreDropped() {
        val c = BarConfig.parse("", "undo:1,xyz:2,redo:7,eraser:x,lasso,tape:2")
        assertEquals(BarMode.ALWAYS, c.mode("undo"))
        assertEquals(BarMode.AUTO, c.mode("redo"))
        assertEquals(BarMode.AUTO, c.mode("eraser"))
        assertEquals(BarMode.MENU, c.mode("tape"))
    }

    @Test fun roundTrip() {
        val c = BarConfig.DEFAULT.move("ruler", -3).withMode("tape", BarMode.MENU).withMode("undo", BarMode.ALWAYS)
        val back = BarConfig.parse(c.orderString(), c.modesString())
        assertEquals(c.order, back.order)
        for (id in ids) assertEquals(c.mode(id), back.mode(id))
    }

    @Test fun moveShiftsAndStopsAtEdges() {
        val c = BarConfig.DEFAULT.move("redo", 1)
        assertEquals("ballpoint", c.order[1]); assertEquals("redo", c.order[2])
        assertEquals(BarConfig.DEFAULT.order, BarConfig.DEFAULT.move("undo", -1).order)
        assertEquals(BarConfig.DEFAULT.order, BarConfig.DEFAULT.move(BarConfig.DEFAULT_ORDER.last(), 1).order)
    }

    @Test fun moveSkipsItemsInMenu() {
        val c = BarConfig.DEFAULT.withMode("ballpoint", BarMode.MENU).move("redo", 1)
        assertEquals(listOf("undo", "ballpoint", "fountain", "redo"), c.order.take(4))
    }

    @Test fun withModeAutoRemovesEntryAndUnknownIdIsIgnored() {
        val c = BarConfig.DEFAULT.withMode("tape", BarMode.MENU).withMode("tape", BarMode.AUTO)
        assertTrue(c.isDefault)
        assertTrue(BarConfig.DEFAULT.withMode("xyz", BarMode.MENU).isDefault)
    }

    @Test fun resetGoesBackToDefault() {
        assertTrue(BarConfig.DEFAULT.move("ruler", -5).withMode("tape", BarMode.ALWAYS).reset().isDefault)
    }
}

class BarConfigOffTest {
    private val ids = BarConfig.DEFAULT_ORDER

    @Test fun offSurvivesRoundTripAndOlderValuesStayValid() {
        val c = BarConfig.DEFAULT.withMode("lasso", BarMode.OFF).withMode("tape", BarMode.MENU)
        val back = BarConfig.parse(c.orderString(), c.modesString())
        assertTrue(back.isOff("lasso")); assertEquals(BarMode.MENU, back.mode("tape"))
        assertFalse(back.isOff("tape"))
        // Nieznana wartość z przyszłej wersji — bezpiecznie „gdy jest miejsce”.
        assertEquals(BarMode.AUTO, BarConfig.parse("", "lasso:9").mode("lasso"))
    }

    @Test fun undoAndRedoCannotBeTurnedOff() {
        assertFalse(BarConfig.DEFAULT.withMode("undo", BarMode.OFF).isOff("undo"))
        assertFalse(BarConfig.parse("", "redo:3").isOff("redo"))
        assertFalse(BarConfig.DEFAULT.canTurnOff("undo"))
        assertTrue(BarConfig.DEFAULT.canTurnOff("eraser"))
    }

    @Test fun lastPenStaysOn() {
        var c = BarConfig.DEFAULT
        for (p in BarConfig.PENS) c = c.withMode(p, BarMode.OFF)
        assertEquals(1, BarConfig.PENS.count { !c.isOff(it) })
        assertEquals("highlighter", c.firstPen())
        assertFalse(c.canTurnOff("highlighter"))
        val all = BarConfig.PENS.joinToString(",") { "$it:3" }
        val parsed = BarConfig.parse("", all)
        assertEquals(1, BarConfig.PENS.count { !parsed.isOff(it) })
        assertEquals("ballpoint", parsed.firstPen())
    }

    @Test fun firstPenFollowsBarOrder() {
        val c = BarConfig.DEFAULT.move("pencil", -2).withMode("ballpoint", BarMode.OFF)
        assertEquals("pencil", c.firstPen())
    }

    @Test fun commandsOfOffToolAreBlocked() {
        val c = BarConfig.DEFAULT.withMode("eraser", BarMode.OFF).withMode("ruler", BarMode.OFF)
        assertFalse(c.allows("toolEraser")); assertFalse(c.allows("eraserPixel")); assertFalse(c.allows("ruler"))
        assertTrue(c.allows("toolLasso")); assertTrue(c.allows("undo")); assertTrue(c.allows("tapesToggle"))
        assertTrue(BarConfig.DEFAULT.allows("toolEraser"))
    }

    @Test fun mapsPointAtRealIds() {
        for (id in BarConfig.TOOLS.values + BarConfig.COMMANDS.values + BarConfig.PENS) assertTrue(id, id in ids)
        for (t in com.mdev.mnotes.editor.Tool.entries) assertEquals(t.name, t != com.mdev.mnotes.editor.Tool.PEN, t.name in BarConfig.TOOLS)
        for (k in BarConfig.COMMANDS.keys) assertTrue(k, com.mdev.mnotes.ui.CmdDefs.byId.containsKey(k))
        for (p in BarConfig.PENS) assertTrue(p, com.mdev.mnotes.core.model.PenKind.entries.any { it.name.lowercase() == p })
    }

    @Test fun moveSkipsOffItems() {
        val c = BarConfig.DEFAULT.withMode("ballpoint", BarMode.OFF).move("redo", 1)
        assertEquals(listOf("undo", "ballpoint", "fountain", "redo"), c.order.take(4))
    }
}
