package com.mdev.mnotes.core

import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.text.TextFit
import com.mdev.mnotes.core.text.TextMeasure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class TextFitTest {
    /** Każdy znak ma szerokość 0,5 rozmiaru, wiersz 1,2 rozmiaru; zawijanie po znakach (bez słów — wystarcza do liczenia). */
    private val m = object : TextMeasure {
        override fun naturalWidth(t: TextBox) = t.text.split('\n').maxOf { it.length } * t.size * 0.5f
        override fun textHeight(t: TextBox, width: Float): Float {
            val perLine = max(1, (width / (t.size * 0.5f)).toInt())
            val lines = t.text.split('\n').sumOf { max(1, ceil(it.length / perLine.toDouble()).toInt()) }
            return lines * t.size * 1.2f
        }
    }

    private val pageW = 595f
    private val pageH = 842f

    private fun box(text: String, x: Float = 100f, y: Float = 200f, w: Float = 80f, h: Float = 30f, rot: Float = 0f, fit: Int = TextBox.FIT_AUTO) =
        TextBox(1, x + w / 2f, y + h / 2f, w, h, rot, text, 20f, 0xFF000000.toInt(), fit = fit)

    private fun corner(t: TextBox) = TextFit.corner(t)

    @Test
    fun autoShortTextFitsItsWidth() {
        val t = TextFit.refit(box("abcd"), pageW, pageH, m)
        // 4 znaki po 10 pt, +1 zapasu na zaokrąglenie układu
        assertEquals(41f, t.w, 0.01f)
        assertEquals(TextFit.boxHeight(m, t, t.w), t.h, 0.01f)
        assertEquals(100f, corner(t).first, 0.01f)
        assertEquals(200f, corner(t).second, 0.01f)
    }

    @Test
    fun autoLongTextStopsAtPageEdgeAndWraps() {
        val t = TextFit.refit(box("x".repeat(200)), pageW, pageH, m)
        assertEquals(pageW - TextFit.MARGIN - 100f, t.w, 0.01f)
        assertTrue("wysokość rośnie przy zawijaniu", t.h > 3 * 20f)
        assertEquals(100f, corner(t).first, 0.01f)
    }

    @Test
    fun autoNeverNarrowerThanMinimum() {
        val t = TextFit.refit(box("", w = 200f), pageW, pageH, m)
        assertEquals(TextFit.minWidth(20f), t.w, 0.01f)
    }

    @Test
    fun heightModeKeepsWidth() {
        val t = TextFit.refit(box("x".repeat(100), fit = TextBox.FIT_HEIGHT), pageW, pageH, m)
        assertEquals(80f, t.w, 0f)
        assertEquals(TextFit.boxHeight(m, t, 80f), t.h, 0.01f)
        assertEquals(100f, corner(t).first, 0.01f)
        assertEquals(200f, corner(t).second, 0.01f)
    }

    @Test
    fun fixedModeIsUntouched() {
        val src = box("x".repeat(500), fit = TextBox.FIT_FIXED)
        val t = TextFit.refit(src, pageW, pageH, m)
        assertEquals(src.w, t.w, 0f)
        assertEquals(src.h, t.h, 0f)
    }

    @Test
    fun rotatedBoxKeepsTopLeftCorner() {
        val src = box("abcdefgh", x = 200f, y = 300f, rot = 0.6f)
        val before = corner(src)
        val t = TextFit.refit(src, pageW, pageH, m)
        assertEquals(before.first, corner(t).first, 0.01f)
        assertEquals(before.second, corner(t).second, 0.01f)
        assertEquals(81f, t.w, 0.01f)
    }

    @Test
    fun roomFollowsTextAxis() {
        assertEquals(pageW - TextFit.MARGIN - 100f, TextFit.room(100f, 200f, 0f, pageW, pageH), 0.01f)
        // pionowo w dół: do dolnego marginesu
        val down = TextFit.room(100f, 200f, (Math.PI / 2).toFloat(), pageW, pageH)
        assertEquals(pageH - TextFit.MARGIN - 200f, down, 0.05f)
        // ukośnie: promień trafia w bliższą krawędź
        val diag = TextFit.room(500f, 200f, 0.5f, pageW, pageH)
        assertEquals((pageW - TextFit.MARGIN - 500f) / cos(0.5f), diag, 0.05f)
    }

    @Test
    fun placeXMovesNewBoxLeftWhenNearEdge() {
        assertEquals(200f, TextFit.placeX(200f, pageW), 0f)
        val x = TextFit.placeX(pageW - 10f, pageW)
        assertEquals(pageW - TextFit.MARGIN - TextFit.MIN_ROOM, x, 0.01f)
        // wąska strona: nie wychodzi na lewo poza margines
        assertEquals(TextFit.MARGIN, TextFit.placeX(TextFit.MARGIN, 100f), 0.01f)
    }

    @Test
    fun rightHandleFromAutoFreezesWidthAndKeepsTopLeft() {
        val src = TextFit.refit(box("x".repeat(60)), pageW, pageH, m)
        val c = corner(src)
        val t = TextFit.resizeSide(src, TextFit.Side.RIGHT, c.first + 150f, 220f, m)
        assertEquals(TextBox.FIT_HEIGHT, t.fit)
        assertEquals(150f, t.w, 0.01f)
        assertEquals(TextFit.boxHeight(m, t, 150f), t.h, 0.01f)
        assertEquals(c.first, corner(t).first, 0.01f)
        assertEquals(c.second, corner(t).second, 0.01f)
    }

    @Test
    fun leftHandleKeepsRightEdge() {
        val src = box("x".repeat(30), fit = TextBox.FIT_HEIGHT)
        val right = src.cx + src.w / 2f
        val t = TextFit.resizeSide(src, TextFit.Side.LEFT, 60f, 210f, m)
        assertEquals(right, t.cx + t.w / 2f, 0.01f)
        assertEquals(right - 60f, t.w, 0.01f)
        assertEquals(src.cy - src.h / 2f, t.cy - t.h / 2f, 0.01f)
    }

    @Test
    fun widthNotBelowMinimum() {
        val src = box("abc", fit = TextBox.FIT_HEIGHT)
        val t = TextFit.resizeSide(src, TextFit.Side.RIGHT, 0f, 210f, m)
        assertEquals(TextFit.minWidth(20f), t.w, 0.01f)
    }

    @Test
    fun verticalHandlesSwitchToFixedAndKeepWidth() {
        val src = box("abc", fit = TextBox.FIT_AUTO)
        val bottom = TextFit.resizeSide(src, TextFit.Side.BOTTOM, src.cx, 400f, m)
        assertEquals(TextBox.FIT_FIXED, bottom.fit)
        assertEquals(src.w, bottom.w, 0f)
        assertEquals(400f - 200f, bottom.h, 0.01f)
        assertEquals(200f, bottom.cy - bottom.h / 2f, 0.01f)

        val top = TextFit.resizeSide(bottom, TextFit.Side.TOP, bottom.cx, 150f, m)
        assertEquals(150f, top.cy - top.h / 2f, 0.01f)
        assertEquals(400f, top.cy + top.h / 2f, 0.01f)
    }

    @Test
    fun rightHandleOnFixedKeepsHeightAndMode() {
        val src = box("abc", w = 100f, h = 90f, fit = TextBox.FIT_FIXED)
        val t = TextFit.resizeSide(src, TextFit.Side.RIGHT, 300f, 220f, m)
        assertEquals(TextBox.FIT_FIXED, t.fit)
        assertEquals(90f, t.h, 0f)
        assertEquals(200f, t.w, 0.01f)
    }

    @Test
    fun rotatedHandleDragUsesBoxAxes() {
        val rot = 0.5f
        val src = box("abc", x = 150f, y = 250f, w = 100f, h = 40f, rot = rot, fit = TextBox.FIT_HEIGHT)
        val c = corner(src)
        // punkt 160 pt od lewego-górnego rogu wzdłuż osi tekstu (na wysokości środka pola)
        val ux = cos(rot); val uy = sin(rot)
        val px = c.first + ux * 160f - uy * src.h / 2f
        val py = c.second + uy * 160f + ux * src.h / 2f
        val t = TextFit.resizeSide(src, TextFit.Side.RIGHT, px, py, m)
        assertEquals(160f, t.w, 0.01f)
        assertEquals(c.first, corner(t).first, 0.01f)
        assertEquals(c.second, corner(t).second, 0.01f)
    }

    @Test
    fun withFitRestoresAutoSizeAfterManualResize() {
        val manual = box("abcd", w = 300f, h = 120f, fit = TextBox.FIT_FIXED)
        val auto = TextFit.withFit(manual, TextBox.FIT_AUTO, pageW, pageH, m)
        assertEquals(TextBox.FIT_AUTO, auto.fit)
        assertEquals(41f, auto.w, 0.01f)
        assertEquals(corner(manual).first, corner(auto).first, 0.01f)
        assertEquals(corner(manual).second, corner(auto).second, 0.01f)
    }
}
