// Algorytmy kredy na podstawie programu kreda — autor i licencja do potwierdzenia przed publikacją (app/src/kreda/NOTICE.md).

package com.mdev.mnotes.core.image

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max

/**
 * Pomiary kredy na prawdziwych zdjęciach (2b-B2) do `app/src/kreda/KREDA-POMIARY.md`: dla każdego zdjęcia z `KREDA_DIR` udział
 * kredy, liczba kresek, średnia długość kreski, odsetek drobnych fragmentów (< 10 px), próg i czas. To nie jest test
 * z progami (te są w [RealPhotoTest]), tylko tabela do porównania przed/po; wypisuje ją na wyjście testu.
 * `KREDA_DIR=/ścieżka/kreda/blackboard ./gradlew :app:testGmsDebugUnitTest --tests '*KredaPomiaryTest*'`;
 * wynik w `app/build/test-results/testGmsDebugUnitTest/TEST-…KredaPomiaryTest.xml` (system-out).
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
        println("| zdjęcie | wynik px | próg | kreda % | kresek | śr. długość px | fragmenty < 10 px % | czas ms | maska bez ściany −% |")
        println("|---|---|---|---|---|---|---|---|---|")
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
            System.getenv("KREDA_OUT")?.let { out -> dump(File(out, f.nameWithoutExtension + ".png"), photo, res.image, res.quad) }
            // Ile maski tablicy odcięło przycięcie do zakresu pisma (BoardGeometry.clipToContent); 0, gdy nie zadziałało.
            val small = photo.fit(BoardGeometry.WORK)
            val clipped = BoardGeometry.boardMask(small, true)?.let { m ->
                BoardGeometry.clipToContent(small, m, true)?.let { c -> 100.0 * (m.count { it } - c.count { it }) / m.count { it } }
            } ?: 0.0
            // Mapa śladów pisma na podglądzie (biało) w masce tablicy (granat) i granice przycięcia (czerwono).
            System.getenv("KREDA_OUT")?.let { out ->
                val m = BoardGeometry.boardMask(small, true) ?: return@let
                val ink = BoardGeometry.inkMap(small, m, true)
                val edges = BoardGeometry.clipEdges(small, m, true)
                val img = java.awt.image.BufferedImage(small.w, small.h, java.awt.image.BufferedImage.TYPE_INT_RGB)
                for (i in m.indices) {
                    val x = i % small.w; val y = i / small.w
                    val edge = edges != null && (x == edges[0] || x == edges[2] || y == edges[1] || y == edges[3])
                    img.setRGB(x, y, if (edge) 0xd04040 else if (ink?.ink?.get(i) == true) 0xffffff else if (m[i]) 0x303060 else 0)
                }
                ImageIO.write(img, "png", File(out, f.nameWithoutExtension + "-slady.png"))
            }
            println("| ${f.name} | ${res.image.w}×${res.image.h} | ${"%.2f".format(res.cut)} | ${"%.1f".format(share * 100)} | ${lines.size} | ${"%.1f".format(mean)} | ${"%.0f".format(frag * 100)} | $ms | ${"%.0f".format(clipped)} |")
            tShare += share; tLines += lines.size; tLen += mean; tFrag += frag; tMs += ms
        }
        val n = max(1, photos.size)
        println("| **średnio (${photos.size})** | | | ${"%.1f".format(tShare / n * 100)} | ${tLines / n} | ${"%.1f".format(tLen / n)} | ${"%.0f".format(tFrag / n * 100)} | ${tMs / n} |")
    }

    /**
     * Próg lokalny (2c-B9): to samo źródło ([BoardPipeline.prepare]) wykończone bez i z [BoardOptions.localCut]. Kryterium
     * z planu: fragmenty < 10 px nie rosną o więcej niż 1 p.p. na żadnym zdjęciu; ostatni wiersz podaje najgorszy przyrost.
     * Z `KREDA_OUT` zapisuje `*-lokalny.png`: krycie bez progu lokalnego, z nim i (czerwono) to, co doszło.
     */
    @Test
    fun measureLocalCut() {
        val dir = System.getenv("KREDA_DIR")?.let(::File)?.takeIf { it.isDirectory }
        assumeTrue("brak KREDA_DIR", dir != null)
        val photos = dir!!.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }.orEmpty().sortedBy { it.name }
        println("| zdjęcie | próg | niżej % | kreda % bez → z | widoczne % bez → z | kresek bez → z | śr. długość px bez → z | fragmenty % bez → z | Δ fragm. p.p. | czas ms bez → z |")
        println("|---|---|---|---|---|---|---|---|---|---|")
        var worst = -100.0; var worstName = ""; var sumLowered = 0.0; var anyOff = 0.0; var anyOn = 0.0
        val sum = DoubleArray(9)
        for (f in photos) {
            val src = BoardPipeline.prepare(load(f, SIDE), BoardOptions(maxSide = SIDE))
            val visible = max(1, src.image.px.count { Raster.alpha(it) > 127 })
            val lowered = Chalk.localCut(src.image, src.black, src.autoCut).count { it < src.autoCut - 1e-4f } * 100.0 / visible
            sumLowered += lowered
            val row = listOf(false, true).map { local ->
                val t0 = System.nanoTime()
                val res = BoardPipeline.finish(src, BoardOptions(maxSide = SIDE, localCut = local))
                val ms = (System.nanoTime() - t0) / 1_000_000
                val lines = ChalkVector.vectorize(res.image)
                val lens = lines.map { it.length() }
                doubleArrayOf(
                    res.image.px.count { Raster.alpha(it) > 128 } * 100.0 / visible, lines.size.toDouble(),
                    if (lens.isEmpty()) 0.0 else lens.average(), if (lens.isEmpty()) 0.0 else lens.count { it < 10f } * 100.0 / lens.size,
                    ms.toDouble(), res.image.px.count { Raster.alpha(it) > 0 } * 100.0 / visible,
                ) to res.image
            }
            val (a, b) = row[0].first to row[1].first
            val d = b[3] - a[3]
            if (d > worst) { worst = d; worstName = f.name }
            for (k in 0 until 4) { sum[k] += a[k]; sum[k + 4] += b[k] }
            sum[8] += d
            anyOff += a[5]; anyOn += b[5]
            println("| ${f.name} | ${"%.2f".format(src.autoCut)} | ${"%.0f".format(lowered)} | ${"%.1f → %.1f".format(a[0], b[0])} | ${"%.1f → %.1f".format(a[5], b[5])} | ${a[1].toInt()} → ${b[1].toInt()} | " +
                "${"%.1f → %.1f".format(a[2], b[2])} | ${"%.0f → %.0f".format(a[3], b[3])} | ${"%+.1f".format(d)} | ${a[4].toInt()} → ${b[4].toInt()} |")
            System.getenv("KREDA_OUT")?.let { out -> dumpLocal(File(out, f.nameWithoutExtension + "-lokalny.png"), row[0].second, row[1].second) }
        }
        val n = max(1, photos.size)
        println("| **średnio (${photos.size})** | | ${"%.0f".format(sumLowered / n)} | ${"%.1f → %.1f".format(sum[0] / n, sum[4] / n)} | ${"%.1f → %.1f".format(anyOff / n, anyOn / n)} | ${"%.0f → %.0f".format(sum[1] / n, sum[5] / n)} | " +
            "${"%.1f → %.1f".format(sum[2] / n, sum[6] / n)} | ${"%.1f → %.1f".format(sum[3] / n, sum[7] / n)} | ${"%+.1f".format(sum[8] / n)} | |")
        println("Najgorszy przyrost fragmentów: ${"%+.1f".format(worst)} p.p. ($worstName); próg lokalny niższy od globalnego średnio na ${"%.0f".format(sumLowered / n)} % tablicy (K = ${Chalk.LOCAL_K}, dół ${Chalk.LOCAL_FLOOR})")
    }

    private fun dumpLocal(to: File, off: Raster, on: Raster) {
        val img = java.awt.image.BufferedImage(off.w * 2, off.h, java.awt.image.BufferedImage.TYPE_INT_RGB)
        for (i in off.px.indices) {
            val x = i % off.w; val y = i / off.w
            val a = Raster.alpha(off.px[i]); val b = Raster.alpha(on.px[i])
            img.setRGB(x, y, (a shl 16) or (a shl 8) or a)
            img.setRGB(off.w + x, y, if (b > 128 && a <= 128) 0xff3030 else (b shl 16) or (b shl 8) or b)
        }
        to.parentFile.mkdirs()
        ImageIO.write(img, "png", to)
    }

    /**
     * Podgląd do oczu (tylko gdy `KREDA_OUT`): z lewej zdjęcie z narożnikami tablicy (czerwone) i przyciemnionym tym,
     * co [BoardGeometry.boardMask] uznał za „nie tablicę”; z prawej krycie kredy biało na czarnym.
     */
    private fun dump(to: File, photo: Raster, chalk: Raster, quad: Quad?) {
        val h = max(photo.h, chalk.h)
        val img = java.awt.image.BufferedImage(photo.w + chalk.w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val small = photo.fit(BoardGeometry.WORK)
        val mask = BoardGeometry.boardMask(small, true)
        val shown = IntArray(photo.px.size) { i ->
            val c = photo.px[i]
            val inside = mask == null || mask[minOf(small.h - 1, (i / photo.w) * small.h / photo.h) * small.w + minOf(small.w - 1, (i % photo.w) * small.w / photo.w)]
            if (inside) c else ((c shr 2) and 0x3f3f3f)
        }
        img.setRGB(0, 0, photo.w, photo.h, shown, 0, photo.w)
        quad?.let { q ->
            val g = img.createGraphics()
            g.color = java.awt.Color.RED
            g.stroke = java.awt.BasicStroke(5f)
            for (i in 0 until 4) g.drawLine(q.x(i).toInt(), q.y(i).toInt(), q.x((i + 1) % 4).toInt(), q.y((i + 1) % 4).toInt())
            g.dispose()
        }
        val a = IntArray(chalk.px.size) { val v = Raster.alpha(chalk.px[it]); (v shl 16) or (v shl 8) or v }
        img.setRGB(photo.w, 0, chalk.w, chalk.h, a, 0, chalk.w)
        to.parentFile.mkdirs()
        ImageIO.write(img, "png", to)
    }

    private companion object {
        const val SIDE = 1600
    }
}
