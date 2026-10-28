package com.mdev.mnotes.data

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pliki z `src/test/resources/samsung/` powstają skryptami skilla `samsung-notes-do-mnotes`
 * (`narzedzia/generuj-fixture-samsung.py`, z syntetycznego .sdocx). Test pilnuje, że aplikacja czyta kreski i tło
 * tak, jak skill zakłada: pióra, szerokości, współczynniki grubości, kolory i kratkę.
 */
class SkillSamsungFormatTest {
    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/samsung/$name")) { "brak zasobu $name" }.use { it.readBytes() }

    private val meta = NotebookMeta.fromJson(String(resource("meta.json"), Charsets.UTF_8))

    private fun strokes(page: Int): List<Stroke> =
        PageCodec.decode(resource("strona$page.mnp")).elements.filterIsInstance<Stroke>()

    private fun Stroke.pressures(): List<Float> = (0 until count).map { pts[3 * it + 2] }

    @Test fun notebookHasTwoPagesAndTitle() {
        assertEquals(2, meta.pages.size)
        assertEquals("Próbka Samsung ąę", meta.title)
    }

    @Test fun firstPageKeepsSamsungGridAndPaper() {
        val spec = meta.pages[0].spec
        assertEquals(Template.GRID, spec.template)
        assertEquals(38.25f, spec.spacing, 0.01f)
        assertEquals(1.0f, spec.lineWidth, 0.01f)
        assertEquals(0xFFE6E6E6.toInt(), spec.paper)
        assertEquals(0xFFC8C8C8.toInt(), spec.line)
        assertNull(spec.pdf)
        // Strona powiększona, by wyśrodkowana siatka mNotes zgadzała się z siatką Notatek.
        assertTrue(spec.w >= 600f && spec.h >= 848f)
    }

    @Test fun secondPageIsPlainPaper() {
        val spec = meta.pages[1].spec
        assertEquals(Template.BLANK, spec.template)
        assertEquals(0xFFFCFCFC.toInt(), spec.paper)
        assertEquals(600f, spec.w, 0.01f)
    }

    @Test fun firstPageHoldsFountainBrushAndHighlighter() {
        val st = strokes(0)
        assertEquals(
            listOf(PenKind.FOUNTAIN, PenKind.FOUNTAIN, PenKind.BRUSH, PenKind.HIGHLIGHTER),
            st.map { it.pen },
        )
        assertTrue("kreski mają być edytowalne, nie zablokowane", st.none { it.locked })
    }

    @Test fun fountainWidthComesFromSizeAndPressureFromSpeed() {
        val (slow, fast) = strokes(0)
        assertEquals(16.77f * 0.375f, slow.width, 0.01f)
        val slowMid = slow.pressures().let { it.subList(it.size / 3, it.size - it.size / 3) }
        val fastMid = fast.pressures().let { it.subList(it.size / 3, it.size - it.size / 3) }
        assertTrue("wolne pisanie ma pełną grubość: $slowMid", slowMid.all { it > 0.95f })
        assertTrue("szybkie pisanie ma ok. 0,55-0,6: $fastMid", fastMid.all { it in 0.5f..0.65f })
        assertEquals(0xFF000000.toInt(), slow.color)
        assertEquals(0xFF1565C0.toInt(), fast.color)
    }

    @Test fun brushThickensWithPressure() {
        val brush = strokes(0)[2]
        assertEquals(19.88f * 0.375f * 1.2f, brush.width, 0.01f)
        val p = brush.pressures()
        assertTrue(p.last() > p.first() + 0.3f)
        assertTrue(p.all { it in 0.04f..1f })
    }

    @Test fun highlighterHasConstantWidthAndOpaqueColor() {
        val marker = strokes(0)[3]
        assertEquals(31.0f * 0.375f, marker.width, 0.01f)
        assertTrue(marker.pressures().all { it == 1f })
        assertEquals(0xFF, (marker.color ushr 24) and 0xFF)
    }

    @Test fun strokesStayInsideThePage() {
        for ((i, page) in meta.pages.withIndex()) {
            for (s in strokes(i)) for (k in 0 until s.count) {
                assertTrue(s.pts[3 * k] in 0f..page.spec.w)
                assertTrue(s.pts[3 * k + 1] in 0f..page.spec.h)
            }
        }
    }

    @Test fun secondPageHoldsOneFountainStroke() {
        val st = strokes(1)
        assertEquals(1, st.size)
        assertEquals(PenKind.FOUNTAIN, st[0].pen)
        assertEquals(0xFF2E7D32.toInt(), st[0].color)
    }
}
