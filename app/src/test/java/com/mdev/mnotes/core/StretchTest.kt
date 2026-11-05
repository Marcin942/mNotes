package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.edit.Stretch
import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.model.ImageCrop
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** LP-4: rozciąganie zaznaczenia w jedną oś. */
class StretchTest {

    private val eps = 1e-3f

    private fun text(w: Float = 100f, h: Float = 20f, rot: Float = 0f, size: Float = 12f, stretch: Float = 1f) =
        TextBox(Ids.next(), 50f, 40f, w, h, rot, "abc", size, 0xFF000000.toInt(), stretch = stretch)

    @Test
    fun alongKeepsAnchorAndPerpendicular() {
        val m = Stretch.along(1f, 0f, 2f, 10f, 5f)
        assertEquals(10f, m.mapX(10f, 5f), eps); assertEquals(5f, m.mapY(10f, 5f), eps)
        // x od kotwicy podwaja się, y bez zmian
        assertEquals(30f, m.mapX(20f, 99f), eps); assertEquals(99f, m.mapY(20f, 99f), eps)
    }

    @Test
    fun alongDiagonalAxis() {
        // kierunek nie musi być jednostkowy; prostopadły (1, -1) zostaje w miejscu
        val m = Stretch.along(3f, 3f, 3f, 0f, 0f)
        assertEquals(1f, m.mapX(1f, -1f), eps); assertEquals(-1f, m.mapY(1f, -1f), eps)
        assertEquals(3f, m.mapX(1f, 1f), eps); assertEquals(3f, m.mapY(1f, 1f), eps)
    }

    @Test
    fun factorProjectsOnHandleAxis() {
        // kotwica (0,0), uchwyt (10,0): palec w (25, 40) — ruch w poprzek osi się nie liczy
        assertEquals(2.5f, Stretch.factor(0f, 0f, 10f, 0f, 25f, 40f), eps)
        assertEquals(Stretch.MIN_FACTOR, Stretch.factor(0f, 0f, 10f, 0f, -5f, 0f), eps)
        val m = Stretch.handle(0f, 0f, 0f, 10f, 3f, 20f)
        assertEquals(5f, m.mapX(5f, 10f), eps); assertEquals(20f, m.mapY(5f, 10f), eps)
    }

    @Test
    fun uniformScaleKeepsTextStretch() {
        val t = text().transformed(Affine.scale(2f, 2f, 0f, 0f))
        assertEquals(1f, t.stretch, eps); assertEquals(24f, t.size, eps); assertEquals(200f, t.w, eps); assertEquals(40f, t.h, eps)
    }

    @Test
    fun horizontalStretchWidensLettersOnly() {
        val t = text().transformed(Stretch.along(1f, 0f, 2f, 0f, 40f))
        assertEquals(2f, t.stretch, eps); assertEquals(12f, t.size, eps); assertEquals(200f, t.w, eps); assertEquals(20f, t.h, eps)
        // szerokość łamania w jednostkach czcionki zostaje ta sama, więc wiersze się nie przestawiają
        assertEquals(100f / 12f, t.w / t.stretch / t.size, eps)
    }

    @Test
    fun verticalStretchGrowsFontAndNarrowsLetters() {
        val t = text().transformed(Stretch.along(0f, 1f, 3f, 50f, 0f))
        assertEquals(36f, t.size, eps); assertEquals(1f / 3f, t.stretch, eps); assertEquals(100f, t.w, eps); assertEquals(60f, t.h, eps)
    }

    @Test
    fun rotatedTextStretchedAcrossItsAxis() {
        // tekst obrócony o 90°: rozciągnięcie strony w x to rozciągnięcie w lokalnej osi y pola
        val t = text(rot = (PI / 2).toFloat()).transformed(Stretch.along(1f, 0f, 2f, 50f, 40f))
        assertEquals(24f, t.size, eps); assertEquals(0.5f, t.stretch, eps); assertEquals(100f, t.w, eps); assertEquals(40f, t.h, eps)
    }

    @Test
    fun strokeGoesThroughSharedTransform() {
        val s = Stroke(Ids.next(), PenKind.BALLPOINT, 0, 2f, floatArrayOf(0f, 0f, 1f, 10f, 10f, 1f))
        val t = s.transformed(Stretch.along(1f, 0f, 4f, 0f, 0f))
        assertEquals(40f, t.pts[3], eps); assertEquals(10f, t.pts[4], eps)
    }

    @Test
    fun restoreWidenedTextKeepsCornerAndFont() {
        val t = text(w = 200f, stretch = 2f)
        val r = Stretch.restoredText(t)
        assertEquals(1f, r.stretch, 0f); assertEquals(100f, r.w, eps); assertEquals(12f, r.size, eps); assertEquals(20f, r.h, eps)
        assertEquals(t.cx - t.w / 2, r.cx - r.w / 2, eps); assertEquals(t.cy - t.h / 2, r.cy - r.h / 2, eps)
    }

    @Test
    fun restoreNarrowedTextShrinksFont() {
        val t = text(size = 36f, h = 60f, stretch = 1f / 3f)
        val r = Stretch.restoredText(t)
        assertEquals(12f, r.size, eps); assertEquals(100f, r.w, eps); assertEquals(20f, r.h, eps)
        assertSame(r, Stretch.restoredText(r))
    }

    @Test
    fun restoreRotatedTextKeepsLocalCorner() {
        val rot = 0.7f
        val t = text(w = 300f, rot = rot, stretch = 3f)
        val r = Stretch.restoredText(t)
        fun corner(cx: Float, cy: Float, w: Float, h: Float) =
            (cx - cos(rot) * w / 2 + sin(rot) * h / 2) to (cy - sin(rot) * w / 2 - cos(rot) * h / 2)
        val a = corner(t.cx, t.cy, t.w, t.h); val b = corner(r.cx, r.cy, r.w, r.h)
        assertEquals(a.first, b.first, eps); assertEquals(a.second, b.second, eps)
    }

    @Test
    fun restoreImageFitsSourceAspectInsideFrame() {
        // plik 400×200 (2:1), ramka rozciągnięta do 100×100
        val im = ImageEl(Ids.next(), "a.png", 50f, 50f, 100f, 100f)
        assertTrue(Stretch.isImageStretched(im, 400f, 200f))
        val r = Stretch.restoredImage(im, 400f, 200f)
        assertEquals(100f, r.w, eps); assertEquals(50f, r.h, eps)
        assertEquals(0f, r.cx - r.w / 2, eps); assertEquals(0f, r.cy - r.h / 2, eps)
        assertFalse(Stretch.isImageStretched(r, 400f, 200f))
    }

    @Test
    fun restoreImageUsesCropAspect() {
        // wycinek lewej połowy pliku 400×200 to kwadrat; ramka 300×100 wraca do 100×100
        val im = ImageEl(Ids.next(), "a.png", 150f, 50f, 300f, 100f, crop = ImageCrop(0f, 0f, 0.5f, 1f))
        val r = Stretch.restoredImage(im, 400f, 200f)
        assertEquals(100f, r.w, eps); assertEquals(100f, r.h, eps)
    }

    @Test
    fun stretchSurvivesCodecAndDefaultAddsNoBytes() {
        fun enc(s: Float) = PageCodec.encode(PageContent.of(listOf(text(stretch = s))))
        // bez rozciągnięcia zapis jest bajt w bajt taki jak przed LP-4 (stare pliki i starsze wersje bez zmian)
        assertEquals(enc(1f).size, PageCodec.encode(PageContent.of(listOf(text()))).size)
        assertEquals(1f, (PageCodec.decode(enc(1f)).elements[0] as TextBox).stretch, 0f)
        assertEquals(2.5f, (PageCodec.decode(enc(2.5f)).elements[0] as TextBox).stretch, 0f)
        val both = TextBox(Ids.next(), 1f, 2f, 3f, 4f, 0f, "x", 9f, 0, lineSpacing = 1.15f, mirror = true, stretch = 0.5f)
        val back = PageCodec.decode(PageCodec.encode(PageContent.of(listOf(both)))).elements[0] as TextBox
        assertEquals(1.15f, back.lineSpacing, 0f); assertTrue(back.mirror); assertEquals(0.5f, back.stretch, 0f)
    }
}
