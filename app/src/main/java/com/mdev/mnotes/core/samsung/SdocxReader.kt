package com.mdev.mnotes.core.samsung

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Dekoder `.sdocx` (Samsung Notes): archiwum ZIP z `note.note` (tablica napisów), `pageIdInfo.dat` (kolejność stron)
 * i plikami `<uuid>.page` (nagłówek strony + warstwy z obiektami). Napisany na podstawie opisu formatu
 * (bxff/samsung-notes-format, MIT), bez kopiowania cudzego kodu. Odczytuje tylko to, co aplikacja umie przenieść:
 * pociągnięcia i tło strony. Resztę obiektów zachowuje jako surowe ramki, żeby konwerter mógł je policzyć w raporcie.
 */
object SdocxReader {
    private const val MAX_ENTRY = 64 * 1024 * 1024
    private const val MAX_TOTAL = 256 * 1024 * 1024
    private const val MAX_DEPTH = 48
    private const val MAX_PAGES = 5000

    /** Wczytuje cały dokument ze strumienia ZIP. Strona, której nie da się odczytać, trafia do wyniku z [SdPage.error]. */
    fun read(input: InputStream): SdDocument {
        val files = HashMap<String, ByteArray>()
        val media = HashMap<Int, ByteArray>()
        var total = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.substringAfterLast('/')
                val mediaId = if (e.isDirectory) null else mediaId(name)
                val wanted = name == "note.note" || name == "pageIdInfo.dat" || name.endsWith(".page")
                if ((!wanted && mediaId == null) || e.isDirectory) continue
                val data = readBounded(zip, MAX_ENTRY)
                total += data.size
                if (total > MAX_TOTAL) throw SamsungFormatException("plik jest zbyt duży")
                if (mediaId != null) media[mediaId] = data else files[name] = data
            }
        }
        val note = files["note.note"] ?: throw SamsungFormatException("brak note.note: to nie jest notatka Samsung Notes")
        val order = files["pageIdInfo.dat"] ?: throw SamsungFormatException("brak pageIdInfo.dat")
        val strings = readStringTable(note)
        val pages = ArrayList<SdPage>()
        for (uuid in readPageOrder(order)) {
            val data = files["$uuid.page"]
            pages.add(
                if (data == null) failedPage(uuid, "brak pliku strony")
                else try {
                    parsePage(data)
                } catch (e: SamsungFormatException) {
                    failedPage(uuid, e.message ?: "błąd odczytu")
                } catch (e: RuntimeException) {
                    failedPage(uuid, e.message ?: "błąd odczytu")
                },
            )
        }
        return SdDocument(strings, pages, SdFlowReader.find(note), media)
    }

    /** Numer pliku obrazu z nazwy `<numer>@<nazwa>.jpg|png|webp`; podglądy `.spi` i inne pliki dają null. */
    internal fun mediaId(name: String): Int? {
        val at = name.indexOf('@')
        if (at <= 0 || at > 9) return null
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext != "jpg" && ext != "jpeg" && ext != "png" && ext != "webp") return null
        return name.substring(0, at).toIntOrNull()?.takeIf { it >= 0 }
    }

    /** Czy to archiwum wygląda jak `.sdocx` (ma `note.note`). Czyta strumień tylko do pierwszego trafienia. */
    fun looksLikeSdocx(input: InputStream): Boolean = try {
        ZipInputStream(input).use { zip ->
            var found = false
            while (!found) {
                val e = zip.nextEntry ?: break
                if (e.name.substringAfterLast('/') == "note.note") found = true
            }
            found
        }
    } catch (_: Exception) {
        false
    }

    private fun readBounded(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > max) throw SamsungFormatException("część pliku jest zbyt duża")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun failedPage(uuid: String, message: String) =
        SdPage(uuid, 0, 0, null, null, null, null, false, emptyList(), error = message)

    /** Tablica napisów z `note.note`: u32 rozmiar, u16 liczba, pary (u32 id, napis). Położenie szukane skanem. */
    internal fun readStringTable(note: ByteArray): Map<Int, String> {
        val n = note.size
        var o = 0
        while (o < n - 6) {
            val r = LeReader(note, o)
            val size = r.u32()
            val cnt = r.u16()
            if (cnt == 0 || cnt > 200 || size < 2 || o + 4 + size > n) { o++; continue }
            val out = LinkedHashMap<Int, String>()
            val ok = try {
                repeat(cnt) {
                    val id = r.i32()
                    val ln = r.u16()
                    out[id] = String(r.raw(2 * ln), Charsets.UTF_16LE)
                }
                r.p.toLong() == o + 4 + size
            } catch (_: SamsungFormatException) {
                false
            }
            if (ok && out.isNotEmpty() && out.values.any { "samsung" in it || it.endsWith(";") }) return out
            o++
        }
        return emptyMap()
    }

    internal fun readPageOrder(pageIdInfo: ByteArray): List<String> {
        val r = LeReader(pageIdInfo, 32)
        val cnt = r.u16()
        if (cnt > MAX_PAGES) throw SamsungFormatException("zbyt wiele stron: $cnt")
        val out = ArrayList<String>(cnt)
        repeat(cnt) {
            out.add(r.s16())
            r.skip(32)
        }
        return out
    }

    private fun dq(raw: Int, scale: Double): Double {
        val v = (raw and 0x7fff) / scale
        return if (raw and 0x8000 != 0) -v else v
    }

    /**
     * Pociągnięcie (obiekt typu 1): ramka bazowa i ramka pociągnięcia. Wersja skompresowana (bit 0 maski) zapisuje
     * współrzędne jako 16-bitowe przyrosty ze znakiem w bicie 15 (xy: 1/32, nacisk: 1/4096), czas jako przyrosty ms.
     */
    internal fun parseStroke(b: ByteArray, strings: Map<Int, String>): SdStroke {
        var r = LeReader(b, 0)
        val baseSize = r.u32()
        if (r.u16().toShort().toInt() != 0) throw SamsungFormatException("zła ramka bazowa pociągnięcia")
        if (baseSize > b.size) throw SamsungFormatException("zła ramka bazowa pociągnięcia")
        val fs = baseSize.toInt()
        r = LeReader(b, fs)
        val fsize = r.u32().toInt()
        if (r.u16().toShort().toInt() != 1) throw SamsungFormatException("zła ramka pociągnięcia")
        val flex = r.u32().toInt()
        val props = r.mask()
        val fmask = r.mask()
        val n = r.u16()
        val x = DoubleArray(n)
        val y = DoubleArray(n)
        val pr = FloatArray(n)
        val ts = IntArray(n)
        if (props and 1L != 0L) {
            if (n > 0) {
                x[0] = r.f64(); y[0] = r.f64()
                for (i in 1 until n) {
                    x[i] = x[i - 1] + dq(r.u16(), 32.0)
                    y[i] = y[i - 1] + dq(r.u16(), 32.0)
                }
                var p = r.f32().toDouble()
                pr[0] = p.toFloat()
                for (i in 1 until n) { p += dq(r.u16(), 4096.0); pr[i] = p.toFloat() }
                var t = r.i32()
                ts[0] = t
                for (i in 1 until n) { t += r.u16(); ts[i] = t }
                if (props and 4L != 0L) repeat(2) { r.f32(); r.skip(2 * (n - 1)) }
                r.skip(2)
            }
        } else {
            for (i in 0 until n) { x[i] = r.f64(); y[i] = r.f64() }
            for (i in 0 until n) pr[i] = r.f32()
            for (i in 0 until n) ts[i] = r.i32()
            if (props and 4L != 0L) r.skip(8 * n)
            r.skip(2)
        }
        if (r.p != fs + flex) throw SamsungFormatException("koniec tablic ${r.p - fs} != $flex (n=$n, props=$props)")
        r = LeReader(b, fs + flex)
        var color: Int? = null
        var size: Float? = null
        var pen: String? = null
        fun sid(i: Int): String? = if (i != -1) strings[i] else null
        for (bit in 0 until 26) {
            if (fmask shr bit and 1L == 0L) continue
            when (bit) {
                0, 1 -> r.i32()
                2 -> color = r.i32()
                3 -> size = r.f32()
                4 -> r.u8()
                5 -> throw SamsungFormatException("pole 5 pociągnięcia nieobsługiwane")
                7 -> pen = sid(r.i32())
                8 -> r.f32()
                9, 10, 11, 12, 19, 21, 22 -> r.i32()
                13, 15, 17, 18, 20, 23 -> r.f32()
                14, 16, 25 -> r.u16()
                24 -> repeat(r.u16()) { r.u32() }
            }
        }
        if (fsize < 0) throw SamsungFormatException("zła długość ramki pociągnięcia")
        return SdStroke(x, y, pr, ts, props, color, size, pen)
    }

    private fun readObjects(r: LeReader, count: Int, depth: Int): List<SdObject> {
        if (depth > MAX_DEPTH) throw SamsungFormatException("zbyt głębokie zagnieżdżenie obiektów")
        val out = ArrayList<SdObject>()
        repeat(count) {
            val type = r.u8()
            val cc = r.u16()
            val ds = r.u32()
            if (ds < 32) throw SamsungFormatException("zły rozmiar obiektu")
            val payload = r.raw((ds - 32).toInt())
            r.skip(32)
            out.add(SdObject(type, payload, readObjects(r, cc, depth + 1)))
        }
        return out
    }

    internal fun parsePage(b: ByteArray): SdPage {
        var r = LeReader(b, 0)
        val layerOff = r.u32().toInt()
        val flexOff = r.u32().toInt()
        r.mask()
        val fm = r.mask()
        r.u32()
        val w = r.u32().toInt()
        val h = r.u32().toInt()
        r.skip(8)
        val uuid = r.s16()
        r.skip(8 + 4 + 4)
        if (flexOff < 0 || flexOff > b.size || layerOff < 0 || layerOff > b.size) throw SamsungFormatException("zły układ strony")
        r = LeReader(b, flexOff)
        if (fm and 1L != 0L) r.skip(32)
        if (fm and 2L != 0L) throw SamsungFormatException("znaczniki strony nieobsługiwane")
        val uri = if (fm and 4L != 0L) r.s16() else null
        val bgImage = if (fm and 8L != 0L) r.i32() else null
        if (fm and 16L != 0L) r.i32()
        val bgColor = if (fm and 32L != 0L) r.i32() else null
        if (fm and 64L != 0L) r.i32()
        if (fm and 128L != 0L) r.i32()
        var hasPdf = false
        if (fm and 256L != 0L) {
            hasPdf = true
            repeat(r.u16()) { r.skip(24) }
        }
        val template = if (fm and 512L != 0L) r.i32() else null
        r = LeReader(b, layerOff)
        val layerCount = r.u16()
        r.u16()
        val layers = ArrayList<List<SdObject>>()
        repeat(layerCount) {
            val start = r.p
            val hsize = r.u32().toInt()
            r.p = start + hsize
            val cnt = r.u32().toInt()
            if (cnt < 0 || cnt > MAX_ENTRY) throw SamsungFormatException("zła liczba obiektów")
            layers.add(readObjects(r, cnt, 0))
            r.skip(32)
        }
        return SdPage(uuid, w, h, bgColor, template, uri, bgImage, hasPdf, layers)
    }
}
