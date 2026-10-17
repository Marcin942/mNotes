package com.mdev.mnotes.core.samsung

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * „PDF zgodny z Notatkami”: zwykły PDF z rastrem, który w `/Root/PieceInfo/SPenSDK_PAGE_LIST/Private/Bin0` niesie
 * cały `.sdocx` (strumień Flate zaczynający się od `PK`). Zwykły PDF (sam raster) takiego strumienia nie ma.
 */
object SamsungPdf {
    private const val MARKER = "SPenSDK_PAGE_LIST"
    const val MAX_PDF = 256L * 1024 * 1024
    private const val MAX_SDOCX = 192 * 1024 * 1024

    /**
     * Tani test bez ładowania pliku do pamięci: szuka w strumieniu nazwy słownika Samsunga, która stoi w PDF-ie
     * czystym tekstem. Prawdziwą odpowiedź daje dopiero [extractSdocx] (nazwa może być ukryta w strumieniu obiektów).
     */
    fun looksCompatible(input: InputStream): Boolean {
        val pat = MARKER.toByteArray(Charsets.US_ASCII)
        val buf = ByteArray(64 * 1024 + pat.size)
        var keep = 0
        var total = 0L
        while (true) {
            val n = input.read(buf, keep, buf.size - keep)
            if (n < 0) return false
            total += n
            if (total > MAX_PDF) return false
            val end = keep + n
            if (indexOf(buf, end, pat, 0) >= 0) return true
            keep = minOf(pat.size - 1, end)
            System.arraycopy(buf, end - keep, buf, 0, keep)
        }
    }

    /** Osadzony `.sdocx` albo null, gdy to zwykły PDF. */
    fun extractSdocx(pdf: ByteArray): ByteArray? {
        val stream = "stream".toByteArray(Charsets.US_ASCII)
        val end = "endstream".toByteArray(Charsets.US_ASCII)
        var pos = 0
        while (true) {
            val i = indexOf(pdf, pdf.size, stream, pos)
            if (i < 0) return null
            if (i >= 3 && pdf[i - 3] == 'e'.code.toByte() && pdf[i - 2] == 'n'.code.toByte() && pdf[i - 1] == 'd'.code.toByte()) {
                pos = i + stream.size
                continue
            }
            var j = i + stream.size
            if (j + 1 < pdf.size && pdf[j] == '\r'.code.toByte() && pdf[j + 1] == '\n'.code.toByte()) j += 2
            else if (j < pdf.size && (pdf[j] == '\n'.code.toByte() || pdf[j] == '\r'.code.toByte())) j += 1
            val k = indexOf(pdf, pdf.size, end, j)
            if (k < 0) return null
            val body = pdf.copyOfRange(j, k)
            val data = if (isZip(body)) body else if (inflatesToZip(body)) inflate(body) else null
            if (data != null && SdocxReader.looksLikeSdocx(ByteArrayInputStream(data))) return data
            pos = k + end.size
        }
    }

    private fun isZip(b: ByteArray) =
        b.size >= 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() && b[2] == 3.toByte() && b[3] == 4.toByte()

    /** Rozpakowuje tylko 4 pierwsze bajty, żeby nie dekompresować każdego obrazu z PDF-a. */
    private fun inflatesToZip(b: ByteArray): Boolean {
        val inf = Inflater()
        return try {
            inf.setInput(b)
            val head = ByteArray(4)
            var got = 0
            while (got < 4) {
                val n = inf.inflate(head, got, 4 - got)
                if (n == 0 && (inf.finished() || inf.needsInput() || inf.needsDictionary())) break
                got += n
            }
            got == 4 && isZip(head)
        } catch (_: DataFormatException) {
            false
        } finally {
            inf.end()
        }
    }

    private fun inflate(b: ByteArray): ByteArray? {
        val inf = Inflater()
        return try {
            inf.setInput(b)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > MAX_SDOCX) return null
            }
            out.toByteArray()
        } catch (_: DataFormatException) {
            null
        } finally {
            inf.end()
        }
    }

    private fun indexOf(hay: ByteArray, end: Int, pat: ByteArray, from: Int): Int {
        if (pat.isEmpty()) return from
        val last = end - pat.size
        var i = from
        while (i <= last) {
            if (hay[i] == pat[0]) {
                var k = 1
                while (k < pat.size && hay[i + k] == pat[k]) k++
                if (k == pat.size) return i
            }
            i++
        }
        return -1
    }
}
