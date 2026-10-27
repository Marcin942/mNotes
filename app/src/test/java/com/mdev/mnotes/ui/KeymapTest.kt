package com.mdev.mnotes.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KeymapTest {
    @Test fun parseAndLabelRoundTrip() {
        for (s in listOf("Ctrl+Shift+N", "Ctrl+=", "Ctrl+,", "F11", "Shift+F3", "PgDn", "Alt+←", "P", "[", "Ctrl+Alt+Shift+Del")) {
            val c = Chord.parse(s)
            assertNotNull(s, c)
            assertEquals(s, c!!.label())
        }
    }

    @Test fun parseIsCaseAndOrderTolerant() {
        assertEquals(Chord.parse("Ctrl+Shift+N"), Chord.parse("shift+CTRL+n"))
    }

    @Test fun parseRejectsGarbage() {
        assertNull(Chord.parse(""))
        assertNull(Chord.parse("Ctrl+"))
        assertNull(Chord.parse("Ctrl+Foo"))
    }

    @Test fun defaultsAreValidAndConflictFree() {
        val ids = CmdDefs.list.map { it.id }
        assertEquals("duplicate command ids", ids.size, ids.toSet().size)
        val seen = HashMap<Chord, String>()
        for (d in CmdDefs.list) {
            if (d.key.isEmpty()) continue
            val c = Chord.parse(d.key)
            assertNotNull("unparsable default for ${d.id}: ${d.key}", c)
            val prev = seen.put(c!!, d.id)
            assertTrue("${d.id} and $prev share ${d.key}", prev == null)
        }
    }

    /** 2b-A12/A14: każde polecenie z listy ma akcję w mapie edytora — paleta nie pokaże martwej pozycji. */
    @Test fun everyCommandHasAnAction() {
        val src = File("src/main/java/com/mdev/mnotes/ui/EditorDialogs.kt").readText()
        val missing = CmdDefs.list.map { it.id }.filter { id -> "\"" + id + "\" to (" !in src }
        assertTrue("polecenia bez akcji: " + missing, missing.isEmpty())
    }
}
