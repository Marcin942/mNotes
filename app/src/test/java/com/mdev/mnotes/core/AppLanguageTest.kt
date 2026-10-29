package com.mdev.mnotes.core

import com.mdev.mnotes.core.prefs.AppLanguage
import com.mdev.mnotes.core.prefs.AppLanguage.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

/** 3c-A15: język z Ustawień i systemowy „Język aplikacji” pokazują to samo, wygrywa ostatnia zmiana. */
class AppLanguageTest {
    @Test fun tagsAndModesRoundTrip() {
        for (m in 0..2) assertEquals(m, AppLanguage.mode(AppLanguage.tag(m)))
        assertEquals(1, AppLanguage.mode("pl-PL"))
        assertEquals(2, AppLanguage.mode("en-GB,pl"))
        assertEquals(2, AppLanguage.mode("EN_us"))
        assertEquals("nieobsługiwany język = systemowy", 0, AppLanguage.mode("de-DE"))
        assertEquals(0, AppLanguage.mode(""))
    }

    @Test fun choiceInTheAppGoesToTheSystem() {
        // Wcześniej uzgodnione „jak w systemie”, teraz w Ustawieniach angielski.
        assertEquals(Decision(2, "en", "en"), AppLanguage.reconcile(2, "", ""))
        // Powrót do „Systemowy” czyści wybór w systemie.
        assertEquals(Decision(0, "", ""), AppLanguage.reconcile(0, "pl", "pl"))
    }

    @Test fun choiceInSystemSettingsGoesToTheApp() {
        assertEquals(Decision(1, null, "pl"), AppLanguage.reconcile(2, "pl-PL", "en"))
        // „Domyślny systemu” w ustawieniach Androida — aplikacja wraca do „Systemowy”.
        assertEquals(Decision(0, null, ""), AppLanguage.reconcile(1, "", "pl"))
    }

    @Test fun agreementChangesNothing() {
        assertEquals(Decision(1, null, "pl"), AppLanguage.reconcile(1, "pl-PL", "pl"))
        assertEquals(Decision(0, null, ""), AppLanguage.reconcile(0, "", null))
        // Po własnym ustawieniu systemu (zmiana konfiguracji wraca do aplikacji) nie ma pętli.
        val d = AppLanguage.reconcile(2, "", "")
        assertEquals(Decision(2, null, "en"), AppLanguage.reconcile(d.mode, d.push!!, d.last))
    }

    @Test fun firstReconcileAfterUpdate() {
        // Język ustawiony w aplikacji przed tą wersją — trafia do systemu.
        assertEquals(Decision(1, "pl", "pl"), AppLanguage.reconcile(1, "", null))
        // Język wybrany w systemie przed pierwszym otwarciem — aplikacja go przyjmuje.
        assertEquals(Decision(2, null, "en"), AppLanguage.reconcile(0, "en", null))
        assertEquals(Decision(2, null, "en"), AppLanguage.reconcile(1, "en", null))
    }
}
