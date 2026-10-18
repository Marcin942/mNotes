package com.mdev.mnotes.core.samsung

import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.samsung.SynthSdObjects.Box
import com.mdev.mnotes.core.samsung.SynthSdObjects.Seg
import com.mdev.mnotes.core.samsung.SynthSdObjects.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/** Konwersja tekstu płynącego, obrazów i kształtów Samsunga na elementy mNotes (układ, klucze zasobów, raport). */
class SdObjectsConvertTest {
    private val uuid = "eeeeeeee-0000-4000-8000-000000000001"
    private val size = (17 * SdFlowLayout.DP_PT).toFloat()
    private val left = (16 * SdFlowLayout.DP_PT).toFloat()
    private val top = 10 * SdFlowLayout.DP_PT

    private fun doc(
        text: String, spans: List<Span> = emptyList(), images: List<ByteArray> = emptyList(), extra: List<Pair<Int, ByteArray>> = emptyList(),
        media: Map<String, ByteArray> = emptyMap(),
    ): SdDocument {
        val flow = SynthSdObjects.flow(text, spans, pages = listOf(text.length to text.length - 1), images = images)
        return SdocxReader.read(ByteArrayInputStream(SynthSdocx.build(listOf(SynthSdocx.Page(uuid, extra = extra)), flow, media)))
    }

    private fun layout(d: SdDocument, obstacles: List<SdRect> = emptyList()) =
        SdFlowLayout.layout(d.flow!!, 0, 600f, 848f, obstacles, SdApproxMeasure) { it }

    private fun boxes(p: SdFlowPage) = p.elements.filterIsInstance<TextBox>()

    @Test fun paragraphBecomesOneFullWidthFitHeightBox() {
        val b = boxes(layout(doc("Ala ma kota\nA kot ma Alę"))).single()
        assertEquals("Ala ma kota\nA kot ma Alę", b.text)
        assertEquals(TextBox.FIT_HEIGHT, b.fit)
        assertEquals(size, b.size, 1e-3f)
        assertEquals(600f - 2 * left, b.w, 0.01f)
        assertEquals(left + b.w / 2, b.cx, 0.01f)
        assertEquals(0xFF252525.toInt(), b.color)
        val ascent = SdApproxMeasure.ascent(size)
        assertEquals(top + SdFlowLayout.BASELINE_EM * size - ascent, b.cy.toDouble() - b.h / 2, 0.01)
    }

    @Test fun lineSpacingMatchesSamsungPitch() {
        val b = boxes(layout(doc("x"))).single()
        val natural = SdApproxMeasure.ascent(size) + SdApproxMeasure.descent(size)
        assertEquals(SdFlowLayout.PITCH_EM * size, (natural * b.lineSpacing).toDouble(), 0.02)
    }

    @Test fun obstacleSplitsTheTextAndPushesLineBelowIt() {
        // przeszkoda w pasmie drugiego wiersza: dwa pola, drugie pod obiektem
        val pitch = SdFlowLayout.PITCH_EM * size
        val o = SdRect(0.0, top + pitch + 1, 600.0, top + pitch + 100)
        val bs = boxes(layout(doc("pierwszy\ndrugi"), listOf(o)))
        assertEquals(2, bs.size)
        assertEquals("pierwszy\n", bs[0].text.let { if (it.endsWith("\n")) it else it + "\n" })
        assertEquals("drugi", bs[1].text.trim())
        val y2 = bs[1].cy - bs[1].h / 2
        assertTrue("drugie pole pod przeszkodą: $y2", y2 >= o.y1 - SdApproxMeasure.ascent(size) * 0 )
    }

    @Test fun differentColourStartsNewBox() {
        val red = Span(1, 9, 14, SynthSdObjects.intBytes(0xFFFF0000.toInt()))
        val bs = boxes(layout(doc("pierwszy\ndrugi", listOf(red))))
        assertEquals(2, bs.size)
        assertEquals(0xFF252525.toInt(), bs[0].color)
        assertEquals(0xFFFF0000.toInt(), bs[1].color)
        assertTrue(bs[1].cy > bs[0].cy)
    }

    @Test fun sizeSpanChangesBoxSize() {
        val big = Span(3, 0, 5, SynthSdObjects.floatBytes(30f))
        val b = boxes(layout(doc("duzy", listOf(big)))).single()
        assertEquals(30 * SdFlowLayout.DP_PT.toFloat(), b.size, 1e-3f)
    }

    @Test fun colourMapIsApplied() {
        val d = doc("x")
        val p = SdFlowLayout.layout(d.flow!!, 0, 600f, 848f, emptyList(), SdApproxMeasure) { 0xFFFFFFFF.toInt() }
        assertEquals(0xFFFFFFFF.toInt(), boxes(p).single().color)
    }

    @Test fun emptyPageGivesNothing() {
        val d = doc("x")
        val p = SdFlowLayout.layout(d.flow!!, 5, 600f, 848f, emptyList(), SdApproxMeasure) { it }
        assertTrue(p.elements.isEmpty())
    }

    @Test fun inlineImageStaysInPlaceAndTextContinuesBelowIt() {
        val photo = Box(100.0, 300.0, 700.0, 700.0)
        val d = doc("przed\n￼\npo", images = listOf(SynthSdObjects.image(photo, 5)))
        val p = layout(d)
        val im = p.elements.filterIsInstance<ImageEl>().single()
        assertEquals("sd:5", im.asset)
        assertEquals(600f * 0.375f, im.w, 0.01f)
        val bs = boxes(p)
        assertEquals(2, bs.size)
        val after = bs[1].cy - bs[1].h / 2
        val bandTop = 700.0 * SamsungConvert.UNIT_PT + 6 * SdFlowLayout.DP_PT
        assertEquals(bandTop + SdFlowLayout.BASELINE_EM * size - SdApproxMeasure.ascent(size), after.toDouble(), 0.5)
    }

    @Test fun mixedStylesInOneParagraphAreReported() {
        val red = Span(1, 2, 4, SynthSdObjects.intBytes(0xFFFF0000.toInt()))
        assertTrue(layout(doc("abcdef", listOf(red))).mixedStyles)
        assertFalse(layout(doc("abcdef")).mixedStyles)
    }

    @Test fun unsupportedSpanKindIsReported() {
        val s = Span(17, 0, 3, ByteArray(4))
        assertEquals(setOf(17), layout(doc("abcdef", listOf(s))).ignoredSpanKinds)
    }

    @Test fun overflowBelowPageIsReported() {
        val text = (1..80).joinToString("\n") { "wiersz $it" }
        assertTrue(layout(doc(text)).overflow)
    }

    // --- kształty ---

    private fun shape(kind: Int, segs: List<Seg>, w: Float = 8f) =
        SdShape(kind, SdRect(0.0, 0.0, 1.0, 1.0), 0xFF123456.toInt(), w, segs.map { SdSeg(it.kind, it.pts) })

    @Test fun polygonIsClosedPolylineWithHandles() {
        val s = shape(9, listOf(Seg(1, 0.0, 0.0), Seg(2, 100.0, 0.0), Seg(2, 100.0, 100.0), Seg(2, 0.0, 100.0), Seg(6)))
        val st = SdShapes.strokes(s, 0.375, 0xFF123456.toInt()).single()
        assertTrue(st.poly)
        assertEquals(5, st.count)
        assertEquals(st.pts[0], st.pts[12], 0f)
        assertEquals(8 * 0.375f, st.width, 1e-4f)
    }

    @Test fun polylineStaysOpen() {
        val s = shape(88, listOf(Seg(1, 0.0, 0.0), Seg(2, 50.0, 10.0), Seg(2, 90.0, 80.0)))
        val st = SdShapes.strokes(s, 1.0, 0xFF000000.toInt()).single()
        assertTrue(st.poly)
        assertEquals(3, st.count)
    }

    @Test fun freehandCurveIsSampledNotPoly() {
        val s = shape(90, listOf(Seg(1, 0.0, 0.0), Seg(4, 10.0, 0.0, 20.0, 10.0, 30.0, 10.0)))
        val st = SdShapes.strokes(s, 1.0, 0xFF000000.toInt()).single()
        assertFalse(st.poly)
        assertEquals(15, st.count)
        assertEquals(30f, st.pts[3 * 14], 1e-3f)
    }

    private fun ellipseSegs(cx: Double, cy: Double, a: Double, b: Double): List<Seg> {
        // 8 łuków kwadratowych jak w Samsungu: punkt kontrolny na stycznych, koniec co 45°
        val out = arrayListOf(Seg(1, cx + a, cy))
        for (k in 1..8) {
            val t0 = Math.PI / 4 * (k - 1)
            val t1 = Math.PI / 4 * k
            val tm = (t0 + t1) / 2
            val r = 1 / Math.cos(Math.PI / 8)
            out.add(Seg(3, cx + a * r * Math.cos(tm), cy + b * r * Math.sin(tm), cx + a * Math.cos(t1), cy + b * Math.sin(t1)))
        }
        out.add(Seg(6))
        return out
    }

    @Test fun eightQuadEllipseBecomesEditableEllipse() {
        val st = SdShapes.strokes(shape(1, ellipseSegs(200.0, 300.0, 120.0, 60.0)), 1.0, 0xFF000000.toInt()).single()
        assertTrue(st.ellipse)
        assertEquals(49, st.count)
        assertEquals(320f, st.pts[0], 0.5f)
        assertEquals(300f, st.pts[1], 0.5f)
    }

    @Test fun quadPathThatIsNotEllipseStaysPlainStroke() {
        val segs = listOf(
            Seg(1, 0.0, 0.0), Seg(3, 50.0, -80.0, 100.0, 0.0), Seg(3, 150.0, 90.0, 200.0, 10.0), Seg(3, 0.0, 300.0, 20.0, 40.0),
            Seg(3, 1.0, 1.0, 2.0, 2.0), Seg(3, 5.0, 5.0, 9.0, 3.0), Seg(3, 3.0, 8.0, 2.0, 4.0), Seg(3, 9.0, 9.0, 1.0, 1.0),
            Seg(3, 0.0, 1.0, 0.0, 0.0), Seg(6),
        )
        val st = SdShapes.strokes(shape(1, segs), 1.0, 0xFF000000.toInt()).single()
        assertFalse(st.ellipse)
    }

    // --- klucze obrazów ---

    @Test fun uncroppedImageKeyIsPlainMediaId() {
        assertEquals("sd:4", ImageConvert.key(SdImage(SdRect(0.0, 0.0, 10.0, 10.0), 0.0, 4, null)))
        val same = SdRect(0.0, 0.0, 10.0, 10.0)
        assertEquals("sd:4", ImageConvert.key(SdImage(same, 0.0, 4, same)))
    }

    @Test fun croppedImageKeyCarriesFractionsOfFullImage() {
        val img = SdImage(SdRect(100.0, 200.0, 300.0, 400.0), 0.0, 7, SdRect(0.0, 0.0, 400.0, 800.0))
        assertEquals("sd:7:0.2500,0.2500,0.7500,0.5000", ImageConvert.key(img))
        val (id, crop) = SdImageKey.parse("sd:7:0.2500,0.2500,0.7500,0.5000")!!
        assertEquals(7, id)
        assertEquals(0.75, crop!![2], 1e-9)
    }

    @Test fun imageKeyParserRejectsForeignAndBrokenKeys() {
        assertNull(SdImageKey.parse("abc.png"))
        assertNull(SdImageKey.parse("sd:x"))
        assertEquals(3, SdImageKey.parse("sd:3")!!.first)
        assertNull(SdImageKey.parse("sd:3:0.9,0.1,0.2,0.5")!!.second)
    }

    // --- cały tor konwersji ---

    private fun convert(d: SdDocument, opts: ConvertOptions = ConvertOptions()) = SamsungConvert.convert(d, "a.sdocx", opts)

    @Test fun convertedPageHoldsTextImageAndShapeInDrawingOrder() {
        val photo = Box(100.0, 1200.0, 500.0, 1500.0)
        val box = Box(200.0, 200.0, 600.0, 500.0)
        val poly = shape9(box)
        val d = doc("tekst", extra = listOf(3 to SynthSdObjects.image(photo, 4), 7 to poly), media = mapOf("4@f.jpg" to byteArrayOf(1, 2, 3)))
        val n = convert(d)
        val els = n.pages.single().elements
        assertTrue(els[0] is TextBox)
        assertTrue(els.any { it is ImageEl && it.asset == "sd:4" })
        assertTrue(els.last() is Stroke)
        assertEquals(1, n.textBoxCount)
        assertEquals(1, n.imageCount)
        assertNotNull(n.media[4])
    }

    private fun shape9(r: Box, argb: Int = 0xFF0000FF.toInt()) = SynthSdObjects.shape(
        9, r, argb, 6f,
        listOf(Seg(1, r.x0, r.y0), Seg(2, r.x1, r.y0), Seg(2, r.x1, r.y1), Seg(2, r.x0, r.y1), Seg(6)),
    )

    @Test fun shapeUnderTextPushesTextBelowIt() {
        val r = Box(100.0, 0.0, 1500.0, 700.0)
        val withShape = convert(doc("tekst", extra = listOf(7 to shape9(r)))).pages.single().textBoxes.single()
        val without = convert(doc("tekst")).pages.single().textBoxes.single()
        assertTrue(withShape.cy > without.cy + 100f)
    }

    @Test fun pageWithOnlyFlowTextIsNotTrimmedAsEmpty() {
        val text = "a"
        val flow = SynthSdObjects.flow(text, pages = listOf(1 to 0, 0 to 0))
        val pages = listOf(SynthSdocx.Page(uuid), SynthSdocx.Page("eeeeeeee-0000-4000-8000-000000000002"))
        val d = SdocxReader.read(ByteArrayInputStream(SynthSdocx.build(pages, flow)))
        val n = convert(d)
        assertEquals(1, n.pages.size)
        assertEquals(1, n.textBoxCount)
    }

    @Test fun reportMentionsFlowTextAndShapes() {
        val r = Box(200.0, 200.0, 600.0, 500.0)
        val n = convert(doc("tekst", extra = listOf(7 to shape9(r))))
        assertTrue(n.report.any { "kształt" in it.pl })
        assertTrue(n.report.any { "tekst" in it.pl })
    }

    @Test fun darkInvertsTextAndShapeColours() {
        val r = Box(200.0, 200.0, 600.0, 500.0)
        val n = convert(doc("tekst", extra = listOf(7 to shape9(r, 0xFF000000.toInt()))), ConvertOptions(dark = true))
        val p = n.pages.single()
        assertNotEquals(0xFF252525.toInt(), p.textBoxes.single().color)
        assertNotEquals(0xFF000000.toInt(), p.strokes.single().color)
    }

    private fun assertNotEquals(a: Int, b: Int) = org.junit.Assert.assertNotEquals(a, b)
}
