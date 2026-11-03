package com.mdev.mnotes.core

import com.mdev.mnotes.core.export.ClipExport
import com.mdev.mnotes.core.export.ClipExport.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Eksport do schowka: tryb dla formatu i tło wycinka lassem. */
class ClipExportTest {
    @Test
    fun textFormatsGoAsTextUnlessSplitIntoFiles() {
        assertEquals(Mode.TEXT, ClipExport.mode(text = true, perPageImage = false, pages = 12, split = false))
        assertEquals(Mode.FILE, ClipExport.mode(text = true, perPageImage = false, pages = 12, split = true))
    }

    @Test
    fun imagesPerPageUpToTheLimitThenOneArchive() {
        assertEquals(Mode.IMAGE_PER_PAGE, ClipExport.mode(text = false, perPageImage = true, pages = 1, split = false))
        assertEquals(Mode.IMAGE_PER_PAGE, ClipExport.mode(text = false, perPageImage = true, pages = ClipExport.MAX_IMAGES, split = false))
        assertEquals(Mode.FILE, ClipExport.mode(text = false, perPageImage = true, pages = ClipExport.MAX_IMAGES + 1, split = false))
        assertEquals(Mode.FILE, ClipExport.mode(text = false, perPageImage = true, pages = 3, split = true))
        assertEquals(Mode.FILE, ClipExport.mode(text = false, perPageImage = false, pages = 1, split = false))   // PDF
    }

    @Test
    fun backgroundChoices() {
        assertNull(ClipExport.background(ClipExport.BG_NONE))
        assertEquals(0xFF000000.toInt(), ClipExport.background(ClipExport.BG_BLACK))
        assertEquals(0xFFFFFFFF.toInt(), ClipExport.background(ClipExport.BG_WHITE))
    }

    @Test
    fun overBlendsEdgeAndFillsTransparentArea() {
        val black = 0xFF000000.toInt(); val white = 0xFFFFFFFF.toInt()
        assertEquals(black, ClipExport.over(0x00FF0000, black))                 // poza obrysem: samo tło
        assertEquals(0xFF123456.toInt(), ClipExport.over(0xFF123456.toInt(), white))   // wnętrze bez zmian
        // brzeg w połowie krycia: czerwień na bieli → róż
        assertEquals(0xFFFF7F7F.toInt(), ClipExport.over(0x80FF0000.toInt(), white))
        assertEquals(0xFF800000.toInt(), ClipExport.over(0x80FF0000.toInt(), black))
    }
}
