package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.Workbench
import com.mdev.mnotes.core.layout.Workbench.Station
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2b-A7: stanowisko — rzędy dokumentów na jednym polu, wspólne przewijanie w pionie, zapis stanowisk. */
class WorkbenchTest {
    private val g = Workbench.geometry(listOf(1000f, 600f, 800f), gap = 20f)

    @Test fun rowsStackWithGaps() {
        assertArrayEquals(floatArrayOf(0f, 1020f, 1640f), g.tops, 0f)
        assertEquals(2440f, g.total, 0f)
        assertEquals(1, g.rowAt(1500f))
        assertEquals(-1, g.rowAt(1010f))                  // przerwa
        assertEquals(1, g.nearest(1015f))                 // przerwa należy do bliższego rzędu
        assertEquals(Workbench.rowHeight(842f, 2f, 16f), 842f * 2f + 32f, 0f)
    }

    @Test fun eachRowDrawsOnlyItsVisibleBand() {
        // Okno 900 px przewinięte o 700: widać koniec rzędu 0 (700–1000) i początek rzędu 1 (0–580).
        assertArrayEquals(floatArrayOf(700f, 1000f), g.band(0, 700f, 900f)!!, 0f)
        assertArrayEquals(floatArrayOf(0f, 580f), g.band(1, 700f, 900f)!!, 0f)
        assertNull(g.band(2, 700f, 900f))
        assertEquals(0f, Workbench.clampScroll(-50f, g.total, 900f), 0f)
        assertEquals(g.total - 900f, Workbench.clampScroll(9999f, g.total, 900f), 0f)
    }

    @Test fun dropPointMapsToTargetRow() {
        // Ekran y = 400 przy przewinięciu 800 → pole 1200 → rząd 1, 180 px od jego góry.
        val (row, y) = Workbench.locate(g, 400f, 800f)!!
        assertEquals(1, row); assertEquals(180f, y, 1e-3f)
        // Upuszczenie w przerwie trafia do najbliższego rzędu, przy jego brzegu.
        val (r2, y2) = Workbench.locate(g, 1015f, 0f)!!
        assertEquals(1, r2); assertEquals(0f, y2, 1e-3f)
    }

    @Test fun zoomKeepsThePointUnderTheFingers() {
        val big = Workbench.geometry(listOf(2000f, 1200f, 1600f), gap = 20f)
        // Punkt w połowie rzędu 1 pod palcami (ekran 300) zostaje pod nimi po powiększeniu.
        val scroll = 1020f + 300f - 300f
        val s2 = Workbench.keepAnchor(g, big, scroll, 300f)
        val (row, y) = Workbench.locate(big, 300f, s2)!!
        assertEquals(1, row); assertEquals(600f, y, 1e-3f)
    }

    @Test fun stationsSurviveSavingAndReplaceByName() {
        val a = Station("Wykład + zadania", listOf("nb1", "nb2"), 120f, 2.5f)
        val b = Station("Fizyka", listOf("nb3"))
        val list = Workbench.upsert(Workbench.upsert(emptyList(), a), b)
        assertEquals(listOf("Fizyka", "Wykład + zadania"), list.map { it.name })
        val back = Workbench.decode(Workbench.encode(list))
        assertEquals(list, back)
        val replaced = Workbench.upsert(back, a.copy(ids = listOf("nb2", "nb1")))
        assertEquals(2, replaced.size); assertEquals(listOf("nb2", "nb1"), replaced.first().ids)
        assertEquals(emptyList<Station>(), Workbench.decode("to nie json"))
        assertEquals(listOf("nb1"), Workbench.alive(a) { it != "nb2" }.ids)
    }
}
