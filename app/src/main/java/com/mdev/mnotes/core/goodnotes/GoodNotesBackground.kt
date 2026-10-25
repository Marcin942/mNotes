package com.mdev.mnotes.core.goodnotes

import com.mdev.mnotes.core.samsung.BgKind
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Tło strony z GoodNotes: papier jako pierwsze wypełnienie na całą stronę i (opcjonalnie) regularny wzór nad nim. */
internal class BgPattern(
    val kind: BgKind,
    val pitchX: Double,
    val pitchY: Double,
    /** Środek pierwszej linii (kropki) na stronie, w pt. */
    val offX: Double,
    val offY: Double,
    val color: Int,
    val thick: Double,
    /** Średnica kropki. */
    val dot: Double,
)

internal class PaperInfo(val item: PathItem, val color: Int)

internal class BgAnalysis(
    val paper: Int,
    /** Elementy tła (papier, wzór, rzeczy poza stroną): nie zamieniamy ich na obiekty. */
    val members: Set<ContentItem>,
    val pattern: BgPattern?,
    /** Tło da się odtworzyć w całości (papier + wzór, który umiemy opisać); inaczej strona zostaje z tłem PDF. */
    val clean: Boolean,
)

internal object GoodNotesBackground {
    private val NORMAL = setOf("Normal", "Compatible")
    private const val LINE_MAX_THICK = 3.0
    private const val MIN_LINES = 3
    private const val MIN_DOTS = 6
    private const val WHITE = 0xFFFFFFFF.toInt()

    fun pageBox(p: PathItem, pm: PageMap): DoubleArray = pm.box(PathMath.bbox(p.subs))

    /** Prostokąt równoległy do osi: jedna podścieżka, wszystkie punkty na narożnikach obwiedni. */
    fun isAxisRect(p: PathItem): Boolean {
        if (p.subs.size != 1) return false
        val b = PathMath.bbox(p.subs)
        for (s in p.subs[0].segs) {
            if (s.kind == SegKind.CURVE) return false
            var i = 0
            while (i + 1 < s.pts.size) {
                val onX = abs(s.pts[i] - b[0]) < 0.01 || abs(s.pts[i] - b[2]) < 0.01
                val onY = abs(s.pts[i + 1] - b[1]) < 0.01 || abs(s.pts[i + 1] - b[3]) < 0.01
                if (!onX || !onY) return false
                i += 2
            }
        }
        return true
    }

    /** Pierwsze, co strona maluje: nieprzezroczyste wypełnienie na ≥ 98% strony. */
    fun findPaper(items: List<ContentItem>, pm: PageMap): PaperInfo? {
        val first = items.firstOrNull { !(it is PathItem && it.clip && !it.fill && !it.stroke) && !(it is TextItem && !it.visible) }
        if (first !is PathItem || !first.fill || first.stroke || first.clip) return null
        val st = first.state
        if (st.fillAlpha < 0.99 || st.blend !in NORMAL || st.softMask || st.fillCs.kind == CsKind.PATTERN) return null
        val color = st.fillArgb ?: return null
        if (!isAxisRect(first)) return null
        val b = pageBox(first, pm)
        val ix = max(0.0, min(b[2], pm.w) - max(b[0], 0.0))
        val iy = max(0.0, min(b[3], pm.h) - max(b[1], 0.0))
        if (pm.w <= 0 || pm.h <= 0 || ix * iy < 0.98 * pm.w * pm.h) return null
        return PaperInfo(first, color or 0xFF000000.toInt())
    }

    private class Cand(val item: PathItem, val kind: Char, val cx: Double, val cy: Double, val thick: Double, val color: Int, val opaque: Boolean)

    private fun classify(p: PathItem, pm: PageMap): Cand? {
        if (p.clip || p.subs.isEmpty() || p.stroke == p.fill) return null
        val st = p.state
        if (st.dash.isNotEmpty() && st.dash.any { it > 0 }) return null
        val cs = if (p.fill) st.fillCs else st.strokeCs
        if (cs.kind == CsKind.PATTERN) return null
        val color = (if (p.fill) st.fillArgb else st.strokeArgb) ?: return null
        val alpha = if (p.fill) st.fillAlpha else st.strokeAlpha
        val b = pageBox(p, pm)
        val extra = if (p.stroke) st.lineWidth * st.ctm.scale else 0.0
        val bw = b[2] - b[0] + extra
        val bh = b[3] - b[1] + extra
        val cx = (b[0] + b[2]) / 2
        val cy = (b[1] + b[3]) / 2
        val opaque = alpha >= 0.99 && st.blend in NORMAL && !st.softMask
        val argb = color or 0xFF000000.toInt()
        return when {
            bw >= 0.95 * pm.w && bh <= LINE_MAX_THICK -> Cand(p, 'H', cx, cy, bh, argb, opaque)
            bh >= 0.95 * pm.h && bw <= LINE_MAX_THICK -> Cand(p, 'V', cx, cy, bw, argb, opaque)
            p.fill && bw <= LINE_MAX_THICK && bh <= LINE_MAX_THICK && min(bw, bh) >= 0.3 && min(bw, bh) / max(bw, bh) >= 0.6 ->
                Cand(p, 'D', cx, cy, (bw + bh) / 2, argb, opaque)
            else -> null
        }
    }

    fun analyse(items: List<ContentItem>, pm: PageMap): BgAnalysis {
        val paper = findPaper(items, pm) ?: return BgAnalysis(WHITE, emptySet(), null, true)
        val members = HashSet<ContentItem>()
        members.add(paper.item)
        val group = paper.item.group
        val firstGrouped = items.indexOfFirst { it.group >= 0 }
        val from = items.indexOf(paper.item) + 1
        var hs = ArrayList<Cand>()
        var vs = ArrayList<Cand>()
        var ds = ArrayList<Cand>()
        for (i in from until items.size) {
            val it = items[i]
            val same = if (group >= 0) it.group == group else (firstGrouped < 0 || i < firstGrouped)
            if (!same || it !is PathItem || it.clip) continue
            val b = pageBox(it, pm)
            if (b[2] < -1 || b[0] > pm.w + 1 || b[3] < -1 || b[1] > pm.h + 1) { members.add(it); continue }
            val c = classify(it, pm) ?: continue
            when (c.kind) { 'H' -> hs.add(c); 'V' -> vs.add(c); else -> ds.add(c) }
        }
        // kilka pojedynczych linii (ramka, podkreślenie) to nie wzór: zostają zwykłymi obiektami
        if (hs.size < MIN_LINES) hs = ArrayList()
        if (vs.size < MIN_LINES) vs = ArrayList()
        if (ds.size < MIN_DOTS) ds = ArrayList()
        if (hs.isEmpty() && vs.isEmpty() && ds.isEmpty()) return BgAnalysis(paper.color, members, null, true)
        for (c in hs + vs + ds) members.add(c.item)

        val all = hs + vs + ds
        val color = all[0].color
        if (all.any { !it.opaque || it.color != color }) return BgAnalysis(paper.color, members, null, false)
        val lineThick = (hs + vs).map { it.thick }.sorted()
        if (lineThick.isNotEmpty()) {
            val med = lineThick[lineThick.size / 2]
            if (lineThick.any { abs(it - med) > 0.3 * med }) return BgAnalysis(paper.color, members, null, false)
        }
        val unclean = BgAnalysis(paper.color, members, null, false)
        if (ds.isNotEmpty()) {
            if (hs.isNotEmpty() || vs.isNotEmpty()) return unclean
            val dia = ds.map { it.thick }.sorted()
            val dmed = dia[dia.size / 2]
            if (dia.any { abs(it - dmed) > 0.3 * dmed }) return unclean
            val lx = lattice(ds.map { it.cx }, pm.w) ?: return unclean
            val ly = lattice(ds.map { it.cy }, pm.h) ?: return unclean
            if (lx[2].toInt() * ly[2].toInt() != ds.size) return unclean
            val pat = BgPattern(BgKind.DOTS, lx[0], ly[0], lx[1], ly[1], color, dmed, dmed)
            return BgAnalysis(paper.color, members, pat, true)
        }
        if (hs.isEmpty()) return unclean
        val ly = lattice(hs.map { it.cy }, pm.h) ?: return unclean
        val med = lineThick[lineThick.size / 2]
        if (vs.isEmpty()) {
            val pat = BgPattern(BgKind.LINES, ly[0], ly[0], 0.0, ly[1], color, med, 0.0)
            return BgAnalysis(paper.color, members, pat, true)
        }
        val lx = lattice(vs.map { it.cx }, pm.w) ?: return unclean
        return BgAnalysis(paper.color, members, BgPattern(BgKind.GRID, lx[0], ly[0], lx[1], ly[1], color, med, 0.0), true)
    }

    /**
     * Równo rozmieszczone środki linii: [rozstaw, pierwszy środek, liczba]. Wzór musi sięgać obu brzegów strony, bo
     * wzór mNotes rysuje się do krawędzi.
     */
    fun lattice(raw: List<Double>, extent: Double): DoubleArray? {
        val merged = ArrayList<Double>()
        for (v in raw.sorted()) if (merged.isEmpty() || v - merged.last() > 0.3) merged.add(v)
        if (merged.size < MIN_LINES) return null
        val diffs = (1 until merged.size).map { merged[it] - merged[it - 1] }
        val med = diffs.sorted()[diffs.size / 2]
        val tol = max(0.03 * med, 0.15)
        if (diffs.any { abs(it - med) > tol }) return null
        val n = merged.size
        val pitch = (merged.last() - merged.first()) / (n - 1)
        if (pitch < 2.0) return null
        if (merged.first() - pitch >= 0.5) return null
        if (merged.last() + pitch < extent - 0.5) return null
        return doubleArrayOf(pitch, merged.first(), n.toDouble())
    }
}
