package com.mdev.mnotes.core.samsung

import com.mdev.mnotes.core.samsung.SynthSdObjects.Box
import com.mdev.mnotes.core.samsung.SynthSdObjects.Span
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class SdFlowTest {
    private val page = SynthSdocx.Page("dddddddd-0000-4000-8000-000000000001")
    private val photo = Box(71.11, 44.44, 500.0, 300.0)

    // Strona 0: znaki 0-3, strona 1: 4-5 (obraz i enter), strona 2 pusta (z nieistotnym końcem jak w pliku), strona 3: 6-8.
    private val text = "abc\n￼\nxyz"
    private val pairs = listOf(4 to 3, 2 to 5, 0 to 1, 3 to 8)

    private fun read(flow: ByteArray?, media: Map<String, ByteArray> = emptyMap()): SdDocument =
        SdocxReader.read(ByteArrayInputStream(SynthSdocx.build(listOf(page), flow, media)))

    private fun flow(
        images: List<ByteArray> = listOf(SynthSdObjects.image(photo, 5)),
        pages: List<Pair<Int, Int>> = pairs,
        spans: List<Span> = emptyList(),
    ) = SynthSdObjects.flow(text, spans, pages = pages, images = images)

    @Test fun readsTextMarginsAndPageRanges() {
        val f = read(flow()).flow
        assertNotNull(f)
        assertEquals(text, f!!.text)
        assertArrayEquals(floatArrayOf(16f, 10f, 16f, 10f), f.margins, 0f)
        assertEquals(0..3, f.pageRange(0))
        assertEquals(4..5, f.pageRange(1))
        assertTrue(f.pageRange(2).isEmpty())
        assertEquals(6..8, f.pageRange(3))
        assertTrue(f.pageRange(9).isEmpty())
    }

    @Test fun readsSpansAndFindsThemByCharacter() {
        val size = Span(3, 0, 6, SynthSdObjects.floatBytes(17f))
        val colour = Span(1, 4, 9, SynthSdObjects.intBytes(0xFF252525.toInt()))
        val f = read(flow(spans = listOf(size, colour))).flow!!
        assertEquals(2, f.spans.size)
        assertEquals(17f, f.spanAt(3, 2)!!.floatValue()!!, 1e-6f)
        assertNull(f.spanAt(3, 6))
        assertEquals(0xFF252525.toInt(), f.spanAt(1, 8)!!.intValue())
        assertNull(f.spanAt(1, 3))
    }

    @Test fun readsInlineImageRectAndMedia() {
        val f = read(flow()).flow!!
        assertTrue(f.imagesComplete)
        val im = f.images.single().image
        assertEquals(5, im.mediaId)
        assertEquals(71.11, im.rect.x0, 1e-9)
        assertEquals(300.0, im.rect.y1, 1e-9)
    }

    @Test fun textWithoutImagesEndsAtFrameEnd() {
        val f = SynthSdObjects.flow("abc", pages = listOf(3 to 2))
        val r = read(f).flow!!
        assertEquals("abc", r.text)
        assertTrue(r.imagesComplete)
        assertTrue(r.images.isEmpty())
    }

    @Test fun mismatchedImageCountKeepsTextButDropsImages() {
        val f = read(flow(images = listOf(SynthSdObjects.image(photo, 5), SynthSdObjects.image(photo, 4)))).flow!!
        assertEquals(text, f.text)
        assertFalse(f.imagesComplete)
        assertTrue(f.images.isEmpty())
    }

    @Test fun damagedImageListKeepsText() {
        val f = read(flow(images = listOf(ByteArray(40)))).flow!!
        assertEquals(text, f.text)
        assertFalse(f.imagesComplete)
        assertTrue(f.images.isEmpty())
    }

    @Test fun pageTableNotCoveringTextIsNotAFlowFrame() {
        // fałszywe trafienie skanu: liczby wyglądają dobrze, ale strony kończą się przed końcem tekstu
        assertNull(read(flow(pages = listOf(4 to 3))).flow)
    }

    @Test fun pageEndsGoingBackwardsAreRejected() {
        assertNull(read(flow(pages = listOf(6 to 5, 3 to 2, 3 to 8))).flow)
    }

    @Test fun truncatedFrameDoesNotBreakReading() {
        val whole = flow()
        val doc = read(whole.copyOf(whole.size - 10))
        assertNull(doc.flow)
        assertEquals(1, doc.pages.size)
    }

    @Test fun noteWithoutFlowHasNullFlow() {
        assertNull(read(null).flow)
    }

    @Test fun collectsMediaAndIgnoresPreviews() {
        val jpg = byteArrayOf(1, 2, 3)
        val doc = read(null, mapOf("4@files_a.jpg" to jpg, "5@page_b.spi" to byteArrayOf(9), "mediaInfo.dat" to byteArrayOf(8)))
        assertEquals(setOf(4), doc.media.keys)
        assertArrayEquals(jpg, doc.media[4])
    }
}
