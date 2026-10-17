package com.mdev.mnotes.core.samsung

/**
 * Pociągnięcie z Samsung Notes w jednostkach pliku (strona 1600 × 2262, 1 jednostka = 0,375 pt).
 * Tablice mają tę samą długość; czas [t] w milisekundach od początku pisania.
 */
class SdStroke(
    val x: DoubleArray,
    val y: DoubleArray,
    val pressure: FloatArray,
    val t: IntArray,
    val props: Long,
    val color: Int?,
    val size: Float?,
    /** Pełna nazwa klasy pióra z tablicy napisów, np. `…InkPen2`. */
    val pen: String?,
) {
    val count: Int get() = x.size
}

/** Obiekt strony: [type] to typ z formatu (1 = pociągnięcie), [children] to obiekty zagnieżdżone (kontenery). */
class SdObject(val type: Int, val payload: ByteArray, val children: List<SdObject>)

/**
 * Strona notatki. [error] niepusty oznacza stronę, której nie dało się odczytać (jest wtedy bez warstw);
 * konwerter zachowuje jej miejsce jako pustą kartkę z ostrzeżeniem w raporcie.
 */
class SdPage(
    val uuid: String,
    val width: Int,
    val height: Int,
    val bgColor: Int?,
    val templateType: Int?,
    val templateUri: String?,
    val bgImage: Int?,
    val hasPdf: Boolean,
    val layers: List<List<SdObject>>,
    val error: String? = null,
) {
    /** Strona bez żadnego obiektu (Samsung dopisuje taką po ostatniej stronie z treścią). */
    val isEmpty: Boolean get() = error == null && layers.all { it.isEmpty() }
}

/**
 * Cała notatka. [flow] to tekst płynący z `note.note` (null, gdy notatka go nie ma), [media] to pliki obrazów z katalogu
 * `media/` pod numerem z przedrostka nazwy (`4@files_….jpg` → 4); bajty trzymane są raz i współdzielone z wynikiem konwersji.
 */
class SdDocument(
    val strings: Map<Int, String>,
    val pages: List<SdPage>,
    val flow: SdFlow? = null,
    val media: Map<Int, ByteArray> = emptyMap(),
)

/** Wszystkie obiekty warstw w kolejności rysowania: rodzic przed dziećmi. */
internal fun SdPage.allObjects(): List<SdObject> {
    val out = ArrayList<SdObject>()
    fun visit(o: SdObject) {
        out.add(o)
        for (c in o.children) visit(c)
    }
    for (layer in layers) for (o in layer) visit(o)
    return out
}
