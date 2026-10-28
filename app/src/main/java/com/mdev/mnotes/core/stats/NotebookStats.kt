package com.mdev.mnotes.core.stats

import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.hypot

/**
 * FB7: statystyki zeszytu — liczone na urządzeniu z treści stron i z odstępów między kreskami, bez telemetrii.
 * Czas pisania leży w preferencjach aplikacji (nie w pliku zeszytu), więc format `.mnotes` się nie zmienia.
 */
object NotebookStats {

    /** Podsumowanie treści; [inkPt] to łączna długość kresek w punktach strony (1 pt = 1/72 cala). */
    class Counts(
        val pages: Int, val strokes: Int, val shapes: Int, val textBoxes: Int,
        val words: Int, val images: Int, val inkPt: Float,
    ) {
        /** Długość „atramentu” w metrach, jak na kartce w skali 1:1. */
        val inkMeters: Float get() = inkPt * 0.0254f / 72f
    }

    fun count(pages: List<List<Element>>): Counts {
        var strokes = 0; var shapes = 0; var texts = 0; var words = 0; var images = 0; var ink = 0.0
        for (els in pages) for (e in els) when (e) {
            is Stroke -> {
                // Kształt z uchwytami (łamana, elipsa, krzywa) to jedna konstrukcja, nie „pismo” — liczony osobno.
                if (e.poly || e.ellipse || e.ctrl != null) shapes++ else strokes++
                ink += length(e.pts)
            }
            is TextBox -> { texts++; words += words(e.text) }
            is ImageEl -> images++
            else -> {}
        }
        return Counts(pages.size, strokes, shapes, texts, words, images, ink.toFloat())
    }

    /** Długość łamanej z trójek x, y, p. */
    fun length(pts: FloatArray): Double {
        var s = 0.0
        var i = 3
        while (i + 1 < pts.size) { s += hypot((pts[i] - pts[i - 3]).toDouble(), (pts[i + 1] - pts[i - 2]).toDouble()); i += 3 }
        return s
    }

    /**
     * Słowa w polu tekstu: ciągi zawierające literę albo cyfrę (same znaki interpunkcji i „—” się nie liczą);
     * `\alpha` i `x²` liczą się jak jedno słowo, bo w notatkach z matematyki to jednostka treści.
     */
    fun words(s: String): Int = s.split(Regex("\\s+")).count { w -> w.any { it.isLetterOrDigit() } }
}

/**
 * FB7: czas aktywnego pisania. Odstęp między kolejnymi kreskami krótszy niż [IDLE_MS] liczy się jako praca (myślenie
 * nad zadaniem też), dłuższy to przerwa — wtedy kreska dolicza tylko [FIRST_MS]. Sumy per tydzień ISO, żeby było
 * widać „ile w tym tygodniu” bez śledzenia dnia po dniu.
 */
object ActiveTime {
    const val IDLE_MS = 120_000L
    const val FIRST_MS = 5_000L
    /** Tyle ostatnich tygodni zostaje w zapisie każdego zeszytu. */
    const val KEEP_WEEKS = 12

    /** Ile doliczyć za kreskę skończoną w [now], gdy poprzednia była w [last] (0 — pierwsza w tym otwarciu). */
    fun credit(last: Long, now: Long): Long =
        if (last <= 0L || now < last || now - last > IDLE_MS) FIRST_MS else now - last

    /** Tydzień ISO jako klucz „2026-W44”; zero wiodące, żeby klucze sortowały się jak daty (Locale.ROOT: cyfry ASCII). */
    fun weekKey(d: LocalDate): String =
        String.format(Locale.ROOT, "%d-W%02d", d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    /** [n] ostatnich tygodni do [today] włącznie, od najstarszego; tydzień bez pisania ma 0 (słupek w oknie jest pusty). */
    fun recent(weeks: Map<String, Long>, today: LocalDate, n: Int): List<Pair<String, Long>> =
        (n - 1 downTo 0).map { weekKey(today.minusWeeks(it.toLong())) }.map { it to (weeks[it] ?: 0L) }

    /** Dolicza [ms] do tygodnia [week] i zostawia [keep] najnowszych tygodni. */
    fun add(weeks: Map<String, Long>, week: String, ms: Long, keep: Int = KEEP_WEEKS): Map<String, Long> {
        val m = weeks.toMutableMap()
        m[week] = (m[week] ?: 0L) + ms
        return m.toSortedMap().entries.toList().takeLast(keep).associate { it.key to it.value }
    }

    /**
     * Zapis wszystkich zeszytów w jednym napisie preferencji: wiersz na zeszyt, `id<TAB>2026-W43=360000,2026-W44=125000`.
     * Odczyt pomija uszkodzone kawałki zamiast tracić całość.
     */
    fun encode(all: Map<String, Map<String, Long>>): String =
        all.entries.filter { it.value.isNotEmpty() }.joinToString("\n") { (id, w) ->
            id + "\t" + w.entries.joinToString(",") { "${it.key}=${it.value}" }
        }

    fun decode(s: String): Map<String, Map<String, Long>> {
        val out = LinkedHashMap<String, Map<String, Long>>()
        for (line in s.split('\n')) {
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val weeks = line.substring(tab + 1).split(',').mapNotNull { e ->
                val i = e.indexOf('=')
                val ms = if (i > 0) e.substring(i + 1).toLongOrNull() else null
                if (ms == null || ms < 0) null else e.substring(0, i) to ms
            }.toMap()
            if (weeks.isNotEmpty()) out[line.substring(0, tab)] = weeks
        }
        return out
    }

    /** „1 h 05 min”, „12 min”, „< 1 min” — do okna statystyk. */
    fun format(ms: Long): String {
        val min = ms / 60_000L
        return when {
            min < 1 -> "< 1 min"
            min < 60 -> "$min min"
            else -> String.format(Locale.ROOT, "%d h %02d min", min / 60, min % 60)
        }
    }
}
