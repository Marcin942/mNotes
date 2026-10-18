package com.mdev.mnotes.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import com.mdev.mnotes.core.samsung.SdImageKey
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Obrazy z notatki Samsung: kadrowanie według klucza `sd:` i dekodowanie do podglądu albo do pliku zeszytu. */
object SamsungImages {
    /** Plik obrazu gotowy do zapisania w zeszycie: oryginalne bajty albo, gdy obraz jest kadrowany, wykadrowany fragment. */
    class Encoded(val bytes: ByteArray, val ext: String)

    private fun ext(b: ByteArray): String? = when {
        b.size > 3 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() -> "png"
        b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "jpg"
        b.size > 12 && String(b, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(b, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> "webp"
        else -> null
    }

    private fun bounds(bytes: ByteArray): Pair<Int, Int>? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
    }

    private fun cropRect(crop: DoubleArray, w: Int, h: Int): Rect? {
        val r = Rect(
            (crop[0] * w).toInt().coerceIn(0, w - 1), (crop[1] * h).toInt().coerceIn(0, h - 1),
            Math.round(crop[2] * w).toInt().coerceIn(1, w), Math.round(crop[3] * h).toInt().coerceIn(1, h),
        )
        return if (r.width() >= 1 && r.height() >= 1) r else null
    }

    /** Bitmapa obrazu [key] z [media] o dłuższym boku najwyżej [maxDim] px albo null, gdy się nie da jej odczytać. */
    fun decode(media: Map<Int, ByteArray>, key: String, maxDim: Int): Bitmap? = try {
        val (id, crop) = SdImageKey.parse(key) ?: return null
        val bytes = media[id] ?: return null
        val (w, h) = bounds(bytes) ?: return null
        val region = if (crop != null) cropRect(crop, w, h) ?: return null else Rect(0, 0, w, h)
        var sample = 1
        while (max(region.width(), region.height()) / (sample * 2) >= maxDim) sample *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = sample }
        if (crop == null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        else {
            val dec = BitmapRegionDecoder.newInstance(bytes, 0, bytes.size)
            try { dec?.decodeRegion(region, o) } finally { dec?.recycle() }
        }
    } catch (_: Throwable) { null }

    /** Plik do zeszytu dla klucza [key]; null, gdy medium brakuje albo nie jest obrazem. */
    fun encode(media: Map<Int, ByteArray>, key: String): Encoded? = try {
        val (id, crop) = SdImageKey.parse(key) ?: return null
        val bytes = media[id] ?: return null
        if (crop == null) ext(bytes)?.let { Encoded(bytes, it) } ?: bounds(bytes)?.let { Encoded(bytes, "png") }
        else {
            val bmp = decode(media, key, Int.MAX_VALUE) ?: return null
            val out = ByteArrayOutputStream()
            val png = bmp.hasAlpha()
            bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, out)
            bmp.recycle()
            Encoded(out.toByteArray(), if (png) "png" else "jpg")
        }
    } catch (_: Throwable) { null }
}
