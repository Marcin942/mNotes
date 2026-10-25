package com.mdev.mnotes.core.goodnotes

import java.io.ByteArrayOutputStream

/**
 * Obiekty PDF w postaci czystego Kotlina (bez Androida), na potrzeby importu PDF-ów z GoodNotes.
 * Liczby są zawsze `Double`, `null` to obiekt pusty, tablice to `List<Any?>`, a słowniki gubią klucze z `null`
 * (tak każe specyfikacja: wpis z null znaczy to samo co brak wpisu).
 */
class PdfException(message: String) : Exception(message)

data class PdfName(val name: String) {
    override fun toString() = "/$name"
}

class PdfString(val bytes: ByteArray) {
    /** Tekst w PDFDocEncoding albo UTF-16BE z BOM; wystarcza do metadanych (Producer, Title). */
    fun text(): String =
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        else String(bytes, Charsets.ISO_8859_1)

    override fun toString() = text()
}

data class PdfObjRef(val num: Int, val gen: Int)

/** Słowo kluczowe treści strony albo znak budowy tablicy/słownika. */
data class PdfKeyword(val word: String)

class PdfDict(val map: LinkedHashMap<String, Any?> = LinkedHashMap()) {
    operator fun get(key: String): Any? = map[key]
    operator fun set(key: String, value: Any?) { if (value == null) map.remove(key) else map[key] = value }
    fun name(key: String): String? = (map[key] as? PdfName)?.name
    val keys: Set<String> get() = map.keys
}

class PdfStream(val dict: PdfDict, val raw: ByteArray)

internal object PdfEof

/** Leksyka PDF na tablicy bajtów: liczby, nazwy, napisy, słowa kluczowe, znaki `[ ] << >> { }`. */
class PdfLexer(val b: ByteArray, var pos: Int = 0, val end: Int = b.size) {

    fun skipWs() {
        while (pos < end) {
            val c = b[pos].toInt() and 0xFF
            if (isWs(c)) pos++
            else if (c == '%'.code) { while (pos < end && b[pos].toInt() != 10 && b[pos].toInt() != 13) pos++ }
            else return
        }
    }

    /** Następny element: Double, PdfName, PdfString, PdfKeyword albo [PdfEof]. */
    fun token(): Any {
        skipWs()
        if (pos >= end) return PdfEof
        val c = b[pos].toInt() and 0xFF
        when (c) {
            '/'.code -> return name()
            '('.code -> return literal()
            '['.code, ']'.code, '{'.code, '}'.code -> { pos++; return PdfKeyword(c.toChar().toString()) }
            '<'.code -> {
                if (pos + 1 < end && b[pos + 1].toInt() == '<'.code) { pos += 2; return KW_DICT_OPEN }
                return hex()
            }
            '>'.code -> {
                pos++
                if (pos < end && b[pos].toInt() == '>'.code) pos++
                return KW_DICT_CLOSE
            }
            ')'.code -> { pos++; return token() }
        }
        if (c == '+'.code || c == '-'.code || c == '.'.code || c in '0'.code..'9'.code) {
            number()?.let { return it }
        }
        val start = pos
        while (pos < end && isRegular(b[pos].toInt() and 0xFF)) pos++
        if (pos == start) pos++
        return PdfKeyword(String(b, start, pos - start, Charsets.ISO_8859_1))
    }

    private fun number(): Double? {
        val start = pos
        var p = pos
        var neg = false
        while (p < end && (b[p].toInt() == '+'.code || b[p].toInt() == '-'.code)) { if (b[p].toInt() == '-'.code) neg = !neg; p++ }
        var v = 0.0
        var digits = 0
        while (p < end && b[p] in DIGITS) { v = v * 10 + (b[p] - '0'.code.toByte()); p++; digits++ }
        if (p < end && b[p].toInt() == '.'.code) {
            p++
            var scale = 0.1
            while (p < end && b[p] in DIGITS) { v += (b[p] - '0'.code.toByte()) * scale; scale /= 10; p++; digits++ }
        }
        if (digits == 0) { pos = start; return null }
        // „12abc” to nie liczba, tylko słowo; „12)” albo „12/” już tak
        if (p < end && isRegular(b[p].toInt() and 0xFF)) {
            val c = b[p].toInt()
            if (c != 'e'.code && c != 'E'.code) { pos = start; return null }
        }
        pos = p
        return if (neg) -v else v
    }

    private fun name(): PdfName {
        pos++
        val out = ByteArrayOutputStream()
        while (pos < end) {
            val c = b[pos].toInt() and 0xFF
            if (!isRegular(c)) break
            if (c == '#'.code && pos + 2 < end && hexVal(b[pos + 1].toInt()) >= 0 && hexVal(b[pos + 2].toInt()) >= 0) {
                out.write(hexVal(b[pos + 1].toInt()) * 16 + hexVal(b[pos + 2].toInt())); pos += 3
            } else { out.write(c); pos++ }
        }
        return PdfName(String(out.toByteArray(), Charsets.ISO_8859_1))
    }

    private fun literal(): PdfString {
        pos++
        val out = ByteArrayOutputStream()
        var depth = 1
        while (pos < end) {
            val c = b[pos].toInt() and 0xFF
            pos++
            when (c) {
                '('.code -> { depth++; out.write(c) }
                ')'.code -> { depth--; if (depth == 0) break; out.write(c) }
                '\\'.code -> {
                    if (pos >= end) break
                    val e = b[pos].toInt() and 0xFF
                    pos++
                    when (e) {
                        'n'.code -> out.write(10); 'r'.code -> out.write(13); 't'.code -> out.write(9)
                        'b'.code -> out.write(8); 'f'.code -> out.write(12)
                        13 -> { if (pos < end && b[pos].toInt() == 10) pos++ }
                        10 -> {}
                        in '0'.code..'7'.code -> {
                            var v = e - '0'.code
                            var n = 1
                            while (n < 3 && pos < end && b[pos].toInt() in '0'.code..'7'.code) { v = v * 8 + (b[pos] - '0'.code.toByte()); pos++; n++ }
                            out.write(v and 0xFF)
                        }
                        else -> out.write(e)
                    }
                }
                13 -> { out.write(10); if (pos < end && b[pos].toInt() == 10) pos++ }
                else -> out.write(c)
            }
        }
        return PdfString(out.toByteArray())
    }

    private fun hex(): PdfString {
        pos++
        val out = ByteArrayOutputStream()
        var hi = -1
        while (pos < end) {
            val c = b[pos].toInt() and 0xFF
            pos++
            if (c == '>'.code) break
            val v = hexVal(c)
            if (v < 0) continue
            if (hi < 0) hi = v else { out.write(hi * 16 + v); hi = -1 }
        }
        if (hi >= 0) out.write(hi * 16)
        return PdfString(out.toByteArray())
    }

    /**
     * Jeden obiekt z rozwiniętymi tablicami i słownikami. [allowRefs] włącza rozpoznawanie `n g R` (w treści strony
     * odwołań nie ma, a „1 0 R” byłoby tam po prostu dwiema liczbami i operatorem).
     */
    fun parseObject(allowRefs: Boolean = true, depth: Int = 0): Any? {
        val t = token()
        return objectFrom(t, allowRefs, depth)
    }

    fun objectFrom(t: Any, allowRefs: Boolean, depth: Int): Any? {
        if (depth > MAX_DEPTH) throw PdfException("zbyt głęboko zagnieżdżony obiekt")
        return when (t) {
            PdfEof -> throw PdfException("nieoczekiwany koniec danych")
            is Double -> if (allowRefs && t >= 0 && t == Math.floor(t)) refOrNumber(t) else t
            is PdfKeyword -> when (t.word) {
                "[" -> {
                    val list = ArrayList<Any?>()
                    while (true) {
                        val n = token()
                        if (n == PdfEof) throw PdfException("niezamknięta tablica")
                        if (n is PdfKeyword && n.word == "]") break
                        list.add(objectFrom(n, allowRefs, depth + 1))
                    }
                    list
                }
                "<<" -> {
                    val d = PdfDict()
                    while (true) {
                        val k = token()
                        if (k == PdfEof) throw PdfException("niezamknięty słownik")
                        if (k is PdfKeyword && k.word == ">>") break
                        if (k !is PdfName) continue
                        val save = pos
                        val vt = token()
                        if (vt is PdfKeyword && vt.word == ">>") { pos = save; continue }
                        d[k.name] = objectFrom(vt, allowRefs, depth + 1)
                    }
                    d
                }
                "true" -> true
                "false" -> false
                "null" -> null
                else -> t
            }
            else -> t
        }
    }

    private fun refOrNumber(n: Double): Any {
        val save = pos
        val g = token()
        if (g is Double && g >= 0 && g == Math.floor(g)) {
            val r = token()
            if (r is PdfKeyword && r.word == "R") return PdfObjRef(n.toInt(), g.toInt())
        }
        pos = save
        return n
    }

    companion object {
        const val MAX_DEPTH = 64
        val KW_DICT_OPEN = PdfKeyword("<<")
        val KW_DICT_CLOSE = PdfKeyword(">>")
        private val DIGITS = '0'.code.toByte()..'9'.code.toByte()

        fun isWs(c: Int) = c == 0 || c == 9 || c == 10 || c == 12 || c == 13 || c == 32
        fun isDelim(c: Int) = c == '('.code || c == ')'.code || c == '<'.code || c == '>'.code || c == '['.code ||
            c == ']'.code || c == '{'.code || c == '}'.code || c == '/'.code || c == '%'.code
        fun isRegular(c: Int) = !isWs(c) && !isDelim(c)
        fun hexVal(c: Int) = when (c) {
            in '0'.code..'9'.code -> c - '0'.code
            in 'a'.code..'f'.code -> c - 'a'.code + 10
            in 'A'.code..'F'.code -> c - 'A'.code + 10
            else -> -1
        }
    }
}

/** Zapis obiektów PDF z powrotem do bajtów (do PDF-a tła). Odwołania przechodzą przez [refMap]. */
object PdfSerializer {
    fun write(v: Any?, out: ByteArrayOutputStream, refMap: (PdfObjRef) -> PdfObjRef? = { it }) {
        when (v) {
            null -> out.ascii("null")
            is Boolean -> out.ascii(if (v) "true" else "false")
            is Double -> out.ascii(num(v))
            is Int -> out.ascii(v.toString())
            is PdfName -> { out.write('/'.code); nameBytes(v.name, out) }
            is PdfString -> {
                out.write('<'.code)
                for (b in v.bytes) out.ascii("%02x".format(b.toInt() and 0xFF))
                out.write('>'.code)
            }
            is PdfObjRef -> {
                val r = refMap(v)
                if (r == null) out.ascii("null") else out.ascii("${r.num} ${r.gen} R")
            }
            is List<*> -> {
                out.write('['.code)
                v.forEachIndexed { i, e -> if (i > 0) out.write(' '.code); write(e, out, refMap) }
                out.write(']'.code)
            }
            is PdfDict -> {
                out.ascii("<<")
                for ((k, e) in v.map) {
                    out.write('/'.code); nameBytes(k, out); out.write(' '.code); write(e, out, refMap); out.write(' '.code)
                }
                out.ascii(">>")
            }
            is PdfKeyword -> out.ascii(v.word)
            else -> out.ascii("null")
        }
    }

    fun num(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "0"
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return v.toLong().toString()
        var s = "%.6f".format(java.util.Locale.ROOT, v).trimEnd('0')
        if (s.endsWith('.')) s = s.dropLast(1)
        return if (s == "-0") "0" else s
    }

    private fun nameBytes(name: String, out: ByteArrayOutputStream) {
        for (ch in name) {
            val c = ch.code and 0xFF
            if (c < 33 || c > 126 || c == '#'.code || PdfLexer.isDelim(c)) out.ascii("#%02x".format(c)) else out.write(c)
        }
    }

    internal fun ByteArrayOutputStream.ascii(s: String) = write(s.toByteArray(Charsets.ISO_8859_1))
}
