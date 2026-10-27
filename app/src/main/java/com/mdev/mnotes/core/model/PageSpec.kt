package com.mdev.mnotes.core.model

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

/** Wzór tła strony. Kody są częścią formatu bazy — nie zmieniać. */
enum class Template(val code: Int, val defaultSpacing: Float) {
    BLANK(0, 0f),
    LINED(1, 22f),
    GRID(2, 14.17f),
    DOTS(3, 14.17f),
    ISO_DOTS(4, 16f),
    ISO_GRID(5, 16f),
    CORNELL(6, 22f),
    STAFF(7, 7f),
    CHECKLIST(8, 26f);

    companion object {
        fun fromCode(code: Int): Template = entries.firstOrNull { it.code == code } ?: BLANK
    }
}

/** Tło z zaimportowanego PDF-a: plik zasobu zeszytu + numer strony (od 0). */
data class PdfRef(val asset: String, val page: Int)

/** Gotowy format papieru, wymiary w punktach (1 pt = 1/72 cala). */
enum class PageFormat(val id: String, val w: Float, val h: Float) {
    A4("a4", 595f, 842f),
    A5("a5", 420f, 595f),
    A6("a6", 298f, 420f),
    B5("b5", 499f, 709f),
    LETTER("letter", 612f, 792f),
    LEGAL("legal", 612f, 1008f),
    TABLET("tablet", 600f, 960f),
    SQUARE("square", 600f, 600f);

    fun landscape(): Pair<Float, Float> = h to w

    companion object {
        fun fromId(id: String): PageFormat? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Wygląd strony bez zawartości: rozmiar, papier i wzór tła.
 * Kolory są w ARGB. [spacing] to rozstaw linii/kratki w pt (dla pustej kartki ignorowany).
 */
data class PageSpec(
    val w: Float = PageFormat.A4.w,
    val h: Float = PageFormat.A4.h,
    val paper: Int = Paper.WHITE,
    val template: Template = Template.BLANK,
    val line: Int = Paper.autoLineColor(paper),
    val spacing: Float = template.defaultSpacing,
    val lineWidth: Float = 0.6f,
    val opts: Int = OPT_MARGIN,
    val pdf: PdfRef? = null,
) {
    val marginLine: Boolean get() = opts and OPT_MARGIN != 0

    /**
     * Ciemny papier — zakreślacz działa na nim jak „screen”, nie „multiply”. Dotyczy też strony z tłem PDF, bo strona
     * zaimportowana z Samsung Notes niesie wzór tła w PDF-ie, a kolor papieru w [paper]. Zwykłe importowane PDF-y mają
     * papier biały, więc dla nich nic się nie zmienia.
     */
    val darkPaper: Boolean get() = Paper.isDark(paper)

    fun withSize(w: Float, h: Float) = copy(w = w, h = h)

    val landscape: Boolean get() = w > h

    /** Format strony (A4, A5…), gdy rozmiar pasuje co do 1 pt w którejkolwiek orientacji; null = rozmiar własny. */
    val format: PageFormat? get() = PageFormat.entries.firstOrNull { f ->
        (abs(f.w - w) <= 1f && abs(f.h - h) <= 1f) || (abs(f.h - w) <= 1f && abs(f.w - h) <= 1f)
    }

    /**
     * Ta sama strona w orientacji [land] (F5: pionowe i poziome strony w jednym zeszycie). Zamienia boki tylko,
     * gdy orientacja się zmienia; kwadrat zostaje bez zmian.
     */
    fun oriented(land: Boolean): PageSpec = if (landscape == land || w == h) this else copy(w = h, h = w)

    /** Ten sam format w orientacji tej strony. */
    fun withFormat(f: PageFormat): PageSpec = if (landscape) copy(w = f.h, h = f.w) else copy(w = f.w, h = f.h)

    /** Zmiana wzoru zachowuje papier i rozmiar, ale przywraca rozstaw typowy dla wzoru. */
    fun withTemplate(t: Template) = copy(template = t, spacing = t.defaultSpacing)

    fun toJson(): String {
        val o = JSONObject()
        o.put("w", round2(w)).put("h", round2(h)).put("p", paper).put("t", template.code)
        o.put("l", line).put("s", round2(spacing)).put("lw", round2(lineWidth)).put("o", opts)
        if (pdf != null) o.put("pa", pdf.asset).put("pn", pdf.page)
        return o.toString()
    }

    private fun round2(v: Float): Double = (v * 100f).roundToInt() / 100.0

    companion object {
        const val OPT_MARGIN = 1
        const val MIN_SIZE = 72f
        const val MAX_SIZE = 8000f

        fun fromJson(json: String): PageSpec = try {
            val o = JSONObject(json)
            val t = Template.fromCode(o.optInt("t", 0))
            val paper = o.optInt("p", Paper.WHITE)
            PageSpec(
                w = o.optDouble("w", PageFormat.A4.w.toDouble()).toFloat().coerceIn(MIN_SIZE, MAX_SIZE),
                h = o.optDouble("h", PageFormat.A4.h.toDouble()).toFloat().coerceIn(MIN_SIZE, MAX_SIZE),
                paper = paper,
                template = t,
                line = Paper.migrateLine(paper, o.optInt("l", Paper.autoLineColor(paper))),
                spacing = o.optDouble("s", t.defaultSpacing.toDouble()).toFloat(),
                lineWidth = o.optDouble("lw", 0.6).toFloat(),
                opts = o.optInt("o", OPT_MARGIN),
                pdf = if (o.has("pa")) PdfRef(o.getString("pa"), o.optInt("pn", 0)) else null,
            )
        } catch (_: Exception) {
            PageSpec()
        }

        fun of(format: PageFormat, landscape: Boolean = false, template: Template = Template.BLANK, paper: Int = Paper.WHITE): PageSpec {
            val (w, h) = if (landscape) format.landscape() else format.w to format.h
            return PageSpec(w, h, paper, template, Paper.autoLineColor(paper), template.defaultSpacing)
        }
    }
}

/** Kolory papieru i dobór koloru linii wzoru do jasności tła. */
object Paper {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val IVORY = 0xFFFBF5E4.toInt()
    const val YELLOW = 0xFFFFF6BF.toInt()
    const val GRAY = 0xFFEDEDED.toInt()
    const val BLUE = 0xFFE8F1FB.toInt()
    const val GREEN = 0xFFE7F4E8.toInt()
    const val PINK = 0xFFFCEBEF.toInt()
    const val DARK = 0xFF1E1F22.toInt()
    const val BLACK = 0xFF000000.toInt()

    val presets: List<Int> = listOf(WHITE, IVORY, YELLOW, GRAY, BLUE, GREEN, PINK, DARK, BLACK)

    fun luminance(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
    }

    fun isDark(argb: Int): Boolean = luminance(argb) < 0.45f

    /** Delikatna linia o chłodnym odcieniu: ciemniejsza od jasnego papieru, jaśniejsza od ciemnego. */
    fun autoLineColor(paper: Int): Int =
        if (isDark(paper)) mix(paper, 0xFFA7B4C8.toInt(), 0.20f) else mix(paper, 0xFF3F5F8F.toInt(), 0.22f)

    /** Kolor automatyczny sprzed AES-003 (mocniejszy) — strony z nim zapisane trafiają przy wczytaniu na obecny. */
    private fun legacyAutoLineColor(paper: Int): Int =
        if (isDark(paper)) mix(paper, 0xFFA7B4C8.toInt(), 0.28f) else mix(paper, 0xFF3F5F8F.toInt(), 0.30f)

    /** Linia zapisana jako dawny kolor automatyczny = użytkownik jej nie ustawiał → delikatniejszy kolor obecny. */
    fun migrateLine(paper: Int, line: Int): Int = if (line == legacyAutoLineColor(paper)) autoLineColor(paper) else line

    /** Mieszanie kolorów w ARGB; [t] = udział [b]. Alfa wyniku jest pełna. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + (y - x) * t).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
