package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.DocLayout
import com.mdev.mnotes.core.layout.LayoutMode
import com.mdev.mnotes.core.layout.PageOverflow
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F10: tekst wychodzący za ostatnią stronę albo zaczęty za nią zakłada nową stronę. */
class PageOverflowTest {
    private val a5 = PageSpec.of(PageFormat.A5)

    @Test fun onlyLastPageOverflowsNearBottom() {
        assertTrue(PageOverflow.textOverflows(true, 600f, 595f))
        assertTrue(PageOverflow.textOverflows(true, 593f, 595f))
        assertFalse(PageOverflow.textOverflows(true, 500f, 595f))
        assertFalse(PageOverflow.textOverflows(false, 700f, 595f))
    }

    @Test fun tapBelowLastPageInVerticalLayout() {
        val l = DocLayout.of(listOf(a5, a5), LayoutMode.VERTICAL, gap = 14f, pad = 20f)
        val last = l.rect(1)
        assertTrue(PageOverflow.beyondEnd(l, last.l + 100f, last.b + 50f))
        assertFalse(PageOverflow.beyondEnd(l, last.l + 100f, last.b - 50f))      // na stronie
        assertFalse(PageOverflow.beyondEnd(l, last.l + 100f, last.b + 2000f))    // daleko za końcem
        assertFalse(PageOverflow.beyondEnd(l, last.l - 10f, last.b + 50f))       // obok, nie pod
        val p = PageOverflow.onNewPage(l, last.l + 100f, last.b + 14f + 30f, a5.w, a5.h)
        assertEquals(100f, p[0], 1e-3f)
        assertEquals(30f, p[1], 1e-3f)
        // Dotknięcie w przerwie trafia na górny margines nowej strony.
        assertEquals(PageOverflow.MARGIN, PageOverflow.onNewPage(l, last.l + 100f, last.b + 3f, a5.w, a5.h)[1], 1e-3f)
    }

    @Test fun tapRightOfLastPageInHorizontalLayout() {
        val l = DocLayout.of(listOf(a5, a5), LayoutMode.HORIZONTAL, gap = 14f, pad = 20f)
        val last = l.rect(1)
        assertTrue(PageOverflow.beyondEnd(l, last.r + 40f, last.t + 100f))
        val p = PageOverflow.onNewPage(l, last.r + 14f + 40f, last.t + 100f, a5.w, a5.h)
        assertEquals(40f, p[0], 1e-3f)
        assertEquals(100f, p[1], 1e-3f)
    }
}
