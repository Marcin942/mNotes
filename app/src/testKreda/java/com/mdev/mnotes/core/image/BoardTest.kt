// Algorytmy kredy na podstawie programu kreda — autor i licencja do potwierdzenia przed publikacją (app/src/kreda/NOTICE.md).

package com.mdev.mnotes.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs

/** Syntetyczne „zdjęcia tablicy”: ciemny czworokąt w perspektywie na jasnej ścianie, gradient światła, szum, kreda. */
object Synthetic {
    val QUAD = Quad.of(70f, 50f, 330f, 70f, 320f, 230f, 60f, 215f)

    fun photo(w: Int = 400, h: Int = 300, seed: Long = 1, quad: Quad = QUAD, chalk: Boolean = true): Raster {
        val rnd = Random(seed)
        val r = Raster(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val light = 0.75f + 0.35f * x / w  // światło z prawej strony
            val n = (rnd.nextGaussian() * 0.006).toFloat()
            val inBoard = Chalk.inside(quad.pts, x + 0.5f, y + 0.5f)
            val v = if (inBoard) 0.16f * light + n else 0.72f * light + n
            val tint = if (inBoard) 0.02f else 0f
            r[x, y] = Raster.rgbF(v, v + tint, v)
        }
        if (chalk) {
            // kreda: pozioma linia w połowie tablicy (w układzie tablicy), narysowana przez odwzorowanie prostokąta
            val back = Homography.of(Quad.of(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f), quad)!!
            for (i in 0..600) {
                val t = 0.2 + 0.6 * i / 600.0
                for (dy in -2..2) {
                    val p = back.map(t, 0.5 + dy * 0.004)
                    val x = p[0].toInt(); val y = p[1].toInt()
                    if (x in 0 until w && y in 0 until h) r[x, y] = Raster.rgbF(0.93f, 0.93f, 0.9f)
                }
            }
        }
        return r
    }

    fun paper(w: Int = 300, h: Int = 200): Raster = Raster.filled(w, h, Raster.rgbF(0.95f, 0.95f, 0.93f)).also {
        for (x in 20 until 280) it[x, 100] = Raster.rgbF(0.1f, 0.1f, 0.1f)
    }
}

class HomographyTest {
    @Test
    fun mapsFourCornersExactly() {
        val src = Synthetic.QUAD
        val dst = Quad.of(0f, 0f, 259f, 0f, 259f, 179f, 0f, 179f)
        val h = Homography.of(src, dst)!!
        for (i in 0 until 4) {
            val p = h.map(src.x(i).toDouble(), src.y(i).toDouble())
            assertEquals(dst.x(i).toDouble(), p[0], 1e-6)
            assertEquals(dst.y(i).toDouble(), p[1], 1e-6)
        }
    }

    @Test
    fun identityAndDegenerate() {
        val q = Quad.full(100, 50)
        val p = Homography.of(q, q)!!.map(37.0, 12.0)
        assertEquals(37.0, p[0], 1e-9)
        assertEquals(12.0, p[1], 1e-9)
        assertEquals(null, Homography.of(Quad.of(0f, 0f, 1f, 1f, 2f, 2f, 3f, 3f), q))
    }

    @Test
    fun quadOrderSizeAndConvexity() {
        val q = Synthetic.QUAD
        assertTrue(q.isConvex())
        assertFalse(Quad.of(0f, 0f, 10f, 10f, 10f, 0f, 0f, 10f).isConvex())
        val (w, h) = q.rectSize()
        assertEquals(261, w)
        assertEquals(165, h)
    }
}

class BoardDetectTest {
    @Test
    fun darkBoardPhotoIsRecognised() {
        assertTrue(BoardGeometry.looksLikeDarkBoard(Synthetic.photo()))
        assertFalse(BoardGeometry.looksLikeDarkBoard(Synthetic.paper()))
    }

    @Test
    fun cornersAreGuessedNearTheTrueOnes() {
        val q = BoardGeometry.guessQuad(Synthetic.photo())
        for (i in 0 until 4) {
            assertTrue("narożnik $i: x ${q.x(i)} zamiast ${Synthetic.QUAD.x(i)}", abs(q.x(i) - Synthetic.QUAD.x(i)) < 10f)
            assertTrue("narożnik $i: y ${q.y(i)} zamiast ${Synthetic.QUAD.y(i)}", abs(q.y(i) - Synthetic.QUAD.y(i)) < 10f)
        }
    }

    /**
     * Tablica (60..330 × 50..230) z „pismem” (krótkie kreski) i ściana tego samego koloru pod nią aż do lewego i dolnego
     * brzegu zdjęcia, jak na 20260525_*; z [wallWriting] także pismo na ścianie.
     */
    private fun boardWithWall(wallWriting: Boolean): Raster {
        val w = 400; val h = 300
        val rnd = java.util.Random(7)
        val r = Raster(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val dark = (x in 60..330 && y in 50..230) || (x <= 330 && y > 230)
            val v = (if (dark) 0.18f else 0.75f) + (rnd.nextGaussian() * 0.006).toFloat()
            r[x, y] = Raster.rgbF(v, v, v)
        }
        fun letters(x0: Int, x1: Int, y0: Int, y1: Int, n: Int) = repeat(n) {
            val cx = x0 + rnd.nextInt(x1 - x0); val cy = y0 + rnd.nextInt(y1 - y0)
            val a = rnd.nextDouble() * Math.PI
            for (t in -4..4) {
                val x = (cx + t * kotlin.math.cos(a)).toInt(); val y = (cy + t * kotlin.math.sin(a)).toInt()
                if (x in 0 until w && y in 0 until h) r[x, y] = Raster.rgbF(0.92f, 0.92f, 0.9f)
            }
        }
        letters(80, 310, 70, 210, 160)
        if (wallWriting) letters(80, 310, 245, 290, 80)
        return r
    }

    @Test
    fun wallOfTheBoardColourIsCutOffWhereTheMaskRunsOutOfTheFrame() {
        val q = BoardGeometry.guessQuad(boardWithWall(wallWriting = false))
        for (i in 2..3) assertTrue("narożnik $i: y ${q.y(i)}, a tablica kończy się na 230", q.y(i) < 245f)
        for (i in listOf(0, 3)) assertTrue("narożnik $i: x ${q.x(i)}, a tablica zaczyna się na 60", q.x(i) > 45f)
    }

    @Test
    fun writingBeyondTheCutKeepsTheSide() {
        // Bezpiecznik: pod tablicą też jest pismo (≥ 3 % śladów), więc dół zostaje, nawet jeśli to „ściana”.
        val q = BoardGeometry.guessQuad(boardWithWall(wallWriting = true))
        assertTrue("dół ${q.y(2)}", q.y(2) > 280f)
    }

    @Test
    fun boardInsideTheFrameIsNotClipped() {
        val small = Synthetic.photo().fit(BoardGeometry.WORK)
        val mask = BoardGeometry.boardMask(small, true)!!
        assertEquals(null, BoardGeometry.clipEdges(small, mask, true))
    }

    @Test
    fun noBoardFallsBackToWholeImage() {
        val q = BoardGeometry.guessQuad(Raster.filled(200, 100, Raster.rgbF(0.5f, 0.2f, 0.9f)))
        assertEquals(0f, q.x(0)); assertEquals(199f, q.x(1)); assertEquals(99f, q.y(2))
    }
}

class ChalkTest {
    @Test
    fun warpStraightensTheBoard() {
        val flat = BoardGeometry.warp(Synthetic.photo(), Synthetic.QUAD)
        assertEquals(261, flat.w)
        // linia kredy leży w połowie wysokości wyprostowanej tablicy, od 20% do 80% szerokości
        val mid = flat.h / 2
        assertTrue(Raster.luma(flat[flat.w / 2, mid]) > 0.7f)
        assertTrue(Raster.luma(flat[flat.w / 2, mid / 2]) < 0.35f)
        assertTrue(Raster.luma(flat[flat.w / 10, mid]) < 0.35f)
    }

    @Test
    fun flattenRemovesTheLightingGradient() {
        val board = BoardGeometry.warp(Synthetic.photo(chalk = false), Synthetic.QUAD)
        fun mean(r: Raster, x0: Int): Float {
            var s = 0f
            for (y in r.h / 2 - 10 until r.h / 2 + 10) for (x in x0 until x0 + 20) s += Raster.maxRgb(r[x, y])
            return s / 400f
        }
        val before = mean(board, board.w - 40) - mean(board, 20)
        val flat = Chalk.flatten(board, black = true)
        val left = mean(flat, 20); val right = mean(flat, flat.w - 40)
        assertTrue("przed wyrównaniem różnica $before", before > 0.02f)
        assertTrue("tło po lewej $left, po prawej $right", left < 0.25f && right < 0.25f && abs(left - right) < 0.05f)
    }

    @Test
    fun autoCutFollowsTheNoise() {
        val rnd = Random(3)
        val quiet = Raster(200, 200, IntArray(40000) { val v = 0.02f + (rnd.nextGaussian() * 0.01).toFloat(); Raster.rgbF(v, v, v) })
        val noisy = Raster(200, 200, IntArray(40000) { val v = 0.1f + (rnd.nextGaussian() * 0.06).toFloat(); Raster.rgbF(v, v, v) })
        assertEquals(Chalk.AUTO_CUT_MIN, Chalk.autoCut(quiet, true), 1e-6f)
        val c = Chalk.autoCut(noisy, true)
        assertTrue("próg $c", c in 0.33f..0.43f)
    }

    @Test
    fun colourToAlphaKeepsChalkAndDropsBoard() {
        val img = Raster(3, 1, intArrayOf(Raster.rgbF(0.05f, 0.05f, 0.05f), Raster.rgbF(0.9f, 0.9f, 0.9f), Raster.rgbF(0.5f, 0.25f, 0.0f)))
        val out = Chalk.toAlpha(img, black = true, cut = 0.1f, solid = 0.85f)
        assertEquals(0, Raster.alpha(out[0, 0]))
        assertEquals(255, Raster.alpha(out[1, 0]))
        val a = Raster.alpha(out[2, 0])
        assertTrue("krycie $a", a in 100..140)
        // kolor odtworzony przez dzielenie przez jasność: pomarańcz zostaje pomarańczem
        assertEquals(255, Raster.red(out[2, 0]))
        assertEquals(128.0, Raster.green(out[2, 0]).toDouble(), 1.0)
        val tinted = Chalk.toAlpha(img, black = true, cut = 0.1f, solid = 0.85f, tint = 0xFF336699.toInt())
        assertEquals(0x336699, tinted[1, 0] and 0xFFFFFF)
    }

    @Test
    fun whiteBoardWorksTheOtherWayRound() {
        val img = Raster(2, 1, intArrayOf(Raster.rgbF(0.97f, 0.97f, 0.97f), Raster.rgbF(0.1f, 0.1f, 0.6f)))
        val out = Chalk.toAlpha(img, black = false, cut = 0.1f, solid = 0.85f)
        assertEquals(0, Raster.alpha(out[0, 0]))
        assertEquals(255, Raster.alpha(out[1, 0]))
    }

    @Test
    fun polygonEraserClearsOnlyInside() {
        val img = Raster.filled(20, 20, Raster.rgbF(1f, 1f, 1f))
        val out = Chalk.erase(img, listOf(floatArrayOf(2f, 2f, 10f, 2f, 10f, 10f, 2f, 10f)))
        assertEquals(0, out[5, 5])
        assertEquals(255, Raster.alpha(out[15, 15]))
        assertEquals(255, Raster.alpha(out[1, 1]))
    }

    @Test
    fun sharpenIncreasesEdgeContrast() {
        val img = Raster(40, 1, IntArray(40) { if (it < 20) Raster.rgbF(0.3f, 0.3f, 0.3f) else Raster.rgbF(0.6f, 0.6f, 0.6f) })
        val s = Chalk.sharpen(Raster(40, 40, IntArray(1600) { img.px[it % 40] }), amount = 1f)
        assertTrue(Raster.luma(s[18, 20]) < 0.3f)
        assertTrue(Raster.luma(s[21, 20]) > 0.6f)
    }
}

class BoardPipelineTest {
    @Test
    fun wholePipelineGivesChalkOnTransparency() {
        val res = BoardPipeline.run(Synthetic.photo())
        assertTrue(res.black)
        assertNotNull(res.quad)
        val img = res.image
        val transparent = img.px.count { Raster.alpha(it) < 10 }
        val opaque = img.px.count { Raster.alpha(it) > 200 }
        val border = (0 until img.h).sumOf { y -> (0 until img.w).count { x -> (x < 8 || y < 8 || x >= img.w - 8 || y >= img.h - 8) && Raster.alpha(img[x, y]) >= 10 } }
        val partial = img.px.count { Raster.alpha(it) in 10..200 }
        assertTrue("przezroczystych ${transparent * 100 / img.px.size}%, próg ${res.cut}, ${img.w}×${img.h}, na brzegu $border, częściowych $partial, quad ${res.quad?.pts?.toList()}",
            transparent > 0.85 * img.px.size)
        assertTrue("kryjących $opaque", opaque > 200)
        assertTrue(Raster.alpha(img[img.w / 2, img.h / 2]) > 150)
    }

    @Test
    fun everyStepCanBeSwitchedOff() {
        val photo = Synthetic.photo()
        val res = BoardPipeline.run(photo, BoardOptions(straighten = false, crop = false, flatten = false, sharpen = false, autoCut = false, cut = 0.5f, solid = 0.7f, faint = false, frames = false, specks = false))
        assertEquals(photo.w, res.image.w)
        assertEquals(null, res.quad)
        assertEquals(0.5f, res.cut)
        // bez prostowania i kadru ściana (jasna) zostaje kryjąca, a ciemna tablica znika
        assertTrue(Raster.alpha(res.image[395, 5]) > 200)
        assertEquals(0, Raster.alpha(res.image[200, 100]))
    }

    @Test
    fun manualCornersAndEraserAreUsed() {
        val photo = Synthetic.photo()
        val res = BoardPipeline.run(photo, BoardOptions(crop = false), quad = Synthetic.QUAD,
            erase = listOf(floatArrayOf(0f, 0f, 400f, 0f, 400f, 300f, 0f, 300f)))
        assertEquals(261, res.image.w)
        assertEquals(0, res.image.px.count { Raster.alpha(it) > 0 })
    }

    /** 3c-B1: wybrzuszenie maski (osoba przy tablicy) nie ciągnie narożnika; boki idą po prostych krawędziach tablicy. */
    @Test
    fun bulgeOnTheSideDoesNotPullTheCorner() {
        val w = 400; val h = 225
        val mask = BooleanArray(w * h) { i ->
            val x = i % w; val y = i / w
            val board = x in 60..330 && y in 10..180
            val person = x in 300..395 && y in 120..220 && (x - 345) * (x - 345) + (y - 170) * (y - 170) < 2500
            board || person
        }
        val rough = Quad.ofExtremes(IntArray(mask.count { it }).also { a -> var j = 0; for (i in mask.indices) if (mask[i]) a[j++] = i % w },
            IntArray(mask.count { it }).also { a -> var j = 0; for (i in mask.indices) if (mask[i]) a[j++] = i / w }, mask.count { it })
        assertTrue("zgrubny narożnik ciągnie osoba", rough.x(2) > 345f)
        val q = BoardGeometry.fitSides(mask, w, h, rough)!!
        val want = floatArrayOf(60f, 10f, 330f, 10f, 330f, 180f, 60f, 180f)
        for (k in 0 until 8) assertEquals("współrzędna $k", want[k], q.pts[k], 2f)
    }

    /** 3c-B1: listwa przez całą szerokość znika; falująca kreska i prosta kreska podziału przez 60 % zostają. */
    @Test
    fun fullWidthRailGoesHandDrawnLinesStay() {
        val ink = Raster.rgbF(0.95f, 0.95f, 0.95f)
        val r = Raster(800, 400)
        // Listwa: prosta, lekko pochylona (1 px na 400), 6 px, przez całą szerokość z jedną przerwą (ręka).
        for (x in 0 until 800) if (x !in 300..310) for (d in 0 until 6) r[x, 200 + x / 400 + d] = ink
        // Kreska kredą: falująca, 3 px, przez 75 % szerokości (nie mieści się w paśmie ±1).
        for (x in 100 until 700) { val y = 80 + (6 * Math.sin(x / 40.0)).toInt(); for (d in 0 until 3) r[x, y + d] = ink }
        for (x in 150 until 630) for (d in 0 until 3) r[x, 320 + d] = ink
        val out = Chalk.dropRails(r)
        assertEquals("listwa", 0, (0 until 800).count { Raster.alpha(out[it, 203]) > 128 })
        assertTrue("falująca kreska", (100 until 700).count { x -> (60..100).any { Raster.alpha(out[x, it]) > 128 } } > 590)
        assertTrue("kreska podziału", (150 until 630).count { Raster.alpha(out[it, 321]) > 128 } > 470)
    }

    /** 3c-B1: kawałek ramy wzdłuż brzegu wyniku znika, „=” ucięte brzegiem (w poprzek) zostaje. */
    @Test
    fun frameScrapAlongTheEdgeGoesCutLetterStays() {
        val ink = Raster.rgbF(0.95f, 0.95f, 0.95f)
        val r = Raster(800, 400)
        for (y in 50 until 200) for (x in 795 until 799) r[x, y] = ink
        for (x in 0 until 30) for (y in 100 until 103) r[x, y] = ink
        for (x in 0 until 30) for (y in 110 until 113) r[x, y] = ink
        val out = Chalk.dropEdgeScraps(r)
        assertEquals("rama", 0, (50 until 200).count { Raster.alpha(out[797, it]) > 128 })
        assertEquals("litera", 30, (0 until 30).count { Raster.alpha(out[it, 101]) > 128 })
    }
}

/** 3c-B3: przegląd kredy (K1–K5) — każdy test odtwarzał błąd przed poprawką. */
class KredaReviewTest {
    private val plain = BoardOptions(crop = false, flatten = false, sharpen = false)

    /** K1: ręczny próg poniżej 10 % z progiem lokalnym — pusty zakres coerceIn wywracał panel. */
    @Test fun localCutBelowAutoMinimumDoesNotThrow() {
        val m = Chalk.localCut(Raster.filled(8, 8, 0xFF202020.toInt()), black = true, cut = 0.05f)
        assertEquals(64, m.size)
        for (v in m) assertEquals(0.05f, v, 1e-6f)
    }

    /** K4: listwa z drobnymi przerwami (≤ RAIL_GAP) to jeden pas, nie kawałki za krótkie, żeby się liczyły. */
    @Test fun railWithSmallGapsIsBridged() {
        val ink = Raster.rgbF(0.95f, 0.95f, 0.95f)
        val r = Raster(1000, 200)
        for (x in 0 until 1000) if (x % 25 >= 2) for (y in 100..102) r[x, y] = ink
        val out = Chalk.dropRails(r)
        assertEquals(0, Raster.alpha(out[510, 101]))
    }

    /** K5: „tablicy nie znaleziono” (cały kadr) nie przepróbkowuje zdjęcia: ten sam rozmiar i te same piksele. */
    @Test fun fullFrameStraightenKeepsPixels() {
        val r = Raster(50, 40)
        for (y in 0 until 40) for (x in 0 until 50) r[x, y] = if ((x + y) % 2 == 0) 0xFF202020.toInt() else 0xFF282828.toInt()
        val s = BoardPipeline.prepare(r, plain)
        assertEquals(50, s.image.w); assertEquals(40, s.image.h)
        assertTrue(s.image.px.contentEquals(r.px))
        val p = s.toPhoto(49f, 39f)
        assertEquals(49.5f / 50, p[0], 1e-4f); assertEquals(39.5f / 40, p[1], 1e-4f)
    }

    /** K3: skrzyżowane ręczne narożniki nie zamieniają zdjęcia w prawie pusty obraz. */
    @Test fun crossedManualCornersFallBack() {
        val r = Raster.filled(100, 50, 0xFF202020.toInt())
        val s = BoardPipeline.prepare(r, plain, Quad.of(99f, 0f, 0f, 0f, 99f, 49f, 0f, 49f))
        val clear = s.image.px.count { Raster.alpha(it) == 0 }
        assertTrue("przezroczystych $clear z ${s.image.px.size}", clear < s.image.px.size / 100)
    }

    /** K2: przy kilku zdjęciach wynik większy niż maxSide nie jest pomniejszany drugi raz (kreski trafiały obok). */
    @Test fun mergedBoardLargerThanMaxSideMapsBack() {
        // Długa tablica po przekątnej: górny bok ~430 px na zdjęciu 400×400, czyli dłuższy niż maxSide.
        val q = Quad.of(64f, 27f, 394f, 304f, 336f, 373f, 6f, 96f)
        val photos = listOf(Synthetic.photo(400, 400, seed = 1, quad = q), Synthetic.photo(400, 400, seed = 2, quad = q))
        val s = BoardPipeline.prepareMany(2, plain.copy(maxSide = 400)) { photos[it] }!!
        val found = s.quad!!
        assertTrue("wyprostowana tablica dłuższa niż maxSide: ${found.rectSize()}", found.rectSize().first > 400)
        // Prawy górny piksel wyniku wraca dokładnie na prawy górny narożnik tablicy.
        val tr = s.toPhoto(s.image.w - 1f, 0f)
        assertEquals("x", (found.x(1) + 0.5f) / 400, tr[0], 1e-3f)
        assertEquals("y", (found.y(1) + 0.5f) / 400, tr[1], 1e-3f)
    }
}
