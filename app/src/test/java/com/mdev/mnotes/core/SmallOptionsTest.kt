package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.layout.ToolReturn
import com.mdev.mnotes.core.layout.ZoomLimits
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F25 (maks. zoom), F26 (powrót do pisania), F27 (wyrównanie tekstu). */
class SmallOptionsTest {

    @Test fun defaultMaxZoomIsUnchanged() {
        // Dawniej: max(14, base · 6).
        for (base in floatArrayOf(0.8f, 1.7f, 3f)) assertEquals(maxOf(14f, base * 6f), ZoomLimits.maxZoom(base, 6f), 1e-5f)
        assertTrue(ZoomLimits.maxZoom(1.7f, 12f) > ZoomLimits.maxZoom(1.7f, 6f))
        assertEquals(ZoomLimits.maxZoom(1f, ZoomLimits.MAX_FACTOR), ZoomLimits.maxZoom(1f, 100f), 0f)
    }

    @Test fun returnToWritingOnlyWhenEnabled() {
        assertTrue(ToolReturn.shouldReturn(enabled = true, temporaryTool = true, reclick = true, emptyTap = false, hasSelection = true))
        assertTrue(ToolReturn.shouldReturn(enabled = true, temporaryTool = true, reclick = false, emptyTap = true, hasSelection = false))
        // Dotknięcie pustego miejsca przy zaznaczeniu najpierw tylko je zdejmuje.
        assertFalse(ToolReturn.shouldReturn(enabled = true, temporaryTool = true, reclick = false, emptyTap = true, hasSelection = true))
        assertFalse(ToolReturn.shouldReturn(enabled = false, temporaryTool = true, reclick = true, emptyTap = true, hasSelection = false))
        assertFalse(ToolReturn.shouldReturn(enabled = true, temporaryTool = false, reclick = true, emptyTap = true, hasSelection = false))
    }

    @Test fun alignCyclesAndJustifySurvivesCodec() {
        var a = TextBox.ALIGN_START
        val seen = ArrayList<Int>()
        repeat(4) { a = TextBox.nextAlign(a); seen.add(a) }
        assertEquals(listOf(TextBox.ALIGN_CENTER, TextBox.ALIGN_END, TextBox.ALIGN_JUSTIFY, TextBox.ALIGN_START), seen)
        val t = TextBox(Ids.next(), 50f, 50f, 80f, 20f, 0f, "Wyjustowany akapit", 10f, 0xFF000000.toInt(), align = TextBox.ALIGN_JUSTIFY)
        val back = PageCodec.decode(PageCodec.encode(PageContent.of(listOf(t)))).elements[0] as TextBox
        assertEquals(TextBox.ALIGN_JUSTIFY, back.align)
    }
}
