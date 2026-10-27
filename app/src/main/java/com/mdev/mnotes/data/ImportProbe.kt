package com.mdev.mnotes.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.mdev.mnotes.core.codec.FileKind
import com.mdev.mnotes.core.codec.FileKinds
import com.mdev.mnotes.core.goodnotes.GoodNotesConvert
import com.mdev.mnotes.core.samsung.SamsungPdf
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

/**
 * Plik wskazany do importu, rozpoznany po zawartości. [samsungPdf]: PDF zgodny z Notatkami (niesie osadzony `.sdocx`).
 * [goodnotesPdf]: PDF wyeksportowany z GoodNotes (iOS), zamieniany na obiekty mNotes (import eksperymentalny).
 */
class ImportSource(
    val uri: Uri,
    val name: String,
    val kind: FileKind,
    val samsungPdf: Boolean = false,
    val goodnotesPdf: Boolean = false,
) {
    /** Czy plik ma przejść przez okno importu z podglądem i wyborem trybu (Samsung Notes albo GoodNotes). */
    val isSamsung: Boolean get() = kind == FileKind.SDOCX || samsungPdf || goodnotesPdf
}

object ImportProbe {
    const val HEAD = 1024
    private const val MAX_ZIP_ENTRIES = 64
    private const val MAX_TREE_FILES = 500

    fun displayName(ctx: Context, uri: Uri): String =
        runCatching { ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast('/') ?: "import"

    /** Rozpoznaje plik. Nigdy nie rzuca: plik nieczytelny dostaje rodzaj [FileKind.UNKNOWN]. */
    fun probe(ctx: Context, uri: Uri, mimeHint: String? = null): ImportSource {
        val name = displayName(ctx, uri)
        val mime = (runCatching { ctx.contentResolver.getType(uri) }.getOrNull() ?: mimeHint).orEmpty()
        val head = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { s ->
                val buf = ByteArray(HEAD)
                var got = 0
                while (got < HEAD) { val n = s.read(buf, got, HEAD - got); if (n < 0) break; got += n }
                buf.copyOf(got)
            }
        }.getOrNull() ?: ByteArray(0)
        val kind = FileKinds.classify(name, mime, head) { zipNames(ctx, uri) }
        val samsungPdf = kind == FileKind.PDF && runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { SamsungPdf.looksCompatible(BufferedInputStream(it)) } ?: false
        }.getOrDefault(false)
        // Znacznik Quartz mają też zwykłe PDF-y z iOS i macOS, więc dopiero struktura stron rozstrzyga o GoodNotes.
        val goodnotesPdf = kind == FileKind.PDF && !samsungPdf && runCatching {
            val marker = ctx.contentResolver.openInputStream(uri)?.use { GoodNotesConvert.looksCompatible(BufferedInputStream(it)) } ?: false
            marker && GoodNotesConvert.detect(SamsungLoad.readPdf(ctx, uri))
        }.getOrDefault(false)
        return ImportSource(uri, name, kind, samsungPdf, goodnotesPdf)
    }

    private fun zipNames(ctx: Context, uri: Uri): List<String> {
        val out = ArrayList<String>()
        // Wpis, którego strumień nie przeczyta (STORED z deskryptorem), nie może kasować nazw zebranych przed nim.
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { s ->
                ZipInputStream(s).use { z ->
                    while (out.size < MAX_ZIP_ENTRIES) { val e = z.nextEntry ?: break; out.add(e.name) }
                }
            }
        }
        return out
    }

    /**
     * Pliki z folderu wybranego w systemowym oknie (SAF), razem z podfolderami. Zwraca tylko pliki; rodzaj ustala dopiero [probe].
     * Limit chroni przed folderem z tysiącami plików (np. całą kartą pamięci).
     */
    fun listTree(ctx: Context, tree: Uri): List<Uri> {
        val out = ArrayList<Uri>()
        fun walk(docId: String) {
            if (out.size >= MAX_TREE_FILES) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE)
            val dirs = ArrayList<String>()
            runCatching {
                ctx.contentResolver.query(children, cols, null, null, null)?.use { c ->
                    while (c.moveToNext() && out.size < MAX_TREE_FILES) {
                        val id = c.getString(0)
                        if (c.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR) dirs.add(id)
                        else out.add(DocumentsContract.buildDocumentUriUsingTree(tree, id))
                    }
                }
            }
            dirs.forEach { walk(it) }
        }
        walk(DocumentsContract.getTreeDocumentId(tree))
        return out
    }
}
