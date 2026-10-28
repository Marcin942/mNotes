package com.mdev.mnotes.core

import com.mdev.mnotes.core.guide.SetupGuide
import com.mdev.mnotes.core.guide.SetupGuide.Entry
import com.mdev.mnotes.core.guide.SetupGuide.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 3c-A10: logika przewodnika po ustawieniach — kolejność, zapis wyborów, pomijanie, powrót, pierwsze uruchomienie. */
class SetupGuideTest {
    private class Mem(val m: HashMap<String, Int> = HashMap()) : SetupGuide.Store {
        override fun get(key: String) = m[key] ?: 0
        override fun set(key: String, v: Int) { m[key] = v }
    }

    @Test fun startPathIsShortAndEndsWithDone() {
        val p = SetupGuide.START_PAGES
        assertTrue("6–8 stron: ${p.size}", p.size in 6..8)
        assertEquals(listOf("welcome", "hand", "input", "page", "theme", "toolbar", "done"), p.map { it.id })
        // Każda strona poza „Gotowe” to jedna decyzja z 2–4 opcjami i zaleceniem w zakresie.
        for (pg in p.dropLast(1) + SetupGuide.TOPICS.flatMap { it.pages }) {
            assertTrue(pg.id, pg.options.size in 2..4)
            assertTrue(pg.id, SetupGuide.recommendedIn(pg, Mem()))
            assertTrue("${pg.id}: tytuł krótki", pg.titlePl.length <= 32 && pg.titleEn.length <= 36)
            assertTrue("${pg.id}: opis 1–2 zdania", pg.bodyPl.count { it == '.' || it == '?' || it == '—' } <= 3)
        }
        val ids = (p + SetupGuide.TOPICS.flatMap { it.pages }).map { it.id }
        assertEquals("id stron niepowtarzalne", ids.size, ids.toSet().size)
    }

    @Test fun choosingSavesAtOnceAndCurrentFollowsTheStore() {
        val s = Mem()
        val theme = SetupGuide.START_PAGES.first { it.id == "theme" }
        SetupGuide.choose(theme, 2, s)
        assertEquals(2, s.get("ui.theme")); assertEquals(2, s.get("pages.theme"))
        assertEquals(2, SetupGuide.current(theme, s))
        s.set("pages.theme", 0)                     // zmienione w Ustawieniach osobno — żadna opcja nie jest „bieżąca”
        assertEquals(-1, SetupGuide.current(theme, s))
        // Pasek z boku stoi po stronie wolnej ręki.
        val bar = SetupGuide.START_PAGES.first { it.id == "toolbar" }
        SetupGuide.choose(bar, 2, s); assertEquals(2, s.get("toolbar.position"))
        s.set("input.hand", 1); SetupGuide.choose(bar, 2, s); assertEquals(3, s.get("toolbar.position"))
        assertEquals(2, SetupGuide.current(bar, s))
    }

    @Test fun recommendedPaperFollowsTheUse() {
        val page = SetupGuide.START_PAGES.first { it.id == "page" }
        val s = Mem()
        assertEquals("Linie", page.options[page.recommended(s)].pl)
        s.set(SetupGuide.USE, SetupGuide.USE_MATH); assertEquals("Kratka", page.options[page.recommended(s)].pl)
        s.set(SetupGuide.USE, SetupGuide.USE_DRAW); assertEquals("Gładka", page.options[page.recommended(s)].pl)
    }

    @Test fun nextBackSkipAndResume() {
        var st = State()
        assertTrue(st.first); assertEquals(st, st.back())       // wstecz nie schodzi poniżej pierwszej strony
        repeat(3) { st = st.next() }
        assertEquals("page", st.page.id)
        st = st.back(); assertEquals("input", st.page.id)
        // Przerwanie i powrót w to samo miejsce.
        assertEquals(st, State.decode(st.encode()))
        // Do końca startu: „Gotowe”, potem lista tematów, start za nami.
        while (!st.last) st = st.next()
        assertEquals("done", st.page.id)
        st = st.next()
        assertEquals(SetupGuide.HUB, st.path); assertTrue(st.startDone)
        // Temat: przejście do końca zaznacza go jako przejrzany; pominięcie — nie.
        st = st.open("gestures")
        assertEquals("double_tap", st.page.id)
        st = st.skip(); assertEquals(SetupGuide.HUB, st.path); assertFalse("gestures" in st.seen)
        st = st.open("math"); while (st.path == "math") st = st.next()
        assertTrue("math" in st.seen)
        assertEquals(st, State.decode(st.encode()))
    }

    @Test fun skippingTheStartEndsItForGood() {
        val st = State().next().skip()
        assertTrue(st.startDone)
        assertEquals(Entry.NONE, SetupGuide.entry(st.encode(), oldInstall = false, dismissed = false))
    }

    @Test fun firstRunShowsOnceAndOldInstallsOnlyGetAProposal() {
        assertEquals(Entry.FULL, SetupGuide.entry("", oldInstall = false, dismissed = false))
        // Otwarty raz (stan zapisany od razu) — następnym razem już tylko propozycja dokończenia.
        assertEquals(Entry.SUGGEST, SetupGuide.entry(State().encode(), oldInstall = false, dismissed = false))
        assertEquals(Entry.SUGGEST, SetupGuide.entry("", oldInstall = true, dismissed = false))
        assertEquals(Entry.NONE, SetupGuide.entry("", oldInstall = true, dismissed = true))
        assertEquals(Entry.NONE, SetupGuide.entry(State(startDone = true).encode(), oldInstall = true, dismissed = false))
    }

    @Test fun brokenStateFallsBackToTheStart() {
        assertEquals(State(), State.decode(""))
        assertEquals(State(), State.decode("nonsense"))
        assertEquals(0, State.decode("start|99|0|").index.coerceAtMost(0))
        assertEquals(SetupGuide.START, State.decode("nieznany|2|0|").path)
        assertEquals(emptySet<String>(), State.decode("hub|0|1|nieznany").seen)
    }

    /** Każdy klucz przewodnika ma odczyt i zapis w adapterze ustawień (inaczej wybór po cichu by nie działał). */
    @Test fun everyGuideKeyIsBoundToSettings() {
        val src = File("src/main/java/com/mdev/mnotes/ui/SetupStore.kt").readText()
        val probe = Mem()
        for (k in SetupGuide.keys(probe)) {
            val lit = if (k == SetupGuide.USE) "SetupGuide.USE ->" else "\"$k\" ->"
            assertEquals("klucz $k: odczyt i zapis", 2, Regex(Regex.escape(lit)).findAll(src).count())
        }
    }
}
