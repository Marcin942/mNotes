package com.mdev.mnotes.editor

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.mdev.mnotes.core.layout.CacheBudget
import com.mdev.mnotes.core.layout.Fling
import com.mdev.mnotes.core.layout.Workspace
import com.mdev.mnotes.core.model.DocEdit
import com.mdev.mnotes.core.model.Element

/**
 * Pas stanowiska → kontener (2d-A1, `docs/STANOWISKO-PROJEKT.md` §4): widok dokumentu na stanowisku nie zmienia sam
 * swojej macierzy, tylko zgłasza ruch; kontener zmienia model i w tej samej klatce wpisuje macierz wszystkim pasom.
 */
interface LaneHost {
    fun baseZoom(): Float
    fun minZoom(): Float
    fun maxZoom(): Float
    /** Prostokąt pasa na ekranie (l, t, r, b); null — pas poza ekranem. */
    fun rect(v: NoteView): FloatArray?
    fun active(v: NoteView): Boolean
    /** Przesunięcie palcem; zwraca, ile ruchu w osi stron się zmieściło (reszta = odciągnięcie „nowa strona”). */
    fun pan(v: NoteView, dx: Float, dy: Float): Float
    fun zoomAbout(v: NoteView, px: Float, py: Float, f: Float)
    fun fling(v: NoteView, vx: Float, vy: Float)
    fun animateTo(v: NoteView, z: Float, tx: Float, ty: Float, dur: Long, ax: Float, ay: Float)
    /** Cel ustawiony wprost przez pas (strona, mini-mapa, kółko myszy): ten pas i oś wspólna do celu. */
    fun propose(v: NoteView, z: Float, tx: Float, ty: Float)
    fun stop()
    /** Zmienił się układ stron pasa (nowa strona, inny kierunek). */
    fun structure(v: NoteView)
    /** Pas dostał rozmiar ([first] — pierwszy raz). */
    fun ready(v: NoteView, first: Boolean)
    fun outside(v: NoteView, sx: Float, sy: Float): Boolean
    /**
     * Zaznaczenie upuszczone środkiem w punkcie ekranu ([cx], [cy]) poza swoim pasem; true — przyjął je inny dokument,
     * a [removal] (usunięcie u źródła) trafił na stos źródła w parze z wstawieniem.
     */
    fun dropOutside(v: NoteView, els: List<Element>, cx: Float, cy: Float, removal: DocEdit): Boolean
}

/**
 * Płótno edytora (2d-A1, zastępuje `StationCanvas` z 2b-A7): jeden dokument jak dotąd albo stanowisko — N widoków
 * [NoteView], każdy na całe okno, rysujących tylko swój pas, i jeden model [Workspace], z którego wszystkie dostają
 * macierz. Dotyk trafia do pasa pod palcem (cały gest), pas dotknięty staje się aktywny i rysuje się ostatni.
 * Przy jednym dokumencie widok działa po staremu (własny stan widoku, `OverScroller`) — bez ryzyka regresji.
 */
@SuppressLint("ViewConstructor")
class WorkspaceView(ctx: Context, private val st: Station) : ViewGroup(ctx), LaneHost {
    private val lanes = ArrayList<NoteView>()
    private var ws: Workspace? = null
    private val placed = HashSet<NoteView>()
    private val density = resources.displayMetrics.density

    /** Licznik ruchu dla Compose (nagłówki pasów idą za modelem). */
    var tick by mutableIntStateOf(0)
        private set

    init { isChildrenDrawingOrderEnabled = true }

    /** Ustawia pasy według edytorów stanowiska (kolejność jak [editors]); widok głównego zeszytu zostaje ten sam. */
    fun sync(editors: List<Editor>, accent: Int, bg: Int, dark: Boolean) {
        // Przesunięcia pasów idą za dokumentami, nie za indeksami (zamknięcie środkowego nie przestawia pozostałych).
        val before = ws?.let { w -> lanes.withIndex().associate { (i, v) -> v to w.main.getOrElse(i) { 0f } } }
        for (v in lanes.toList()) if (editors.none { it === v.ed }) { removeView(v); lanes.remove(v); placed.remove(v); v.lane = null }
        val ordered = editors.map { e ->
            lanes.firstOrNull { it.ed === e } ?: NoteView(context, e).also { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
        }
        val changed = ordered != lanes
        lanes.clear(); lanes.addAll(ordered)
        for (v in lanes) { v.accent = accent; v.viewBg = bg; v.darkUi = dark }
        if (lanes.size < 2) {
            // Koniec stanowiska: widok wraca do własnego stanu widoku i swojego „100 %”.
            stop(); ws = null; placed.clear()
            for (v in lanes) if (v.lane != null) { v.lane = null; v.applyLayoutMode() }
            return
        }
        for (v in lanes) v.lane = this
        if (changed) {
            structure(lanes[0])
            val w = ws
            if (w != null && before != null) { w.set(Workspace.Pose(w.zoom, w.cross, FloatArray(w.size) { before[lanes[it]] ?: Float.POSITIVE_INFINITY })); push() }
        }
        invalidate()
    }

    /** Prostokąt pasa [i] na ekranie (dla nagłówków); null — poza ekranem albo bez stanowiska. */
    fun laneRect(i: Int): FloatArray? = ws?.laneRect(i)

    // — model —

    private fun ensure(): Workspace? {
        if (lanes.size < 2 || width == 0 || height == 0) return null
        ws?.let { if (it.horizontal == st.horizontal) return it }
        stop()
        val w = Workspace(st.horizontal, lanes.map { it.docLayout }, width.toFloat(), height.toFloat(), st.main.settings.zoomMaxFactor)
        ws = w; placed.clear()
        post { place() }
        return w
    }

    /** Każdy pas staje na swojej bieżącej stronie; potem zapisany stan stanowiska (jeśli otwierano zapisane). */
    private fun place() {
        val w = ws ?: return
        for ((i, v) in lanes.withIndex()) if (v.width > 0 && placed.add(v)) { v.setViewport(w.zoom, w.tx(i), w.ty(i)); v.goToPage(v.ed.pageIndex, false) }
        restore()
        push()
    }

    private fun restore() {
        val w = ws ?: return
        if (lanes.any { it !in placed }) return
        if (st.pendingIds() != lanes.map { it.ed.nbId }) return
        val r = st.takeRestore() ?: return
        w.set(Workspace.Pose(if (r.zoom > 0f) r.zoom else w.zoom, -r.scroll, FloatArray(w.size) { r.offsets.getOrElse(it) { w.main[it] } }))
        lanes.getOrNull(r.active)?.let { st.activate(it.ed) }
    }

    private fun push() {
        val w = ws ?: return
        for ((i, v) in lanes.withIndex()) if (v.width > 0) v.setViewport(w.zoom, w.tx(i), w.ty(i))
        tick++
        share()
    }

    /** Zapisany stan otwieranego stanowiska, gdy pasy już stoją (te same dokumenty). */
    fun restorePending() { restore(); push() }

    /** Stan do zapisania stanowiska: przewinięcie wspólne (px ≥ 0), powiększenie, przesunięcia pasów, aktywny pas. */
    fun snapshot(): Workspace.Pose? = ws?.pose()

    // — LaneHost —

    override fun baseZoom() = ws?.baseZoom() ?: 1f
    override fun minZoom() = ws?.minZoom() ?: 0.3f
    override fun maxZoom() = ws?.maxZoom() ?: 1f
    override fun rect(v: NoteView): FloatArray? { val w = ensure() ?: return null; return w.laneRect(lanes.indexOf(v)) }
    override fun active(v: NoteView) = st.active === v.ed

    override fun pan(v: NoteView, dx: Float, dy: Float): Float {
        val w = ws ?: return 0f
        val r = w.pan(lanes.indexOf(v), dx, dy)
        push()
        return r
    }

    override fun zoomAbout(v: NoteView, px: Float, py: Float, f: Float) {
        val w = ws ?: return
        w.zoomAbout(px, py, f)
        push()
    }

    override fun propose(v: NoteView, z: Float, tx: Float, ty: Float) {
        val w = ws
        val i = lanes.indexOf(v)
        if (w == null || i < 0) { v.setViewport(z, tx, ty); return }
        w.set(w.poseFor(i, z, tx, ty))
        push()
    }

    override fun structure(v: NoteView) {
        val w = ws
        if (w == null || w.horizontal != st.horizontal) { ensure(); return }
        w.setLanes(lanes.map { it.docLayout })
        push()
    }

    override fun ready(v: NoteView, first: Boolean) {
        val w = ensure() ?: return
        val i = lanes.indexOf(v)
        if (i < 0) return
        v.setViewport(w.zoom, w.tx(i), w.ty(i))
        if (placed.add(v)) v.goToPage(v.ed.pageIndex, false)
        restore()
    }

    override fun outside(v: NoteView, sx: Float, sy: Float): Boolean {
        val w = ws ?: return false
        return w.laneAt(sx, sy) != lanes.indexOf(v)
    }

    override fun dropOutside(v: NoteView, els: List<Element>, cx: Float, cy: Float, removal: DocEdit): Boolean {
        val w = ws ?: return false
        val t = lanes.getOrNull(w.laneAt(cx, cy)) ?: return false
        if (t === v || t.ed.readOnly) return false
        val added = t.dropIn(els, v.ed.nbId, cx, cy) ?: return false
        // Usunięcie w źródle i wstawienie w celu: jeden krok cofania w obu dokumentach (2c-A5).
        v.ed.record(removal)
        st.linked.link(v.ed, removal, t.ed, added)
        st.activate(t.ed)
        invalidate()
        return true
    }

    // — rzut i animacja —

    private var flingLane = -1
    private var flingPair: Pair<Fling, Fling>? = null
    private var flingT0 = 0L
    private val flingFrame = Choreographer.FrameCallback { now -> flingStep(now) }
    private var anim: ValueAnimator? = null

    override fun fling(v: NoteView, vx: Float, vy: Float) {
        val w = ws ?: return
        val i = lanes.indexOf(v)
        if (i < 0) return
        stop()
        flingPair = w.fling(i, vx, vy, density)
        flingLane = i
        flingT0 = System.nanoTime()
        Choreographer.getInstance().postFrameCallback(flingFrame)
    }

    private fun flingStep(now: Long) {
        val w = ws ?: return
        val f = flingPair ?: return
        // Czas klatki z Choreographer (ten sam zegar co System.nanoTime); pierwsza klatka może wypaść tuż przed startem.
        val t = ((now - flingT0) / 1e6f).coerceAtLeast(0f)
        w.flingFrame(flingLane, f, t)
        push()
        if (f.first.done(t) && f.second.done(t)) flingPair = null
        else Choreographer.getInstance().postFrameCallback(flingFrame)
    }

    override fun animateTo(v: NoteView, z: Float, tx: Float, ty: Float, dur: Long, ax: Float, ay: Float) {
        val w = ws
        val i = lanes.indexOf(v)
        if (w == null || i < 0) { v.setViewport(z, tx, ty); return }
        stop()
        val a = w.pose()
        val b = w.poseFor(i, z, tx, ty, if (w.horizontal) ax else ay)
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = dur
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { if (ws === w) { w.set(w.lerp(a, b, it.animatedFraction)); push() } }
            start()
        }
    }

    override fun stop() {
        anim?.cancel(); anim = null
        if (flingPair != null) { flingPair = null; Choreographer.getInstance().removeFrameCallback(flingFrame) }
    }

    // — wspólny budżet bitmap (2c-A6): podział wg widocznej części, pas poza ekranem po 2 s zwalnia wszystko —

    private val budget = CacheBudget(HIDDEN_MS)
    private val trimCheck = Runnable { share() }

    private fun share() {
        val w = ws ?: return
        val p = lanes.firstOrNull()?.renderProfile ?: return
        val now = SystemClock.uptimeMillis()
        val vis = FloatArray(lanes.size) { i -> w.laneRect(i)?.let { r -> if (w.horizontal) r[3] - r[1] else r[2] - r[0] } ?: 0f }
        for ((i, s) in budget.shares(p.tileBytes, p.baseBytes, vis, now).withIndex()) lanes[i].budget(s)
        removeCallbacks(trimCheck)
        budget.nextRelease(now)?.let { postDelayed(trimCheck, it - now + 50L) }
    }

    // — dotyk: cały gest do pasa pod palcem —

    private var target: NoteView? = null
    private var hoverLane: NoteView? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val w = ws
        if (w == null || lanes.size < 2) return super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            // Dotknięcie zatrzymuje trwający rzut (jak w jednym dokumencie) i aktywuje pas pod palcem.
            stop()
            target = lanes.getOrNull(w.laneAt(ev.x, ev.y))
            target?.let { if (st.active !== it.ed) { st.activate(it.ed); invalidate() } }
        }
        val t = target ?: return false
        val r = t.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) target = null
        return r
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val w = ws
        if (w == null || lanes.size < 2 || !ev.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return super.dispatchGenericMotionEvent(ev)
        // Najechanie rysikiem i kółko myszy — do pasa pod wskaźnikiem; poprzedni dostaje wyjście, żeby zgasił kursor.
        val v = lanes.getOrNull(w.laneAt(ev.x, ev.y)) ?: return false
        val old = hoverLane
        if (old != null && old !== v) {
            val exit = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_HOVER_EXIT }
            try { old.dispatchGenericMotionEvent(exit) } finally { exit.recycle() }
        }
        hoverLane = if (ev.actionMasked == MotionEvent.ACTION_HOVER_EXIT) null else v
        return v.dispatchGenericMotionEvent(ev)
    }

    // Aktywny pas rysuje się ostatni: przeciągane zaznaczenie i nakładki widać nad sąsiednimi pasami.
    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
        val a = lanes.indexOfFirst { it.ed === st.active }.let { if (it < 0) -1 else indexOfChild(lanes[it]) }
        if (a < 0 || lanes.size < 2) return drawingPosition
        return when {
            drawingPosition == childCount - 1 -> a
            drawingPosition >= a -> drawingPosition + 1
            else -> drawingPosition
        }
    }

    // — układ: każde dziecko na całe okno —

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec); val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val cw = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY); val ch = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(cw, ch)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) getChildAt(i).layout(0, 0, r - l, b - t)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w == 0 || h == 0) return
        val m = ws
        if (m != null) { m.resize(w.toFloat(), h.toFloat()); push() } else ensure()
    }

    /** Płótno zdjęte z kompozycji: widoki dołożonych dokumentów zwalniają kafelki i wątki (główny zamyka edytor). */
    fun release() {
        stop()
        removeCallbacks(trimCheck)
        for (v in lanes) if (v.ed !== st.main) v.dispose()
    }

    override fun onDetachedFromWindow() {
        stop()
        removeCallbacks(trimCheck)
        super.onDetachedFromWindow()
    }

    companion object {
        /** Po tylu ms poza ekranem pas zwalnia kafelki i bazy stron. */
        const val HIDDEN_MS = 2000L
    }
}
