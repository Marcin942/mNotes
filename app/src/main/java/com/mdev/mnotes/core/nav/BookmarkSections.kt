package com.mdev.mnotes.core.nav

/** Zakładka na stronie o indeksie [page] (od 0) i tytule [title]. */
data class Mark(val page: Int, val title: String)

/** Koniec przedziału liczonego od zakładki: najbliższa następna zakładka, wskazana zakładka albo koniec zeszytu. */
sealed interface SectionEnd {
    data object Next : SectionEnd
    data class At(val markIndex: Int) : SectionEnd
    data object NotebookEnd : SectionEnd
}

/** Fragment zeszytu: [title] i strony [pages] (od 0, kolejne). */
data class Part(val title: String, val pages: IntRange)

/**
 * Dzieli zeszyt na sekcje wyznaczone zakładkami: sekcja zaczyna się na stronie zakładki i trwa do strony następnej zakładki
 * (bez niej). Strony przed pierwszą zakładką tworzą sekcję początkową. Logika czysta — testowana jednostkowo.
 */
object BookmarkSections {
    /** Zakładki w kolejności stron, bez powtórzeń na jednej stronie i bez stron spoza zeszytu o [count] stronach. */
    fun normalize(marks: List<Mark>, count: Int): List<Mark> =
        marks.filter { it.page in 0 until count }.sortedBy { it.page }.distinctBy { it.page }

    /** Czy przed pierwszą zakładką są strony (wtedy „początek zeszytu” jest sensownym początkiem eksportu). */
    fun hasIntro(marks: List<Mark>): Boolean = marks.firstOrNull()?.let { it.page > 0 } ?: false

    /** Indeks zakładki, do której sekcji należy strona [page]; −1 = sekcja początkowa. */
    fun sectionOf(marks: List<Mark>, page: Int): Int = marks.indexOfLast { it.page <= page }

    /**
     * Strony od zakładki [from] (−1 = początek zeszytu) do końca [end] z pominięciem strony kończącej, chyba że [includeEndPage].
     * [marks] pochodzą z [normalize]. Null, gdy przedział byłby pusty albo [from] / [end] nie istnieją.
     */
    fun range(marks: List<Mark>, count: Int, from: Int, end: SectionEnd, includeEndPage: Boolean = false): IntRange? {
        if (count <= 0 || from !in -1 until marks.size) return null
        val first = if (from < 0) 0 else marks[from].page
        val stop = stop(marks, count, from, end) ?: return null
        val last = if (includeEndPage && stop < count) stop else stop - 1
        return if (last >= first) first..last else null
    }

    /**
     * Przedział z [range] pocięty na sekcje wg zakładek, które w nim leżą; [introTitle] to nazwa sekcji początkowej.
     * Strona kończąca (przy [includeEndPage]) dołącza do ostatniej sekcji, nie tworzy nowej.
     */
    fun parts(marks: List<Mark>, count: Int, from: Int, end: SectionEnd, includeEndPage: Boolean, introTitle: String): List<Part> {
        val whole = range(marks, count, from, end, includeEndPage) ?: return emptyList()
        val stop = stop(marks, count, from, end) ?: return emptyList()
        val starts = ArrayList<Pair<Int, String>>()
        starts.add(whole.first to (if (from < 0) introTitle else marks[from].title))
        for (m in marks) if (m.page > whole.first && m.page < stop) starts.add(m.page to m.title)
        return starts.mapIndexed { i, (page, title) ->
            val last = if (i + 1 < starts.size) starts[i + 1].first - 1 else whole.last
            Part(title, page..last)
        }
    }

    /** Pierwsza strona, której przedział już nie obejmuje (przed dopuszczeniem strony kończącej). */
    private fun stop(marks: List<Mark>, count: Int, from: Int, end: SectionEnd): Int? = when (end) {
        SectionEnd.NotebookEnd -> count
        SectionEnd.Next -> marks.getOrNull(from + 1)?.page ?: count
        is SectionEnd.At -> if (end.markIndex in marks.indices && end.markIndex > from) marks[end.markIndex].page else null
    }
}
