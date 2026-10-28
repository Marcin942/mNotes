package com.mdev.mnotes.core.image

import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.Stroke
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max

/**
 * Prawdziwe zdjęcia tablicy (B2-6): jedyny test, który sprawdza port na tym, do czego służy. Zdjęcia nie trafiają do
 * repozytorium; test czyta je z katalogu w zmiennej `KREDA_DIR` (np. `kreda/blackboard/`), a bez niej jest pomijany:
 * `KREDA_DIR=/ścieżka/do/kreda/blackboard ./gradlew :app:testLabDebugUnitTest --tests '*RealPhotoTest*'`.
 */
class RealPhotoTest {
    private val photos: List<File> by lazy {
        val dir = System.getenv("KREDA_DIR")?.let(::File)?.takeIf { it.isDirectory } ?: return@lazy emptyList()
        dir.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }.orEmpty().sortedBy { it.name }.take(MAX_PHOTOS)
    }

    /** Zdjęcie podpróbkowane już przy czytaniu (12 Mpx bez pełnej klatki w pamięci), potem dłuższy bok ≤ [longest]. */
    private fun load(f: File, longest: Int): Raster {
        ImageIO.createImageInputStream(f).use { input ->
            val reader = ImageIO.getImageReaders(input).next()
            reader.input = input
            val step = max(1, max(reader.getWidth(0), reader.getHeight(0)) / longest)
            val img = reader.read(0, reader.defaultReadParam.apply { setSourceSubsampling(step, step, 0, 0) })
            reader.dispose()
            val r = Raster(img.width, img.height)
            img.getRGB(0, 0, img.width, img.height, r.px, 0, img.width)
            return r.fit(longest)
        }
    }

    @Test
    fun chalkFromRealBoardPhotosIsSensible() {
        assumeTrue("brak KREDA_DIR (katalog ze zdjęciami tablicy)", photos.isNotEmpty())
        for (f in photos) {
            val photo = load(f, SIDE)
            val src = BoardPipeline.prepare(photo)
            val res = BoardPipeline.finish(src)
            val img = res.image
            // udział kredy wśród widocznych pikseli wyniku (poza kadrem tablicy przezroczysto)
            val visible = src.image.px.count { Raster.alpha(it) > 127 }
            val chalk = img.px.count { Raster.alpha(it) > 128 }
            val share = chalk.toDouble() / max(1, visible)
            // tło po wyrównaniu oświetlenia: jasność tam, gdzie wynik jest przezroczysty, ma być równa (mały rozrzut)
            val bg = (img.px.indices).filter { Raster.alpha(src.image.px[it]) > 127 && Raster.alpha(img.px[it]) == 0 }
                .map { if (res.black) Raster.maxRgb(src.image.px[it]) else 1f - Raster.minRgb(src.image.px[it]) }.sorted()
            val spread = bg[bg.size * 9 / 10] - bg[bg.size / 10]
            // kreski: ile punktów leży w czworokącie tablicy na zdjęciu
            val el = ImageEl(1, f.name, photo.w / 2f, photo.h / 2f, photo.w.toFloat(), photo.h.toFloat())
            val strokes = ChalkStrokes.toStrokes(img, src, el, 0).filterIsInstance<Stroke>()
            val quad = res.quad ?: Quad.full(photo.w, photo.h)
            var inside = 0; var all = 0
            for (s in strokes) for (i in 0 until s.pts.size / 3) {
                all++
                if (Chalk.inside(quad.inset(-4f).pts, s.pts[3 * i], s.pts[3 * i + 1])) inside++
            }
            val inBoard = inside.toDouble() / max(1, all)
            // kreski na kredzie: punkt kreski trafia (±1 px) w piksel zdjęcia jaśniejszy od mediany tablicy o 0,08
            val boardLuma = (0 until photo.h step 4).flatMap { y -> (0 until photo.w step 4).filter { x -> Chalk.inside(quad.pts, x + 0.5f, y + 0.5f) }.map { x -> Raster.luma(photo[x, y]) } }.sorted()
            val median = boardLuma[boardLuma.size / 2]
            var onChalk = 0
            for (s in strokes) for (i in 0 until s.pts.size / 3) {
                val x = s.pts[3 * i].toInt(); val y = s.pts[3 * i + 1].toInt()
                val hit = (-1..1).any { dy -> (-1..1).any { dx -> (x + dx) in 0 until photo.w && (y + dy) in 0 until photo.h && Raster.luma(photo[x + dx, y + dy]) > median + 0.08f } }
                if (hit) onChalk++
            }
            val chalkHit = onChalk.toDouble() / max(1, all)
            // dawne mapowanie (przed B2-6): wynik po prostowaniu i kadrze rozciągnięty liniowo na prostokąt zdjęcia
            val naiveSrc = BoardSource(src.image, src.black, null, src.flattened, img.w, img.h)
            val naive = ChalkStrokes.toStrokes(img, naiveSrc, el, 0).filterIsInstance<Stroke>()
            var naiveHit = 0; var naiveAll = 0
            for (s in naive) for (i in 0 until s.pts.size / 3) {
                naiveAll++
                val x = s.pts[3 * i].toInt(); val y = s.pts[3 * i + 1].toInt()
                if ((-1..1).any { dy -> (-1..1).any { dx -> (x + dx) in 0 until photo.w && (y + dy) in 0 until photo.h && Raster.luma(photo[x + dx, y + dy]) > median + 0.08f } }) naiveHit++
            }
            val naiveRate = naiveHit.toDouble() / max(1, naiveAll)
            println("  dawne mapowanie: na kredzie ${"%.1f".format(naiveRate * 100)}%")
            // pomiar 27.10: dawne 28–51 %, nowe 94–100 % na czterech pierwszych zdjęciach z kreda/blackboard
            assertTrue("${f.name}: dawne mapowanie ${naiveRate * 100}% wobec ${chalkHit * 100}%", naiveRate < chalkHit - 0.3)
            println("${f.name}: ${photo.w}×${photo.h}, wynik ${img.w}×${img.h}, czarna=${res.black}, próg ${"%.3f".format(res.cut)}, " +
                "kreda ${"%.1f".format(share * 100)}%, rozrzut tła ${"%.3f".format(spread)}, kresek ${strokes.size}, w tablicy ${"%.1f".format(inBoard * 100)}%, " +
                "na kredzie ${"%.1f".format(chalkHit * 100)}%")
            assertTrue("${f.name}: kreda ${share * 100}% widocznych pikseli", share in 0.01..0.25)
            assertTrue("${f.name}: rozrzut tła $spread", spread < MAX_SPREAD)
            assertTrue("${f.name}: kresek ${strokes.size}", strokes.size >= 10)
            assertTrue("${f.name}: w tablicy ${inBoard * 100}% punktów kresek", inBoard >= 0.9)
            assertTrue("${f.name}: na kredzie ${chalkHit * 100}% punktów kresek", chalkHit >= MIN_ON_CHALK)
        }
    }

    /** Reguła sprzed 2c-B4 (tylko tło < 0,42), do porównania z [BoardGeometry.looksLikeDarkBoard]. */
    private fun oldDarkBoard(src: Raster): Boolean {
        val s = src.fit(160)
        val bg = BoardFilters.open(s.plane { Raster.maxRgb(it) }, s.w, s.h, max(1, s.w / 60))
        var dark = 0; var total = 0
        for (y in s.h / 5 until s.h * 4 / 5) for (x in s.w / 5 until s.w * 4 / 5) {
            val c = s[x, y]
            total++
            if (bg[y * s.w + x] < 0.42f && Raster.maxRgb(c) - Raster.minRgb(c) < 0.3f) dark++
        }
        return total > 0 && dark >= 0.55 * total
    }

    @Test
    fun grayBoardRuleKeepsEveryRealBoardTheOldRuleFound() {
        val dir = System.getenv("KREDA_DIR")?.let(::File)?.takeIf { it.isDirectory }
        val all = dir?.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }.orEmpty().sortedBy { it.name }
        assumeTrue("brak KREDA_DIR (katalog ze zdjęciami tablicy)", all.isNotEmpty())
        var old = 0; var now = 0
        for (f in all) {
            val small = load(f, 320)  // jak podpowiedź po wstawieniu zdjęcia
            val o = oldDarkBoard(small); val n = BoardGeometry.looksLikeDarkBoard(small)
            if (o) old++
            if (n) now++
            assertTrue("${f.name}: stara reguła rozpoznała tablicę, nowa nie", !o || n)
        }
        println("tablica rozpoznana: dawniej $old, teraz $now z ${all.size} zdjęć")
    }

    private companion object {
        const val SIDE = 1000
        const val MAX_PHOTOS = 4
        const val MAX_SPREAD = 0.2f
        const val MIN_ON_CHALK = 0.7
    }
}
