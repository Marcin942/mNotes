package com.mdev.mnotes.core.guide

import com.mdev.mnotes.core.geom.Axes
import com.mdev.mnotes.core.geom.AxesSpec
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.pen.LineStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Zeszyt-przewodnik (F20): interaktywna instrukcja — każda strona to jeden temat z krótkimi poleceniami i szarymi
 * wzorami do przerysowania, starcia albo zaznaczenia, więc funkcji uczy się, używając ich na kartce. Treść jest
 * zwykłymi elementami (pola tekstu, kreski), więc przewodnik to zwyczajny zeszyt do pisania po nim.
 */
object Guide {
    class Page(val spec: PageSpec, val elements: List<Element>)

    private const val INK = 0xFF263238.toInt()
    private const val ACCENT = 0xFF2E7D4F.toInt()
    private const val HINT = 0x8090A4AE.toInt()
    private const val MARGIN = 42f

    /** Strony przewodnika po polsku albo angielsku. */
    fun pages(pl: Boolean): List<Page> {
        fun t(p: String, e: String) = if (pl) p else e
        return listOf(
            page(t("Witaj w mNotes", "Welcome to mNotes"), listOf(
                t("Ten zeszyt to ćwiczenia: pisz po nim śmiało, a gdy coś zepsujesz — cofnij (dwa palce stuknij razem albo Ctrl+Z).", "This notebook is practice: write on it freely; if you break something, undo (two-finger tap or Ctrl+Z)."),
                t("1. Wybierz długopis na pasku i przerysuj szary wzór poniżej.", "1. Pick the ballpoint on the toolbar and trace the grey shape below."),
                t("2. Listwa obok strony zmienia grubość, styl linii i kolor jednym dotknięciem. Dotknij wybranego koloru drugi raz, by otworzyć panel pióra.", "2. The strip beside the page changes width, line style and colour in one tap. Tap the chosen colour again to open the pen panel."),
                t("3. Zakreślacz kładzie się pod pismem — zaznacz nim słowo „ważne”.", "3. The highlighter goes under the ink — mark the word “important”."),
            ), wave(MARGIN, 470f) + listOf(text(MARGIN, 560f, t("ważne", "important"), 16f, INK))),
            page(t("Kształty: narysuj i przytrzymaj", "Shapes: draw and hold"), listOf(
                t("Narysuj kształt i nie odrywaj pióra przez pół sekundy — wyprostuje się. Trzymając dalej, zmieniasz jego rozmiar.", "Draw a shape and keep the pen down for half a second — it snaps. Keep holding to resize it."),
                t("Spróbuj: prosta, koło, prostokąt, trójkąt, strzałka. Krótkie proste (minus, kreska ułamkowa) też się prostują.", "Try: line, circle, rectangle, triangle, arrow. Short lines (minus, fraction bar) snap too."),
                t("Narzędzie „Krzywa” na pasku zamienia każdą kreskę w gładką krzywą z uchwytami. Wygładzanie łuków dłuższym przytrzymaniem pióra włączysz w Ustawieniach.", "The “Curve” tool on the toolbar turns every stroke into a smooth curve with handles. Smoothing arcs by holding the pen longer can be turned on in Settings."),
            ), dashed(listOf(circle(130f, 500f, 50f), rect(230f, 450f, 360f, 550f), listOf(400f, 550f, 470f, 450f, 540f, 550f, 400f, 550f)))),
            page(t("Gumka: cztery sposoby", "Eraser: four ways"), listOf(
                t("Obiektowa usuwa całą kreskę, pikselowa ściera tylko to, nad czym przejdzie, lasso gumki usuwa obrysowane, a wycinanie tnie kreski dokładnie na obrysie.", "Object erases whole strokes, pixel erases only what it passes over, lasso eraser removes what you circle, cut-out cuts strokes exactly along the loop."),
                t("Tryby zmienisz ikonami na listwie, gdy gumka jest w ręku. Zetrzyj środkowe fale na cztery sposoby.", "Switch modes with the icons on the strip while the eraser is active. Erase the middle waves in all four ways."),
            ), (0 until 4).flatMap { wave(MARGIN, 440f + it * 60f, INK) }),
            page(t("Lasso: zaznacz, przesuń, zapisz", "Lasso: select, move, save"), listOf(
                t("Obrysuj lassem kwadrat i koło razem, przeciągnij je. „Skaluj i obracaj” na pasku zaznaczenia włącza uchwyty.", "Circle the square and the circle together, drag them. “Resize & rotate” on the selection bar shows the handles."),
                t("„Tylko zahacz” na listwie lassa wystarczy, by musnąć obiekt. Prostokąt łapie to, co w ramce.", "“Just touch” on the lasso strip selects by brushing. The rectangle catches what is inside the frame."),
                t("Przytrzymaj zaznaczenie: „Wycinek jako obraz”, „Kopiuj wycinek do schowka”, „Zapisz jako naklejkę”.", "Long-press the selection: “Snip as image”, “Copy snip to clipboard”, “Save as sticker”."),
            ), solid(listOf(rect(MARGIN + 20f, 470f, MARGIN + 120f, 570f), circle(MARGIN + 220f, 520f, 50f)))),
            page(t("Tekst, wykresy i nowe strony", "Text, graphs and new pages"), listOf(
                t("Narzędzie Tekst: dotknij miejsca i pisz z klawiatury. Pole rośnie samo; uchwyty boków zmieniają szerokość.", "Text tool: tap a spot and type. The box grows by itself; side handles change its width."),
                t("Dotknięcie tekstem pod ostatnią stroną zakłada nową. Tekst dłuższy niż strona też ją dodaje.", "A text tap below the last page creates a new one. Text longer than the page adds one too."),
                t("Więcej (⋮) → „Układ współrzędnych…” wstawia osie z podziałką — wykres rysujesz sam, jak niżej.", "More (⋮) → “Coordinate axes…” inserts axes with ticks — draw the graph yourself, as below."),
            ), axes(MARGIN + 40f, 430f)),
            page(t("Strony, obrazy i ciemny tryb", "Pages, images and dark mode"), listOf(
                t("Panel stron: przeciągnij miniaturę, by zmienić kolejność; obok „Dodaj stronę” jest strona pozioma.", "Pages panel: drag a thumbnail to reorder; next to “Add page” there is a landscape page."),
                t("„Styl strony”: wzór, papier, rozmiar, orientacja i „Odwróć kolory” do czytania PDF-ów wieczorem.", "“Page style”: template, paper, size, orientation and “Invert colours” for reading PDFs at night."),
                t("Zdjęcie kartki: zaznacz je, „Efekty obrazu” → „Auto: białe tło”, potem jasność i temperatura.", "Photo of a page: select it, “Image effects” → “Auto: white background”, then brightness and temperature."),
                t("Wszystko da się dostosować: Ustawienia mają wyszukiwarkę (Ctrl+F).", "Everything is customisable: Settings have a search box (Ctrl+F)."),
            ), emptyList()),
        )
    }

    private fun page(title: String, lines: List<String>, extra: List<Element>): Page {
        val spec = PageSpec.of(PageFormat.A4, template = Template.GRID)
        val els = ArrayList<Element>()
        els.add(text(MARGIN, 52f, title, 22f, ACCENT, bold = true))
        var y = 100f
        for (l in lines) {
            val h = textHeight(l, 11.5f, spec.w - 2 * MARGIN)
            els.add(text(MARGIN, y, l, 11.5f, INK, w = spec.w - 2 * MARGIN, h = h))
            y += h + 10f
        }
        els.addAll(extra)
        return Page(spec, els)
    }

    /** Wysokość akapitu z grubsza (wiersze z liczby znaków); pole i tak przelicza układ przy edycji. */
    fun textHeight(s: String, size: Float, w: Float): Float {
        val perLine = (w / (size * 0.5f)).toInt().coerceAtLeast(8)
        val lines = (s.length + perLine - 1) / perLine
        return lines.coerceAtLeast(1) * size * 1.35f
    }

    private fun text(x: Float, y: Float, s: String, size: Float, color: Int, bold: Boolean = false, w: Float = s.length * size * 0.6f + 8f, h: Float = size * 1.4f) =
        TextBox(Ids.next(), x + w / 2f, y + h / 2f, w, h, 0f, s, size, color, style = if (bold) TextBox.STYLE_BOLD else 0)

    private fun stroke(xy: FloatArray, color: Int, line: LineStyle, poly: Boolean): Stroke {
        val pts = FloatArray(xy.size / 2 * 3)
        for (i in 0 until xy.size / 2) { pts[3 * i] = xy[2 * i]; pts[3 * i + 1] = xy[2 * i + 1]; pts[3 * i + 2] = 1f }
        return Stroke(Ids.next(), PenKind.BALLPOINT, color, 1.6f, pts, poly = poly, line = line)
    }

    private fun wave(x0: Float, y: Float, color: Int = HINT): List<Element> {
        val xy = FloatArray(2 * 80) { k -> val i = k / 2; if (k % 2 == 0) x0 + i * 6f else y + 14f * sin(i * 0.25f) }
        return listOf(stroke(xy, color, if (color == HINT) LineStyle.DASHED else LineStyle.SOLID, false))
    }

    private fun circle(cx: Float, cy: Float, r: Float): List<Float> = (0..48).flatMap { i -> val a = 2 * PI * i / 48; listOf(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat()) }
    private fun rect(l: Float, t: Float, r: Float, b: Float) = listOf(l, t, r, t, r, b, l, b, l, t)

    private fun dashed(shapes: List<List<Float>>) = shapes.map { stroke(it.toFloatArray(), HINT, LineStyle.DASHED, it.size <= 12) }
    private fun solid(shapes: List<List<Float>>) = shapes.map { stroke(it.toFloatArray(), INK, LineStyle.SOLID, it.size <= 12) }

    private fun axes(left: Float, top: Float): List<Element> {
        val d = Axes.build(AxesSpec(-4f, 4f, -2f, 2f, unit = 28f, labels = false), left, top)
        val out = ArrayList<Element>()
        d.grid.forEach { out.add(stroke(it, HINT, LineStyle.SOLID, true).withStyle(width = 0.4f)) }
        d.axes.forEach { out.add(stroke(it, INK, LineStyle.SOLID, true)) }
        // Szkic sinusa do przerysowania na osiach.
        val xs = FloatArray(2 * 60) { k -> val i = k / 2; val x = -4f + 8f * i / 59f; if (k % 2 == 0) left + (x + 4f) * 28f else top + (2f - 1.6f * sin(x * 1.2f)) * 28f }
        out.add(stroke(xs, HINT, LineStyle.DASHED, false))
        return out
    }
}
