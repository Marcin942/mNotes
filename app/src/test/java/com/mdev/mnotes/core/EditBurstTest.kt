package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.EditBurst
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditBurstTest {
    @Test fun dragFramesWithinWindowStayOneStep() {
        val b = EditBurst(2000)
        for (t in 0L..1990L step 16) assertFalse(b.change(t))
        assertTrue(b.end())
        assertFalse(b.end())
    }

    @Test fun longDragSplitsEveryWindow() {
        val b = EditBurst(2000)
        var splits = 0
        for (t in 0L..5000L step 16) if (b.change(t)) splits++
        assertTrue(b.end())
        // 0–2000, 2000–4000, 4000–5000: dwa podziały w trakcie i trzeci krok przy puszczeniu
        assertTrue(splits == 2)
    }

    @Test fun releaseStartsFreshWindow() {
        val b = EditBurst(2000)
        b.change(0); b.end()
        assertFalse(b.change(1900))
        assertFalse(b.change(3800))
        assertTrue(b.change(3900))
    }
}
