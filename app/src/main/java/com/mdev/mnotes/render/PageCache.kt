package com.mdev.mnotes.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.model.ElementsEdit
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.data.NotebookDoc
import com.mdev.mnotes.data.NotebookMeta
import com.mdev.mnotes.data.PageState
import com.mdev.mnotes.data.Repository
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Rastrowa pamięć podręczna stron: jedna bitmapa ARGB na stronę, renderowana w tle.
 * Małe zmiany (nowa kreska, gumka, cofnięcie) nanosimy synchronnie na istniejącą bitmapę — bez migotania.
 * Wątek renderujący nigdy nie dotyka bitmapy widocznej na ekranie: zawsze rysuje nową i podmienia ją na wątku UI.
 */
class PageCache(
    private val doc: NotebookDoc,
    private val assets: Assets,
    private val pdf: PdfPool,
    private val onReady: () -> Unit,
) {
    class Entry(val id: String) {
        var bmp: Bitmap? = null
        var bg: Bitmap? = null
        var scale = 0f
        var version = -1
        var inflight = false
        var want = 0f
        var lastUse = 0L
    }

    private val entries = HashMap<String, Entry>()
    private val exec = Executors.newSingleThreadExecutor { Thread(it, "mnotes-render").apply { priority = Thread.NORM_PRIORITY - 2 } }
    private val main = Handler(Looper.getMainLooper())
    private val ui = PageRenderer(assets)
    private val worker = PageRenderer(assets)
    private var closed = false

    fun scaleFor(zoom: Float, spec: PageSpec): Float {
        val budget = sqrt(MAX_PIXELS / (spec.w * spec.h))
        val s = min(zoom, MAX_SCALE).coerceAtLeast(BUCKETS[0])
        for (b in BUCKETS) if (b >= s * 0.999f) return min(b, budget)
        return min(MAX_SCALE, budget)
    }

    /** Czy przy tym powiększeniu bitmapa byłaby zbyt rozmyta i trzeba rysować wektorowo. */
    fun vectorMode(zoom: Float, scale: Float) = zoom > scale * 1.22f

    fun entry(page: PageState, scale: Float, request: Boolean): Entry {
        val e = entries.getOrPut(page.id) { Entry(page.id) }
        e.lastUse = System.nanoTime()
        e.want = scale
        if (request) ensure(page, e)
        return e
    }

    private fun ensure(page: PageState, e: Entry) {
        if (closed || e.inflight) return
        if (e.bmp != null && e.version == page.version && e.scale == e.want) return
        e.inflight = true
        val ver = page.version
        val spec = page.spec
        val sc = e.want
        exec.execute {
            val result = runCatching {
                val content = doc.content(page)
                val w = ceil(spec.w * sc).toInt().coerceAtLeast(1)
                val h = ceil(spec.h * sc).toInt().coerceAtLeast(1)
                val bg = spec.pdf?.let { pdf.render(doc.id, it.asset, it.page, w, h) }
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                c.scale(w / spec.w, h / spec.h)
                worker.drawPaper(c, spec, sc, bg)
                worker.drawElements(c, doc.id, content, 0, null, sc, true)
                bmp to bg
            }.getOrNull()
            main.post { finish(page, e, ver, sc, result?.first, result?.second) }
        }
    }

    private fun finish(page: PageState, e: Entry, ver: Int, sc: Float, bmp: Bitmap?, bg: Bitmap?) {
        e.inflight = false
        if (closed || entries[page.id] !== e) return
        if (bmp != null && ver == page.version) {
            e.bmp = bmp; e.bg = bg; e.scale = sc; e.version = ver
            trim()
            onReady()
        }
        // Zmiana w trakcie renderu albo błąd: wynik odrzucony, ew. ponowne żądanie zrobi następne rysowanie widoku.
        if (e.bmp == null || e.version != page.version || e.scale != e.want) onReady()
    }

    /** Wołane z wątku UI po zmianie zawartości lub wyglądu strony. */
    fun onChanged(page: PageState, edit: ElementsEdit?) {
        val e = entries[page.id] ?: return
        val bmp = e.bmp
        if (edit == null || bmp == null || e.version != page.version - 1) return
        val content = page.content ?: return
        val spec = page.spec
        val sc = e.scale
        val c = Canvas(bmp)
        c.scale(bmp.width / spec.w, bmp.height / spec.h)
        val n = content.size
        val appended = edit.removed.isEmpty() && edit.added.isNotEmpty() && edit.added.all { it.index >= n - edit.added.size }
        if (appended) {
            for (p in edit.added.sortedBy { it.index }) ui.drawElement(c, doc.id, p.element, sc, false)
        } else {
            val d = edit.dirtyBounds.inflate(2.5f / sc)
            val r = Bounds(maxOf(0f, d.l), maxOf(0f, d.t), minOf(spec.w, d.r), minOf(spec.h, d.b))
            if (r.isEmpty) { e.version = page.version; return }
            c.save()
            c.clipRect(r.l, r.t, r.r, r.b)
            ui.drawPaper(c, spec, sc, e.bg)
            ui.drawElements(c, doc.id, content, 0, r, sc, false)
            c.restore()
        }
        e.version = page.version
    }

    fun invalidateAll() { for (e in entries.values) e.version = -1 }

    fun forget(pageId: String) { entries.remove(pageId) }

    private fun trim() {
        var total = 0L
        for (e in entries.values) total += (e.bmp?.allocationByteCount ?: 0) + (e.bg?.allocationByteCount ?: 0)
        if (total <= MAX_BYTES) return
        val now = System.nanoTime()
        val victims = entries.values.filter { it.bmp != null && now - it.lastUse > 700_000_000L }.sortedBy { it.lastUse }
        for (v in victims) {
            if (total <= MAX_BYTES) break
            total -= (v.bmp?.allocationByteCount ?: 0) + (v.bg?.allocationByteCount ?: 0)
            v.bmp = null; v.bg = null; v.version = -1
        }
    }

    fun dispose() {
        closed = true
        exec.shutdown()
        entries.clear()
    }

    companion object {
        const val MAX_SCALE = 4.4f
        const val MAX_PIXELS = 10_200_000f
        const val MAX_BYTES = 300L * 1024 * 1024
        val BUCKETS = floatArrayOf(0.3f, 0.45f, 0.65f, 0.9f, 1.25f, 1.75f, 2.4f, 3.2f, 4.4f)
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
    private val pending = HashSet<String>()

    fun cached(key: String): Bitmap? = mem.get(key)

    /** Uruchamia [work] w tle (jeśli nie ma wyniku w pamięci) i oddaje wynik na wątku UI. */
    fun async(key: String, work: () -> Bitmap?, done: (Bitmap?) -> Unit) {
        mem.get(key)?.let { done(it); return }
        if (!pending.add(key)) return
        exec.execute {
            val b = runCatching(work).getOrNull()
            main.post {
                pending.remove(key)
                if (b != null) mem.put(key, b)
                done(b)
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
        renderer.drawElements(c, nb, content, 0, null, sc, true)
        return bmp
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
