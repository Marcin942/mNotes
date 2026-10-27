package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.geom.ConstructOps
import com.mdev.mnotes.core.guide.Guide
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F20 / 2b-A11: zeszyt-przewodnik — strony mieszczą się na kartce, układ się nie nakłada, zakres obejmuje nowe funkcje. */
class GuideTest {

    private val variants = listOf(true to false, false to false, true to true, false to true)

    @Test fun pagesFitOnPaperAndSurviveCodec() {
        for ((pl, lab) in variants) {
            val pages = Guide.pages(pl, lab)
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
        for ((pl, lab) in variants) for (p in Guide.pages(pl, lab).drop(1)) {
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

    @Test fun bothLanguagesHaveTheSameStructure() {
        for (lab in listOf(false, true)) {
            val a = Guide.pages(true, lab); val b = Guide.pages(false, lab)
            assertEquals(a.size, b.size)
            for (i in a.indices) assertEquals(a[i].title, a[i].elements.size, b[i].elements.size)
        }
        assertEquals("Przewodnik po mNotes", Guide.pages(true)[0].title)
    }

    /** Okładka ma spis treści: każda lekcja z numerem strony, na której naprawdę jest. */
    @Test fun contentsListEveryLessonWithItsPage() {
        val pages = Guide.pages(true)
        val cover = pages[0].elements.filterIsInstance<TextBox>().map { it.text }
        for (i in 1 until pages.size) {
            val at = cover.indexOf(pages[i].title)
            assertTrue("brak w spisie: ${pages[i].title}", at >= 0)
            assertEquals("${i + 1}", cover[at + 1])
        }
        // Stopka z numerem strony na każdej stronie.
        pages.forEachIndexed { i, p -> assertTrue(p.elements.filterIsInstance<TextBox>().any { it.text == "${i + 1} / ${pages.size}" }) }
    }

    /** 2b-A11: zakres rozszerzony o funkcje zaawansowane — każda ma swoją stronę. */
    @Test fun coversAdvancedFeatures() {
        val titles = Guide.pages(true).joinToString("\n") { it.title }
        for (k in listOf("Krzywe", "Geometria", "Układ współrzędnych", "Stanowiska", "Naklejki", "Wycinek", "wykładu", "ciemny", "Eksport", "greckie")) {
            assertTrue("brak tematu: $k", titles.contains(k))
        }
        // Kreda tylko w wariancie z laboratorium.
        fun all(lab: Boolean) = Guide.pages(true, lab).flatMap { p -> p.elements.filterIsInstance<TextBox>().map { it.text } }.joinToString("\n")
        assertTrue(all(true).contains("kred"))
        assertFalse(all(false).contains("kred"))
    }

    /** Każda lekcja ma ilustrację albo wzór do pracy piórem, a większość — kartę ze wskazówką. */
    @Test fun lessonsHaveArtAndTips() {
        val lessons = Guide.pages(true).drop(1)
        for (p in lessons) assertTrue(p.title, p.elements.any { it.bounds.t >= p.artTop - 1f && it.bounds.b <= Guide.FOOT + 1f && (it is Stroke || it is FillEl) })
        assertTrue(lessons.count { p -> p.elements.filterIsInstance<TextBox>().any { it.text == "WSKAZÓWKA" } } >= lessons.size - 1)
    }

    @Test fun textHeightGrowsWithLength() {
        assertTrue(Guide.textHeight("x".repeat(400), 11f, 500f) > Guide.textHeight("krótko", 11f, 500f))
    }

    /** A2-11: ostatnia strona „Sprawdź się” — zadania działają na jej własnych wzorach (okręgi, trójkąt). */
    @Test fun checkYourselfPageExercisesWork() {
        for (pl in listOf(true, false)) {
            val last = Guide.pages(pl).last()
            assertEquals(if (pl) "Sprawdź się" else "Try it yourself", last.title)
            val strokes = last.elements.filterIsInstance<Stroke>()
            val rings = strokes.filter { it.ellipse }
            assertEquals(2, rings.size)
            val cross = ConstructOps.ops(rings).first { it.id == "xcc" }.build(0, 1f)
            assertEquals(2, cross.filterIsInstance<Stroke>().size)
            val tri = strokes.single { it.poly }
            assertTrue(ConstructOps.ops(listOf(tri)).any { it.id == "circum" })
        }
    }
}
