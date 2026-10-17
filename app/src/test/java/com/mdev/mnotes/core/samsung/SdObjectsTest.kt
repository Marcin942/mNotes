package com.mdev.mnotes.core.samsung

import com.mdev.mnotes.core.samsung.SynthSdObjects.Box
import com.mdev.mnotes.core.samsung.SynthSdObjects.Seg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class SdObjectsTest {
    private val visible = Box(100.0, 200.0, 500.0, 600.0)

    @Test fun readsPlainImage() {
        val im = SdObjects.image(SynthSdObjects.image(visible, mediaId = 4))
        assertEquals(100.0, im.rect.x0, 1e-9)
        assertEquals(600.0, im.rect.y1, 1e-9)
        assertEquals(4, im.mediaId)
        assertEquals(0.0, im.rotationDeg, 1e-9)
        assertNull(im.full)
    }

    @Test fun readsRotationFromEdgeMidpoints() {
        val im = SdObjects.image(SynthSdObjects.image(visible, mediaId = 5, rotationDeg = 31.0))
        assertEquals(31.0, im.rotationDeg, 0.01)
        assertEquals(300.0, im.rect.cx, 1e-9)
        val back = SdObjects.image(SynthSdObjects.image(visible, mediaId = 5, rotationDeg = -12.5))
        assertEquals(-12.5, back.rotationDeg, 0.01)
    }

    @Test fun readsFullRectOfCroppedImage() {
        val full = Box(0.0, 100.0, 800.0, 700.0)
        val im = SdObjects.image(SynthSdObjects.image(visible, mediaId = 7, full = full))
        val f = im.full
        assertNotNull(f)
        assertEquals(0.0, f!!.x0, 1e-9)
        assertEquals(800.0, f.x1, 1e-9)
        assertEquals(700.0, f.y1, 1e-9)
    }

    @Test fun ignoresFullRectThatDoesNotContainVisiblePart() {
        val im = SdObjects.image(SynthSdObjects.image(visible, mediaId = 7, full = Box(300.0, 300.0, 400.0, 400.0)))
        assertNull(im.full)
    }

    @Test fun imageInsidePageObjectIsReadFromPayload() {
        val page = SynthSdocx.Page("cccccccc-0000-4000-8000-000000000001", extra = listOf(3 to SynthSdObjects.image(visible, 9)))
        val doc = SdocxReader.read(ByteArrayInputStream(SynthSdocx.build(listOf(page))))
        val o = doc.pages[0].allObjects().single { it.type == 3 }
        assertEquals(9, SdObjects.image(o.payload).mediaId)
    }

    @Test fun truncatedImageIsFormatError() {
        val bytes = SynthSdObjects.image(visible, 4)
        for (cut in listOf(0, 3, 20, bytes.size / 2)) {
            try {
                SdObjects.image(bytes.copyOf(cut))
                fail("oczekiwano wyjątku dla $cut bajtów")
            } catch (_: SamsungFormatException) {
            }
        }
    }

    private val path = listOf(
        Seg(1, 10.0, 20.0),
        Seg(2, 110.0, 20.0),
        Seg(3, 150.0, 20.0, 150.0, 60.0),
        Seg(4, 150.0, 90.0, 120.0, 120.0, 100.0, 120.0),
        Seg(6),
    )

    @Test fun readsShapePathColorAndWidth() {
        val s = SdObjects.shape(SynthSdObjects.shape(9, Box(10.0, 20.0, 150.0, 120.0), 0xFFE53935.toInt(), 4.5f, path))
        assertEquals(9, s.kind)
        assertEquals(0xFFE53935.toInt(), s.color)
        assertEquals(4.5f, s.widthUnits, 1e-6f)
        assertEquals(listOf(1, 2, 3, 4, 6), s.segs.map { it.kind })
        assertEquals(listOf(2, 2, 4, 6, 0), s.segs.map { it.pts.size })
        assertEquals(60.0, s.segs[2].pts[3], 1e-9)
        assertEquals(100.0, s.segs[3].pts[4], 1e-9)
        assertEquals(150.0, s.rect.x1, 1e-9)
    }

    @Test fun shapeWithWrongPathLengthIsRejected() {
        val bytes = SynthSdObjects.shape(88, Box(0.0, 0.0, 50.0, 50.0), 0xFF000000.toInt(), 3f, path)
        try {
            SdObjects.shape(bytes.copyOf(bytes.size - 9))
            fail("oczekiwano wyjątku")
        } catch (_: SamsungFormatException) {
        }
    }

    @Test fun shapeWithUnknownSegmentKindIsRejected() {
        val bytes = SynthSdObjects.shape(88, Box(0.0, 0.0, 50.0, 50.0), 0xFF000000.toInt(), 3f, listOf(Seg(1, 1.0, 2.0), Seg(5, 3.0, 4.0)))
        try {
            SdObjects.shape(bytes)
            fail("oczekiwano wyjątku")
        } catch (_: SamsungFormatException) {
        }
    }

    @Test fun mediaIdComesFromEntryNamePrefix() {
        assertEquals(4, SdocxReader.mediaId("4@files_abc.jpg"))
        assertEquals(12, SdocxReader.mediaId("12@x.PNG"))
        assertNull(SdocxReader.mediaId("4@page_abc.spi"))
        assertNull(SdocxReader.mediaId("mediaInfo.dat"))
        assertNull(SdocxReader.mediaId("@x.jpg"))
        assertNull(SdocxReader.mediaId("abc@x.jpg"))
    }
}
