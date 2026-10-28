package com.mdev.mnotes.lab.recognize

import com.mdev.mnotes.core.image.Raster
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizerTest {
    private fun block(type: RecognizedBlock.Type, text: String, x: Float, y: Float, conf: Float = 0.9f) =
        RecognizedBlock(type, text, conf, floatArrayOf(x, y, 100f, 30f), "test")

    @Test
    fun noRecognizerGuessesNothing() = runBlocking {
        assertFalse(NoRecognizer.available)
        assertTrue(NoRecognizer.recognize(Raster(4, 4), floatArrayOf(0f, 0f, 4f, 4f)).isEmpty())
    }

    @Test
    fun pageJsonFollowsTheMlabsFormat() {
        val blocks = listOf(
            block(RecognizedBlock.Type.FORMULA, "h = \\frac{g t^2}{2}", 40f, 110f, 0.783f),
            block(RecognizedBlock.Type.TEXT, "Spadek swobodny", 40f, 60f),
            block(RecognizedBlock.Type.DRAWING, "wykres h(t)", 320f, 100f, 1f),
        )
        val o = JSONObject(PageExport.json("Fizyka 1", 3, "2026-10-27", "fizyka-1-s3.png", 595f, 842f, blocks))
        assertEquals("mnotes-strona", o.getString("format"))
        assertEquals(1, o.getInt("wersja"))
        assertEquals("Fizyka 1", o.getString("zeszyt"))
        assertEquals("pt", o.getJSONObject("rozmiar").getString("jednostka"))
        val b = o.getJSONArray("bloki")
        assertEquals(3, b.length())
        // kolejność czytania: tytuł na górze, potem wiersz ze wzorem (lewa) i rysunkiem (prawa)
        assertEquals("tekst", b.getJSONObject(0).getString("typ"))
        assertEquals("Spadek swobodny", b.getJSONObject(0).getString("tresc"))
        assertEquals("wzor", b.getJSONObject(1).getString("typ"))
        assertEquals("h = \\frac{g t^2}{2}", b.getJSONObject(1).getString("latex"))
        assertEquals(0.78, b.getJSONObject(1).getDouble("pewnosc"), 1e-9)
        assertEquals("rysunek", b.getJSONObject(2).getString("typ"))
        assertEquals(4, b.getJSONObject(2).getJSONArray("ramka").length())
    }

    @Test
    fun boardExportHasNoNotebook() {
        val o = JSONObject(PageExport.json(null, 1, "2026-10-27", "tablica-1.png", 1600f, 900f, emptyList(), source = "kreda"))
        assertTrue(o.isNull("zeszyt"))
        assertEquals("kreda", o.getString("zrodlo"))
    }

    @Test
    fun userTextBecomesOneSureBlockAndEmptyTextNone() {
        assertEquals(null, PageExport.userBlock("  \n ", 595f, 842f))
        val block = PageExport.userBlock("  v = s / t\n", 595f, 842f)!!
        val o = JSONObject(PageExport.json("Fizyka", 2, "2026-10-27", "fizyka-s2.png", 595f, 842f, listOf(block)))
        val b = o.getJSONArray("bloki").getJSONObject(0)
        assertEquals("tekst", b.getString("typ"))
        assertEquals("v = s / t", b.getString("tresc"))
        assertEquals(1.0, b.getDouble("pewnosc"), 0.0)
        assertEquals(PageExport.USER_ENGINE, b.getString("silnik"))
        assertEquals(842.0, b.getJSONArray("ramka").getDouble(3), 1e-9)
    }

    @Test
    fun fileStemIsAsciiWithoutSpaces() {
        assertEquals("fizyka-1-s3", PageExport.fileStem("Fizyka 1", 3))
        assertEquals("zolta-lodz-s12", PageExport.fileStem("Żółta Łódź!", 12))
        assertEquals("zeszyt-s1", PageExport.fileStem("???", 1))
    }
}
