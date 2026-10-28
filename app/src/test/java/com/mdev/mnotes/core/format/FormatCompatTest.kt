package com.mdev.mnotes.core.format

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.layout.ViewMode
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.ImageCrop
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.Paper
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TapeEl
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.pen.LineStyle
import com.mdev.mnotes.core.shapes.Bezier
import com.mdev.mnotes.core.shapes.CurveFit
import com.mdev.mnotes.data.NotebookMeta
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 2c-A13: zgodność formatu z wydaną wersją. Wzorcem jest koder z `main` ([LegacyPageCodec]), a nie nowy koder
 * porównywany z samym sobą: (a) strona bez nowych flag zapisuje się bajt w bajt jak dawniej, (b) stara wersja czyta
 * nową stronę z wypełnioną krzywą na tablicy, (c) zeszyty `.mnotes` zapisane starym koderem otwierają się dziś.
 */
class FormatCompatTest {

    private fun line(rnd: Random, pen: PenKind, n: Int, x0: Float, y0: Float): Stroke {
        val pts = FloatArray(3 * n)
        var x = x0; var y = y0
        for (i in 0 until n) {
            x += 1f + rnd.nextFloat() * 4f; y += (rnd.nextFloat() - 0.5f) * 3f
            pts[3 * i] = x; pts[3 * i + 1] = y; pts[3 * i + 2] = 0.3f + 0.7f * rnd.nextFloat()
        }
        return Stroke(Ids.next(), pen, 0xFF000000.toInt() or rnd.nextInt(0xFFFFFF), 0.6f + rnd.nextFloat() * 3f, pts)
    }

    /** Strona tylko z tym, co umiała wydana wersja (bez krzywych, wypełnień kresek i nowych bitów). */
    private fun oldPage(seed: Long): List<Element> {
        val rnd = Random(seed)
        val out = ArrayList<Element>()
        for (i in 0 until 24) out.add(line(rnd, PenKind.entries[i % PenKind.entries.size], 6 + rnd.nextInt(40), 40f + 20f * (i % 5), 60f + 30f * i))
        val sq = floatArrayOf(300f, 100f, 1f, 400f, 100f, 1f, 400f, 180f, 1f, 300f, 180f, 1f, 300f, 100f, 1f)
        out.add(Stroke(Ids.next(), PenKind.BALLPOINT, 0xFF1565C0.toInt(), 1.2f, sq, poly = true, line = LineStyle.DASHED))
        out.add(Stroke(Ids.next(), PenKind.BALLPOINT, 0xFFC62828.toInt(), 1.5f, sq.copyOf(), locked = true, ellipse = true, line = LineStyle.DOTTED))
        out.add(TextBox(Ids.next(), 120.5f, 300f, 200f, 40f, 0.3f, "Zażółć gęślą jaźń ✓", 14f, 0xFF00AA00.toInt(),
            TextBox.FAMILY_SERIF, TextBox.STYLE_BOLD or TextBox.STYLE_UNDERLINE, TextBox.ALIGN_CENTER, 0x33FFFF00, true))
        out.add(TextBox(Ids.next(), 60f, 500f, 180f, 30f, 0f, "auto", 12f, 0xFF000000.toInt(), fit = TextBox.FIT_AUTO))
        out.add(TextBox(Ids.next(), 60f, 560f, 180f, 30f, 0f, "stałe", 12f, 0xFF000000.toInt(), fit = TextBox.FIT_FIXED))
        out.add(ImageEl(Ids.next(), "img-1.webp", 250f, 400f, 120f, 80f, -0.2f, 0.5f, false))
        out.add(ImageEl(Ids.next(), "img-1.webp", 320f, 500f, 90f, 60f, 0.1f, 1f, false, ImageCrop(-0.05f, 0.1f, 0.6f, 1.02f, -0.21f)))
        out.add(TapeEl(Ids.next(), 200f, 700f, 160f, 24f, 0.05f, 0xFFFFC107.toInt()))
        out.add(FillEl(Ids.next(), 0xFF3366CC.toInt(), false, listOf(floatArrayOf(0f, 0f, 600f, 0f, 600f, 800f, 0f, 800f), floatArrayOf(100.5f, 100f, 200f, 100f, 150f, 180.25f))))
        return out
    }

    @Test fun pageWithoutNewFlagsIsWrittenByteForByteAsBefore() {
        for (seed in 1L..5L) {
            val c = PageContent.of(oldPage(seed))
            assertArrayEquals("strona $seed", LegacyPageCodec.encode(c), PageCodec.encode(c))
        }
    }

    @Test fun oldReaderSurvivesFilledCurveOnABoard() {
        // Tablica rośnie z treścią: współrzędne daleko poza zwykłą stroną i ujemne.
        val ctrl = CurveFit.through(floatArrayOf(-300f, -200f, 9000f, 400f, 12000f, 9500f, 200f, 8800f), closed = true)!!
        val curve = Stroke(Ids.next(), PenKind.FOUNTAIN, 0xFF2255AA.toInt(), 1.5f, Bezier.sample(ctrl), ctrl = ctrl, fill = 0x662255AA)
        val after = TextBox(Ids.next(), 11000f, 9000f, 200f, 30f, 0f, "za krzywą", 12f, 0xFF000000.toInt())
        val els = listOf(curve, after, TapeEl(Ids.next(), -100f, 50f, 80f, 20f, 0f, 0xFFFFC107.toInt()))
        val old = LegacyPageCodec.decode(PageCodec.encode(PageContent.of(els)))
        assertEquals(3, old.size)
        val s = old.elements[0] as Stroke
        assertEquals(curve.count, s.count)
        for (k in 0 until curve.count) {
            assertEquals(curve.pts[3 * k], s.pts[3 * k], 0.006f)
            assertEquals(curve.pts[3 * k + 1], s.pts[3 * k + 1], 0.006f)
        }
        assertEquals("za krzywą", (old.elements[1] as TextBox).text)
        assertEquals(-100f, (old.elements[2] as TapeEl).cx, 0.01f)
    }

    // — (c) zeszyty wzorcowe w src/test/resources/format/ —

    /** Opis wzorca: nazwa pliku, tytuł i liczby elementów kolejnych stron. */
    private class Sample(val file: String, val title: String, val pages: List<List<Element>>, val pdf: Boolean = false)

    private fun samples(): List<Sample> {
        val rnd = Random(42)
        return listOf(
            Sample("pismo.mnotes", "Analiza — wykład 3", listOf(oldPage(11), oldPage(12).take(10))),
            Sample("obrazy.mnotes", "Zdjęcia tablicy", listOf(
                listOf(ImageEl(Ids.next(), "board-1.webp", 297f, 420f, 560f, 400f, 0f, 1f, false), line(rnd, PenKind.HIGHLIGHTER, 30, 60f, 300f)),
                emptyList(),
            )),
            Sample("pdf.mnotes", "Skrypt.pdf", listOf(listOf(line(rnd, PenKind.HIGHLIGHTER, 20, 80f, 120f)), listOf(line(rnd, PenKind.BALLPOINT, 12, 70f, 400f))), pdf = true),
        )
    }

    /** meta.json dokładnie z polami wydanej wersji (`NotebookMeta.toJson` i `PageSpec.toJson` na `main`). */
    private fun legacyMeta(s: Sample, ids: List<String>): String {
        val pages = JSONArray()
        for ((i, id) in ids.withIndex()) {
            val spec = JSONObject().put("w", 595.28).put("h", 841.89).put("p", Paper.WHITE)
                .put("t", if (s.pdf) Template.BLANK.code else Template.LINED.code).put("l", 0xFFB0C4DE.toInt()).put("s", 22.0).put("lw", 0.6).put("o", 1)
            if (s.pdf) spec.put("pa", "doc.pdf").put("pn", i)
            pages.put(JSONObject().put("id", id).put("s", spec))
        }
        return JSONObject().put("v", 1).put("id", "nb-" + s.file.substringBefore('.')).put("title", s.title)
            .put("cr", 1_700_000_000_000L).put("mod", 1_700_000_100_000L).put("op", 1_700_000_200_000L)
            .put("cov", 0xFF4C9A6A.toInt()).put("cs", 0).put("fav", false).put("tr", 0L).put("lock", false)
            .put("last", 1).put("vm", ViewMode.DEFAULT).put("pdfdoc", s.pdf).put("tags", JSONArray(listOf("studia")))
            .put("pages", pages).put("bm", JSONArray().put(JSONObject().put("p", ids[0]).put("t", "Początek"))).toString()
    }

    /** Odtwarza wzorce starym koderem — tylko z `MNOTES_FORMAT_REGEN=1` (raz, potem pliki idą do repozytorium). */
    @Test fun regenerateSamples() {
        assumeTrue(System.getenv("MNOTES_FORMAT_REGEN") != null)
        val dir = File("src/test/resources/format").apply { mkdirs() }
        for (s in samples()) {
            val ids = s.pages.indices.map { "pg$it" }
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { z ->
                fun put(name: String, data: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry() }
                put("meta.json", legacyMeta(s, ids).toByteArray(Charsets.UTF_8))
                for ((i, p) in s.pages.withIndex()) put("p/${ids[i]}.mnp", LegacyPageCodec.encode(PageContent.of(p)))
                if (s.pdf) put("assets/doc.pdf", "%PDF-1.4 wzorzec".toByteArray())
            }
            File(dir, s.file).writeBytes(bytes.toByteArray())
        }
    }

    @Test fun notebooksWrittenByTheOldVersionOpen() {
        for (s in samples()) {
            val entries = HashMap<String, ByteArray>()
            val stream = javaClass.getResourceAsStream("/format/${s.file}")
            assertNotNull("brak wzorca ${s.file}", stream)
            ZipInputStream(stream!!).use { z -> while (true) { val e = z.nextEntry ?: break; entries[e.name] = z.readBytes() } }
            val meta = NotebookMeta.fromJson(String(entries.getValue("meta.json"), Charsets.UTF_8))
            assertEquals(s.title, meta.title)
            assertEquals(s.pdf, meta.pdfDoc)
            assertEquals(s.pages.size, meta.pages.size)
            assertEquals("Początek", meta.bookmarks.single().title)
            for ((i, pm) in meta.pages.withIndex()) {
                if (s.pdf) assertEquals(i, pm.spec.pdf?.page)
                val c = PageCodec.decode(entries.getValue("p/${pm.id}.mnp"))
                val want = s.pages[i]
                assertEquals("${s.file} strona $i", want.map { it::class }, c.elements.map { it::class })
                for ((a, b) in want.zip(c.elements)) if (a is TextBox) assertEquals(a.text, (b as TextBox).text)
                for ((a, b) in want.zip(c.elements)) if (a is Stroke) assertEquals(a.count, (b as Stroke).count)
            }
        }
    }
}
