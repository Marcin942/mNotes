package com.mdev.mnotes.core.goodnotes

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/** Strona po rozwinięciu dziedziczenia drzewa stron. Prostokąty są w jednostkach PDF jako [x0, y0, x1, y1]. */
class PdfPage(
    val index: Int,
    val ref: PdfObjRef?,
    val dict: PdfDict,
    val resources: PdfDict,
    val mediaBox: DoubleArray,
    val cropBox: DoubleArray,
    /** Obrót w stopniach, wielokrotność 90, 0..270. */
    val rotate: Int,
)

/**
 * Czytnik PDF wystarczający do PDF-ów z GoodNotes (i większości innych): klasyczna tabela xref i strumienie xref,
 * aktualizacje przyrostowe (`/Prev`), strumienie obiektów, filtry Flate/ASCIIHex/ASCII85/RunLength z predyktorami.
 * Gdy tabela xref jest uszkodzona, obiekty są odszukiwane skanowaniem pliku. Plików zaszyfrowanych nie czyta.
 */
class PdfDocument(val data: ByteArray) {
    private val offsets = HashMap<Int, Int>()
    private val compressed = HashMap<Int, IntArray>() // numer → (strumień obiektów, indeks)
    private val cache = HashMap<Int, Any?>()
    private val loading = HashSet<Int>()
    val trailer: PdfDict
    val pages: List<PdfPage>

    init {
        var t: PdfDict? = null
        try { t = readXrefChain() } catch (_: Exception) {}
        if (t == null || t["Root"] == null || !rootLooksValid(t)) t = rebuild()
        trailer = t
        if (trailer["Encrypt"] != null) throw PdfException("PDF jest zaszyfrowany")
        pages = collectPages()
    }

    // --- xref --------------------------------------------------------------------------------------

    private fun rootLooksValid(t: PdfDict): Boolean = try {
        (resolve(t["Root"]) as? PdfDict)?.let { resolve(it["Pages"]) is PdfDict } ?: false
    } catch (_: Exception) { false }

    private fun readXrefChain(): PdfDict? {
        val sx = lastIndexOf("startxref".toByteArray(), data.size - 1, maxOf(0, data.size - 4096))
        if (sx < 0) return null
        val lx = PdfLexer(data, sx + 9)
        val off = (lx.token() as? Double)?.toInt() ?: return null
        val header = indexOf("%PDF-".toByteArray(), 0, minOf(data.size, 1024)).coerceAtLeast(0)
        var trailer: PdfDict? = null
        val seen = HashSet<Int>()
        var next: Int? = off
        while (next != null && seen.add(next)) {
            val at = fixOffset(next, header) ?: return trailer
            val (t, xrefStm) = readXrefSection(at)
            if (trailer == null) trailer = t
            if (xrefStm != null) fixOffset(xrefStm, header)?.let { readXrefSection(it) }
            next = (t["Prev"] as? Double)?.toInt()
        }
        return trailer
    }

    private fun fixOffset(off: Int, header: Int): Int? {
        for (o in listOf(off, off + header)) if (o in 0 until data.size) {
            val l = PdfLexer(data, o)
            l.skipWs()
            if (startsWith(l.pos, "xref")) return l.pos
            val a = l.token(); val b = l.token(); val c = l.token()
            if (a is Double && b is Double && c is PdfKeyword && c.word == "obj") return o
        }
        return null
    }

    /** Jedna sekcja xref; wpisy wcześniej wczytane (nowsze) mają pierwszeństwo. */
    private fun readXrefSection(at: Int): Pair<PdfDict, Int?> {
        val l = PdfLexer(data, at)
        l.skipWs()
        if (startsWith(l.pos, "xref")) {
            l.pos += 4
            while (true) {
                val save = l.pos
                val first = l.token()
                if (first !is Double) { l.pos = save; break }
                val count = (l.token() as? Double)?.toInt() ?: break
                for (i in 0 until count) {
                    val o = l.token() as? Double ?: break
                    l.token()
                    val kind = l.token() as? PdfKeyword ?: break
                    val num = first.toInt() + i
                    if (kind.word == "n" && num !in offsets && num !in compressed && o > 0) offsets[num] = o.toInt()
                }
            }
            val kw = l.token()
            if (kw !is PdfKeyword || kw.word != "trailer") throw PdfException("brak trailer")
            val t = l.parseObject() as? PdfDict ?: throw PdfException("zły trailer")
            return t to (t["XRefStm"] as? Double)?.toInt()
        }
        val obj = readObjectAt(at, -1) as? PdfStream ?: throw PdfException("brak xref")
        val d = obj.dict
        if (d.name("Type") != "XRef") throw PdfException("to nie strumień xref")
        val w = (d["W"] as? List<*>)?.map { (it as? Double)?.toInt() ?: 0 } ?: throw PdfException("brak /W")
        if (w.size < 3 || w.any { it < 0 || it > 8 }) throw PdfException("złe /W")
        val size = (d["Size"] as? Double)?.toInt() ?: 0
        val index = (d["Index"] as? List<*>)?.map { (it as? Double)?.toInt() ?: 0 } ?: listOf(0, size)
        val bytes = decodeStream(obj)
        val rec = w.sum()
        // rekord 0 bajtów nie przesuwa `p`, więc kontrola końca danych nie przerwałaby pętli po /Index (2³¹ obrotów)
        if (rec == 0) throw PdfException("złe /W")
        var p = 0
        var k = 0
        while (k + 1 < index.size) {
            val first = index[k]; val count = minOf(index[k + 1], (bytes.size - p) / rec)
            for (i in 0 until count) {
                if (p + rec > bytes.size) break
                fun field(n: Int): Long { var v = 0L; repeat(n) { v = (v shl 8) or (bytes[p++].toLong() and 0xFF) }; return v }
                val type = if (w[0] == 0) 1L else field(w[0])
                val f2 = field(w[1]); val f3 = field(w[2])
                val num = first + i
                if (num !in offsets && num !in compressed) when (type) {
                    1L -> if (f2 > 0) offsets[num] = f2.toInt()
                    2L -> compressed[num] = intArrayOf(f2.toInt(), f3.toInt())
                }
            }
            k += 2
        }
        return d to null
    }

    /** Odbudowa po uszkodzonym xref: wszystkie „n g obj” w pliku (późniejsze wygrywają) i trailer albo strumień XRef. */
    private fun rebuild(): PdfDict {
        offsets.clear(); compressed.clear(); cache.clear()
        var i = 0
        val n = data.size
        while (i < n - 3) {
            if (data[i] == 'o'.code.toByte() && data[i + 1] == 'b'.code.toByte() && data[i + 2] == 'j'.code.toByte() &&
                (i + 3 >= n || !PdfLexer.isRegular(data[i + 3].toInt() and 0xFF))
            ) {
                var p = i - 1
                fun skipBackWs() { while (p >= 0 && PdfLexer.isWs(data[p].toInt() and 0xFF)) p-- }
                fun backDigits(): Int { val e = p; while (p >= 0 && data[p] in '0'.code.toByte()..'9'.code.toByte()) p--; return e - p }
                skipBackWs()
                if (backDigits() > 0) {
                    val genEnd = p
                    skipBackWs()
                    if (p < genEnd && backDigits() > 0 && (p < 0 || !PdfLexer.isRegular(data[p].toInt() and 0xFF))) {
                        val start = p + 1
                        val l = PdfLexer(data, start)
                        val num = (l.token() as? Double)?.toInt()
                        if (num != null) offsets[num] = start
                    }
                }
            }
            i++
        }
        val trailer = PdfDict()
        var tpos = lastIndexOf("trailer".toByteArray(), n - 1, 0)
        while (tpos >= 0) {
            try {
                val t = PdfLexer(data, tpos + 7).parseObject() as? PdfDict
                if (t != null && t["Root"] != null) { trailer.map.putAll(t.map); break }
            } catch (_: PdfException) {}
            tpos = lastIndexOf("trailer".toByteArray(), tpos - 1, 0)
        }
        // strumienie obiektów i strumienie XRef znalezione skanem
        for (num in offsets.keys.toList()) {
            val o = try { obj(num) } catch (_: Exception) { null }
            if (o is PdfStream) {
                when (o.dict.name("Type")) {
                    "ObjStm" -> try {
                        objStm(num).forEachIndexed { idx, (inner, _) -> if (inner !in offsets) compressed[inner] = intArrayOf(num, idx) }
                    } catch (_: Exception) {}
                    "XRef" -> if (trailer["Root"] == null && o.dict["Root"] != null) trailer.map.putAll(o.dict.map)
                }
            }
        }
        if (trailer["Root"] == null) {
            // ostatnia deska ratunku: dowolny słownik /Type /Catalog
            for (num in offsets.keys.sorted()) {
                val o = try { obj(num) } catch (_: Exception) { null }
                if (o is PdfDict && o.name("Type") == "Catalog") { trailer["Root"] = PdfObjRef(num, 0); break }
            }
        }
        if (trailer["Root"] == null) throw PdfException("nie znaleziono katalogu dokumentu")
        trailer.map.remove("Prev"); trailer.map.remove("XRefStm")
        return trailer
    }

    // --- obiekty -----------------------------------------------------------------------------------

    fun resolve(v: Any?): Any? {
        var x = v
        var guard = 0
        while (x is PdfObjRef && guard++ < 32) x = obj(x.num)
        return if (x is PdfObjRef) null else x
    }

    fun dict(v: Any?): PdfDict? = when (val r = resolve(v)) {
        is PdfDict -> r
        is PdfStream -> r.dict
        else -> null
    }

    fun num(v: Any?): Double? = resolve(v) as? Double

    fun array(v: Any?): List<Any?>? = resolve(v) as? List<Any?>

    fun obj(num: Int): Any? {
        if (cache.containsKey(num)) return cache[num]
        if (!loading.add(num)) return null
        try {
            val v: Any? = compressed[num]?.let { (stm, idx) ->
                val list = objStm(stm)
                if (idx in list.indices && list[idx].first == num) list[idx].second
                else list.firstOrNull { it.first == num }?.second
            } ?: offsets[num]?.let { readObjectAt(it, num) }
            cache[num] = v
            return v
        } catch (_: Exception) {
            cache[num] = null
            return null
        } finally {
            loading.remove(num)
        }
    }

    private val objStmCache = HashMap<Int, List<Pair<Int, Any?>>>()

    private fun objStm(num: Int): List<Pair<Int, Any?>> = objStmCache.getOrPut(num) {
        val s = (if (offsets.containsKey(num)) readObjectAt(offsets[num]!!, num) else null) as? PdfStream
            ?: return@getOrPut emptyList()
        val n = (resolve(s.dict["N"]) as? Double)?.toInt() ?: 0
        val first = (resolve(s.dict["First"]) as? Double)?.toInt() ?: 0
        val bytes = decodeStream(s)
        val l = PdfLexer(bytes)
        val heads = ArrayList<IntArray>()
        repeat(n) {
            val a = l.token() as? Double ?: return@repeat
            val b = l.token() as? Double ?: return@repeat
            heads.add(intArrayOf(a.toInt(), b.toInt()))
        }
        heads.map { (onum, off) ->
            val v = try {
                val ol = PdfLexer(bytes, first + off)
                ol.parseObject()
            } catch (_: PdfException) { null }
            onum to v
        }
    }

    /** Obiekt pośredni pod przesunięciem [at]; [expect] < 0 = dowolny numer. */
    private fun readObjectAt(at: Int, expect: Int): Any? {
        val l = PdfLexer(data, at)
        val a = l.token(); l.token()
        val kw = l.token()
        if (a !is Double || kw !is PdfKeyword || kw.word != "obj") throw PdfException("brak obiektu pod $at")
        if (expect >= 0 && a.toInt() != expect) throw PdfException("inny obiekt pod $at")
        val v = l.parseObject()
        val save = l.pos
        val t = l.token()
        if (t is PdfKeyword && t.word == "stream" && v is PdfDict) {
            var p = l.pos
            if (p < data.size && data[p].toInt() == 13) p++
            if (p < data.size && data[p].toInt() == 10) p++
            val len = (resolveLength(v["Length"]) ?: -1)
            var end = if (len >= 0 && p + len <= data.size) p + len else -1
            if (end >= 0) {
                val after = PdfLexer(data, end)
                after.skipWs()
                if (!startsWith(after.pos, "endstream")) end = -1
            }
            if (end < 0) {
                val es = indexOf("endstream".toByteArray(), p, data.size)
                if (es < 0) throw PdfException("brak endstream")
                end = es
                if (end > p && data[end - 1].toInt() == 10) end--
                if (end > p && data[end - 1].toInt() == 13) end--
            }
            return PdfStream(v, data.copyOfRange(p, end))
        }
        l.pos = save
        return v
    }

    private fun resolveLength(v: Any?): Int? = when (v) {
        is Double -> v.toInt()
        is PdfObjRef -> (obj(v.num) as? Double)?.toInt()
        else -> null
    }

    // --- strumienie --------------------------------------------------------------------------------

    /** Rozpakowane bajty strumienia; filtry obrazów (DCT, JPX, CCITT, JBIG2) zostają nierozpakowane i dają wyjątek. */
    fun decodeStream(s: PdfStream, stopAtImageFilter: Boolean = false): ByteArray {
        val filters = filterList(s.dict)
        val parms = parmsList(s.dict, filters.size)
        var bytes = s.raw
        for ((i, f) in filters.withIndex()) {
            if (stopAtImageFilter && f in IMAGE_FILTERS) return bytes
            bytes = when (f) {
                "FlateDecode", "Fl" -> predict(inflate(bytes), parms[i])
                "ASCIIHexDecode", "AHx" -> asciiHex(bytes)
                "ASCII85Decode", "A85" -> ascii85(bytes)
                "RunLengthDecode", "RL" -> runLength(bytes)
                "LZWDecode", "LZW" -> predict(lzw(bytes, (parms[i]?.get("EarlyChange") as? Double)?.toInt() ?: 1), parms[i])
                else -> throw PdfException("filtr $f nieobsługiwany")
            }
        }
        return bytes
    }

    fun filterList(d: PdfDict): List<String> = when (val f = resolve(d["Filter"])) {
        is PdfName -> listOf(f.name)
        is List<*> -> f.mapNotNull { (resolve(it) as? PdfName)?.name }
        else -> emptyList()
    }

    private fun parmsList(d: PdfDict, n: Int): List<PdfDict?> = when (val p = resolve(d["DecodeParms"] ?: d["DP"])) {
        is PdfDict -> List(n) { if (it == 0) p else null }
        is List<*> -> List(n) { resolve(p.getOrNull(it)) as? PdfDict }
        else -> List(n) { null }
    }

    private fun inflate(src: ByteArray): ByteArray {
        fun run(nowrap: Boolean): ByteArray {
            val inf = Inflater(nowrap)
            inf.setInput(src)
            val out = ByteArrayOutputStream(src.size * 3 + 64)
            val buf = ByteArray(65536)
            try {
                while (!inf.finished()) {
                    val n = inf.inflate(buf)
                    if (n == 0) { if (inf.needsInput() || inf.needsDictionary()) break }
                    out.write(buf, 0, n)
                    if (out.size() > MAX_STREAM) throw PdfException("strumień za duży")
                }
            } catch (_: java.util.zip.DataFormatException) {
                // uszkodzony koniec: zostaje to, co dało się rozpakować
            } finally { inf.end() }
            return out.toByteArray()
        }
        val a = run(false)
        if (a.isNotEmpty() || src.size < 3) return a
        return run(true)
    }

    private fun predict(src: ByteArray, p: PdfDict?): ByteArray {
        val pred = (p?.get("Predictor") as? Double)?.toInt() ?: 1
        if (pred < 2) return src
        val colors = (p?.get("Colors") as? Double)?.toInt()?.coerceIn(1, 32) ?: 1
        val bpc = (p?.get("BitsPerComponent") as? Double)?.toInt()?.coerceIn(1, 16) ?: 8
        val cols = (p?.get("Columns") as? Double)?.toInt()?.coerceIn(1, 1 shl 20) ?: 1
        val bpp = maxOf(1, (colors * bpc + 7) / 8)
        val row = (colors * bpc * cols + 7) / 8
        if (pred == 2) {
            if (bpc != 8) return src
            val out = src.copyOf()
            var r = 0
            while (r + row <= out.size) {
                for (i in bpp until row) out[r + i] = (out[r + i] + out[r + i - bpp]).toByte()
                r += row
            }
            return out
        }
        val out = ByteArrayOutputStream(src.size)
        var prev = ByteArray(row)
        var i = 0
        while (i < src.size) {
            val type = src[i].toInt() and 0xFF
            i++
            val cur = ByteArray(row)
            val n = minOf(row, src.size - i)
            System.arraycopy(src, i, cur, 0, n)
            i += row
            for (x in 0 until row) {
                val a = if (x >= bpp) cur[x - bpp].toInt() and 0xFF else 0
                val b = prev[x].toInt() and 0xFF
                val c = if (x >= bpp) prev[x - bpp].toInt() and 0xFF else 0
                val v = cur[x].toInt() and 0xFF
                cur[x] = when (type) {
                    1 -> v + a
                    2 -> v + b
                    3 -> v + (a + b) / 2
                    4 -> { val pp = a + b - c; val pa = Math.abs(pp - a); val pb = Math.abs(pp - b); val pc = Math.abs(pp - c)
                        v + if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c }
                    else -> v
                }.toByte()
            }
            out.write(cur, 0, row)
            prev = cur
        }
        return out.toByteArray()
    }

    private fun asciiHex(src: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(src.size / 2)
        var hi = -1
        for (b in src) {
            val c = b.toInt() and 0xFF
            if (c == '>'.code) break
            val v = PdfLexer.hexVal(c)
            if (v < 0) continue
            if (hi < 0) hi = v else { out.write(hi * 16 + v); hi = -1 }
        }
        if (hi >= 0) out.write(hi * 16)
        return out.toByteArray()
    }

    private fun ascii85(src: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(src.size)
        var i = 0
        if (src.size >= 2 && src[0] == '<'.code.toByte() && src[1] == '~'.code.toByte()) i = 2
        val group = IntArray(5)
        var n = 0
        while (i < src.size) {
            val c = src[i++].toInt() and 0xFF
            if (c == '~'.code) break
            if (PdfLexer.isWs(c)) continue
            if (c == 'z'.code && n == 0) { repeat(4) { out.write(0) }; continue }
            if (c < 33 || c > 117) continue
            group[n++] = c - 33
            if (n == 5) {
                var v = 0L
                for (g in group) v = v * 85 + g
                for (s in 3 downTo 0) out.write(((v shr (8 * s)) and 0xFF).toInt())
                n = 0
            }
        }
        if (n > 1) {
            for (k in n until 5) group[k] = 84
            var v = 0L
            for (g in group) v = v * 85 + g
            for (s in 3 downTo 4 - (n - 1)) out.write(((v shr (8 * s)) and 0xFF).toInt())
        }
        return out.toByteArray()
    }

    private fun runLength(src: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(src.size * 2)
        var i = 0
        while (i < src.size) {
            val len = src[i++].toInt() and 0xFF
            if (len == 128) break
            if (len < 128) { val n = minOf(len + 1, src.size - i); out.write(src, i, n); i += n }
            else if (i < src.size) { val b = src[i++].toInt(); repeat(257 - len) { out.write(b) } }
            if (out.size() > MAX_STREAM) throw PdfException("strumień za duży")
        }
        return out.toByteArray()
    }

    private fun lzw(src: ByteArray, early: Int): ByteArray {
        val out = ByteArrayOutputStream(src.size * 3)
        val dict = ArrayList<ByteArray>(4096)
        fun reset() { dict.clear(); for (k in 0 until 256) dict.add(byteArrayOf(k.toByte())); dict.add(ByteArray(0)); dict.add(ByteArray(0)) }
        reset()
        var bits = 9
        var buf = 0L
        var nbits = 0
        var prev: ByteArray? = null
        var i = 0
        while (true) {
            while (nbits < bits && i < src.size) { buf = (buf shl 8) or (src[i++].toLong() and 0xFF); nbits += 8 }
            if (nbits < bits) break
            val code = ((buf shr (nbits - bits)) and ((1L shl bits) - 1)).toInt()
            nbits -= bits
            if (code == 256) { reset(); bits = 9; prev = null; continue }
            if (code == 257) break
            val entry = when {
                code < dict.size -> dict[code]
                prev != null -> prev + prev[0]
                else -> break
            }
            out.write(entry)
            if (out.size() > MAX_STREAM) throw PdfException("strumień za duży")
            if (prev != null && dict.size < 4096) dict.add(prev + entry[0])
            prev = entry
            if (dict.size + early >= (1 shl bits) && bits < 12) bits++
        }
        return out.toByteArray()
    }

    // --- strony ------------------------------------------------------------------------------------

    private fun collectPages(): List<PdfPage> {
        val root = dict(trailer["Root"]) ?: throw PdfException("brak katalogu")
        val out = ArrayList<PdfPage>()
        val seen = HashSet<Any>()
        fun walk(node: Any?, inh: Map<String, Any?>, depth: Int) {
            if (depth > 64 || out.size > MAX_PAGES) return
            val ref = node as? PdfObjRef
            if (ref != null && !seen.add(ref)) return
            val d = dict(node) ?: return
            val here = HashMap(inh)
            for (k in INHERITED) d[k]?.let { here[k] = it }
            val kids = array(d["Kids"])
            if (d.name("Type") == "Pages" || (kids != null && d.name("Type") != "Page")) {
                kids?.forEach { walk(it, here, depth + 1) }
                return
            }
            val media = box(here["MediaBox"]) ?: doubleArrayOf(0.0, 0.0, 612.0, 792.0)
            val crop = box(here["CropBox"])?.let { intersect(it, media) } ?: media
            val rot = ((((num(here["Rotate"]) ?: 0.0).toInt() % 360) + 360) % 360).let { it - it % 90 }
            out.add(PdfPage(out.size, ref, d, dict(here["Resources"]) ?: PdfDict(), media, crop, rot))
        }
        walk(root["Pages"], emptyMap(), 0)
        return out
    }

    private fun box(v: Any?): DoubleArray? {
        val a = array(v) ?: return null
        if (a.size < 4) return null
        val n = a.take(4).map { num(it) ?: return null }
        val r = doubleArrayOf(minOf(n[0], n[2]), minOf(n[1], n[3]), maxOf(n[0], n[2]), maxOf(n[1], n[3]))
        return if (r[2] - r[0] < 1 || r[3] - r[1] < 1) null else r
    }

    private fun intersect(a: DoubleArray, b: DoubleArray): DoubleArray? {
        val r = doubleArrayOf(maxOf(a[0], b[0]), maxOf(a[1], b[1]), minOf(a[2], b[2]), minOf(a[3], b[3]))
        return if (r[2] - r[0] < 1 || r[3] - r[1] < 1) null else r
    }

    /** Treść strony: wszystkie strumienie /Contents rozpakowane i sklejone (z odstępem, jak każe specyfikacja). */
    fun contents(page: PdfPage): ByteArray {
        val c = resolve(page.dict["Contents"])
        val list: List<Any?> = when (c) {
            is PdfStream -> listOf(c)
            is List<*> -> c
            else -> emptyList()
        }
        val out = ByteArrayOutputStream()
        for (e in list) {
            val s = resolve(e) as? PdfStream ?: continue
            out.write(decodeStream(s))
            out.write('\n'.code)
            if (out.size() > MAX_STREAM) throw PdfException("treść strony za duża")
        }
        return out.toByteArray()
    }

    /** Pole /Info jako tekst (Producer, Creator, Title). */
    fun info(key: String): String? = (resolve(dict(trailer["Info"])?.get(key)) as? PdfString)?.text()

    // --- pomocnicze --------------------------------------------------------------------------------

    private fun startsWith(at: Int, s: String): Boolean {
        if (at < 0 || at + s.length > data.size) return false
        for (i in s.indices) if (data[at + i] != s[i].code.toByte()) return false
        return true
    }

    private fun indexOf(pat: ByteArray, from: Int, to: Int): Int {
        var i = maxOf(0, from)
        val last = minOf(to, data.size) - pat.size
        outer@ while (i <= last) {
            for (k in pat.indices) if (data[i + k] != pat[k]) { i++; continue@outer }
            return i
        }
        return -1
    }

    private fun lastIndexOf(pat: ByteArray, from: Int, downTo: Int): Int {
        var i = minOf(from, data.size - pat.size)
        outer@ while (i >= downTo) {
            for (k in pat.indices) if (data[i + k] != pat[k]) { i--; continue@outer }
            return i
        }
        return -1
    }

    companion object {
        const val MAX_STREAM = 128 * 1024 * 1024
        const val MAX_PAGES = 5000
        private val INHERITED = listOf("Resources", "MediaBox", "CropBox", "Rotate")
        val IMAGE_FILTERS = setOf("DCTDecode", "DCT", "JPXDecode", "CCITTFaxDecode", "CCF", "JBIG2Decode")
    }
}
