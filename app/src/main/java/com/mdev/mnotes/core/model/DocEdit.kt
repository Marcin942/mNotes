package com.mdev.mnotes.core.model

/** Strona wraz z zawartością — używana przy wstawianiu i usuwaniu stron. */
class PageSnapshot(val id: String, val spec: PageSpec, val content: PageContent)

/**
 * Co edytor musi umieć zrobić na dokumencie, żeby cofanie i ponawianie działały.
 * Implementacja odpowiada też za unieważnienie kafelków i oznaczenie stron do zapisu.
 */
interface DocHost {
    fun applyElements(pageId: String, edit: ElementsEdit)
    fun spec(pageId: String): PageSpec
    fun setSpec(pageId: String, spec: PageSpec)
    fun insertPages(entries: List<Pair<Int, PageSnapshot>>)
    fun removePages(ids: List<String>)
    fun movePage(pageId: String, toIndex: Int)
    fun indexOf(pageId: String): Int
}

/** Odwracalna operacja na dokumencie; element stosu cofania. */
sealed class DocEdit {
    /** Strony dotknięte zmianą — edytor przewija do pierwszej z nich po cofnięciu. */
    abstract val pageIds: List<String>

    abstract fun apply(host: DocHost)
    abstract fun revert(host: DocHost)

    class Elements(val pageId: String, val edit: ElementsEdit) : DocEdit() {
        override val pageIds: List<String> get() = listOf(pageId)
        override fun apply(host: DocHost) = host.applyElements(pageId, edit)
        override fun revert(host: DocHost) = host.applyElements(pageId, edit.inverse())
    }

    class Spec(val pageId: String, val before: PageSpec, val after: PageSpec) : DocEdit() {
        override val pageIds: List<String> get() = listOf(pageId)
        override fun apply(host: DocHost) = host.setSpec(pageId, after)
        override fun revert(host: DocHost) = host.setSpec(pageId, before)
    }

    /** Wstawienie stron; [entries] to pary (indeks po wstawieniu, strona) w rosnącej kolejności indeksów. */
    class InsertPages(val entries: List<Pair<Int, PageSnapshot>>) : DocEdit() {
        override val pageIds: List<String> get() = entries.map { it.second.id }
        override fun apply(host: DocHost) = host.insertPages(entries)
        override fun revert(host: DocHost) = host.removePages(entries.map { it.second.id })
    }

    /** Usunięcie stron; [entries] to pary (indeks przed usunięciem, strona) w rosnącej kolejności indeksów. */
    class RemovePages(val entries: List<Pair<Int, PageSnapshot>>) : DocEdit() {
        override val pageIds: List<String> get() = entries.map { it.second.id }
        override fun apply(host: DocHost) = host.removePages(entries.map { it.second.id })
        override fun revert(host: DocHost) = host.insertPages(entries)
    }

    class MovePage(val pageId: String, val from: Int, val to: Int) : DocEdit() {
        override val pageIds: List<String> get() = listOf(pageId)
        override fun apply(host: DocHost) = host.movePage(pageId, to)
        override fun revert(host: DocHost) = host.movePage(pageId, from)
    }

    /** Kilka operacji cofanych jednym gestem; cofanie przebiega w odwrotnej kolejności. */
    class Group(val edits: List<DocEdit>) : DocEdit() {
        override val pageIds: List<String> get() = edits.flatMap { it.pageIds }.distinct()
        override fun apply(host: DocHost) { for (e in edits) e.apply(host) }
        override fun revert(host: DocHost) { for (i in edits.indices.reversed()) edits[i].revert(host) }
    }
}

/** Stos cofania/ponawiania o ograniczonej głębokości. Nie jest bezpieczny wątkowo — używany z wątku UI. */
class UndoStack(private val limit: Int = 500) {
    private val undoList = ArrayList<DocEdit>()
    private val redoList = ArrayList<DocEdit>()

    /** Rośnie przy każdej zmianie stosu; pozwala UI odświeżać stan przycisków. */
    var version: Int = 0
        private set

    val canUndo: Boolean get() = undoList.isNotEmpty()
    val canRedo: Boolean get() = redoList.isNotEmpty()

    fun push(edit: DocEdit) {
        undoList.add(edit)
        if (undoList.size > limit) undoList.removeAt(0)
        redoList.clear()
        version++
    }

    fun undo(host: DocHost): DocEdit? {
        if (undoList.isEmpty()) return null
        val e = undoList.removeAt(undoList.size - 1)
        e.revert(host)
        redoList.add(e)
        version++
        return e
    }

    fun redo(host: DocHost): DocEdit? {
        if (redoList.isEmpty()) return null
        val e = redoList.removeAt(redoList.size - 1)
        e.apply(host)
        undoList.add(e)
        version++
        return e
    }

    fun clear() {
        undoList.clear()
        redoList.clear()
        version++
    }
}
