package com.mdev.mnotes.core.model

import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * 2c-A5: przeniesienie zaznaczenia między dokumentami stanowiska to dwa kroki na dwóch stosach — usunięcie w źródle
 * i wstawienie w celu. Cofnięcie tylko jednego z nich gubiło obiekt (Ctrl+Z w celu) albo go podwajało (w źródle).
 * Tu para jest zapamiętana: cofnięcie i ponowienie w dowolnym z dwóch dokumentów obejmuje oba kroki, o ile druga
 * połowa leży na wierzchu stosu partnera. Gdy partner ma już późniejsze kroki, cofamy tylko u siebie — cofnięcie
 * cudzego kroku spod spodu rozjechałoby jego stos.
 *
 * [K] wskazuje dokument (w aplikacji: edytor). Kroki trzymane słabo, więc krok wypchnięty ze stosu znika też stąd.
 */
class LinkedUndo<K : Any> {
    private class Half<K>(val key: K, val edit: WeakReference<DocEdit>)
    private val halves = WeakHashMap<DocEdit, Half<K>>()

    fun link(ka: K, a: DocEdit, kb: K, b: DocEdit) {
        halves[a] = Half(kb, WeakReference(b))
        halves[b] = Half(ka, WeakReference(a))
    }

    /** Partner kroku [e], o ile [peek] partnera (wierzch jego stosu) to właśnie druga połowa pary. */
    fun partner(e: DocEdit?, peek: (K) -> DocEdit?): K? {
        if (e == null) return null
        // 3c-B3: edytor może zapisać połowę razem z krokiem dodatkowym (ruch konstrukcji na żywo) jako Group.
        val h = halves[e] ?: (e as? DocEdit.Group)?.edits?.firstNotNullOfOrNull { halves[it] } ?: return null
        val other = h.edit.get() ?: return null
        return h.key.takeIf { holds(peek(it), other) }
    }

    private fun holds(top: DocEdit?, half: DocEdit) = top === half || (top as? DocEdit.Group)?.edits?.any { it === half } == true

    /** Cofa w [k] przez [undoIn], a gdy na wierzchu jest połowa pary — też u partnera. */
    fun undo(k: K, stackOf: (K) -> UndoStack?, undoIn: (K) -> Unit) {
        val p = partner(stackOf(k)?.top) { stackOf(it)?.top }
        undoIn(k)
        if (p != null) undoIn(p)
    }

    /** Ponawia w [k] przez [redoIn], a gdy następny krok to połowa pary — też u partnera. */
    fun redo(k: K, stackOf: (K) -> UndoStack?, redoIn: (K) -> Unit) {
        val p = partner(stackOf(k)?.nextRedo) { stackOf(it)?.nextRedo }
        redoIn(k)
        if (p != null) redoIn(p)
    }
}
