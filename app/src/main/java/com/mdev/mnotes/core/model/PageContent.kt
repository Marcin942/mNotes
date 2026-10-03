package com.mdev.mnotes.core.model

import com.mdev.mnotes.core.geom.Bounds

/** Element z jego pozycją w liście (kolejność = kolejność rysowania, od spodu). */
class Placed(val index: Int, val element: Element)

/**
 * Odwracalna zmiana zawartości strony.
 * [removed] mają indeksy z listy PRZED zmianą, [added] indeksy w liście PO zmianie.
 */
class ElementsEdit(val removed: List<Placed>, val added: List<Placed>) {

    val isEmpty: Boolean get() = removed.isEmpty() && added.isEmpty()

    fun inverse(): ElementsEdit = ElementsEdit(removed = added, added = removed)

    /** Obwiednia wszystkiego, co się zmieniło (do unieważniania kafelków). */
    val dirtyBounds: Bounds
        get() {
            var b = Bounds.EMPTY
            for (p in removed) b = b.union(p.element.bounds)
            for (p in added) b = b.union(p.element.bounds)
            return b
        }

    companion object {
        val EMPTY = ElementsEdit(emptyList(), emptyList())

        /**
         * Minimalna edycja zamieniająca [before] w [after]. Element „ten sam” = ta sama instancja w zachowanej
         * kolejności względnej; wszystko inne (zmienione, przesunięte w kolejności, nowe) trafia do removed/added.
         */
        fun diff(before: PageContent, after: PageContent): ElementsEdit {
            val pos = java.util.IdentityHashMap<Element, Int>(before.size * 2)
            before.elements.forEachIndexed { i, e -> pos[e] = i }
            val kept = BooleanArray(before.size)
            val added = ArrayList<Placed>()
            var last = -1
            after.elements.forEachIndexed { j, e ->
                val bi = pos[e]
                if (bi != null && bi > last) { kept[bi] = true; last = bi } else added.add(Placed(j, e))
            }
            val removed = ArrayList<Placed>()
            before.elements.forEachIndexed { i, e -> if (!kept[i]) removed.add(Placed(i, e)) }
            return ElementsEdit(removed, added)
        }

        fun add(content: PageContent, elements: List<Element>): ElementsEdit {
            var i = content.size
            return ElementsEdit(emptyList(), elements.map { Placed(i++, it) })
        }

        fun remove(content: PageContent, ids: Set<Int>): ElementsEdit {
            val out = ArrayList<Placed>()
            content.elements.forEachIndexed { i, e -> if (e.id in ids) out.add(Placed(i, e)) }
            return ElementsEdit(out, emptyList())
        }

        /** Podmienia elementy o tych samych id, zachowując ich miejsce w kolejności rysowania. */
        fun replace(content: PageContent, replacements: Map<Int, Element>): ElementsEdit {
            val rem = ArrayList<Placed>()
            val add = ArrayList<Placed>()
            content.elements.forEachIndexed { i, e ->
                val n = replacements[e.id]
                if (n != null) {
                    rem.add(Placed(i, e))
                    add.add(Placed(i, n))
                }
            }
            return ElementsEdit(rem, add)
        }

        /** Podmienia elementy o tych samych id i jednocześnie przenosi je na wierzch (zachowując wzajemną kolejność). */
        fun replaceOnTop(content: PageContent, replacements: Map<Int, Element>): ElementsEdit {
            val rem = ArrayList<Placed>()
            val add = ArrayList<Element>()
            content.elements.forEachIndexed { i, e ->
                val n = replacements[e.id]
                if (n != null) { rem.add(Placed(i, e)); add.add(n) }
            }
            if (rem.isEmpty()) return EMPTY
            val base = content.size - rem.size
            return ElementsEdit(rem, add.mapIndexed { j, e -> Placed(base + j, e) })
        }

        /** Przenosi wskazane elementy na wierzch (zachowując ich wzajemną kolejność). */
        fun bringToFront(content: PageContent, ids: Set<Int>): ElementsEdit {
            val moved = content.elements.filter { it.id in ids }
            if (moved.isEmpty()) return EMPTY
            val rem = ArrayList<Placed>()
            content.elements.forEachIndexed { i, e -> if (e.id in ids) rem.add(Placed(i, e)) }
            val base = content.size - moved.size
            return ElementsEdit(rem, moved.mapIndexed { k, e -> Placed(base + k, e) })
        }

        /** Przenosi wskazane elementy na spód. */
        fun sendToBack(content: PageContent, ids: Set<Int>): ElementsEdit {
            val moved = content.elements.filter { it.id in ids }
            if (moved.isEmpty()) return EMPTY
            val rem = ArrayList<Placed>()
            content.elements.forEachIndexed { i, e -> if (e.id in ids) rem.add(Placed(i, e)) }
            return ElementsEdit(rem, moved.mapIndexed { k, e -> Placed(k, e) })
        }
    }
}

/** Niezmienna zawartość strony — lista elementów od spodu do wierzchu. */
class PageContent private constructor(val elements: List<Element>) {

    val size: Int get() = elements.size
    val isEmpty: Boolean get() = elements.isEmpty()

    val bounds: Bounds by lazy(LazyThreadSafetyMode.PUBLICATION) {
        var b = Bounds.EMPTY
        for (e in elements) b = b.union(e.bounds)
        b
    }

    fun byId(id: Int): Element? {
        for (e in elements) if (e.id == id) return e
        return null
    }

    fun apply(edit: ElementsEdit): PageContent {
        if (edit.isEmpty) return this
        val list = ArrayList<Element>(elements.size + edit.added.size)
        if (edit.removed.isEmpty()) {
            list.addAll(elements)
        } else {
            val ids = HashSet<Int>(edit.removed.size * 2)
            for (p in edit.removed) ids.add(p.element.id)
            for (e in elements) if (e.id !in ids) list.add(e)
        }
        if (edit.added.isNotEmpty()) {
            for (p in edit.added.sortedBy { it.index }) {
                val at = p.index.coerceIn(0, list.size)
                list.add(at, p.element)
            }
        }
        return PageContent(list)
    }

    fun revert(edit: ElementsEdit): PageContent = apply(edit.inverse())

    companion object {
        val EMPTY = PageContent(emptyList())

        fun of(elements: List<Element>): PageContent =
            if (elements.isEmpty()) EMPTY else PageContent(ArrayList(elements))
    }
}
