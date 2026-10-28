package com.mdev.mnotes.core.image

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Pomiar 2b-B4 do `docs/KREDA-POMIARY.md`: kreski kredy jako łamana (dawny wynik, [ChalkVector.simplify] z ε 1,2 px)
 * i jako krzywe Béziera ([ChalkCurves]) na tych samych ścieżkach szkieletu, miary z [CurveMetrics]. Zdjęcia z
 * `KREDA_DIR` (katalog) i `KREDA_EXTRA` (pliki albo katalogi rozdzielone `:`, np. wzór z okładki PDF), w rozmiarze
 * aplikacji (2400 px). Podglądy PNG (4×: u góry linia środkowa z węzłami, u dołu kreska w pełnej grubości; z lewej
 * łamana, z prawej krzywa) idą do `KREDA_OUT` albo `build/kreda-krzywe/`.
 * `KREDA_DIR=… ./gradlew :app:testLabDebugUnitTest --tests '*KredaKrzyweTest*'`; tabela w system-out wyniku testu.
 */
class KredaKrzyweTest {

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

    private fun photos(): List<File> {
        fun images(f: File): List<File> =
            if (f.isDirectory) f.listFiles { x -> x.extension.lowercase() in setOf("jpg", "jpeg", "png") }.orEmpty().sortedBy { it.name }
            else if (f.isFile) listOf(f) else emptyList()
        val dir = System.getenv("KREDA_DIR")?.let { images(File(it)) }.orEmpty()
        val extra = System.getenv("KREDA_EXTRA")?.split(File.pathSeparator)?.filter { it.isNotBlank() }?.flatMap { images(File(it)) }.orEmpty()
        return dir + extra
    }

    private fun outDir(): File = File(System.getenv("KREDA_OUT")?.let { "$it/krzywe" } ?: "build/kreda-krzywe")

    /** Łamane i krzywe jednego obrazu kredy: miary obu i czasy (ms) liczenia samej geometrii. */
    private class Both(val lines: List<ChalkLine>, val polys: List<FloatArray>, val curves: List<FloatArray?>) {
        val old = CurveMetrics.Sum(); val new = CurveMetrics.Sum()
        var msOld = 0L; var msNew = 0L
    }

    private fun both(chalk: Raster): Both {
        val lines = ChalkVector.skeleton(chalk).filter { it.length() >= 6f }
        val tol = ChalkCurves.tolFor(chalk.w, chalk.h)
        var t = System.nanoTime()
        val polys = lines.map { ChalkVector.simplify(it.pts, 1.2f) }
        val msOld = (System.nanoTime() - t) / 1_000_000
        t = System.nanoTime()
        val curves = lines.map { ChalkCurves.fit(ChalkCurves.smooth(it.pts, it.width), tol) }
        val msNew = (System.nanoTime() - t) / 1_000_000
        val b = Both(lines, polys, curves)
        b.msOld = msOld; b.msNew = msNew
        for (i in lines.indices) {
            CurveMetrics.addPolyline(b.old, lines[i].pts, polys[i])
            curves[i]?.let { CurveMetrics.addCurve(b.new, lines[i].pts, it) }
        }
        return b
    }

    @Test
    fun measure() {
        val files = photos()
        assumeTrue("brak KREDA_DIR / KREDA_EXTRA", files.isNotEmpty())
        println("| zdjęcie | wynik px | kresek | długość px | węzły / 100 px łamana → krzywa | mniej × | zęby / 100 px łamana → krzywa | błąd px łamana → krzywa | maks. skręt na gładkim ° | ms łamana / krzywe |")
        println("|---|---|---|---|---|---|---|---|---|---|")
        val old = CurveMetrics.Sum(); val new = CurveMetrics.Sum()
        var lines = 0; var msOld = 0L; var msNew = 0L; var worstSmooth = 0.0
        val previews = setOf("okladka-wzor", "20260318_154119", "20260326_140000")
        for (f in files) {
            val res = BoardPipeline.run(load(f, SIDE))
            val b = both(res.image)
            val o = b.old; val n = b.new
            println("| ${f.name} | ${res.image.w}×${res.image.h} | ${b.lines.size} | ${"%.0f".format(o.length)} | " +
                "${"%.1f".format(o.nodesPer100)} → ${"%.1f".format(n.nodesPer100)} | ${"%.1f".format(o.nodesPer100 / max(1e-9, n.nodesPer100))} | " +
                "${"%.2f".format(o.teethPer100)} → ${"%.2f".format(n.teethPer100)} | ${"%.2f".format(o.meanErr)} → ${"%.2f".format(n.meanErr)} | " +
                "${"%.0f".format(n.maxSmooth)} | ${b.msOld} / ${b.msNew} |")
            for ((s, d) in listOf(old to o, new to n)) {
                s.length += d.length; s.nodes += d.nodes; s.teeth += d.teeth; s.err += d.err; s.errN += d.errN
            }
            lines += b.lines.size; msOld += b.msOld; msNew += b.msNew; worstSmooth = max(worstSmooth, n.maxSmooth)
            if (f.nameWithoutExtension in previews) render(File(outDir(), f.nameWithoutExtension + ".png"), res.image, b, window(res.image, b))
            // cały wzór z okładki PDF (przykład Marcina) bez powiększenia
            if (f.nameWithoutExtension == "okladka-wzor") render(File(outDir(), "okladka-wzor-calosc.png"), res.image, b, intArrayOf(0, 0, res.image.w, res.image.h), zoom = 1)
        }
        val k = files.size
        println("| **razem (${k})** | | ${lines / k} śr. | ${"%.0f".format(old.length / k)} śr. | ${"%.1f".format(old.nodesPer100)} → ${"%.1f".format(new.nodesPer100)} | " +
            "${"%.1f".format(old.nodesPer100 / new.nodesPer100)} | ${"%.2f".format(old.teethPer100)} → ${"%.2f".format(new.teethPer100)} | " +
            "${"%.2f".format(old.meanErr)} → ${"%.2f".format(new.meanErr)} | ${"%.0f".format(worstSmooth)} | ${msOld / k} / ${msNew / k} śr. |")
        // Plan 2b-B4 chciał ≥ 3× mniej węzłów; na zdjęciach to niemożliwe: średnia kreska ma ok. 43 px, więc same dwa
        // końce dają ok. 4,6 węzła na 100 px (pomiar 28.10: łamana 11,1, krzywa 7,4). Pilnujemy: mniej węzłów, mniej zębów,
        // błąd dopasowania w granicy tolerancji.
        assertTrue("węzły/100 px: łamana ${old.nodesPer100}, krzywa ${new.nodesPer100}", new.nodesPer100 * 1.3 <= old.nodesPer100)
        assertTrue("zęby/100 px: łamana ${old.teethPer100}, krzywa ${new.teethPer100}", new.teethPer100 < old.teethPer100)
        assertTrue("błąd ${new.meanErr} px", new.meanErr < 1.0)
    }

    /** Syntetyczne: okrąg, sinusoida, S i ∑ z chropowatym brzegiem jak kreda (te same kształty co w `ChalkCurvesTest`). */
    @Test
    fun syntheticPreview() {
        val w = 360; val h = 130
        val shapes = listOf(
            FloatArray(802) { i -> val t = 2 * PI * (i / 2) / 400; (if (i % 2 == 0) 60 + 40 * cos(t) else 62 + 40 * sin(t)).toFloat() },
            FloatArray(602) { i -> val t = (i / 2) / 300.0; (if (i % 2 == 0) 140 + 20 * sin(2 * PI * t) else 22 + 86 * t).toFloat() },
            FloatArray(402) { i -> val t = (i / 2) / 200.0; (if (i % 2 == 0) 175 + 90 * t else 65 + 25 * sin(2 * PI * 90 * t / 60)).toFloat() },
            dense(floatArrayOf(335f, 20f, 285f, 20f, 315f, 65f, 285f, 110f, 337f, 110f)),
        )
        val chalk = Raster(w, h)
        val rnd = java.util.Random(7)
        val ink = Raster.argb(255, 235, 235, 230)
        for (y in 0 until h) for (x in 0 until w) {
            val d = shapes.minOf { CurveMetrics.distToPolyline(x + 0.5f, y + 0.5f, it) }
            if (d <= 2.5f + (rnd.nextFloat() - 0.5f) * 0.9f) chalk[x, y] = ink
        }
        val b = both(chalk)
        render(File(outDir(), "syntetyczne.png"), chalk, b, intArrayOf(0, 0, w, h), zoom = 3)
        println("syntetyczne: kresek ${b.lines.size}, węzły/100 px ${b.old.nodesPer100} → ${b.new.nodesPer100}, zęby/100 px ${b.old.teethPer100} → ${b.new.teethPer100}, błąd ${b.old.meanErr} → ${b.new.meanErr}")
        // ∑ to proste odcinki (łamana ma tam tyle samo węzłów), więc na całym zestawie ok. 2×, nie 3× (sinusoida sama: ≥ 3×, `ChalkCurvesTest`)
        assertTrue("węzły/100 px: łamana ${b.old.nodesPer100}, krzywa ${b.new.nodesPer100}", b.new.nodesPer100 * 2 <= b.old.nodesPer100)
        assertTrue("zęby krzywej ${b.new.teeth}", b.new.teeth == 0)
    }

    private fun dense(poly: FloatArray): FloatArray {
        val out = ArrayList<Float>()
        for (i in 1 until poly.size / 2) {
            val ax = poly[2 * i - 2]; val ay = poly[2 * i - 1]; val bx = poly[2 * i]; val by = poly[2 * i + 1]
            val k = (hypot(bx - ax, by - ay) * 2).toInt().coerceAtLeast(1)
            for (j in 0 until k) { out += ax + (bx - ax) * j / k; out += ay + (by - ay) * j / k }
        }
        out += poly[poly.size - 2]; out += poly[poly.size - 1]
        return out.toFloatArray()
    }

    /** Okno [WIN_W]×[WIN_H] px wyniku z największą długością kresek (x, y, w, h). */
    private fun window(chalk: Raster, b: Both): IntArray {
        val cell = 10
        val cw = (chalk.w + cell - 1) / cell; val ch = (chalk.h + cell - 1) / cell
        val dens = IntArray(cw * ch)
        for (l in b.lines) for (i in 0 until l.size) {
            val x = (l.pts[2 * i] / cell).toInt().coerceIn(0, cw - 1); val y = (l.pts[2 * i + 1] / cell).toInt().coerceIn(0, ch - 1)
            dens[y * cw + x]++
        }
        val ww = min(cw, WIN_W / cell); val wh = min(ch, WIN_H / cell)
        var best = -1; var bx = 0; var by = 0
        for (y in 0..ch - wh) for (x in 0..cw - ww) {
            var s = 0
            for (yy in y until y + wh) for (xx in x until x + ww) s += dens[yy * cw + xx]
            if (s > best) { best = s; bx = x; by = y }
        }
        return intArrayOf(bx * cell, by * cell, ww * cell, wh * cell)
    }

    /**
     * Cztery panele okna [win] w powiększeniu [zoom]: u góry kreda zdjęcia (szara) z linią środkową i węzłami — z lewej
     * łamana (pomarańczowa, każdy wierzchołek), z prawej krzywa (turkusowa, kotwice; narożniki kwadratem); u dołu te
     * same kreski w pełnej grubości, białe na tablicy, jak w zeszycie.
     */
    private fun render(to: File, chalk: Raster, b: Both, win: IntArray, zoom: Int = 4) {
        val (x0, y0, w, h) = win.toList()
        val pw = w * zoom; val ph = h * zoom; val gap = 6
        val img = BufferedImage(2 * pw + gap, 2 * ph + gap, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(0x101010); g.fillRect(0, 0, img.width, img.height)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        val board = 0x2e3b35
        val bg = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) for (x in 0 until w) {
            val a = if (x0 + x < chalk.w && y0 + y < chalk.h) Raster.alpha(chalk[x0 + x, y0 + y]) else 0
            val k = a * 0.45f / 255f
            fun mix(c: Int) = (c + (200 - c) * k).toInt()
            bg.setRGB(x, y, (mix(board shr 16 and 255) shl 16) or (mix(board shr 8 and 255) shl 8) or mix(board and 255))
        }
        for (panel in 0 until 4) {
            val px = (panel % 2) * (pw + gap); val py = (panel / 2) * (ph + gap)
            val gp = g.create(px, py, pw, ph) as java.awt.Graphics2D
            if (panel < 2) gp.drawImage(bg, 0, 0, pw, ph, null) else { gp.color = Color(board); gp.fillRect(0, 0, pw, ph) }
            gp.translate(-x0 * zoom.toDouble(), -y0 * zoom.toDouble())
            gp.scale(zoom.toDouble(), zoom.toDouble())
            for (i in b.lines.indices) {
                val path = Path2D.Float()
                val curve = panel % 2 == 1
                if (curve) {
                    val c = b.curves[i] ?: continue
                    path.moveTo(c[0], c[1])
                    for (s in 0 until (c.size / 2 - 1) / 3) {
                        val o = 6 * s
                        path.curveTo(c[o + 2], c[o + 3], c[o + 4], c[o + 5], c[o + 6], c[o + 7])
                    }
                } else {
                    val p = b.polys[i]
                    path.moveTo(p[0], p[1])
                    for (k in 1 until p.size / 2) path.lineTo(p[2 * k], p[2 * k + 1])
                }
                if (panel < 2) {
                    gp.color = if (curve) Color(0x48dbfb) else Color(0xff9f43)
                    gp.stroke = BasicStroke(1.6f / zoom)
                    gp.draw(path)
                    val r = 2.2f / zoom
                    if (curve) {
                        val c = b.curves[i]!!
                        val corners = CurveMetrics.cornerAnchors(c)
                        for (a in 0 until c.size / 2 step 3) gp.fill(Ellipse2D.Float(c[2 * a] - r, c[2 * a + 1] - r, 2 * r, 2 * r))
                        gp.color = Color(0xff6b6b)
                        for (k in 0 until corners.size / 2) gp.fill(java.awt.geom.Rectangle2D.Float(corners[2 * k] - r * 1.4f, corners[2 * k + 1] - r * 1.4f, 2.8f * r, 2.8f * r))
                    } else {
                        val p = b.polys[i]
                        for (k in 0 until p.size / 2) gp.fill(Ellipse2D.Float(p[2 * k] - r, p[2 * k + 1] - r, 2 * r, 2 * r))
                    }
                } else {
                    gp.color = Color(0xf2f2ee)
                    gp.stroke = BasicStroke(max(1f, b.lines[i].width), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    gp.draw(path)
                }
            }
            gp.dispose()
        }
        g.dispose()
        to.parentFile.mkdirs()
        ImageIO.write(img, "png", to)
        println("podgląd: ${to.absolutePath} (okno ${win.toList()})")
    }

    private companion object {
        const val SIDE = 2400
        const val WIN_W = 240
        const val WIN_H = 140
    }
}
