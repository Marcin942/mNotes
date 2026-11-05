package com.mdev.mnotes.core.geom

import com.mdev.mnotes.core.model.BoxElement
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.ElementsEdit
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox

/**
 * NAU-3 „żywa” konstrukcja: wynik Konstrukcji/Geometrii pamięta w sesji, z czego powstał, i po przesunięciu wejścia
 * (np. wierzchołka A) liczy się od nowa. Zależności żyją tylko w pamięci — zapisane są zwykłe elementy, format bez zmian.
 *
 * Kolejność topologiczna to kolejność dodawania: wyniki dostają nowe id, a wejścia istniały wcześniej, więc zależność
 * może wskazywać tylko na starsze — cyklu nie da się zbudować. Zależność się zrywa, gdy brakuje wejścia albo wyniku,
 * gdy uczeń sam zmieni kształt wyniku (bez ruszania wejść) albo gdy przeliczony wynik ma inny skład (wtedy stare wyniki
 * ustępują nowym z nowymi id, a zależne od nich konstrukcje zrywają się przy braku wejścia).
 */
class Live {
    /**
     * Zależność: wyniki [outputs] policzone z elementów [inputs] (w tej kolejności). [rebuild] liczy wynik z bieżących
     * wejść; null albo pusta lista — teraz bez rozwiązania (wynik zostaje, jak był, zależność trwa).
     */
    class Link(val inputs: List<Int>, outputs: List<Int>, val rebuild: (List<Element>) -> List<Element>?) {
        var outputs: List<Int> = outputs
            internal set
    }

    private val pages = HashMap<String, MutableList<Link>>()

    fun add(pageId: String, inputs: List<Int>, outputs: List<Int>, rebuild: (List<Element>) -> List<Element>?) {
        if (inputs.isEmpty() || outputs.isEmpty() || outputs.any { it in inputs }) return
        pages.getOrPut(pageId) { ArrayList() }.add(Link(inputs.distinct(), outputs, rebuild))
    }

    fun links(pageId: String): List<Link> = pages[pageId].orEmpty()

    /**
     * Po zmianie treści strony [pageId] z [before] na [after] (zmiana ucznia): edycja [after] z przeliczonymi wynikami
     * albo null, gdy nic nie trzeba przeliczać. Zerwane zależności znikają od razu.
     */
    fun follow(pageId: String, before: PageContent, after: PageContent): ElementsEdit? {
        val links = pages[pageId] ?: return null
        val content = run(links, before, after, commit = true) ?: return null
        if (links.isEmpty()) pages.remove(pageId)
        return if (content === after) null else ElementsEdit.diff(after, content)
    }

    /** Czy któreś z [ids] jest wejściem zależności na stronie [pageId] (przeciąganie go coś przeliczy). */
    fun drives(pageId: String, ids: Set<Int>): Boolean = pages[pageId]?.any { l -> l.inputs.any { it in ids } } == true

    /**
     * 3c-B2: podgląd w trakcie przeciągania — przeliczone wyniki dla treści [after] (jeszcze nie zapisanej) bez zmiany
     * stanu: zależności się nie zrywają, a wyniki nie dostają nowych id. Pusta lista — nic do pokazania.
     */
    fun preview(pageId: String, before: PageContent, after: PageContent): List<Element> {
        val links = pages[pageId] ?: return emptyList()
        val content = run(ArrayList(links), before, after, commit = false) ?: return emptyList()
        if (content === after) return emptyList()
        val was = after.elements.associateBy { it.id }
        return content.elements.filter { was[it.id] !== it }
    }

    /**
     * Przeliczenie zależności [links] po zmianie [before] → [after]; null — nic nie dotknęło wejść ani wyników.
     * [commit]: zerwane zależności znikają z [links], a wyniki o innym składzie dostają nowe id na stałe.
     */
    private fun run(links: MutableList<Link>, before: PageContent, after: PageContent, commit: Boolean): PageContent? {
        val ids = HashSet<Int>()
        for (l in links) { ids.addAll(l.inputs); ids.addAll(l.outputs) }
        val was = index(before, ids)
        val now = index(after, ids)
        // Szybka droga: edycja nie dotknęła żadnego wejścia ani wyniku (zwykła kreska obok).
        if (ids.all { was[it] === now[it] }) return null
        val cur = HashMap(now)
        var content = after
        val it = links.iterator()
        while (it.hasNext()) {
            val l = it.next()
            val ins = l.inputs.map { cur[it] }
            if (ins.any { it == null } || l.outputs.any { now[it] == null }) { if (commit) it.remove(); continue }
            val moved = l.inputs.any { !sameShape(was[it], cur[it]!!) }
            if (!moved) {
                if (commit && l.outputs.any { !sameShape(was[it], now[it]!!) }) it.remove()
                continue
            }
            val fresh = l.rebuild(ins.map { it!! })
            if (fresh.isNullOrEmpty()) continue
            val old = l.outputs.map { cur[it]!! }
            if (fresh.size == old.size && fresh.indices.all { fresh[it].javaClass == old[it].javaClass }) {
                val repl = old.indices.associate { k -> old[k].id to keepStyle(old[k], fresh[k]) }
                content = content.apply(ElementsEdit.replace(content, repl))
                cur.putAll(repl)
            } else {
                content = content.apply(ElementsEdit.remove(content, l.outputs.toSet()))
                content = content.apply(ElementsEdit.add(content, fresh))
                for (o in l.outputs) cur.remove(o)
                if (commit) l.outputs = fresh.map { it.id }
            }
        }
        return content
    }

    private fun index(c: PageContent, ids: Set<Int>): HashMap<Int, Element> {
        val m = HashMap<Int, Element>(ids.size * 2)
        for (e in c.elements) if (e.id in ids) m[e.id] = e
        return m
    }

    companion object {
        /** Ten sam kształt i miejsce (kolor, grubość, tekst się nie liczą): zmiana stylu wyniku nie zrywa zależności. */
        fun sameShape(a: Element?, b: Element): Boolean = when {
            a === b -> true
            a == null -> false
            a is Stroke && b is Stroke -> a.pts.contentEquals(b.pts) && a.ctrl.contentEquals(b.ctrl)
            a is BoxElement && b is BoxElement -> a.cx == b.cx && a.cy == b.cy && a.rot == b.rot
            else -> a.bounds.let { p -> val q = b.bounds; p.l == q.l && p.t == q.t && p.r == q.r && p.b == q.b }
        }

        /**
         * Przeliczony wynik [fresh] na miejscu starego [old]: kształt nowy, id i wygląd stare — kolor zmieniony przez
         * ucznia i nazwa po [ConstructOps.renameTaken] („A₁”) zostają. 3c-B3: miara (podpis z cyframi, np. „53°”)
         * bierze nowy tekst — dawniej po ruchu wierzchołka kąt dalej pokazywał starą liczbę.
         */
        fun keepStyle(old: Element, fresh: Element): Element = when {
            old is Stroke && fresh is Stroke -> Stroke(old.id, old.pen, old.color, old.width, fresh.pts, old.locked, fresh.poly, fresh.ellipse, old.line, fresh.ctrl, old.fill, fresh.memo)
            old is TextBox && fresh is TextBox -> old.copy(cx = fresh.cx, cy = fresh.cy, text = if (isMeasure(old.text) && isMeasure(fresh.text)) fresh.text else old.text)
            else -> fresh.withId(old.id)
        }

        /** Podpis z liczbą (cyfry 0–9; indeksy dolne nazw „A₁” się nie liczą). */
        private fun isMeasure(s: String) = s.any { it in '0'..'9' }
    }
}
