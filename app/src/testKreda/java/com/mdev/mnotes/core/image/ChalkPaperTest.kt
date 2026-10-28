package com.mdev.mnotes.core.image

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.util.Random
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Kreda na stronie docelowej i szara tablica (2c-B4). Kontrast liczony tu niezależnie od [ChalkInk] (WCAG 2 wprost ze
 * wzoru), żeby test nie sprawdzał kodu nim samym.
 */
class ChalkPaperTest {
    private val white = 0xFFFFFFFF.toInt()
    private val cream = 0xFFFAF6EC.toInt()
    private val darkPaper = 0xFF1E2228.toInt()

    private fun lin(c: Int): Double = (c / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    private fun lum(c: Int): Double = 0.2126 * lin(c shr 16 and 255) + 0.7152 * lin(c shr 8 and 255) + 0.0722 * lin(c and 255)
    private fun contrast(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (max(x, y) + 0.05) / (min(x, y) + 0.05) }

    /** Najgorszy kontrast kryjących pikseli kredy (alfa ≥ 90 %) do [paper]; NaN, gdy kredy nie ma. */
    private fun worst(img: Raster, paper: Int): Double {
        var w = Double.NaN
        for (c in img.px) if (Raster.alpha(c) >= 230) { val k = contrast(c, paper); if (w.isNaN() || k < w) w = k }
        return w
    }

    @Test
    fun whiteChalkOnLightPaperGetsReadableContrast() {
        for (paper in listOf(white, cream)) for (tint in listOf(null, 0xFFF5F5F0.toInt())) {
            val out = BoardPipeline.run(Synthetic.photo(), BoardOptions(tint = tint, paper = paper)).image
            val k = worst(out, paper)
            assertTrue("papier ${Integer.toHexString(paper)}, kolor ${tint?.let(Integer::toHexString)}: kontrast $k", k >= 3.0)
        }
    }

    @Test
    fun graphiteOnDarkPaperGetsReadableContrast() {
        val out = BoardPipeline.run(Synthetic.photo(), BoardOptions(tint = 0xFF37474F.toInt(), paper = darkPaper)).image
        val k = worst(out, darkPaper)
        assertTrue("kontrast $k", k >= 3.0)
    }

    @Test
    fun withoutPaperTheChalkKeepsItsColour() {
        val out = BoardPipeline.run(Synthetic.photo(), BoardOptions()).image
        assertTrue(worst(out, white) < 1.5)  // biała kreda zostaje biała, gdy nie wiadomo, gdzie trafi
    }

    /**
     * Szara tablica jak na okładce „Do poprawy w mNotes pt2” (tło 0,45–0,55): kreda jaśniejsza od tła. Ściana jasna,
     * lekki gradient i szum jak w [Synthetic.photo].
     */
    private fun grayBoard(level: Float, seed: Long = 2): Raster {
        val w = 400; val h = 300
        val rnd = Random(seed)
        val r = Raster(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val n = (rnd.nextGaussian() * 0.006).toFloat()
            val inBoard = Chalk.inside(Synthetic.QUAD.pts, x + 0.5f, y + 0.5f)
            val v = if (inBoard) level + 0.04f * (x.toFloat() / w - 0.5f) + n else 0.84f + n
            r[x, y] = Raster.rgbF(v, v + 0.01f, v)
        }
        val back = Homography.of(Quad.of(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f), Synthetic.QUAD)!!
        for (row in listOf(0.35, 0.65)) for (i in 0..600) {
            val t = 0.2 + 0.6 * i / 600.0
            for (dy in -2..2) {
                val p = back.map(t, row + dy * 0.004)
                r[p[0].toInt().coerceIn(0, w - 1), p[1].toInt().coerceIn(0, h - 1)] = Raster.rgbF(0.95f, 0.95f, 0.92f)
            }
        }
        return r
    }

    /** Kartka sfotografowana w słabym świetle: cała szara (0,45–0,55) z ciemnym pismem; to nie jest tablica. */
    private fun dimPaper(level: Float): Raster = Raster.filled(400, 300, Raster.rgbF(level, level, level * 0.97f)).also {
        for (y in listOf(100, 150, 200)) for (x in 60 until 340) { it[x, y] = Raster.rgbF(0.12f, 0.12f, 0.14f); it[x, y + 1] = Raster.rgbF(0.12f, 0.12f, 0.14f) }
    }

    @Test
    fun grayBoardIsRecognisedAndCleaned() {
        for (level in listOf(0.45f, 0.5f, 0.55f)) {
            val photo = grayBoard(level)
            assertTrue("tło $level", BoardGeometry.looksLikeDarkBoard(photo))
            val res = BoardPipeline.run(photo)
            assertTrue("tło $level: kreda na ciemnym", res.black)
            val solid = res.image.px.count { Raster.alpha(it) >= 128 }
            // dwie kreski ok. 0,6 × 261 px × 5 px ≈ 1600 px; tło tablicy (≈ 43 000 px) przezroczyste
            assertTrue("tło $level: kryjących $solid", solid in 800..4000)
        }
    }

    /** Kreda nałożona na papier (jak na stronie): do podglądu PNG przed/po. */
    private fun onPaper(img: Raster, paper: Int): BufferedImage {
        val out = BufferedImage(img.w, img.h, BufferedImage.TYPE_INT_RGB)
        for (i in img.px.indices) {
            val c = img.px[i]; val a = Raster.alpha(c) / 255f
            fun ch(sh: Int) = ((c shr sh and 255) * a + (paper shr sh and 255) * (1 - a) + 0.5f).toInt()
            out.setRGB(i % img.w, i / img.w, (ch(16) shl 16) or (ch(8) shl 8) or ch(0))
        }
        return out
    }

    /**
     * Okładka „Do poprawy w mNotes pt2” (szara tablica ze wzorem Φ), wyciągnięta z PDF poza repozytorium; ścieżki w
     * `KREDA_EXTRA` (rozdzielone `:`), bez niej test jest pomijany. Podglądy przed/po na białej kartce w
     * `app/build/kreda-papier/`.
     */
    @Test
    fun pdfCoverIsABoardAndReadableOnWhitePaper() {
        val files = System.getenv("KREDA_EXTRA")?.split(':')?.map(::File)?.filter { it.isFile }.orEmpty()
        assumeTrue("brak KREDA_EXTRA (okładka PDF)", files.isNotEmpty())
        val out = File("build/kreda-papier").apply { mkdirs() }
        for (f in files) {
            val big = ImageIO.read(f)
            val photo = Raster(big.width, big.height).also { big.getRGB(0, 0, big.width, big.height, it.px, 0, big.width) }.fit(1600)
            val board = BoardGeometry.looksLikeDarkBoard(photo.fit(320))
            val before = BoardPipeline.run(photo, BoardOptions(maxSide = 1600)).image
            val after = BoardPipeline.run(photo, BoardOptions(maxSide = 1600, paper = white)).image
            val share = after.px.count { Raster.alpha(it) > 128 } * 100.0 / after.px.size
            println("${f.name}: tablica=$board, kreda ${"%.1f".format(share)} %, kontrast na białym ${"%.2f".format(worst(before, white))} → ${"%.2f".format(worst(after, white))}")
            ImageIO.write(onPaper(before, white), "png", File(out, f.nameWithoutExtension + "-przed.png"))
            ImageIO.write(onPaper(after, white), "png", File(out, f.nameWithoutExtension + "-po.png"))
            assertTrue("${f.name}: nie rozpoznana jako tablica", board)
            assertTrue("${f.name}: kontrast ${worst(after, white)}", worst(after, white) >= 3.0)
        }
    }

    @Test
    fun dimPaperIsNotABoard() {
        for (level in listOf(0.45f, 0.5f, 0.55f)) assertFalse("kartka $level", BoardGeometry.looksLikeDarkBoard(dimPaper(level)))
        assertFalse(BoardGeometry.looksLikeDarkBoard(Synthetic.paper()))
    }
}
