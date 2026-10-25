package com.mdev.mnotes.core.goodnotes

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Obraz XObject z PDF-a jako plik, który mNotes umie wstawić: JPEG przepisany bez zmian albo PNG złożony tu (czysty
 * Kotlin, bez Androida). Null = obraz w formacie, którego nie odczytamy (JPEG 2000, JBIG2, faks, maska szablonowa,
 * egzotyczna przestrzeń barw); taki obraz zostaje w tle PDF.
 */
object PdfImages {
    class Encoded(val bytes: ByteArray, val ext: String, val w: Int, val h: Int)

    const val MAX_PIXELS = 40_000_000

    fun extract(doc: PdfDocument, img: PdfStream): Encoded? = try { extractOrThrow(doc, img) } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }

    private fun extractOrThrow(doc: PdfDocument, img: PdfStream): Encoded? {
        val d = img.dict
        if (d["ImageMask"] == true) return null
        val w = doc.num(d["Width"] ?: d["W"])?.toInt() ?: return null
        val h = doc.num(d["Height"] ?: d["H"])?.toInt() ?: return null
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_PIXELS) return null
        val cs = ColorSpace.resolve(doc, d["ColorSpace"] ?: d["CS"], null)
        val filters = doc.filterList(d)
        val decodeArr = doc.array(d["Decode"] ?: d["D"])?.mapNotNull { doc.num(it) }
        val hasMask = d["SMask"] != null || d["Mask"] != null
        val imageFilter = filters.lastOrNull { it in PdfDocument.IMAGE_FILTERS }
        if (imageFilter != null) {
            if (imageFilter != "DCTDecode" && imageFilter != "DCT") return null
            if (hasMask || decodeArr != null || (cs.kind != CsKind.GRAY && cs.kind != CsKind.RGB)) return null
            val jpg = doc.decodeStream(img, stopAtImageFilter = true)
            if (jpg.size < 4 || jpg[0] != 0xFF.toByte() || jpg[1] != 0xD8.toByte()) return null
            return Encoded(jpg, "jpg", w, h)
        }
        val bpc = doc.num(d["BitsPerComponent"] ?: d["BPC"])?.toInt() ?: 8
        if (bpc !in setOf(1, 2, 4, 8, 16)) return null
        val n = when (cs.kind) {
            CsKind.GRAY, CsKind.INDEXED -> 1
            CsKind.RGB, CsKind.LAB -> 3
            CsKind.CMYK -> 4
            else -> return null
        }
        val data = doc.decodeStream(img)
        val rgb = toRgb(data, w, h, n, bpc, cs, decodeArr) ?: return null
        val alpha = maskAlpha(doc, d, data, w, h, n, bpc)
        return Encoded(png(w, h, rgb, alpha), "png", w, h)
    }

    /** Próbki obrazu jako liczby 0..(2^bpc − 1), wiersz po wierszu (wiersz dopełniony do bajtu). */
    private fun samples(data: ByteArray, w: Int, h: Int, n: Int, bpc: Int): IntArray? {
        val rowBytes = (w * n * bpc + 7) / 8
        if (data.size < rowBytes * h) {
            // obcięty strumień: brakujące wiersze puste, lepsze niż utrata całego obrazu
            if (data.size < rowBytes) return null
        }
        val out = IntArray(w * h * n)
        var k = 0
        for (y in 0 until h) {
            val row = y * rowBytes
            for (i in 0 until w * n) {
                val v = when (bpc) {
                    8 -> data.getOrElse(row + i) { 0 }.toInt() and 0xFF
                    16 -> data.getOrElse(row + 2 * i) { 0 }.toInt() and 0xFF
                    else -> {
                        val bit = i * bpc
                        val byte = data.getOrElse(row + bit / 8) { 0 }.toInt() and 0xFF
                        (byte shr (8 - bpc - bit % 8)) and ((1 shl bpc) - 1)
                    }
                }
                out[k++] = v
            }
        }
        return out
    }

    private fun toRgb(data: ByteArray, w: Int, h: Int, n: Int, bpc: Int, cs: ColorSpace, decode: List<Double>?): ByteArray? {
        val s = samples(data, w, h, n, bpc) ?: return null
        val max = if (bpc == 16) 255 else (1 shl bpc) - 1
        val out = ByteArray(w * h * 3)
        val c = DoubleArray(n)
        val cache = HashMap<Long, Int>()
        for (p in 0 until w * h) {
            var key = 0L
            for (j in 0 until n) {
                val raw = s[p * n + j]
                key = key * 65537 + raw
                c[j] = if (cs.kind == CsKind.INDEXED) {
                    if (decode != null && decode.size >= 2) decode[0] + raw * (decode[1] - decode[0]) / max else raw.toDouble()
                } else {
                    val lo = decode?.getOrNull(2 * j) ?: defaultLo(cs, j)
                    val hi = decode?.getOrNull(2 * j + 1) ?: defaultHi(cs, j)
                    lo + raw * (hi - lo) / max
                }
            }
            val argb = cache.getOrPut(key) { cs.argb(c) ?: return null }
            out[3 * p] = (argb shr 16).toByte(); out[3 * p + 1] = (argb shr 8).toByte(); out[3 * p + 2] = argb.toByte()
        }
        return out
    }

    private fun defaultLo(cs: ColorSpace, j: Int) = if (cs.kind == CsKind.LAB && j > 0) -100.0 else 0.0
    private fun defaultHi(cs: ColorSpace, j: Int) = if (cs.kind == CsKind.LAB) (if (j == 0) 100.0 else 100.0) else 1.0

    /** Kanał alfa z /SMask, maski szablonowej /Mask albo klucza kolorów /Mask; null = obraz nieprzezroczysty. */
    private fun maskAlpha(doc: PdfDocument, d: PdfDict, data: ByteArray, w: Int, h: Int, n: Int, bpc: Int): ByteArray? {
        val sm = doc.resolve(d["SMask"]) as? PdfStream
        if (sm != null) return grayPlane(doc, sm, w, h, invertStencil = false, soft = true)
        return when (val m = doc.resolve(d["Mask"])) {
            is PdfStream -> grayPlane(doc, m, w, h, invertStencil = true, soft = false)
            is List<*> -> {
                val r = m.mapNotNull { doc.num(it)?.toInt() }
                if (r.size < 2 * n) return null
                val s = samples(data, w, h, n, bpc) ?: return null
                ByteArray(w * h) { p ->
                    val hit = (0 until n).all { j -> s[p * n + j] in r[2 * j]..r[2 * j + 1] }
                    if (hit) 0 else -1
                }
            }
            else -> null
        }
    }

    /** Płaszczyzna maski przeskalowana (najbliższy sąsiad) do rozmiaru obrazu. */
    private fun grayPlane(doc: PdfDocument, m: PdfStream, w: Int, h: Int, invertStencil: Boolean, soft: Boolean): ByteArray? {
        val md = m.dict
        if (doc.filterList(md).any { it in PdfDocument.IMAGE_FILTERS }) return null
        val mw = doc.num(md["Width"])?.toInt() ?: return null
        val mh = doc.num(md["Height"])?.toInt() ?: return null
        if (mw <= 0 || mh <= 0 || mw.toLong() * mh > MAX_PIXELS) return null
        val bpc = if (md["ImageMask"] == true) 1 else doc.num(md["BitsPerComponent"])?.toInt() ?: if (soft) 8 else 1
        val s = samples(doc.decodeStream(m), mw, mh, 1, bpc) ?: return null
        val max = if (bpc == 16) 255 else (1 shl bpc) - 1
        val dec = doc.array(md["Decode"])?.mapNotNull { doc.num(it) }
        // maska szablonowa: próbka 1 (przy domyślnym /Decode [0 1]) zakrywa obraz
        val invert = (dec != null && dec.size >= 2 && dec[0] > dec[1]) xor (invertStencil && !soft)
        val out = ByteArray(w * h)
        for (y in 0 until h) {
            val my = (y.toLong() * mh / h).toInt()
            for (x in 0 until w) {
                val mx = (x.toLong() * mw / w).toInt()
                var v = s[my * mw + mx] * 255 / max
                if (invert) v = 255 - v
                out[y * w + x] = v.toByte()
            }
        }
        return out
    }

    fun png(w: Int, h: Int, rgb: ByteArray, alpha: ByteArray?): ByteArray {
        val ch = if (alpha != null) 4 else 3
        val raw = ByteArray((w * ch + 1) * h)
        var k = 0
        for (y in 0 until h) {
            raw[k++] = 0
            for (x in 0 until w) {
                val p = y * w + x
                raw[k++] = rgb[3 * p]; raw[k++] = rgb[3 * p + 1]; raw[k++] = rgb[3 * p + 2]
                if (alpha != null) raw[k++] = alpha[p]
            }
        }
        val def = Deflater(6)
        def.setInput(raw)
        def.finish()
        val z = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (!def.finished()) { val c = def.deflate(buf); z.write(buf, 0, c) }
        def.end()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        val ihdr = ByteArrayOutputStream().also { DataOutputStream(it).apply { writeInt(w); writeInt(h); writeByte(8); writeByte(if (alpha != null) 6 else 2); writeByte(0); writeByte(0); writeByte(0) } }
        chunk(out, "IHDR", ihdr.toByteArray())
        chunk(out, "IDAT", z.toByteArray())
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val d = DataOutputStream(out)
        d.writeInt(data.size)
        val t = type.toByteArray(Charsets.ISO_8859_1)
        d.write(t); d.write(data)
        val crc = CRC32().apply { update(t); update(data) }
        d.writeInt(crc.value.toInt())
    }
}
