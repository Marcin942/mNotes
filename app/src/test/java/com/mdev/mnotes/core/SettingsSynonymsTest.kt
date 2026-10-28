package com.mdev.mnotes.core

import com.mdev.mnotes.core.search.SettingsQuery
import com.mdev.mnotes.core.search.SettingsSynonyms
import com.mdev.mnotes.core.search.TextSearch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2b-A4: wyszukiwarka ustawień zna słowa skojarzone. Wiersze (`ToggleRow` i `Opt` z etykietą `T.t("…", "…")`) są czytane
 * prosto ze źródeł ekranu Ustawień i kategorii „Laboratorium”, więc nowy wiersz bez synonimów od razu wywróci test.
 */
class SettingsSynonymsTest {
    private class Row(val pl: String, val en: String, val keys: String)

    private val sources = listOf(
        "src/main/java/com/mdev/mnotes/ui/SettingsScreen.kt",
        "src/lab/java/com/mdev/mnotes/lab/Lab.kt",
    )

    private val q = "(?:[^\"\\\\]|\\\\.)*"
    private val toggle = Regex("ToggleRow\\(\\s*T\\.t\\(\"($q)\",\\s*\"($q)\"\\),(?:[^\\n]*?keys = \"($q)\")?")
    private val opt = Regex("Opt\\(\\s*T\\.t\\(\"($q)\",\\s*\"($q)\"\\)(?:,\\s*\"($q)\")?")

    private fun rows(): List<Row> = sources.map { File(it) }.filter { it.exists() }.flatMap { f ->
        val s = f.readText()
        (toggle.findAll(s) + opt.findAll(s)).map { Row(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.toList()
    }

    private fun words(s: String) = TextSearch.fold(s).split(Regex("[^\\p{L}\\p{N}-]+")).filter { it.length >= 3 }.toSet()

    @Test fun everySettingHasAtLeastThreeSynonyms() {
        val all = rows()
        assertTrue("za mało wierszy: ${all.size}", all.size >= 60)
        val poor = all.filter { r ->
            val label = words(r.pl)
            val syn = (words(r.keys) - label) + SettingsSynonyms.of(r.pl + " " + r.keys)
            syn.size < 3
        }
        assertTrue("wiersze z < 3 synonimami: " + poor.joinToString { "„${it.pl}”" }, poor.isEmpty())
    }

    private fun hits(query: String): List<String> {
        val sq = SettingsQuery.of(query)!!
        return rows().filter { sq.matches(it.pl, it.keys) }.map { it.pl }
    }

    @Test fun colloquialQueriesFindTheRightSetting() {
        fun expect(query: String, fragment: String) {
            val h = hits(query)
            assertTrue("„$query” nie znalazło „$fragment” (znalazło: $h)", h.any { fragment in it })
        }
        expect("mazanie", "Rozmiar gumki")
        expect("scieranie", "gumki")              // bez polskich znaków
        expect("lupa", "Największe powiększenie")
        expect("przyblizanie", "powiększenie")
        expect("wibracje", "wibracja")
        expect("tooltip", "Podpowiedź")
        expect("magnes", "Przyciągaj do siatki")
        expect("ciemny", "Motyw")
        expect("eraser", "gumki")
        expect("mapa", "Mini-mapa")
        expect("pierwiastek", "√")
        expect("zoom", "powiększenie")
        expect("gum", "Rozmiar gumki")            // fragment słowa
        expect("wskaznik", "lasera")              // przegląd §3.2a: kolor lasera także w Ustawieniach
    }

    /** 2c-A12: przycinanie słów nie łączy różnych wyrazów o wspólnym początku; zwykłe odmiany i fragmenty dalej trafiają. */
    @Test fun trimmingDoesNotJoinDifferentWords() {
        fun miss(query: String, label: String, keys: String = "") =
            assertFalse("„$query” trafiło w „$label”", SettingsQuery.of(query)!!.matches(label, keys))
        miss("obraz", "Obracanie i kąty", "obrót obracanie kąt snap rotate angle")
        miss("kolor", "Koło zębate")
        miss("share", "Image sharpness")
        miss("ruled", "Show ruler")
        assertTrue(hits("ścieranie").any { "gumki" in it })
        assertTrue(hits("gum").any { "gumki" in it })
        assertTrue(hits("lupa").any { "powiększenie" in it })
        assertTrue(hits("ciemny").any { "Motyw" in it })
        assertTrue(SettingsQuery.of("profil")!!.matches("Profile pracy"))
        assertTrue(SettingsQuery.of("obraz")!!.matches("Obrazy na stronie"))
        assertTrue(SettingsQuery.of("kolor")!!.matches("Kolory pióra"))
    }

    /** 2c-A15: para „Przytrzymaj…” / „Czas przytrzymania” stoi razem — wiersz o zdjęciach jej nie rozdziela. */
    @Test fun holdPairStaysTogether() {
        val rows = File(sources[0]).readLines().filter { "ToggleRow(T.t(\"" in it || "Opt(T.t(\"" in it }
        val i = rows.indexOfFirst { "Przytrzymaj, aby wyprostować" in it }
        assertTrue(i >= 0)
        assertTrue(rows[i + 1].trim(), "Czas przytrzymania" in rows[i + 1])
    }

    @Test fun synonymsDoNotMatchInsideOtherWords() {
        // „cofnij” ma w grupie „back”, ale nie może przez to znaleźć „background”/„tło”.
        val sq = SettingsQuery.of("cofnij")!!
        assertTrue(!sq.matches("Wzór strony", "background pattern"))
        assertTrue(sq.matches("Cofnij dotknięciem dwóch palców", "undo"))
    }
}
