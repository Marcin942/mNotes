package com.mdev.mnotes.core

import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.stats.ActiveTime
import com.mdev.mnotes.core.stats.NotebookStats
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** FB7: statystyki zeszytu i czas aktywnego pisania — liczone lokalnie, zapis w jednym napisie preferencji. */
class NotebookStatsTest {
    private fun line(vararg xy: Float) = FloatArray(xy.size / 2 * 3) { i -> if (i % 3 == 2) 1f else xy[i / 3 * 2 + i % 3] }
    private fun stroke(pts: FloatArray, poly: Boolean = false) = Stroke(Ids.next(), PenKind.BALLPOINT, 0xFF000000.toInt(), 2f, pts, poly = poly)
    private fun text(s: String) = TextBox(Ids.next(), 50f, 50f, 100f, 30f, 0f, s, 12f, 0xFF000000.toInt())

    @Test fun countsSplitHandwritingShapesTextAndImages() {
        val c = NotebookStats.count(listOf(
            listOf(stroke(line(0f, 0f, 30f, 40f)), stroke(line(0f, 0f, 10f, 0f, 10f, 10f), poly = true), text("Twierdzenie Pitagorasa: a² + b² = c²")),
            emptyList(),
            listOf(ImageEl(Ids.next(), "tablica.jpg", 50f, 50f, 40f, 30f), text("—  …  ")),
        ))
        assertEquals(3, c.pages)
        assertEquals(1, c.strokes)
        assertEquals(1, c.shapes)
        assertEquals(2, c.textBoxes)
        assertEquals(1, c.images)
        // „a²”, „b²”, „c²” to słowa, „+” i „=” nie; pole z samą interpunkcją nie dokłada słów.
        assertEquals(5, c.words)
        // 50 pt (ukośna 30–40–50) + 20 pt (łamana).
        assertEquals(70f, c.inkPt, 1e-3f)
        assertEquals(70f * 0.0254f / 72f, c.inkMeters, 1e-6f)
    }

    @Test fun lengthIgnoresPressureAndSinglePoints() {
        assertEquals(0.0, NotebookStats.length(line(5f, 5f)), 0.0)
        assertEquals(0.0, NotebookStats.length(FloatArray(0)), 0.0)
        val p = line(0f, 0f, 3f, 4f)
        p[2] = 0.1f; p[5] = 0.9f
        assertEquals(5.0, NotebookStats.length(p), 1e-9)
    }

    @Test fun wordsCountTokensWithLetterOrDigit() {
        assertEquals(0, NotebookStats.words(""))
        assertEquals(0, NotebookStats.words("  \n "))
        assertEquals(3, NotebookStats.words("\\alpha  +  x^10\n2024"))
        assertEquals(2, NotebookStats.words("zad. 3 — ?"))
    }

    @Test fun creditCountsShortGapsAndCapsBreaks() {
        assertEquals(ActiveTime.FIRST_MS, ActiveTime.credit(0L, 1_000_000L))
        assertEquals(40_000L, ActiveTime.credit(1_000_000L, 1_040_000L))
        assertEquals(ActiveTime.IDLE_MS, ActiveTime.credit(1_000_000L, 1_000_000L + ActiveTime.IDLE_MS))
        // Przerwa (kawa, inny przedmiot) nie liczy się jako praca; zegar cofnięty też nie.
        assertEquals(ActiveTime.FIRST_MS, ActiveTime.credit(1_000_000L, 1_000_001L + ActiveTime.IDLE_MS))
        assertEquals(ActiveTime.FIRST_MS, ActiveTime.credit(1_000_000L, 999_000L))
    }

    @Test fun weekKeysSortLikeDatesAcrossYearBoundary() {
        assertEquals("2026-W44", ActiveTime.weekKey(LocalDate.of(2026, 10, 28)))
        // 1.01.2027 to piątek: należy jeszcze do 53. tygodnia 2026 według ISO.
        assertEquals("2026-W53", ActiveTime.weekKey(LocalDate.of(2027, 1, 1)))
        assertEquals("2027-W01", ActiveTime.weekKey(LocalDate.of(2027, 1, 4)))
        assertEquals("2026-W02", ActiveTime.weekKey(LocalDate.of(2026, 1, 7)))
        val keys = listOf(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 10, 28), LocalDate.of(2027, 1, 4)).map(ActiveTime::weekKey)
        assertEquals(keys, keys.sorted())
    }

    @Test fun addKeepsOnlyNewestWeeks() {
        var w = emptyMap<String, Long>()
        w = ActiveTime.add(w, "2026-W44", 60_000L)
        w = ActiveTime.add(w, "2026-W44", 30_000L)
        assertEquals(mapOf("2026-W44" to 90_000L), w)
        for (k in 30..43) w = ActiveTime.add(w, "2026-W$k", 1L, keep = 12)
        assertEquals(12, w.size)
        assertEquals("2026-W33", w.keys.first())
        assertEquals(90_000L, w["2026-W44"])
    }

    @Test fun recentFillsMissingWeeksWithZero() {
        val r = ActiveTime.recent(mapOf("2026-W44" to 5L, "2026-W42" to 7L, "2026-W30" to 9L), LocalDate.of(2026, 10, 28), 4)
        assertEquals(listOf("2026-W41" to 0L, "2026-W42" to 7L, "2026-W43" to 0L, "2026-W44" to 5L), r)
    }

    @Test fun encodeDecodeRoundTripAndSkipsDamage() {
        val all = mapOf("nb-1" to mapOf("2026-W43" to 360_000L, "2026-W44" to 125_000L), "nb-2" to mapOf("2026-W44" to 1L), "pusty" to emptyMap())
        val s = ActiveTime.encode(all)
        assertEquals(all - "pusty", ActiveTime.decode(s))
        val damaged = s + "\nbez-tabulatora\n\tbez-id=5\nnb-3\t2026-W44=abc,2026-W43=-4,2026-W42=10,=7"
        assertEquals((all - "pusty") + ("nb-3" to mapOf("2026-W42" to 10L)), ActiveTime.decode(damaged))
        assertEquals(emptyMap<String, Map<String, Long>>(), ActiveTime.decode(""))
    }

    @Test fun formatReadsLikeAClock() {
        assertEquals("< 1 min", ActiveTime.format(59_999L))
        assertEquals("12 min", ActiveTime.format(12 * 60_000L + 59_000L))
        assertEquals("1 h 05 min", ActiveTime.format(65 * 60_000L))
        assertEquals("27 h 00 min", ActiveTime.format(27 * 3_600_000L))
    }
}
