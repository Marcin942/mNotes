package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.CropFrame
import com.mdev.mnotes.core.edit.CropGeom
import com.mdev.mnotes.core.edit.CropHandle
import com.mdev.mnotes.core.edit.CropPlace
import com.mdev.mnotes.core.model.ImageCrop
import com.mdev.mnotes.core.model.ImageEl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class CropGeomTest {
    private val iw = 4000f
    private val ih = 3000f
    private fun deg(d: Float) = (d * PI / 180.0).toFloat()

    @Test fun cropRoundTripsThroughFrame() {
        val c = ImageCrop(0.1f, 0.2f, 0.7f, 0.9f, 0.15f)
        val back = CropGeom.cropOf(CropGeom.frameOf(c, iw, ih), iw, ih)
        assertEquals(c.l, back.l, 1e-5f); assertEquals(c.t, back.t, 1e-5f)
        assertEquals(c.r, back.r, 1e-5f); assertEquals(c.b, back.b, 1e-5f); assertEquals(c.a, back.a, 1e-6f)
    }

    @Test fun nearlyFullFrameSnapsToFull() {
        val f = CropFrame(iw / 2, ih / 2, iw / 2 - 0.2f, ih / 2 - 0.3f, 0f)
        assertTrue(CropGeom.cropOf(f, iw, ih).isFull)
        assertFalse(CropGeom.cropOf(f.copy(a = 0.01f), iw, ih).isFull)
    }

    @Test fun tiltedFullFrameShrinksToFit() {
        val g = CropGeom.fitted(CropGeom.full(iw, ih).copy(a = deg(5f)), iw, ih)
        assertTrue(g.hw < iw / 2 && g.hh < ih / 2)
        assertTrue(CropGeom.fits(g, iw, ih))
        // wszystkie narożniki leżą w pliku
        val k = CropGeom.corners(g)
        for (i in 0 until 4) { assertTrue(k[i * 2] >= 0f && k[i * 2] <= iw); assertTrue(k[i * 2 + 1] >= 0f && k[i * 2 + 1] <= ih) }
        // proporcje ramki zostają
        assertEquals(iw / ih, g.hw / g.hh, 1e-3f)
    }

    @Test fun smallTiltedFrameOnlyMovesInsteadOfShrinking() {
        val f = CropFrame(100f, 100f, 500f, 300f, deg(10f))
        val g = CropGeom.fitted(f, iw, ih)
        assertEquals(f.hw, g.hw, 0f); assertEquals(f.hh, g.hh, 0f)
        assertTrue(g.cx > f.cx && g.cy > f.cy)
        assertTrue(CropGeom.fits(g, iw, ih))
    }

    @Test fun rotatingBackRestoresTheWantedSize() {
        val want = CropGeom.full(iw, ih)
        val tilted = CropGeom.fitted(want.copy(a = deg(8f)), iw, ih)
        val straight = CropGeom.fitted(want.copy(a = 0f), iw, ih)
        assertTrue(tilted.hw < want.hw)
        assertEquals(want.hw, straight.hw, 1e-3f)
    }

    @Test fun movedStopsAtImageEdge() {
        val f = CropFrame(2000f, 1500f, 500f, 400f, deg(7f))
        val g = CropGeom.moved(f, 99999f, -99999f, iw, ih)
        assertTrue(CropGeom.fits(g, iw, ih))
        assertEquals(f.hw, g.hw, 0f)
        assertEquals(iw - CropGeom.MARGIN, g.cx + CropGeom.extentX(g), 1e-2f)
        assertEquals(CropGeom.MARGIN, g.cy - CropGeom.extentY(g), 1e-2f)
    }

    @Test fun resizeKeepsOppositeCornerAndNeverLeavesImage() {
        val f = CropFrame(2000f, 1500f, 800f, 600f, deg(12f))
        val anchor = CropGeom.corners(f).let { floatArrayOf(it[0], it[1]) } // lewy górny
        val g = CropGeom.resized(f, CropHandle.BR, 9e6f, 9e6f, iw, ih, 20f)
        assertTrue(CropGeom.fits(g, iw, ih))
        assertTrue(g.hw > f.hw)
        val k = CropGeom.corners(g)
        assertEquals(anchor[0], k[0], 0.05f); assertEquals(anchor[1], k[1], 0.05f)
    }

    @Test fun resizeShrinksDownToMinimumOnly() {
        val f = CropFrame(2000f, 1500f, 800f, 600f, 0f)
        val g = CropGeom.resized(f, CropHandle.R, -9e6f, 1500f, iw, ih, 30f)
        assertEquals(30f, g.hw, 1e-3f)
        assertEquals(f.hh, g.hh, 0f)
        assertEquals(f.cx - f.hw, g.cx - g.hw, 1e-2f)
    }

    @Test fun ratioLockKeepsProportionsOnCornerDrag() {
        val f = CropFrame(2000f, 1500f, 800f, 600f, 0f)
        val g = CropGeom.resized(f, CropHandle.BR, 2600f, 1800f, iw, ih, 30f, 4f / 3f)
        assertEquals(4f / 3f, g.hw / g.hh, 1e-3f)
        assertEquals(f.cx - f.hw, g.cx - g.hw, 1e-2f)
        assertEquals(f.cy - f.hh, g.cy - g.hh, 1e-2f)
    }

    @Test fun edgeDragWithRatioLockResizesBothAxes() {
        val f = CropFrame(2000f, 1500f, 400f, 400f, 0f)
        val g = CropGeom.resized(f, CropHandle.R, 2600f, 1500f, iw, ih, 30f, 1f)
        assertEquals(1f, g.hw / g.hh, 1e-3f)
        assertEquals(f.cx - f.hw, g.cx - g.hw, 1e-2f)
        assertEquals(f.cy, g.cy, 1e-2f)
    }

    @Test fun quarterTurnKeepsTheSameRegion() {
        val f = CropFrame(1500f, 1200f, 700f, 400f, deg(6f))
        for (cw in listOf(true, false)) {
            val q = CropGeom.quarter(f, cw)
            assertEquals(f.hh, q.hw, 0f); assertEquals(f.hw, q.hh, 0f)
            val a = CropGeom.corners(f).toList().chunked(2).map { it[0] to it[1] }
            val b = CropGeom.corners(q).toList().chunked(2).map { it[0] to it[1] }
            for (p in a) assertTrue(b.any { abs(it.first - p.first) < 0.05f && abs(it.second - p.second) < 0.05f })
        }
    }

    @Test fun placementRoundTripsAndKeepsPivotInPlace() {
        val p = CropPlace(300f, 400f, 0.05f, deg(20f))
        val pg = p.toPage(1234f, 567f, iw, ih)
        val back = p.toFile(pg[0], pg[1], iw, ih)
        assertEquals(1234f, back[0], 0.01f); assertEquals(567f, back[1], 0.01f)
        val q = p.turned(deg(-35f), 1234f, 567f, iw, ih)
        val pg2 = q.toPage(1234f, 567f, iw, ih)
        assertEquals(pg[0], pg2[0], 0.01f); assertEquals(pg[1], pg2[1], 0.01f)
    }

    @Test fun applyingUnchangedFrameLeavesTheImageAsItWas() {
        val im = ImageEl(1, "a.webp", 250f, 300f, 160f, 120f, 0.3f, 1f, false, ImageCrop(0.1f, 0.2f, 0.7f, 0.9f, 0.1f))
        val f = CropGeom.frameOf(im.crop, iw, ih)
        val p = CropGeom.placeOf(im, iw, ih)
        // plik wypełnia ramkę w tej samej skali
        assertEquals(im.w / (2 * f.hw), p.s, 1e-6f)
        val out = CropGeom.applied(im, f, p, iw, ih)
        assertEquals(im.cx, out.cx, 0.01f); assertEquals(im.cy, out.cy, 0.01f)
        assertEquals(im.w, out.w, 0.01f)
        assertEquals(im.rot, out.rot, 1e-4f)
        assertEquals(im.crop.a, out.crop.a, 1e-5f)
    }

    @Test fun shrinkingTheFrameKeepsVisibleContentOnThePage() {
        val im = ImageEl(1, "a.webp", 250f, 300f, 400f, 300f, 0.2f, 1f, false)
        val p = CropGeom.placeOf(im, iw, ih)
        // punkt pliku ((iw*0.3), (ih*0.4)) leży na stronie tam samo przed i po przycięciu
        val fx = iw * 0.3f; val fy = ih * 0.4f
        val before = p.toPage(fx, fy, iw, ih)
        val g = CropFrame(iw * 0.35f, ih * 0.45f, iw * 0.15f, ih * 0.1f, 0f)
        val out = CropGeom.applied(im, g, p, iw, ih)
        // odwzorowanie punktu: ze współrzędnych obrazu po przycięciu na stronę
        val c = out.crop
        val u = (fx / iw - c.l) / c.width - 0.5f; val v = (fy / ih - c.t) / c.height - 0.5f
        val lx = u * out.w; val ly = v * out.h
        val x = out.cx + lx * kotlin.math.cos(out.rot) - ly * kotlin.math.sin(out.rot)
        val y = out.cy + lx * kotlin.math.sin(out.rot) + ly * kotlin.math.cos(out.rot)
        assertEquals(before[0], x, 0.02f); assertEquals(before[1], y, 0.02f)
    }

    @Test fun tiltedCropMapsFileToImageLikeTheRenderer() {
        // ramka obrócona o 10° w pliku: obraz na stronie ma rot = phi + a, a środek pliku leży tam, gdzie wskazuje CropPlace
        val im = ImageEl(1, "a.webp", 500f, 400f, 300f, 200f, 0f, 1f, false)
        val p = CropGeom.placeOf(im, iw, ih)
        val f = CropGeom.fitted(CropGeom.full(iw, ih).copy(a = deg(10f)), iw, ih)
        val out = CropGeom.applied(im, f, p, iw, ih)
        assertEquals(deg(10f), out.rot, 1e-4f)
        assertEquals(out.w / out.h, f.hw / f.hh, 1e-4f)
        assertTrue(abs(out.crop.a - deg(10f)) < 1e-5f)
    }

    @Test fun straighteningSliderNeverThrowsAtAnyAngle() {
        // suwak prostowania przechodzi przez setki kątów; zaokrąglenie przy zmniejszaniu ramki nie może dać pustego zakresu
        for ((w, h) in listOf(4000f to 3000f, 4032f to 3024f, 3024f to 4032f, 1080f to 2400f, 777f to 333f, 100f to 100f)) {
            val want = CropGeom.full(w, h)
            var d = -45f
            while (d <= 45f) {
                for (q in -2..2) {
                    val g = CropGeom.fitted(want.copy(a = CropGeom.normAngle(q * CropGeom.HALF_PI + deg(d))), w, h)
                    assertTrue(g.hw > 0f && g.hh > 0f && !g.cx.isNaN() && !g.cy.isNaN())
                }
                d += 0.037f
            }
        }
    }

    @Test fun sliderKeepsItsQuarterAtPlusAndMinus45() {
        // suwak ustawia a = q·90° + δ; przy δ = ±45° odczyt odchylenia nie może przeskoczyć do sąsiedniej ćwiartki
        for (q in 0..3) for (d in listOf(-45f, -44.9f, 0f, 44.9f, 45f)) {
            val a = CropGeom.normAngle(q * CropGeom.HALF_PI + deg(d))
            assertEquals(deg(d), CropGeom.tiltOf(a, q), 1e-4f)
        }
        // na starcie odchylenie mieści się w (-45°, 45°]
        assertEquals(0, CropGeom.quarterOf(deg(45f)))
        assertEquals(1, CropGeom.quarterOf(deg(45.1f)))
        assertEquals(3, CropGeom.quarterOf(deg(-45f)))
        assertEquals(0, CropGeom.quarterOf(deg(-44.9f)))
    }
}
