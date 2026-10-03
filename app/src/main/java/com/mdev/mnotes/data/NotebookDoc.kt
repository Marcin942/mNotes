package com.mdev.mnotes.data

import android.os.Handler
import android.os.Looper
import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.model.DocEdit
import com.mdev.mnotes.core.model.DocHost
import com.mdev.mnotes.core.model.ElementsEdit
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PageSnapshot
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.UndoStack

/** Strona w pamięci. Zawartość ładuje się leniwie, bo zeszyt może mieć setki stron. */
class PageState(val id: String, spec: PageSpec) {
    @Volatile var spec: PageSpec = spec
    @Volatile var content: PageContent? = null
    /** Rośnie przy każdej zmianie zawartości lub wyglądu — rendery porównują ją, by odrzucać nieaktualne wyniki. */
    @Volatile var version: Int = 0
}

interface DocListener {
    /** [edit] = zmiana zawartości (z niej wynika obszar do odświeżenia); null = zmienił się wygląd/rozmiar strony. */
    fun pageChanged(pageId: String, edit: ElementsEdit?)
    fun structureChanged()
}

/**
 * Otwarty zeszyt: strony, cofanie/ponawianie i zapis automatyczny (debounce + zapis przy wyjściu).
 * Wszystkie mutacje — wyłącznie z wątku UI; wątek renderujący czyta niezmienne [PageContent] z pól volatile.
 */
class NotebookDoc(val repo: Repository, val id: String) : DocHost {
    val pages = ArrayList<PageState>()
    val undo = UndoStack()
    var listener: DocListener? = null

    private val handler = Handler(Looper.getMainLooper())
    private val dirtyContent = LinkedHashSet<String>()
    private val deleted = LinkedHashSet<String>()
    private var metaDirty = false
    private val saveTask = Runnable { flush() }

    init {
        repo.meta(id)?.pages?.forEach { pages.add(PageState(it.id, it.spec)) }
    }

    val meta: NotebookMeta get() = repo.meta(id) ?: NotebookMeta(id, "", created = 0, modified = 0)

    fun page(pageId: String): PageState? = pages.firstOrNull { it.id == pageId }

    fun content(p: PageState): PageContent {
        p.content?.let { return it }
        synchronized(p) {
            p.content?.let { return it }
            val c = repo.readPage(id, p.id)
            p.content = c
            return c
        }
    }

    /** Zawartość strony bez cache'owania (wyszukiwanie po całym zeszycie nie powinno trzymać wszystkich stron w pamięci). */
    fun peekContent(p: PageState): PageContent = p.content ?: repo.readPage(id, p.id)

    // — DocHost —

    override fun applyElements(pageId: String, edit: ElementsEdit) {
        val p = page(pageId) ?: return
        p.content = content(p).apply(edit)
        p.version++
        dirtyContent.add(pageId)
        scheduleSave()
        listener?.pageChanged(pageId, edit)
    }

    override fun spec(pageId: String): PageSpec = page(pageId)?.spec ?: PageSpec()

    override fun setSpec(pageId: String, spec: PageSpec) {
        val p = page(pageId) ?: return
        val sizeChanged = p.spec.w != spec.w || p.spec.h != spec.h
        p.spec = spec
        p.version++
        metaDirty = true
        scheduleSave()
        if (sizeChanged) listener?.structureChanged() else listener?.pageChanged(pageId, null)
    }

    override fun insertPages(entries: List<Pair<Int, PageSnapshot>>) {
        for ((idx, snap) in entries.sortedBy { it.first }) {
            val ps = PageState(snap.id, snap.spec)
            ps.content = snap.content
            pages.add(idx.coerceIn(0, pages.size), ps)
            dirtyContent.add(snap.id)
            deleted.remove(snap.id)
        }
        metaDirty = true
        scheduleSave()
        listener?.structureChanged()
    }

    override fun removePages(ids: List<String>) {
        val set = ids.toSet()
        pages.removeAll { it.id in set }
        dirtyContent.removeAll(set)
        deleted.addAll(set)
        metaDirty = true
        scheduleSave()
        listener?.structureChanged()
    }

    override fun movePage(pageId: String, toIndex: Int) {
        val from = indexOf(pageId)
        if (from < 0) return
        val p = pages.removeAt(from)
        pages.add(toIndex.coerceIn(0, pages.size), p)
        metaDirty = true
        scheduleSave()
        listener?.structureChanged()
    }

    override fun indexOf(pageId: String): Int = pages.indexOfFirst { it.id == pageId }

    // — Edycje z cofaniem —

    /** Wykonuje i zapisuje na stosie cofania. */
    fun exec(edit: DocEdit) { edit.apply(this); undo.push(edit) }

    /** Dopisuje do stosu edycję, która jest JUŻ zastosowana (np. gumka stosowana na żywo). */
    fun record(edit: DocEdit) { undo.push(edit) }

    fun doUndo(): DocEdit? = undo.undo(this)
    fun doRedo(): DocEdit? = undo.redo(this)

    fun snapshot(p: PageState): PageSnapshot = PageSnapshot(p.id, p.spec, content(p))

    // — Zapis —

    private fun scheduleSave() {
        handler.removeCallbacks(saveTask)
        handler.postDelayed(saveTask, 1500)
    }

    fun flush() {
        handler.removeCallbacks(saveTask)
        if (dirtyContent.isEmpty() && !metaDirty && deleted.isEmpty()) return
        val writes = dirtyContent.mapNotNull { pid -> page(pid)?.let { pid to (it.content ?: return@let null) } }
        val dels = deleted.toList()
        dirtyContent.clear(); deleted.clear(); metaDirty = false
        val metaPages = pages.map { PageMeta(it.id, it.spec) }
        repo.update(id) { it.copy(pages = metaPages, modified = System.currentTimeMillis()) }
        repo.io.execute {
            for ((pid, c) in writes) repo.writePage(id, pid, PageCodec.encode(c))
            for (pid in dels) repo.deletePageFiles(id, pid)
            repo.thumbFile(id).delete()
        }
    }

    fun setLastPage(index: Int, layout: Int) {
        if (meta.lastPage != index || meta.layout != layout) repo.update(id) { it.copy(lastPage = index, layout = layout) }
    }
}
