package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.shapes.Bezier
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** F17: punkty kontrolne krzywej w pliku — zgodnie wstecz (gęsta łamana jak dawniej, dopisek na końcu rekordu). */
class BezierCodecTest {
    private val ctrl = floatArrayOf(10f, 10f, 10f, 60f, 80f, 60f, 80f, 10f, 80f, -20f, 120f, -20f, 140f, 10f)

    private fun curve() = Stroke(Ids.next(), PenKind.FOUNTAIN, 0xFF223344.toInt(), 1.4f, Bezier.sample(ctrl), ctrl = ctrl)

    @Test fun ctrlRoundTripsAndNeighboursSurvive() {
        val tb = TextBox(Ids.next(), 50f, 50f, 40f, 12f, 0f, "po krzywej", 9f, 0xFF000000.toInt())
        val back = PageCodec.decode(PageCodec.encode(PageContent.of(listOf(curve(), tb))))
        assertEquals(2, back.size)
        val s = back.elements[0] as Stroke
        assertNotNull(s.ctrl)
        assertArrayEquals(ctrl, s.ctrl, 0.01f)
        assertEquals("po krzywej", (back.elements[1] as TextBox).text)
    }

    @Test fun densePointsAreTheSameAsWithoutCtrl() {
        // Starsza wersja czyta tylko łamaną: musi być taka sama jak z krzywą.
        val c = curve()
        val plain = Stroke(c.id, c.pen, c.color, c.width, c.pts)
        val a = PageCodec.decode(PageCodec.encode(PageContent.of(listOf(c)))).elements[0] as Stroke
        val b = PageCodec.decode(PageCodec.encode(PageContent.of(listOf(plain)))).elements[0] as Stroke
        assertArrayEquals(b.pts, a.pts, 0f)
        assertNull(b.ctrl)
    }

    @Test fun transformMovesCtrlToo() {
        val t = curve().transformed(Affine.translation(5f, -3f))
        assertEquals(15f, t.ctrl!![0], 1e-4f)
        assertEquals(7f, t.ctrl!![1], 1e-4f)
        assertEquals(t.ctrl!!.size, ctrl.size)
    }
}
