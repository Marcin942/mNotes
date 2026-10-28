package com.mdev.mnotes.core

import com.mdev.mnotes.core.color.PageTone
import com.mdev.mnotes.core.model.Paper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 2c-A9: ton strony PDF i klasyfikator rysunek/zdjęcie na syntetycznych obrazach 32×32. */
class PageToneTest {
    private val n = 32
    private fun img(f: (x: Int, y: Int) -> Int) = IntArray(n * n) { f(it % n, it / n) }

    @Test fun darkSlideKeepsItsPaperAndWhitePageStaysLight() {
        val navy = 0xFF1A2440.toInt()
        val slide = img { x, y -> if (y in 8..10 && x in 4..27) 0xFFFFFFFF.toInt() else navy }
        val paper = PageTone.pdfPaper(slide)
        assertNotNull(paper)
        assertTrue(Paper.isDark(paper!!))
        val page = img { x, y -> if (y % 4 == 0 && x in 3..28) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        assertNull(PageTone.pdfPaper(page))
    }

    @Test fun lineArtOnTransparentIsADrawing() {
        assertTrue(PageTone.isDrawing(img { x, y -> if (x == y || x == n - 1 - y) 0xFF000000.toInt() else 0x00000000 }))
    }

    @Test fun diagramWithFewColorsIsADrawing() {
        assertTrue(PageTone.isDrawing(img { x, y ->
            when {
                x in 4..12 && y in 4..12 -> 0xFF1E88E5.toInt()
                y == 20 -> 0xFF000000.toInt()
                x == 25 -> 0xFFE53935.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
        }))
    }

    @Test fun photosStayPhotos() {
        val r = Random(7)
        assertFalse(PageTone.isDrawing(img { _, _ -> (0xFF shl 24) or r.nextInt(0x1000000) }))
        // Gładkie przejście z lekkim szumem — jak niebo i trawa na zdjęciu.
        assertFalse(PageTone.isDrawing(img { x, y ->
            val rr = (x * 8 + r.nextInt(6)).coerceIn(0, 255); val gg = (y * 8 + r.nextInt(6)).coerceIn(0, 255); val bb = (128 + r.nextInt(20))
            (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        }))
    }
}
