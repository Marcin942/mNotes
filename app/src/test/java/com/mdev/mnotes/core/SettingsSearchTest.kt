package com.mdev.mnotes.core

import com.mdev.mnotes.core.search.SettingsPhrases
import com.mdev.mnotes.core.search.SettingsQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 3c-A6: wyszukiwarka ustawień rozumie zapytania „ludzkie”: literówki, brak polskich znaków, odmianę, kolejność, opisy
 * problemu. Wiersze czytane ze źródeł jak w [SettingsSynonymsTest]; słownik literówek = słowa tych wierszy (w aplikacji
 * zbierany z ekranu). Każde zapytanie musi mieć oczekiwany wiersz w pierwszych trzech wynikach.
 */
class SettingsSearchTest {
    private class Row(val pl: String, val en: String, val keys: String)

    private val q = "(?:[^\"\\\\]|\\\\.)*"
    private val toggle = Regex("ToggleRow\\(\\s*T\\.t\\(\"($q)\",\\s*\"($q)\"\\),(?:[^\\n]*?keys = \"($q)\")?")
    private val opt = Regex("Opt\\(\\s*T\\.t\\(\"($q)\",\\s*\"($q)\"\\)(?:,\\s*\"($q)\")?")

    private val rows: List<Row> = listOf("src/main/java/com/mdev/mnotes/ui/SettingsScreen.kt").map { File(it) }.flatMap { f ->
        val s = f.readText()
        (toggle.findAll(s) + opt.findAll(s)).map { Row(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.toList()
    }
    private val vocab = SettingsPhrases.vocabulary(rows.flatMap { listOf(it.pl, it.en, it.keys) })

    private fun top(query: String, n: Int = 3): List<String> {
        val sq = SettingsQuery.of(query, vocab)!!
        return rows.map { it to sq.score(it.pl, it.en, it.keys) }.filter { it.second > 0 }
            .sortedByDescending { it.second }.take(n).map { it.first.pl }
    }

    /** 3c-A17b: „Ustawienia tej funkcji” z przytrzymanego przycisku paska trafiają we właściwy wiersz. */
    @Test fun barAnchorsLandOnTheirSetting() {
        val expected = mapOf(
            "undo" to "Cofnij dotknięciem dwóch palców", "ballpoint" to "Wygładzanie", "highlighter" to "Wygładzanie",
            "eraser" to "Domyślny tryb gumki", "painter" to "Tryb pędzla formatu", "fill" to "Wiaderko domyka",
            "pipette" to "Pobrany kolor zostaje", "lasso" to "Kształt lassa", "text" to "Grecki i indeksy",
            "laser" to "Kolor śladu lasera", "hand" to "Ponowne dotknięcie lasera", "curve" to "Trzymaj dalej",
            "ruler" to "Przyciągaj do równych kątów", "geometry" to "Przyczep do figur",
        )
        val misses = expected.mapNotNull { (id, row) ->
            val q = com.mdev.mnotes.core.search.SettingsAnchors.of(id)!!
            if (top(q).any { it.startsWith(row) }) null else "$id („$q”) → $row: ${top(q)}"
        }
        assertEquals(emptyList<String>(), misses)
        assertNull("taśma nie ma własnych ustawień", com.mdev.mnotes.core.search.SettingsAnchors.of("tape"))
        // Każdy przycisk paska z kotwicą istnieje w źródle paska (literówka w id po cichu wyłączyłaby pozycję menu).
        val bar = File("src/main/java/com/mdev/mnotes/ui/EditorScreen.kt").readText()
        for (id in expected.keys + listOf("redo", "fountain", "pencil", "brush", "protractor")) {
            val pen = com.mdev.mnotes.core.model.PenKind.entries.any { it.name.lowercase() == id }
            assertTrue(id, pen || bar.contains("\"$id\""))
        }
    }

    @Test fun typicalQueriesFindTheRightSettingFirst() {
        val cases = listOf(
            "żeby ręka nie zasłaniała" to "Piszę",
            "lewa ręka" to "Piszę",
            "leworęczny" to "Piszę",
            "mańkut" to "Piszę",
            "piszę lewą" to "Piszę",
            "przewiń stronę dalej od brzegu" to "Przewijanie o pół ekranu",
            "ciemne tło" to "Strony zeszytów|Motyw",
            "ciemny ekran" to "Motyw|Strony zeszytów",
            "oczy mnie bolą wieczorem" to "Motyw|Strony zeszytów",
            "motyw ciemny" to "Motyw",
            "gumka" to "gumki",
            "gumak" to "gumki",
            "rozmiar gumki" to "Rozmiar gumki",
            "gumka zakreślacz" to "Gumka pomija zakreślacz",
            "powiekszenie" to "powiększenie",
            "powiększnie" to "powiększenie",
            "wygladzanie" to "Wygładzanie",
            "wygłdzanie" to "Wygładzanie",
            "drżące pismo" to "Wygładzanie",
            "jak zmienić kolor lasera" to "Kolor śladu lasera",
            "lasera kolor" to "Kolor śladu lasera",
            "lassso" to "lass",
            "przypadkowe kreski od dłoni" to "Odrzucanie dłoni",
            "dłoń rysuje" to "Odrzucanie dłoni",
            "palec rysuje" to "Palec",
            "za grube linie" to "grubości",
            "samo prostuje kształty" to "wyprostować",
            "zablokować notatki" to "Blokada aplikacji",
            "nie chcę zgubić notatek" to "Kopiuj automatycznie",
            "aplikacja wolno działa" to "Jakość wyświetlania",
            "drukowanie" to "PDF",
            "pusta kartka" to "Wzór|Papier",
            "kratka na nowej stronie" to "Wzór",
            "większe przyciski" to "Rozmiar",
            "wibracje" to "wibracja",
            "brzęczy przy dotknięciu" to "wibracja",
            "angielski" to "Język",
            "english" to "Język",
            "mysz i klawiatura" to "Zagęszczony|Pasek menu",
            "kilka zeszytów naraz" to "karty",
            "cofanie dwoma palcami" to "Cofnij dotknięciem",
            "podwójne stuknięcie" to "Podwójne dotknięcie",
            "przycisk na rysiku" to "Przycisk rysika",
            "zdjęcie do kratki" to "Wstawione zdjęcie",
            "przyciąganie kątów" to "równych kątów",
            "pierwiastek" to "√",
            "greckie litery" to "Grecki",
            "pasek zniknął" to "Położenie|Pasek menu",
            "how to change theme" to "Motyw",
            "palm rejection" to "Odrzucanie dłoni",
            "left handed" to "Piszę",
        )
        assertTrue(cases.size >= 40)
        val bad = cases.mapNotNull { (query, want) ->
            val t = top(query)
            if (want.split('|').any { w -> t.any { w in it } }) null else "„$query” → $t"
        }
        assertTrue("nie trafiły (${bad.size}):\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun typoGivesASuggestionInScreenSpelling() {
        assertEquals("powiększenie", SettingsQuery.of("powiększnie", vocab)!!.suggestion)
        assertNull(SettingsQuery.of("gumka", vocab)!!.suggestion)          // znane słowo — bez „Czy chodziło o…”
        assertNull(SettingsQuery.of("ab", vocab)!!.suggestion)             // za krótkie, żeby zgadywać
    }

    @Test fun fillersAloneFindNothingAndExactBeatsLooser() {
        assertTrue(top("jak to").isEmpty())
        val sq = SettingsQuery.of("gumka", vocab)!!
        assertTrue(sq.score("Rozmiar gumki") > SettingsQuery.of("gumak", vocab)!!.score("Rozmiar gumki"))
        // 2c-A12 dalej obowiązuje: poprawianie literówek nie łączy różnych wyrazów.
        assertFalse(SettingsQuery.of("obraz", vocab)!!.matches("Obracanie i kąty", "obrót obracanie kąt snap rotate angle"))
    }

    @Test fun editDistanceCountsSwapsAsOne() {
        assertEquals(1, SettingsPhrases.distance("gumak", "gumka", 2))
        assertEquals(1, SettingsPhrases.distance("lassso", "lasso", 2))
        assertEquals(3, SettingsPhrases.distance("abc", "xyzabc", 2))
    }
}
