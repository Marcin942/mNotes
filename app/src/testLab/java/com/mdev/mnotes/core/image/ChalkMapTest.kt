package com.mdev.mnotes.core.image

import com.mdev.mnotes.core.model.ImageCrop
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.Stroke
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs

/** Faza 2 (B2-5…B2-7): podział potoku, mapowanie wyniku na zdjęcie, kreski na kredzie, mediana bez „duchów”. */
class BoardSourceTest {
    @Test
    fun prepareThenFinishIsTheWholePipeline() {
        val photo = Synthetic.photo()
        val whole = BoardPipeline.run(photo)
        val src = BoardPipeline.prepare(photo)
        val parts = BoardPipeline.finish(src)
        assertArrayEquals(whole.image.px, parts.image.px)
        assertEquals(whole.cut, parts.cut, 0f)
        // suwak progu na gotowym źródle = pełny potok z tym progiem
        val opt = BoardOptions(autoCut = false, cut = 0.3f, solid = 0.6f)
        assertArrayEquals(BoardPipeline.run(photo, opt).image.px, BoardPipeline.finish(src, opt).image.px)
    }

    @Test
    fun autoCutAddsTheMedianOnlyAfterFlatten() {
        val rnd = Random(3)
        val noisy = Raster(200, 200, IntArray(40000) { val v = 0.1f + (rnd.nextGaussian() * 0.06).toFloat(); Raster.rgbF(v, v, v) })
        val flat = Chalk.autoCut(noisy, true, flattened = true)
        val raw = Chalk.autoCut(noisy, true, flattened = false)
        assertEquals(flat, Chalk.autoCut(noisy, true), 0f)
        // różnica to mediana jasności tła (ok. 0,1): bez wyrównania próg = K·σ jak w kreda.py
        assertTrue("z medianą $flat, bez $raw", abs(flat - raw - 0.1f) < 0.02f)
    }

    @Test
    fun resultPixelsMapBackOntoTheChalkInThePhoto() {
        val photo = Synthetic.photo()
        val res = BoardPipeline.run(photo)
        val src = res.source!!
        assertTrue("kadr ${src.cropX}, ${src.cropY}", src.warp != null)
        // środek każdego kryjącego piksela wyniku wraca na linię kredy narysowaną w układzie tablicy (v = 0,5)
        val toBoard = Homography.of(Synthetic.QUAD, Quad.of(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))!!
        var n = 0
        for (y in 0 until res.image.h) for (x in 0 until res.image.w) {
            if (Raster.alpha(res.image[x, y]) < 200) continue
            val uv = src.toPhoto(x.toFloat(), y.toFloat())
            val b = toBoard.map(uv[0] * photo.w - 0.5, uv[1] * photo.h - 0.5)
            assertTrue("piksel ($x, $y) → tablica (${b[0]}, ${b[1]})", abs(b[1] - 0.5) < 0.03 && b[0] in 0.17..0.83)
            n++
        }
        assertTrue("kryjących $n", n > 200)
    }

    @Test
    fun withoutStraightenAndCropTheMappingIsTheIdentity() {
        val photo = Synthetic.photo()
        val src = BoardPipeline.prepare(photo, BoardOptions(straighten = false, crop = false))
        assertArrayEquals(floatArrayOf(10.5f / 400f, 20.5f / 300f), src.toPhoto(10f, 20f), 1e-6f)
    }

    @Test
    fun photoWithoutABoardIsSkippedInsteadOfGhosting() {
        val board = Synthetic.photo(seed = 1)
        val other = Synthetic.photo(seed = 2, quad = Quad.of(74f, 48f, 333f, 72f, 318f, 233f, 58f, 213f))
        val wall = Raster.filled(400, 300, Raster.rgbF(0.7f, 0.68f, 0.66f))
        val loaded = ArrayList<Int>()
        val src = BoardPipeline.prepareMany(3, BoardOptions()) { loaded += it; listOf(wall, board, other)[it] }!!
        assertEquals(listOf(0, 1, 2), loaded)
        assertEquals(1, src.ref)
        assertEquals(listOf(0), src.skipped)
        // bez tablicy na żadnym zdjęciu: wszystkie całe, nic nie wypada
        val none = BoardPipeline.prepareMany(2, BoardOptions()) { wall }!!
        assertEquals(emptyList<Int>(), none.skipped)
        assertEquals(0, none.ref)
    }

    @Test
    fun failedLoadGivesNull() {
        assertNull(BoardPipeline.prepareMany(2, BoardOptions()) { if (it == 0) Synthetic.photo() else null })
    }
}

class DecodePlanTest {
    @Test
    fun twelveMegapixelsDecodeStraightTo2400() {
        val p = DecodePlan.of(4000, 3000, 2400)
        assertEquals(1, p.sample)
        assertTrue(p.scales)
        assertEquals(4000, p.density); assertEquals(2400, p.targetDensity)
    }

    @Test
    fun hugePhotoIsSampledFirstAndSmallOneIsLeftAlone() {
        val big = DecodePlan.of(9000, 6000, 2400)
        assertEquals(2, big.sample)
        assertEquals(4500, big.density)
        val small = DecodePlan.of(1600, 1200, 2400)
        assertEquals(1, small.sample)
        assertTrue(!small.scales)
        val preview = DecodePlan.of(4000, 3000, 520)
        assertEquals(4, preview.sample)
        assertEquals(1000, preview.density); assertEquals(520, preview.targetDensity)
    }
}

class ChalkStrokesTest {
    private val photoEl = ImageEl(1, "a.jpg", 200f, 150f, 400f, 300f)

    @Test
    fun placeFollowsCentreSizeRotationMirrorAndCrop() {
        assertArrayEquals(floatArrayOf(200f, 150f), ChalkStrokes.place(0.5f, 0.5f, photoEl, 4f / 3f), 1e-3f)
        assertArrayEquals(floatArrayOf(0f, 0f), ChalkStrokes.place(0f, 0f, photoEl, 4f / 3f), 1e-3f)
        val turned = photoEl.copy(rot = (PI / 2).toFloat())
        // obrót o 90° zgodnie z ruchem wskazówek: prawy brzeg zdjęcia idzie w dół
        assertArrayEquals(floatArrayOf(200f, 350f), ChalkStrokes.place(1f, 0.5f, turned, 4f / 3f), 1e-3f)
        assertArrayEquals(floatArrayOf(0f, 150f), ChalkStrokes.place(1f, 0.5f, photoEl.copy(mirror = true), 4f / 3f), 1e-3f)
        // wycinek prawej połowy pliku rozciągnięty na cały element; lewa połowa jest poza nim
        val half = photoEl.copy(crop = ImageCrop(0.5f, 0f, 1f, 1f))
        assertArrayEquals(floatArrayOf(0f, 150f), ChalkStrokes.place(0.5f, 0.5f, half, 4f / 3f), 1e-3f)
        assertNull(ChalkStrokes.place(0.25f, 0.5f, half, 4f / 3f))
    }

    @Test
    fun strokesLieOnTheChalkOfTheStraightenedAndCroppedPhoto() {
        val photo = Synthetic.photo()
        val res = BoardPipeline.run(photo)
        val strokes = ChalkStrokes.toStrokes(res.image, res.source!!, photoEl, 0x336699).filterIsInstance<Stroke>()
        assertTrue("kresek ${strokes.size}", strokes.isNotEmpty())
        val toBoard = Homography.of(Synthetic.QUAD, Quad.of(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))!!
        var len = 0.0
        for (s in strokes) for (i in 0 until s.pts.size / 3) {
            val b = toBoard.map(s.pts[3 * i].toDouble() - 0.5, s.pts[3 * i + 1].toDouble() - 0.5)
            assertTrue("punkt (${s.pts[3 * i]}, ${s.pts[3 * i + 1]}) → tablica (${b[0]}, ${b[1]})", abs(b[1] - 0.5) < 0.03 && b[0] in 0.15..0.85)
            if (i > 0) len += kotlin.math.hypot((s.pts[3 * i] - s.pts[3 * i - 3]).toDouble(), (s.pts[3 * i + 1] - s.pts[3 * i - 2]).toDouble())
        }
        // linia kredy ma w zdjęciu ok. 0,6 × 260 px długości; kreski pokrywają większość
        assertTrue("długość kresek $len", len > 100)
    }

    @Test
    fun strokesOutsideTheVisibleCropAreCut() {
        val photo = Synthetic.photo()
        val res = BoardPipeline.run(photo)
        // w zeszycie widać tylko lewą połowę zdjęcia: żaden punkt nie wychodzi poza element
        val left = ImageEl(2, "a.jpg", 100f, 150f, 200f, 300f, crop = ImageCrop(0f, 0f, 0.5f, 1f))
        val strokes = ChalkStrokes.toStrokes(res.image, res.source!!, left, 0).filterIsInstance<Stroke>()
        assertTrue(strokes.isNotEmpty())
        for (s in strokes) for (i in 0 until s.pts.size / 3) assertTrue("x ${s.pts[3 * i]}", s.pts[3 * i] <= 202f)
    }
}

/** 2b-B2: blada kreda dociągana do wyraźnej (histereza) i usuwanie listew/ram grubszych niż kreda. */
class FaintAndFramesTest {
    private val ink = Raster.argb(255, 240, 240, 240)

    @Test
    fun thickBarGoesThinChalkStays() {
        val r = Raster(200, 100)
        for (y in 40..60) for (x in 0 until 200) r[x, y] = ink   // listwa 21 px
        for (y in 20..22) for (x in 20..180) r[x, y] = ink        // kreda 3 px
        for (y in 0..39) for (x in 99..101) r[x, y] = ink         // kreda dotykająca listwy
        val out = Chalk.dropThick(r, 4f)
        assertEquals(0, Raster.alpha(out[100, 50]))
        assertEquals(0, Raster.alpha(out[5, 45]))
        assertEquals(255, Raster.alpha(out[100, 21]))
        assertEquals(255, Raster.alpha(out[100, 10]))  // dotykająca kreska zostaje poza otoczką pasa
        assertTrue(Chalk.thickLimit(1600) > 6f && Chalk.thickLimit(300) == 4f)
    }

    @Test
    fun compactBoldBlobIsALetterNotAFrame() {
        // pogrubiona, rozmyta litera: gruba, ale zwarta (12 px na 200 px obrazu = 6 % < 8 %)
        val r = Raster(200, 100)
        for (y in 40..51) for (x in 90..101) r[x, y] = ink
        val out = Chalk.dropThick(r, 4f)
        assertEquals(255, Raster.alpha(out[95, 45]))
    }

    @Test
    fun faintPartOfALetterStaysIsolatedSmudgeGoes() {
        // po wyrównaniu oświetlenia: czarne tło, kreska wyraźna przechodzi w bladą, osobno blada smuga
        val r = Raster(200, 40, IntArray(8000) { Raster.rgbF(0.02f, 0.02f, 0.02f) })
        for (y in 18..21) {
            for (x in 10..49) r[x, y] = Raster.rgbF(0.9f, 0.9f, 0.9f)
            for (x in 50..90) r[x, y] = Raster.rgbF(0.33f, 0.33f, 0.33f)
            for (x in 150..170) r[x, y] = Raster.rgbF(0.33f, 0.33f, 0.33f)
        }
        val plain = Chalk.toAlpha(r, black = true, cut = 0.45f, solid = 0.85f)
        assertEquals(0, Raster.alpha(plain[70, 19]))
        val linked = Chalk.toAlpha(r, black = true, cut = 0.45f, solid = 0.85f, low = 0.45f * Chalk.FAINT_FRACTION)
        assertTrue("blada część litery ${Raster.alpha(linked[70, 19])}", Raster.alpha(linked[70, 19]) > 20)
        assertEquals(0, Raster.alpha(linked[160, 19]))
        assertEquals(0, Raster.alpha(linked[120, 5]))
    }

    @Test
    fun specksGoDotsStay() {
        val r = Raster(60, 30)
        r[5, 5] = ink; r[6, 5] = ink                       // drobinka 2 px
        for (y in 10..12) for (x in 30..32) r[x, y] = ink  // kropka 3×3 = 9 px
        r[5, 6] = Raster.argb(80, 240, 240, 240)          // półprzezroczysta otoczka drobinki
        val out = Chalk.despeckle(r, 6)
        assertEquals(0, Raster.alpha(out[5, 5]))
        assertEquals(0, Raster.alpha(out[5, 6]))
        assertEquals(255, Raster.alpha(out[31, 11]))
        assertEquals(6, Chalk.speckArea(1600)); assertEquals(3, Chalk.speckArea(400))
    }

    @Test
    fun bothStepsAreOptional() {
        val photo = Synthetic.photo()
        val off = BoardPipeline.run(photo, BoardOptions(faint = false, frames = false))
        val on = BoardPipeline.run(photo)
        assertEquals(off.image.w, on.image.w)
        // na syntetycznej tablicy nie ma listew: kreda (pozioma linia) zostaje w obu
        assertTrue(on.image.px.count { Raster.alpha(it) > 200 } > 200)
    }
}
