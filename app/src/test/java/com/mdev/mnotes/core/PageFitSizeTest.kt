package com.mdev.mnotes.core

import com.mdev.mnotes.core.model.PageSpec
import org.junit.Assert.assertEquals
import org.junit.Test

/** 3c-A16: rozmiar strony z importu PDF/obrazu taki, jaki przetrwa ponowny odczyt (PageSpec.fromJson przycina 72..8000). */
class PageFitSizeTest {
    private fun survives(w: Float, h: Float) {
        val (fw, fh) = PageSpec.fitSize(w, h)
        val back = PageSpec.fromJson(PageSpec(w = fw, h = fh).toJson())
        assertEquals(fw, back.w, 0.01f); assertEquals(fh, back.h, 0.01f)
    }

    @Test fun ordinaryPageUnchanged() {
        assertEquals(595f to 842f, PageSpec.fitSize(595f, 842f))
    }

    @Test fun longPdfPageScaledKeepingProportions() {
        val (w, h) = PageSpec.fitSize(595f, 12000f)
        assertEquals(8000f, h, 0.01f)
        assertEquals(595f / 12000f, w / h, 1e-4f)
        survives(595f, 12000f)
    }

    @Test fun thinPanoramaScaledUp() {
        val (w, h) = PageSpec.fitSize(842f, 842f / 15f)
        assertEquals(72f, h, 0.01f)
        assertEquals(15f, w / h, 1e-3f)
        survives(842f, 842f / 15f)
    }

    @Test fun extremeAndBrokenSizesStayInBounds() {
        survives(10f, 100000f)
        assertEquals(72f, PageSpec.fitSize(10f, 100000f).first, 0.01f)
        assertEquals(PageSpec.fitSize(0f, 5f), PageSpec.fitSize(Float.NaN, 1f))
    }
}
