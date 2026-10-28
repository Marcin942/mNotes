package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.geom.ConstructOps
import com.mdev.mnotes.core.guide.Guide
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TapeEl
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.text.AwtTextMeasure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F20 / 2b-A11: zeszyt-przewodnik — strony mieszczą się na kartce, układ się nie nakłada, zakres obejmuje nowe funkcje. */
class GuideTest {

    private val variants = listOf(true to false, false to false, true to true, false to true)
    private val m = AwtTextMeasure
    private fun pages(pl: Boolean, lab: Boolean = false) = Guide.pages(pl, lab, m)

    @Test fun pagesFitOnPaperAndSurviveCodec() {
        for ((pl, lab) in variants) {
            val pages = pages(pl, lab)
            assertTrue(pages.size >= 15)
            for (p in pages) {
                for (e in p.elements) {
                    val b = e.bounds
                    assertTrue("${p.title}: $e poza stroną: $b", b.l >= 0f && b.t >= 0f && b.r <= p.spec.w && b.b <= p.spec.h)
                }
                assertEquals(p.elements.size, PageCodec.decode(PageCodec.encode(PageContent.of(p.elements))).size)
            }
        }
    }

    /** Tekst kończy się nad ilustracją, a ilustracja nad stopką — nic na siebie nie wchodzi. */
    @Test fun textArtAndFooterDoNotOverlap() {
        for ((pl, lab) in variants) for (p in pages(pl, lab).drop(1)) {
            assertTrue("${p.title}: tekst ${p.textBottom} > ilustracja ${p.artTop}", p.textBottom <= p.artTop)
            val footer = p.elements.takeLast(3).toSet()
            for (e in p.elements) {
                if (e in footer) continue
                assertTrue("${p.title}: $e schodzi na stopkę (${e.bounds.b})", e.bounds.b <= Guide.FOOT + 1f)
                // Ilustracja (wszystko po tekście) zaczyna się pod tekstem.
                if (e.bounds.t > p.textBottom + 1f) assertTrue(e.bounds.t >= p.artTop - 24f)
            }
        }
    }

    /**
     * 2c-A14: przy prawdziwej metryce czcionki każde pole ma wysokość swojego tekstu (nie szacunek z liczby znaków, który
     * przy szerszej czcionce wpychał tekst na ilustrację), etykiety się nie zawijają i żadne dwa teksty na siebie nie wchodzą.
     */
    @Test fun noOverlapWithRealFontMetrics() {
        for ((pl, lab) in variants) for (p in pages(pl, lab)) {
            val boxes = p.elements.filterIsInstance<TextBox>()
            val rects = boxes.map { t ->
                val need = m.textHeight(t, t.w)
                assertTrue("${p.title}: „${t.text.take(30)}” potrzebuje $need pt, pole ${t.h}", need <= t.h + 0.5f)
                assertTrue("${p.title}: „${t.text.take(30)}” pole ${t.h} pt przy tekście $need pt", t.h - need < 1f)
                val top = t.cy - t.h / 2
                floatArrayOf(t.cx - t.w / 2, top, t.cx + t.w / 2, top + need)
            }
            for (i in rects.indices) for (j in i + 1 until rects.size) {
                val a = rects[i]; val b = rects[j]
                val hit = a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]
                assertFalse("${p.title}: „${boxes[i].text.take(24)}” nachodzi na „${boxes[j].text.take(24)}”", hit)
            }
        }
    }

    /** 2c-A14: każdy rozdział kończy się stroną „Sprawdź się”, a odpowiedzi mają tylko te strony. */
    @Test fun everyChapterEndsWithCheckYourself() {
        for ((pl, lab) in variants) {
            val ps = pages(pl, lab).drop(1)
            val chapters = ps.map { it.chapter }.distinct()
            assertEquals(listOf(0, 1), chapters)
            for (c in chapters) {
                val last = ps.last { it.chapter == c }
                assertTrue(last.title, last.title.startsWith(if (pl) "Sprawdź się" else "Try it yourself"))
                assertTrue(last.title, last.answers.size >= 4)
            }
            assertEquals(2, ps.count { it.answers.isNotEmpty() })
        }
    }

    /** 2c-A14: każda odpowiedź leży pod zakrytą taśmą — taśma obejmuje cały jej tekst i jest rysowana nad nią. */
    @Test fun everyAnswerLiesUnderTape() {
        for ((pl, lab) in variants) for (p in pages(pl, lab)) for (a in p.answers) {
            val at = p.elements.indexOf(a)
            assertTrue(at >= 0)
            val l = a.cx - a.w / 2; val t = a.cy - a.h / 2; val r = a.cx + a.w / 2; val b = t + m.textHeight(a, a.w)
            val tape = p.elements.drop(at + 1).filterIsInstance<TapeEl>().firstOrNull { e ->
                e.hidden && e.bounds.l <= l && e.bounds.t <= t && e.bounds.r >= r && e.bounds.b >= b
            }
            assertNotNull("${p.title}: odpowiedź „${a.text}” bez taśmy", tape)
            assertTrue(a.text.isNotBlank())
        }
    }

    @Test fun bothLanguagesHaveTheSameStructure() {
        for (lab in listOf(false, true)) {
            val a = pages(true, lab); val b = pages(false, lab)
            assertEquals(a.size, b.size)
            for (i in a.indices) {
                assertEquals(a[i].title, a[i].elements.size, b[i].elements.size)
                assertEquals(a[i].title, a[i].answers.size, b[i].answers.size)
            }
        }
        assertEquals("Przewodnik po mNotes", pages(true)[0].title)
    }

    /** Okładka ma spis treści: każda lekcja z numerem strony, na której naprawdę jest. */
    @Test fun contentsListEveryLessonWithItsPage() {
        val pages = pages(true)
        val cover = pages[0].elements.filterIsInstance<TextBox>().map { it.text }
        for (i in 1 until pages.size) {
            val at = cover.indexOf(pages[i].title)
            assertTrue("brak w spisie: ${pages[i].title}", at >= 0)
            assertEquals("${i + 1}", cover[at + 1])
        }
        // Stopka z numerem strony na każdej stronie.
        pages.forEachIndexed { i, p -> assertTrue(p.elements.filterIsInstance<TextBox>().any { it.text == "${i + 1} / ${pages.size}" }) }
    }

    /** 2b-A11, 2c-A14: zakres rozszerzony o funkcje zaawansowane — każda ma swoją stronę. */
    @Test fun coversAdvancedFeatures() {
        val titles = pages(true).joinToString("\n") { it.title }
        for (k in listOf("Krzywe", "Geometria", "Układ współrzędnych", "Stanowiska", "Naklejki", "Wycinek", "wykładu", "ciemny", "Eksport", "greckie",
            "Profile pracy", "Tablica", "Zdjęcie tablicy")) {
            assertTrue("brak tematu: $k", titles.contains(k))
        }
        // Kreda tylko w wariancie z laboratorium.
        fun all(lab: Boolean) = pages(true, lab).flatMap { p -> p.elements.filterIsInstance<TextBox>().map { it.text } }.joinToString("\n")
        assertTrue(all(true).contains("kred"))
        assertFalse(all(false).contains("kred"))
    }

    /** Każda lekcja ma ilustrację albo wzór do pracy piórem, a większość — kartę ze wskazówką. */
    @Test fun lessonsHaveArtAndTips() {
        val lessons = pages(true).drop(1)
        for (p in lessons) assertTrue(p.title, p.elements.any { it.bounds.t >= p.artTop - 1f && it.bounds.b <= Guide.FOOT + 1f && (it is Stroke || it is FillEl) })
        assertTrue(lessons.count { p -> p.elements.filterIsInstance<TextBox>().any { it.text == "WSKAZÓWKA" } } >= lessons.size - 1)
    }

    /** A2-11: ostatnia strona „Sprawdź się” — zadania działają na jej własnych wzorach, a odpowiedzi zgadzają się z rysunkiem. */
    @Test fun checkYourselfPageExercisesWork() {
        for (pl in listOf(true, false)) {
            val last = pages(pl).last()
            assertEquals(if (pl) "Sprawdź się: zaawansowane" else "Try it yourself: advanced", last.title)
            val strokes = last.elements.filterIsInstance<Stroke>()
            val rings = strokes.filter { it.ellipse }
            assertEquals(2, rings.size)
            val cross = ConstructOps.ops(rings).first { it.id == "xcc" }.build(0, 1f)
            // Dwie kropki i prosta przez nie (2c-A8: menu rysuje ją jak narzędzie).
            assertEquals(3, cross.filterIsInstance<Stroke>().size)
            val tri = strokes.single { it.poly }
            assertTrue(ConstructOps.ops(listOf(tri)).any { it.id == "circum" })
            // Odpowiedź 1: trójkąt ostrokątny (każdy kąt < 90°), więc środek okręgu opisanego leży wewnątrz.
            val v = (0 until 3).map { tri.pts[3 * it] to tri.pts[3 * it + 1] }
            val acute = (0 until 3).all { i ->
                val (ax, ay) = v[i]; val (bx, by) = v[(i + 1) % 3]; val (cx, cy) = v[(i + 2) % 3]
                (bx - ax) * (cx - ax) + (by - ay) * (cy - ay) > 0f
            }
            assertTrue(acute)
            assertTrue(last.answers[0].text.startsWith(if (pl) "Tak" else "Yes"))
            // Odpowiedź 3: równe promienie, więc prosta PQ przechodzi przez środek odcinka środków.
            assertEquals(rings[0].bounds.r - rings[0].bounds.l, rings[1].bounds.r - rings[1].bounds.l, 0.01f)
        }
    }
}
