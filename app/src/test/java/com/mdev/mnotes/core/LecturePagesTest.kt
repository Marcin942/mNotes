package com.mdev.mnotes.core

import com.mdev.mnotes.core.pages.LecturePages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Pomysł Fable nr 1: tryb wykładu — zdjęcie na nowej stronie z nagłówkiem. */
class LecturePagesTest {
    private val t = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 27, 10, 5, 0) }.timeInMillis

    @Test fun headerWithDateTimeAndNumber() {
        assertEquals("27.10.2026, 10:05 · zdjęcie 3", LecturePages.header(t, 3, true))
        assertEquals("27.10.2026, 10:05 · photo 1", LecturePages.header(t, 1, false))
    }

    @Test fun photoFitsUnderHeaderLeavingRoomForNotes() {
        val r = LecturePages.photoRect(4000, 3000, 595f, 842f)
        assertEquals(4f / 3f, r.w / r.h, 1e-3f)
        assertTrue(r.l >= LecturePages.MARGIN - 0.01f && r.r <= 595f - LecturePages.MARGIN + 0.01f)
        assertTrue(r.t >= LecturePages.MARGIN + LecturePages.HEADER - 0.01f)
        assertTrue(r.b <= 842f * LecturePages.PHOTO_SHARE + LecturePages.MARGIN + 0.01f)
        // Zdjęcie pionowe jest ograniczone wysokością i wyśrodkowane.
        val p = LecturePages.photoRect(3000, 4000, 595f, 842f)
        assertEquals(595f / 2f, (p.l + p.r) / 2f, 0.01f)
    }

    @Test fun numberingCountsTodaysPhotos() {
        val today = LecturePages.header(t, 1, true)
        val other = "26.10.2026, 09:00 · zdjęcie 1"
        assertEquals(2, LecturePages.nextNumber(listOf(today, other, "Notatka"), t))
    }
}
