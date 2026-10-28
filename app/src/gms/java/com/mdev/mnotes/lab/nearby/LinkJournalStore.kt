package com.mdev.mnotes.lab.nearby

import android.content.Context
import com.mdev.mnotes.core.nearby.LinkJournal
import java.io.File
import java.util.concurrent.Executors

/**
 * Dziennik łącza w pamięci prywatnej aplikacji (3b-B1): jeden na proces, wczytywany przy pierwszym użyciu z
 * `files/lacze/dziennik.txt`, zapisywany w tle, gdy coś się zmieniło (okno łącza woła [save] co kilka sekund i przy
 * zamknięciu). Plik nie wychodzi z urządzenia sam: tylko przez „Eksportuj” w Ustawieniach.
 */
class LinkJournalStore private constructor(private val file: File) {
    val journal: LinkJournal = runCatching { LinkJournal.decode(file.readText()) }.getOrElse { LinkJournal() }

    fun save() {
        if (!journal.dirty) return
        val text = journal.encode()
        io.execute {
            runCatching {
                file.parentFile?.mkdirs()
                val tmp = File(file.path + ".tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) { file.writeText(text); tmp.delete() }
            }
        }
    }

    fun clear() { journal.clear(); save() }

    companion object {
        private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "dziennik-lacza").apply { isDaemon = true } }
        private var instance: LinkJournalStore? = null

        fun of(ctx: Context): LinkJournalStore =
            instance ?: LinkJournalStore(File(ctx.applicationContext.filesDir, "lacze/dziennik.txt")).also { instance = it }
    }
}
