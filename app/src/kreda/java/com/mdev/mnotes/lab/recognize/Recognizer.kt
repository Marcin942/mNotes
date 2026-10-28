package com.mdev.mnotes.lab.recognize

import com.mdev.mnotes.core.image.Raster
import org.json.JSONArray
import org.json.JSONObject

/** Jeden rozpoznany fragment strony: tekst albo wzór, z pewnością i ramką w jednostkach strony (x, y, w, h). */
class RecognizedBlock(
    val type: Type,
    /** Treść: zwykły tekst dla [Type.TEXT], LaTeX dla [Type.FORMULA], opis dla [Type.DRAWING]. */
    val content: String,
    val confidence: Float,
    val box: FloatArray,
    val engine: String,
    val alternatives: List<String> = emptyList(),
) {
    enum class Type(val key: String) { TEXT("tekst"), FORMULA("wzor"), DRAWING("rysunek"), IMAGE("obraz") }
}

/**
 * Rozpoznawanie pisma i wzorów dla wariantu lab (K4). Bez modeli w tej aplikacji: dziś jest tylko [NoRecognizer],
 * a docelowo implementacja woła usługę mLabs (TrOCR dla linii tekstu, TAMER dla wzorów), patrz
 * `docs/INTEGRACJA-MLABS.md`. Interfejs jest celowo mały, żeby dało się go podmienić bez ruszania UI.
 */
interface Recognizer {
    val name: String

    /** Czy rozpoznawanie jest dostępne (model albo usługa); gdy false, UI pokazuje „wpisz ręcznie”. */
    val available: Boolean

    /** Rozpoznaje wycinek strony [image]; [pageBox] to jego położenie na stronie (x, y, w, h), w które trafiają ramki. */
    suspend fun recognize(image: Raster, pageBox: FloatArray, kind: Kind = Kind.AUTO): List<RecognizedBlock>

    enum class Kind { TEXT, FORMULA, AUTO }
}

/** Brak rozpoznawania: nic nie zgaduje (lepiej pusto niż zmyślony wzór). */
object NoRecognizer : Recognizer {
    override val name = "brak"
    override val available = false
    override suspend fun recognize(image: Raster, pageBox: FloatArray, kind: Recognizer.Kind): List<RecognizedBlock> = emptyList()
}

/**
 * Strona zeszytu do mLabs w formacie `mnotes-strona` wersja 1 (`mLabs/docs/INTEGRACJA-MNOTES-KREDA.md`): obraz strony
 * obok i bloki w kolejności czytania (z góry na dół, w wierszu z lewej do prawej).
 */
object PageExport {
    const val FORMAT = "mnotes-strona"
    const val VERSION = 1

    fun json(notebook: String?, page: Int, date: String, image: String, pageW: Float, pageH: Float, blocks: List<RecognizedBlock>, source: String = "mNotes"): String {
        val root = JSONObject()
            .put("format", FORMAT).put("wersja", VERSION).put("zrodlo", source)
            .put("zeszyt", notebook ?: JSONObject.NULL).put("strona", page).put("data", date).put("obraz", image)
            .put("rozmiar", JSONObject().put("szer", pageW.toDouble()).put("wys", pageH.toDouble()).put("jednostka", "pt"))
        val arr = JSONArray()
        for (b in readingOrder(blocks)) {
            val o = JSONObject().put("typ", b.type.key)
            when (b.type) {
                RecognizedBlock.Type.TEXT -> o.put("tresc", b.content)
                RecognizedBlock.Type.FORMULA -> o.put("latex", b.content)
                else -> o.put("opis", b.content)
            }
            o.put("pewnosc", Math.round(b.confidence * 100) / 100.0)
            o.put("ramka", JSONArray(b.box.map { Math.round(it * 10) / 10.0 }))
            o.put("silnik", b.engine)
            if (b.alternatives.isNotEmpty()) o.put("alternatywy", JSONArray(b.alternatives))
            arr.put(o)
        }
        return root.put("bloki", arr).toString(1)
    }

    /** Kolejność czytania: wiersze (bloki, których środki dzieli mniej niż pół wysokości), w wierszu od lewej. */
    fun readingOrder(blocks: List<RecognizedBlock>): List<RecognizedBlock> {
        val sorted = blocks.sortedBy { it.box[1] + it.box[3] / 2 }
        val rows = ArrayList<MutableList<RecognizedBlock>>()
        for (b in sorted) {
            val cy = b.box[1] + b.box[3] / 2
            val row = rows.lastOrNull()
            val ref = row?.first()
            if (ref != null && kotlin.math.abs(cy - (ref.box[1] + ref.box[3] / 2)) < minOf(ref.box[3], b.box[3]) / 2) row.add(b)
            else rows.add(mutableListOf(b))
        }
        return rows.flatMap { r -> r.sortedBy { it.box[0] } }
    }

    /** Silnik bloku wpisanego ręcznie (zamiast rozpoznawania): pewność 1, cała strona jako ramka. */
    const val USER_ENGINE = "uzytkownik"

    /** Tekst strony dopisany przez użytkownika jako jeden blok; null dla pustego (lepiej nic niż pusty blok). */
    fun userBlock(text: String, pageW: Float, pageH: Float): RecognizedBlock? =
        text.trim().takeIf { it.isNotEmpty() }?.let { RecognizedBlock(RecognizedBlock.Type.TEXT, it, 1f, floatArrayOf(0f, 0f, pageW, pageH), USER_ENGINE) }

    /** Nazwa pliku bez polskich znaków i spacji: „Fizyka 1”, strona 3 → fizyka-1-s3. */
    fun fileStem(notebook: String, page: Int): String {
        val ascii = java.text.Normalizer.normalize(notebook.lowercase().replace('ł', 'l'), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        val slug = ascii.replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "zeszyt" }
        return "$slug-s$page"
    }
}
