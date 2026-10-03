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
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.input.motionprediction.MotionEventPredictor
import com.mdev.mnotes.core.edit.EraseMath
import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.geom.Ruler
import com.mdev.mnotes.core.layout.DocLayout
import com.mdev.mnotes.core.layout.LayoutMode
import com.mdev.mnotes.core.layout.ZoomRegion
import com.mdev.mnotes.core.model.BoxElement
import com.mdev.mnotes.core.model.DocEdit
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.TapeEl
import com.mdev.mnotes.core.model.ElementsEdit
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PageSpace
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.pen.PenSpec
import com.mdev.mnotes.core.pen.StrokeBuilder
import com.mdev.mnotes.core.shapes.ShapeRecognizer
import com.mdev.mnotes.data.DocListener
import com.mdev.mnotes.data.PageState
import com.mdev.mnotes.render.PageCache
import com.mdev.mnotes.render.PageRenderer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

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

    private val cache = PageCache(doc, ed.app.assets, ed.app.pdf) { postInvalidateOnAnimation() }
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
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f * dp; pathEffect = DashPathEffect(floatArrayOf(7f * dp, 5f * dp), 0f) }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFFFFFFF.toInt() }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * dp }
    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f * dp; pathEffect = DashPathEffect(floatArrayOf(9f * dp, 6f * dp), 0f); strokeJoin = Paint.Join.ROUND }
    private val lassoFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.3f * dp }
    private val laserGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 14f * dp }
    private val laserCore = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 5f * dp }
    private val tmpPath = Path()
    private val tmpRect = RectF()
    private val tmpMatrix = Matrix()

    // — stan wejścia —
    private enum class Mode { NONE, IGNORE, NAV, TOOL, RULER, PICK }
    private enum class LM { NONE, DRAW, MOVE, SCALE, ROTATE }

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
    private var navTravel = 0f
    private var navT0 = 0L
    private var lastCx = 0f; private var lastCy = 0f; private var lastSpan = 0f
    private var lastTapT = 0L; private var lastTapX = 0f; private var lastTapY = 0f

    // pióro
    private var liveActive = false
    private var livePage = -1
    private var shapeSnapped = false
    private var shapePts = FloatArray(0)
    private var holdX = 0f; private var holdY = 0f
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
    private var lStartX = 0f; private var lStartY = 0f
    private var lAx = 0f; private var lAy = 0f; private var lHx = 0f; private var lHy = 0f
    private var lAngleStart = 0f
    private var sbSel: Sel? = null; private var sbKey = -1; private var sbVal: Bounds? = null

    // laser
    private var lasX = FloatArray(256); private var lasY = FloatArray(256); private var lasT = LongArray(256); private var lasN = 0

    private val holdRunnable = Runnable { if (mode == Mode.TOOL && activeTool == Tool.PEN && liveActive && !shapeSnapped) trySnapShape() }
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
        val mode = if (settings.layoutMode == 0) LayoutMode.VERTICAL else LayoutMode.HORIZONTAL
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
        return when (settings.layoutMode) {
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
        return when (settings.layoutMode) {
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
        val z = if (settings.layoutMode == 2) fitPageZoom(i) else baseZoom()
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
        removeCallbacks(holdRunnable); removeCallbacks(longPress)
        stopAnim()
        doc.listener = null
        ed.app.assets.onLoaded = null
        cache.dispose()
    }

    // ───────────────────────── DocListener ─────────────────────────

    override fun pageChanged(pageId: String, edit: ElementsEdit?) {
        val page = doc.page(pageId) ?: return
        if (edit == null) { rebuildLayout(); moved(); return }
        cache.onChanged(page, edit)
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
        c.drawColor(viewBg)
        if (doc.pages.isEmpty() || layout.count == 0) return
        val vb = Bounds(docX(0f), docY(0f), docX(width.toFloat()), docY(height.toFloat()))
        val range = layout.visibleRange(vb)
        shadowPaint.color = if (darkUi) 0x66000000 else 0x24000000
        borderPaint.color = if (darkUi) 0x33FFFFFF else 0x14000000
        borderPaint.strokeWidth = 1f / zoom

        val pullOff = pullOffset()
        if (pullOff > 0f) { c.save(); if (settings.layoutMode == 1) c.translate(-pullOff, 0f) else c.translate(0f, -pullOff) }
        c.save()
        c.translate(tx, ty); c.scale(zoom, zoom)
        for (i in range) drawPage(c, i, vb)
        val pre = if (mode == Mode.NAV || anim != null) 0 else 1
        if (pre > 0) {
            val n0 = range.first - 1; val n1 = range.last + 1
            if (n0 in 0 until layout.count) prefetch(n0)
            if (n1 in 0 until layout.count) prefetch(n1)
        }
        drawLive(c)
        c.restore()

        drawRuler(c)
        drawSpace(c)
        drawTapePreview(c)
        drawPicker(c)
        drawLasso(c)
        drawSelection(c)
        drawRing(c)
        drawSearch(c)
        if (pullOff > 0f) { c.restore(); drawPullHint(c, pullOff) }
        drawLaser(c)
        drawZoomWin(c)
    }

    private fun prefetch(i: Int) {
        val p = doc.pages[i]
        cache.entry(p, cache.scaleFor(zoom, p.spec), true)
    }

    private fun drawPage(c: Canvas, i: Int, vb: Bounds) {
        val page = doc.pages[i]; val spec = page.spec; val r = layout.rect(i)
        val o = 2f * dp / zoom
        c.drawRect(r.l - 0.5f * o, r.t, r.r + 0.5f * o, r.b + o, shadowPaint)
        c.save()
        c.translate(r.l, r.t)
        c.clipRect(0f, 0f, spec.w, spec.h)
        val e = cache.entry(page, cache.scaleFor(zoom, spec), true)
        val bmp = e.bmp
        if (bmp != null && (e.version == page.version || !cache.vectorMode(zoom, e.scale))) {
            tmpRect.set(0f, 0f, spec.w, spec.h)
            c.drawBitmap(bmp, null, tmpRect, bmpPaint)
        } else {
            renderer.drawPaper(c, spec, zoom, e.bg)
            val content = page.content
            if (content != null) renderer.drawElements(c, doc.id, content, 0, Bounds(vb.l - r.l, vb.t - r.t, vb.r - r.l, vb.b - r.t), zoom, false)
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
            if (shapeSnapped) renderer.drawStrokeData(c, builder.penKind, builder.penColor, builder.width, shapePts, shapePts.size / 3, zoom)
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
                    renderer.drawStrokeData(c, builder.penKind, builder.penColor, builder.width, scratch, total, zoom)
                } else renderer.drawStrokeData(c, builder.penKind, builder.penColor, builder.width, builder.pts, total, zoom)
            }
            c.restore()
        }
        if (lLifting && lPage in 0 until layout.count) {
            val spec = doc.pages[lPage].spec
            c.save()
            c.translate(layout.left(lPage), layout.top(lPage))
            c.clipRect(0f, 0f, spec.w, spec.h)
            c.concat(matrixOf(lM))
            val z = zoom * lM.scaleFactor
            for (el in lEls) renderer.drawElement(c, doc.id, el, z, false)
            c.restore()
        }
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
        val replaces = (activeTool == Tool.ERASER && settings.eraserMode == 2)
        if (mode != Mode.TOOL || lm != LM.DRAW && !replaces || ln == 0 || lPage !in 0 until layout.count) return
        val col = if (replaces) 0xFFE5484D.toInt() else accent
        lassoPaint.color = col
        lassoFill.color = (col and 0x00FFFFFF) or 0x22000000
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
        c.drawPath(tmpPath, lassoFill)
        c.drawPath(tmpPath, lassoPaint)
    }

    private fun cornersScreen(pi: Int, b: Bounds): FloatArray = floatArrayOf(
        scrX(pi, b.l), scrY(pi, b.t), scrX(pi, b.r), scrY(pi, b.t),
        scrX(pi, b.r), scrY(pi, b.b), scrX(pi, b.l), scrY(pi, b.b),
    )

    private fun drawSelection(c: Canvas) {
        val s = ed.sel ?: return
        val pi = doc.indexOf(s.pageId); if (pi < 0) return
        val cs: FloatArray
        if (lLifting) {
            val b = lBounds
            val m = lM
            cs = floatArrayOf(
                scrX(pi, m.mapX(b.l, b.t)), scrY(pi, m.mapY(b.l, b.t)), scrX(pi, m.mapX(b.r, b.t)), scrY(pi, m.mapY(b.r, b.t)),
                scrX(pi, m.mapX(b.r, b.b)), scrY(pi, m.mapY(b.r, b.b)), scrX(pi, m.mapX(b.l, b.b)), scrY(pi, m.mapY(b.l, b.b)),
            )
        } else cs = cornersScreen(pi, selBounds() ?: return)
        selPaint.color = accent
        handleStroke.color = accent
        tmpPath.rewind()
        tmpPath.moveTo(cs[0], cs[1]); tmpPath.lineTo(cs[2], cs[3]); tmpPath.lineTo(cs[4], cs[5]); tmpPath.lineTo(cs[6], cs[7]); tmpPath.close()
        c.drawPath(tmpPath, selPaint)
        val locked = ed.selElements().let { it.isNotEmpty() && it.all { e -> e.locked } }
        if (locked) return
        val r = 6.5f * dp
        for (k in 0..3) { c.drawCircle(cs[2 * k], cs[2 * k + 1], r, handleFill); c.drawCircle(cs[2 * k], cs[2 * k + 1], r, handleStroke) }
        val rot = rotateHandle(cs)
        val mx = (cs[0] + cs[2]) / 2; val my = (cs[1] + cs[3]) / 2
        c.drawLine(mx, my, rot[0], rot[1], handleStroke)
        c.drawCircle(rot[0], rot[1], r, handleFill); c.drawCircle(rot[0], rot[1], r, handleStroke)
    }

    private fun rotateHandle(cs: FloatArray): FloatArray {
        val mx = (cs[0] + cs[2]) / 2; val my = (cs[1] + cs[3]) / 2
        var nx = cs[1] - cs[3]; var ny = cs[2] - cs[0]   // normalna na zewnątrz górnej krawędzi
        val l = hypot(nx, ny).coerceAtLeast(0.001f)
        nx /= l; ny /= l
        return floatArrayOf(mx + nx * 34f * dp, my + ny * 34f * dp)
    }

    private fun drawRing(c: Canvas) {
        val t: Tool; val x: Float; val y: Float
        if (mode == Mode.TOOL && (activeTool == Tool.ERASER || activeTool == Tool.PEN) && toolType != MotionEvent.TOOL_TYPE_FINGER) { t = activeTool; x = curX; y = curY }
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
                val w = ed.preset.width * zoom
                ringPaint.color = ed.preset.color and 0x00FFFFFF or 0x99000000.toInt()
                c.drawCircle(x, y, max(w / 2f, 2.5f * dp), ringPaint)
            }
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
        if (b == null || settings.layoutMode == 2) { goToPage(pi); return }
        val cx = layout.left(pi) + (b.l + b.r) / 2; val cy = layout.top(pi) + (b.t + b.b) / 2
        animateView(zoom, clampX(width / 2f - cx * zoom, zoom), clampY(height / 2f - cy * zoom, zoom), 260L)
    }

    private fun drawLaser(c: Canvas) {
        if (lasN == 0) return
        val now = System.currentTimeMillis()
        var w = 0
        for (i in 0 until lasN) if (now - lasT[i] < LASER_MS) { lasX[w] = lasX[i]; lasY[w] = lasY[i]; lasT[w] = lasT[i]; w++ }
        lasN = w
        if (w == 0) return
        for (i in 1 until w) {
            val a = 1f - (now - lasT[i]).toFloat() / LASER_MS
            val x0 = lasX[i - 1] * zoom + tx; val y0 = lasY[i - 1] * zoom + ty
            val x1 = lasX[i] * zoom + tx; val y1 = lasY[i] * zoom + ty
            laserGlow.color = (((a * 70).toInt().coerceIn(0, 255)) shl 24) or 0xFF3B30
            laserCore.color = (((a * 255).toInt().coerceIn(0, 255)) shl 24) or 0xFF3B30
            c.drawLine(x0, y0, x1, y1, laserGlow)
            c.drawLine(x0, y0, x1, y1, laserCore)
        }
        val lx = lasX[w - 1] * zoom + tx; val ly = lasY[w - 1] * zoom + ty
        laserCore.color = 0xFFFF3B30.toInt()
        c.drawCircle(lx, ly, 6f * dp, laserCore.also { it.style = Paint.Style.FILL })
        laserCore.style = Paint.Style.STROKE
        postInvalidateOnAnimation()
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
        if (settings.layoutMode != 2 || layout.count == 0 || mode == Mode.NAV) return
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

    private fun rejectFinger(now: Long) = settings.palmRejection && (stylusNear || now - lastStylusT < 450L)

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
        if (isStylus(t)) { stylusNear = true; lastStylusT = ev.eventTime }
        if (ed.pipette != null) {
            if (t == MotionEvent.TOOL_TYPE_MOUSE && ev.buttonState and MotionEvent.BUTTON_SECONDARY != 0) { ed.cancelPipette(); mode = Mode.IGNORE; return }
            mode = Mode.PICK; pickX = ev.x; pickY = ev.y; pickColor = sampleColor(ev.x, ev.y)
            invalidate(); return
        }
        val rh = if (isStylus(t)) 0 else rulerHit(ev.x, ev.y)
        when {
            t == MotionEvent.TOOL_TYPE_MOUSE -> {
                val bs = ev.buttonState
                when {
                    bs and MotionEvent.BUTTON_SECONDARY != 0 -> { openCtx(ev.x, ev.y); mode = Mode.IGNORE }
                    bs and MotionEvent.BUTTON_TERTIARY != 0 -> startNav(ev)
                    ed.readOnly -> startNav(ev)
                    rh != 0 -> startRuler(ev, rh)
                    else -> beginTool(ev, 0)
                }
            }
            isStylus(t) -> if (ed.readOnly) startNav(ev) else beginTool(ev, 0)
            else -> {
                if (rejectFinger(ev.eventTime)) { mode = Mode.IGNORE; return }
                if (rh != 0) { startRuler(ev, rh); return }
                when (settings.fingerMode) {
                    0 -> startNav(ev)
                    1 -> if (ed.readOnly) startNav(ev) else beginTool(ev, 0)
                    else -> mode = Mode.IGNORE
                }
            }
        }
    }

    private fun onPointerDown(ev: MotionEvent) {
        val idx = ev.actionIndex
        val t = ev.getToolType(idx)
        when (mode) {
            Mode.TOOL -> if (toolType == MotionEvent.TOOL_TYPE_FINGER && t == MotionEvent.TOOL_TYPE_FINGER) { cancelTool(); startNav(ev) }
            Mode.NAV -> if (isStylus(t) && !ed.readOnly) { endNavQuiet(); beginTool(ev, idx) } else { navMax = max(navMax, ev.pointerCount); rebase(ev, -1) }
            Mode.RULER -> if (isStylus(t) && !ed.readOnly) { mode = Mode.NONE; rulerGrab = 0; beginTool(ev, idx) } else rulerRebase(ev, -1)
            Mode.PICK -> {}
            Mode.IGNORE, Mode.NONE -> {
                if (isStylus(t) && !ed.readOnly) { stylusNear = true; lastStylusT = ev.eventTime; beginTool(ev, idx) }
                else if (t == MotionEvent.TOOL_TYPE_FINGER && ev.pointerCount >= 2 && !rejectFinger(ev.eventTime) && settings.fingerMode != 1) startNav(ev)
            }
        }
    }

    private fun onMove(ev: MotionEvent) {
        when (mode) {
            Mode.NAV -> navMove(ev)
            Mode.RULER -> rulerMove(ev)
            Mode.PICK -> { pickX = ev.x; pickY = ev.y; pickColor = sampleColor(ev.x, ev.y); invalidate() }
            Mode.TOOL -> { val idx = ev.findPointerIndex(toolPid); if (idx >= 0) toolMove(ev, idx) }
            else -> {}
        }
    }

    private fun onPointerUp(ev: MotionEvent) {
        val idx = ev.actionIndex
        when (mode) {
            Mode.TOOL -> if (ev.getPointerId(idx) == toolPid) { finishTool(ev, idx, false); mode = Mode.IGNORE }
            Mode.NAV -> rebase(ev, idx)
            Mode.RULER -> rulerRebase(ev, idx)
            else -> {}
        }
    }

    private fun onUp(ev: MotionEvent, cancel: Boolean) {
        when (mode) {
            Mode.TOOL -> finishTool(ev, ev.findPointerIndex(toolPid).coerceAtLeast(0), cancel)
            Mode.NAV -> navUp(ev, cancel)
            Mode.PICK -> if (!cancel) pickDone()
            else -> {}
        }
        if (isStylus(ev.getToolType(0))) lastStylusT = ev.eventTime
        mode = Mode.NONE; toolPid = -1; rulerGrab = 0
        predictor?.let { predN = 0 }
        invalidate()
    }

    // ───────────────────────── nawigacja (palec / mysz) ─────────────────────────

    private fun startNav(ev: MotionEvent) {
        mode = Mode.NAV
        stopAnim()
        vt?.recycle(); vt = VelocityTracker.obtain().also { it.addMovement(ev) }
        navMax = ev.pointerCount; navTravel = 0f; navT0 = ev.eventTime
        pullAnim?.cancel(); pull = 0f; pullArmed = false
        pullEligible = ev.pointerCount == 1 && pullEnabled() && atDocEnd()
        rebase(ev, -1)
        removeCallbacks(longPress)
        if (ev.getToolType(0) != MotionEvent.TOOL_TYPE_MOUSE || true) postDelayed(longPress, 520L)
    }

    private fun endNavQuiet() { removeCallbacks(longPress); vt?.recycle(); vt = null; mode = Mode.NONE }

    private fun rebase(ev: MotionEvent, skip: Int) {
        var sx = 0f; var sy = 0f; var n = 0
        for (i in 0 until ev.pointerCount) { if (i == skip) continue; sx += ev.getX(i); sy += ev.getY(i); n++ }
        if (n == 0) return
        lastCx = sx / n; lastCy = sy / n
        lastSpan = 0f
        if (n >= 2) {
            var d = 0f
            for (i in 0 until ev.pointerCount) { if (i == skip) continue; d += hypot(ev.getX(i) - lastCx, ev.getY(i) - lastCy) }
            lastSpan = d / n
        }
    }

    private fun navMove(ev: MotionEvent) {
        vt?.addMovement(ev)
        var sx = 0f; var sy = 0f; val n = ev.pointerCount
        for (i in 0 until n) { sx += ev.getX(i); sy += ev.getY(i) }
        val cx = sx / n; val cy = sy / n
        var span = 0f
        if (n >= 2) { for (i in 0 until n) span += hypot(ev.getX(i) - cx, ev.getY(i) - cy); span /= n }
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
                        if (now - lastTapT < 320L && hypot(ev.x - lastTapX, ev.y - lastTapY) < 48f * dp) { toggleZoom(ev.x, ev.y); lastTapT = 0L }
                        else { lastTapT = now; lastTapX = ev.x; lastTapY = ev.y }
                    }
                    2 -> if (settings.twoFingerUndo) ed.undo()
                    3 -> if (settings.twoFingerUndo) ed.redo()
                }
            } else if (navMax == 1 && tracker != null) {
                tracker.addMovement(ev)
                tracker.computeCurrentVelocity(1000, cfg.scaledMaximumFlingVelocity.toFloat())
                val vx = tracker.xVelocity; val vy = tracker.yVelocity
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
        if (settings.layoutMode == 1) clampX(Float.NEGATIVE_INFINITY, zoom) else clampY(Float.NEGATIVE_INFINITY, zoom)

    /** Czy widok stoi na końcu dokumentu (dół albo prawa krawędź w układzie poziomym). */
    private fun atDocEnd(): Boolean {
        val cur = if (settings.layoutMode == 1) tx else ty
        return cur <= pullLimit() + 4f * dp
    }

    private fun pullOffset(): Float {
        if (pull <= 0f) return 0f
        val cap = 100f * dp
        return cap * (1f - exp(-pull / (cap * 1.3f)))
    }

    /** Przesuwa widok o (dx, dy); nadmiar poza koniec dokumentu zamienia w odciągnięcie. */
    private fun panWithPull(dx: Float, dy: Float, pullOk: Boolean) {
        val ax = settings.layoutMode == 1
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
        val ax = settings.layoutMode == 1
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
        val b = cache.entry(page, cache.scaleFor(zoom, spec), false).bmp ?: return 0
        return runCatching {
            val px = (pgX(pi, sx) / spec.w * b.width).toInt().coerceIn(0, b.width - 1)
            val py = (pgY(pi, sy) / spec.h * b.height).toInt().coerceIn(0, b.height - 1)
            b.getPixel(px, py) or 0xFF000000.toInt()
        }.getOrDefault(0)
    }

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

    // ───────────────────────── narzędzia: wspólne ─────────────────────────

    private fun beginTool(ev: MotionEvent, idx: Int) {
        toolPid = ev.getPointerId(idx); toolType = ev.getToolType(idx)
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS && ev.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 && settings.penButton == 2) {
            ed.undo(); mode = Mode.IGNORE; return
        }
        val tool = effectiveTool(ev, idx)
        activeTool = tool
        if (tool == Tool.HAND) { startNav(ev); return }
        mode = Mode.TOOL
        curX = ev.getX(idx); curY = ev.getY(idx)
        downX = curX; downY = curY; downT = ev.eventTime; toolMoved = false
        lm = LM.NONE; ln = 0
        when (tool) {
            Tool.PEN -> penDown(ev, idx)
            Tool.ERASER -> eraseDown()
            Tool.LASSO -> lassoDown()
            Tool.SPACE -> spaceDown()
            Tool.TAPE -> tapeDown()
            Tool.LASER -> { lasN = 0; addLaser(curX, curY) }
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
            Tool.TEXT -> if (!cancel && !toolMoved) textTap()
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
        val p = ed.preset
        val sp = PenSpec.of(p.kind)
        builder.begin(p.kind, p.color, p.width, (sp.smoothing * settings.smoothing).coerceIn(0f, 1f), (0.5f / zoom).coerceIn(0.05f, 0.9f))
        liveActive = true; shapeSnapped = false; predN = 0
        holdX = curX; holdY = curY
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
        if (!liveActive || shapeSnapped) return
        feedPen(ev, idx)
        if (hypot(curX - holdX, curY - holdY) > 3f * dp) { holdX = curX; holdY = curY; armHold() }
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
        val n = builder.count
        if (n < 8) return
        val xy = FloatArray(2 * n)
        for (i in 0 until n) { xy[2 * i] = builder.pts[3 * i]; xy[2 * i + 1] = builder.pts[3 * i + 1] }
        val shape = ShapeRecognizer.recognize(xy, n) ?: return
        val m = shape.xy.size / 2
        shapePts = FloatArray(3 * m)
        for (i in 0 until m) { shapePts[3 * i] = shape.xy[2 * i]; shapePts[3 * i + 1] = shape.xy[2 * i + 1]; shapePts[3 * i + 2] = 1f }
        shapeSnapped = true; predN = 0
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        invalidate()
    }

    private fun penUp(cancel: Boolean) {
        if (!liveActive) return
        liveActive = false; rulerSide = 0
        val pi = livePage
        val stroke = if (cancel) null else if (shapeSnapped) Stroke(Ids.next(), builder.penKind, builder.penColor, builder.width, shapePts, false) else builder.finish(Ids.next())
        shapeSnapped = false
        if (stroke == null || pi !in doc.pages.indices) return
        val page = doc.pages[pi]
        ed.exec(DocEdit.Elements(page.id, ElementsEdit.add(doc.content(page), listOf(stroke))))
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
        val bottom = height - (if (settings.toolbarPos == 1 && ed.chrome) 76f * dp else 8f * dp)
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
        val e = cache.entry(page, cache.scaleFor(zoom, spec), true)
        renderer.drawPaper(c, spec, z, e.bg)
        renderer.drawElements(c, doc.id, doc.content(page), 0, Bounds(zw.x, zw.y, zw.x + rw, zw.y + rh), z, false)
        if (liveActive && livePage == zwPage) {
            if (shapeSnapped) renderer.drawStrokeData(c, builder.penKind, builder.penColor, builder.width, shapePts, shapePts.size / 3, z)
            else renderer.drawStrokeData(c, builder.penKind, builder.penColor, builder.width, builder.pts, builder.count, z)
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
        if (eraseLastPage == pi) {
            val steps = ceil(hypot(px - eraseLx, py - eraseLy) / (r * 0.6f)).toInt().coerceAtLeast(1)
            for (k in 1..steps) { val f = k.toFloat() / steps; eraseAt(pi, eraseLx + (px - eraseLx) * f, eraseLy + (py - eraseLy) * f) }
        } else eraseAt(pi, px, py)
        eraseLastPage = pi; eraseLx = px; eraseLy = py
    }

    private fun eraseAt(pi: Int, px: Float, py: Float) {
        val page = doc.pages[pi]
        val cur = doc.content(page)
        val r = settings.eraserSize
        val keepHl = settings.eraserKeepHighlighter
        val pixel = settings.eraserMode == 1
        val out = ArrayList<Element>(cur.size)
        var changed = false
        for (e in cur.elements) {
            if (e.locked || e is TapeEl || (keepHl && e is Stroke && e.pen == PenKind.HIGHLIGHTER) || !e.bounds.inflate(r + 1f).contains(px, py)) { out.add(e); continue }
            if (pixel) {
                if (e is Stroke) {
                    val pieces = EraseMath.erasePartial(e, px, py, r)
                    if (pieces == null) out.add(e)
                    else { changed = true; for (pc in pieces) out.add(Stroke(Ids.next(), e.pen, e.color, e.width, pc, false)) }
                } else out.add(e)
            } else if (EraseMath.hitsElement(e, px, py, r)) changed = true else out.add(e)
        }
        if (!changed) return
        eraseStart.getOrPut(page.id) { cur }
        doc.applyElements(page.id, ElementsEdit.diff(cur, PageContent.of(out)))
    }

    private fun eraseUp(cancel: Boolean) {
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
                val cs = cornersScreen(pi, b)
                val rot = rotateHandle(cs)
                val hr = 24f * dp
                val px = pgX(pi, curX); val py = pgY(pi, curY)
                if (!locked) {
                    if (hypot(curX - rot[0], curY - rot[1]) < hr) {
                        prepareTransform(LM.ROTATE, s, b, px, py)
                        lAngleStart = atan2(py - b.cy, px - b.cx); return
                    }
                    for (k in 0..3) if (hypot(curX - cs[2 * k], curY - cs[2 * k + 1]) < hr) {
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
        val pi = pageIdxAt(curX, curY)
        if (pi < 0) { mode = Mode.IGNORE; return }
        lPage = pi; lm = LM.DRAW; ln = 0
        addLasso(pgX(pi, curX), pgY(pi, curY))
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
                if (len * zoom < 14f * dp || ln < 3) { lassoTap(page, pgX(pi, downX), pgY(pi, downY)); ln = 0; lm = LM.NONE; return }
                val poly: FloatArray = if (settings.lassoRect) {
                    val x0 = lp[0]; val y0 = lp[1]; val x1 = lp[2 * (ln - 1)]; val y1 = lp[2 * (ln - 1) + 1]
                    floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1)
                } else lp.copyOf(2 * ln)
                val ids = doc.content(page).elements.filter { !it.locked && EraseMath.selectedByLasso(it, poly) }.map { it.id }.toSet()
                ed.select(page.id, ids)
            }
            LM.MOVE, LM.SCALE, LM.ROTATE, LM.NONE -> if (!toolMoved) lassoTap(page, pgX(pi, curX), pgY(pi, curY))
        }
        ln = 0; lm = LM.NONE
    }

    private fun commitTransform(cancel: Boolean) {
        val page = doc.pages.getOrNull(lPage)
        val orig = lOrig; val lifted = lLifted
        lLifting = false
        lm = LM.NONE; ln = 0
        if (page == null || orig == null || lifted == null) return
        val m = lM
        if (cancel || m.isIdentity) {
            doc.applyElements(page.id, ElementsEdit.diff(lifted, orig))
        } else {
            val map = HashMap<Int, Element>()
            for (e in lEls) map[e.id] = e.transformed(m)
            val final = PageContent.of(orig.elements.map { map[it.id] ?: it })
            doc.applyElements(page.id, ElementsEdit.diff(lifted, final))
            ed.record(DocEdit.Elements(page.id, ElementsEdit.diff(orig, final)))
        }
        lOrig = null; lLifted = null; lEls = emptyList(); lM = Affine.IDENTITY
        ed.selTick++
    }

    private fun hitTop(page: PageState, px: Float, py: Float): Element? {
        val els = doc.content(page).elements
        val slop = 8f * dp / zoom
        for (i in els.indices.reversed()) {
            val e = els[i]
            if (!e.bounds.inflate(slop + 2f).contains(px, py)) continue
            if (e is BoxElement) { if (e.containsPoint(px, py, slop)) return e }
            else if (EraseMath.hitsElement(e, px, py, slop)) return e
        }
        return null
    }

    private fun lassoTap(page: PageState, px: Float, py: Float) {
        val hit = hitTop(page, px, py)
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
        if (lasN == lasX.size) {
            val h = lasN / 2
            System.arraycopy(lasX, h, lasX, 0, lasN - h); System.arraycopy(lasY, h, lasY, 0, lasN - h); System.arraycopy(lasT, h, lasT, 0, lasN - h)
            lasN -= h
        }
        lasX[lasN] = docX(sx); lasY[lasN] = docY(sy); lasT[lasN] = System.currentTimeMillis(); lasN++
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
        private const val LASER_MS = 900L
    }
}
