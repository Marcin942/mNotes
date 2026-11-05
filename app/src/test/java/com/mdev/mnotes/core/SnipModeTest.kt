package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.SnipMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** LP-2: tryby wycinka. */
class SnipModeTest {

    @Test fun unknownValueFallsBackToAsk() {
        assertEquals(SnipMode.ASK, SnipMode.of(7))
        assertEquals(SnipMode.ASK, SnipMode.of(-1))
        assertEquals(SnipMode.SHARE, SnipMode.of(SnipMode.SHARE))
    }

    @Test fun legacySwitchMapsToClipboardOrAsk() {
        assertEquals(SnipMode.CLIPBOARD, SnipMode.fromLegacy(true))
        assertEquals(SnipMode.ASK, SnipMode.fromLegacy(false))
    }

    @Test fun clipboardCommandTogglesWithAsk() {
        assertEquals(SnipMode.CLIPBOARD, SnipMode.toggleClipboard(SnipMode.ASK))
        assertEquals(SnipMode.ASK, SnipMode.toggleClipboard(SnipMode.CLIPBOARD))
        assertEquals(SnipMode.CLIPBOARD, SnipMode.toggleClipboard(SnipMode.SHARE))
    }

    @Test fun onlyAskShowsBar() {
        assertTrue(SnipMode.showsBar(SnipMode.ASK))
        for (m in listOf(SnipMode.INSERT, SnipMode.CLIPBOARD, SnipMode.SHARE)) assertFalse(SnipMode.showsBar(m))
    }

    @Test fun insertGoesToMostRecentOtherWritableDocument() {
        // źródło 0 (slajdy), notatki 1 i 2; w 2 pisano ostatnio
        assertEquals(2, SnipMode.insertTarget(0, listOf(true, true, true), listOf(50L, 10L, 20L)))
        // jedyny inny jest do odczytu → do źródła
        assertEquals(0, SnipMode.insertTarget(0, listOf(true, false), listOf(0L, 99L)))
        // wszystko do odczytu
        assertEquals(-1, SnipMode.insertTarget(0, listOf(false, false), listOf(0L, 0L)))
        // samo źródło
        assertEquals(0, SnipMode.insertTarget(0, listOf(true), listOf(0L)))
    }
}
