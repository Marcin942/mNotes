package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.PipetteMath
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PipetteMathTest {
    private val white = 0xFFFFFFFF.toInt()
    private val ochre = 0xFFDDA11C.toInt()
    private val green = 0xFF7BD389.toInt()

    private fun line(id: Int, pen: PenKind, color: Int, width: Float, y: Float) =
        Stroke(id, pen, color, width, floatArrayOf(10f, y, 1f, 90f, y, 1f))

    @Test fun penStrokeGivesItsColor() {
        val els = listOf(line(1, PenKind.BALLPOINT, ochre, 2f, 50f))
        assertEquals(ochre, PipetteMath.originalColor(els, 40f, 50f, 1f))
    }

    @Test fun highlighterGivesItsStoredColorAcrossItsWidth() {
        val els = listOf(line(1, PenKind.HIGHLIGHTER, green, 14f, 50f))
        assertEquals(green, PipetteMath.originalColor(els, 40f, 55f, 1f))
    }

    @Test fun highlighterWinsOverWritingOnTopOfIt() {
        val els = listOf(line(1, PenKind.HIGHLIGHTER, green, 14f, 50f), line(2, PenKind.BALLPOINT, white, 2f, 50f))
        assertEquals(green, PipetteMath.originalColor(els, 40f, 50f, 1f))
    }

    @Test fun highlighterWinsOverWritingBelowIt() {
        val els = listOf(line(1, PenKind.BALLPOINT, white, 2f, 50f), line(2, PenKind.HIGHLIGHTER, green, 14f, 50f))
        assertEquals(green, PipetteMath.originalColor(els, 40f, 50f, 1f))
    }

    @Test fun writingOutsideHighlighterStillGivesItsColor() {
        val els = listOf(line(1, PenKind.HIGHLIGHTER, green, 6f, 50f), line(2, PenKind.BALLPOINT, ochre, 2f, 70f))
        assertEquals(ochre, PipetteMath.originalColor(els, 40f, 70f, 1f))
    }

    @Test fun highlighterFillCountsAsHighlighter() {
        val ring = floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f)
        val els = listOf(FillEl(1, green, true, listOf(ring)), line(2, PenKind.BALLPOINT, white, 2f, 50f))
        assertEquals(green, PipetteMath.originalColor(els, 40f, 50f, 1f))
    }

    @Test fun nothingUnderPointGivesNull() {
        assertNull(PipetteMath.originalColor(listOf(line(1, PenKind.BALLPOINT, ochre, 2f, 50f)), 40f, 80f, 1f))
    }
}
