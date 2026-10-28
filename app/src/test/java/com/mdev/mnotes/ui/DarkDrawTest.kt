package com.mdev.mnotes.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 3c-A1: każde wektorowe rysowanie strony w NoteView (poza gotowymi kafelkami) idzie przez przekształcenie trybu
 * ciemnego/jasnego. Duch po przesunięciu rysował bez niego i biały obiekt błyskał na biało, zanim doszły kafelki.
 */
class DarkDrawTest {
    @Test fun everyElementDrawInNoteViewIsThemed() {
        val lines = File("src/main/java/com/mdev/mnotes/editor/NoteView.kt").readLines()
        val draws = lines.indices.filter { Regex("""renderer\.(drawElements?|drawPaper)\(""").containsMatchIn(lines[it]) }
        assertTrue(draws.size >= 4)
        for (i in draws) {
            // Najbliższe wcześniejsze ustawienie motywu rysownika musi być prawdziwe, nie „= null” (wtedy kolory surowe).
            val set = (i downTo maxOf(0, i - 12)).firstOrNull { lines[it].contains("renderer.theme = ") }
            assertTrue("NoteView.kt:${i + 1} rysuje bez motywu strony: ${lines[i].trim()}",
                set != null && !lines[set].substringAfter("renderer.theme = ").startsWith("null"))
        }
    }
}
