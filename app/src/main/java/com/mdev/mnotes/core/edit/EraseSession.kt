package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.shapes.CurveMemo
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.geom.BoundsGrid
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TapeEl
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Jeden gest gumki (pikselowej albo obiektowej) na jednej stronie, liczony w pamięci.
 *
 * Dlaczego tak (B1, „gumka zacina całą aplikację”): dawniej każde zdarzenie ruchu dzielono na kroki co 0,6 promienia
 * (przy gumce 1 pt i szybkim ruchu setki kroków), a każdy krok przeglądał wszystkie elementy strony i przy trafieniu
 * kopiował całą listę oraz całą przeciętą kreskę. Tu ruch między zdarzeniami to jedna „kapsuła” ([EraseMath.eraseSwept]),
 * kandydatów daje siatka kubełków ([BoundsGrid]), a każdy element strony ma własne miejsce ([slots]) z bieżącymi
 * kawałkami — lista strony składa się dopiero przy zapisie do dokumentu ([elements]).
 */
class EraseSession(
    private val base: List<Element>,
    private val pixel: Boolean,
    private val keepHighlighter: Boolean,
) {
    /** Bieżące kawałki elementu [base] o tym samym indeksie; null = nietknięty. Kawałki nie wychodzą poza jego obwiednię. */
    private val slots = arrayOfNulls<List<Element>>(base.size)
    private val grid = BoundsGrid(base.map { it.bounds })

    /** Obszar zmieniony od ostatniego [takeDirty] (do przemalowania kafelków). */
    var dirty: Bounds = Bounds.EMPTY
        private set

    /** Czy gest zmienił cokolwiek na stronie. */
    var changed = false
        private set

    fun takeDirty(): Bounds { val d = dirty; dirty = Bounds.EMPTY; return d }

    /** Gumka o promieniu [r] przesunięta od (ax, ay) do (bx, by); true = coś się zmieniło. */
    fun sweep(ax: Float, ay: Float, bx: Float, by: Float, r: Float): Boolean {
        val q = Bounds(min(ax, bx), min(ay, by), max(ax, bx), max(ay, by)).inflate(r + 1f)
        var hit = false
        grid.query(q) { i -> if (sweepSlot(i, q, ax, ay, bx, by, r)) hit = true }
        if (hit) changed = true
        return hit
    }

    private fun skip(e: Element): Boolean =
        // Wypełnienie leży pod pismem: gumka obiektowa przeciągana po zamalowanym obszarze ściera tylko kreski (całe
        // wypełnienie usuwa lasso gumki), a pikselowa ściera z wypełnienia to, nad czym przejdzie.
        e.locked || e is TapeEl || (e is FillEl && (!pixel || keepHighlighter && e.hl)) ||
            (keepHighlighter && e is Stroke && e.pen == PenKind.HIGHLIGHTER)

    private fun sweepSlot(i: Int, q: Bounds, ax: Float, ay: Float, bx: Float, by: Float, r: Float): Boolean {
        val cur = slots[i]
        if (cur == null) {
            val out = eraseOne(base[i], q, ax, ay, bx, by, r) ?: return false
            slots[i] = out
            return true
        }
        var next: ArrayList<Element>? = null
        for (k in cur.indices) {
            val e = cur[k]
            val res = eraseOne(e, q, ax, ay, bx, by, r)
            if (res != null && next == null) { next = ArrayList(cur.size + 2); for (j in 0 until k) next.add(cur[j]) }
            if (next != null) { if (res == null) next.add(e) else next.addAll(res) }
        }
        if (next == null) return false
        slots[i] = next
        return true
    }

    /** Wynik gumki na jednym elemencie: null = nietknięty, pusta lista = usunięty. */
    private fun eraseOne(e: Element, q: Bounds, ax: Float, ay: Float, bx: Float, by: Float, r: Float): List<Element>? {
        if (skip(e) || !e.bounds.intersects(q)) return null
        if (!pixel) {
            if (!EraseMath.hitsElementSwept(e, ax, ay, bx, by, r)) return null
            dirty = dirty.union(e.bounds)
            return emptyList()
        }
        return when (e) {
            is Stroke -> {
                // Łamana z uchwytami (bez elipsy, która jest gęstą krzywą) zostaje łamaną: kawałki mają własne uchwyty (F3).
                val poly = e.poly && !e.ellipse
                val res = (if (poly) EraseMath.erasePoly(e, ax, ay, bx, by, r) else EraseMath.eraseSwept(e, ax, ay, bx, by, r)) ?: return null
                dirty = dirty.union(res.dirty)
                // Kawałek krzywej albo kreski z pamięcią krzywej pamięta swój dokładny odcinek krzywej (LP-3).
                val spans = res.spans
                val memos = if (!poly && (e.ctrl != null || e.memo != null) && spans != null && spans.size == 2 * res.pieces.size) {
                    CurveMemo.pieces(e, spans)
                } else null
                res.pieces.mapIndexed { k, it -> Stroke(Ids.next(), e.pen, e.color, e.width, it, false, poly = poly, line = e.line, memo = memos?.get(k)) }
            }
            is FillEl -> {
                // Wypełnienia są rzadkie i mają własną, dokładną matematykę koła: ruch idzie krokami jak dawniej.
                var f: FillEl? = e
                var hit = false
                val steps = ceil(hypot(bx - ax, by - ay) / max(0.5f, r * 0.6f)).toInt().coerceIn(0, 4000)
                for (k in 0..steps) {
                    val cf = f ?: break
                    val t = if (steps == 0) 0f else k.toFloat() / steps
                    val res = FillErase.erase(cf, ax + (bx - ax) * t, ay + (by - ay) * t, r) ?: continue
                    hit = true
                    dirty = dirty.union(res.dirty)
                    f = if (res.rings.isEmpty()) null else FillEl(Ids.next(), cf.color, cf.hl, res.rings)
                }
                if (!hit) null else listOfNotNull(f)
            }
            else -> null
        }
    }

    /** Bieżąca zawartość strony: nietknięte elementy to te same instancje co w [base], więc różnica wobec niej jest mała. */
    fun elements(): List<Element> {
        if (!changed) return base
        val out = ArrayList<Element>(base.size + 16)
        for (i in base.indices) { val s = slots[i]; if (s == null) out.add(base[i]) else out.addAll(s) }
        return out
    }
}
