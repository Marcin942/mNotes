package com.mdev.mnotes.core.export

/**
 * Folder wymiany po stronie czytającej (3c-A8): drugie urządzenie wczytuje z folderu (SAF) pliki `.mnotes` zapisane przez
 * tablet ([com.mdev.mnotes.data.SyncFolder]) jako kopie tylko do odczytu. Synchronizację folderu robi zewnętrzna aplikacja.
 *
 * Zeszyt rozpoznajemy po identyfikatorze z `meta.json` archiwum („źródło”), nie po nazwie pliku: po zmianie tytułu na
 * tablecie stary plik zostaje w folderze jako archiwum, a nowy ma inną nazwę — wygrywa nowszy plik tego samego źródła
 * i podmienia tę samą kopię, zamiast dokładać drugą. Źródło pliku znamy dopiero po rozpakowaniu, więc pamiętamy je dla
 * (ścieżka, czas zmiany, rozmiar): niezmieniony plik nie jest pobierany drugi raz.
 */
object ExchangeIn {
    /** Plik w folderze: ścieżka względem folderu, czas zmiany od dostawcy (ms), rozmiar w bajtach. */
    data class Entry(val path: String, val modified: Long, val size: Long)

    /** Zapamiętane źródło pliku o tym czasie i rozmiarze. */
    data class Seen(val modified: Long, val size: Long, val src: String)

    /** Kopia źródła w bibliotece: zeszyt [nbId] wczytany z pliku o czasie [modified]. */
    data class Copy(val nbId: String, val modified: Long)

    sealed class Act {
        abstract val src: String
        abstract val entry: Entry
        /** Nowa kopia (też wtedy, gdy poprzednią usunięto, a przyszła nowsza wersja). */
        data class Add(override val src: String, override val entry: Entry) : Act()
        /** Nowsza wersja podmienia treść kopii [nbId] (ten sam identyfikator: karty, przypięcia, statystyki zostają). */
        data class Replace(override val src: String, override val entry: Entry, val nbId: String) : Act()
    }

    /** Plan przebiegu; [busy] — źródła z nowszą wersją, których kopia jest teraz otwarta (wczytają się następnym razem). */
    class Plan(val acts: List<Act>, val busy: List<String>)

    /** Czy plik o tej nazwie bierzemy pod uwagę: `.mnotes`, bez plików ukrytych i tymczasowych dostawców. */
    fun candidate(name: String): Boolean =
        name.endsWith(".mnotes", ignoreCase = true) && !name.startsWith(".") && !name.startsWith("~")

    /** Pliki, których źródła nie znamy: nowe albo zmienione od ostatniego razu — trzeba je pobrać i rozpakować. */
    fun unknown(files: List<Entry>, seen: Map<String, Seen>): List<Entry> = files.filter { f -> !matches(seen[f.path], f) }

    private fun matches(s: Seen?, f: Entry) = s != null && s.modified == f.modified && s.size == f.size

    /**
     * Co zrobić z plikami [files]: [seen] — znane źródła plików, [copies] — źródło → kopia, [alive] — zeszyty w
     * bibliotece (nie w koszu), [busy] — zeszyty otwarte teraz, [own] — zeszyty tego urządzenia, które nie są kopiami
     * (folder wymiany wskazujący własne lustro nie dubluje zeszytów).
     */
    fun plan(files: List<Entry>, seen: Map<String, Seen>, copies: Map<String, Copy>, alive: Set<String>, busy: Set<String>, own: Set<String>): Plan {
        val acts = ArrayList<Act>()
        val waiting = ArrayList<String>()
        val bySrc = files.mapNotNull { f -> seen[f.path]?.takeIf { matches(it, f) }?.let { it.src to f } }.groupBy({ it.first }, { it.second })
        for ((src, list) in bySrc.entries.sortedBy { it.key }) {
            // Pusty — plik, który się nie rozpakował (uszkodzony, nie .mnotes): czeka, aż się zmieni.
            if (src.isEmpty() || src in own) continue
            val newest = list.maxWith(compareBy<Entry> { it.modified }.thenBy { it.path })
            val copy = copies[src]
            when {
                copy == null -> acts.add(Act.Add(src, newest))
                newest.modified <= copy.modified -> {}
                copy.nbId !in alive -> acts.add(Act.Add(src, newest))
                copy.nbId in busy -> waiting.add(src)
                else -> acts.add(Act.Replace(src, newest, copy.nbId))
            }
        }
        return Plan(acts, waiting)
    }

    /** Katalogi pliku (bez nazwy) — kopia trafia do folderu biblioteki o tej samej ścieżce pod folderem wymiany. */
    fun dirs(path: String): List<String> = path.split('/').dropLast(1).filter { it.isNotEmpty() }

    /** Tytuł kopii, gdy archiwum nie ma własnego: nazwa pliku bez rozszerzenia. */
    fun titleOf(path: String): String = path.substringAfterLast('/').let { if (it.endsWith(".mnotes", true)) it.dropLast(7) else it }
}
