package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.model.ElementsEdit
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.TapeEl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapeTest {
    private fun stroke(id: Int) = Stroke(id, PenKind.BALLPOINT, 0xFF000000.toInt(), 2f, floatArrayOf(0f, 0f, 1f, 10f, 10f, 1f), false)

    @Test
    fun codecRoundTrip() {
        val t = TapeEl(1, 50f, 60f, 120f, 30f, 0.25f, 0xFFFFD54F.toInt(), hidden = false, locked = true)
        val back = PageCodec.decode(PageCodec.encode(PageContent.of(listOf(stroke(2), t)))).elements
        assertEquals(2, back.size)
        val b = back[1] as TapeEl
        assertEquals(50f, b.cx, 0f); assertEquals(60f, b.cy, 0f)
        assertEquals(120f, b.w, 0f); assertEquals(30f, b.h, 0f)
        assertEquals(0.25f, b.rot, 0f)
        assertEquals(0xFFFFD54F.toInt(), b.color)
        assertTrue(!b.hidden && b.locked)
    }

    @Test
    fun replaceOnTopMovesToEndAndKeepsOrder() {
        val a = TapeEl(1, 0f, 0f, 10f, 10f, 0f, 1)
        val s = stroke(2)
        val c = TapeEl(3, 5f, 5f, 10f, 10f, 0f, 2)
        val base = PageContent.of(listOf(a, s, c))
        val edit = ElementsEdit.replaceOnTop(base, mapOf(1 to a.copy(hidden = false)))
        val after = base.apply(edit)
        assertEquals(listOf(2, 3, 1), after.elements.map { it.id })
        assertTrue(!(after.elements.last() as TapeEl).hidden)
        // cofnięcie przywraca stan wyjściowy
        val undone = after.apply(edit.inverse())
        assertEquals(listOf(1, 2, 3), undone.elements.map { it.id })
        assertTrue((undone.elements[0] as TapeEl).hidden)
    }
}
