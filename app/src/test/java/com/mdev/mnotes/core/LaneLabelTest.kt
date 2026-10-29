package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.LaneLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3c-A7: nagłówek pasa stanowiska — rodzaj dokumentu, nazwa, szerokość, opis dla czytnika ekranu. */
class LaneLabelTest {
    @Test
    fun pdfLosesItsExtensionButKeepsItsKind() {
        val l = LaneLabel.of(" Wykład 3.PDF ", pdf = true, readOnly = true, laneDp = 600f)
        assertEquals(LaneLabel.Kind.PDF, l.kind)
        assertEquals("Wykład 3", l.title)
        assertEquals("Wykład 3 — dokument PDF, tylko do odczytu", l.description)
        // zeszyt o nazwie z „.pdf” zostaje jak jest; sama „.pdf” nie znika do pustej
        assertEquals("notatki.pdf", LaneLabel.of("notatki.pdf", pdf = false, readOnly = false, laneDp = 600f).title)
        assertEquals(".pdf", LaneLabel.of(".pdf", pdf = true, readOnly = false, laneDp = 600f).title)
    }

    @Test
    fun emptyTitleGetsAName() {
        assertEquals("Bez tytułu", LaneLabel.of("  ", pdf = false, readOnly = false, laneDp = 400f).title)
        val en = LaneLabel.of("", pdf = false, readOnly = false, laneDp = 400f, polish = false)
        assertEquals("Untitled — notebook", en.description)
    }

    @Test
    fun widthFollowsTheLane() {
        assertEquals(LaneLabel.MAX_DP, LaneLabel.of("x", false, false, 1200f).maxDp)
        val narrow = LaneLabel.of("Fizyka", false, false, 150f)
        assertTrue(narrow.showTitle)
        assertEquals(134, narrow.maxDp)
        // bardzo wąski pas (kolumny obok siebie na telefonie): sama ikonka
        assertFalse(LaneLabel.of("Fizyka", false, false, 80f).showTitle)
    }
}
