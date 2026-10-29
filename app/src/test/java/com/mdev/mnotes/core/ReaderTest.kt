package com.mdev.mnotes.core

import com.mdev.mnotes.core.prefs.PVal
import com.mdev.mnotes.core.prefs.Profile
import com.mdev.mnotes.core.prefs.Profiles
import com.mdev.mnotes.core.prefs.Reader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3c-A9: tryb „do odczytu” — kiedy się włącza, co blokuje, profil „Do odczytu” na nowych i starych instalacjach. */
class ReaderTest {
    @Test fun turnsOnBySettingPhoneOrMirror() {
        assertTrue(Reader.onOpen(on = true, auto = false, smallestWidthDp = 800, mirror = false))
        assertTrue(Reader.onOpen(on = false, auto = true, smallestWidthDp = 411, mirror = false))
        assertFalse("tablet nie włącza sam", Reader.onOpen(on = false, auto = true, smallestWidthDp = 600, mirror = false))
        assertFalse("na telefonie do wyłączenia", Reader.onOpen(on = false, auto = false, smallestWidthDp = 360, mirror = false))
        assertTrue("kopia z folderu wymiany zawsze", Reader.onOpen(on = false, auto = false, smallestWidthDp = 800, mirror = true))
        assertFalse("nieznana szerokość (0) to nie telefon", Reader.isPhone(0))
    }

    @Test fun enterHidesBarsAndLocks_leaveKeepsMirrorLocked() {
        assertEquals(Reader.State(readOnly = true, chrome = false, editable = true), Reader.enter(mirror = false))
        assertEquals(Reader.State(readOnly = false, chrome = true, editable = true), Reader.leave(mirror = false))
        assertEquals(Reader.State(readOnly = true, chrome = true, editable = false), Reader.leave(mirror = true))
        assertFalse(Reader.enter(mirror = true).editable)
    }

    @Test fun readingProfileTurnsModeOnAndFingerScrolls() {
        val p = Profiles.builtIns(true).single { it.id == Profiles.READER_ID }
        assertEquals("Do odczytu", p.name)
        assertEquals(PVal.B(true), p.values[Reader.KEY])
        assertEquals(PVal.I(Profiles.FINGER_SCROLL), p.values[Profiles.FINGER])
        assertTrue(Profiles.known(Reader.KEY))
        // Wyłączenie profilu wraca do stanu sprzed niego (tryb wyłączony).
        val on = Profiles.switch(p, emptyMap()) { k -> if (k == Reader.KEY) PVal.B(false) else null }
        assertEquals(PVal.B(true), on.write[Reader.KEY])
        val off = Profiles.switch(null, on.backup) { null }
        assertEquals(PVal.B(false), off.write[Reader.KEY])
    }

    @Test fun newBuiltInAddedOnceAndDeletedOnesStayDeleted() {
        val built = Profiles.builtIns(true)
        // Stara lista: użytkownik usunął „Rysowanie” i dodał własny profil; trzy pierwsze gotowe już widział.
        val old = listOf(built[0], built[2], Profile("p4", "Mój", emptyMap()))
        val (list, seen) = Profiles.addNewBuiltIns(old, setOf("wyklad", "rysowanie", "rysik"), built)
        assertEquals(listOf("wyklad", "rysik", "p4", Profiles.READER_ID), list.map { it.id })
        assertTrue(Profiles.READER_ID in seen)
        // Po usunięciu „Do odczytu” kolejne uruchomienie go nie przywraca.
        val (again, _) = Profiles.addNewBuiltIns(list.dropLast(1), seen, built)
        assertEquals(listOf("wyklad", "rysik", "p4"), again.map { it.id })
        // Własny profil o tym samym id nie jest dublowany.
        val (mine, _) = Profiles.addNewBuiltIns(listOf(Profile(Profiles.READER_ID, "Mój odczyt", emptyMap())), emptySet(), built)
        assertEquals(1, mine.count { it.id == Profiles.READER_ID })
    }

    /** Ustawienia trybu muszą istnieć w Settings (profil nadpisujący nieistniejący klucz nic by nie zrobił). */
    @Test fun settingsDeclareReaderKeys() {
        val src = java.io.File("src/main/java/com/mdev/mnotes/data/Settings.kt").readText()
        assertTrue(src.contains("Pref(\"${Reader.KEY}\", false)"))
        assertTrue(src.contains("Pref(\"${Reader.AUTO_KEY}\", true)"))
        assertTrue("domyślnie widziane: trzy pierwsze gotowe profile", src.contains("Pref(\"profile.builtins_seen\", \"wyklad,rysowanie,rysik\")"))
    }
}
