package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.layout.Board
import com.mdev.mnotes.core.model.DocEdit
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.core.model.UndoStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2b-A13: zeszyt „Tablica” — flaga strony zgodna wstecz, wzrost płótna, kadr eksportu. */
class BoardTest {

    @Test fun boardFlagLivesInExistingOptsField() {
        val s = Board.spec()
        assertTrue(s.board)
        assertEquals(Template.GRID, s.template)
        assertFalse(PageSpec().board)
        assertFalse(s.marginLine)   // tablica bez marginesu zeszytu
        // Zapis: ten sam JSON co zwykła strona, bit w polu „o”; tablica większa niż zwykły limit zostaje po odczycie.
        val big = s.withSize(20000f, 9000f)
        val back = PageSpec.fromJson(big.toJson())
        assertEquals(big, back)
        assertTrue(back.board)
        // Zwykła strona dalej przycinana do dawnego limitu (tak czyta ją też stara wersja).
        assertEquals(PageSpec.MAX_SIZE, PageSpec.fromJson(PageSpec().withSize(20000f, 9000f).toJson()).w, 0f)
        // Inne flagi przeżywają obok tablicy.
        assertTrue(PageSpec.fromJson(s.withInverted(true).toJson()).let { it.board && it.inverted })
    }

    @Test fun noGrowthWhileContentIsAwayFromEdges() {
        assertNull(Board.grow(3000f, 3000f, Bounds(1000f, 1000f, 2000f, 2000f)))
        assertNull(Board.grow(3000f, 3000f, Bounds.EMPTY))
        assertNull(Board.grow(3000f, 3000f, null))
    }

    @Test fun growsRightAndDownBySteps() {
        val g = Board.grow(3000f, 3000f, Bounds(1000f, 1000f, 2700f, 2600f))!!
        assertEquals(Board.Growth(3000f + Board.STEP, 3000f + Board.STEP, 0f, 0f), g)
    }

    @Test fun growsLeftAndUpWithShift() {
        val g = Board.grow(3000f, 3000f, Bounds(-40f, 300f, 800f, 900f))!!
        assertEquals(Board.STEP, g.dx, 0f)
        assertEquals(Board.STEP, g.dy, 0f)
        assertEquals(3000f + Board.STEP, g.w, 0f)
        // Po przesunięciu treść ma zapas z obu stron.
        assertTrue(-40f + g.dx >= Board.MARGIN)
        assertTrue(300f + g.dy >= Board.MARGIN)
    }

    @Test fun growthStopsAtMax() {
        val g = Board.grow(Board.MAX - 100f, 3000f, Bounds(-10f, 1000f, Board.MAX - 50f, 2000f))!!
        assertEquals(Board.MAX, g.w, 0f)
        // Najpierw dostaje dalsza strona (prawo), na lewą nie zostało miejsca.
        assertEquals(0f, g.dx, 0f)
        assertNull(Board.grow(Board.MAX, Board.MAX, Bounds(-10f, -10f, Board.MAX + 10f, Board.MAX + 10f)))
    }

    @Test fun shiftKeepsContentAndCodecRoundTrip() {
        val s = Stroke(1, PenKind.BALLPOINT, 0xFF000000.toInt(), 1.5f, floatArrayOf(-30f, 400f, 1f, 200f, 420f, 1f))
        val g = Board.grow(3000f, 3000f, s.bounds)!!
        val moved = s.transformed(Affine.translation(g.dx, g.dy))
        assertEquals(s.pts[0] + g.dx, moved.pts[0], 1e-3f)
        assertTrue(moved.bounds.l >= Board.MARGIN)
        // Treść tablicy zapisuje się jak każda strona.
        val back = PageCodec.decode(PageCodec.encode(PageContent.of(listOf(moved)))).elements.single() as Stroke
        assertEquals(moved.pts.toList(), back.pts.toList())
    }

    @Test fun boardHasExactlyOnePage() {
        assertFalse(Board.canAddPage(listOf(Board.spec())))
        assertTrue(Board.canAddPage(listOf(PageSpec(), PageSpec())))
        assertTrue(Board.canAddPage(emptyList()))
    }

    @Test fun leftGrowthUndoesWithoutJump() {
        val h = FakeHost().apply { addPage("b"); specs["b"] = Board.spec() }
        val s = Stroke(1, PenKind.BALLPOINT, 0xFF000000.toInt(), 1.5f, floatArrayOf(-500f, 1200f, 1f, 300f, 1300f, 1f))
        h.contents["b"] = PageContent.of(listOf(s))
        val parts = Board.growthEdits("b", h.spec("b"), h.contents.getValue("b").bounds)!!
        val undo = UndoStack()
        val growth = DocEdit.Group(parts).also { it.apply(h); undo.push(it) }
        val (dx, dy) = DocEdit.shiftOf(growth)!!
        assertTrue(dx >= 1000f); assertEquals(0f, dy, 0f)
        val moved = h.contents.getValue("b").elements.single() as Stroke
        assertEquals(-500f + dx, moved.pts[0], 1e-3f)
        assertEquals(Board.START + dx, h.spec("b").w, 0f)
        // Cofnięcie: treść, rozmiar i przesunięcie (dla widoku) wracają.
        val back = undo.undo(h)!!
        assertEquals(dx to dy, DocEdit.shiftOf(back))
        val again = h.contents.getValue("b").elements.single() as Stroke
        assertEquals(-500f, again.pts[0], 1e-3f); assertEquals(1200f, again.pts[1], 1e-3f)
        assertEquals(Board.START, h.spec("b").w, 0f)
    }

    @Test fun growthEditKeepsNoElementCopies() {
        val many = (0 until 2000).map { i ->
            Stroke(i, PenKind.BALLPOINT, 0xFF000000.toInt(), 1.5f, floatArrayOf(-200f + i, 900f, 1f, i.toFloat(), 950f, 1f))
        }
        val parts = Board.growthEdits("b", Board.spec(), PageContent.of(many).bounds)!!
        // Wpis cofania wzrostu ma stały rozmiar: rozmiar strony i dwie liczby, bez elementów.
        assertEquals(2, parts.size)
        assertTrue(parts.none { it is DocEdit.Elements })
        assertTrue(parts[1] is DocEdit.Shift)
        assertEquals(DocEdit.shiftOf(DocEdit.Group(listOf(DocEdit.Group(parts), DocEdit.Shift("b", 1f, 2f)))),
            (parts[1] as DocEdit.Shift).dx + 1f to 2f)
        // Wzrost tylko w prawo — bez przesunięcia.
        assertNull(DocEdit.shiftOf(DocEdit.Group(Board.growthEdits("b", Board.spec(), Bounds(1000f, 1000f, 2900f, 1100f))!!)))
    }

    @Test fun exportFrameHugsContent() {
        val f = Board.frame(Bounds(1000f, 1200f, 1400f, 1300f), 3000f, 3000f)!!
        assertEquals(Bounds(1000f - Board.FRAME_PAD, 1200f - Board.FRAME_PAD, 1400f + Board.FRAME_PAD, 1300f + Board.FRAME_PAD), f)
        // Przy krawędzi kadr nie wychodzi poza płótno.
        val e = Board.frame(Bounds(5f, 5f, 50f, 50f), 3000f, 3000f)!!
        assertEquals(0f, e.l, 0f); assertEquals(0f, e.t, 0f)
        assertNull(Board.frame(Bounds.EMPTY, 3000f, 3000f))
        assertNotNull(Board.focus(null, 3000f, 3000f))
        assertEquals(1500f to 1500f, Board.focus(Bounds.EMPTY, 3000f, 3000f))
        assertEquals(1200f to 1250f, Board.focus(Bounds(1000f, 1200f, 1400f, 1300f), 3000f, 3000f))
    }
}
