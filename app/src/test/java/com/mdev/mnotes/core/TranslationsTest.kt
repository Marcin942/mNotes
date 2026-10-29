package com.mdev.mnotes.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 3c-A15: każdy tekst interfejsu ma wersję angielską. Teksty są w kodzie jako `T.t("polski", "English")`, więc test
 * czyta źródła warstwy UI: polski napis (z ogonkami) bez sąsiedniego drugiego napisu to tekst bez tłumaczenia, a para,
 * w której drugi napis ma polskie znaki — zamienione albo nieprzetłumaczone. Słowa kluczowe wyszukiwarki ustawień
 * (`keys = …`, drugi napis po etykiecie) są celowo dwujęzyczne w jednym napisie i test je pomija. Komunikaty `core`
 * (wyjątki formatu, dziennik łącza dla diagnostyki) nie są tu sprawdzane.
 */
class TranslationsTest {
    private val pl = Regex("[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]")
    private val lit = Regex("\"((?:[^\"\\\\\\n]|\\\\.)*)\"")
    private val pair = Regex("\\bT\\.t\\(\\s*${lit.pattern}\\s*,\\s*${lit.pattern}\\s*\\)")

    private val sources: List<File> = (
        listOf("ui", "editor", "app", "render").map { File("src/main/java/com/mdev/mnotes/$it") } +
            listOf(File("src/foss/java"), File("src/gms/java"))
        ).flatMap { d -> d.walk().filter { it.isFile && it.extension == "kt" && "/nearby/" !in it.invariantSeparatorsPath }.toList() }

    /** Kod bez komentarzy (te same długości i wiersze, żeby numery wierszy się zgadzały). */
    private fun code(f: File): String = f.readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)) { m -> m.value.replace(Regex("[^\\n]"), " ") }
        .replace(Regex("(?<![:\"\\\\])//[^\\n]*")) { m -> " ".repeat(m.value.length) }

    private fun where(f: File, src: String, at: Int) = "${f.name}:${src.substring(0, at).count { it == '\n' } + 1}"

    @Test fun sourcesAreFound() {
        assertTrue("nie znaleziono źródeł UI (katalog roboczy testu: moduł app)", sources.size > 20)
    }

    @Test fun everyPolishTextHasAnEnglishNeighbour() {
        val bad = ArrayList<String>()
        for (f in sources) {
            val src = code(f)
            for (m in lit.findAll(src)) {
                if (!pl.containsMatchIn(m.groupValues[1])) continue
                val after = src.substring(m.range.last + 1, minOf(src.length, m.range.last + 400))
                if (Regex("^\\s*[,+]\\s*\"").containsMatchIn(after)) continue
                val before = src.substring(maxOf(0, m.range.first - 200), m.range.first)
                if (Regex("\"\\s*[,+]\\s*$").containsMatchIn(before)) continue
                if (Regex("(keys\\s*=|keys|\\),|Group\\(|_QUERY\\s*=|\\.Tool\\.[A-Z]+,)\\s*$").containsMatchIn(before)) continue
                bad += "${where(f, src, m.range.first)} „${m.groupValues[1].take(60)}”"
            }
        }
        assertTrue("Teksty bez wersji angielskiej (użyj T.t(\"…\", \"…\")):\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun englishSideIsEnglish() {
        val bad = ArrayList<String>()
        var n = 0
        for (f in sources) {
            val src = code(f)
            for (m in pair.findAll(src)) {
                n++
                val (p, e) = m.destructured
                if (pl.containsMatchIn(e) || (e.isBlank() && p.isNotBlank())) bad += "${where(f, src, m.range.first)} „$p” / „$e”"
            }
        }
        assertTrue("za mało par T.t — zmienił się zapis tekstów? ($n)", n > 500)
        assertTrue("Druga (angielska) wersja ma polskie znaki albo jest pusta:\n" + bad.joinToString("\n"), bad.isEmpty())
    }
}
