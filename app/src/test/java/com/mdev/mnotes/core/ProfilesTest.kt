package com.mdev.mnotes.core

import com.mdev.mnotes.core.prefs.PVal
import com.mdev.mnotes.core.prefs.Profile
import com.mdev.mnotes.core.prefs.Profiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2b-A12: profile ustawień — przełączanie z powrotem do stanu sprzed profilu, plik, import, przypięcia. */
class ProfilesTest {

    /** Ustawienia „aplikacji” w pamięci: zastosowanie przełączenia jak w Settings.switchProfile. */
    private class Prefs(val m: MutableMap<String, PVal>) {
        var backup: Map<String, PVal> = emptyMap()
        fun switch(p: Profile?) {
            val s = Profiles.switch(p, backup) { m[it] }
            m.putAll(s.write)
            backup = s.backup
        }
    }

    private val start = mapOf<String, PVal>(
        "input.finger" to PVal.I(0), "input.palm_rejection" to PVal.B(true), "input.two_finger_undo" to PVal.B(true),
        "input.double_tap" to PVal.I(0), "view.minimap" to PVal.B(false), "toolbar.modes" to PVal.S("eraser:2"),
    )

    @Test fun applyAndRevertRestoresExactly() {
        val p = Prefs(start.toMutableMap())
        val stylus = Profile("x", "Tylko rysik", mapOf("input.finger" to PVal.I(2), "input.two_finger_undo" to PVal.B(false)))
        p.switch(stylus)
        assertEquals(PVal.I(2), p.m["input.finger"])
        assertEquals(PVal.B(false), p.m["input.two_finger_undo"])
        assertEquals(PVal.I(0), p.m["input.double_tap"])   // poza profilem — bez zmian
        p.switch(null)
        assertEquals(start, p.m)
        assertTrue(p.backup.isEmpty())
    }

    @Test fun switchingBetweenProfilesDoesNotStack() {
        val p = Prefs(start.toMutableMap())
        val a = Profile("a", "A", mapOf("input.finger" to PVal.I(2), "view.minimap" to PVal.B(true)))
        val b = Profile("b", "B", mapOf("input.finger" to PVal.I(1), "toolbar.modes" to PVal.S("lasso:2")))
        p.switch(a); p.switch(b)
        // B nie dziedziczy mini-mapy z A — wraca do stanu sprzed A.
        assertEquals(PVal.B(false), p.m["view.minimap"])
        assertEquals(PVal.I(1), p.m["input.finger"])
        assertEquals(PVal.S("lasso:2"), p.m["toolbar.modes"])
        p.switch(null)
        assertEquals(start, p.m)
    }

    @Test fun unknownKeysAreIgnored() {
        val p = Prefs(start.toMutableMap())
        p.switch(Profile("z", "Z", mapOf("sync.uri" to PVal.S("content://x"), "lock.app" to PVal.B(false))))
        assertEquals(start, p.m)
        assertTrue(Profiles.KEYS.none { it.key.startsWith("sync.") || it.key.startsWith("lock.") || it.key == "pen.kit" })
    }

    @Test fun fileRoundTripWithTricksyNames() {
        val list = listOf(
            Profile("p1", "Wykład\tz tabem \\ i\nnową linią", mapOf("input.finger" to PVal.I(2), "toolbar.order" to PVal.S("pen,eraser\tx"), "ui.compact" to PVal.B(true))),
            Profile("p2", "Rysowanie", mapOf("view.minimap" to PVal.B(true), "toolbar.size" to PVal.I(2))),
        )
        val back = Profiles.decode(Profiles.encode(list))
        assertEquals(list, back)
        // Plik z nowszej wersji: nieznany klucz i zepsuty wiersz pominięte, reszta zostaje.
        val newer = Profiles.encode(list) + "\tfuture.key\tb:true\n\tinput.finger\tq:zz\nśmieci\n"
        assertEquals(list, Profiles.decode(newer))
        assertEquals(mapOf("input.finger" to PVal.I(1)), Profiles.decodeValues(Profiles.encodeValues(mapOf("input.finger" to PVal.I(1)))))
        assertTrue(Profiles.decodeValues("").isEmpty())
    }

    @Test fun captureTakesOnlyChosenKnownKeys() {
        val cur = start + ("pen.kit" to PVal.S("{}"))
        val p = Profiles.capture("p9", "Mój", Profiles.keysOf(0) + "pen.kit") { cur[it] }
        assertEquals(setOf("input.finger", "input.palm_rejection"), p.values.keys)
    }

    @Test fun mergeRenamesCollidingIds() {
        val a = listOf(Profile("p1", "A", emptyMap()), Profile("p2", "B", emptyMap()))
        val m = Profiles.merge(a, listOf(Profile("p1", "Import", emptyMap()), Profile("q", "Q", emptyMap())))
        assertEquals(listOf("p1", "p2", "p3", "q"), m.map { it.id })
        assertEquals("Import", m[2].name)
    }

    @Test fun nextCyclesThroughProfilesAndOff() {
        val l = Profiles.builtIns(true)
        assertEquals(l[0], Profiles.next(l, null))
        assertEquals(l[1], Profiles.next(l, l[0].id))
        assertNull(Profiles.next(l, l.last().id))
        assertEquals(listOf("Wykład", "Rysowanie", "Tylko rysik"), l.map { it.name })
        assertTrue(l.all { p -> p.values.keys.all(Profiles::known) })
        assertEquals(PVal.I(Profiles.FINGER_OFF), l[2].values[Profiles.FINGER])
    }

    /** 2c-A11: profil, który nadpisuje nieistniejące ustawienie, nic nie robi — klucze sprawdzamy w źródle Settings. */
    @Test fun everyKeyExistsInSettings() {
        val src = java.io.File("src/main/java/com/mdev/mnotes/data/Settings.kt").readText()
        val prefs = Regex("""Pref\("([^"]+)"""").findAll(src).map { it.groupValues[1] }.toSet()
        assertTrue(prefs.size > 50)
        assertEquals(emptyList<String>(), Profiles.KEYS.map { it.key }.filter { it !in prefs })
    }

    @Test fun manualChangeSavedInProfileSurvivesAndRevertsOnOff() {
        val p = Prefs(start.toMutableMap())
        var stylus = Profile("x", "Tylko rysik", mapOf("input.finger" to PVal.I(2)))
        p.switch(stylus)
        // Ręcznie: palec rysuje (klucz profilu) i mini-mapa (spoza profilu); „Tak” zapisuje obie w profilu.
        val edits = mapOf("input.finger" to (PVal.I(2) to PVal.I(1)), "view.minimap" to (PVal.B(false) to PVal.B(true)))
        for ((k, e) in edits) p.m[k] = e.second
        val (np, nb) = Profiles.adopt(stylus, p.backup, edits)
        stylus = np; p.backup = nb
        assertEquals(PVal.I(1), stylus.values["input.finger"])
        assertEquals(PVal.B(true), stylus.values["view.minimap"])
        p.switch(null)
        assertEquals(start, p.m)
        p.switch(stylus)
        assertEquals(PVal.I(1), p.m["input.finger"]); assertEquals(PVal.B(true), p.m["view.minimap"])
    }

    @Test fun leavingPinnedNotebookRestoresStateBefore() {
        val p = Prefs(start.toMutableMap())
        val a = Profile("a", "A", mapOf("view.minimap" to PVal.B(true)))
        val pinned = Profile("rysik", "Tylko rysik", mapOf("input.finger" to PVal.I(2)))
        p.switch(a)
        val before = p.m.toMap()
        // Otwarcie zeszytu z przypiętym profilem, potem stanowisko z tym samym profilem: „przed” zostaje pierwotne.
        var pin = Profiles.pin(null, pinned.id, "a"); p.switch(pinned)
        pin = Profiles.pin(pin, pinned.id, pinned.id)
        assertEquals("a", Profiles.unpin(pin, pinned.id))
        p.switch(a)
        assertEquals(before, p.m)
        // Zmiana profilu w trakcie albo profil już włączony przy otwarciu — wyjście niczego nie przełącza.
        assertNull(Profiles.unpin(pin, "a"))
        assertNull(Profiles.unpin(Profiles.pin(null, "rysik", "rysik"), "rysik"))
        assertEquals("", Profiles.unpin(Profiles.pin(null, "rysik", ""), "rysik"))
    }

    @Test fun stationKeepsItsProfile() {
        val s = com.mdev.mnotes.core.layout.Workbench.Station("Analiza", listOf("nb1", "nb2"), profile = "rysik")
        val back = com.mdev.mnotes.core.layout.Workbench.decode(com.mdev.mnotes.core.layout.Workbench.encode(listOf(s)))
        assertEquals("rysik", back.single().profile)
        assertEquals("", com.mdev.mnotes.core.layout.Workbench.decode("""[{"n":"S","ids":["a"],"s":0,"z":0}]""").single().profile)
    }

    @Test fun pinsRoundTrip() {
        val m = mapOf("nb1" to "p1", "nb-2" to "rysik")
        assertEquals(m, Profiles.pinsDecode(Profiles.pinsEncode(m)))
        assertTrue(Profiles.pinsDecode("").isEmpty())
        assertEquals(mapOf("a" to "b"), Profiles.pinsDecode("a=b;=x;y=;zz"))
    }
}
