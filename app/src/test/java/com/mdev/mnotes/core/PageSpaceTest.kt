package com.mdev.mnotes.core

import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PageSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSpaceTest {
    private val above = stroke(10f, 100f, 60f, 110f)
    private val below = stroke(10f, 300f, 60f, 310f)
    private val content = PageContent.of(listOf(above, below))

    @Test fun moversAreElementsBelowTheLine() {
        assertEquals(listOf(below.id), PageSpace.movers(content, 200f).map { it.id })
    }

    @Test fun lockedElementsStay() {
        val c = PageContent.of(listOf(above, below.withLocked(true)))
        assertTrue(PageSpace.movers(c, 200f).isEmpty())
        assertTrue(PageSpace.edit(c, 200f, 50f).isEmpty)
    }

    @Test fun insertShiftsOnlyLowerContent() {
        val e = PageSpace.edit(content, 200f, 80f)
        val after = e.added.single().element
        assertEquals(below.bounds.t + 80f, after.bounds.t, 0.01f)
        assertEquals(1, e.removed.size)
        assertEquals(below.id, e.removed.single().element.id)
    }

    @Test fun insertIsClampedToPageBottom() {
        val d = PageSpace.clamp(content, 400f, 200f, 500f)
        assertEquals(400f - below.bounds.b, d, 0.01f)
    }

    @Test fun removeOnlyEmptySpace() {
        val d = PageSpace.clamp(content, 842f, 200f, -1000f)
        assertEquals(-(below.bounds.t - above.bounds.b), d, 0.01f)
    }

    /** 3c-A16: zablokowane tło na całą stronę (środek w połowie) nie blokuje usuwania odstępu w dolnej połowie. */
    @Test fun lockedPageBackgroundDoesNotBlockRemovalInLowerHalf() {
        val bg = stroke(0f, 0f, 595f, 842f).withLocked(true)
        val lo = stroke(10f, 700f, 60f, 710f)
        val mid = stroke(10f, 500f, 60f, 510f)
        val c = PageContent.of(listOf(bg, mid, lo))
        val d = PageSpace.clamp(c, 842f, 600f, -1000f)
        assertEquals(-(lo.bounds.t - mid.bounds.b), d, 0.01f)
    }

    @Test fun nothingBelowMeansNoShift() {
        assertEquals(0f, PageSpace.clamp(content, 842f, 400f, 50f), 0f)
    }
}
