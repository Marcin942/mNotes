package com.mdev.mnotes.core

import com.mdev.mnotes.core.prefs.PrefValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 3c-A16: import ustawień — org.json zapisuje 20f jako `20` (wraca Int), a Int pod kluczem Float wywracał start aplikacji. */
class PrefValueTest {
    @Test fun wholeFloatReadBackAsIntBecomesFloat() {
        assertEquals(20f, PrefValue.coerce(14f, 20))
        assertEquals(1.5f, PrefValue.coerce(1f, 1.5))
    }

    @Test fun valueTypeFollowsTheSetting() {
        assertEquals(3, PrefValue.coerce(0, 3))
        assertEquals(3, PrefValue.coerce(0, 3.0))
        assertNull(PrefValue.coerce(0, 2.5))          // ułamek pod kluczem Int: pomijany
        assertEquals(7L, PrefValue.coerce(0L, 7))
        assertEquals(true, PrefValue.coerce(false, true))
        assertNull(PrefValue.coerce(false, "tak"))
        assertNull(PrefValue.coerce("", 4))
        assertEquals("pl", PrefValue.coerce("", "pl"))
    }

    @Test fun unknownKeyKeepsFileType() {
        assertEquals(5, PrefValue.coerce(null, 5))
        assertEquals(0.25f, PrefValue.coerce(null, 0.25))
        assertEquals("x", PrefValue.coerce(null, "x"))
        assertNull(PrefValue.coerce(null, listOf(1)))
    }
}
