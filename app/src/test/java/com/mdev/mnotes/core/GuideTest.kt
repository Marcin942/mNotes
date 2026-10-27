package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.geom.ConstructOps
import com.mdev.mnotes.core.guide.Guide
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F20: zeszyt-przewodnik — strony mieszczą się na kartce i zapisują się jak zwykłe strony. */
class GuideTest {

    @Test fun pagesFitOnPaperAndSurviveCodec() {
        for (pl in listOf(true, false)) {
            val pages = Guide.pages(pl)
            assertTrue(pages.size >= 6)
            for (p in pages) {
                assertTrue(p.elements.first() is TextBox)
                for (e in p.elements) {
                    val b = e.bounds
                    assertTrue("$e poza stroną: $b", b.l >= 0f && b.t >= 0f && b.r <= p.spec.w && b.b <= p.spec.h)
                }
                assertEquals(p.elements.size, PageCodec.decode(PageCodec.encode(PageContent.of(p.elements))).size)
            }
        }
    }

    @Test fun bothLanguagesHaveTheSameStructure() {
        val a = Guide.pages(true); val b = Guide.pages(false)
        assertEquals(a.size, b.size)
        for (i in a.indices) assertEquals(a[i].elements.size, b[i].elements.size)
        assertTrue((a[0].elements[0] as TextBox).text.startsWith("Witaj"))
    }

    @Test fun textHeightGrowsWithLength() {
        assertTrue(Guide.textHeight("x".repeat(400), 11f, 500f) > Guide.textHeight("krótko", 11f, 500f))
    }

    /** A2-11: ostatnia strona „Sprawdź się” — zadania działają na jej własnych wzorach (okręgi, trójkąt). */
    @Test fun checkYourselfPageExercisesWork() {
        for (pl in listOf(true, false)) {
            val last = Guide.pages(pl).last()
            assertEquals(if (pl) "Sprawdź się" else "Try it yourself", (last.elements[0] as TextBox).text)
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
