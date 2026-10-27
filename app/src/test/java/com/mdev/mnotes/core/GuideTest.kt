package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.guide.Guide
import com.mdev.mnotes.core.model.PageContent
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
}
