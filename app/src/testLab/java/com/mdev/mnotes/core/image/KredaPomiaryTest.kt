package com.mdev.mnotes.core.image

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max

/**
 * Pomiary kredy na prawdziwych zdjęciach (2b-B2) do `docs/KREDA-POMIARY.md`: dla każdego zdjęcia z `KREDA_DIR` udział
 * kredy, liczba kresek, średnia długość kreski, odsetek drobnych fragmentów (< 10 px), próg i czas. To nie jest test
 * z progami (te są w [RealPhotoTest]), tylko tabela do porównania przed/po; wypisuje ją na wyjście testu.
 * `KREDA_DIR=/ścieżka/kreda/blackboard ./gradlew :app:testLabDebugUnitTest --tests '*KredaPomiaryTest*'`;
 * wynik w `app/build/test-results/testLabDebugUnitTest/TEST-…KredaPomiaryTest.xml` (system-out).
 */
class KredaPomiaryTest {
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
    fun measure() {
        val dir = System.getenv("KREDA_DIR")?.let(::File)?.takeIf { it.isDirectory }
        assumeTrue("brak KREDA_DIR", dir != null)
        val photos = dir!!.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }.orEmpty().sortedBy { it.name }
        println("| zdjęcie | wynik px | próg | kreda % | kresek | śr. długość px | fragmenty < 10 px % | czas ms |")
        println("|---|---|---|---|---|---|---|---|")
        var tShare = 0.0; var tLines = 0; var tLen = 0.0; var tFrag = 0.0; var tMs = 0L
        for (f in photos) {
            val photo = load(f, SIDE)
            val t0 = System.nanoTime()
            val res = BoardPipeline.run(photo, BoardOptions(maxSide = SIDE))
            val lines = ChalkVector.vectorize(res.image)
            val ms = (System.nanoTime() - t0) / 1_000_000
            val src = res.source!!
            val visible = src.image.px.count { Raster.alpha(it) > 127 }
            val share = res.image.px.count { Raster.alpha(it) > 128 }.toDouble() / max(1, visible)
            val lens = lines.map { it.length() }
            val mean = if (lens.isEmpty()) 0.0 else lens.average()
            val frag = if (lens.isEmpty()) 0.0 else lens.count { it < 10f }.toDouble() / lens.size
            System.getenv("KREDA_OUT")?.let { out -> dump(File(out, f.nameWithoutExtension + ".png"), photo, res.image) }
            println("| ${f.name} | ${res.image.w}×${res.image.h} | ${"%.2f".format(res.cut)} | ${"%.1f".format(share * 100)} | ${lines.size} | ${"%.1f".format(mean)} | ${"%.0f".format(frag * 100)} | $ms |")
            tShare += share; tLines += lines.size; tLen += mean; tFrag += frag; tMs += ms
        }
        val n = max(1, photos.size)
        println("| **średnio (${photos.size})** | | | ${"%.1f".format(tShare / n * 100)} | ${tLines / n} | ${"%.1f".format(tLen / n)} | ${"%.0f".format(tFrag / n * 100)} | ${tMs / n} |")
    }

    /** Podgląd do oczu (tylko gdy `KREDA_OUT`): z lewej zdjęcie, z prawej krycie kredy biało na czarnym. */
    private fun dump(to: File, photo: Raster, chalk: Raster) {
        val h = max(photo.h, chalk.h)
        val img = java.awt.image.BufferedImage(photo.w + chalk.w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, photo.w, photo.h, photo.px, 0, photo.w)
        val a = IntArray(chalk.px.size) { val v = Raster.alpha(chalk.px[it]); (v shl 16) or (v shl 8) or v }
        img.setRGB(photo.w, 0, chalk.w, chalk.h, a, 0, chalk.w)
        to.parentFile.mkdirs()
        ImageIO.write(img, "png", to)
    }

    private companion object {
        const val SIDE = 1600
    }
}
