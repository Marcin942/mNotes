package com.mdev.mnotes.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import com.mdev.mnotes.data.Repository
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max

/** Obrazy z zasobów zeszytu: dekodowanie z próbkowaniem, EXIF i podręczna pamięć LRU ograniczona bajtami. */
class Assets(private val repo: Repository) {
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(2) { Thread(it, "mnotes-decode").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val pending = HashSet<String>()
    private val lru = object : LinkedHashMap<String, Bitmap>(32, 0.75f, true) {}
    private var bytes = 0L
    var onLoaded: (() -> Unit)? = null

    @Synchronized private fun getCached(key: String): Bitmap? = lru[key]

    @Synchronized private fun put(key: String, b: Bitmap) {
        lru.put(key, b)?.let { bytes -= it.allocationByteCount.toLong() }
        bytes += b.allocationByteCount
        val it = lru.entries.iterator()
        while (bytes > MAX_BYTES && it.hasNext()) {
            val e = it.next()
            if (e.value === b) continue
            bytes -= e.value.allocationByteCount
            it.remove()
        }
    }

    /** Zwraca obraz, jeśli jest w pamięci; w przeciwnym razie (gdy [decode]=false) uruchamia dekodowanie w tle i zwraca null. */
    fun bitmap(nb: String, name: String, decode: Boolean): Bitmap? {
        val key = "$nb/$name"
        getCached(key)?.let { return it }
        if (decode) return load(nb, name)?.also { put(key, it) }
        synchronized(pending) { if (!pending.add(key)) return null }
        pool.execute {
            val b = load(nb, name)
            if (b != null) put(key, b)
            synchronized(pending) { pending.remove(key) }
            if (b != null) main.post { onLoaded?.invoke() }
        }
        return null
    }

    fun load(nb: String, name: String, maxDim: Int = MAX_DIM): Bitmap? = decodeFile(repo.assetFile(nb, name), maxDim)

    fun drop(nb: String, name: String) = synchronized(this) { lru.remove("$nb/$name")?.let { bytes -= it.allocationByteCount } }

    companion object {
        const val MAX_DIM = 3072
        const val MAX_BYTES = 96L * 1024 * 1024

        fun decodeFile(f: File, maxDim: Int): Bitmap? = try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            var sample = 1
            while (max(o.outWidth, o.outHeight) / (sample * 2) >= maxDim) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = BitmapFactory.decodeFile(f.path, o2)
            if (bmp != null) {
                val rot = when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (rot != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
            }
            bmp
        } catch (_: Throwable) { null }

        /** Rozmiar obrazu bez dekodowania (po uwzględnieniu obrotu EXIF). */
        fun imageSize(f: File): Pair<Int, Int>? = try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            val rot = ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            if (o.outWidth <= 0) null
            else if (rot == ExifInterface.ORIENTATION_ROTATE_90 || rot == ExifInterface.ORIENTATION_ROTATE_270) o.outHeight to o.outWidth
            else o.outWidth to o.outHeight
        } catch (_: Throwable) { null }
    }
}

/** Otwarte dokumenty PDF (jedna strona naraz na dokument — ograniczenie PdfRenderer), dostęp szeregowy. */
class PdfPool(private val repo: Repository) {
    private class Doc(val pfd: ParcelFileDescriptor, val r: PdfRenderer)
    private val docs = HashMap<String, Doc>()

    @Synchronized private fun open(nb: String, asset: String): Doc? {
        val key = "$nb/$asset"
        docs[key]?.let { return it }
        return try {
            val pfd = ParcelFileDescriptor.open(repo.assetFile(nb, asset), ParcelFileDescriptor.MODE_READ_ONLY)
            Doc(pfd, PdfRenderer(pfd)).also { docs[key] = it }
        } catch (_: Throwable) { null }
    }

    @Synchronized fun pageCount(nb: String, asset: String): Int = open(nb, asset)?.r?.pageCount ?: 0

    /** Rozmiar strony w pt. */
    @Synchronized fun pageSize(nb: String, asset: String, page: Int): Pair<Float, Float>? {
        val d = open(nb, asset) ?: return null
        if (page !in 0 until d.r.pageCount) return null
        return d.r.openPage(page).use { it.width.toFloat() to it.height.toFloat() }
    }

    @Synchronized fun render(nb: String, asset: String, page: Int, wPx: Int, hPx: Int): Bitmap? {
        val d = open(nb, asset) ?: return null
        if (page !in 0 until d.r.pageCount) return null
        return try {
            val bmp = Bitmap.createBitmap(wPx.coerceAtLeast(1), hPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            bmp.eraseColor(0xFFFFFFFF.toInt())
            d.r.openPage(page).use { it.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
            bmp
        } catch (_: Throwable) { null }
    }

    /** Kafelek strony PDF: fragment [w]×[h] px od ([ox], [oy]) px przy skali [scale] (px na pt). */
    @Synchronized fun renderTile(nb: String, asset: String, page: Int, scale: Float, ox: Int, oy: Int, w: Int, h: Int): Bitmap? {
        val d = open(nb, asset) ?: return null
        if (page !in 0 until d.r.pageCount) return null
        return try {
            val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            bmp.eraseColor(0xFFFFFFFF.toInt())
            val m = Matrix().apply { setScale(scale, scale); postTranslate(-ox.toFloat(), -oy.toFloat()) }
            d.r.openPage(page).use { it.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
            bmp
        } catch (_: Throwable) { null }
    }

    @Synchronized fun closeAll() {
        docs.values.forEach { runCatching { it.r.close(); it.pfd.close() } }
        docs.clear()
    }
}
