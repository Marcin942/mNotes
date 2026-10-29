package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.Workbench
import com.mdev.mnotes.core.layout.Workbench.Station
import org.junit.Assert.assertEquals
import org.junit.Test

/** 2b-A7 / 2d-A1: zapis stanowisk (geometria stanowiska: `WorkspaceTest`). */
class WorkbenchTest {
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

    @Test fun oldSavedStationWithoutNewFieldsStillLoads() {
        // Zapis sprzed 2d-A1 (tylko n, ids, s, z): wczytuje się, kierunek „jak pierwszy dokument”, pasy od początku.
        val old = """[{"n":"Wykład","ids":["nb1","nb2"],"s":340.5,"z":1.25}]"""
        val s = Workbench.decode(old).single()
        assertEquals(Station("Wykład", listOf("nb1", "nb2"), 340.5f, 1.25f), s)
        assertEquals(-1, s.mode); assertEquals(emptyList<Float>(), s.offsets); assertEquals(0, s.active)
        // Nowe pola przechodzą zapis i odczyt; stara wersja czytająca ten zapis zignoruje m, o, a.
        val n = s.copy(mode = 0, offsets = listOf(-120f, -800f), active = 1)
        assertEquals(n, Workbench.decode(Workbench.encode(listOf(n))).single())
        // Usunięty dokument: przesunięcia i aktywny pas idą za tymi, które zostały.
        val three = n.copy(ids = listOf("nb1", "nb2", "nb3"), offsets = listOf(-1f, -2f, -3f), active = 2)
        val alive = Workbench.alive(three) { it != "nb2" }
        assertEquals(listOf("nb1", "nb3"), alive.ids); assertEquals(listOf(-1f, -3f), alive.offsets); assertEquals(1, alive.active)
    }

    /** 3c-B3: puste i powtórzone id w zapisie przesuwały przesunięcia i aktywny pas na sąsiedni dokument. */
    @Test fun droppedIdsKeepOffsetsAndActiveAligned() {
        val saved = """[{"n":"X","ids":["nb1","","nb1","nb2"],"o":[-1,-9,-8,-2],"a":3}]"""
        val s = Workbench.decode(saved).single()
        assertEquals(listOf("nb1", "nb2"), s.ids)
        assertEquals(listOf(-1f, -2f), s.offsets)
        assertEquals(1, s.active)
        // Aktywny pas wskazywał odrzucony duplikat — wraca na pierwszy.
        assertEquals(0, Workbench.decode("""[{"n":"X","ids":["nb1","nb1","nb2"],"a":1}]""").single().active)
    }
}
