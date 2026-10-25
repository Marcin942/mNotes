package com.mdev.mnotes.data

import android.content.Context
import android.net.Uri
import com.mdev.mnotes.core.samsung.SamsungFormatException
import com.mdev.mnotes.core.samsung.SamsungPdf
import com.mdev.mnotes.core.samsung.SdDocument
import com.mdev.mnotes.core.samsung.SdocxReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Czyta plik wskazany do importu Samsung Notes: `.sdocx` wprost albo `.sdocx` osadzony w PDF-ie zgodnym z Notatkami. */
object SamsungLoad {
    /** Blokuje wątek (czytanie pliku); wołać poza UI. Rzuca [SamsungFormatException] lub [IOException] z komunikatem dla użytkownika. */
    fun read(ctx: Context, src: ImportSource): SdDocument {
        val input = ctx.contentResolver.openInputStream(src.uri) ?: throw IOException("nie można otworzyć pliku")
        return input.use {
            if (src.samsungPdf) {
                val pdf = readBounded(it, SamsungPdf.MAX_PDF)
                val sdocx = SamsungPdf.extractSdocx(pdf) ?: throw SamsungFormatException("w tym PDF-ie nie ma notatki Samsung Notes")
                SdocxReader.read(ByteArrayInputStream(sdocx))
            } else SdocxReader.read(it)
        }
    }

    /** Cały PDF do pamięci (do [SamsungPdf.MAX_PDF]); blokuje wątek. */
    fun readPdf(ctx: Context, uri: Uri): ByteArray {
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("nie można otworzyć pliku")
        return input.use { readBounded(it, SamsungPdf.MAX_PDF) }
    }

    private fun readBounded(input: java.io.InputStream, max: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw SamsungFormatException("plik jest zbyt duży")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
