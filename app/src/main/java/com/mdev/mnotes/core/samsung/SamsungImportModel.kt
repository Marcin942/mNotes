package com.mdev.mnotes.core.samsung

import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox

/**
 * Jak przenieść notatkę z Samsung Notes (LIB-016). Wybór jest osobny dla każdej notatki.
 *
 * - [STANDARD]: najbliższy odpowiednik w standardach mNotes. Wzór tła to zwykły wzór mNotes (linie, kratka, kropki),
 *   a rozmiar strony jest dopasowany tak, żeby siatka mNotes zgadzała się z siatką Notatek. Strony da się potem
 *   przestylizować w całości (zmiana wzoru, papieru, rozstawu).
 * - [ORIGINAL]: wygląd oryginału. Strona ma dokładnie ten rozmiar co w Notatkach, a tło, którego mNotes nie umie
 *   odtworzyć wzorem, jest rysowane tak jak w oryginale i leży na stronie jak tło zaimportowanego PDF-a.
 */
enum class ImportMode { STANDARD, ORIGINAL }

/** Ustawienia konwersji jednej notatki. [dark] to wygląd ciemny aplikacji Samsunga (pliki niosą zawsze notatkę jasną). */
data class ConvertOptions(val mode: ImportMode = ImportMode.ORIGINAL, val dark: Boolean = false)

enum class Level { INFO, WARN }

/**
 * Jedna pozycja raportu odstępstw: tekst po polsku i po angielsku (rdzeń nie zna języka interfejsu) oraz numery
 * stron (od 1), których dotyczy; pusta lista = cała notatka.
 */
class ReportItem(val level: Level, val pl: String, val en: String, val pages: List<Int> = emptyList()) {
    fun text(polish: Boolean): String = if (polish) pl else en
}

enum class BgKind { LINES, GRID, DOTS }

/**
 * Tło strony, którego mNotes nie ma jako wzoru (położenie linii Samsunga jest inne niż siatki mNotes), opisane
 * czysto: kolory są już końcowe (po odwróceniu dla wyglądu ciemnego), wymiary w pt. Warstwa `data/` rysuje je do PDF-a,
 * a strona odwołuje się do niego jak do tła zaimportowanego PDF-a. Równe opisy to to samo tło (jedna strona PDF-a).
 */
data class SdBackground(
    val w: Float,
    val h: Float,
    val paper: Int,
    val kind: BgKind,
    val pitch: Float,
    val offsetX: Float,
    val offsetY: Float,
    val line: Int,
    val lineWidth: Float,
)

/** Strona po konwersji. [background] niepusty = [spec] nie ma jeszcze [PageSpec.pdf], dopisuje je warstwa zapisu. */
class ConvertedPage(
    val spec: PageSpec,
    val background: SdBackground?,
    /** Wszystko, co leży na stronie, w kolejności rysowania; obrazy mają klucz `sd:<media>[:l,t,r,b]` zamiast nazwy pliku. */
    val elements: List<Element>,
    /** Numer strony w pliku źródłowym (od 0). */
    val source: Int,
) {
    val strokes: List<Stroke> get() = elements.filterIsInstance<Stroke>()
    val textBoxes: List<TextBox> get() = elements.filterIsInstance<TextBox>()
    val images: List<ImageEl> get() = elements.filterIsInstance<ImageEl>()
}

class ConvertedNote(
    val title: String,
    val pages: List<ConvertedPage>,
    val report: List<ReportItem>,
    val strokeCount: Int,
    /** Ile końcowych pustych stron pominięto. */
    val droppedTrailing: Int,
    /** Pliki obrazów dokumentu (numer → bajty) dla kluczy `sd:` z [ConvertedPage.elements]. */
    val media: Map<Int, ByteArray> = emptyMap(),
) {
    val textBoxCount: Int get() = pages.sumOf { it.textBoxes.size }
    val imageCount: Int get() = pages.sumOf { it.images.size }

    val warnings: Int get() = report.count { it.level == Level.WARN }
}
