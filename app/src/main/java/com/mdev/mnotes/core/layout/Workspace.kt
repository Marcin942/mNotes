package com.mdev.mnotes.core.layout

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Stanowisko (2d-A1): kilka dokumentów jako jeden dokument — jedno powiększenie, jedno przesunięcie wspólne, osobne
 * przesunięcie każdego dokumentu w jego osi stron. Jedyne źródło prawdy o widoku stanowiska; widoki dokumentów dostają
 * z niego gotową macierz ([tx], [ty], [zoom]). Model i wzory: `docs/STANOWISKO-PROJEKT.md` §2–§6.
 *
 * [horizontal]: dokumenty to rzędy (strony obok siebie, oś stron = x, oś wspólna = y); inaczej kolumny (oś stron = y).
 * [lanes]: układy stron dokumentów w tym kierunku; [viewW]×[viewH] — okno w px.
 */
class Workspace(val horizontal: Boolean, lanes: List<DocLayout>, viewW: Float, viewH: Float, var maxFactor: Float = ZoomLimits.DEFAULT_FACTOR) {
    var lanes: List<DocLayout> = lanes
        private set
    var viewW = viewW; private set
    var viewH = viewH; private set

    /** Odstęp i margines jak w dokumencie: odstęp stron sąsiednich pasów = odstęp stron w pasie. */
    val gap: Float get() = lanes.firstOrNull()?.gap ?: DocLayout.DEFAULT_GAP
    val pad: Float get() = lanes.firstOrNull()?.pad ?: DocLayout.DEFAULT_PAD

    private var starts = FloatArray(0)
    private var sizes = FloatArray(0)
    /** Długość stanowiska w osi wspólnej (pt). */
    var total = 0f; private set

    var zoom = 1f; private set
    /** Przesunięcie wspólne (px): ekran w osi wspólnej = punkt stanowiska · zoom + cross. */
    var cross = 0f; private set
    /** Przesunięcia pasów w ich osi stron (px). */
    var main = FloatArray(0); private set

    val size: Int get() = lanes.size

    init { geometry(); zoom = startZoom(); main = FloatArray(size); clamp() }

    private fun geometry() {
        val n = lanes.size
        starts = FloatArray(n); sizes = FloatArray(n)
        var p = pad
        for (i in 0 until n) {
            val l = lanes[i]
            sizes[i] = max(0f, (if (horizontal) l.docHeight else l.docWidth) - 2f * l.pad)
            starts[i] = p
            p += sizes[i] + gap
        }
        total = if (n == 0) 2f * pad else p - gap + pad
    }

    private val viewMain: Float get() = if (horizontal) viewW else viewH
    private val viewCross: Float get() = if (horizontal) viewH else viewW

    /** Długość pasa [i] w jego osi stron (pt, z marginesami). */
    fun laneLength(i: Int): Float = if (horizontal) lanes[i].docWidth else lanes[i].docHeight
    /** Początek stron pasa [i] w osi wspólnej stanowiska (pt). */
    fun laneStart(i: Int): Float = starts[i]
    /** Największa strona pasa [i] w osi wspólnej (pt). */
    fun laneSize(i: Int): Float = sizes[i]
    /** Przesunięcie układu pasa względem stanowiska w osi wspólnej (pt). */
    fun laneOffset(i: Int): Float = starts[i] - pad

    // — Macierz pasa —

    fun tx(i: Int): Float = if (horizontal) main[i] else cross + laneOffset(i) * zoom
    fun ty(i: Int): Float = if (horizontal) cross + laneOffset(i) * zoom else main[i]

    fun toDocX(i: Int, sx: Float) = (sx - tx(i)) / zoom
    fun toDocY(i: Int, sy: Float) = (sy - ty(i)) / zoom
    fun toScreenX(i: Int, dx: Float) = dx * zoom + tx(i)
    fun toScreenY(i: Int, dy: Float) = dy * zoom + ty(i)

    // — Granice powiększenia jak w dokumencie —

    /** „100 %”: pierwszy pas wypełnia okno w osi wspólnej (jak `NoteView.baseZoom` dla rzędu albo kolumny). */
    fun baseZoom(): Float = if (size == 0) 1f else viewCross / (sizes[0] + 2f * pad).coerceAtLeast(1f)
    fun minZoom(): Float = baseZoom() * 0.3f
    fun maxZoom(): Float = ZoomLimits.maxZoom(baseZoom(), maxFactor)
    /** Na start całe stanowisko w oknie, ale nie mniej niż 30 % i nie więcej niż „100 %”. */
    fun startZoom(): Float = (viewCross / total.coerceAtLeast(1f)).coerceIn(minZoom(), baseZoom())

    // — Ograniczenia jak `NoteView.clampX/clampY` —

    private fun clampAxis(v: Float, len: Float, view: Float): Float {
        val c = len * zoom
        return if (c <= view) (view - c) / 2f else v.coerceIn(view - c, 0f)
    }

    /** Zakres przesunięcia pasa [i] w osi stron (dla rzutu); pas krótszy od okna stoi na środku. */
    fun mainRange(i: Int): FloatArray { val c = laneLength(i) * zoom; return if (c <= viewMain) floatArrayOf((viewMain - c) / 2f, (viewMain - c) / 2f) else floatArrayOf(viewMain - c, 0f) }
    fun crossRange(): FloatArray { val c = total * zoom; return if (c <= viewCross) floatArrayOf((viewCross - c) / 2f, (viewCross - c) / 2f) else floatArrayOf(viewCross - c, 0f) }

    private fun clamp() {
        cross = clampAxis(cross, total, viewCross)
        for (i in main.indices) main[i] = clampAxis(main[i], laneLength(i), viewMain)
    }

    // — Ruch —

    /**
     * Przesunięcie palcem w pasie [i] o ([dx], [dy]) px: składowa w osi stron rusza tylko ten pas, składowa wspólna —
     * całe stanowisko, naraz. Zwraca, ile ruchu w osi stron się zmieściło (reszta = odciągnięcie za końcem pasa).
     */
    fun pan(i: Int, dx: Float, dy: Float): Float {
        if (i !in 0 until size) return 0f
        val dm = if (horizontal) dx else dy
        val dc = if (horizontal) dy else dx
        val before = main[i]
        main[i] += dm; cross += dc
        clamp()
        return main[i] - before
    }

    /** Szczypanie: powiększenie ×[f] wokół punktu ekranu ([px], [py]) — punkt stoi w każdym pasie. */
    fun zoomAbout(px: Float, py: Float, f: Float) {
        val nz = (zoom * f).coerceIn(minZoom(), maxZoom())
        val k = nz / zoom
        val pm = if (horizontal) px else py
        val pc = if (horizontal) py else px
        cross = pc - (pc - cross) * k
        for (j in main.indices) main[j] = pm - (pm - main[j]) * k
        zoom = nz
        clamp()
    }

    /** Stan widoku do zapamiętania i animacji. */
    class Pose(val zoom: Float, val cross: Float, val main: FloatArray)

    fun pose(): Pose = Pose(zoom, cross, main.copyOf())

    fun set(p: Pose) {
        zoom = p.zoom.coerceIn(minZoom(), maxZoom()); cross = p.cross
        for (j in main.indices) main[j] = p.main.getOrElse(j) { main[j] }
        clamp()
    }

    /**
     * Cel widoku zgłoszony przez pas [i] w jego macierzy ([z], [tx], [ty]) — np. przejście do strony albo dopasowanie:
     * ten pas i oś wspólna idą do celu, pozostałe pasy skalują się wokół punktu [anchorMain] osi stron (domyślnie środek okna).
     */
    fun poseFor(i: Int, z: Float, tx: Float, ty: Float, anchorMain: Float = viewMain / 2f): Pose {
        val nz = z.coerceIn(minZoom(), maxZoom())
        val k = nz / zoom
        val cm = anchorMain
        val m = FloatArray(size) { j -> if (j == i) (if (horizontal) tx else ty) else cm - (cm - main[j]) * k }
        val c = (if (horizontal) ty else tx) - (if (i in 0 until size) laneOffset(i) else 0f) * nz
        val save = pose()
        zoom = nz; cross = c; main = m; clamp()
        val out = pose()
        zoom = save.zoom; cross = save.cross; main = save.main
        return out
    }

    /** Pośredni stan animacji: zoom geometrycznie, przesunięcia liniowo ([t] ∈ 0..1). */
    fun lerp(a: Pose, b: Pose, t: Float): Pose =
        Pose(a.zoom * (b.zoom / a.zoom).pow(t), a.cross + (b.cross - a.cross) * t, FloatArray(a.main.size) { j -> a.main[j] + (b.main.getOrElse(j) { a.main[j] } - a.main[j]) * t })

    /** Rzut w pasie [i] z prędkością palca ([vx], [vy]) px/s: oś stron tego pasa i oś wspólna, każda do swojej granicy. */
    fun fling(i: Int, vx: Float, vy: Float, density: Float): Pair<Fling, Fling> {
        val r = mainRange(i); val c = crossRange()
        val fm = Fling(main[i], if (horizontal) vx else vy, r[0], r[1], density)
        val fc = Fling(cross, if (horizontal) vy else vx, c[0], c[1], density)
        return fm to fc
    }

    /** Klatka rzutu: położenia z [fling] po [tMs] ms. */
    fun flingFrame(i: Int, fling: Pair<Fling, Fling>, tMs: Float) {
        if (i in 0 until size) main[i] = fling.first.at(tMs)
        cross = fling.second.at(tMs)
        clamp()
    }

    // — Zmiany układu —

    /** Nowe układy pasów (strona dodana, pas dołożony lub zamknięty): stan zostaje, przesunięcia nowych pasów od początku. */
    fun setLanes(newLanes: List<DocLayout>) {
        lanes = newLanes
        geometry()
        main = FloatArray(newLanes.size) { main.getOrElse(it) { Float.POSITIVE_INFINITY } }
        clamp()
    }

    /** Zmiana rozmiaru okna: punkt na środku zostaje na środku. */
    fun resize(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val dw = (w - viewW) / 2f; val dh = (h - viewH) / 2f
        viewW = w; viewH = h
        if (horizontal) { for (j in main.indices) main[j] += dw; cross += dh } else { for (j in main.indices) main[j] += dh; cross += dw }
        clamp()
    }

    // — Trafienia —

    /** Pas pod punktem ekranu: granice w połowie odstępu, skrajne pasy sięgają dalej (margines należy do nich). */
    fun laneAt(sx: Float, sy: Float): Int {
        if (size == 0) return -1
        val w = ((if (horizontal) sy else sx) - cross) / zoom
        for (i in 0 until size - 1) if (w < starts[i] + sizes[i] + gap / 2f) return i
        return size - 1
    }

    /** Prostokąt pasa [i] na ekranie (l, t, r, b) przycięty do okna; null — pas poza ekranem. */
    fun laneRect(i: Int): FloatArray? {
        if (i !in 0 until size) return null
        val a = if (i == 0) 0f else max(0f, (starts[i] - gap / 2f) * zoom + cross)
        val b = if (i == size - 1) viewCross else min(viewCross, (starts[i] + sizes[i] + gap / 2f) * zoom + cross)
        if (b <= a) return null
        return if (horizontal) floatArrayOf(0f, a, viewW, b) else floatArrayOf(a, 0f, b, viewH)
    }

    /** Punkt ekranu jako (pas, strona, x, y na stronie); strona najbliższa wzdłuż osi stron. */
    class Hit(val lane: Int, val page: Int, val x: Float, val y: Float)

    fun locate(sx: Float, sy: Float): Hit? {
        val i = laneAt(sx, sy)
        if (i < 0) return null
        val l = lanes[i]
        if (l.count == 0) return null
        val dx = toDocX(i, sx); val dy = toDocY(i, sy)
        val p = l.pageAt(dx, dy).takeIf { it >= 0 } ?: l.nearest(dx, dy)
        return Hit(i, p, dx - l.left(p), dy - l.top(p))
    }

    /**
     * Przesunięcie (pt) elementów przenoszonych ze strony [fromPage] pasa [fromLane] na stronę [toPage] pasa [toLane],
     * żeby zostały w tym samym miejscu ekranu (zoom jest wspólny, więc wystarcza przesunięcie).
     */
    fun relocate(fromLane: Int, fromPage: Int, toLane: Int, toPage: Int): FloatArray {
        val a = lanes[fromLane]; val b = lanes[toLane]
        return floatArrayOf(
            (tx(fromLane) - tx(toLane)) / zoom + a.left(fromPage) - b.left(toPage),
            (ty(fromLane) - ty(toLane)) / zoom + a.top(fromPage) - b.top(toPage),
        )
    }
}
