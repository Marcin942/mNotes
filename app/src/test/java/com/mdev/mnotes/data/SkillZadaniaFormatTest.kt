package com.mdev.mnotes.data

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.PdfRef
import com.mdev.mnotes.core.model.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pliki z `src/test/resources/zadania/` powstają skryptami skilla `zadania-do-mnotes`
 * (`skills/generuj-fixture.py`). Test pilnuje, że aplikacja czyta je tak, jak skill zakłada.
 */
class SkillZadaniaFormatTest {
    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/zadania/$name")) { "brak zasobu $name" }.use { it.readBytes() }

    private val meta = NotebookMeta.fromJson(String(resource("meta.json"), Charsets.UTF_8))

    @Test fun notebookHasOriginalPageAndGridPage() {
        assertEquals(2, meta.pages.size)
        assertEquals("Zadania ąę", meta.title)
        assertEquals(PdfRef("orig.pdf", 0), meta.pages[0].spec.pdf)
    }

    @Test fun taskPageMatchesDefaultA5Grid() {
        val expected = PageSpec.of(PageFormat.A5, false, Template.GRID)
        assertEquals(expected, meta.pages[1].spec)
    }

    @Test fun taskPageHoldsTwoFullWidthUnlockedImages() {
        val content = PageCodec.decode(resource("strona1.mnp"))
        val images = content.elements.filterIsInstance<ImageEl>()
        assertEquals(2, images.size)
        for (im in images) {
            assertEquals(420f, im.w, 0.01f)
            assertEquals(210f, im.cx, 0.01f)
            assertFalse(im.locked)
        }
        assertEquals(setOf("a.png", "b.png"), images.map { it.asset }.toSet())
        assertTrue(images[0].cy < images[1].cy)
    }

    @Test fun originalPageIsEmpty() {
        assertNotNull(PageCodec.decode(resource("strona0.mnp")))
        assertTrue(PageCodec.decode(resource("strona0.mnp")).isEmpty)
    }
}
