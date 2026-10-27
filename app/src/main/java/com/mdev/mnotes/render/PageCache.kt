package com.mdev.mnotes.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.mdev.mnotes.core.cache.Coalescer
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.layout.RenderProfile
import com.mdev.mnotes.core.layout.TileGrid
import com.mdev.mnotes.core.model.ColorInvert
import com.mdev.mnotes.core.model.ElementsEdit
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.data.NotebookDoc
import com.mdev.mnotes.data.NotebookMeta
import com.mdev.mnotes.data.PageState
import com.mdev.mnotes.data.Repository
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Rastrowa pamięć podręczna stron oparta na kafelkach (512 px) renderowanych w skali bieżącego powiększenia.
 *
 * Każda strona ma małą bitmapę „bazową” (cała strona, niska skala — natychmiastowy podkład) oraz zestaw poziomów
 * kafelków. Podczas ruchu rysujemy to, co już jest (starsze poziomy skalowane filtrem, a pod spodem baza); po ustaniu
 * zoomu (lub gdy poziom jest stabilny) kafelki w dokładnej skali wnikają płynnie (crossfade), więc nie ma skoku
 * rozdzielczości, a pamięć i koszt zależą od ekranu, nie od zoomu. Wątki robocze rysują zawsze nową bitmapę —
 * widoczna na ekranie zmienia się tylko na wątku UI (podmiana albo naniesienie świeżej kreski w miejscu).
 */
class PageCache(
    private val doc: NotebookDoc,
    private val assets: Assets,
    private val pdf: PdfPool,
    quality: Int = RenderProfile.BALANCED,
    private val onReady: () -> Unit,
) {
    private class Tile(val ix: Int, val iy: Int, val owner: Entry, val level: Level?) {
        var bmp: Bitmap? = null
        var bg: Bitmap? = null            // tło PDF (kafelek lub cała strona dla bazy), potrzebne do łatania w miejscu
        var bscale = 0f                   // skala bitmapy bazowej (kafelki mają skalę poziomu)
        var version = -1
        var readyAt = 0L
        var retryAt = 0L
        var state = IDLE
        @Volatile var stamp = 0L
        @Volatile var dist = 0f
        @Volatile var ver = 0
        @Volatile var rscale = 0f         // żądana skala bazy
        @Volatile var page: PageState? = null
        var pending = Bounds.EMPTY        // obszar (pt) zmieniony przez gumkę, jeszcze nie naniesiony na bitmapę
        var inflight = Bounds.EMPTY       // obszar łaty renderowanej właśnie w tle
        var touched = Bounds.EMPTY        // obszar zmieniony w miejscu w trakcie renderu łaty (łata byłaby starsza)
        val bytes get() = (bmp?.allocationByteCount ?: 0) + (bg?.allocationByteCount ?: 0)
    }

    private class Level(val scale: Float, val owner: Entry) {
        val tiles = HashMap<Int, Tile>()
    }

    private class Entry(val id: String) {
        val base: Tile = Tile(-1, -1, this, null)
        val levels = ArrayList<Level>(3)     // najnowszy pierwszy
    }

    private val entries = HashMap<String, Entry>()
    private val main = Handler(Looper.getMainLooper())
    private val ui = PageRenderer(assets)
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val basePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val layers = ArrayList<Level>(MAX_LEVELS)
    private val sorted = ArrayList<Level>(MAX_LEVELS)
    private val lock = Object()
    private val queue = ArrayList<Tile>()
    private val workers = List(WORKERS) { i ->
        Thread({ workerLoop(i) }, "mnotes-render-$i").apply { priority = Thread.NORM_PRIORITY - 2; isDaemon = true; start() }
    }
    private val tick = Runnable { onReady() }
    private var profile = RenderProfile.of(quality)
    @Volatile private var maxWorkers = profile.workers
    private val fadeNs get() = profile.fadeMs * 1_000_000L
    private var tileBytes = 0L
    private var baseBytes = 0L
    @Volatile private var closed = false
    @Volatile private var frame = 0L
    /** Jeden wątek renderuje łaty gumki; FIFO, więc widoczne kafelki (zlecane pierwsze) są gotowe najpierw. */
    private val patchExec = Executors.newSingleThreadExecutor { Thread(it, "mnotes-patch").apply { priority = Thread.NORM_PRIORITY - 1; isDaemon = true } }
    private val patchRenderer = PageRenderer(assets)
    private val patchRect = RectF()
    private val patchPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val srcPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC) }

    private var lastZoom = 0f
    private var zoomChangedAt = 0L
    private var prevZoom = 0f
    private var prevFrameNs = 0L
    private var vel = 0f            // ln(zoom)/s, wygładzona prędkość zoomu
    private var curZoom = 1f
    private var primary = 1f        // skala kafelków, które chcemy mieć w pierwszej kolejności
    private var headroom = 0f       // skala zapasowa (po ustaniu zoomu), by pierwsze przybliżenie było od razu ostre
    private var viewCx = 0f
    private var viewCy = 0f

    /** Zoom ustabilizował się — kafelki mają dokładną skalę, a widok może przyciąć origo strony do pikseli. */
    var settled = true
        private set

    /** Czy kafelki mają dokładną skalę widoku, więc origo strony warto przyciąć do pikseli (tylko tryby kafelkowe). */
    val pixelExact get() = settled && profile.tiled

    /** Element (strona, id), którego nie rysujemy: przycinany obraz widać wtedy tylko w edytorze przycinania. Czysto graficzne, dokument się nie zmienia. */
    @Volatile private var hidden: Pair<String, Int>? = null

    /** [refresh] = false przy odkrywaniu tuż przed edycją dokumentu: łata z tej edycji sama naniesie element, więc kafelki nie muszą się renderować od nowa (obraz nie znika na chwilę). */
    fun hide(pageId: String?, elementId: Int, refresh: Boolean = true) {
        val now = if (pageId == null) null else pageId to elementId
        val before = hidden
        if (now == before) return
        hidden = now
        if (!refresh) return
        for (id in listOfNotNull(before?.first, now?.first).distinct()) entries[id]?.let { e ->
            e.base.version = -1; e.base.pending = Bounds.EMPTY
            for (lv in e.levels) for (t in lv.tiles.values) { t.version = -1; t.pending = Bounds.EMPTY }
        }
        onReady()
    }

    /** Zawartość strony bez ukrytego elementu. */
    fun visible(pageId: String, content: PageContent): PageContent {
        val h = hidden ?: return content
        return if (h.first != pageId) content else PageContent.of(content.elements.filter { it.id != h.second })
    }

    /** Kod aktywnego profilu jakości. */
    val quality get() = profile.code

    /** Zmiana profilu w locie: bitmapy w innej skali/podziale są zwalniane i renderowane od nowa. */
    fun configure(code: Int) {
        if (code == profile.code) return
        profile = RenderProfile.of(code)
        maxWorkers = profile.workers
        for (e in entries.values) { for (lv in e.levels) release(lv); e.levels.clear(); drop(e.base) }
        synchronized(lock) { lock.notifyAll() }
        onReady()
    }

    // ───────────────────────── klatka ─────────────────────────

    /** Wołane raz na początku onDraw: [cx], [cy] = środek widoku w układzie dokumentu (priorytet kafelków). */
    fun beginFrame(zoom: Float, cx: Float, cy: Float) {
        frame++
        val now = System.nanoTime()
        if (prevFrameNs != 0L) {
            val dt = (now - prevFrameNs) / 1e9f
            vel = if (dt in 0.001f..0.12f) 0.6f * vel + 0.4f * ln(zoom / prevZoom) / dt else 0f
        }
        prevFrameNs = now; prevZoom = zoom
        if (lastZoom == 0f || abs(zoom / lastZoom - 1f) > 0.0004f) { lastZoom = zoom; zoomChangedAt = now }
        settled = now - zoomChangedAt >= SETTLE_NS
        curZoom = zoom
        if (settled) {
            primary = min(zoom, profile.maxScale)
            headroom = if (profile.headroom > 0f) min(TileGrid.level(zoom * profile.headroom, TileGrid.STEPS_MOTION), profile.maxScale) else 0f
        } else {
            // W ruchu renderujemy z wyprzedzeniem: przy przybliżaniu kafelki w skali, jaką zoom osiągnie za chwilę —
            // wtedy na ekranie są one tylko zmniejszane (ostre), a nie powiększane (rozmyte). Przy oddalaniu wystarczą starsze, większe.
            val ahead = when {
                profile.lookaheadS == 0f -> 1f
                vel > 0.05f -> max(1.15f, exp(min(vel * profile.lookaheadS, LN_MAX_AHEAD)))
                vel < -0.05f -> 1f
                else -> 1.1f
            }
            primary = min(TileGrid.level(zoom * ahead, TileGrid.STEPS_MOTION), profile.maxScale)
            headroom = 0f
            viewCx = cx; viewCy = cy
            main.removeCallbacks(tick)
            main.postDelayed(tick, (SETTLE_NS - (now - zoomChangedAt)) / 1_000_000L + 6L)
        }
        viewCx = cx; viewCy = cy
    }

    private fun entry(page: PageState): Entry = entries.getOrPut(page.id) { Entry(page.id) }

    private fun key(ix: Int, iy: Int) = iy * 4096 + ix

    /**
     * Rysuje stronę w jej układzie (canvas ma już początek w rogu strony, przycięty do strony): [vis] = widoczny
     * fragment strony (pt), [req] = fragment, dla którego przygotowujemy kafelki (z zapasem). Zwraca false, gdy nie
     * ma jeszcze żadnej bitmapy — wtedy widok rysuje wektorowo.
     */
    fun draw(c: Canvas, page: PageState, vis: Bounds, req: Bounds, left: Float, top: Float, zoom: Float): Boolean {
        val e = entry(page)
        want(page, e, vis, req, left, top)
        val spec = page.spec
        val now = System.nanoTime()
        val tilesOn = profile.tiled && primary > profile.baseScale * 1.03f
        layers.clear()
        var complete = false
        if (tilesOn) {
            // Poziomy od najlepiej dopasowanych do powiększenia; rysujemy aż do pierwszego, który w pełni pokrywa widok.
            sorted.clear()
            for (lv in e.levels) {
                val k = TileGrid.fit(lv.scale, zoom)
                var at = sorted.size
                while (at > 0 && TileGrid.fit(sorted[at - 1].scale, zoom) > k) at--
                sorted.add(at, lv)
            }
            for (lv in sorted) {
                layers.add(lv)
                if (covered(lv, spec, vis, now)) { complete = true; break }
            }
        } else if (e.levels.isNotEmpty()) {
            // Niskie powiększenie: wystarcza baza.
            for (lv in e.levels) release(lv)
            e.levels.clear()
        }
        var drew = false
        val b = e.base.bmp
        if (b != null && !complete) {
            rect.set(0f, 0f, spec.w, spec.h)
            basePaint.alpha = 255
            c.drawBitmap(b, null, rect, basePaint)
            drew = true
        }
        var fading = false
        for (k in layers.indices.reversed()) {   // najgorzej dopasowany na dół, najlepszy na wierzch
            val lv = layers[k]
            val under = drew || k < layers.size - 1
            val pxW = TileGrid.pixels(spec.w, lv.scale); val pxH = TileGrid.pixels(spec.h, lv.scale)
            val nx = TileGrid.count(pxW); val ny = TileGrid.count(pxH)
            val xs = TileGrid.range(vis.l, vis.r, lv.scale, nx)
            val ys = TileGrid.range(vis.t, vis.b, lv.scale, ny)
            val ext = if (abs(lv.scale / zoom - 1f) < 0.002f) 0f else 0.75f / zoom
            for (iy in ys) for (ix in xs) {
                val t = lv.tiles[key(ix, iy)] ?: continue
                val bm = t.bmp ?: continue
                var a = 255
                if (under && fadeNs > 0) {
                    val f = (now - t.readyAt).toFloat() / fadeNs
                    if (f < 1f) { a = (f.coerceAtLeast(0f) * 255f).toInt(); fading = true }
                }
                val ox = ix * TileGrid.TILE / lv.scale; val oy = iy * TileGrid.TILE / lv.scale
                rect.set(ox, oy, ox + bm.width / lv.scale + ext, oy + bm.height / lv.scale + ext)
                tilePaint.alpha = a
                c.drawBitmap(bm, null, rect, tilePaint)
                drew = true
            }
        }
        if (fading) onReady()
        return drew
    }

    /** Tylko przygotowanie kafelków (strony tuż poza ekranem). */
    fun request(page: PageState, req: Bounds, left: Float, top: Float) = want(page, entry(page), null, req, left, top)

    /** Czy wszystkie widoczne kafelki poziomu są gotowe i w pełni wnikły. */
    private fun covered(lv: Level, spec: PageSpec, vis: Bounds, now: Long): Boolean {
        val nx = TileGrid.count(TileGrid.pixels(spec.w, lv.scale)); val ny = TileGrid.count(TileGrid.pixels(spec.h, lv.scale))
        val xs = TileGrid.range(vis.l, vis.r, lv.scale, nx); val ys = TileGrid.range(vis.t, vis.b, lv.scale, ny)
        for (iy in ys) for (ix in xs) {
            val t = lv.tiles[key(ix, iy)] ?: return false
            if (t.bmp == null || now - t.readyAt < fadeNs) return false
        }
        return true
    }

    // ───────────────────────── żądania ─────────────────────────

    private fun want(page: PageState, e: Entry, vis: Bounds?, req: Bounds, left: Float, top: Float) {
        if (closed) return
        e.base.stamp = frame
        requestBase(page, e)
        if (!profile.tiled || primary <= profile.baseScale * 1.03f) return
        requestLevel(page, levelFor(e, primary), req, left, top, 0f)
        // Zapas: po ustaniu zoomu przygotowujemy w tle drugi, gęstszy poziom (tylko widoczne kafelki, najniższy priorytet).
        if (vis != null && headroom > primary * 1.03f) {
            requestLevel(page, levelFor(e, headroom), vis, left, top, HEADROOM_BIAS)
        }
    }

    /**
     * Skala bitmapy bazowej: w trybach kafelkowych stała; w klasycznym próg z [RenderProfile.classicScale], ale w ruchu
     * zostaje poprzednia bitmapa (skalowana przez GPU), a nowa jest renderowana dopiero po ustaniu zoomu.
     */
    private fun baseScaleFor(e: Entry, spec: PageSpec): Float {
        if (profile.tiled) return profile.baseScale
        if (e.base.bmp != null && !settled) return e.base.bscale
        return profile.classicScale(curZoom, spec.w, spec.h)
    }

    private fun requestBase(page: PageState, e: Entry) {
        val sc = baseScaleFor(e, page.spec)
        if (e.base.bmp == null || e.base.version != page.version || e.base.bscale != sc) enqueue(e.base, page, -1f, sc)
    }

    private fun levelFor(e: Entry, scale: Float): Level {
        for (lv in e.levels) if (abs(lv.scale / scale - 1f) < 0.002f) return lv
        val lv = Level(scale, e)
        e.levels.add(lv)
        while (e.levels.size > MAX_LEVELS) {
            var worst: Level? = null; var wk = -1f
            for (x in e.levels) {
                if (x === lv || abs(x.scale / primary - 1f) < 0.002f || (headroom > 0f && abs(x.scale / headroom - 1f) < 0.002f)) continue
                val k = TileGrid.fit(x.scale, curZoom)
                if (k > wk) { wk = k; worst = x }
            }
            if (worst == null) break
            release(worst); e.levels.remove(worst)
        }
        return lv
    }

    private fun requestLevel(page: PageState, lv: Level, area: Bounds, left: Float, top: Float, bias: Float) {
        val spec = page.spec
        val nx = TileGrid.count(TileGrid.pixels(spec.w, lv.scale)); val ny = TileGrid.count(TileGrid.pixels(spec.h, lv.scale))
        val xs = TileGrid.range(area.l, area.r, lv.scale, nx); val ys = TileGrid.range(area.t, area.b, lv.scale, ny)
        val now = System.nanoTime()
        for (iy in ys) for (ix in xs) {
            val t = lv.tiles.getOrPut(key(ix, iy)) { Tile(ix, iy, lv.owner, lv) }
            t.stamp = frame
            if (t.bmp != null && t.version == page.version) continue
            if (now < t.retryAt) continue
            val cx = left + (ix + 0.5f) * TileGrid.TILE / lv.scale; val cy = top + (iy + 0.5f) * TileGrid.TILE / lv.scale
            val dx = cx - viewCx; val dy = cy - viewCy
            enqueue(t, page, dx * dx + dy * dy + bias, 0f)
        }
    }

    private fun enqueue(t: Tile, page: PageState, dist: Float, scale: Float) {
        if (System.nanoTime() < t.retryAt) return
        synchronized(lock) {
            t.page = page; t.ver = page.version; t.dist = dist; t.rscale = scale
            if (t.state == IDLE) { t.state = QUEUED; queue.add(t); lock.notifyAll() }
        }
    }

    private fun workerLoop(index: Int) {
        val r = PageRenderer(assets)
        while (true) {
            val t: Tile
            synchronized(lock) {
                var pick: Tile? = null
                while (pick == null) {
                    if (closed) return
                    if (index >= maxWorkers) { lock.wait(); continue }
                    var best: Tile? = null
                    val it = queue.iterator()
                    while (it.hasNext()) {
                        val q = it.next()
                        if (q.stamp < frame - STALE_FRAMES) { q.state = IDLE; it.remove(); continue }
                        if (best == null || q.dist < best.dist) best = q
                    }
                    if (best != null) { queue.remove(best); best.state = RUNNING; pick = best } else lock.wait()
                }
                t = pick
            }
            val page = t.page
            val ver = t.ver
            val baseSc = t.rscale
            val hid = hidden
            var out: Bitmap? = null
            var bg: Bitmap? = null
            if (page != null) runCatching {
                val content = visible(page.id, doc.content(page))
                val spec = page.spec
                if (t.level == null) {
                    val w = TileGrid.pixels(spec.w, baseSc); val h = TileGrid.pixels(spec.h, baseSc)
                    bg = spec.pdf?.let { pdf.render(doc.id, it.asset, it.page, w, h) }
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    c.scale(w / spec.w, h / spec.h)
                    r.drawPaper(c, spec, baseSc, bg)
                    r.drawElements(c, doc.id, content, 0, null, baseSc, true, spec.darkPaper)
                    out = bmp
                } else {
                    val sc = t.level.scale
                    val w = TileGrid.size(t.ix, TileGrid.pixels(spec.w, sc)); val h = TileGrid.size(t.iy, TileGrid.pixels(spec.h, sc))
                    val oxPx = t.ix * TileGrid.TILE; val oyPx = t.iy * TileGrid.TILE
                    bg = spec.pdf?.let { pdf.renderTile(doc.id, it.asset, it.page, sc, oxPx, oyPx, w, h) }
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    if (bg != null) c.drawBitmap(bg!!, 0f, 0f, null)
                    c.scale(sc, sc)
                    c.translate(-oxPx / sc, -oyPx / sc)
                    if (bg == null) r.drawPaper(c, spec, sc, null)
                    val clip = Bounds(oxPx / sc, oyPx / sc, (oxPx + w) / sc, (oyPx + h) / sc).inflate(1.5f / sc)
                    r.drawElements(c, doc.id, content, 0, clip, sc, true, spec.darkPaper)
                    out = bmp
                }
            }
            val res = out; val resBg = bg
            main.post { finish(t, ver, baseSc, res, resBg, hid) }
        }
    }

    private fun alive(t: Tile): Boolean {
        val e = t.owner
        val lv = t.level
        return entries[e.id] === e && (lv == null || (e.levels.contains(lv) && lv.tiles[key(t.ix, t.iy)] === t))
    }

    private fun finish(t: Tile, ver: Int, baseSc: Float, bmp: Bitmap?, bg: Bitmap?, hid: Pair<String, Int>?) {
        t.state = IDLE
        if (closed) return
        if (!alive(t)) return
        val page = t.page
        if (bmp == null) { t.retryAt = System.nanoTime() + RETRY_NS; return }
        // Zmiana w trakcie renderu: wynik odrzucony. Przyjęcie nieaktualnego pokazywałoby na chwilę stan sprzed kilku
        // kroków (np. przesuwany obiekt w starym miejscu), a nowa bitmapa wnika z przenikaniem, spod którego widać bazę.
        // Wyjątek: gdy od startu renderu zmienił się tylko wygląd strony (suwak rozstawu zmienia go co kilkadziesiąt ms,
        // częściej niż trwa render), wynik przyjmujemy mimo starszej wersji, a kafelek zostanie odnowiony od nowa.
        // Odrzucanie wszystkiego zostawiałoby na ekranie stan sprzed gestu aż do puszczenia suwaka.
        if (page != null && ver >= page.contentVersion) {
            // Podmiana bitmapy, która już była, nie przenika: spod przenikania wyzierałaby baza w innej wersji.
            val swap = t.bmp != null && t.version >= page.contentVersion
            adopt(t, bmp, bg); t.bscale = baseSc; t.version = ver; t.readyAt = if (swap) 0L else System.nanoTime()
            if (hid != hidden) t.version = -1   // ukrycie zmieniło się w trakcie renderu: kafelek do odnowienia
            t.pending = Bounds.EMPTY
            trim()
        }
        onReady()
    }

    // ───────────────────────── edycje ─────────────────────────

    /**
     * Wołane z wątku UI po zmianie zawartości lub wyglądu strony: nanosi świeże zmiany na bitmapy w miejscu.
     * [dirty] = obszar do naniesienia w tle (gumka, zatwierdzone przesunięcie); dokładniejszy niż [ElementsEdit.dirtyBounds].
     *
     * Dopisana kreska i zwykła edycja (przesunięcie, zmiana rozmiaru, cofanie) trafiają na bitmapy od razu, żeby obiekt
     * w ruchu nie rozrywał się między kafelkami. Gumka tylko oznacza obszar do naniesienia: łatę renderuje wątek
     * w tle, a wątek UI ją jedynie nakleja ([landPatch]), więc ciężka strona (nakładające się zakreślacze) pokazuje
     * starcie z opóźnieniem, ale nie zatrzymuje aplikacji i nie podmienia całych kafelków. Tak samo idzie zatwierdzenie
     * przesuniętego obiektu (duże zdjęcie na wszystkich kafelkach zatrzymywało wątek UI na ułamek sekundy); do naniesienia
     * łat widok rysuje obiekt sam ([stale]).
     */
    fun onChanged(page: PageState, edit: ElementsEdit?, dirty: Bounds? = null) {
        val e = entries[page.id] ?: return
        val content = page.content ?: return
        if (edit == null) return
        val n = content.size
        val appended = edit.removed.isEmpty() && edit.added.isNotEmpty() && edit.added.all { it.index >= n - edit.added.size }
        val spec = page.spec
        if (dirty == null || dirty.isEmpty) {
            patch(e.base, page, edit, edit.dirtyBounds, appended, spec.w, spec.h, e.base.bscale, 0f, 0f)
            for (lv in e.levels) for (t in lv.tiles.values)
                patch(t, page, edit, edit.dirtyBounds, appended, spec.w, spec.h, lv.scale, t.ix * TileGrid.TILE / lv.scale, t.iy * TileGrid.TILE / lv.scale)
            return
        }
        val marked = ArrayList<Tile>()
        fun mark(t: Tile, sc: Float, ox: Float, oy: Float) {
            val bmp = t.bmp ?: return
            if (t.version != page.version - 1) return   // nieaktualny: zostanie wyrenderowany od nowa
            t.version = page.version
            val tw = if (t.level == null) spec.w else bmp.width / sc
            val th = if (t.level == null) spec.h else bmp.height / sc
            if (!dirty.inflate(2.5f / sc).intersects(ox, oy, ox + tw, oy + th)) return
            t.pending = t.pending.union(dirty)
            marked.add(t)
        }
        for (lv in e.levels) for (t in lv.tiles.values)
            if (t.stamp >= frame - STALE_FRAMES) mark(t, lv.scale, t.ix * TileGrid.TILE / lv.scale, t.iy * TileGrid.TILE / lv.scale)
        mark(e.base, e.base.bscale, 0f, 0f)
        for (lv in e.levels) for (t in lv.tiles.values)
            if (t.stamp < frame - STALE_FRAMES) mark(t, lv.scale, t.ix * TileGrid.TILE / lv.scale, t.iy * TileGrid.TILE / lv.scale)
        for (t in marked) schedulePatch(t, page)
    }

    /** Czy któraś bitmapa strony z ostatniej klatki pokazuje w [area] stan sprzed zmiany (czeka łata albo pełny render). */
    fun stale(page: PageState, area: Bounds): Boolean {
        val e = entries[page.id] ?: return false
        val spec = page.spec
        fun old(t: Tile) = t.version != page.version || !t.pending.isEmpty || !t.inflight.isEmpty
        val b = e.base
        if (e.levels.isEmpty() && b.bmp != null && old(b)) return true
        for (lv in e.levels) for (t in lv.tiles.values) {
            val bmp = t.bmp ?: continue
            if (t.stamp < frame || !old(t)) continue
            val ox = t.ix * TileGrid.TILE / lv.scale; val oy = t.iy * TileGrid.TILE / lv.scale
            if (area.intersects(ox, oy, min(spec.w, ox + bmp.width / lv.scale), min(spec.h, oy + bmp.height / lv.scale))) return true
        }
        return false
    }

    /** Zleca w tle render łaty [Tile.pending] w pikselach bitmapy kafelka; najwyżej jedna łata kafelka naraz (kolejność). */
    private fun schedulePatch(t: Tile, page: PageState) {
        if (!t.inflight.isEmpty || t.pending.isEmpty) return
        val bmp = t.bmp ?: run { t.pending = Bounds.EMPTY; return }
        val content = visible(page.id, page.content ?: return)
        val spec = page.spec
        val level = t.level
        val sc = level?.scale ?: t.bscale
        val sx = if (level == null) bmp.width / spec.w else sc
        val sy = if (level == null) bmp.height / spec.h else sc
        val ox = if (level == null) 0f else t.ix * TileGrid.TILE / sc
        val oy = if (level == null) 0f else t.iy * TileGrid.TILE / sc
        val area = t.pending
        t.pending = Bounds.EMPTY
        val d = area.inflate(2.5f / sc)
        val px0 = floor((d.l - ox) * sx).toInt().coerceIn(0, bmp.width)
        val py0 = floor((d.t - oy) * sy).toInt().coerceIn(0, bmp.height)
        val px1 = ceil((d.r - ox) * sx).toInt().coerceIn(0, bmp.width)
        val py1 = ceil((d.b - oy) * sy).toInt().coerceIn(0, bmp.height)
        if (px1 <= px0 || py1 <= py0) return
        t.inflight = area
        t.touched = Bounds.EMPTY
        val bg = t.bg
        patchExec.execute {
            val out = runCatching {
                val w = px1 - px0; val h = py1 - py0
                val pb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(pb)
                c.scale(sx, sy)
                // Przesunięcie o całe piksele: łata ma dokładnie te same piksele, które dałby render całego kafelka.
                val gx = ox + px0 / sx; val gy = oy + py0 / sy
                c.translate(-gx, -gy)
                val r = Bounds(gx, gy, gx + w / sx, gy + h / sy)
                if (level != null && spec.pdf != null) {
                    patchRenderer.drawPaper(c, spec, sc, null)
                    bg?.let { patchRect.set(ox, oy, ox + it.width / sx, oy + it.height / sy); c.drawBitmap(it, null, patchRect, patchPaint) }
                } else patchRenderer.drawPaper(c, spec, sc, bg)
                patchRenderer.drawElements(c, doc.id, content, 0, r, sc, false, spec.darkPaper)
                pb
            }.getOrNull()
            main.post { landPatch(t, page, bmp, out, px0, py0) }
        }
    }

    private fun landPatch(t: Tile, page: PageState, target: Bitmap, out: Bitmap?, px: Int, py: Int) {
        val area = t.inflight
        t.inflight = Bounds.EMPTY
        if (closed || !alive(t) || t.bmp !== target) return   // kafelek podmieniony całym, świeższym renderem
        when {
            out == null -> t.version = -1   // nieudana łata: cały kafelek od nowa
            // W trakcie renderu ten obszar zmieniono w miejscu — łata jest starsza, więc idzie od nowa ze świeżą treścią.
            t.touched.intersects(area) -> t.pending = t.pending.union(area)
            else -> Canvas(target).drawBitmap(out, px.toFloat(), py.toFloat(), srcPaint)
        }
        t.touched = Bounds.EMPTY
        if (t.version == page.version) schedulePatch(t, page) else t.pending = Bounds.EMPTY
        onReady()
    }

    private fun patch(t: Tile, page: PageState, edit: ElementsEdit, area: Bounds, appended: Boolean, pw: Float, ph: Float, sc: Float, ox: Float, oy: Float) {
        val bmp = t.bmp ?: return
        if (t.version != page.version - 1) return   // nieaktualny: zostanie wyrenderowany od nowa
        val content = visible(page.id, page.content ?: return)
        val spec = page.spec
        val changed = if (appended) edit.dirtyBounds else area
        if (!t.inflight.isEmpty) t.touched = t.touched.union(changed)
        // Zaległa łata gumki obejmie też ten obszar, żeby jej starsza treść nie przykryła zmiany.
        if (!t.pending.isEmpty || !t.inflight.isEmpty) t.pending = t.pending.union(changed)
        val sx = if (t.level == null) bmp.width / pw else sc
        val sy = if (t.level == null) bmp.height / ph else sc
        val tb = Bounds(ox, oy, ox + bmp.width / sx, oy + bmp.height / sy)
        val c = Canvas(bmp)
        c.scale(sx, sy)
        c.translate(-ox, -oy)
        if (appended) {
            for (p in edit.added.sortedBy { it.index }) {
                if (p.element.bounds.inflate(1.5f / sc).intersects(tb) && hidden != page.id to p.element.id) ui.drawElement(c, doc.id, p.element, sc, false, spec.darkPaper)
            }
        } else {
            val d = area.inflate(2.5f / sc)
            val r = Bounds(maxOf(0f, d.l, ox), maxOf(0f, d.t, oy), minOf(spec.w, d.r, tb.r), minOf(spec.h, d.b, tb.b))
            if (r.isEmpty) { t.version = page.version; return }
            c.save()
            c.clipRect(r.l, r.t, r.r, r.b)
            if (t.level != null && spec.pdf != null) {
                ui.drawPaper(c, spec, sc, null)
                t.bg?.let { rect.set(ox, oy, ox + it.width / sx, oy + it.height / sy); c.drawBitmap(it, null, rect, basePaint) }
            } else ui.drawPaper(c, spec, sc, t.bg)
            ui.drawElements(c, doc.id, content, 0, r, sc, false, spec.darkPaper)
            c.restore()
        }
        t.version = page.version
    }

    fun invalidateAll() {
        for (e in entries.values) {
            e.base.version = -1; e.base.pending = Bounds.EMPTY
            for (lv in e.levels) for (t in lv.tiles.values) { t.version = -1; t.pending = Bounds.EMPTY }
        }
    }

    fun forget(pageId: String) { entries.remove(pageId)?.let { for (lv in it.levels) release(lv); drop(it.base) } }

    // ───────────────────────── odczyt pikseli i tła ─────────────────────────

    /** Kolor piksela strony w punkcie ([x], [y]) pt z najlepszej dostępnej bitmapy; 0, gdy brak. */
    fun pixel(page: PageState, x: Float, y: Float): Int {
        val e = entries[page.id] ?: return 0
        val spec = page.spec
        for (lv in e.levels) {
            val ix = floor(x * lv.scale / TileGrid.TILE).toInt(); val iy = floor(y * lv.scale / TileGrid.TILE).toInt()
            val bm = lv.tiles[key(ix, iy)]?.bmp ?: continue
            return runCatching {
                bm.getPixel((x * lv.scale - ix * TileGrid.TILE).toInt().coerceIn(0, bm.width - 1), (y * lv.scale - iy * TileGrid.TILE).toInt().coerceIn(0, bm.height - 1))
            }.getOrDefault(0)
        }
        val b = e.base.bmp ?: return 0
        return runCatching {
            b.getPixel((x / spec.w * b.width).toInt().coerceIn(0, b.width - 1), (y / spec.h * b.height).toInt().coerceIn(0, b.height - 1))
        }.getOrDefault(0)
    }

    /** Tło PDF strony (niska skala) dla lupy; zleca renderowanie bazy, jeśli jej jeszcze nie ma. */
    /** Bitmapa całej strony w skali bazy (papier i treść) — do mini-mapy; null, dopóki baza się nie wyrenderuje. */
    fun overview(page: PageState): Bitmap? {
        val e = entry(page)
        e.base.stamp = frame
        requestBase(page, e)
        return e.base.bmp
    }

    fun backdrop(page: PageState): Bitmap? {
        val e = entry(page)
        e.base.stamp = frame
        requestBase(page, e)
        return e.base.bg
    }

    // ───────────────────────── pamięć ─────────────────────────

    /** Podpina bitmapy do kafelka, utrzymując sumy bajtów (bez skanowania wszystkich kafelków). */
    private fun adopt(t: Tile, bmp: Bitmap?, bg: Bitmap?) {
        account(t, -t.bytes)
        t.bmp = bmp; t.bg = bg
        account(t, t.bytes)
    }

    private fun drop(t: Tile) {
        account(t, -t.bytes)
        t.bmp = null; t.bg = null; t.version = -1; t.pending = Bounds.EMPTY
    }

    private fun account(t: Tile, d: Int) { if (t.level == null) baseBytes += d else tileBytes += d }

    private fun release(lv: Level) {
        for (t in lv.tiles.values) drop(t)
        lv.tiles.clear()
    }

    private fun trim() {
        if (tileBytes > profile.tileBytes) evict(false)
        if (baseBytes > profile.baseBytes) evict(true)
    }

    /** Zwalnia najdawniej używane bitmapy (nie te z ostatniej klatki), aż suma zmieści się w budżecie profilu. */
    private fun evict(bases: Boolean) {
        val list = ArrayList<Tile>()
        for (e in entries.values) {
            if (bases) { if (e.base.bmp != null) list.add(e.base) }
            else for (lv in e.levels) for (t in lv.tiles.values) if (t.bmp != null) list.add(t)
        }
        list.sortBy { it.stamp }
        for (t in list) {
            if ((if (bases) baseBytes else tileBytes) <= (if (bases) profile.baseBytes else profile.tileBytes)) break
            if (t.stamp >= frame - 1) break
            t.level?.tiles?.remove(key(t.ix, t.iy))
            drop(t)
        }
    }

    fun dispose() {
        closed = true
        main.removeCallbacks(tick)
        patchExec.shutdownNow()
        synchronized(lock) { queue.clear(); lock.notifyAll() }
        entries.clear()
    }

    companion object {
        const val WORKERS = 3
        const val MAX_LEVELS = 4
        const val HEADROOM_BIAS = 1e9f
        val LN_MAX_AHEAD = ln(4f)
        const val STALE_FRAMES = 2L
        const val SETTLE_NS = 130_000_000L
        const val RETRY_NS = 1_500_000_000L
        private const val IDLE = 0
        private const val QUEUED = 1
        private const val RUNNING = 2
    }
}

/** Miniatury stron i okładek (pamięć LRU + plik `thumb.png` dla pierwszej strony zeszytu). */
class Thumbs(private val repo: Repository, assets: Assets, private val pdf: PdfPool) {
    private val exec = Executors.newSingleThreadExecutor { Thread(it, "mnotes-thumbs").apply { priority = Thread.NORM_PRIORITY - 2 } }
    private val main = Handler(Looper.getMainLooper())
    private val renderer = PageRenderer(assets)
    private val mem = object : LruCache<String, Bitmap>(28 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val pending = Coalescer<String, Bitmap?>()

    fun cached(key: String): Bitmap? = mem.get(key)

    /** Uruchamia [work] w tle (jeśli nie ma wyniku w pamięci) i oddaje wynik na wątku UI — każdemu, kto o niego prosił. */
    fun async(key: String, work: () -> Bitmap?, done: (Bitmap?) -> Unit) {
        mem.get(key)?.let { done(it); return }
        if (!pending.join(key, done)) return
        exec.execute {
            val b = runCatching(work).getOrNull()
            main.post {
                if (b != null) mem.put(key, b)
                pending.finish(key, b)
            }
        }
    }

    /** Renderuje stronę do bitmapy o szerokości [wPx]. Wywoływać poza wątkiem UI. */
    fun render(nb: String, spec: PageSpec, content: PageContent, wPx: Int): Bitmap {
        val sc = wPx / spec.w
        val h = ceil(spec.h * sc).toInt().coerceAtLeast(1)
        val bg = spec.pdf?.let { pdf.render(nb, it.asset, it.page, wPx, h) }
        val bmp = Bitmap.createBitmap(wPx, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(sc, h / spec.h)
        renderer.drawPaper(c, spec, sc, bg)
        renderer.drawElements(c, nb, content, 0, null, sc, true, spec.darkPaper)
        if (!spec.inverted) return bmp
        // F11: miniatura (i okładka z pierwszej strony) w odwróconych kolorach, jak strona w edytorze.
        val inv = Bitmap.createBitmap(wPx, h, Bitmap.Config.ARGB_8888)
        Canvas(inv).drawBitmap(bmp, 0f, 0f, Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(ColorInvert.MATRIX)) })
        bmp.recycle()
        return inv
    }

    /** Okładka: miniatura pierwszej strony z pliku lub świeżo wyrenderowana (i zapisana). */
    fun cover(nb: NotebookMeta, wPx: Int): Bitmap? {
        val f: File = repo.thumbFile(nb.id)
        if (f.exists()) Assets.decodeFile(f, 1024)?.let { return it }
        val first = nb.pages.firstOrNull() ?: return null
        val bmp = render(nb.id, first.spec, repo.readPage(nb.id, first.id), wPx.coerceAtLeast(160))
        runCatching { f.parentFile?.mkdirs(); f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) } }
        return bmp
    }
}
