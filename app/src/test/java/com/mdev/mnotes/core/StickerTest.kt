package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.edit.Sticker
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F19: naklejki — zapis rysunku i wielokrotne wstawianie. */
class StickerTest {
    private fun stroke(vararg xy: Float): Stroke {
        val pts = FloatArray(xy.size / 2 * 3)
        for (i in 0 until xy.size / 2) { pts[3 * i] = xy[2 * i]; pts[3 * i + 1] = xy[2 * i + 1]; pts[3 * i + 2] = 1f }
        return Stroke(Ids.next(), PenKind.BALLPOINT, 0, 2f, pts, locked = true)
    }

    private val drawing = listOf(stroke(100f, 200f, 140f, 260f), stroke(120f, 200f, 160f, 220f))

    @Test fun normalizeMovesToOriginAndUnlocks() {
        val n = Sticker.normalize(drawing)!!
        var b = Bounds.EMPTY; for (e in n.elements) b = b.union(e.bounds)
        assertEquals(0f, b.l, 1e-4f); assertEquals(0f, b.t, 1e-4f)
        assertEquals(b.w, n.w, 1e-4f)
        assertTrue(n.elements.none { it.locked })
        assertNull(Sticker.normalize(emptyList()))
    }

    @Test fun placeCentersWithNewIdsAndScale() {
        val n = Sticker.normalize(drawing)!!
        val a = Sticker.place(n, 300f, 300f)
        val b2 = Sticker.place(n, 300f, 300f)
        assertNotEquals(a[0].id, b2[0].id)
        var b = Bounds.EMPTY; for (e in a) b = b.union(e.bounds)
        assertEquals(300f, b.cx, 0.01f); assertEquals(300f, b.cy, 0.01f)
        val half = Sticker.place(n, 0f, 0f, 0.5f)
        var hb = Bounds.EMPTY; for (e in half) hb = hb.union(e.bounds)
        assertTrue(hb.w < b.w * 0.6f)
    }

    @Test fun imagesGetNewAssetNamesAndSurviveCodec() {
        val img = ImageEl(Ids.next(), "stary.jpg", 50f, 50f, 40f, 30f)
        val n = Sticker.normalize(listOf(img, stroke(0f, 0f, 10f, 10f)))!!
        assertEquals(setOf("stary.jpg"), Sticker.assets(n.elements))
        val placed = Sticker.place(n, 200f, 200f) { "nowy-$it" }
        assertEquals("nowy-stary.jpg", (placed[0] as ImageEl).asset)
        // Naklejka zapisuje się jak strona (MNP1), więc obsługuje wszystkie rodzaje elementów.
        val back = PageCodec.decode(PageCodec.encode(PageContent.of(n.elements)))
        assertEquals(2, back.size)
    }

    @Test fun fitScaleNeverEnlarges() {
        assertEquals(1f, Sticker.fitScale(50f, 50f, 420f, 595f), 0f)
        assertTrue(Sticker.fitScale(1000f, 200f, 420f, 595f) < 0.5f)
    }
}
