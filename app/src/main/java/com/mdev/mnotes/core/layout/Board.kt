package com.mdev.mnotes.core.layout

import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.model.DocEdit
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Paper
import com.mdev.mnotes.core.model.Template
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Zeszyt „Tablica” (2b-A13): jedna strona bez granic jak w Excalidraw — w zapisie zwykła duża strona z flagą
 * [PageSpec.OPT_BOARD], więc stara wersja otwiera ją jako dużą stronę z kratką. Płótno rośnie, gdy pismo zbliża się do
 * brzegu: w prawo i w dół przez powiększenie strony, w lewo i w górę przez powiększenie i przesunięcie całej treści
 * (widok przesuwa się razem z nią, więc na ekranie nic nie skacze).
 */
object Board {
    /** Rozmiar startowy (pt, ok. 1 m) i krok wzrostu. */
    const val START = 3000f
    const val STEP = 1500f
    /** Zapas: treść bliżej brzegu niż tyle pt powiększa płótno. */
    const val MARGIN = 500f
    /** Największy bok tablicy (pt, ok. 8,5 m); starsza wersja przytnie stronę do [PageSpec.MAX_SIZE] przy odczycie. */
    const val MAX = 24000f
    /** „100 %” tablicy: szerokość kartki A4 na szerokości widoku — pisze się jak w zeszycie. */
    const val VIEW_W = 595f
    /** Kadr eksportu: zapas wokół treści (pt). */
    const val FRAME_PAD = 24f

    /**
     * Czy do zeszytu o stronach [pages] wolno dodać stronę (2c-A1): tablica ma zawsze dokładnie jedną, bo widok tablicy
     * (powiększenie, minimapa, kadr eksportu) działa tylko dla zeszytu z jedną stroną.
     */
    fun canAddPage(pages: List<PageSpec>): Boolean = pages.none { it.board }

    fun spec(paper: Int = Paper.WHITE): PageSpec =
        PageSpec(START, START, paper, Template.GRID, Paper.autoLineColor(paper), Template.GRID.defaultSpacing, opts = PageSpec.OPT_BOARD)

    /** Wzrost płótna: nowy rozmiar i przesunięcie treści ([dx], [dy] ≥ 0 — o tyle treść idzie w prawo/w dół). */
    data class Growth(val w: Float, val h: Float, val dx: Float, val dy: Float)

    /** Jak powiększyć płótno [w]×[h], żeby treść o obrysie [b] miała wokół zapas [MARGIN]; null — bez zmian. */
    fun grow(w: Float, h: Float, b: Bounds?): Growth? {
        if (b == null || b.isEmpty) return null
        fun steps(need: Float) = if (need <= 0f) 0f else ceil(need / STEP) * STEP
        val (left, right) = fit(steps(MARGIN - b.l), steps(b.r - (w - MARGIN)), w)
        val (top, bottom) = fit(steps(MARGIN - b.t), steps(b.b - (h - MARGIN)), h)
        if (left + right + top + bottom == 0f) return null
        return Growth(w + left + right, h + top + bottom, left, top)
    }

    /**
     * Edycje powiększające tablicę [pageId] o wyglądzie [spec] dla treści o obrysie [b]: rozmiar i ewentualnie
     * przesunięcie całej treści (2c-A1: dwie liczby zamiast kopii elementów, więc wpis cofania nie rośnie z treścią).
     */
    fun growthEdits(pageId: String, spec: PageSpec, b: Bounds?): List<DocEdit>? {
        val g = grow(spec.w, spec.h, b) ?: return null
        val size = DocEdit.Spec(pageId, spec, spec.withSize(g.w, g.h))
        return if (g.dx == 0f && g.dy == 0f) listOf(size) else listOf(size, DocEdit.Shift(pageId, g.dx, g.dy))
    }

    /** Przycina wzrost do [MAX]: najpierw część po stronie dalszej (prawo/dół), potem bliższej. */
    private fun fit(near: Float, far: Float, size: Float): Pair<Float, Float> {
        val room = max(0f, MAX - size)
        val f = min(far, room)
        val n = min(near, room - f)
        return n to f
    }

    /** Kadr eksportu: obrys treści z zapasem, przycięty do płótna; null — tablica pusta. */
    fun frame(b: Bounds?, w: Float, h: Float): Bounds? {
        if (b == null || b.isEmpty) return null
        val l = max(0f, b.l - FRAME_PAD); val t = max(0f, b.t - FRAME_PAD)
        val r = min(w, b.r + FRAME_PAD); val bt = min(h, b.b + FRAME_PAD)
        return if (r <= l || bt <= t) null else Bounds(l, t, r, bt)
    }

    /** Punkt, na który widok patrzy po otwarciu: środek treści albo środek pustej tablicy. */
    fun focus(b: Bounds?, w: Float, h: Float): Pair<Float, Float> =
        if (b == null || b.isEmpty) w / 2f to h / 2f else b.cx to b.cy
}
