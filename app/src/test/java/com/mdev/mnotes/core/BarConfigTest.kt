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
        assertEquals(BarConfig.DEFAULT.order, BarConfig.DEFAULT.move("ruler", 1).order)
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
