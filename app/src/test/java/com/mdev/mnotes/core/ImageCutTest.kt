package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.CutLine
import com.mdev.mnotes.core.edit.ImageCut
import com.mdev.mnotes.core.model.ImageCrop
import com.mdev.mnotes.core.model.ImageEl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCutTest {
    private var next = 100
    private fun id() = next++
    private fun img(rot: Float = 0f, crop: ImageCrop = ImageCrop.FULL) =
        ImageEl(1, "a.webp", 200f, 300f, 200f, 100f, rot, 0.8f, false, crop)

    @Test
    fun noLinesGivesNoPieces() {
        assertTrue(ImageCut.split(img(), emptyList(), 0f, ::id).isEmpty())
    }

    @Test
    fun verticalCutSplitsWidthAndCrop() {
        val p = ImageCut.split(img(), listOf(CutLine(true, 0.25f)), 0f, ::id)
        assertEquals(2, p.size)
        assertEquals(50f, p[0].w, 1e-3f); assertEquals(150f, p[1].w, 1e-3f)
        assertEquals(100f, p[0].h, 1e-3f)
        assertEquals(125f, p[0].cx, 1e-3f); assertEquals(225f, p[1].cx, 1e-3f)
        assertEquals(ImageCrop(0f, 0f, 0.25f, 1f), p[0].crop)
        assertEquals(ImageCrop(0.25f, 0f, 1f, 1f), p[1].crop)
        assertEquals(0.8f, p[1].alpha, 0f)
    }

    @Test
    fun gridIsRowMajor() {
        val p = ImageCut.split(img(), listOf(CutLine(true, 0.5f), CutLine(false, 0.5f)), 0f, ::id)
        assertEquals(4, p.size)
        assertEquals(ImageCrop(0f, 0f, 0.5f, 0.5f), p[0].crop)
        assertEquals(ImageCrop(0.5f, 0f, 1f, 0.5f), p[1].crop)
        assertEquals(ImageCrop(0f, 0.5f, 0.5f, 1f), p[2].crop)
    }

    @Test
    fun cutOfCroppedImageStaysInsideItsCrop() {
        val p = ImageCut.split(img(crop = ImageCrop(0.5f, 0f, 1f, 1f)), listOf(CutLine(true, 0.5f)), 0f, ::id)
        assertEquals(ImageCrop(0.5f, 0f, 0.75f, 1f), p[0].crop)
        assertEquals(ImageCrop(0.75f, 0f, 1f, 1f), p[1].crop)
    }

    @Test
    fun gapKeepsCenterOfMass() {
        val p = ImageCut.split(img(), listOf(CutLine(true, 0.5f)), 10f, ::id)
        assertEquals(200f, (p[0].cx + p[1].cx) / 2f, 1e-3f)
        assertEquals(10f, (p[1].cx - p[0].cx) - 100f, 1e-3f)
    }

    @Test
    fun rotatedImagePiecesStayOnRotatedAxes() {
        val rot = Math.PI.toFloat() / 2f
        val p = ImageCut.split(img(rot), listOf(CutLine(true, 0.5f)), 0f, ::id)
        // obraz obrócony o 90°: lewa połowa lokalna leży nad środkiem na stronie
        assertEquals(200f, p[0].cx, 1e-2f); assertEquals(250f, p[0].cy, 1e-2f)
        assertEquals(200f, p[1].cx, 1e-2f); assertEquals(350f, p[1].cy, 1e-2f)
        assertEquals(rot, p[0].rot, 0f)
    }

    @Test
    fun tooThinPiecesAndDuplicatesAreDropped() {
        val lines = listOf(CutLine(true, 0.01f), CutLine(true, 0.5f), CutLine(true, 0.505f), CutLine(false, 0.99f))
        val n = ImageCut.normalize(img(), lines)
        assertEquals(1, n.size)
        assertEquals(0.5f, n[0].pos, 0f)
    }

    @Test
    fun localCoordinatesRoundTrip() {
        val im = img(0.7f)
        val u = ImageCut.toLocal(im, 230f, 280f)
        val back = ImageCut.toPage(im, u[0], u[1])
        assertEquals(230f, back[0], 1e-3f); assertEquals(280f, back[1], 1e-3f)
    }

    @Test
    fun snapPullsNearMiddleOnly() {
        assertEquals(0.5f, ImageCut.snap(0.51f, 200f, 6f), 0f)
        assertEquals(0.4f, ImageCut.snap(0.4f, 200f, 6f), 0f)
    }

    @Test
    fun nextLineGoesToMiddleOfBiggestGap() {
        val im = img()
        assertEquals(0.5f, ImageCut.nextPos(im, true, emptyList())!!, 0f)
        assertEquals(0.25f, ImageCut.nextPos(im, true, listOf(CutLine(true, 0.5f)))!!, 0f)
        assertEquals(0.625f, ImageCut.nextPos(im, true, listOf(CutLine(true, 0.25f)))!!, 0f)
        assertEquals(0.5f, ImageCut.nextPos(im, false, listOf(CutLine(true, 0.5f)))!!, 0f)
    }

    @Test
    fun nextLineIsNullWhenNoRoomLeft() {
        val tiny = ImageEl(2, "a", 0f, 0f, 20f, 20f)
        assertEquals(null, ImageCut.nextPos(tiny, true, emptyList()))
    }
}
