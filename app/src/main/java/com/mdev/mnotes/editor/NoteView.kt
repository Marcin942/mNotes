package com.mdev.mnotes.editor

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.input.motionprediction.MotionEventPredictor
import com.mdev.mnotes.core.edit.CropFrame
import com.mdev.mnotes.core.edit.CropHandle
import com.mdev.mnotes.core.edit.CutLine
import com.mdev.mnotes.core.edit.EraseMath
import com.mdev.mnotes.core.edit.ImageCut
import com.mdev.mnotes.core.edit.PipetteMath
import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.geom.Ruler
import com.mdev.mnotes.core.layout.DocLayout
import com.mdev.mnotes.core.layout.LayoutMode
import com.mdev.mnotes.core.layout.PageDrop
import com.mdev.mnotes.core.layout.ZoomRegion
import com.mdev.mnotes.core.model.BoxElement
import com.mdev.mnotes.core.model.DocEdit
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.TapeEl
import com.mdev.mnotes.core.model.ElementsEdit
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PageSpace
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.pen.PenSpec
import com.mdev.mnotes.core.pen.StrokeBuilder
import com.mdev.mnotes.core.shapes.ShapeBox
import com.mdev.mnotes.core.shapes.ShapeFrame
import com.mdev.mnotes.core.shapes.ShapeKind
import com.mdev.mnotes.core.text.TextFit
import com.mdev.mnotes.core.shapes.ShapeRecognizer
import com.mdev.mnotes.data.DocListener
import com.mdev.mnotes.data.PageState
import com.mdev.mnotes.render.PageCache
import com.mdev.mnotes.render.PageRenderer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Główny widok edytora: przewijanie/zoom wielu stron, rysowanie na żywo, gumka, lasso z uchwytami,
 * laser, mysz/DeX. Strony rysuje z rastrowego cache ([PageCache]); w razie potrzeby wektorowo.
 *
 * Układ współrzędnych: ekran = dokument·zoom + (tx, ty); dokument = strony ułożone przez [DocLayout];
 * współrzędne strony (pt) = dokument − lewy-górny róg strony.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class NoteView(ctx: Context, val ed: Editor) : View(ctx), DocListener {
    private val doc = ed.doc
    private val settings = ed.settings
    private val dp = resources.displayMetrics.density
    private val cfg = ViewConfiguration.get(ctx)
    private val slop = cfg.scaledTouchSlop.toFloat()

    private val cache = PageCache(doc, ed.app.assets, ed.app.pdf, ed.app.settings.renderQuality) { postInvalidateOnAnimation() }
    private val renderer = PageRenderer(ed.app.assets)
    private val builder = StrokeBuilder()
    private val scroller = OverScroller(ctx)
    private val predictor: MotionEventPredictor? = runCatching { MotionEventPredictor.newInstance(this) }.getOrNull()

    private var layout: DocLayout = DocLayout.of(emptyList(), LayoutMode.VERTICAL)
    var zoom = 1f; private set
    var tx = 0f; private set
    var ty = 0f; private set
    private var inited = false
    private var anim: ValueAnimator? = null

    /** Kolory z motywu Compose (ustawiane z zewnątrz). */
    var accent = 0xFF87CF3E.toInt()
    var viewBg = 0xFFE4E7EB.toInt()
    var darkUi = false

    // — farby —
    private val shadowPaint = Paint().apply { style = Paint.Style.FILL }
    private val borderPaint = Paint().apply { style = Paint.Style.STROKE }
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f * dp; pathEffect = DashPathEffect(floatArrayOf(7f * dp, 5f * dp), 0f) }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFFFFFFF.toInt() }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * dp }
    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f * dp; pathEffect = DashPathEffect(floatArrayOf(9f * dp, 6f * dp), 0f); strokeJoin = Paint.Join.ROUND }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.3f * dp }
    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tmpPath = Path()
    private val tmpRect = RectF()
    private val tmpMatrix = Matrix()

    // — stan wejścia —
    private enum class Mode { NONE, IGNORE, NAV, TOOL, RULER, PICK, CUT, CROP }
    private enum class LM { NONE, DRAW, MOVE, SCALE, ROTATE, VERTEX, RESIZE }

    private var mode = Mode.NONE
    private var toolPid = -1
    private var toolType = MotionEvent.TOOL_TYPE_UNKNOWN
    private var activeTool = Tool.PEN
    private var curX = 0f; private var curY = 0f
    private var downX = 0f; private var downY = 0f; private var downT = 0L
    private var toolMoved = false
    private var stylusNear = false
    private var lastStylusT = 0L
    private var hoverOn = false
    private var hoverX = 0f; private var hoverY = 0f
    private var hoverTool = Tool.PEN

    // nawigacja
    private var vt: VelocityTracker? = null
    private var navMax = 1
    private var navPid = 0
    private var navTravel = 0f
    private var navT0 = 0L
    private var lastCx = 0f; private var lastCy = 0f; private var lastSpan = 0f
    private var navSelect = false                  // czy dotknięcie (bez przesunięcia) tego gestu nawigacji zaznacza element
    private var lastTapT = 0L; private var lastTapX = 0f; private var lastTapY = 0f
    private var tapCount = 0                       // kolejne szybkie dotknięcia palcem w tym samym miejscu (1, 2, 3)
    // Podwójne dotknięcie czeka chwilę na trzecie; bez tego „ponów” po trzech dotknięciach zaczynałoby się od zbędnego „cofnij”.
    private var pendingDouble: Runnable? = null

    // pióro
    private var liveActive = false
    private var livePage = -1
    private var shapeSnapped = false
    private var shapePts = FloatArray(0)
    private var holdX = 0f; private var holdY = 0f
    private var holdRaw = 0                         // ile surowych próbek miało powstać do chwili osiadnięcia pióra (reszta to dryf)
    private var shapeKind = ShapeKind.LINE
    private var shapePoly = false
    private var shapeBase = FloatArray(0)          // kształt tuż po rozpoznaniu (x, y …), baza regulacji
    private var shapeDownX = 0f; private var shapeDownY = 0f   // pióro w chwili rozpoznania (współrzędne strony)
    private var shapeCx = 0f; private var shapeCy = 0f; private var shapeRef = 0f
    private var shapeSnapAng = Float.NaN
    private var shapeLabel: String? = null
    private var predXY = FloatArray(24); private var predN = 0
    private var scratch = FloatArray(3 * 512)

    // linijka (współrzędne dokumentu)
    private val ruler = Ruler(0f, 0f, 0f, 520f)
    private var rulerPlaced = false
    private var rulerGrab = 0              // 0 brak, 1 przesuwanie, 2 obrót uchwytem, 3 dwa palce
    private var rulerHandle = 1            // który uchwyt (±1) obraca linijkę
    private var rulerSide = 0              // ±1: pióro przyklejone do krawędzi, 0 swobodne
    private var rulerU0 = 0f; private var rulerU1 = 0f
    private var rulerSnapped = false
    private var rLastX = 0f; private var rLastY = 0f; private var rLastAng = 0f
    private val rulerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rulerEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f * dp }
    private val rulerTick = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * dp; strokeCap = Paint.Cap.ROUND }
    private val rulerText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f * dp; textAlign = Paint.Align.CENTER }

    // pipeta
    private var pickX = 0f; private var pickY = 0f
    private var pickColor = 0
    private val pickFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pickRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    // gumka
    private val eraseStart = LinkedHashMap<String, PageContent>()
    private var eraseLastPage = -1
    private var erasePending: List<Element>? = null
    private var erasePendingPage: String? = null
    private var eraseNextApply = 0L
    private var eraseFlushPosted = false
    private var eraseLx = 0f; private var eraseLy = 0f

    // lasso
    private var lm = LM.NONE
    private var lPage = -1
    private var lp = FloatArray(256); private var ln = 0
    private var lIds: Set<Int> = emptySet()
    private var lOrig: PageContent? = null
    private var lLifted: PageContent? = null
    private var lEls: List<Element> = emptyList()
    private var lBounds = Bounds.EMPTY
    private var lM: Affine = Affine.IDENTITY
    private var lLifting = false
    /** Przekształcenie z ostatniej narysowanej klatki: tam użytkownik widział obiekt, gdy puścił palec. */
    private var lMShown: Affine? = null
    /** Obiekty zatwierdzone po przesunięciu, rysowane na wierzchu, dopóki łaty kafelków w tle ich nie naniosą. */
    private var ghostEls: List<Element> = emptyList()
    private var ghostPage = -1
    private var ghostArea = Bounds.EMPTY
    private var ghostUntil = 0L
    private var lStartX = 0f; private var lStartY = 0f
    private var lAx = 0f; private var lAy = 0f; private var lHx = 0f; private var lHy = 0f
    private var lAngleStart = 0f
    private var vIdx = 0; private var vBase: Stroke? = null; private var vDx = 0f; private var vDy = 0f
    private var rFrame: ShapeFrame? = null; private var rHandle = 0
    private var tBase: TextBox? = null; private var tSide = TextFit.Side.RIGHT
    private var sbSel: Sel? = null; private var sbKey = -1; private var sbVal: Bounds? = null

    // laser
    private val laser = LaserTrail()
    private var lsX = FloatArray(256); private var lsY = FloatArray(256); private var lsNx = FloatArray(256); private var lsNy = FloatArray(256)

    private val holdRunnable = Runnable {
        if (mode == Mode.TOOL && activeTool == Tool.PEN && liveActive && !shapeSnapped) trySnapShape()
        else if (mode == Mode.TOOL && activeTool == Tool.LASSO) lassoHold()
    }
    private val longPress = Runnable {
        if (mode == Mode.NAV && navMax == 1 && navTravel < slop * 1.5f) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            openCtx(lastCx, lastCy)
            mode = Mode.IGNORE
        }
    }

    init {
        isFocusable = false
        doc.listener = this
        ed.view = this
        ed.app.assets.onLoaded = { cache.invalidateAll(); invalidate() }
        rebuildLayout()
    }

    // ───────────────────────── układ i widok ─────────────────────────

    private fun rebuildLayout() {
        val mode = if (ed.layoutMode == 0) LayoutMode.VERTICAL else LayoutMode.HORIZONTAL
        layout = DocLayout.of(doc.pages.map { it.spec }, mode)
        ed.pageCount = doc.pages.size
    }

    /** Wołane po zmianie trybu układu w ustawieniach/menu. */
    fun applyLayoutMode() {
        rebuildLayout()
        if (width == 0) return
        zoom = baseZoom()
        goToPage(ed.pageIndex, false)
    }

    private fun fitPageZoom(i: Int): Float {
        if (i !in 0 until layout.count || width == 0) return 1f
        val m = 14f * dp
        return min((width - 2 * m) / layout.width(i), (height - 2 * m) / layout.height(i))
    }

    /** „100%”: dopasowanie do szerokości (pion), wysokości (poziom) lub całej strony (pojedyncza). */
    fun baseZoom(): Float {
        if (width == 0 || layout.count == 0) return 1f
        return when (ed.layoutMode) {
            0 -> width / layout.docWidth
            1 -> height / layout.docHeight
            else -> fitPageZoom(ed.pageIndex.coerceIn(0, layout.count - 1))
        }
    }

    private fun minZoom() = baseZoom() * 0.3f
    private fun maxZoom() = max(14f, baseZoom() * 6f)

    private fun clampX(x: Float, z: Float): Float { val cw = layout.docWidth * z; return if (cw <= width) (width - cw) / 2 else x.coerceIn(width - cw, 0f) }
    private fun clampY(y: Float, z: Float): Float { val ch = layout.docHeight * z; return if (ch <= height) (height - ch) / 2 else y.coerceIn(height - ch, 0f) }

    private fun docX(sx: Float) = (sx - tx) / zoom
    private fun docY(sy: Float) = (sy - ty) / zoom

    private fun moved() {
        tx = clampX(tx, zoom); ty = clampY(ty, zoom)
        if (layout.count > 0) {
            val i = layout.nearest(docX(width / 2f), docY(height / 2f))
            if (i != ed.pageIndex) ed.pageIndex = i
            ed.zoomPct = (zoom / baseZoom() * 100f).roundToInt()
        }
        ed.viewTick++
        invalidate()
    }

    private fun zoomAbout(px: Float, py: Float, f: Float) {
        val nz = (zoom * f).coerceIn(minZoom(), maxZoom())
        val k = nz / zoom
        tx = px - (px - tx) * k; ty = py - (py - ty) * k; zoom = nz
    }

    private fun stopAnim() { anim?.cancel(); anim = null; scroller.forceFinished(true) }

    private fun animateView(zT: Float, xT: Float, yT: Float, dur: Long = 240L) {
        anim?.cancel()
        val z0 = zoom; val x0 = tx; val y0 = ty
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = dur
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                val t = it.animatedFraction
                zoom = z0 * (zT / z0).pow(t); tx = x0 + (xT - x0) * t; ty = y0 + (yT - y0) * t
                moved()
            }
            start()
        }
    }

    private fun targetFor(i: Int): Triple<Float, Float, Float> {
        val spec = doc.pages[i].spec
        return when (ed.layoutMode) {
            0 -> Triple(zoom, clampX(tx, zoom), clampY(-(layout.top(i) - layout.pad * 0.5f) * zoom, zoom))
            1 -> Triple(zoom, clampX(-(layout.left(i) - layout.pad * 0.5f) * zoom, zoom), clampY(ty, zoom))
            else -> {
                val z = fitPageZoom(i)
                Triple(z, clampX((width - spec.w * z) / 2 - layout.left(i) * z, z), clampY((height - spec.h * z) / 2 - layout.top(i) * z, z))
            }
        }
    }

    fun goToPage(i: Int, animate: Boolean = true) {
        if (doc.pages.isEmpty() || width == 0) return
        val idx = i.coerceIn(0, doc.pages.size - 1)
        val (z, x, y) = targetFor(idx)
        stopAnim()
        if (animate) animateView(z, x, y, 300L) else { zoom = z; tx = x; ty = y; moved() }
    }

    /** Wykonuje [change] (zmianę kolejności stron) tak, by strona [pageId] została w tym samym miejscu ekranu. */
    fun keepPageInPlace(pageId: String, change: () -> Unit) {
        val i0 = doc.indexOf(pageId)
        val before = if (i0 in 0 until layout.count) layout.rect(i0) else null
        change()
        val i1 = doc.indexOf(pageId)
        if (before == null || i1 !in 0 until layout.count) return
        val after = layout.rect(i1)
        stopAnim()
        tx -= (after.l - before.l) * zoom; ty -= (after.t - before.t) * zoom
        moved()
    }

    fun ensureVisible(pageId: String) {
        val i = doc.indexOf(pageId)
        if (i < 0 || width == 0) return
        val r = layout.rect(i)
        val l = r.l * zoom + tx; val t = r.t * zoom + ty
        if (l > width || t > height || l + r.w * zoom < 0 || t + r.h * zoom < 0) goToPage(i)
    }

    fun fitWidth() {
        if (width == 0 || layout.count == 0) return
        val i = ed.pageIndex.coerceIn(0, layout.count - 1)
        val z = if (ed.layoutMode == 2) fitPageZoom(i) else baseZoom()
        val k = z / zoom
        val cx = width / 2f; val cy = height / 2f
        animateView(z, clampX(cx - (cx - tx) * k, z), clampY(cy - (cy - ty) * k, z))
    }

    fun zoomBy(f: Float) {
        val nz = (zoom * f).coerceIn(minZoom(), maxZoom())
        val k = nz / zoom
        val cx = width / 2f; val cy = height / 2f
        animateView(nz, clampX(cx - (cx - tx) * k, nz), clampY(cy - (cy - ty) * k, nz), 160L)
    }

    /** Akcja podwójnego albo potrójnego dotknięcia palcem wybrana w ustawieniach: 0 cofnij, 1 ponów, 2 przybliż/oddal, 3 brak. */
    private fun tapAction(action: Int, sx: Float, sy: Float) {
        when (action) { 0 -> ed.undo(); 1 -> ed.redo(); 2 -> toggleZoom(sx, sy) }
    }

    private fun cancelPendingDouble() {
        pendingDouble?.let { removeCallbacks(it) }
        pendingDouble = null
    }

    private fun toggleZoom(sx: Float, sy: Float) {
        val base = baseZoom()
        val nz = if (zoom > base * 1.35f) base else min(base * 2.4f, maxZoom())
        val k = nz / zoom
        animateView(nz, clampX(sx - (sx - tx) * k, nz), clampY(sy - (sy - ty) * k, nz), 200L)
    }

    /** Środek widoku jako (indeks strony, x, y) we współrzędnych strony — miejsce wklejania/wstawiania. */
    fun viewCenterOnPage(): Triple<Int, Float, Float>? {
        if (doc.pages.isEmpty() || layout.count == 0) return null
        val dx = docX(width / 2f); val dy = docY(height / 2f)
        var i = layout.pageAt(dx, dy)
        if (i < 0) i = layout.nearest(dx, dy)
        val r = layout.rect(i)
        return Triple(i, (dx - r.l).coerceIn(0f, r.w), (dy - r.t).coerceIn(0f, r.h))
    }

    fun pageScreenRect(i: Int): RectF {
        val r = layout.rect(i)
        return RectF(r.l * zoom + tx, r.t * zoom + ty, r.r * zoom + tx, r.b * zoom + ty)
    }

    /** Ramka zaznaczenia w pikselach ekranu (dla paska kontekstowego Compose). */
    fun selScreenRect(): RectF? {
        val s = ed.sel ?: return null
        val pi = doc.indexOf(s.pageId); if (pi < 0) return null
        val b = (if (lLifting) lM.mapBounds(lBounds) else selBounds()) ?: return null
        val ox = layout.left(pi); val oy = layout.top(pi)
        return RectF((ox + b.l) * zoom + tx, (oy + b.t) * zoom + ty, (ox + b.r) * zoom + tx, (oy + b.b) * zoom + ty)
    }

    /** Prostokąt (w pikselach widoku) opisany na ramce przycinania; do ustawiania panelu tak, by jej nie zasłaniał. */
    fun cropScreenRect(): RectF? {
        val s = ed.crop ?: return null
        val pi = doc.indexOf(s.pageId); if (pi < 0) return null
        val fc = com.mdev.mnotes.core.edit.CropGeom.corners(s.frame)
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in 0..3) {
            val p = s.place.toPage(fc[i * 2], fc[i * 2 + 1], s.iw, s.ih)
            val x = scrX(pi, p[0]); val y = scrY(pi, p[1])
            l = minOf(l, x); r = maxOf(r, x); t = minOf(t, y); b = maxOf(b, y)
        }
        return RectF(l, t, r, b)
    }

    private fun selBounds(): Bounds? {
        val s = ed.sel ?: return null
        val p = doc.page(s.pageId) ?: return null
        if (sbSel === s && sbKey == p.version * 31 + ed.selTick) return sbVal
        var b = Bounds.EMPTY
        for (e in ed.selElements()) b = b.union(e.bounds)
        sbSel = s; sbKey = p.version * 31 + ed.selTick; sbVal = if (b.isEmpty) null else b
        return sbVal
    }

    fun dispose() {
        removeCallbacks(holdRunnable); removeCallbacks(longPress); cancelPendingDouble()
        stopAnim()
        doc.listener = null
        ed.app.assets.onLoaded = null
        cache.dispose()
    }

    // ───────────────────────── DocListener ─────────────────────────

    override fun pageChanged(pageId: String, edit: ElementsEdit?) {
        val page = doc.page(pageId) ?: return
        if (edit == null) { invalidate(); return }   // zmiana wzoru/papieru: wymiary bez zmian, więc układ stron zostaje
        val dirty = applyDirty?.takeIf { applyPage == pageId }
        applyDirty = null; applyPage = null
        ghostEls = emptyList()
        cache.onChanged(page, edit, dirty)
        invalidate()
    }

    override fun structureChanged() {
        rebuildLayout()
        ed.structTick++
        if (ed.pageIndex >= layout.count) ed.pageIndex = max(0, layout.count - 1)
        moved()
    }

    // ───────────────────────── rysowanie widoku ─────────────────────────

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w == 0 || h == 0) return
        if (!inited) {
            inited = true
            ed.pageIndex = doc.meta.lastPage.coerceIn(0, max(0, doc.pages.size - 1))
            zoom = baseZoom()
            goToPage(ed.pageIndex, false)
        } else {
            val cx = docX(ow / 2f); val cy = docY(oh / 2f)
            tx = w / 2f - cx * zoom; ty = h / 2f - cy * zoom
            moved()
        }
    }

    override fun onDraw(c: Canvas) {
        // Compose hostuje widok z clipChildren=false: bez własnego przycięcia tło zamalowałoby sąsiednie paski (nagłówek wyglądał na czarny).
        c.clipRect(0, 0, width, height)
        c.drawColor(viewBg)
        if (doc.pages.isEmpty() || layout.count == 0) return
        val vb = Bounds(docX(0f), docY(0f), docX(width.toFloat()), docY(height.toFloat()))
        val range = layout.visibleRange(vb)
        // Kafelki przygotowujemy z zapasem poza ekranem (w kierunku przewijania), żeby brzeg nie był chwilowo miękki.
        val mx = width * 0.3f / zoom; val my = height * 0.3f / zoom
        val rvb = Bounds(vb.l - mx, vb.t - my, vb.r + mx, vb.b + my)
        val reqRange = if (mode == Mode.NAV) range else layout.visibleRange(rvb)
        cache.configure(settings.renderQuality)
        ed.crop.let { cr -> if (cr == null) cache.hide(null, 0) else cache.hide(cr.pageId, cr.imageId) }
        cache.beginFrame(zoom, (vb.l + vb.r) / 2f, (vb.t + vb.b) / 2f)
        shadowPaint.color = if (darkUi) 0x66000000 else 0x24000000
        borderPaint.color = if (darkUi) 0x33FFFFFF else 0x14000000
        borderPaint.strokeWidth = 1f / zoom

        val pullOff = pullOffset()
        if (pullOff > 0f) { c.save(); if (ed.layoutMode == 1) c.translate(-pullOff, 0f) else c.translate(0f, -pullOff) }
        c.save()
        c.translate(tx, ty); c.scale(zoom, zoom)
        for (i in range) drawPage(c, i, vb, rvb)
        if (reqRange.first < range.first || reqRange.last > range.last) {
            for (i in reqRange) if (i !in range) requestPage(i, rvb)
        }
        drawLive(c)
        c.restore()

        drawRuler(c)
        drawSpace(c)
        drawTapePreview(c)
        drawPicker(c)
        drawCut(c)
        drawCrop(c)
        drawLasso(c)
        drawSelection(c)
        drawShapeLabel(c)
        drawRing(c)
        drawSearch(c)
        if (pullOff > 0f) { c.restore(); drawPullHint(c, pullOff) }
        drawLaser(c)
        drawZoomWin(c)
    }

    private fun requestPage(i: Int, rvb: Bounds) {
        val page = doc.pages[i]; val r = layout.rect(i)
        cache.request(page, Bounds(rvb.l - r.l, rvb.t - r.t, rvb.r - r.l, rvb.b - r.t), r.l, r.t)
    }

    private fun drawPage(c: Canvas, i: Int, vb: Bounds, rvb: Bounds) {
        val page = doc.pages[i]; val spec = page.spec; val r = layout.rect(i)
        val o = 2f * dp / zoom
        c.drawRect(r.l - 0.5f * o, r.t, r.r + 0.5f * o, r.b + o, shadowPaint)
        c.save()
        // Po ustaniu zoomu kafelki są w skali 1:1 — origo strony przycinamy do piksela, żeby nic nie było rozmyte filtrem.
        if (cache.pixelExact) {
            val sx = tx + r.l * zoom; val sy = ty + r.t * zoom
            c.translate((Math.round(sx) - sx) / zoom, (Math.round(sy) - sy) / zoom)
        }
        c.translate(r.l, r.t)
        c.clipRect(0f, 0f, spec.w, spec.h)
        val vis = Bounds(vb.l - r.l, vb.t - r.t, vb.r - r.l, vb.b - r.t)
        val req = Bounds(rvb.l - r.l, rvb.t - r.t, rvb.r - r.l, rvb.b - r.t)
        if (!cache.draw(c, page, vis, req, r.l, r.t, zoom)) {
            renderer.drawPaper(c, spec, zoom, null)
            val content = page.content
            if (content != null) renderer.drawElements(c, doc.id, cache.visible(page.id, content), 0, vis, zoom, false, spec.darkPaper)
        }
        c.restore()
        c.drawRect(r.l, r.t, r.r, r.b, borderPaint)
    }

    private fun drawLive(c: Canvas) {
        if (liveActive && livePage in 0 until layout.count) {
            val spec = doc.pages[livePage].spec
            c.save()
            c.translate(layout.left(livePage), layout.top(livePage))
            c.clipRect(0f, 0f, spec.w, spec.h)
            if (shapeSnapped) renderer.drawStrokeData(c, builder.penKind, builder.penColor, builder.width, shapePts, shapePts.size / 3, zoom, spec.darkPaper, shapePoly, builder.line, true)
            else {
                val n = builder.count
                var total = n
                if (predN > 0 && n > 0) {
                    val need = 3 * (n + predN)
                    if (scratch.size < need) scratch = FloatArray(need * 2)
                    System.arraycopy(builder.pts, 0, scratch, 0, 3 * n)
                    val ox = layout.left(livePage); val oy = layout.top(livePage)
                    val pl = builder.pts[3 * n - 1]
                    for (k in 0 until predN) {
                        scratch[3 * (n + k)] = docX(predXY[2 * k]) - ox
                        scratch[3 * (n + k) + 1] = docY(predXY[2 * k + 1]) - oy
                        scratch[3 * (n + k) + 2] = pl
                    }
                    total = n + predN
                    renderer.drawStrokeData(c, builder.penKind, builder.penColor, builder.width, scratch, total, zoom, spec.darkPaper, line = builder.line)
                } else renderer.drawStrokeData(c, builder.penKind, builder.penColor, builder.width, builder.pts, total, zoom, spec.darkPaper, line = builder.line)
            }
            c.restore()
        }
        if (lLifting && lPage in 0 until layout.count) {
            val tgt = dropPage()
            val spec = doc.pages[tgt].spec
            c.save()
            c.translate(layout.left(tgt), layout.top(tgt))
            c.clipRect(0f, 0f, spec.w, spec.h)
            c.translate(layout.left(lPage) - layout.left(tgt), layout.top(lPage) - layout.top(tgt))
            c.concat(matrixOf(lM))
            val z = zoom * lM.scaleFactor
            for (el in lEls) renderer.drawElement(c, doc.id, el, z, false, spec.darkPaper)
            c.restore()
            lMShown = lM
        }
        drawGhost(c)
    }

    /** Etykieta regulacji kształtu (długość i kąt / wymiary) przy piórze. */
    private fun drawShapeLabel(c: Canvas) {
        val label = shapeLabel ?: return
        if (!(liveActive && shapeSnapped) && !(lLifting && lm == LM.RESIZE)) return
        rulerText.color = 0xFFFFFFFF.toInt()
        val w = rulerText.measureText(label) + 18f * dp
        val h = 22f * dp
        val cx = curX.coerceIn(w / 2 + 4f * dp, max(w / 2 + 4f * dp, width - w / 2 - 4f * dp))
        val cy = max(h, curY - 44f * dp)
        rulerFill.color = rgbA(0xFF1F2933.toInt(), 0xE0)
        c.drawRoundRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, h / 2, h / 2, rulerFill)
        c.drawText(label, cx, cy + 3.4f * dp, rulerText)
    }

    private fun matrixOf(m: Affine): Matrix {
        tmpMatrix.setValues(floatArrayOf(m.a, m.c, m.e, m.b, m.d, m.f, 0f, 0f, 1f))
        return tmpMatrix
    }

    private fun scrX(pi: Int, px: Float) = (layout.left(pi) + px) * zoom + tx
    private fun scrY(pi: Int, py: Float) = (layout.top(pi) + py) * zoom + ty

    // — wstaw / usuń odstęp (EDT-065) —
    private var spPage = -1
    private var spY = 0f
    private var spDy = 0f
    private val spaceLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * dp; pathEffect = DashPathEffect(floatArrayOf(10f * dp, 6f * dp), 0f) }
    private val spaceFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val spaceText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f * dp; typeface = Typeface.DEFAULT_BOLD }

    private fun spaceDown() {
        spDy = 0f
        spPage = pageIdxAt(curX, curY)
        if (spPage >= 0) spY = pgY(spPage, curY)
    }

    private fun spaceMove() {
        val p = doc.pages.getOrNull(spPage) ?: return
        spDy = PageSpace.clamp(doc.content(p), layout.height(spPage), spY, pgY(spPage, curY) - spY)
    }

    private fun spaceUp(cancel: Boolean) {
        val p = doc.pages.getOrNull(spPage)
        if (!cancel && p != null && abs(spDy) * zoom > 4f * dp) ed.shiftBelow(p.id, spY, spDy)
        spPage = -1; spDy = 0f
    }

    private fun drawSpace(c: Canvas) {
        if (mode != Mode.TOOL || activeTool != Tool.SPACE || spPage !in 0 until layout.count) return
        val x0 = scrX(spPage, 0f); val x1 = scrX(spPage, layout.width(spPage))
        val y0 = scrY(spPage, spY); val y1 = scrY(spPage, spY + spDy)
        spaceFill.color = (accent and 0x00FFFFFF) or 0x2E000000
        c.drawRect(x0, min(y0, y1), x1, max(y0, y1), spaceFill)
        spaceLine.color = accent
        c.drawLine(x0, y0, x1, y0, spaceLine)
        if (spDy != 0f) c.drawLine(x0, y1, x1, y1, spaceLine)
        val v = if (settings.unitsMm) "%.1f mm".format(abs(spDy) * 25.4f / 72f) else "%.2f\"".format(abs(spDy) / 72f)
        val label = (if (spDy < 0f) "− " else if (spDy > 0f) "+ " else "") + v
        spaceText.color = accent
        c.drawText(label, max(x0, 0f) + 10f * dp, (if (spDy >= 0f) y0 else y1) - 6f * dp, spaceText)
    }

    // — taśma (EDT-064) —
    private var tpPage = -1
    private var tpX = 0f
    private var tpY = 0f
    private var tpX1 = 0f
    private var tpY1 = 0f
    private val tapePrev = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun tapeDown() {
        tpPage = pageIdxAt(curX, curY)
        if (tpPage >= 0) { tpX = pgX(tpPage, curX); tpY = pgY(tpPage, curY); tpX1 = tpX; tpY1 = tpY }
    }

    private fun tapeMove() {
        if (tpPage !in 0 until layout.count) return
        tpX1 = pgX(tpPage, curX).coerceIn(0f, layout.width(tpPage)); tpY1 = pgY(tpPage, curY).coerceIn(0f, layout.height(tpPage))
    }

    private fun tapeUp(cancel: Boolean) {
        val p = doc.pages.getOrNull(tpPage)
        if (!cancel && p != null) {
            if (!toolMoved) {
                val hit = doc.content(p).elements.lastOrNull { it is TapeEl && it.containsPoint(tpX, tpY, 4f * dp / zoom) }
                if (hit != null) ed.toggleTape(p.id, hit.id)
            } else ed.addTape(p.id, tpX, tpY, tpX1, tpY1)
        }
        tpPage = -1
    }

    private fun drawTapePreview(c: Canvas) {
        if (mode != Mode.TOOL || activeTool != Tool.TAPE || tpPage !in 0 until layout.count || !toolMoved) return
        val col = settings.tapeColor and 0x00FFFFFF
        val x0 = scrX(tpPage, min(tpX, tpX1)); val x1 = scrX(tpPage, max(tpX, tpX1))
        val y0 = scrY(tpPage, min(tpY, tpY1)); val y1 = scrY(tpPage, max(tpY, tpY1))
        tapePrev.style = Paint.Style.FILL; tapePrev.color = col or 0xB0000000.toInt()
        c.drawRoundRect(x0, y0, x1, y1, 4f * dp, 4f * dp, tapePrev)
        tapePrev.style = Paint.Style.STROKE; tapePrev.strokeWidth = 1.5f * dp; tapePrev.color = accent
        c.drawRoundRect(x0, y0, x1, y1, 4f * dp, 4f * dp, tapePrev)
    }

    private fun drawLasso(c: Canvas) {
        val eraser = activeTool == Tool.ERASER && settings.eraserMode == 2
        val replaces = eraser || (activeTool == Tool.PAINTER && settings.painterMode == 1)
        if (mode != Mode.TOOL || lm != LM.DRAW && !replaces || ln == 0 || lPage !in 0 until layout.count) return
        val col = if (eraser) 0xFFE5484D.toInt() else if (replaces) ed.pen.color or (0xFF shl 24) else accent
        lassoPaint.color = col
        tmpPath.rewind()
        if (settings.lassoRect && !replaces) {
            val x0 = scrX(lPage, lp[0]); val y0 = scrY(lPage, lp[1])
            val x1 = scrX(lPage, lp[2 * (ln - 1)]); val y1 = scrY(lPage, lp[2 * (ln - 1) + 1])
            tmpPath.addRect(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1), Path.Direction.CW)
        } else {
            tmpPath.moveTo(scrX(lPage, lp[0]), scrY(lPage, lp[1]))
            for (i in 1 until ln) tmpPath.lineTo(scrX(lPage, lp[2 * i]), scrY(lPage, lp[2 * i + 1]))
            if (ln > 2) tmpPath.close()
        }
        c.drawPath(tmpPath, lassoPaint)
    }

    private fun cornersScreen(pi: Int, b: Bounds): FloatArray = floatArrayOf(
        scrX(pi, b.l), scrY(pi, b.t), scrX(pi, b.r), scrY(pi, b.t),
        scrX(pi, b.r), scrY(pi, b.b), scrX(pi, b.l), scrY(pi, b.b),
    )

    private fun drawSelection(c: Canvas) {
        if (ed.cut != null || ed.crop != null) return
        val s = ed.sel ?: return
        val pi = doc.indexOf(s.pageId); if (pi < 0) return
        val cs: FloatArray
        if (lLifting && lm != LM.RESIZE) {
            val b = lBounds
            val m = lM
            cs = floatArrayOf(
                scrX(pi, m.mapX(b.l, b.t)), scrY(pi, m.mapY(b.l, b.t)), scrX(pi, m.mapX(b.r, b.t)), scrY(pi, m.mapY(b.r, b.t)),
                scrX(pi, m.mapX(b.r, b.b)), scrY(pi, m.mapY(b.r, b.b)), scrX(pi, m.mapX(b.l, b.b)), scrY(pi, m.mapY(b.l, b.b)),
            )
        } else cs = selQuad(pi, selBounds() ?: (if (lLifting) lBounds else return))
        selPaint.color = accent
        handleStroke.color = accent
        tmpPath.rewind()
        tmpPath.moveTo(cs[0], cs[1]); tmpPath.lineTo(cs[2], cs[3]); tmpPath.lineTo(cs[4], cs[5]); tmpPath.lineTo(cs[6], cs[7]); tmpPath.close()
        c.drawPath(tmpPath, selPaint)
        val locked = ed.selElements().let { it.isNotEmpty() && it.all { e -> e.locked } }
        if (locked) return
        val r = 6.5f * dp
        val poly = polySel()
        val shape = shapeSel()
        if (ed.transformMode) {
            if (poly == null && shape == null) for (k in 0..3) { c.drawCircle(cs[2 * k], cs[2 * k + 1], r, handleFill); c.drawCircle(cs[2 * k], cs[2 * k + 1], r, handleStroke) }
            val rot = rotateHandle(cs, topmost = !(lLifting && lm == LM.ROTATE))
            c.drawLine(rot[2], rot[3], rot[0], rot[1], handleStroke)
            c.drawCircle(rot[0], rot[1], r, handleFill); c.drawCircle(rot[0], rot[1], r, handleStroke)
        }
        if (poly != null) for (i in 0 until poly.vertexCount) {
            val vx = scrX(pi, poly.pts[3 * i]); val vy = scrY(pi, poly.pts[3 * i + 1])
            c.drawCircle(vx, vy, r * 1.15f, handleFill); c.drawCircle(vx, vy, r * 1.15f, handleStroke)
        }
        textSel()?.let { tb ->
            for (side in TextFit.Side.values()) {
                val hx = scrX(pi, textHandleX(tb, side)); val hy = scrY(pi, textHandleY(tb, side))
                c.drawCircle(hx, hy, r * 0.95f, handleFill); c.drawCircle(hx, hy, r * 0.95f, handleStroke)
            }
        }
        val sf = shape?.let { ShapeBox.frameOf(it) }
        if (sf != null) for (h in 0 until ShapeBox.HANDLES) {
            val hx = scrX(pi, ShapeBox.handleX(sf, h)); val hy = scrY(pi, ShapeBox.handleY(sf, h))
            val hr = if (ShapeBox.isCorner(h)) r * 1.15f else r * 0.95f
            c.drawCircle(hx, hy, hr, handleFill); c.drawCircle(hx, hy, hr, handleStroke)
        }
    }

    /** Uchwyt obrotu: nad najwyżej leżącą krawędzią ramki (cztery rogi [cs]); zwraca (x, y) uchwytu i (x, y) środka krawędzi. */
    private fun rotateHandle(cs: FloatArray, topmost: Boolean = true): FloatArray {
        val gx = (cs[0] + cs[2] + cs[4] + cs[6]) / 4; val gy = (cs[1] + cs[3] + cs[5] + cs[7]) / 4
        var bx = 0f; var by = 0f; var bnx = 0f; var bny = 1f
        for (k in 0..3) {
            val k2 = (k + 1) % 4
            val mx = (cs[2 * k] + cs[2 * k2]) / 2; val my = (cs[2 * k + 1] + cs[2 * k2 + 1]) / 2
            var nx = mx - gx; var ny = my - gy
            val l = hypot(nx, ny).coerceAtLeast(0.001f)
            nx /= l; ny /= l
            if (k == 0 || (topmost && ny < bny)) { bx = mx; by = my; bnx = nx; bny = ny }
        }
        return floatArrayOf(bx + bnx * ROTATE_OFFSET_DP * dp, by + bny * ROTATE_OFFSET_DP * dp, bx, by)
    }

    /** Ramka zaznaczenia na ekranie (4 rogi): dla kształtu z uchwytami zorientowana, inaczej prostokąt osiowy [b]. */
    private fun selQuad(pi: Int, b: Bounds): FloatArray {
        val f = shapeSel()?.let { ShapeBox.frameOf(it) } ?: return cornersScreen(pi, b)
        val cs = FloatArray(8)
        for (k in 0..3) { cs[2 * k] = scrX(pi, ShapeBox.handleX(f, 2 * k)); cs[2 * k + 1] = scrY(pi, ShapeBox.handleY(f, 2 * k)) }
        return cs
    }

    private fun drawRing(c: Canvas) {
        val t: Tool; val x: Float; val y: Float
        if (mode == Mode.TOOL && (activeTool == Tool.ERASER || activeTool == Tool.PEN || activeTool == Tool.PAINTER) && toolType != MotionEvent.TOOL_TYPE_FINGER) { t = activeTool; x = curX; y = curY }
        else if (hoverOn && mode == Mode.NONE) { t = hoverTool; x = hoverX; y = hoverY }
        else return
        when (t) {
            Tool.ERASER -> {
                if (settings.eraserMode == 2) return
                ringPaint.color = if (darkUi) 0xCCFFFFFF.toInt() else 0xCC202124.toInt()
                c.drawCircle(x, y, settings.eraserSize * zoom, ringPaint)
            }
            Tool.PEN -> {
                if (mode == Mode.TOOL) return
                val w = ed.pen.width * zoom
                ringPaint.color = ed.pen.color and 0x00FFFFFF or 0x99000000.toInt()
                c.drawCircle(x, y, max(w / 2f, 2.5f * dp), ringPaint)
            }
            Tool.PAINTER -> {
                if (settings.painterMode == 1) return
                ringPaint.color = ed.pen.color and 0x00FFFFFF or 0xCC000000.toInt()
                c.drawCircle(x, y, PAINT_R_DP * dp, ringPaint)
            }
            Tool.LASER -> drawLaserDot(c, x, y, 1f)
            else -> {}
        }
    }

    private val hitFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hitEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * dp; color = 0xFFFF9800.toInt() }

    /** Podświetla trafienia wyszukiwania w widocznym obszarze; bieżące jest mocniejsze i obrysowane. */
    private fun drawSearch(c: Canvas) {
        val hits = ed.searchHits
        if (hits.isEmpty() || !ed.searchOpen) return
        val cur = ed.searchCur
        val r = 4f * dp
        for (k in hits.indices) {
            val h = hits[k]
            val b = h.box ?: continue
            if (h.pageIndex >= layout.count) continue
            val l = scrX(h.pageIndex, b.l) - r; val t = scrY(h.pageIndex, b.t) - r
            val rt = scrX(h.pageIndex, b.r) + r; val bt = scrY(h.pageIndex, b.b) + r
            if (rt < 0 || l > width || bt < 0 || t > height) continue
            val on = k == cur
            hitFill.color = if (on) 0x66FFB300 else 0x44FFE066
            c.drawRoundRect(l, t, rt, bt, r, r, hitFill)
            if (on) c.drawRoundRect(l, t, rt, bt, r, r, hitEdge)
        }
    }

    /** Wyśrodkowuje trafienie (albo, bez prostokąta, całą stronę) na ekranie. */
    fun showHit(pi: Int, b: Bounds?) {
        if (doc.pages.isEmpty() || width == 0 || pi !in 0 until layout.count) return
        stopAnim()
        if (b == null || ed.layoutMode == 2) { goToPage(pi); return }
        val cx = layout.left(pi) + (b.l + b.r) / 2; val cy = layout.top(pi) + (b.t + b.b) / 2
        animateView(zoom, clampX(width / 2f - cx * zoom, zoom), clampY(height / 2f - cy * zoom, zoom), 260L)
    }

    // Warstwy od zewnątrz: kolorowa poświata, korpus, biały rdzeń („prześwietlony” środek jak w GoodNotes).
    private val laserHalfDp = floatArrayOf(11f, 7f, 3.8f, 1.7f)
    private val laserAlpha = floatArrayOf(0.16f, 0.34f, 1f, 1f)
    private val laserColor = intArrayOf(0xFF3B30, 0xFF3B30, 0xFF2A1F, 0xFFFFFF)

    private fun laserFill(layer: Int, k: Float) {
        laserPaint.color = (((laserAlpha[layer] * k * 255f).toInt().coerceIn(0, 255)) shl 24) or laserColor[layer]
    }

    private fun drawLaser(c: Canvas) {
        val now = System.currentTimeMillis()
        val k = laser.fade(now)
        if (k <= 0f) { if (!laser.isEmpty) laser.clear(); return }
        val widthK = 0.55f + 0.45f * k
        for (run in laser.runs) {
            val n = run.n
            if (n == 0) continue
            if (lsX.size < n) { val m = max(n, lsX.size * 2); lsX = FloatArray(m); lsY = FloatArray(m); lsNx = FloatArray(m); lsNy = FloatArray(m) }
            for (i in 0 until n) { lsX[i] = run.x[i] * zoom + tx; lsY[i] = run.y[i] * zoom + ty }
            if (n == 1) { for (l in laserHalfDp.indices) { laserFill(l, k); c.drawCircle(lsX[0], lsY[0], laserHalfDp[l] * dp * widthK, laserPaint) }; continue }
            for (i in 0 until n) {
                val a = max(i - 1, 0); val b = min(i + 1, n - 1)
                var dx = lsX[b] - lsX[a]; var dy = lsY[b] - lsY[a]
                val len = hypot(dx, dy)
                if (len < 1e-3f) { dx = 1f; dy = 0f } else { dx /= len; dy /= len }
                lsNx[i] = -dy; lsNy[i] = dx
            }
            for (l in laserHalfDp.indices) {
                laserFill(l, k)
                buildRibbon(n, laserHalfDp[l] * dp * widthK)
                c.drawPath(tmpPath, laserPaint)
            }
        }
        if (laser.down) drawLaserDot(c, laser.runs.last().let { it.x[it.n - 1] * zoom + tx }, laser.runs.last().let { it.y[it.n - 1] * zoom + ty }, 1f)
        val next = laser.nextFrameIn(now)
        if (next == 0L) postInvalidateOnAnimation() else if (next > 0) postInvalidateDelayed(next)
    }

    /** Wstęga wzdłuż śladu: ogon zwęża się, głowa jest okrągła; jedna ścieżka = jedno równomierne nałożenie koloru. */
    private fun buildRibbon(n: Int, half: Float) {
        val p = tmpPath
        p.reset()
        fun w(i: Int) = half * (0.25f + 0.75f * min(1f, i / 14f))
        fun lx(i: Int) = lsX[i] + lsNx[i] * w(i)
        fun ly(i: Int) = lsY[i] + lsNy[i] * w(i)
        fun rx(i: Int) = lsX[i] - lsNx[i] * w(i)
        fun ry(i: Int) = lsY[i] - lsNy[i] * w(i)
        p.moveTo(lx(0), ly(0))
        for (i in 1 until n - 1) p.quadTo(lx(i), ly(i), (lx(i) + lx(i + 1)) / 2, (ly(i) + ly(i + 1)) / 2)
        p.lineTo(lx(n - 1), ly(n - 1))
        p.lineTo(rx(n - 1), ry(n - 1))
        for (i in n - 2 downTo 1) p.quadTo(rx(i), ry(i), (rx(i) + rx(i - 1)) / 2, (ry(i) + ry(i - 1)) / 2)
        p.lineTo(rx(0), ry(0))
        p.close()
        p.addCircle(lsX[n - 1], lsY[n - 1], w(n - 1), Path.Direction.CW)
    }

    private fun drawLaserDot(c: Canvas, x: Float, y: Float, k: Float) {
        val r = floatArrayOf(14f, 9f, 5.5f, 2.6f)
        for (l in r.indices) { laserFill(l, k); c.drawCircle(x, y, r[l] * dp, laserPaint) }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            tx = -scroller.currX.toFloat(); ty = -scroller.currY.toFloat()
            moved()
            postInvalidateOnAnimation()
            if (scroller.isFinished) snapIfNeeded()
        }
    }

    private fun snapIfNeeded() {
        if (ed.layoutMode != 2 || layout.count == 0 || mode == Mode.NAV) return
        val i = layout.nearest(docX(width / 2f), docY(height / 2f))
        if (zoom <= fitPageZoom(i) * 1.08f) {
            val (z, x, y) = targetFor(i)
            animateView(z, x, y, 220L)
        }
    }

    // ───────────────────────── wejście: zdarzenia ─────────────────────────

    private fun isStylus(t: Int) = t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER

    override fun onTouchEvent(raw: MotionEvent): Boolean {
        if (ed.zoomWin) {
            val used = zwTouch(raw)
            if (used != null) return used
            if (zwGesture) {
                // Gest zaczęty w oknie powiększenia: przeliczamy współrzędne na odpowiadające miejsce strony.
                val t = MotionEvent.obtain(raw)
                try { t.transform(zwMatrix()); handle(t) } finally { t.recycle() }
                zwEnd(raw)
                return true
            }
        }
        return handle(raw)
    }

    private fun handle(ev: MotionEvent): Boolean {
        predictor?.record(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(ev)
            MotionEvent.ACTION_POINTER_DOWN -> onPointerDown(ev)
            MotionEvent.ACTION_MOVE -> onMove(ev)
            MotionEvent.ACTION_POINTER_UP -> onPointerUp(ev)
            MotionEvent.ACTION_UP -> onUp(ev, false)
            MotionEvent.ACTION_CANCEL -> onUp(ev, true)
        }
        return true
    }

    override fun onHoverEvent(ev: MotionEvent): Boolean { hover(ev); return true }

    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_EXIT -> { hover(ev); return true }
            MotionEvent.ACTION_SCROLL -> { wheel(ev); return true }
        }
        return super.onGenericMotionEvent(ev)
    }

    private fun hover(ev: MotionEvent) {
        val t = ev.getToolType(0)
        if (ev.actionMasked == MotionEvent.ACTION_HOVER_EXIT) {
            hoverOn = false
            if (isStylus(t)) { stylusNear = false; lastStylusT = ev.eventTime }
        } else {
            hoverOn = t != MotionEvent.TOOL_TYPE_FINGER
            hoverX = ev.x; hoverY = ev.y
            if (isStylus(t)) { stylusNear = true; lastStylusT = ev.eventTime }
            hoverTool = effectiveTool(ev, 0)
        }
        invalidate()
    }

    private fun wheel(ev: MotionEvent) {
        val v = ev.getAxisValue(MotionEvent.AXIS_VSCROLL); val h = ev.getAxisValue(MotionEvent.AXIS_HSCROLL)
        stopAnim()
        when {
            ed.ruler && !ed.readOnly && (ev.metaState and android.view.KeyEvent.META_ALT_ON != 0) -> {
                ruler.a += v * (if (ev.metaState and android.view.KeyEvent.META_SHIFT_ON != 0) 15f else 1f) * (PI / 180).toFloat()
                invalidate(); return
            }
            (ev.metaState and android.view.KeyEvent.META_CTRL_ON != 0) -> zoomAbout(ev.x, ev.y, 1.12f.pow(v))
            (ev.metaState and android.view.KeyEvent.META_SHIFT_ON != 0) -> tx += v * 70f * dp
            else -> { ty += v * 70f * dp; tx -= h * 70f * dp }
        }
        moved()
    }

    /** W trybie tylko do odczytu gest przewija, chyba że narzędziem jest laser (jedyne, które nic nie zmienia). */
    private fun navOnly(ev: MotionEvent, idx: Int) = ed.readOnly && effectiveTool(ev, idx) != Tool.LASER

    /** Identyfikatory dotknięć, które nie są gestem: opierająca się dłoń albo palec poza stroną (np. na pasku narzędzi). */
    private val palm = HashSet<Int>()

    private fun palmTouch(ev: MotionEvent, idx: Int) = ev.getToolType(idx) == MotionEvent.TOOL_TYPE_FINGER && rejectFinger(ev.eventTime)

    // Odrzucony palec, który okazał się szybkim dotknięciem (nie opartą dłonią), nadal może zaznaczyć lub odznaczyć.
    private var rescuePid = -1; private var rescueX = 0f; private var rescueY = 0f; private var rescueT = 0L

    private fun rescueStart(ev: MotionEvent, idx: Int) {
        rescuePid = ev.getPointerId(idx); rescueX = ev.getX(idx); rescueY = ev.getY(idx); rescueT = ev.eventTime
    }

    private fun rescueEnd(ev: MotionEvent, idx: Int) {
        if (rescuePid != ev.getPointerId(idx)) return
        rescuePid = -1
        if (ed.sel == null || ed.readOnly || ev.eventTime - rescueT > 280L) return
        if (hypot(ev.getX(idx) - rescueX, ev.getY(idx) - rescueY) < 10f * dp) tapSelect(rescueX, rescueY)
    }

    private fun outside(ev: MotionEvent, idx: Int) = ev.getX(idx) < 0f || ev.getY(idx) < 0f || ev.getX(idx) > width || ev.getY(idx) > height

    private fun isLive(ev: MotionEvent, i: Int, skip: Int) = i != skip && ev.getPointerId(i) !in palm

    private fun liveCount(ev: MotionEvent, skip: Int = -1): Int {
        var n = 0
        for (i in 0 until ev.pointerCount) if (isLive(ev, i, skip)) n++
        return n
    }

    private fun firstLive(ev: MotionEvent): Int {
        for (i in 0 until ev.pointerCount) if (isLive(ev, i, -1)) return i
        return 0
    }

    private fun rejectFinger(now: Long) = settings.palmRejection && now - lastStylusT < (if (stylusNear) HOVER_STALE_MS else 450L)

    /** Narzędzie dla bieżącego gestu: gumka rysika, przycisk S Pen, albo wybrane w pasku. */
    private fun effectiveTool(ev: MotionEvent, idx: Int): Tool {
        val t = ev.getToolType(idx)
        if (t == MotionEvent.TOOL_TYPE_ERASER) return Tool.ERASER
        if (t == MotionEvent.TOOL_TYPE_STYLUS && ev.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) {
            when (settings.penButton) { 0 -> return Tool.ERASER; 1 -> return Tool.LASSO; 3 -> return Tool.LASER }
        }
        return ed.tool
    }

    private fun onDown(ev: MotionEvent) {
        removeCallbacks(longPress); removeCallbacks(holdRunnable)
        stopAnim()
        val t = ev.getToolType(0)
        palm.clear()
        if (isStylus(t)) { stylusNear = true; lastStylusT = ev.eventTime }
        if (ed.pipette != null) {
            if (t == MotionEvent.TOOL_TYPE_MOUSE && ev.buttonState and MotionEvent.BUTTON_SECONDARY != 0) { ed.cancelPipette(); mode = Mode.IGNORE; return }
            mode = Mode.PICK; pickX = ev.x; pickY = ev.y; pickColor = sampleColor(ev.x, ev.y)
            invalidate(); return
        }
        if (ed.cut != null && cutDown(ev, t)) return
        if (ed.crop != null && cropDown(ev, t)) return
        val rh = if (isStylus(t)) 0 else rulerHit(ev.x, ev.y)
        when {
            t == MotionEvent.TOOL_TYPE_MOUSE -> {
                val bs = ev.buttonState
                when {
                    bs and MotionEvent.BUTTON_SECONDARY != 0 -> { openCtx(ev.x, ev.y); mode = Mode.IGNORE }
                    bs and MotionEvent.BUTTON_TERTIARY != 0 -> startNav(ev)
                    navOnly(ev, 0) -> startNav(ev)
                    rh != 0 -> startRuler(ev, rh)
                    else -> beginTool(ev, 0)
                }
            }
            isStylus(t) -> if (navOnly(ev, 0)) startNav(ev) else beginTool(ev, 0)
            else -> {
                if (palmTouch(ev, 0)) { palm.add(ev.getPointerId(0)); rescueStart(ev, 0); mode = Mode.IGNORE; return }
                if (rh != 0) { startRuler(ev, rh); return }
                fingerDown(ev, 0)
            }
        }
    }

    private fun fingerDown(ev: MotionEvent, idx: Int) {
        when (settings.fingerMode) {
            0 -> if (!ed.readOnly && selGrab(ev.getX(idx), ev.getY(idx))) beginTool(ev, idx, Tool.LASSO) else { startNav(ev); navSelect = true }
            1 -> if (navOnly(ev, idx)) startNav(ev) else beginTool(ev, idx)
            else -> mode = Mode.IGNORE
        }
    }

    private fun onPointerDown(ev: MotionEvent) {
        val idx = ev.actionIndex
        val t = ev.getToolType(idx)
        // Dłoń i palec poza stroną nie biorą udziału w gestach, więc nie psują dotknięcia ani nie blokują paska.
        if (outside(ev, idx)) { palm.add(ev.getPointerId(idx)); return }
        if (palmTouch(ev, idx)) { palm.add(ev.getPointerId(idx)); if (mode != Mode.TOOL) rescueStart(ev, idx); return }
        // Pierwszy palec leży dłużej i nie rusza się (oparta dłoń): krótkie dotknięcie drugim to zwykłe kliknięcie, nie szczypanie.
        if (t == MotionEvent.TOOL_TYPE_FINGER && ev.eventTime - ev.downTime > 350L &&
            (mode == Mode.TOOL || (mode == Mode.NAV && navTravel < 10f * dp))) rescueStart(ev, idx)
        when (mode) {
            Mode.TOOL -> if (toolType == MotionEvent.TOOL_TYPE_FINGER && t == MotionEvent.TOOL_TYPE_FINGER) { cancelTool(); startNav(ev) }
            Mode.NAV -> if (isStylus(t) && !navOnly(ev, idx)) { endNavQuiet(); beginTool(ev, idx) } else { navMax = max(navMax, liveCount(ev)); rebase(ev, -1) }
            Mode.RULER -> if (isStylus(t) && !navOnly(ev, idx)) { mode = Mode.NONE; rulerGrab = 0; beginTool(ev, idx) } else rulerRebase(ev, -1)
            Mode.PICK -> {}
            Mode.CUT -> if (t == MotionEvent.TOOL_TYPE_FINGER && cutPid != -1 && cutType == MotionEvent.TOOL_TYPE_FINGER) { cutAbort(); startNav(ev); removeCallbacks(longPress) }
            Mode.CROP -> if (t == MotionEvent.TOOL_TYPE_FINGER && cropPid != -1 && cropType == MotionEvent.TOOL_TYPE_FINGER) { cropAbort(); startNav(ev); removeCallbacks(longPress) }
            Mode.IGNORE, Mode.NONE -> {
                if (isStylus(t) && !navOnly(ev, idx)) { stylusNear = true; lastStylusT = ev.eventTime; beginTool(ev, idx) }
                else if (t == MotionEvent.TOOL_TYPE_FINGER && !rejectFinger(ev.eventTime)) {
                    // Palec obok opartej dłoni to zwykłe pojedyncze dotknięcie, a nie szczypanie.
                    if (liveCount(ev) >= 2) { if (settings.fingerMode != 1) startNav(ev) } else if (palm.isNotEmpty()) fingerDown(ev, idx)
                }
            }
        }
    }

    private fun onMove(ev: MotionEvent) {
        when (mode) {
            Mode.NAV -> navMove(ev)
            Mode.RULER -> rulerMove(ev)
            Mode.PICK -> { pickX = ev.x; pickY = ev.y; pickColor = sampleColor(ev.x, ev.y); invalidate() }
            Mode.CUT -> cutMove(ev)
            Mode.CROP -> cropMove(ev)
            Mode.TOOL -> { val idx = ev.findPointerIndex(toolPid); if (idx >= 0) toolMove(ev, idx) }
            else -> {}
        }
    }

    private fun onPointerUp(ev: MotionEvent) {
        val idx = ev.actionIndex
        when (mode) {
            Mode.TOOL -> if (ev.getPointerId(idx) == toolPid) { finishTool(ev, idx, false); mode = Mode.IGNORE }
            Mode.NAV -> if (ev.getPointerId(idx) in palm) {} else if (liveCount(ev, idx) == 0) { navUp(ev, false); mode = Mode.IGNORE } else rebase(ev, idx)
            Mode.RULER -> rulerRebase(ev, idx)
            else -> {}
        }
        rescueEnd(ev, idx)
        palm.remove(ev.getPointerId(idx))
    }

    private fun onUp(ev: MotionEvent, cancel: Boolean) {
        if (!cancel && mode == Mode.IGNORE) rescueEnd(ev, 0) else rescuePid = -1
        when (mode) {
            Mode.TOOL -> finishTool(ev, ev.findPointerIndex(toolPid).coerceAtLeast(0), cancel)
            Mode.NAV -> navUp(ev, cancel)
            Mode.PICK -> if (!cancel) pickDone()
            Mode.CUT -> cutUp(ev, cancel)
            Mode.CROP -> cropUp(ev, cancel)
            else -> {}
        }
        if (isStylus(ev.getToolType(0))) lastStylusT = ev.eventTime
        mode = Mode.NONE; toolPid = -1; rulerGrab = 0; palm.clear()
        predictor?.let { predN = 0 }
        invalidate()
    }

    // ───────────────────────── nawigacja (palec / mysz) ─────────────────────────

    private fun startNav(ev: MotionEvent) {
        mode = Mode.NAV; navSelect = false
        stopAnim()
        vt?.recycle(); vt = VelocityTracker.obtain().also { it.addMovement(ev) }
        navMax = max(1, liveCount(ev)); navPid = ev.getPointerId(firstLive(ev)); navTravel = 0f; navT0 = ev.eventTime
        pullAnim?.cancel(); pull = 0f; pullArmed = false
        pullEligible = navMax == 1 && pullEnabled() && atDocEnd()
        rebase(ev, -1)
        removeCallbacks(longPress)
        if (ev.getToolType(0) != MotionEvent.TOOL_TYPE_MOUSE || true) postDelayed(longPress, 520L)
    }

    private fun endNavQuiet() { removeCallbacks(longPress); vt?.recycle(); vt = null; mode = Mode.NONE }

    private fun rebase(ev: MotionEvent, skip: Int) {
        var sx = 0f; var sy = 0f; var n = 0
        for (i in 0 until ev.pointerCount) { if (!isLive(ev, i, skip)) continue; sx += ev.getX(i); sy += ev.getY(i); n++ }
        if (n == 0) return
        lastCx = sx / n; lastCy = sy / n
        lastSpan = 0f
        if (n >= 2) {
            var d = 0f
            for (i in 0 until ev.pointerCount) { if (!isLive(ev, i, skip)) continue; d += hypot(ev.getX(i) - lastCx, ev.getY(i) - lastCy) }
            lastSpan = d / n
        }
    }

    private fun navMove(ev: MotionEvent) {
        vt?.addMovement(ev)
        var sx = 0f; var sy = 0f; val n = liveCount(ev)
        if (n == 0) return
        for (i in 0 until ev.pointerCount) if (isLive(ev, i, -1)) { sx += ev.getX(i); sy += ev.getY(i) }
        val cx = sx / n; val cy = sy / n
        var span = 0f
        if (n >= 2) { for (i in 0 until ev.pointerCount) if (isLive(ev, i, -1)) span += hypot(ev.getX(i) - cx, ev.getY(i) - cy); span /= n }
        if (n >= 2 && lastSpan > 0f && span > 0f) zoomAbout(cx, cy, span / lastSpan)
        if (n >= 2 && pull > 0f) endPull(false)
        panWithPull(cx - lastCx, cy - lastCy, pullEligible && n == 1 && navMax == 1)
        navTravel += hypot(cx - lastCx, cy - lastCy) + abs(span - lastSpan)
        lastCx = cx; lastCy = cy; lastSpan = span
        moved()
    }

    private fun navUp(ev: MotionEvent, cancel: Boolean) {
        removeCallbacks(longPress)
        if (pull > 0f) {
            endPull(!cancel && pull >= pullThreshold())
            vt?.recycle(); vt = null
            return
        }
        val tracker = vt
        if (!cancel) {
            val dur = ev.eventTime - navT0
            if (navTravel < 10f * dp && dur < 280L) {
                when (navMax) {
                    1 -> {
                        val now = ev.eventTime
                        if (tapCount in 1..2 && now - lastTapT < TAP_WINDOW && hypot(lastCx - lastTapX, lastCy - lastTapY) < 48f * dp) {
                            lastTapT = now
                            val x = lastCx; val y = lastCy
                            if (++tapCount == 3) { cancelPendingDouble(); tapCount = 0; tapAction(settings.tripleTap, x, y) }
                            else if (settings.tripleTap == TAP_NONE) { tapCount = 0; tapAction(settings.doubleTap, x, y) }
                            else {
                                val r = Runnable { pendingDouble = null; tapCount = 0; tapAction(settings.doubleTap, x, y) }
                                pendingDouble = r
                                postDelayed(r, TAP_WINDOW)
                            }
                        } else { tapCount = 1; lastTapT = now; lastTapX = lastCx; lastTapY = lastCy; if (navSelect) tapSelect(lastCx, lastCy) }
                    }
                    2 -> if (settings.twoFingerUndo) ed.undo()
                    3 -> if (settings.twoFingerUndo) ed.redo()
                }
            } else if (navMax == 1 && tracker != null) {
                tracker.addMovement(ev)
                tracker.computeCurrentVelocity(1000, cfg.scaledMaximumFlingVelocity.toFloat())
                val vx = tracker.getXVelocity(navPid); val vy = tracker.getYVelocity(navPid)
                if (hypot(vx, vy) > cfg.scaledMinimumFlingVelocity) {
                    val cw = layout.docWidth * zoom; val ch = layout.docHeight * zoom
                    val minX = if (cw <= width) ((cw - width) / 2).toInt() else 0
                    val maxX = if (cw <= width) minX else (cw - width).toInt()
                    val minY = if (ch <= height) ((ch - height) / 2).toInt() else 0
                    val maxY = if (ch <= height) minY else (ch - height).toInt()
                    scroller.fling((-tx).toInt(), (-ty).toInt(), (-vx).toInt(), (-vy).toInt(), minX, maxX, minY, maxY)
                    postInvalidateOnAnimation()
                } else snapIfNeeded()
            } else snapIfNeeded()
        }
        tracker?.recycle(); vt = null
    }

    // ───────────────────────── odciągnięcie za ostatnią stroną → nowa strona ─────────────────────────

    /** Surowe odciągnięcie (px) poza koniec dokumentu; rysowane z oporem (`pullOffset`). */
    private var pull = 0f
    private var pullArmed = false
    private var pullEligible = false
    private var pullAnim: ValueAnimator? = null
    private val pullFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pullRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pullText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }

    private fun pullEnabled() = settings.pullAddPage && !ed.readOnly && layout.count > 0
    private fun pullThreshold() = 120f * dp
    private fun pullLimit(): Float =
        if (ed.layoutMode == 1) clampX(Float.NEGATIVE_INFINITY, zoom) else clampY(Float.NEGATIVE_INFINITY, zoom)

    /** Czy widok stoi na końcu dokumentu (dół albo prawa krawędź w układzie poziomym). */
    private fun atDocEnd(): Boolean {
        val cur = if (ed.layoutMode == 1) tx else ty
        return cur <= pullLimit() + 4f * dp
    }

    private fun pullOffset(): Float {
        if (pull <= 0f) return 0f
        val cap = 100f * dp
        return cap * (1f - exp(-pull / (cap * 1.3f)))
    }

    /** Przesuwa widok o (dx, dy); nadmiar poza koniec dokumentu zamienia w odciągnięcie. */
    private fun panWithPull(dx: Float, dy: Float, pullOk: Boolean) {
        val ax = ed.layoutMode == 1
        var d = if (ax) dx else dy
        val other = if (ax) dy else dx
        if (pullOk && pull > 0f) {
            if (d > 0f) { val r = min(pull, d); pull -= r; d -= r } else { pull -= d; d = 0f }
        }
        if (ax) { tx += d; ty += other } else { ty += d; tx += other }
        if (pullOk) {
            val lim = pullLimit()
            val cur = if (ax) tx else ty
            if (cur < lim) { pull += lim - cur; if (ax) tx = lim else ty = lim }
            val armed = pull >= pullThreshold()
            if (armed != pullArmed) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            pullArmed = armed
        }
    }

    /** Puszczenie: dodaje stronę (jeśli [add]) i wraca z odciągnięcia animacją. */
    private fun endPull(add: Boolean) {
        pullEligible = false; pullArmed = false
        val from = pull
        pullAnim?.cancel()
        if (from > 0f) {
            pullAnim = ValueAnimator.ofFloat(from, 0f).apply {
                duration = 220L; interpolator = DecelerateInterpolator()
                addUpdateListener { pull = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        if (add && !ed.readOnly) {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            ed.addPage(after = doc.pages.size - 1)
        }
    }

    private fun drawPullHint(c: Canvas, off: Float) {
        val prog = (pull / pullThreshold()).coerceIn(0f, 1f)
        val a = (off / (28f * dp)).coerceIn(0f, 1f)
        val armed = pull >= pullThreshold()
        val r = 13f * dp
        val ink = if (darkUi) 0xFFE8EAED.toInt() else 0xFF3C4043.toInt()
        val ax = ed.layoutMode == 1
        val label = if (armed) T.t("Puść, aby dodać stronę", "Release to add a page") else T.t("Pociągnij, aby dodać stronę", "Pull to add a page")
        pullText.textSize = 13.5f * dp
        val tw = if (ax) 0f else pullText.measureText(label)
        val gap = if (ax) 0f else 10f * dp
        val total = 2 * r + gap + tw
        val cx: Float; val cy: Float
        if (ax) { cx = width - off / 2f; cy = height / 2f }
        else { cx = (width - total) / 2f + r; cy = height - off / 2f }
        pullFill.color = if (armed) accent else rgbA(accent, 0x30)
        pullFill.alpha = (pullFill.alpha * a).toInt()
        c.drawCircle(cx, cy, r, pullFill)
        pullRing.strokeWidth = 2.2f * dp
        pullRing.color = accent; pullRing.alpha = (255 * a).toInt()
        if (!armed) c.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, 360f * prog, false, pullRing)
        val arm = r * 0.46f
        pullRing.color = if (armed) 0xFF1B1D21.toInt() else ink; pullRing.alpha = (255 * a).toInt()
        pullRing.strokeWidth = 2f * dp
        c.drawLine(cx - arm, cy, cx + arm, cy, pullRing)
        c.drawLine(cx, cy - arm, cx, cy + arm, pullRing)
        if (!ax) {
            pullText.color = ink; pullText.alpha = (255 * a * (0.55f + 0.45f * prog)).toInt()
            c.drawText(label, cx + r + gap, cy - (pullText.ascent() + pullText.descent()) / 2f, pullText)
        }
    }

    // ───────────────────────── linijka ─────────────────────────

    /** Wywoływane po włączeniu linijki: ustawia ją na środku widoku, jeśli jeszcze nie była lub zniknęła z ekranu. */
    fun rulerShown() {
        val sx = ruler.cx * zoom + tx; val sy = ruler.cy * zoom + ty
        if (!rulerPlaced || sx < 0f || sy < 0f || sx > width || sy > height) {
            val pw = doc.pages.firstOrNull()?.spec?.w ?: 595f
            ruler.len = (pw * 0.85f).coerceIn(240f, 560f)
            ruler.cx = docX(width / 2f); ruler.cy = docY(height / 2f); ruler.a = 0f
            rulerPlaced = true
        }
        invalidate()
    }

    private fun rulerHandleOff() = min(17f * dp, ruler.len * zoom * 0.25f) / zoom

    /** 0 poza linijką, 1 korpus (przesuwanie), ±2 uchwyt obrotu na końcu u = ±len/2. */
    private fun rulerHit(sx: Float, sy: Float): Int {
        if (!ed.ruler || ed.readOnly) return 0
        val x = docX(sx); val y = docY(sy)
        val u = ruler.u(x, y); val v = ruler.v(x, y)
        val r = 24f * dp / zoom
        for (s in intArrayOf(1, -1)) if (hypot(u - s * (ruler.len / 2 - rulerHandleOff()), v) < r) return 2 * s
        return if (ruler.inBody(x, y, 8f * dp / zoom)) 1 else 0
    }

    private fun startRuler(ev: MotionEvent, hit: Int) {
        mode = Mode.RULER
        rulerGrab = if (hit == 1) 1 else 2
        rulerHandle = if (hit < 0) -1 else 1
        rLastX = ev.x; rLastY = ev.y; rulerSnapped = false
        invalidate()
    }

    /** Po zmianie liczby palców: dwa palce obracają i przesuwają, jeden tylko przesuwa. */
    private fun rulerRebase(ev: MotionEvent, skip: Int) {
        val ids = (0 until ev.pointerCount).filter { it != skip }
        if (ids.size >= 2) {
            val a = ids[0]; val b = ids[1]
            rulerGrab = 3
            rLastX = (ev.getX(a) + ev.getX(b)) / 2; rLastY = (ev.getY(a) + ev.getY(b)) / 2
            rLastAng = atan2(ev.getY(b) - ev.getY(a), ev.getX(b) - ev.getX(a))
        } else if (ids.size == 1) {
            rulerGrab = 1; rLastX = ev.getX(ids[0]); rLastY = ev.getY(ids[0])
        }
    }

    private fun rulerMove(ev: MotionEvent) {
        when {
            ev.pointerCount >= 2 -> {
                val cx = (ev.getX(0) + ev.getX(1)) / 2; val cy = (ev.getY(0) + ev.getY(1)) / 2
                val ang = atan2(ev.getY(1) - ev.getY(0), ev.getX(1) - ev.getX(0))
                if (rulerGrab == 3) {
                    var d = ang - rLastAng
                    if (d > PI) d -= (2 * PI).toFloat() else if (d < -PI) d += (2 * PI).toFloat()
                    ruler.a += d
                    ruler.cx += (cx - rLastX) / zoom; ruler.cy += (cy - rLastY) / zoom
                }
                rulerGrab = 3; rLastX = cx; rLastY = cy; rLastAng = ang
            }
            rulerGrab == 1 -> {
                ruler.cx += (ev.x - rLastX) / zoom; ruler.cy += (ev.y - rLastY) / zoom
                rLastX = ev.x; rLastY = ev.y
            }
            rulerGrab == 2 -> {
                val a = atan2(docY(ev.y) - ruler.cy, docX(ev.x) - ruler.cx) - (if (rulerHandle > 0) 0f else PI.toFloat())
                val s = Ruler.snap(a, 2.2f * (PI / 180).toFloat())
                val on = s != a
                if (on && !rulerSnapped) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                rulerSnapped = on
                ruler.a = s
            }
        }
        invalidate()
    }

    private fun rgbA(c: Int, a: Int) = (c and 0xFFFFFF) or (a shl 24)

    private fun drawRuler(c: Canvas) {
        if (!ed.ruler || ed.readOnly) return
        if (!rulerPlaced) rulerShown()
        val z = zoom
        val len = ruler.len * z; val th = ruler.thick * z
        val scx = ruler.cx * z + tx; val scy = ruler.cy * z + ty
        val ink = if (darkUi) 0xE6FFFFFF.toInt() else 0xE61F2933.toInt()
        c.save()
        c.translate(scx, scy)
        c.rotate(Math.toDegrees(ruler.a.toDouble()).toFloat())
        val rad = min(10f * dp, th * 0.3f)
        rulerFill.color = rgbA(accent, if (darkUi) 0x55 else 0x3D)
        rulerEdge.color = rgbA(accent, 0xD0)
        c.drawRoundRect(-len / 2, -th / 2, len / 2, th / 2, rad, rad, rulerFill)
        c.drawRoundRect(-len / 2, -th / 2, len / 2, th / 2, rad, rad, rulerEdge)

        // podziałka na górnej krawędzi: mm/cm albo 1/8″ i cale
        val mm = settings.unitsMm
        val unit = if (mm) 72f / 25.4f else 9f
        val perMajor = if (mm) 10 else 8
        val perMid = perMajor / 2
        val step = unit * z
        val minPx = 3.5f * dp
        val stride = if (step >= minPx) 1 else if (step * perMid >= minPx) perMid else perMajor
        val n = (ruler.len / unit).toInt()
        rulerTick.color = rgbA(ink, 0xB0)
        rulerText.color = rgbA(ink, 0xCC)
        val top = -th / 2
        val showLabels = th > 34f * dp
        val ls = ceil(26f * dp / (perMajor * step)).toInt().coerceAtLeast(1)
        tmpPath.rewind()
        var i = 0
        while (i <= n) {
            val u = -len / 2 + i * step
            val tl = if (i % perMajor == 0) 12f * dp else if (i % perMid == 0) 8f * dp else 5f * dp
            tmpPath.moveTo(u, top); tmpPath.lineTo(u, top + min(tl, th * 0.4f))
            if (showLabels && i % perMajor == 0 && (i / perMajor) % ls == 0) c.drawText((i / perMajor).toString(), u, top + 23f * dp, rulerText)
            i += stride
        }
        c.drawPath(tmpPath, rulerTick)
        if (showLabels) c.drawText(if (mm) "cm" else "in", len / 2 - rulerHandleOff() * z - 24f * dp, th / 2 - 8f * dp, rulerText)

        // uchwyty obrotu
        handleStroke.color = accent
        val ho = rulerHandleOff() * z
        for (s in intArrayOf(1, -1)) {
            c.drawCircle(s * (len / 2 - ho), 0f, 8f * dp, handleFill)
            c.drawCircle(s * (len / 2 - ho), 0f, 8f * dp, handleStroke)
        }
        c.restore()

        // etykieta: długość kreski (pióro przyklejone) albo kąt (obracanie)
        val label = when {
            liveActive && rulerSide != 0 -> {
                val d = abs(rulerU1 - rulerU0)
                if (mm) "%.1f cm".format(d / unit / 10f) else "%.2f in".format(d / 72f)
            }
            rulerGrab != 0 -> "%.1f°".format((((Math.toDegrees(ruler.a.toDouble()) % 180) + 180) % 180))
            else -> null
        }
        if (label != null) {
            rulerText.color = 0xFFFFFFFF.toInt()
            val w = rulerText.measureText(label) + 18f * dp
            val h = 22f * dp
            rulerFill.color = rgbA(0xFF1F2933.toInt(), 0xE0)
            c.drawRoundRect(scx - w / 2, scy - h / 2, scx + w / 2, scy + h / 2, h / 2, h / 2, rulerFill)
            c.drawText(label, scx, scy + 3.4f * dp, rulerText)
        }
    }

    // ───────────────────────── pipeta ─────────────────────────

    /** Kolor piksela strony pod punktem ekranu (z bitmapy w pamięci podręcznej); 0, gdy poza stroną lub strona jeszcze się nie narysowała. */
    private fun sampleColor(sx: Float, sy: Float): Int {
        val pi = pageIdxAt(sx, sy)
        if (pi < 0) return 0
        val page = doc.pages[pi]; val spec = page.spec
        val col = cache.pixel(page, pgX(pi, sx), pgY(pi, sy))
        if (col == 0) return 0
        if (settings.pipetteOriginal) originalColorAt(page, pgX(pi, sx), pgY(pi, sy))?.let { return it }
        return col or 0xFF000000.toInt()
    }

    /** Pierwotny kolor kreski albo wypełnienia pod punktem strony (zakreślacz ma pierwszeństwo przed pismem, bez rozjaśnienia przez przezroczystość); null, gdy nic tam nie ma. */
    private fun originalColorAt(page: PageState, px: Float, py: Float): Int? =
        PipetteMath.originalColor(doc.content(page).elements, px, py, 2f * dp / zoom)

    private fun pickDone() {
        val col = sampleColor(pickX, pickY)
        if (col == 0) return
        val cb = ed.pipette ?: return
        ed.pipette = null
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        cb(col)
        invalidate()
    }

    private fun drawPicker(c: Canvas) {
        if (ed.pipette == null) return
        val ink = if (darkUi) 0xFFFFFFFF.toInt() else 0xFF1F2933.toInt()
        if (mode != Mode.PICK) {
            val msg = T.t("Pipeta: dotknij koloru na stronie  ·  Esc — anuluj", "Eyedropper: tap a color on the page  ·  Esc to cancel")
            rulerText.color = 0xFFFFFFFF.toInt()
            val w = rulerText.measureText(msg) + 24f * dp; val h = 26f * dp
            rulerFill.color = rgbA(0xFF1F2933.toInt(), 0xE6)
            val cx = width / 2f; val top = 14f * dp
            c.drawRoundRect(cx - w / 2, top, cx + w / 2, top + h, h / 2, h / 2, rulerFill)
            c.drawText(msg, cx, top + h / 2 + 3.4f * dp, rulerText)
            return
        }
        // celownik w punkcie dotyku + lupa z kolorem nad nim
        pickRing.strokeWidth = 2f * dp
        pickRing.color = 0xFFFFFFFF.toInt(); c.drawCircle(pickX, pickY, 8f * dp, pickRing)
        pickRing.strokeWidth = 1f * dp
        pickRing.color = rgbA(ink, 0xCC); c.drawCircle(pickX, pickY, 9.5f * dp, pickRing)
        val r = 30f * dp
        val lx = pickX.coerceIn(r + 6f * dp, width - r - 6f * dp)
        val ly = if (pickY - 74f * dp > r) pickY - 74f * dp else pickY + 74f * dp
        pickFill.color = if (pickColor != 0) pickColor else rgbA(ink, 0x22)
        c.drawCircle(lx, ly, r, pickFill)
        pickRing.strokeWidth = 4f * dp; pickRing.color = 0xFFFFFFFF.toInt(); c.drawCircle(lx, ly, r, pickRing)
        pickRing.strokeWidth = 1.2f * dp; pickRing.color = rgbA(ink, 0xAA); c.drawCircle(lx, ly, r + 2f * dp, pickRing)
        val label = if (pickColor != 0) "#%06X".format(pickColor and 0xFFFFFF) else T.t("poza stroną", "off page")
        rulerText.color = 0xFFFFFFFF.toInt()
        val w = rulerText.measureText(label) + 16f * dp; val h = 20f * dp
        rulerFill.color = rgbA(0xFF1F2933.toInt(), 0xE6)
        c.drawRoundRect(lx - w / 2, ly + r + 6f * dp, lx + w / 2, ly + r + 6f * dp + h, h / 2, h / 2, rulerFill)
        c.drawText(label, lx, ly + r + 6f * dp + h / 2 + 3.4f * dp, rulerText)
    }

    // ───────────────────────── przecinanie obrazu ─────────────────────────

    private var cutPid = -1; private var cutType = 0
    private var cutGrab = -1                      // indeks przeciąganej linii albo -1, gdy rysujemy nową
    private var cutU0 = 0f; private var cutV0 = 0f; private var cutU1 = 0f; private var cutV1 = 0f
    private var cutX0 = 0f; private var cutY0 = 0f; private var cutX1 = 0f; private var cutY1 = 0f
    private var cutSnapped = false

    private fun cutLocal(img: com.mdev.mnotes.core.model.ImageEl, pi: Int, sx: Float, sy: Float) = ImageCut.toLocal(img, pgX(pi, sx), pgY(pi, sy))

    /** Czy ten dotyk należy do cięcia (na obrazie albo przy linii); nie: palec poza obrazem przewija, rysik nic nie robi. */
    private fun cutDown(ev: MotionEvent, t: Int): Boolean {
        val cut = ed.cut ?: return false
        val img = ed.cutImage() ?: return false
        val pi = doc.indexOf(cut.pageId); if (pi < 0) return false
        if (t == MotionEvent.TOOL_TYPE_MOUSE && ev.buttonState and MotionEvent.BUTTON_SECONDARY != 0) { ed.cancelCut(); mode = Mode.IGNORE; return true }
        if (t == MotionEvent.TOOL_TYPE_MOUSE && (ev.buttonState and MotionEvent.BUTTON_TERTIARY != 0 || navOnly(ev, 0))) return false
        if (palmTouch(ev, 0)) return false
        val uv = cutLocal(img, pi, ev.x, ev.y)
        val tolU = 16f * dp / (img.w * zoom); val tolV = 16f * dp / (img.h * zoom)
        var grab = -1; var best = Float.MAX_VALUE
        for ((i, l) in cut.lines.withIndex()) {
            val along = if (l.vertical) uv[1] else uv[0]
            if (along < -0.08f || along > 1.08f) continue
            val d = if (l.vertical) abs(uv[0] - l.pos) / tolU else abs(uv[1] - l.pos) / tolV
            if (d <= 1f && d < best) { best = d; grab = i }
        }
        val onImage = uv[0] in 0f..1f && uv[1] in 0f..1f
        if (grab < 0 && !onImage) {
            if (t == MotionEvent.TOOL_TYPE_FINGER || t == MotionEvent.TOOL_TYPE_MOUSE) return false
            mode = Mode.IGNORE; return true
        }
        mode = Mode.CUT; cutPid = ev.getPointerId(0); cutType = t; cutGrab = grab; cutSnapped = false
        cutX0 = ev.x; cutY0 = ev.y; cutX1 = ev.x; cutY1 = ev.y
        cutU0 = uv[0]; cutV0 = uv[1]; cutU1 = uv[0]; cutV1 = uv[1]
        invalidate(); return true
    }

    private fun cutAbort() { cutPid = -1; cutGrab = -1; mode = Mode.NONE; invalidate() }

    private fun cutMove(ev: MotionEvent) {
        val cut = ed.cut ?: return
        val img = ed.cutImage() ?: return
        val pi = doc.indexOf(cut.pageId); if (pi < 0) return
        val i = ev.findPointerIndex(cutPid); if (i < 0) return
        cutX1 = ev.getX(i); cutY1 = ev.getY(i)
        val uv = cutLocal(img, pi, cutX1, cutY1)
        cutU1 = uv[0]; cutV1 = uv[1]
        if (cutGrab in cut.lines.indices) {
            val l = cut.lines[cutGrab]
            val size = if (l.vertical) img.w else img.h
            val raw = if (l.vertical) uv[0] else uv[1]
            val min = ImageCut.MIN_PIECE_PT / size
            var lo = min; var hi = 1f - min
            for ((k, o) in cut.lines.withIndex()) {
                if (k == cutGrab || o.vertical != l.vertical) continue
                if (o.pos < l.pos) lo = max(lo, o.pos + min) else hi = min(hi, o.pos - min)
            }
            if (lo <= hi) {
                val sn = ImageCut.snap(raw, size, 8f * dp / zoom)
                val v = sn.coerceIn(lo, hi)
                val snappedNow = sn != raw && v == sn
                if (snappedNow && !cutSnapped) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                cutSnapped = snappedNow
                if (v != l.pos) cut.lines[cutGrab] = CutLine(l.vertical, v)
            }
        } else {
            val cand = cutNewLine(img)
            val snappedNow = cand != null && cand.pos != cutRaw
            if (snappedNow && !cutSnapped) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            cutSnapped = snappedNow
        }
        invalidate()
    }

    private var cutRaw = 0f

    /** Linia, jaką da obecny pociągnięcie: orientacja z przeważającego kierunku ruchu, pozycja ze środka pociągnięcia (z przyciąganiem). */
    private fun cutNewLine(img: com.mdev.mnotes.core.model.ImageEl): CutLine? {
        val dxp = (cutU1 - cutU0) * img.w * zoom; val dyp = (cutV1 - cutV0) * img.h * zoom
        if (hypot(dxp, dyp) < 18f * dp) return null
        val vertical = abs(dyp) >= abs(dxp)
        val raw = if (vertical) (cutU0 + cutU1) / 2f else (cutV0 + cutV1) / 2f
        cutRaw = raw
        val size = if (vertical) img.w else img.h
        return CutLine(vertical, ImageCut.snap(raw, size, 8f * dp / zoom))
    }

    private fun cutUp(ev: MotionEvent, cancel: Boolean) {
        val cut = ed.cut; val img = ed.cutImage()
        val grab = cutGrab; val pid = cutPid
        cutPid = -1; cutGrab = -1
        if (cancel || cut == null || img == null || pid == -1) { invalidate(); return }
        if (grab in cut.lines.indices) {
            // Wyciągnięcie linii poza obraz usuwa ją (jak zdjęcie prowadnicy z linijki).
            val outside = cutU1 < -0.02f || cutU1 > 1.02f || cutV1 < -0.02f || cutV1 > 1.02f
            if (outside) { cut.lines.removeAt(grab); performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            invalidate(); return
        }
        val l = cutNewLine(img)
        if (l == null) { invalidate(); return }
        val size = if (l.vertical) img.w else img.h
        val near = cut.lines.any { it.vertical == l.vertical && abs(it.pos - l.pos) * size < ImageCut.MIN_PIECE_PT }
        if (!ImageCut.fits(l.pos, size) || near) { ed.say(T.t("Za blisko krawędzi albo innej linii", "Too close to an edge or another line")); invalidate(); return }
        ed.addCutLine(l.vertical, l.pos)
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    private val cutCell = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cutStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val cutPath = Path()

    private fun drawCut(c: Canvas) {
        val cut = ed.cut ?: return
        val img = ed.cutImage() ?: return
        val pi = doc.indexOf(cut.pageId); if (pi < 0) return
        fun sp(u: Float, v: Float): FloatArray { val p = ImageCut.toPage(img, u, v); return floatArrayOf(scrX(pi, p[0]), scrY(pi, p[1])) }
        fun seg(u0: Float, v0: Float, u1: Float, v1: Float, paint: Paint) { val a = sp(u0, v0); val b = sp(u1, v1); c.drawLine(a[0], a[1], b[0], b[1], paint) }
        // obrys obrazu
        val tl = sp(0f, 0f); val tr = sp(1f, 0f); val br = sp(1f, 1f); val bl = sp(0f, 1f)
        cutPath.reset(); cutPath.moveTo(tl[0], tl[1]); cutPath.lineTo(tr[0], tr[1]); cutPath.lineTo(br[0], br[1]); cutPath.lineTo(bl[0], bl[1]); cutPath.close()
        cutCell.color = rgbA(accent, 0x14); c.drawPath(cutPath, cutCell)
        cutStroke.pathEffect = null; cutStroke.strokeWidth = 1.5f * dp; cutStroke.color = rgbA(accent, 0xCC); c.drawPath(cutPath, cutStroke)
        val dash = DashPathEffect(floatArrayOf(9f * dp, 6f * dp), 0f)
        fun line(l: CutLine, active: Boolean, preview: Boolean) {
            val vExt = 12f * dp / (img.h * zoom); val uExt = 12f * dp / (img.w * zoom)
            val (u0, v0, u1, v1) = if (l.vertical) floatArrayOf(l.pos, -vExt, l.pos, 1f + vExt) else floatArrayOf(-uExt, l.pos, 1f + uExt, l.pos)
            cutStroke.pathEffect = null; cutStroke.strokeWidth = (if (active) 5f else 4f) * dp; cutStroke.color = 0xE6FFFFFF.toInt()
            seg(u0, v0, u1, v1, cutStroke)
            cutStroke.pathEffect = if (preview) dash else null
            cutStroke.strokeWidth = (if (active) 3f else 2.2f) * dp; cutStroke.color = if (active) accent else 0xFF1F2933.toInt()
            seg(u0, v0, u1, v1, cutStroke)
            if (!preview) {
                cutStroke.pathEffect = null
                for ((u, v) in listOf(u0 to v0, u1 to v1)) {
                    val p = sp(u, v); cutCell.color = 0xFFFFFFFF.toInt(); c.drawCircle(p[0], p[1], 6f * dp, cutCell)
                    cutCell.color = if (active) accent else 0xFF1F2933.toInt(); c.drawCircle(p[0], p[1], 4f * dp, cutCell)
                }
            }
        }
        for ((i, l) in cut.lines.withIndex()) line(l, i == cutGrab && mode == Mode.CUT, false)
        if (mode == Mode.CUT && cutGrab < 0) cutNewLine(img)?.let { line(it, true, true) }
    }

    // ───────────────────────── przycinanie obrazu ─────────────────────────

    private var cropPid = -1; private var cropType = 0
    private var cropGrab: CropHandle? = null
    private var cropMoving = false
    private var cropBase: CropFrame? = null
    private var cropDownFx = 0f; private var cropDownFy = 0f      // punkt pliku pod palcem na początku gestu
    private var cropOffFx = 0f; private var cropOffFy = 0f        // różnica między uchwytem a palcem (bez skoku przy chwyceniu)

    /** Punkt ekranu → punkt pliku (piksele) według położenia pliku w sesji. */
    private fun cropFile(s: com.mdev.mnotes.editor.CropSession, pi: Int, sx: Float, sy: Float) = s.place.toFile(pgX(pi, sx), pgY(pi, sy), s.iw, s.ih)

    /** Uchwyt pod punktem w osiach ramki (ekranowe piksele względem jej środka) albo null; poza ramką i uchwytami: null. */
    private fun cropHit(s: com.mdev.mnotes.editor.CropSession, u: Float, v: Float): CropHandle? {
        val hw = s.frame.hw * s.place.s * zoom; val hh = s.frame.hh * s.place.s * zoom
        val corner = 22f * dp
        val band = min(18f * dp, min(hw, hh) * 0.34f)
        var best: CropHandle? = null; var bestD = Float.MAX_VALUE
        for (h in CropHandle.values()) {
            if (h.sx != 0 && h.sy != 0) {
                val d = hypot(u - h.sx * hw, v - h.sy * hh)
                if (d <= corner && d < bestD) { best = h; bestD = d }
            }
        }
        if (best != null) return best
        // krawędzie: pasmo wokół boku, od narożnika do narożnika (z tolerancją na zewnątrz)
        for (h in CropHandle.values()) {
            if (h.sx != 0 && h.sy != 0) continue
            val d: Float; val along: Float; val lim: Float
            if (h.sx != 0) { d = abs(u - h.sx * hw); along = abs(v); lim = hh } else { d = abs(v - h.sy * hh); along = abs(u); lim = hw }
            if (d <= band && along <= lim + band * 0.5f && d < bestD) { best = h; bestD = d }
        }
        return best
    }

    private fun cropLocal(s: com.mdev.mnotes.editor.CropSession, pi: Int, sx: Float, sy: Float): FloatArray {
        val c = s.place.toPage(s.frame.cx, s.frame.cy, s.iw, s.ih)
        val dx = pgX(pi, sx) - c[0]; val dy = pgY(pi, sy) - c[1]
        val a = s.frame.a + s.place.phi
        val co = cos(a); val si = sin(a)
        return floatArrayOf((dx * co + dy * si) * zoom, (-dx * si + dy * co) * zoom)
    }

    private fun cropDown(ev: MotionEvent, t: Int): Boolean {
        val s = ed.crop ?: return false
        if (ed.cropImage() == null) return false
        val pi = doc.indexOf(s.pageId); if (pi < 0) return false
        if (t == MotionEvent.TOOL_TYPE_MOUSE && ev.buttonState and MotionEvent.BUTTON_SECONDARY != 0) { ed.cancelCrop(); mode = Mode.IGNORE; return true }
        if (t == MotionEvent.TOOL_TYPE_MOUSE && (ev.buttonState and MotionEvent.BUTTON_TERTIARY != 0 || navOnly(ev, 0))) return false
        if (palmTouch(ev, 0)) return false
        val uv = cropLocal(s, pi, ev.x, ev.y)
        val h = cropHit(s, uv[0], uv[1])
        val inside = abs(uv[0]) <= s.frame.hw * s.place.s * zoom && abs(uv[1]) <= s.frame.hh * s.place.s * zoom
        if (h == null && !inside) {
            if (t == MotionEvent.TOOL_TYPE_FINGER || t == MotionEvent.TOOL_TYPE_MOUSE) return false
            mode = Mode.IGNORE; return true
        }
        val p = cropFile(s, pi, ev.x, ev.y)
        cropDownFx = p[0]; cropDownFy = p[1]
        cropBase = s.frame
        cropGrab = h; cropMoving = h == null
        if (h != null) {
            // punkt uchwytu w pliku (dla krawędzi liczy się tylko jedna współrzędna ramki, więc środek boku wystarcza)
            val f = s.frame
            val co = cos(f.a); val si = sin(f.a)
            val u = h.sx * f.hw; val v = h.sy * f.hh
            cropOffFx = f.cx + u * co - v * si - p[0]; cropOffFy = f.cy + u * si + v * co - p[1]
        }
        mode = Mode.CROP; cropPid = ev.getPointerId(0); cropType = t
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        invalidate(); return true
    }

    private fun cropAbort() { cropPid = -1; cropGrab = null; cropMoving = false; mode = Mode.NONE; invalidate() }

    private fun cropMove(ev: MotionEvent) {
        val s = ed.crop ?: return
        val pi = doc.indexOf(s.pageId); if (pi < 0) return
        val i = ev.findPointerIndex(cropPid); if (i < 0) return
        val p = cropFile(s, pi, ev.getX(i), ev.getY(i))
        val h = cropGrab
        if (h != null) ed.cropResize(h, p[0] + cropOffFx, p[1] + cropOffFy)
        else if (cropMoving) cropBase?.let { ed.cropMove(it, p[0] - cropDownFx, p[1] - cropDownFy) }
    }

    private fun cropUp(ev: MotionEvent, cancel: Boolean) {
        cropPid = -1; cropGrab = null; cropMoving = false; cropBase = null
        invalidate()
    }

    private val cropPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cropMatrix = Matrix()
    private val cropOut = Path()
    private val cropIn = Path()

    /** Odkrywa przycinany obraz bez odświeżania kafelków: zaraz potem zatwierdzenie przycięcia nanosi go łatą. */
    fun releaseCropHide() = cache.hide(null, 0, refresh = false)

    private fun drawCrop(c: Canvas) {
        val s = ed.crop ?: return
        val img = ed.cropImage() ?: return
        val pi = doc.indexOf(s.pageId); if (pi < 0) return
        val pl = s.place; val f = s.frame
        fun sp(fx: Float, fy: Float): FloatArray { val p = pl.toPage(fx, fy, s.iw, s.ih); return floatArrayOf(scrX(pi, p[0]), scrY(pi, p[1])) }
        // cały plik pod ramką
        val bmp = ed.app.assets.bitmap(doc.id, img.asset, false)
        if (bmp != null) {
            cropMatrix.reset()
            cropMatrix.postScale(s.iw / bmp.width, s.ih / bmp.height)
            cropMatrix.postTranslate(-s.iw * 0.5f, -s.ih * 0.5f)
            if (img.mirror) cropMatrix.postScale(-1f, 1f)
            cropMatrix.postScale(pl.s, pl.s)
            cropMatrix.postRotate(Math.toDegrees(pl.phi.toDouble()).toFloat())
            cropMatrix.postTranslate(pl.ox, pl.oy)
            cropMatrix.postScale(zoom, zoom)
            cropMatrix.postTranslate(layout.left(pi) * zoom + tx, layout.top(pi) * zoom + ty)
            cropPaint.style = Paint.Style.FILL; cropPaint.color = 0xFFFFFFFF.toInt(); cropPaint.isFilterBitmap = true
            c.drawBitmap(bmp, cropMatrix, cropPaint)
        }
        val k = arrayOf(sp(0f, 0f), sp(s.iw, 0f), sp(s.iw, s.ih), sp(0f, s.ih))
        cropOut.reset(); cropOut.moveTo(k[0][0], k[0][1]); for (i in 1..3) cropOut.lineTo(k[i][0], k[i][1]); cropOut.close()
        val fc = com.mdev.mnotes.core.edit.CropGeom.corners(f)
        cropIn.reset(); cropIn.moveTo(sp(fc[0], fc[1])[0], sp(fc[0], fc[1])[1])
        for (i in 1..3) { val q = sp(fc[i * 2], fc[i * 2 + 1]); cropIn.lineTo(q[0], q[1]) }
        cropIn.close()
        // przyciemnienie poza ramką
        cropOut.addPath(cropIn); cropOut.fillType = Path.FillType.EVEN_ODD
        cropPaint.style = Paint.Style.FILL; cropPaint.color = 0x99000000.toInt(); c.drawPath(cropOut, cropPaint)
        cropOut.fillType = Path.FillType.WINDING
        cropPaint.style = Paint.Style.STROKE; cropPaint.strokeWidth = 1f * dp; cropPaint.color = 0x66FFFFFF
        cropOut.reset(); cropOut.moveTo(k[0][0], k[0][1]); for (i in 1..3) cropOut.lineTo(k[i][0], k[i][1]); cropOut.close()
        c.drawPath(cropOut, cropPaint)
        // ramka i uchwyty rysujemy w jej własnych osiach
        val mid = sp(f.cx, f.cy)
        val hw = f.hw * pl.s * zoom; val hh = f.hh * pl.s * zoom
        c.save(); c.translate(mid[0], mid[1]); c.rotate(Math.toDegrees((f.a + pl.phi).toDouble()).toFloat())
        cropPaint.style = Paint.Style.STROKE; cropPaint.strokeWidth = 3.5f * dp; cropPaint.color = 0x55000000; c.drawRect(-hw, -hh, hw, hh, cropPaint)
        cropPaint.strokeWidth = 1.6f * dp; cropPaint.color = 0xFFFFFFFF.toInt(); c.drawRect(-hw, -hh, hw, hh, cropPaint)
        val len = min(20f * dp, min(hw, hh) * 0.5f)
        val mark = min(14f * dp, min(hw, hh) * 0.3f)
        cropPaint.strokeCap = Paint.Cap.ROUND
        for (pass in 0..1) {
            cropPaint.strokeWidth = (if (pass == 0) 7f else 4.5f) * dp
            cropPaint.color = if (pass == 0) 0x66000000 else 0xFFFFFFFF.toInt()
            for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                val x = sx * hw; val y = sy * hh
                c.drawLine(x, y, x - sx * len, y, cropPaint); c.drawLine(x, y, x, y - sy * len, cropPaint)
            }
            c.drawLine(-mark, -hh, mark, -hh, cropPaint); c.drawLine(-mark, hh, mark, hh, cropPaint)
            c.drawLine(-hw, -mark, -hw, mark, cropPaint); c.drawLine(hw, -mark, hw, mark, cropPaint)
        }
        cropPaint.strokeCap = Paint.Cap.BUTT
        c.restore()
    }

    // ───────────────────────── narzędzia: wspólne ─────────────────────────

    private fun beginTool(ev: MotionEvent, idx: Int, forced: Tool? = null) {
        toolPid = ev.getPointerId(idx); toolType = ev.getToolType(idx)
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS && ev.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 && settings.penButton == 2) {
            ed.undo(); mode = Mode.IGNORE; return
        }
        var tool = forced ?: effectiveTool(ev, idx)
        // Ręka z zaznaczeniem: uchwyty i wnętrze ramki (rysikiem i palcem) działają jak lasso, reszta nadal przewija.
        if (tool == Tool.HAND && !ed.readOnly && selGrab(ev.getX(idx), ev.getY(idx))) tool = Tool.LASSO
        activeTool = tool
        if (tool == Tool.HAND) { startNav(ev); navSelect = true; return }
        mode = Mode.TOOL
        curX = ev.getX(idx); curY = ev.getY(idx)
        downX = curX; downY = curY; downT = ev.eventTime; toolMoved = false
        lm = LM.NONE; ln = 0
        if ((tool == Tool.PEN || tool == Tool.LASSO) && (resizeDown() || vertexDown())) { activeTool = Tool.LASSO; invalidate(); return }
        when (tool) {
            Tool.PEN -> penDown(ev, idx)
            Tool.ERASER -> eraseDown()
            Tool.LASSO -> { lassoDown(); if (mode == Mode.TOOL) postDelayed(holdRunnable, 520L) }
            Tool.SPACE -> spaceDown()
            Tool.TAPE -> tapeDown()
            Tool.PAINTER -> paintDown()
            Tool.FILL -> if (ed.readOnly || pageIdxAt(curX, curY) < 0) mode = Mode.IGNORE
            Tool.LASER -> { laser.begin(System.currentTimeMillis()); addLaser(curX, curY) }
            else -> {}
        }
        invalidate()
    }

    private fun toolMove(ev: MotionEvent, idx: Int) {
        curX = ev.getX(idx); curY = ev.getY(idx)
        if (!toolMoved && hypot(curX - downX, curY - downY) > slop) toolMoved = true
        when (activeTool) {
            Tool.PEN -> penMove(ev, idx)
            Tool.ERASER -> {
                for (h in 0 until ev.historySize) eraseTo(ev.getHistoricalX(idx, h), ev.getHistoricalY(idx, h))
                eraseTo(curX, curY)
            }
            Tool.LASSO -> lassoMove()
            Tool.SPACE -> spaceMove()
            Tool.TAPE -> tapeMove()
            Tool.PAINTER -> {
                for (h in 0 until ev.historySize) paintTo(ev.getHistoricalX(idx, h), ev.getHistoricalY(idx, h))
                paintTo(curX, curY)
            }
            Tool.LASER -> { for (h in 0 until ev.historySize) addLaser(ev.getHistoricalX(idx, h), ev.getHistoricalY(idx, h)); addLaser(curX, curY) }
            else -> {}
        }
        invalidate()
    }

    private fun finishTool(ev: MotionEvent, idx: Int, cancel: Boolean) {
        removeCallbacks(holdRunnable)
        when (activeTool) {
            Tool.PEN -> penUp(cancel)
            Tool.ERASER -> eraseUp(cancel)
            Tool.LASSO -> lassoUp(cancel)
            Tool.SPACE -> spaceUp(cancel)
            Tool.TAPE -> tapeUp(cancel)
            Tool.PAINTER -> paintUp(cancel)
            Tool.FILL -> if (!cancel && !toolMoved) fillTap()
            Tool.TEXT -> if (!cancel && !toolMoved) textTap()
            Tool.LASER -> laser.end(System.currentTimeMillis())
            else -> {}
        }
        predN = 0
        invalidate()
    }

    private fun cancelTool() { finishTool(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0).also { it.recycle() }, 0, true); mode = Mode.NONE }

    private fun pageIdxAt(sx: Float, sy: Float) = layout.pageAt(docX(sx), docY(sy))
    private fun pgX(pi: Int, sx: Float) = docX(sx) - layout.left(pi)
    private fun pgY(pi: Int, sy: Float) = docY(sy) - layout.top(pi)

    // ───────────────────────── pióro ─────────────────────────

    private fun pressureOf(raw: Float) = if (toolType == MotionEvent.TOOL_TYPE_STYLUS) raw else 0.5f

    private fun penDown(ev: MotionEvent, idx: Int) {
        val pi = pageIdxAt(curX, curY)
        if (pi < 0) { mode = Mode.IGNORE; return }
        livePage = pi
        val p = ed.pen
        val sp = PenSpec.of(p.kind)
        builder.begin(p.kind, p.color, p.width, (sp.smoothing * settings.smoothing).coerceIn(0f, 1f), (0.5f / zoom).coerceIn(0.05f, 0.9f), p.style)
        liveActive = true; shapeSnapped = false; predN = 0
        holdX = curX; holdY = curY; holdRaw = 0
        if (ed.sel != null) ed.clearSel()
        rulerSide = 0
        if (ed.ruler) {
            val x = docX(curX); val y = docY(curY)
            val side = ruler.edgeAt(x, y, 22f * dp / zoom, 8f * dp / zoom, 12f * dp / zoom)
            if (side != 0) { rulerSide = side; rulerU0 = ruler.u(x, y).coerceIn(-ruler.len / 2, ruler.len / 2); rulerU1 = rulerU0 }
        }
        feedPen(ev, idx)
        armHold()
    }

    private fun feedPen(ev: MotionEvent, idx: Int) {
        val ox = layout.left(livePage); val oy = layout.top(livePage)
        for (h in 0 until ev.historySize) {
            addPenPt(docX(ev.getHistoricalX(idx, h)), docY(ev.getHistoricalY(idx, h)), pressureOf(ev.getHistoricalPressure(idx, h)), ev.getHistoricalEventTime(h), ox, oy)
        }
        addPenPt(docX(ev.getX(idx)), docY(ev.getY(idx)), pressureOf(ev.getPressure(idx)), ev.eventTime, ox, oy)
    }

    /** Punkt kreski; przy linijce rzutowany na jej krawędź, tak by kreska przylegała do niej bokiem. */
    private fun addPenPt(x: Float, y: Float, p: Float, t: Long, ox: Float, oy: Float) {
        if (rulerSide == 0) { builder.add(x - ox, y - oy, p, t); return }
        val u = ruler.u(x, y).coerceIn(-ruler.len / 2, ruler.len / 2)
        rulerU1 = u
        val v = rulerSide * (ruler.thick / 2 + builder.width / 2)
        builder.add(ruler.x(u, v) - ox, ruler.y(u, v) - oy, p, t)
    }

    private fun penMove(ev: MotionEvent, idx: Int) {
        if (!liveActive) return
        if (shapeSnapped) { adjustSnapped(); return }
        feedPen(ev, idx)
        // Ślizgający się rysik dryfuje: licznik przytrzymania zeruje dopiero wyraźny ruch poza promieniem dryfu wokół punktu osiadnięcia.
        if (hypot(curX - holdX, curY - holdY) > HOLD_DRIFT_DP * dp) { holdX = curX; holdY = curY; holdRaw = builder.rawCount; armHold() }
        updatePrediction()
    }

    private fun updatePrediction() {
        predN = 0
        if (rulerSide != 0) return
        val pe = predictor?.predict() ?: return
        val n = min(pe.historySize + 1, predXY.size / 2)
        var k = 0
        while (k < n - 1) { predXY[2 * k] = pe.getHistoricalX(0, k); predXY[2 * k + 1] = pe.getHistoricalY(0, k); k++ }
        predXY[2 * k] = pe.getX(0); predXY[2 * k + 1] = pe.getY(0)
        predN = n
    }

    private fun armHold() {
        removeCallbacks(holdRunnable)
        if (settings.holdToShape && rulerSide == 0 && !shapeSnapped && toolType != MotionEvent.TOOL_TYPE_FINGER) postDelayed(holdRunnable, settings.holdMs.toLong())
    }

    private fun trySnapShape() {
        // Rozpoznajemy z surowych próbek (wygładzony filtr zaokrągla rogi) i bez ogona dryfu po osiadnięciu pióra.
        val total = builder.rawCount
        if (total < 8) return
        val n = if (holdRaw >= 8) holdRaw.coerceAtMost(total) else total
        val shape = ShapeRecognizer.recognize(builder.raw, n) ?: return
        val m = shape.xy.size / 2
        shapePts = FloatArray(3 * m)
        for (i in 0 until m) { shapePts[3 * i] = shape.xy[2 * i]; shapePts[3 * i + 1] = shape.xy[2 * i + 1]; shapePts[3 * i + 2] = 1f }
        shapeKind = shape.kind; shapePoly = shape.poly; shapeBase = shape.xy
        shapeDownX = builder.raw[2 * total - 2]; shapeDownY = builder.raw[2 * total - 1]
        var sx = 0f; var sy = 0f
        for (i in 0 until m) { sx += shape.xy[2 * i]; sy += shape.xy[2 * i + 1] }
        shapeCx = sx / m; shapeCy = sy / m
        shapeRef = hypot(shapeDownX - shapeCx, shapeDownY - shapeCy)
        shapeSnapAng = Float.NaN; shapeLabel = null
        shapeSnapped = true; predN = 0
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        invalidate()
    }

    /**
     * Po rozpoznaniu kształtu pióro nadal trzymane reguluje go na żywo: koniec linii podąża za piórem (długość i kąt,
     * kąt przyciąga co 15°), pozostałe kształty skalują się względem środka.
     */
    private fun adjustSnapped() {
        val pi = livePage
        if (pi !in 0 until layout.count) return
        val px = pgX(pi, curX); val py = pgY(pi, curY)
        // Martwa strefa: drobny dryf pióra tuż po rozpoznaniu nie rusza kształtu, dopiero świadome przesunięcie.
        val dead = SNAP_DEAD_DP * dp / zoom
        var dx = px - shapeDownX; var dy = py - shapeDownY
        val dm = hypot(dx, dy)
        if (dm <= dead) { dx = 0f; dy = 0f } else { val f = (dm - dead) / dm; dx *= f; dy *= f }
        val b = shapeBase
        val mm = settings.unitsMm
        fun fmt(pt: Float) = if (mm) "%.1f mm".format(pt * 25.4f / 72f) else "%.2f in".format(pt / 72f)
        if (shapeKind == ShapeKind.LINE) {
            val x1 = b[0]; val y1 = b[1]
            var x2 = b[2] + dx; var y2 = b[3] + dy
            val len = hypot(x2 - x1, y2 - y1)
            var ang = atan2(y2 - y1, x2 - x1)
            val step = (PI / 12).toFloat()
            val snapped = (ang / step).roundToInt() * step
            val onStep = abs(ang - snapped) < Math.toRadians(3.0)
            if (onStep) { ang = snapped; x2 = x1 + cos(ang) * len; y2 = y1 + sin(ang) * len }
            if (onStep && shapeSnapAng != snapped) { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            shapeSnapAng = if (onStep) snapped else Float.NaN
            shapePts[3] = x2; shapePts[4] = y2
            val deg = ((Math.toDegrees((-ang).toDouble()) % 180.0) + 180.0) % 180.0
            shapeLabel = "${fmt(len)} · ${"%.0f".format(deg)}°"
        } else if (shapeKind == ShapeKind.RECT && ShapeBox.frameOfCorners(b) != null) {
            // Prostokąt: ruch pióra w poziomie zmienia szerokość, w pionie wysokość (niezależnie).
            val f0 = ShapeBox.frameOfCorners(b)!!
            val f = ShapeBox.dragged(f0, shapeDownX, shapeDownY, shapeDownX + dx, shapeDownY + dy, SNAP_EQ_DP * dp / zoom)
            val ca = floatArrayOf(-1f, 1f, 1f, -1f, -1f); val cb = floatArrayOf(-1f, -1f, 1f, 1f, -1f)
            for (i in 0 until 5) { shapePts[3 * i] = f.x(ca[i], cb[i]); shapePts[3 * i + 1] = f.y(ca[i], cb[i]) }
            shapeLabel = "${fmt(2f * f.hw)} × ${fmt(2f * f.hh)}" + if (f.hw == f.hh) " =" else ""
        } else {
            val moved = dm > dead
            val k = if (moved && shapeRef > 6f) (hypot(px - shapeCx, py - shapeCy) / shapeRef).coerceIn(0.2f, 8f) else 1f
            val m = b.size / 2
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var bt = -Float.MAX_VALUE
            for (i in 0 until m) {
                val x = shapeCx + (b[2 * i] - shapeCx) * k; val y = shapeCy + (b[2 * i + 1] - shapeCy) * k
                shapePts[3 * i] = x; shapePts[3 * i + 1] = y
                l = min(l, x); r = max(r, x); t = min(t, y); bt = max(bt, y)
            }
            shapeLabel = "${fmt(r - l)} × ${fmt(bt - t)}"
        }
        ed.viewTick++
        invalidate()
    }

    private fun penUp(cancel: Boolean) {
        if (!liveActive) return
        liveActive = false; rulerSide = 0
        val pi = livePage
        val snapped = shapeSnapped
        val stroke = if (cancel) null else if (snapped) Stroke(Ids.next(), builder.penKind, builder.penColor, builder.width, shapePts.copyOf(), false, shapePoly, shapeKind == ShapeKind.ELLIPSE, builder.line) else builder.finish(Ids.next())
        shapeSnapped = false; shapeLabel = null
        if (stroke == null || pi !in doc.pages.indices) return
        val page = doc.pages[pi]
        ed.exec(DocEdit.Elements(page.id, ElementsEdit.add(doc.content(page), listOf(stroke))))
        // Kształt z uchwytami od razu zaznaczony — niebieskie punkty pozwalają go przekształcić.
        if (snapped) ed.select(page.id, setOf(stroke.id))
    }

    // ───────────────────────── uchwyty wierzchołków łamanej ─────────────────────────

    /** Jedyny zaznaczony element, jeśli jest łamaną z uchwytami (nie zablokowaną); w trakcie przeciągania — jej bieżąca wersja. */
    private fun polySel(): Stroke? {
        if (lLifting && lm == LM.VERTEX) return lEls.firstOrNull() as? Stroke
        val s = ed.sel ?: return null
        if (s.ids.size != 1 || lLifting) return null
        val e = ed.selElements().singleOrNull() as? Stroke ?: return null
        return if (e.poly && !e.locked && e.count >= 2 && ShapeBox.frameOf(e) == null) e else null
    }

    /** Jedyny zaznaczony kwadrat, prostokąt lub elipsa z autokorekty (8 uchwytów szerokości i wysokości); w trakcie zmiany — jej bieżąca wersja. */
    private fun shapeSel(): Stroke? {
        if (lLifting) return if (lm == LM.RESIZE) lEls.firstOrNull() as? Stroke else null
        val s = ed.sel ?: return null
        if (s.ids.size != 1) return null
        val e = ed.selElements().singleOrNull() as? Stroke ?: return null
        return if (ShapeBox.frameOf(e) != null) e else null
    }

    /** Promień chwytu uchwytów kształtu: najwyżej 24 dp, a przy małym kształcie mniejszy, by środek zostawał do przesuwania. */
    private fun resizeHitRadius(f: ShapeFrame) = min(24f * dp, max(12f * dp, 0.7f * min(f.hw, f.hh) * zoom))

    /** Numer uchwytu kształtu pod punktem ekranu albo -1. */
    private fun resizeHit(st: Stroke, pi: Int, sx: Float, sy: Float): Int {
        val f = ShapeBox.frameOf(st) ?: return -1
        var best = -1; var bd = resizeHitRadius(f)
        for (h in 0 until ShapeBox.HANDLES) {
            val d = hypot(sx - scrX(pi, ShapeBox.handleX(f, h)), sy - scrY(pi, ShapeBox.handleY(f, h)))
            if (d < bd) { bd = d; best = h }
        }
        return best
    }

    /** Jedyny zaznaczony, niezablokowany tekst (4 uchwyty boków); w trakcie zmiany rozmiaru — jego bieżąca wersja. */
    private fun textSel(): TextBox? {
        if (lLifting) return if (lm == LM.RESIZE) lEls.firstOrNull() as? TextBox else null
        val s = ed.sel ?: return null
        if (s.ids.size != 1) return null
        val e = ed.selElements().singleOrNull() as? TextBox ?: return null
        return if (e.locked) null else e
    }

    private fun textHandleX(t: TextBox, side: TextFit.Side): Float = when (side) {
        TextFit.Side.RIGHT -> t.cx + cos(t.rot) * t.w / 2f
        TextFit.Side.LEFT -> t.cx - cos(t.rot) * t.w / 2f
        TextFit.Side.TOP -> t.cx + sin(t.rot) * t.h / 2f
        TextFit.Side.BOTTOM -> t.cx - sin(t.rot) * t.h / 2f
    }

    private fun textHandleY(t: TextBox, side: TextFit.Side): Float = when (side) {
        TextFit.Side.RIGHT -> t.cy + sin(t.rot) * t.w / 2f
        TextFit.Side.LEFT -> t.cy - sin(t.rot) * t.w / 2f
        TextFit.Side.TOP -> t.cy - cos(t.rot) * t.h / 2f
        TextFit.Side.BOTTOM -> t.cy + cos(t.rot) * t.h / 2f
    }

    /** Uchwyt boku pola tekstowego pod punktem ekranu albo null. Promień maleje przy małym polu, by środek zostawał do przesuwania. */
    private fun textHit(t: TextBox, pi: Int, sx: Float, sy: Float): TextFit.Side? {
        var best: TextFit.Side? = null
        var bd = min(24f * dp, max(12f * dp, 0.4f * min(t.w, t.h) * zoom))
        for (side in TextFit.Side.values()) {
            val d = hypot(sx - scrX(pi, textHandleX(t, side)), sy - scrY(pi, textHandleY(t, side)))
            if (d < bd) { bd = d; best = side }
        }
        return best
    }

    private fun textResizeDown(): Boolean {
        val t = textSel() ?: return false
        val s = ed.sel ?: return false
        val pi = doc.indexOf(s.pageId); if (pi < 0) return false
        val side = textHit(t, pi, curX, curY) ?: return false
        lPage = pi; lm = LM.RESIZE; lIds = s.ids; tBase = t; tSide = side
        lBounds = t.bounds; lM = Affine.IDENTITY; lLifting = false
        // uchwyt zostaje pod piórem tam, gdzie go chwycono
        vDx = textHandleX(t, side) - pgX(pi, curX); vDy = textHandleY(t, side) - pgY(pi, curY)
        return true
    }

    /** Przeciąga uchwyt boku tekstu; zawsze liczy od stanu z początku gestu, więc nie ma dryfu. */
    private fun textResizeDrag(px: Float, py: Float) {
        val b = tBase ?: return
        var x = px + vDx; var y = py + vDy
        if (settings.snapGrid || ed.gridSnap) { val g = 14.17f; x = (x / g).roundToInt() * g; y = (y / g).roundToInt() * g }
        val cur = TextFit.resizeSide(b, tSide, x, y, ed.textMeasurer())
        lEls = listOf(cur); lBounds = cur.bounds
        val mm = settings.unitsMm
        fun fmt(pt: Float) = if (mm) "%.1f mm".format(pt * 25.4f / 72f) else "%.2f in".format(pt / 72f)
        shapeLabel = "${fmt(cur.w)} × ${fmt(cur.h)}"
        ed.viewTick++
    }

    private fun resizeDown(): Boolean {
        // dotknięcie uchwytu bez przeciągnięcia nie czyści stanu gestu, więc zerujemy go przed następnym
        tBase = null; vBase = null
        val st = shapeSel() ?: return textResizeDown()
        val s = ed.sel ?: return false
        val pi = doc.indexOf(s.pageId); if (pi < 0) return false
        val h = resizeHit(st, pi, curX, curY)
        if (h < 0) return false
        val f = ShapeBox.frameOf(st) ?: return false
        lPage = pi; lm = LM.RESIZE; lIds = s.ids; vBase = st; rFrame = f; rHandle = h
        lBounds = st.bounds; lM = Affine.IDENTITY; lLifting = false
        // Uchwyt zostaje pod piórem tam, gdzie go chwycono (bez skoku do środka kropki).
        vDx = ShapeBox.handleX(f, h) - pgX(pi, curX); vDy = ShapeBox.handleY(f, h) - pgY(pi, curY)
        return true
    }

    /** Przeciąga uchwyt kształtu: przeciwległa krawędź stoi, kwadrat i koło łapią się same, opcjonalnie siatka. */
    private fun resizeDrag(px: Float, py: Float) {
        if (tBase != null) { textResizeDrag(px, py); return }
        val b = vBase ?: return; val f = rFrame ?: return
        var x = px + vDx; var y = py + vDy
        if (settings.snapGrid || ed.gridSnap) { val g = 14.17f; x = (x / g).roundToInt() * g; y = (y / g).roundToInt() * g }
        val g = ShapeBox.resized(f, rHandle, x, y, 6f * dp / zoom)
        val cur = ShapeBox.applyTo(b, f, g)
        lEls = listOf(cur); lBounds = cur.bounds
        val mm = settings.unitsMm
        fun fmt(pt: Float) = if (mm) "%.1f mm".format(pt * 25.4f / 72f) else "%.2f in".format(pt / 72f)
        val equal = abs(g.hw - g.hh) < 1e-3f
        shapeLabel = "${fmt(2 * g.hw)} × ${fmt(2 * g.hh)}" + if (equal) " · =" else ""
        ed.viewTick++
    }

    private fun vertexDown(): Boolean {
        val st = polySel() ?: return false
        val s = ed.sel ?: return false
        val pi = doc.indexOf(s.pageId); if (pi < 0) return false
        var best = -1; var bd = 24f * dp
        for (i in 0 until st.vertexCount) {
            val d = hypot(curX - scrX(pi, st.pts[3 * i]), curY - scrY(pi, st.pts[3 * i + 1]))
            if (d < bd) { bd = d; best = i }
        }
        if (best < 0) return false
        lPage = pi; lm = LM.VERTEX; lIds = s.ids; vIdx = best; vBase = st
        lBounds = st.bounds; lM = Affine.IDENTITY; lLifting = false
        vDx = st.pts[3 * best] - pgX(pi, curX); vDy = st.pts[3 * best + 1] - pgY(pi, curY)
        return true
    }

    /** Przesuwa uchwyt: przyciąga do osi pozostałych wierzchołków (poziom/pion) i opcjonalnie do siatki. */
    private fun vertexDrag(px: Float, py: Float) {
        val b = vBase ?: return
        var x = px + vDx; var y = py + vDy
        if (settings.snapGrid || ed.gridSnap) { val g = 14.17f; x = (x / g).roundToInt() * g; y = (y / g).roundToInt() * g }
        else {
            val tol = 5f * dp / zoom
            var bx = tol; var by = tol
            var sx = x; var sy = y
            for (j in 0 until b.vertexCount) {
                if (j == vIdx) continue
                val dxj = abs(b.pts[3 * j] - x); val dyj = abs(b.pts[3 * j + 1] - y)
                if (dxj < bx) { bx = dxj; sx = b.pts[3 * j] }
                if (dyj < by) { by = dyj; sy = b.pts[3 * j + 1] }
            }
            x = sx; y = sy
        }
        val cur = b.withVertex(vIdx, x, y)
        lEls = listOf(cur); lBounds = cur.bounds
        ed.viewTick++
    }

    // ───────────────────────── okno powiększenia (EXT-003) ─────────────────────────

    private val zw = ZoomRegion()
    private var zwPage = -1
    private var zwGesture = false      // gest zaczęty w obszarze pisania okna: zdarzenia są przeliczane i trafiają do zwykłego potoku
    private var zwCtl = false          // gest zaczęty na pasku okna (przyciski, przeciąganie obszaru)
    private var zwCtlX = 0f; private var zwCtlY = 0f; private var zwCtlMoved = false
    private var zwLastX = 0f
    private val zwRect = RectF()
    private val zwMat = Matrix()
    private val zwFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val zwLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * dp; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val zwText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f * dp; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val zwPath = Path()
    private val zwStrip get() = 34f * dp
    private val zwBtn get() = 40f * dp

    private fun zwMag() = settings.zoomMag.coerceIn(2f, 5f)

    /** Ramka okna: dół widoku (nad paskiem narzędzi, gdy jest na dole), do 640 dp szerokości, wyśrodkowana. */
    private fun zwLayout() {
        val w = min(width - 16f * dp, 640f * dp)
        val h = (height * 0.30f).coerceIn(150f * dp, 260f * dp)
        val bottom = height - 8f * dp
        val l = (width - w) / 2f
        zwRect.set(l, max(8f * dp, bottom - h), l + w, bottom)
    }

    private fun zwRegW() = (zwRect.width() / (zoom * zwMag()))
    private fun zwRegH() = ((zwRect.height() - zwStrip) / (zoom * zwMag()))

    private fun zwClamp() {
        val spec = doc.pages.getOrNull(zwPage)?.spec ?: return
        zw.clamp(spec.w, spec.h, zwRegW(), zwRegH())
    }

    /** Włączenie/wyłączenie: obszar startuje w środku widocznego fragmentu strony. */
    fun zoomWinChanged() {
        if (ed.zoomWin && width > 0 && layout.count > 0) {
            zwLayout()
            val dx = docX(width / 2f); val dy = docY(height * 0.35f)
            val i = layout.nearest(dx, dy)
            zwPage = i
            zw.x = dx - layout.left(i) - zwRegW() / 2; zw.y = dy - layout.top(i) - zwRegH() / 2
            zwClamp()
        }
        zwGesture = false; zwCtl = false
        invalidate()
    }

    /** Macierz: punkt w obszarze pisania okna → ten sam punkt strony na ekranie głównego widoku. */
    private fun zwMatrix(): Matrix {
        val mag = zwMag()
        val x0 = (layout.left(zwPage) + zw.x) * zoom + tx
        val y0 = (layout.top(zwPage) + zw.y) * zoom + ty
        zwMat.setScale(1f / mag, 1f / mag)
        zwMat.postTranslate(x0 - zwRect.left / mag, y0 - (zwRect.top + zwStrip) / mag)
        return zwMat
    }

    /** null = zdarzenie nie dotyczy okna; true/false = obsłużone (pasek okna). */
    private fun zwTouch(ev: MotionEvent): Boolean? {
        if (zwPage !in 0 until layout.count) { zoomWinChanged(); if (zwPage !in 0 until layout.count) return null }
        zwLayout()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                zwGesture = false; zwCtl = false
                if (!zwRect.contains(ev.x, ev.y)) return null
                if (ev.y < zwRect.top + zwStrip) {
                    zwCtl = true; zwCtlX = ev.x; zwCtlY = ev.y; zwCtlMoved = false
                    stopAnim()
                    return true
                }
                zwGesture = true; zwLastX = ev.x
                return null
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                if (zwCtl) {
                    if (ev.actionMasked == MotionEvent.ACTION_MOVE) zwPan(ev)
                    return true
                }
                if (zwGesture) zwLastX = ev.x
                return null
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (zwCtl) {
                    zwCtl = false
                    if (!zwCtlMoved && ev.actionMasked == MotionEvent.ACTION_UP) zwButton(zwCtlX)
                    invalidate()
                    return true
                }
                if (zwGesture) zwLastX = ev.x
                return null
            }
        }
        return if (zwCtl) true else null
    }

    /** Po zakończeniu gestu w oknie: przy dojściu do prawej krawędzi okno samo przesuwa obszar dalej. */
    private fun zwEnd(ev: MotionEvent) {
        if (ev.actionMasked != MotionEvent.ACTION_UP && ev.actionMasked != MotionEvent.ACTION_CANCEL) return
        zwGesture = false
        if (ev.actionMasked == MotionEvent.ACTION_UP && zwLastX > zwRect.left + 0.8f * zwRect.width()) {
            val spec = doc.pages.getOrNull(zwPage)?.spec ?: return
            zw.shiftRight(spec.w, spec.h, zwRegW(), zwRegH(), 0.5f)
        }
        invalidate()
    }

    private fun zwPan(ev: MotionEvent) {
        val dx = ev.x - zwCtlX; val dy = ev.y - zwCtlY
        if (!zwCtlMoved && hypot(dx, dy) < slop) return
        zwCtlMoved = true
        val z = zoom * zwMag()
        zw.x -= dx / z; zw.y -= dy / z
        zwCtlX = ev.x; zwCtlY = ev.y
        zwClamp(); invalidate()
    }

    /** Przyciski paska: ◀ ▶ ↵ ×N … ✕. */
    private fun zwButton(x: Float) {
        val spec = doc.pages.getOrNull(zwPage)?.spec ?: return
        val b = zwBtn
        val i = ((x - zwRect.left) / b).toInt()
        val rw = zwRegW(); val rh = zwRegH()
        when {
            x > zwRect.right - b -> { ed.zoomWin = false; zoomWinChanged() }
            i == 0 -> zw.shiftLeft(rw)
            i == 1 -> zw.shiftRight(spec.w, spec.h, rw, rh)
            i == 2 -> if (!zw.nextLine(spec.h, rh) && zwPage + 1 < layout.count) { zwPage++; zw.x = 0f; zw.y = 0f }
            i == 3 -> { val m = zwMag(); settings.zoomMag = if (m >= 5f) 2f else m + 1f; zwClamp() }
        }
        zwClamp()
    }

    private fun drawZoomWin(c: Canvas) {
        if (!ed.zoomWin || zwPage !in 0 until layout.count) return
        zwLayout(); zwClamp()
        val page = doc.pages[zwPage]; val spec = page.spec
        val z = zoom * zwMag()
        val rw = zwRegW(); val rh = zwRegH()
        val pr = layout.rect(zwPage)

        // Wskaźnik obszaru na stronie głównego widoku.
        val l = (pr.l + zw.x) * zoom + tx; val t = (pr.t + zw.y) * zoom + ty
        zwFill.color = (accent and 0x00FFFFFF) or 0x1E000000
        c.drawRect(l, t, l + rw * zoom, t + rh * zoom, zwFill)
        zwLine.color = accent; zwLine.strokeWidth = 2f * dp
        c.drawRect(l, t, l + rw * zoom, t + rh * zoom, zwLine)

        // Panel okna.
        val rr = 12f * dp
        zwFill.color = if (darkUi) 0xFF2A2D31.toInt() else 0xFFFFFFFF.toInt()
        c.drawRoundRect(zwRect, rr, rr, zwFill)
        c.save()
        tmpRect.set(zwRect); tmpRect.top += zwStrip
        c.clipRect(tmpRect)
        c.translate(tmpRect.left, tmpRect.top)
        c.scale(z, z)
        c.translate(-zw.x, -zw.y)
        c.clipRect(0f, 0f, spec.w, spec.h)
        renderer.drawPaper(c, spec, z, cache.backdrop(page))
        renderer.drawElements(c, doc.id, doc.content(page), 0, Bounds(zw.x, zw.y, zw.x + rw, zw.y + rh), z, false, spec.darkPaper)
        if (liveActive && livePage == zwPage) {
            if (shapeSnapped) renderer.drawStrokeData(c, builder.penKind, builder.penColor, builder.width, shapePts, shapePts.size / 3, z, spec.darkPaper, shapePoly, builder.line, true)
            else renderer.drawStrokeData(c, builder.penKind, builder.penColor, builder.width, builder.pts, builder.count, z, spec.darkPaper, line = builder.line)
        }
        c.restore()

        // Pasek: przyciski i uchwyt do przeciągania.
        val ink = if (darkUi) 0xFFE6E6E6.toInt() else 0xFF3A3A3A.toInt()
        zwFill.color = if (darkUi) 0xFF383C41.toInt() else 0xFFEDEFF1.toInt()
        zwPath.reset()
        zwPath.addRoundRect(zwRect.left, zwRect.top, zwRect.right, zwRect.top + zwStrip, floatArrayOf(rr, rr, rr, rr, 0f, 0f, 0f, 0f), Path.Direction.CW)
        c.drawPath(zwPath, zwFill)
        zwLine.color = ink; zwLine.strokeWidth = 2f * dp
        zwText.color = ink
        val b = zwBtn; val cy = zwRect.top + zwStrip / 2; val s = 6f * dp
        var cx = zwRect.left + b / 2
        // ◀
        zwPath.reset(); zwPath.moveTo(cx + s / 2, cy - s); zwPath.lineTo(cx - s / 2, cy); zwPath.lineTo(cx + s / 2, cy + s); c.drawPath(zwPath, zwLine)
        cx += b
        // ▶
        zwPath.reset(); zwPath.moveTo(cx - s / 2, cy - s); zwPath.lineTo(cx + s / 2, cy); zwPath.lineTo(cx - s / 2, cy + s); c.drawPath(zwPath, zwLine)
        cx += b
        // ↵ (nowa linia)
        zwPath.reset(); zwPath.moveTo(cx + s, cy - s); zwPath.lineTo(cx + s, cy + s * 0.4f); zwPath.lineTo(cx - s, cy + s * 0.4f)
        zwPath.moveTo(cx - s + s * 0.7f, cy - s * 0.3f); zwPath.lineTo(cx - s, cy + s * 0.4f); zwPath.lineTo(cx - s + s * 0.7f, cy + s * 1.1f)
        c.drawPath(zwPath, zwLine)
        cx += b
        c.drawText("×" + zwMag().toInt(), cx, cy + zwText.textSize * 0.35f, zwText)
        // uchwyt (trzy kreski) pośrodku wolnego miejsca
        val gx = (zwRect.left + 4 * b + zwRect.right - b) / 2
        for (k in -1..1) c.drawLine(gx - 10f * dp, cy + k * 4f * dp, gx + 10f * dp, cy + k * 4f * dp, zwLine)
        // ✕
        cx = zwRect.right - b / 2
        c.drawLine(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.8f, zwLine)
        c.drawLine(cx - s * 0.8f, cy + s * 0.8f, cx + s * 0.8f, cy - s * 0.8f, zwLine)
        // obrys
        zwLine.color = if (darkUi) 0x55FFFFFF else 0x33000000; zwLine.strokeWidth = 1f * dp
        c.drawRoundRect(zwRect, rr, rr, zwLine)
    }

    // ───────────────────────── gumka ─────────────────────────

    private fun eraseDown() {
        eraseStart.clear(); eraseLastPage = -1
        erasePending = null; erasePendingPage = null; eraseNextApply = 0L; eraseDirty = Bounds.EMPTY
        if (settings.eraserMode == 2) { val pi = pageIdxAt(curX, curY); if (pi < 0) { mode = Mode.IGNORE; return }; lPage = pi; ln = 0; addLasso(pgX(pi, curX), pgY(pi, curY)) }
        else eraseTo(curX, curY)
        if (ed.sel != null) ed.clearSel()
    }

    private fun eraseTo(sx: Float, sy: Float) {
        if (settings.eraserMode == 2) { if (lPage >= 0) addLasso(pgX(lPage, sx), pgY(lPage, sy)); return }
        val pi = pageIdxAt(sx, sy)
        if (pi < 0) { eraseLastPage = -1; return }
        val px = pgX(pi, sx); val py = pgY(pi, sy)
        val r = settings.eraserSize
        // Kroki liczone są w pamięci na roboczej liście; do dokumentu (a więc do przemalowania bitmap) trafiają
        // rzadziej, gdy przemalowanie jest drogie, bo to ono, a nie liczenie, zacina aplikację.
        val page = doc.pages[pi]
        if (erasePendingPage != null && erasePendingPage != page.id) flushErase()
        val cur = doc.content(page)
        var work: List<Element> = erasePending ?: cur.elements
        var changed = false
        if (eraseLastPage == pi) {
            val steps = ceil(hypot(px - eraseLx, py - eraseLy) / (r * 0.6f)).toInt().coerceAtLeast(1)
            for (k in 1..steps) {
                val f = k.toFloat() / steps
                eraseStep(work, eraseLx + (px - eraseLx) * f, eraseLy + (py - eraseLy) * f)?.let { work = it; changed = true }
            }
        } else eraseStep(work, px, py)?.let { work = it; changed = true }
        eraseLastPage = pi; eraseLx = px; eraseLy = py
        if (!changed) return
        eraseStart.getOrPut(page.id) { cur }
        erasePending = work; erasePendingPage = page.id
        val wait = eraseNextApply - SystemClock.uptimeMillis()
        if (wait <= 0) flushErase() else if (!eraseFlushPosted) { eraseFlushPosted = true; postDelayed(eraseFlushRun, wait) }
    }

    /** Suma obszarów zmienionych przez kroki gumki, które jeszcze nie trafiły do dokumentu. */
    private var eraseDirty = Bounds.EMPTY
    /** Obszar przekazywany do przemalowania w tle w [pageChanged] na czas [flushErase] i zatwierdzenia przesunięcia. */
    private var applyDirty: Bounds? = null
    private var applyPage: String? = null

    private val eraseFlushRun = Runnable { eraseFlushPosted = false; flushErase() }

    /**
     * Przenosi roboczy wynik gumki do dokumentu i mierzy, ile kosztowało przemalowanie: następne może nastąpić
     * dopiero po kilkukrotności tego czasu (najwyżej [ERASE_MAX_WAIT_MS]), więc na ciężkiej stronie gumka
     * odświeża się rzadziej, ale reszta aplikacji (przewijanie, pasek, rysik) ma wolny wątek.
     */
    private fun flushErase() {
        val work = erasePending ?: return
        val pid = erasePendingPage ?: return
        erasePending = null; erasePendingPage = null
        removeCallbacks(eraseFlushRun); eraseFlushPosted = false
        val page = doc.page(pid) ?: return
        val t0 = SystemClock.uptimeMillis()
        val cur = doc.content(page)
        // Przemalowujemy tylko to, co gumka naprawdę zmieniła, a nie całe prostokąty przeciętych kresek.
        applyDirty = eraseDirty; applyPage = pid; eraseDirty = Bounds.EMPTY
        doc.applyElements(pid, ElementsEdit.diff(cur, PageContent.of(work)))
        applyDirty = null; applyPage = null
        val cost = SystemClock.uptimeMillis() - t0
        eraseNextApply = if (cost <= ERASE_FREE_MS) 0L else t0 + cost + (cost * ERASE_REST).toLong().coerceAtMost(ERASE_MAX_WAIT_MS)
    }

    /** Jeden krok gumki w punkcie (px, py); null = nic nie zmienił. Lista powstaje dopiero przy pierwszym trafieniu. */
    private fun eraseStep(src: List<Element>, px: Float, py: Float): List<Element>? {
        val r = settings.eraserSize
        val keepHl = settings.eraserKeepHighlighter
        val pixel = settings.eraserMode == 1
        var out: ArrayList<Element>? = null
        for (i in src.indices) {
            val e = src[i]
            // Wypełnienie leży pod pismem: gumka przeciągana po zamalowanym obszarze ściera tylko kreski (usunąć je można lassem gumki).
            val skip = e.locked || e is TapeEl || e is FillEl || (keepHl && e is Stroke && e.pen == PenKind.HIGHLIGHTER) || !e.bounds.inflate(r + 1f).contains(px, py)
            var repl: List<Element>? = null
            var hit = false
            if (!skip) {
                if (pixel) {
                    if (e is Stroke) {
                        val res = EraseMath.eraseLocal(e, px, py, r)
                        if (res != null) {
                            hit = true; eraseDirty = eraseDirty.union(res.dirty)
                            repl = res.pieces.map { Stroke(Ids.next(), e.pen, e.color, e.width, it, false, line = e.line) }
                        }
                    }
                } else if (EraseMath.hitsElement(e, px, py, r)) { hit = true; eraseDirty = eraseDirty.union(e.bounds) }
            }
            if (hit && out == null) { out = ArrayList(src.size); for (j in 0 until i) out.add(src[j]) }
            if (out != null) { if (!hit) out.add(e) else if (repl != null) out.addAll(repl) }
        }
        return out
    }

    private fun eraseUp(cancel: Boolean) {
        flushErase(); eraseNextApply = 0L
        if (settings.eraserMode == 2) {
            val pi = lPage
            if (!cancel && ln >= 3 && pi in doc.pages.indices) {
                val page = doc.pages[pi]
                val poly = lp.copyOf(2 * ln)
                val ids = doc.content(page).elements.filter { !it.locked && it !is TapeEl && EraseMath.selectedByLasso(it, poly) }.map { it.id }.toSet()
                if (ids.isNotEmpty()) ed.exec(DocEdit.Elements(page.id, ElementsEdit.remove(doc.content(page), ids)))
            }
            ln = 0; lPage = -1
            return
        }
        for ((pid, start) in eraseStart) {
            val page = doc.page(pid) ?: continue
            val d = ElementsEdit.diff(start, doc.content(page))
            if (!d.isEmpty) ed.record(DocEdit.Elements(pid, d))
        }
        eraseStart.clear(); eraseLastPage = -1
    }

    // ───────────────────────── pędzel formatu i wypełnienie ─────────────────────────

    /** Pędzel formatu: jak gumka (obiektowa albo lasso), tylko zamiast usuwać nadaje styl aktywnego pióra. */
    private fun paintDown() {
        eraseStart.clear(); eraseLastPage = -1
        if (ed.formatPick) { takeFormatAt(); mode = Mode.IGNORE; return }
        if (ed.sel != null) ed.clearSel()
        if (ed.readOnly) { mode = Mode.IGNORE; return }
        if (settings.painterQuickFadeUse) ed.quickFormat = false
        if (settings.painterMode == 1) { val pi = pageIdxAt(curX, curY); if (pi < 0) { mode = Mode.IGNORE; return }; lPage = pi; ln = 0; addLasso(pgX(pi, curX), pgY(pi, curY)) }
        else paintTo(curX, curY)
    }

    /** „Pobierz format”: obiekt na wierzchu pod punktem dotyku (ostatni na liście leży najwyżej) ustawia aktywne pióro. */
    private fun takeFormatAt() {
        val pi = pageIdxAt(curX, curY)
        if (pi < 0) return
        val px = pgX(pi, curX); val py = pgY(pi, curY)
        val r = PAINT_R_DP * dp / zoom
        val els = doc.content(doc.pages[pi]).elements
        val top = els.lastOrNull { it !is TapeEl && it.bounds.inflate(r + 1f).contains(px, py) && EraseMath.hitsElement(it, px, py, r) }
        if (top == null) { ed.say(T.t("Dotknij obiektu, z którego chcesz pobrać format", "Tap the object to take the format from")); return }
        if (ed.takeFormat(top)) { ed.formatPick = false; if (settings.painterQuickFadeTake) ed.quickFormat = false }
        else ed.say(T.t("Ten obiekt nie ma formatu do pobrania", "This object has no format to take"))
    }

    private fun paintTo(sx: Float, sy: Float) {
        if (settings.painterMode == 1) { if (lPage >= 0) addLasso(pgX(lPage, sx), pgY(lPage, sy)); return }
        val pi = pageIdxAt(sx, sy)
        if (pi < 0) { eraseLastPage = -1; return }
        val px = pgX(pi, sx); val py = pgY(pi, sy)
        val r = PAINT_R_DP * dp / zoom
        if (eraseLastPage == pi) {
            val steps = ceil(hypot(px - eraseLx, py - eraseLy) / (r * 0.6f)).toInt().coerceAtLeast(1)
            for (k in 1..steps) { val f = k.toFloat() / steps; paintAt(pi, eraseLx + (px - eraseLx) * f, eraseLy + (py - eraseLy) * f, r) }
        } else paintAt(pi, px, py, r)
        eraseLastPage = pi; eraseLx = px; eraseLy = py
    }

    private fun paintAt(pi: Int, px: Float, py: Float, r: Float) {
        val page = doc.pages[pi]
        val cur = doc.content(page)
        val hits = cur.elements.filter { it.bounds.inflate(r + 1f).contains(px, py) && EraseMath.hitsElement(it, px, py, r) }
        if (hits.isEmpty()) return
        // Wypełnienie przejmuje styl tylko wtedy, gdy rysik jest na nim samym, a nie na linii leżącej na nim.
        val onInk = hits.any { it !is FillEl }
        val repl = HashMap<Element, Element>()
        for (e in hits) if (!(onInk && e is FillEl)) ed.painted(e)?.let { repl[e] = it }
        if (repl.isEmpty()) return
        eraseStart.getOrPut(page.id) { cur }
        doc.applyElements(page.id, ElementsEdit.diff(cur, PageContent.of(cur.elements.map { repl[it] ?: it })))
    }

    private fun paintUp(cancel: Boolean) {
        if (settings.painterMode == 1) {
            val pi = lPage
            if (!cancel && ln >= 3 && pi in doc.pages.indices) {
                val page = doc.pages[pi]
                val poly = lp.copyOf(2 * ln)
                val cur = doc.content(page)
                var changed = false
                val out = cur.elements.map { e -> if (EraseMath.selectedByLasso(e, poly)) ed.painted(e)?.also { changed = true } ?: e else e }
                if (changed) ed.exec(DocEdit.Elements(page.id, ElementsEdit.diff(cur, PageContent.of(out))))
            }
            ln = 0; lPage = -1
            return
        }
        for ((pid, start) in eraseStart) {
            val page = doc.page(pid) ?: continue
            val d = ElementsEdit.diff(start, doc.content(page))
            if (!d.isEmpty) ed.record(DocEdit.Elements(pid, d))
        }
        eraseStart.clear(); eraseLastPage = -1
    }

    /** Wiaderko: dotknięcie (bez przeciągania) wypełnia obszar wokół punktu. */
    private fun fillTap() {
        val pi = pageIdxAt(curX, curY)
        if (pi < 0 || ed.readOnly) return
        ed.fillAt(doc.pages[pi].id, pgX(pi, curX), pgY(pi, curY))
    }

    // ───────────────────────── lasso / zaznaczenie ─────────────────────────

    private fun addLasso(x: Float, y: Float) {
        if (ln > 0) {
            if (settings.lassoRect && lm == LM.DRAW) { if (ln >= 2) ln = 1 } else if (abs(lp[2 * ln - 2] - x) + abs(lp[2 * ln - 1] - y) < 0.4f / zoom) return
        }
        if (2 * ln + 2 > lp.size) lp = lp.copyOf(lp.size * 2)
        lp[2 * ln] = x; lp[2 * ln + 1] = y; ln++
    }

    private fun lassoDown() {
        val s = ed.sel
        if (s != null) {
            val pi = doc.indexOf(s.pageId)
            val b = selBounds()
            if (pi >= 0 && b != null) {
                lPage = pi
                val locked = ed.selElements().any { it.locked }
                val cs = selQuad(pi, b)
                val rot = rotateHandle(cs)
                val hr = 24f * dp
                val px = pgX(pi, curX); val py = pgY(pi, curY)
                if (!locked && ed.transformMode) {
                    if (hypot(curX - rot[0], curY - rot[1]) < ROTATE_HIT_DP * dp) {
                        prepareTransform(LM.ROTATE, s, b, px, py)
                        lAngleStart = atan2(py - b.cy, px - b.cx); return
                    }
                    val noScale = polySel() != null || shapeSel() != null
                    for (k in 0..3) if (!noScale && hypot(curX - cs[2 * k], curY - cs[2 * k + 1]) < hr) {
                        prepareTransform(LM.SCALE, s, b, px, py)
                        val ox = cs[(2 * ((k + 2) % 4))]; val oy = cs[2 * ((k + 2) % 4) + 1]
                        lAx = pgX(pi, ox); lAy = pgY(pi, oy)
                        lHx = pgX(pi, cs[2 * k]); lHy = pgY(pi, cs[2 * k + 1])
                        return
                    }
                }
                if (b.inflate(10f * dp / zoom).contains(px, py)) { prepareTransform(if (locked) LM.NONE else LM.MOVE, s, b, px, py); return }
            }
            ed.clearSel()
        }
        // Start poza stroną (ciemne tło, szczelina) liczy się do najbliższej strony: zaznaczanie przy krawędzi ma ruszać.
        val pi = layout.nearest(docX(curX), docY(curY))
        if (pi < 0) { mode = Mode.IGNORE; return }
        lPage = pi; lm = LM.DRAW; ln = 0
        addLasso(pgX(pi, curX), pgY(pi, curY))
    }

    /** Przytrzymanie lassa w miejscu (bez ruchu) otwiera menu kontekstowe — tam jest „Wklej” (jak w GoodNotes). */
    private fun lassoHold() {
        if (toolMoved || lLifting) return
        if (lm != LM.DRAW && lm != LM.MOVE && lm != LM.NONE) return
        if (lm == LM.DRAW && hypot(curX - downX, curY - downY) > slop) return
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        ln = 0; lm = LM.NONE
        openCtx(downX, downY)
        mode = Mode.IGNORE
    }

    private fun prepareTransform(kind: LM, s: Sel, b: Bounds, px: Float, py: Float) {
        lm = kind; lIds = s.ids; lBounds = b; lStartX = px; lStartY = py
        lM = Affine.IDENTITY; lLifting = false
    }

    private fun liftSelection() {
        val page = doc.pages.getOrNull(lPage) ?: return
        val orig = doc.content(page)
        lOrig = orig
        lEls = orig.elements.filter { it.id in lIds }
        doc.applyElements(page.id, ElementsEdit.remove(orig, lIds))
        lLifted = doc.content(page)
        lLifting = true
        lMShown = null
    }

    private fun drawGhost(c: Canvas) {
        if (ghostEls.isEmpty()) return
        val page = doc.pages.getOrNull(ghostPage)
        if (page == null || SystemClock.uptimeMillis() > ghostUntil || !cache.stale(page, ghostArea)) { ghostEls = emptyList(); return }
        val spec = page.spec
        c.save()
        c.translate(layout.left(ghostPage), layout.top(ghostPage))
        c.clipRect(0f, 0f, spec.w, spec.h)
        for (el in ghostEls) renderer.drawElement(c, doc.id, el, zoom, false, spec.darkPaper)
        c.restore()
        postInvalidateOnAnimation()
    }

    /** Zatwierdza [els] na stronie [pi] bez przemalowania kafelków na wątku UI: łaty idą w tle, a do tego czasu rysuje je [drawGhost]. */
    private fun applyInBackground(pi: Int, els: List<Element>, ghost: Boolean = true, apply: () -> Unit) {
        val page = doc.pages[pi]
        var area = Bounds.EMPTY
        for (e in els) area = area.union(e.bounds)
        applyDirty = area; applyPage = page.id
        apply()
        applyDirty = null; applyPage = null
        if (!ghost) return
        ghostEls = els; ghostPage = pi; ghostArea = area; ghostUntil = SystemClock.uptimeMillis() + GHOST_MAX_MS
    }

    /**
     * Podgląd zmiany wyglądu elementów [els] (suwak efektów obrazu): kafelki przemalowują się w tle, a wątek UI tylko
     * podmienia dokument. Duch zasłania stary wygląd tylko dla w pełni kryjących obrazów; półprzezroczysty
     * prześwitywałby przez stare kafelki, więc wtedy podgląd czeka na łatę.
     */
    fun applyElementsInBackground(pageId: String, els: List<Element>, apply: () -> Unit) {
        val pi = doc.indexOf(pageId)
        if (pi < 0) { apply(); return }
        applyInBackground(pi, els, ghost = els.all { it !is ImageEl || it.alpha >= 0.999f }, apply = apply)
    }

    private fun lassoMove() {
        if (lPage !in 0 until layout.count) return
        val px = pgX(lPage, curX); val py = pgY(lPage, curY)
        when (lm) {
            LM.DRAW -> addLasso(px, py)
            LM.MOVE, LM.SCALE, LM.ROTATE -> {
                if (!lLifting) { if (!toolMoved) return; liftSelection() }
                lM = when (lm) {
                    LM.MOVE -> {
                        var dx = px - lStartX; var dy = py - lStartY
                        if (settings.snapGrid || ed.gridSnap) { val g = 14.17f; dx = (dx / g).roundToInt() * g; dy = (dy / g).roundToInt() * g }
                        Affine.translation(dx, dy)
                    }
                    LM.SCALE -> {
                        val vx = lHx - lAx; val vy = lHy - lAy
                        val d2 = (vx * vx + vy * vy).coerceAtLeast(1e-3f)
                        val s = (((px - lAx) * vx + (py - lAy) * vy) / d2).coerceIn(0.05f, 30f)
                        Affine.scale(s, s, lAx, lAy)
                    }
                    else -> {
                        var a = atan2(py - lBounds.cy, px - lBounds.cx) - lAngleStart
                        val step = (PI / 12).toFloat()
                        val snapped = (a / step).roundToInt() * step
                        if (abs(snapped - a) < 0.07f) a = snapped
                        Affine.rotation(a, lBounds.cx, lBounds.cy)
                    }
                }
                ed.viewTick++
            }
            LM.VERTEX -> {
                if (!lLifting) { if (!toolMoved) return; liftSelection() }
                vertexDrag(px, py)
            }
            LM.RESIZE -> {
                if (!lLifting) { if (!toolMoved) return; liftSelection() }
                resizeDrag(px, py)
            }
            LM.NONE -> {}
        }
    }

    private fun lassoUp(cancel: Boolean) {
        if (lLifting) { commitTransform(cancel); return }
        val pi = lPage
        if (cancel || pi !in doc.pages.indices) { ln = 0; lm = LM.NONE; return }
        val page = doc.pages[pi]
        when (lm) {
            LM.DRAW -> {
                var len = 0f
                for (i in 1 until ln) len += hypot(lp[2 * i] - lp[2 * i - 2], lp[2 * i + 1] - lp[2 * i - 1])
                if (len * zoom < 14f * dp || ln < (if (settings.lassoRect) 2 else 3)) { lassoTap(page, pgX(pi, downX), pgY(pi, downY)); ln = 0; lm = LM.NONE; return }
                val poly: FloatArray = if (settings.lassoRect) {
                    val x0 = lp[0]; val y0 = lp[1]; val x1 = lp[2 * (ln - 1)]; val y1 = lp[2 * (ln - 1) + 1]
                    floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1)
                } else lp.copyOf(2 * ln)
                val ids = doc.content(page).elements.filter { !it.locked && EraseMath.selectedByLasso(it, poly) }.map { it.id }.toSet()
                ed.select(page.id, ids)
            }
            LM.MOVE, LM.SCALE, LM.ROTATE, LM.VERTEX, LM.RESIZE, LM.NONE -> if (!toolMoved) lassoTap(page, pgX(pi, curX), pgY(pi, curY))
        }
        ln = 0; lm = LM.NONE
    }

    /** Strona, na którą wyląduje przesuwany element (przeniesienie między stronami); przy innych przekształceniach — źródłowa. */
    private fun dropPage(): Int =
        if (lm == LM.MOVE) PageDrop.target(layout, lPage, lM.mapBounds(lBounds)) else lPage

    private fun commitTransform(cancel: Boolean) {
        val page = doc.pages.getOrNull(lPage)
        val orig = lOrig; val lifted = lLifted
        val vertex = lm == LM.VERTEX || lm == LM.RESIZE
        // Puszczenie palca nie przesuwa już obiektu: ruch z ostatniej, nienarysowanej paczki zdarzeń (drgnięcie przy
        // odrywaniu palca) pomijamy, więc obiekt zostaje dokładnie tam, gdzie był widoczny.
        if (!cancel && (lm == LM.MOVE || lm == LM.SCALE || lm == LM.ROTATE)) lMShown?.let { lM = it }
        val tgt = if (cancel) lPage else dropPage()
        lLifting = false
        lm = LM.NONE; ln = 0
        vBase = null; rFrame = null; tBase = null; shapeLabel = null
        if (page == null || orig == null || lifted == null) return
        val m = lM
        if (cancel || (m.isIdentity && !vertex)) {
            doc.applyElements(page.id, ElementsEdit.diff(lifted, orig))
        } else if (tgt != lPage) {
            val target = doc.pages[tgt]
            val moved = lEls.map { it.transformed(PageDrop.inTarget(layout, lPage, tgt, m)) }
            val remove = DocEdit.Elements(page.id, ElementsEdit.remove(orig, lIds))
            val add = DocEdit.Elements(target.id, ElementsEdit.add(doc.content(target), moved))
            applyInBackground(tgt, moved) { add.apply(doc) }
            ed.record(DocEdit.Group(listOf(remove, add)))
            ed.select(target.id, moved.map { it.id }.toSet())
        } else {
            val map = HashMap<Int, Element>()
            for (e in lEls) map[e.id] = if (vertex) e else e.transformed(m)
            val final = PageContent.of(orig.elements.map { map[it.id] ?: it })
            applyInBackground(lPage, lEls.map { map.getValue(it.id) }) { doc.applyElements(page.id, ElementsEdit.diff(lifted, final)) }
            ed.record(DocEdit.Elements(page.id, ElementsEdit.diff(orig, final)))
        }
        lOrig = null; lLifted = null; lEls = emptyList(); lM = Affine.IDENTITY
        ed.selTick++
    }

    private fun hitTop(page: PageState, px: Float, py: Float, slopDp: Float = 8f, skipLocked: Boolean = false): Element? {
        val els = doc.content(page).elements
        val slop = slopDp * dp / zoom
        for (i in els.indices.reversed()) {
            val e = els[i]
            if (skipLocked && e.locked) continue
            if (!e.bounds.inflate(slop + 2f).contains(px, py)) continue
            if (e is BoxElement) { if (e.containsPoint(px, py, slop)) return e }
            else if (EraseMath.hitsElement(e, px, py, slop)) return e
        }
        return null
    }

    /** Element pod dotknięciem: najpierw ścisłe trafienie, potem coraz szerszy margines (cienka kreska pod rysikiem/palcem). */
    private fun pickAt(page: PageState, px: Float, py: Float, skipLocked: Boolean = false): Element? {
        for (slopDp in TAP_SLOPS_DP) hitTop(page, px, py, slopDp, skipLocked)?.let { return it }
        return null
    }

    /** Dotknięcie w trybie ręki (rysik) lub palcem: zaznacza element pod spodem, puste miejsce zdejmuje zaznaczenie. */
    private fun tapSelect(sx: Float, sy: Float) {
        if (ed.readOnly) return
        val pi = pageIdxAt(sx, sy)
        if (pi < 0) { if (ed.sel != null) ed.clearSel(); return }
        val page = doc.pages[pi]
        val hit = pickAt(page, pgX(pi, sx), pgY(pi, sy), skipLocked = true)
        val s = ed.sel
        when {
            hit == null -> if (s != null) ed.clearSel()
            hit is TextBox && s != null && s.ids.size == 1 && hit.id in s.ids -> ed.editText(page.id, hit)
            else -> ed.select(page.id, setOf(hit.id))
        }
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /**
     * Czy dotyk w (sx, sy) łapie zaznaczenie: uchwyt wierzchołka łamanej, uchwyt obrotu, róg ramki, a przy [interior] także wnętrze ramki.
     * Nic nie zmienia — sam gest przejmuje potem lasso.
     */
    private fun selGrab(sx: Float, sy: Float): Boolean {
        val s = ed.sel ?: return false
        val pi = doc.indexOf(s.pageId); if (pi < 0) return false
        val els = ed.selElements()
        if (els.isEmpty() || els.any { it.locked }) return false
        val st = polySel()
        if (st != null) {
            for (i in 0 until st.vertexCount) if (hypot(sx - scrX(pi, st.pts[3 * i]), sy - scrY(pi, st.pts[3 * i + 1])) < 24f * dp) return true
        }
        val sh = shapeSel()
        if (sh != null && resizeHit(sh, pi, sx, sy) >= 0) return true
        val tx = textSel()
        if (tx != null && textHit(tx, pi, sx, sy) != null) return true
        val b = selBounds() ?: return false
        val cs = selQuad(pi, b)
        val rot = rotateHandle(cs)
        val hr = 24f * dp
        if (ed.transformMode) {
            if (hypot(sx - rot[0], sy - rot[1]) < ROTATE_HIT_DP * dp) return true
            if (st == null && sh == null) for (k in 0..3) if (hypot(sx - cs[2 * k], sy - cs[2 * k + 1]) < hr) return true
        }
        return b.inflate(10f * dp / zoom).contains(pgX(pi, sx), pgY(pi, sy))
    }

    private fun lassoTap(page: PageState, px: Float, py: Float) {
        val hit = pickAt(page, px, py)
        val s = ed.sel
        when {
            hit == null -> ed.clearSel()
            hit is TextBox && s != null && s.ids.size == 1 && hit.id in s.ids -> ed.editText(page.id, hit)
            else -> ed.select(page.id, setOf(hit.id))
        }
    }

    private fun textTap() {
        val pi = pageIdxAt(curX, curY)
        if (pi < 0) return
        val page = doc.pages[pi]
        val px = pgX(pi, curX); val py = pgY(pi, curY)
        val hit = hitTop(page, px, py)
        if (hit is TextBox) ed.editText(page.id, hit) else ed.newText(page.id, px, py)
    }

    private fun addLaser(sx: Float, sy: Float) {
        laser.add(docX(sx), docY(sy), 1.2f * dp / zoom, System.currentTimeMillis())
    }

    // ───────────────────────── menu kontekstowe ─────────────────────────

    private fun openCtx(sx: Float, sy: Float) {
        val pi = pageIdxAt(sx, sy)
        var px = 0f; var py = 0f
        var hasSel = ed.sel != null
        if (pi >= 0) {
            px = pgX(pi, sx); py = pgY(pi, sy)
            val page = doc.pages[pi]
            val hit = hitTop(page, px, py)
            val s = ed.sel
            if (hit != null && (s == null || hit.id !in s.ids)) { ed.tool = Tool.LASSO; ed.select(page.id, setOf(hit.id)); hasSel = true }
        }
        ed.ctxMenu = Editor.CtxMenu(sx, sy, pi, hasSel, px, py)
        invalidate()
    }

    companion object {
        private const val TAP_WINDOW = 320L     // najdłuższa przerwa między kolejnymi dotknięciami palcem (ms)
        private const val HOVER_STALE_MS = 1500L   // po tylu ms bez zdarzeń rysika uznajemy, że odjechał, nawet bez końca zawisu
        private const val TAP_NONE = 3          // „brak” na liście akcji dotknięcia
        private const val HOLD_DRIFT_DP = 10f   // promień dryfu, w którym przytrzymanie nie jest zerowane
        private const val SNAP_EQ_DP = 6f       // przyciąganie boków prostokąta do równych (kwadrat)
        private const val SNAP_DEAD_DP = 6f     // martwa strefa regulacji kształtu po rozpoznaniu
        /** Uchwyt obrotu leży daleko nad ramką, a jego strefa dotyku (20 dp) nie sięga strefy przesuwania (ramka + 10 dp). */
        const val ROTATE_OFFSET_DP = 52f
        private const val ROTATE_HIT_DP = 20f
        private val TAP_SLOPS_DP = floatArrayOf(8f, 14f, 22f)
        /** Promień pędzla formatu w trybie obiektowym (dp ekranu). */
        private const val PAINT_R_DP = 7f
        /** Gumka: przemalowanie do tylu ms nie wymusza przerw; dłuższe czeka ERASE_REST × własny czas (najwyżej ERASE_MAX_WAIT_MS). */
        private const val ERASE_FREE_MS = 8L
        private const val ERASE_REST = 2f
        private const val ERASE_MAX_WAIT_MS = 250L
        /** Najdłużej rysowany na wierzchu zatwierdzony obiekt, gdyby łata kafelka nie przyszła (kafelek usunięty z pamięci). */
        private const val GHOST_MAX_MS = 1500L
    }
}
