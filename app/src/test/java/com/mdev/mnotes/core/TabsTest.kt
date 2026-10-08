package com.mdev.mnotes.core

import com.mdev.mnotes.core.nav.Tabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabsTest {
    private fun of(vararg ids: String, active: Int = ids.size - 1) = Tabs(ids.toList(), active)

    @Test fun openAddsAndActivates() {
        val t = Tabs().open("a").open("b")
        assertEquals(listOf("a", "b"), t.ids); assertEquals("b", t.activeId)
    }

    @Test fun openExistingOnlyActivates() {
        val t = of("a", "b", "c").open("a")
        assertEquals(listOf("a", "b", "c"), t.ids); assertEquals("a", t.activeId)
    }

    @Test fun openBeyondLimitDropsOldestInactive() {
        var t = Tabs()
        for (i in 1..4) t = t.open("n$i", max = 3)
        assertEquals(listOf("n2", "n3", "n4"), t.ids); assertEquals("n4", t.activeId)
    }

    @Test fun closeActiveSelectsNeighbour() {
        assertEquals("c", of("a", "b", "c", active = 1).close("b").activeId)
        assertEquals("b", of("a", "b", "c", active = 2).close("c").activeId)
    }

    @Test fun closeBeforeActiveKeepsSameActiveTab() {
        val t = of("a", "b", "c", active = 2).close("a")
        assertEquals(listOf("b", "c"), t.ids); assertEquals("c", t.activeId)
    }

    @Test fun closeLastLeavesEmpty() {
        val t = of("a").close("a")
        assertTrue(t.ids.isEmpty()); assertNull(t.activeId)
    }

    @Test fun closeUnknownIsNoop() { val t = of("a", "b"); assertEquals(t, t.close("x")) }

    @Test fun stepWraps() {
        assertEquals("a", of("a", "b", "c").step(1).activeId)
        assertEquals("c", of("a", "b", "c", active = 0).step(-1).activeId)
        assertEquals(of("a"), of("a").step(1))
    }

    @Test fun numberedActivation() {
        val t = of("a", "b", "c", active = 0)
        assertEquals("b", t.activateNumber(2).activeId)
        assertEquals("c", t.activateNumber(9).activeId)
        assertEquals("c", t.activateNumber(7).activeId)
    }

    @Test fun retainDropsMissingNotebooks() {
        val t = of("a", "b", "c", active = 1).retain { it != "b" }
        assertEquals(listOf("a", "c"), t.ids); assertEquals("a", t.activeId)
        assertTrue(of("a").retain { false }.ids.isEmpty())
    }
}
