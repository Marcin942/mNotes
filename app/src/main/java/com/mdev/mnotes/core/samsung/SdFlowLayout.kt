package com.mdev.mnotes.core.samsung

import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.text.TextFit
import com.mdev.mnotes.core.text.TextMeasure
import kotlin.math.abs
import kotlin.math.max

/**
 * Pomiar tekstu potrzebny do ułożenia tekstu płynącego Samsunga w polach mNotes: zawijanie wierszy i wymiary czcionki
 * urządzenia. Rdzeń nie zna Androida, więc implementację dostarcza warstwa `render/` (StaticLayout), a testy i tryb
 * awaryjny używają [SdApproxMeasure].
 */
interface SdFlowMeasure : TextMeasure {
    /** Wzniesienie czcionki nad linią bazową (dodatnie, pt) dla rozmiaru [sizePt]. */
    fun ascent(sizePt: Float): Float

    /** Zejście czcionki pod linię bazową (dodatnie, pt). */
    fun descent(sizePt: Float): Float

    /** Końce wierszy (wyłączne, rosnąco, ostatni = długość) tekstu jednego akapitu zawiniętego na [widthPt]. */
    fun breakLines(text: String, sizePt: Float, widthPt: Float): IntArray
}

/** Pomiar przybliżony: wymiary Roboto, znak szerokości 0,5 em, łamanie zachłanne na spacjach. */
object SdApproxMeasure : SdFlowMeasure {
    private const val CHAR_EM = 0.5f

    override fun ascent(sizePt: Float) = SdFlowLayout.ROBOTO_ASCENT * sizePt
    override fun descent(sizePt: Float) = SdFlowLayout.ROBOTO_DESCENT * sizePt

    override fun breakLines(text: String, sizePt: Float, widthPt: Float): IntArray {
        if (text.isEmpty()) return intArrayOf(0)
        val perLine = max(1, (widthPt / (CHAR_EM * sizePt)).toInt())
        val ends = ArrayList<Int>()
        var start = 0
        while (start < text.length) {
            var end = start + perLine
            if (end >= text.length) { ends.add(text.length); break }
            // łamanie po ostatniej spacji w oknie; bez spacji wyraz jest łamany twardo
            var cut = end
            while (cut > start && text[cut - 1] != ' ' && text[cut] != ' ') cut--
            if (cut > start) end = if (text[cut] == ' ') cut + 1 else cut
            ends.add(end)
            start = end
        }
        return ends.toIntArray()
    }

    override fun naturalWidth(t: TextBox): Float =
        t.text.split('\n').maxOf { it.length } * CHAR_EM * t.size

    override fun textHeight(t: TextBox, width: Float): Float {
        var lines = 0
        for (p in t.text.split('\n')) lines += breakLines(p, t.size, width).size
        return lines * (ascent(t.size) + descent(t.size)) * t.lineSpacing
    }
}

/** Wynik układu tekstu jednej strony: elementy (pola tekstowe i obrazy z tekstu) i zauważone odstępstwa. */
class SdFlowPage(
    val elements: List<Element>,
    val mixedStyles: Boolean,
    val overflow: Boolean,
    /** Rodzaje zakresów (poza kolorem i rozmiarem), które tekst tej strony zawiera i które nie są przenoszone. */
    val ignoredSpanKinds: Set<Int>,
)

/**
 * Układ tekstu płynącego Samsung Notes w polach tekstowych mNotes (te same miejsca i ten sam wygląd).
 *
 * Model (skalibrowany na prawdziwej notatce): czcionka Roboto 17 dp, wiersz ma wysokość 1,15 · (wzniesienie + zejście),
 * linia bazowa leży w połowie dodatkowego odstępu, marginesy z ramki tekstu, szerokość zawijania = strona − lewy −
 * prawy. Wiersz, którego pasmo zachodzi na obraz albo kształt strony, spada pod ten obiekt. Obraz w tekście zostaje
 * w zapisanym prostokącie, a tekst płynie dalej 6 dp pod nim. Zwarty ciąg wierszy tego samego stylu to jedno pole.
 */
object SdFlowLayout {
    const val DP_PT = 5.0 / 3.0
    const val ROBOTO_ASCENT = 0.9277f
    const val ROBOTO_DESCENT = 0.2441f
    /** Wysokość wiersza Samsunga w em. */
    const val PITCH_EM = 1.3477
    /** Położenie linii bazowej od góry pasma wiersza w em. */
    const val BASELINE_EM = 1.01558
    const val DEFAULT_DP = 17f
    const val DEFAULT_COLOR = 0xFF252525.toInt()
    private const val IMAGE_GAP_DP = 6.0
    private const val OBJECT_CHAR = '￼'
    private const val SPAN_COLOR = 1
    private const val SPAN_SIZE = 3
    private const val EPS = 0.01

    private class Style(val size: Float, val color: Int) {
        fun same(o: Style) = abs(size - o.size) < 1e-3 && color == o.color
    }

    private class Pending(val style: Style, val start: Int, var end: Int, val bandTop: Double)

    /**
     * Układa tekst strony [page] (numer w pliku, od 0). [obstacles] to prostokąty obiektów strony w pt, [colorMap]
     * przekształca kolor źródłowy (wygląd ciemny). Brak tekstu na stronie daje pusty wynik.
     */
    fun layout(
        flow: SdFlow, page: Int, pageW: Float, pageH: Float, obstacles: List<SdRect>, measure: SdFlowMeasure, colorMap: (Int) -> Int,
    ): SdFlowPage {
        val range = flow.pageRange(page)
        if (range.isEmpty()) return SdFlowPage(emptyList(), false, false, emptySet())
        val m = flow.margins
        val left = m[0] * DP_PT
        val top = m[1] * DP_PT
        val width = pageW - left - m[2] * DP_PT
        if (width < 20.0) return SdFlowPage(emptyList(), false, false, emptySet())
        val text = flow.text
        val out = ArrayList<Element>()
        val ignored = sortedSetOf<Int>()
        var mixed = false
        var overflow = false
        var cursor = top
        var pending: Pending? = null
        var lastStyle = Style(DEFAULT_DP * DP_PT.toFloat(), DEFAULT_COLOR)

        fun styleAt(i: Int): Style {
            val size = flow.spanAt(SPAN_SIZE, i)?.floatValue()?.takeIf { it.isFinite() && it > 0f && it < 400f } ?: DEFAULT_DP
            val color = flow.spanAt(SPAN_COLOR, i)?.intValue() ?: DEFAULT_COLOR
            return Style(size * DP_PT.toFloat(), color)
        }

        fun flush() {
            val p = pending ?: return
            pending = null
            val s = text.substring(p.start, p.end)
            if (s.isBlank()) return
            val size = p.style.size
            val natural = measure.ascent(size) + measure.descent(size)
            var spacing = (PITCH_EM * size / natural).toFloat().coerceIn(0.5f, 3f)
            if (abs(spacing - 1.15f) < 0.01f) spacing = 1.15f
            val y0 = (p.bandTop + BASELINE_EM * size - measure.ascent(size)).coerceAtLeast(0.0).toFloat()
            val probe = TextBox(0, 0f, 0f, width.toFloat(), 0f, 0f, s, size, colorMap(p.style.color), lineSpacing = spacing)
            val h = TextFit.boxHeight(measure, probe, width.toFloat())
            out.add(
                TextBox(
                    Ids.next(), (left + width / 2).toFloat(), y0 + h / 2, width.toFloat(), h, 0f, s, size,
                    colorMap(p.style.color), fit = TextBox.FIT_HEIGHT, lineSpacing = spacing,
                ),
            )
            if (y0 + h > pageH) overflow = true
        }

        fun place(bandTop: Double, pitch: Double): Double {
            var y = bandTop
            var moved = true
            var guard = 0
            while (moved && guard++ < 64) {
                moved = false
                for (o in obstacles) {
                    if (y < o.y1 - EPS && y + pitch > o.y0 + EPS && o.y1 > y) { y = o.y1; moved = true }
                }
            }
            return y
        }

        fun addLine(l0: Int, l1: Int, style: Style) {
            val pitch = PITCH_EM * style.size
            val y = place(cursor, pitch)
            val p = pending
            val contiguous = p != null && (l0 == p.end || (l0 == p.end + 1 && text[p.end] == '\n'))
            if (p != null && (!contiguous || y > cursor + EPS || !p.style.same(style))) flush()
            val q = pending
            if (q == null) pending = Pending(style, l0, l1, y) else q.end = l1
            cursor = y + pitch
            lastStyle = style
        }

        var objIdx = text.substring(0, range.first).count { it == OBJECT_CHAR }
        var i = range.first
        val last = range.last + 1
        while (i < last) {
            var nl = text.indexOf('\n', i)
            if (nl < 0 || nl > last) nl = last
            var a = i
            var first = true
            // akapit [i, nl) dzielony znakami obiektów (obrazy wstawione w tekst)
            while (true) {
                val ob = text.indexOf(OBJECT_CHAR, a).let { if (it in a until nl) it else -1 }
                val b = if (ob >= 0) ob else nl
                if (b > a || (first && ob < 0)) {
                    val style = if (b > a) styleAt(a) else lastStyle
                    for (k in a until b) if (!styleAt(k).same(style)) { mixed = true; break }
                    for (s in flow.spans) if (s.kind != SPAN_COLOR && s.kind != SPAN_SIZE && s.start < b && s.end > a) ignored.add(s.kind)
                    var from = 0
                    for (e in measure.breakLines(text.substring(a, b), style.size, width.toFloat())) {
                        addLine(a + from, a + e, style)
                        from = e
                    }
                }
                first = false
                if (ob < 0) break
                // obraz: stoi w zapisanym prostokącie, tekst płynie dalej pod nim
                flush()
                val img = flow.images.getOrNull(objIdx++)?.image
                if (img != null) {
                    ImageConvert.element(img)?.let { out.add(it) }
                    cursor = img.rect.y1 * SamsungConvert.UNIT_PT + IMAGE_GAP_DP * DP_PT
                }
                a = ob + 1
            }
            i = nl + 1
        }
        flush()
        return SdFlowPage(out, mixed, overflow, ignored)
    }
}

/** Obraz Samsunga → [ImageEl] z kluczem zasobu `sd:<media>[:l,t,r,b]`, który warstwa zapisu zamienia na plik zeszytu. */
internal object ImageConvert {
    fun key(img: SdImage): String {
        val full = img.full
        if (full == null || full.w <= 0 || full.h <= 0) return "sd:${img.mediaId}"
        fun f(v: Double) = "%.4f".format(java.util.Locale.ROOT, v.coerceIn(0.0, 1.0))
        val r = img.rect
        return "sd:${img.mediaId}:" + listOf(
            (r.x0 - full.x0) / full.w, (r.y0 - full.y0) / full.h, (r.x1 - full.x0) / full.w, (r.y1 - full.y0) / full.h,
        ).joinToString(",") { f(it) }
    }

    fun element(img: SdImage): ImageEl? {
        val r = img.rect
        val w = (r.w * SamsungConvert.UNIT_PT).toFloat()
        val h = (r.h * SamsungConvert.UNIT_PT).toFloat()
        if (w < 1f || h < 1f) return null
        return ImageEl(
            Ids.next(), key(img), (r.cx * SamsungConvert.UNIT_PT).toFloat(), (r.cy * SamsungConvert.UNIT_PT).toFloat(),
            w, h, Math.toRadians(img.rotationDeg).toFloat(),
        )
    }
}
