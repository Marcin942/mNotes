package com.mdev.mnotes.core.layout

import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.PdfRef
import com.mdev.mnotes.core.model.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewModeTest {
    @Test fun factoryDefaultsAreHorizontalNotebooksAndVerticalPdf() {
        assertEquals(ViewMode.HORIZONTAL, ViewMode.resolve(ViewMode.DEFAULT, false, ViewMode.DEFAULT_NOTEBOOK, ViewMode.DEFAULT_PDF))
        assertEquals(ViewMode.VERTICAL, ViewMode.resolve(ViewMode.DEFAULT, true, ViewMode.DEFAULT_NOTEBOOK, ViewMode.DEFAULT_PDF))
    }

    @Test fun ownChoiceWinsOverDefaults() {
        assertEquals(ViewMode.SINGLE, ViewMode.resolve(ViewMode.SINGLE, true, ViewMode.HORIZONTAL, ViewMode.VERTICAL))
        assertEquals(ViewMode.VERTICAL, ViewMode.resolve(ViewMode.VERTICAL, false, ViewMode.HORIZONTAL, ViewMode.HORIZONTAL))
    }

    @Test fun settingsDefaultsApplyPerKind() {
        assertEquals(ViewMode.SINGLE, ViewMode.resolve(ViewMode.DEFAULT, false, ViewMode.SINGLE, ViewMode.VERTICAL))
        assertEquals(ViewMode.HORIZONTAL, ViewMode.resolve(ViewMode.DEFAULT, true, ViewMode.SINGLE, ViewMode.HORIZONTAL))
    }

    @Test fun brokenValuesFallBackToFactoryDefaults() {
        assertEquals(ViewMode.HORIZONTAL, ViewMode.resolve(7, false, 9, -3))
        assertEquals(ViewMode.VERTICAL, ViewMode.resolve(-5, true, 9, -3))
    }

    @Test fun recognizesImportedPdfPages() {
        val pdf = List(3) { PageSpec(pdf = PdfRef("a.pdf", it)) }
        assertTrue(ViewMode.looksLikePdf(pdf))
        assertFalse(ViewMode.looksLikePdf(emptyList()))
        assertFalse(ViewMode.looksLikePdf(listOf(PageSpec())))
        assertFalse(ViewMode.looksLikePdf(pdf + PageSpec()))
        assertFalse(ViewMode.looksLikePdf(listOf(PageSpec(pdf = PdfRef("a.pdf", 0)), PageSpec(pdf = PdfRef("b.pdf", 1)))))
        assertFalse(ViewMode.looksLikePdf(listOf(PageSpec(pdf = PdfRef("a.pdf", 0), template = Template.GRID))))
    }
}
