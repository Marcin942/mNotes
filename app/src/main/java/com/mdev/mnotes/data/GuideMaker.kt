package com.mdev.mnotes.data

import android.os.Handler
import android.os.Looper
import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.guide.Guide
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.editor.T

/** Zeszyt-przewodnik (F20): strony z [Guide] zapisane jak zwykły zeszyt; rejestracja w bibliotece na wątku UI. */
object GuideMaker {
    private val main = Handler(Looper.getMainLooper())

    fun create(repo: Repository, folderId: String?, done: (NotebookMeta) -> Unit = {}) {
        val pl = T.pl
        repo.io.execute {
            val id = newId()
            // Pomiar czcionką urządzenia, tą samą co przy rysowaniu pól: akapity mają dokładnie swoją wysokość (2c-A14).
            val pages = Guide.pages(pl, lab = com.mdev.mnotes.app.Flavor.lab?.board == true, m = com.mdev.mnotes.render.PageRenderer(null).textMeasure)
            val metas = pages.map { PageMeta(newId(), it.spec) }
            for ((m, p) in metas.zip(pages)) repo.writePage(id, m.id, PageCodec.encode(PageContent.of(p.elements)))
            val now = System.currentTimeMillis()
            val meta = NotebookMeta(
                id, T.t("Przewodnik po mNotes", "mNotes guide"), folderId, now, now, now,
                0xFF2E7D4F.toInt(), CoverStyle.ARCS, pages = metas,
            )
            main.post { done(repo.addNotebook(meta)) }
        }
    }

    /** Pierwsze uruchomienie: pusta biblioteka dostaje przewodnik raz (ustawienie pamięta, że już był). */
    fun offerOnFirstRun(repo: Repository, settings: Settings) {
        if (settings.guideOffered) return
        settings.guideOffered = true
        if (repo.notebooks.isEmpty()) create(repo, null)
    }
}
