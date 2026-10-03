package com.mdev.mnotes.core.search

import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.TextBox
import java.text.Normalizer

/**
 * Wyszukiwanie tekstu bez zależności od Androida: wielkość liter i znaki diakrytyczne nie mają znaczenia
 * („zolc” znajdzie „Żółć”), a składanie jest znak-w-znak, więc indeksy w złożonym tekście są też indeksami w oryginale.
 */
object TextSearch {
    fun fold(c: Char): Char {
        val l = c.lowercaseChar()
        if (l.code < 128) return l
        return when (l) {
            'ł' -> 'l'
            'ø' -> 'o'
            'đ' -> 'd'
            'ß' -> 's'
            else -> Normalizer.normalize(l.toString(), Normalizer.Form.NFD).firstOrNull() ?: l
        }
    }

    fun fold(s: String): String {
        val out = CharArray(s.length)
        for (i in s.indices) out[i] = fold(s[i])
        return String(out)
    }

    /** Słowa zapytania (złożone); puste zapytanie daje pustą listę. */
    fun words(q: String): List<String> = q.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map(::fold)

    /** Czy wszystkie [words] występują w [foldedText] (kolejność bez znaczenia). */
    fun containsAll(foldedText: String, words: List<String>): Boolean {
        if (words.isEmpty()) return false
        for (w in words) if (!foldedText.contains(w)) return false
        return true
    }

    /** Początki kolejnych, nienakładających się wystąpień frazy [foldedPhrase] w [foldedText]. */
    fun findAll(foldedText: String, foldedPhrase: String): List<Int> {
        if (foldedPhrase.isEmpty()) return emptyList()
        val out = ArrayList<Int>()
        var i = foldedText.indexOf(foldedPhrase)
        while (i >= 0) { out.add(i); i = foldedText.indexOf(foldedPhrase, i + foldedPhrase.length) }
        return out
    }

    /** Pola tekstowe strony, w kolejności czytania (od góry, potem od lewej). */
    fun textBoxes(c: PageContent): List<TextBox> =
        c.elements.filterIsInstance<TextBox>().filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.cy }, { it.cx }))

    fun pageText(c: PageContent): String = textBoxes(c).joinToString("\n") { it.text }

    /** Krótki fragment wokół trafienia, z białymi znakami zwiniętymi do spacji i wielokropkami po bokach. */
    fun snippet(text: String, start: Int, len: Int, radius: Int = 36): String {
        val a = (start - radius).coerceAtLeast(0)
        val b = (start + len + radius).coerceAtMost(text.length)
        val body = text.substring(a, b).replace(Regex("\\s+"), " ").trim()
        return (if (a > 0) "…" else "") + body + (if (b < text.length) "…" else "")
    }
}
