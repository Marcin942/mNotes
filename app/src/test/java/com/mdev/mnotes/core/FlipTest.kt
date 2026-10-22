package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.edit.CropGeom
import com.mdev.mnotes.core.edit.CutLine
import com.mdev.mnotes.core.edit.ImageCut
import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.model.BoxElement
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.ImageCrop
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TapeEl
import com.mdev.mnotes.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Odbicie (przekształcenie z ujemnym wyznacznikiem) wszystkich elementów oraz obrót zaznaczenia jak jednej całości. */
class FlipTest {
    private val tol = 1e-2f

    private fun text(rot: Float, mirror: Boolean = false) =
        TextBox(1, 200f, 300f, 120f, 40f, rot, "Ala", 14f, 0, mirror = mirror)

    private fun image(rot: Float, mirror: Boolean = false, crop: ImageCrop = ImageCrop.FULL) =
        ImageEl(2, "a.webp", 200f, 300f, 160f, 90f, rot, 1f, false, crop, mirror)

    private fun tape(rot: Float) = TapeEl(3, 200f, 300f, 100f, 20f, rot, 0x55FFFF00)

    /** Punkt strony dla współrzędnych lokalnych (a, b) ∈ [−1, 1]² treści pudełka: środek + R(rot)·diag(±1, 1)·(a·w/2, b·h/2). */
    private fun page(e: BoxElement, a: Float, b: Float): Pair<Float, Float> {
        val lx = (if (e.mirror) -a else a) * e.w / 2f
        val ly = b * e.h / 2f
        val c = cos(e.rot); val s = sin(e.rot)
        return Pair(e.cx + c * lx - s * ly, e.cy + s * lx + c * ly)
    }

    private val transforms = listOf(
        "poziomo" to Affine.scale(-1f, 1f, 250f, 400f),
        "pionowo" to Affine.scale(1f, -1f, 250f, 400f),
        "obrót" to Affine.rotation(0.7f, 250f, 400f),
        "odbicie i obrót" to Affine.scale(-1f, 1f, 250f, 400f).then(Affine.rotation(-1.1f, 250f, 400f)),
        "obrót i odbicie" to Affine.rotation(2.2f, 250f, 400f).then(Affine.scale(1f, -1f, 250f, 400f)),
        "odbicie, skala, przesunięcie" to Affine.scale(-1f, 1f, 10f, 20f).then(Affine.scale(1.7f, 1.7f, 30f, 40f)).then(Affine.translation(15f, -9f)),
    )

    private fun boxes(): List<BoxElement> = listOf(0f, 0.4f, -2.3f, 3.1f).flatMap { r ->
        listOf(text(r), text(r, true), image(r), image(r, true))
    }

    @Test
    fun contentFollowsTheTransformForEveryBox() {
        for (e in boxes()) for ((name, m) in transforms) {
            val t = e.transformed(m) as BoxElement
            for ((a, b) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f, 0.3f to -0.6f)) {
                val (x, y) = page(e, a, b)
                val (nx, ny) = page(t, a, b)
                assertEquals("$name ${e::class.simpleName} rot=${e.rot} mirror=${e.mirror}: x", m.mapX(x, y), nx, tol)
                assertEquals("$name ${e::class.simpleName} rot=${e.rot} mirror=${e.mirror}: y", m.mapY(x, y), ny, tol)
            }
        }
    }

    @Test
    fun reflectionTogglesMirrorAndRotationDoesNot() {
        val flipped = text(0.5f).transformed(Affine.scale(-1f, 1f, 0f, 0f))
        assertTrue((flipped as TextBox).mirror)
        assertFalse((flipped.transformed(Affine.scale(1f, -1f, 0f, 0f)) as TextBox).mirror)
        assertFalse((text(0.5f).transformed(Affine.rotation(1f, 5f, 5f)) as TextBox).mirror)
        assertTrue((image(0f, true).transformed(Affine.rotation(1f, 5f, 5f)) as ImageEl).mirror)
    }

    @Test
    fun flippingTwiceRestoresEveryElement() {
        val h = Affine.scale(-1f, 1f, 250f, 400f)
        val v = Affine.scale(1f, -1f, 250f, 400f)
        for (e in boxes() + tape(0.8f)) for (m in listOf(h, v)) {
            val back = e.transformed(m).transformed(m) as BoxElement
            assertEquals(e.cx, back.cx, tol); assertEquals(e.cy, back.cy, tol)
            assertEquals(e.w, back.w, tol); assertEquals(e.h, back.h, tol)
            assertEquals(e.mirror, back.mirror)
            // kąt porównujemy modulo pełny obrót
            assertEquals(0f, abs(sin((e.rot - back.rot).toDouble())).toFloat(), 1e-3f)
            assertEquals(1f, cos((e.rot - back.rot).toDouble()).toFloat(), 1e-3f)
        }
    }

    @Test
    fun horizontalAndVerticalFlipEqualRotationByHalfTurn() {
        val h = Affine.scale(-1f, 1f, 250f, 400f)
        val v = Affine.scale(1f, -1f, 250f, 400f)
        val half = Affine.rotation(PI.toFloat(), 250f, 400f)
        for (e in boxes()) {
            val a = e.transformed(h).transformed(v) as BoxElement
            val b = e.transformed(half) as BoxElement
            for ((u, w) in listOf(-1f to -1f, 1f to 1f, 0.2f to 0.9f)) {
                val pa = page(a, u, w); val pb = page(b, u, w)
                assertEquals(pb.first, pa.first, tol); assertEquals(pb.second, pa.second, tol)
            }
            assertEquals(b.mirror, a.mirror)
        }
    }

    @Test
    fun tapeKeepsItsRectangleUnderReflection() {
        val m = Affine.scale(-1f, 1f, 250f, 400f)
        val t = tape(0.6f).transformed(m)
        assertEquals(300f, t.cx, tol); assertEquals(300f, t.cy, tol)
        assertEquals(100f, t.w, tol); assertEquals(20f, t.h, tol)
        // prostokąt jest symetryczny, więc kąt liczy się modulo π: oś długa odbija się jak każdy wektor (π − rot)
        assertEquals(cos(2 * (PI.toFloat() - 0.6f)), cos(2 * t.rot), tol)
        assertEquals(sin(2 * (PI.toFloat() - 0.6f)), sin(2 * t.rot), tol)
    }

    @Test
    fun strokeAndFillMirrorTheirPointsAndKeepWidth() {
        val s = Stroke(5, PenKind.BALLPOINT, 0, 3f, floatArrayOf(10f, 10f, 0.5f, 30f, 20f, 0.7f, 50f, 15f, 0.9f))
        val m = Affine.scale(-1f, 1f, 100f, 0f)
        val o = s.transformed(m) as Stroke
        assertEquals(190f, o.pts[0], tol); assertEquals(10f, o.pts[1], tol)
        assertEquals(170f, o.pts[3], tol); assertEquals(150f, o.pts[6], tol)
        assertEquals(0.7f, o.pts[5], 1e-3f)
        assertEquals(3f, o.width, tol)

        val f = FillEl(6, 0, false, listOf(floatArrayOf(0f, 0f, 40f, 0f, 40f, 10f)))
        val g = f.transformed(Affine.scale(1f, -1f, 0f, 5f)) as FillEl
        assertEquals(10f, g.rings[0][1], tol); assertEquals(0f, g.rings[0][5], tol)
    }

    @Test
    fun rotatingASelectionMovesEveryElementAsOneRigidBody() {
        val els: List<Element> = listOf(text(0.3f), image(-0.8f, true), tape(1.2f))
        val m = Affine.rotation(0.9f, 220f, 310f)
        for (e in els) {
            val t = e.transformed(m) as BoxElement
            e as BoxElement
            assertEquals(e.w, t.w, tol); assertEquals(e.h, t.h, tol)
            assertEquals(e.mirror, t.mirror)
            assertEquals(m.mapX(e.cx, e.cy), t.cx, tol); assertEquals(m.mapY(e.cx, e.cy), t.cy, tol)
        }
    }

    @Test
    fun mirrorSurvivesTheCodec() {
        val src = PageContent.of(listOf(text(0.2f, true), text(0f), image(0.1f, true, ImageCrop(0.1f, 0.2f, 0.9f, 0.8f)), image(0f)))
        val back = PageCodec.decode(PageCodec.encode(src)).elements
        assertEquals(listOf(true, false, true, false), back.map { (it as? TextBox)?.mirror ?: (it as ImageEl).mirror })
        assertEquals(ImageCrop(0.1f, 0.2f, 0.9f, 0.8f), (back[2] as ImageEl).crop)
    }

    @Test
    fun mirrorCropIsAnInvolutionAndKeepsFull() {
        val c = ImageCrop(0.1f, 0.2f, 0.7f, 0.9f, 0.3f)
        val twice = CropGeom.mirrorCrop(CropGeom.mirrorCrop(c))
        assertEquals(c.l, twice.l, 1e-6f); assertEquals(c.t, twice.t, 1e-6f); assertEquals(c.r, twice.r, 1e-6f)
        assertEquals(c.b, twice.b, 1e-6f); assertEquals(c.a, twice.a, 1e-6f)
        val once = CropGeom.mirrorCrop(c)
        assertEquals(0.3f, once.l, 1e-6f); assertEquals(0.9f, once.r, 1e-6f); assertEquals(-0.3f, once.a, 1e-6f)
        assertTrue(CropGeom.mirrorCrop(ImageCrop.FULL).isFull)
        val im = image(0.4f, true, c)
        val back = CropGeom.remirrored(CropGeom.unmirrored(im), true)
        assertTrue(back.mirror)
        assertEquals(c.l, back.crop.l, 1e-6f); assertEquals(c.r, back.crop.r, 1e-6f); assertEquals(c.a, back.crop.a, 1e-6f)
        assertFalse(CropGeom.unmirrored(im).mirror)
    }

    @Test
    fun cutOfMirroredImageKeepsTheMirrorAndTheCroppedPixels() {
        var next = 100
        val im = image(0f, true, ImageCrop(0f, 0f, 1f, 1f))
        val p = ImageCut.split(im, listOf(CutLine(true, 0.25f)), 0f) { next++ }
        assertEquals(2, p.size)
        assertTrue(p.all { it.mirror })
        // lustrzane odbicie: wąski kawałek (25 % od lewej na stronie) pochodzi z prawego krańca pliku
        assertEquals(ImageCrop(0.75f, 0f, 1f, 1f), p[0].crop)
        assertEquals(ImageCrop(0f, 0f, 0.75f, 1f), p[1].crop)
        assertEquals(40f, p[0].w, tol); assertEquals(120f, p[1].w, tol)
    }
}
